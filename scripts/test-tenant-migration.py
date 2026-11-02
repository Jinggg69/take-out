"""Exercise the stage-two migration against disposable schemas on local MySQL.

Set TENANT_TEST_MYSQL_USER and TENANT_TEST_MYSQL_PASSWORD; optional
TENANT_TEST_MYSQL_PORT defaults to 3306. The account needs CREATE/DROP DATABASE.
"""
import os
import re
import secrets
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MYSQL = os.environ.get("TENANT_TEST_MYSQL", "mysql")
USER = os.environ["TENANT_TEST_MYSQL_USER"]
PORT = os.environ.get("TENANT_TEST_MYSQL_PORT", "3306")
assert PORT.isdigit()
ENV = os.environ.copy()
ENV["MYSQL_PWD"] = os.environ.get("TENANT_TEST_MYSQL_PASSWORD", "")
owned = []


def sql(text, db=None, ok=True):
    args = [MYSQL, "--host=127.0.0.1", "--port=" + PORT, "--user=" + USER,
            "--batch", "--skip-column-names", "--default-character-set=utf8mb4"]
    if db:
        assert db in owned and re.fullmatch(r"tenant_verify_[a-f0-9]{16}", db)
        args.append("--database=" + db)
    result = subprocess.run(args, input=text, text=True, encoding="utf-8", env=ENV,
                            capture_output=True)
    if ok and result.returncode:
        raise AssertionError(result.stderr)
    return result


def database():
    name = "tenant_verify_" + secrets.token_hex(8)
    sql(f"CREATE DATABASE `{name}` CHARACTER SET utf8mb4;")
    owned.append(name)
    return name


def initializer(text):
    text = re.sub(r"(?im)^\s*(?:CREATE DATABASE|USE)\b[^;]*;", "", text)
    assert not re.search(r"(?im)^\s*(?:CREATE DATABASE|USE)\b", text)
    return text


def snapshot(db, columns):
    """Compare every original cell, including PKs, money and payment states."""
    return {table: sql(f"SELECT {names} FROM `{table}` ORDER BY 1;", db).stdout
            for table, names in columns.items()}


def schema(db):
    # SHOW CREATE covers all columns, indexes, collations, FK actions and options.
    # AUTO_INCREMENT is a data-dependent counter, not a schema difference.
    tables = sql("SHOW TABLES;", db).stdout.splitlines()
    return {table: re.sub(r" AUTO_INCREMENT=\d+", "",
                         sql(f"SHOW CREATE TABLE `{table}`;", db).stdout)
            for table in tables}


def seed(db):
    sql("""
      INSERT INTO orders(id,number,status,user_id,address_book_id,order_time,
                         pay_method,pay_status,amount,refund_status)
      VALUES (900001,'fixture-paid',5,900001,900001,'2026-01-01 12:00:00',1,1,123.45,NULL),
             (900002,'fixture-refund',6,900001,900001,'2026-01-01 13:00:00',1,2,0.01,'SUCCESS');
      INSERT INTO refund_request(out_refund_no,order_id,out_trade_no,amount_fen,status,
                                 next_attempt_at,created_at,updated_at)
      VALUES ('fixture-refund',900002,'fixture-refund',1,'SUCCESS',NOW(),NOW(),NOW());
      INSERT INTO order_detail(id,order_id,dish_id,number,amount)
      VALUES (900001,900001,900001,1,123.45);
      INSERT INTO shopping_cart(id,user_id,dish_id,number,amount)
      VALUES (900001,900001,900001,2,19.99);
      INSERT INTO setmeal(id,category_id,name,price) VALUES (900001,900001,'fixture-setmeal',88.88);
    """, db)


def original_columns(db):
    rows = sql("SELECT table_name,GROUP_CONCAT(CONCAT('`',column_name,'`') ORDER BY ordinal_position) "
               "FROM information_schema.columns WHERE table_schema=DATABASE() "
               "GROUP BY table_name ORDER BY table_name;", db).stdout.splitlines()
    return dict(row.split('\t', 1) for row in rows)


def main():
    migration = ROOT / "database/migrations/20261029_01_tenant_store.sql"
    # The stage-two footer follows the original single-store initializer.
    init = (ROOT / 'database/junzhou.sql').read_text(encoding='utf-8')
    marker = '-- Stage 2 single-store foundation; keep in sync with the incremental migration.\n'
    assert init.count(marker) == 1
    old = init.split(marker)[0]
    db = database()
    sql(initializer(old), db)
    seed(db)
    columns = original_columns(db)
    cells = snapshot(db, columns)
    before = sql("SELECT COUNT(*), SUM(id) FROM employee; SELECT COUNT(*), SUM(id) FROM dish;", db).stdout
    assert migration.exists(), "tenant/store migration is missing"
    script = migration.read_text(encoding="utf-8")
    sql(script, db)
    sql(script, db)
    assert snapshot(db, columns) == cells, "upgrade/re-entry changed original business cells"
    assert sql("SELECT SUM(amount) FROM orders; SELECT SUM(amount_fen) FROM refund_request;", db).stdout.strip() == "123.46\n1"
    print("PASS: old-schema upgrade, repeat, all original cells and exact financial amounts")
    assert before == sql("SELECT COUNT(*), SUM(id) FROM employee; SELECT COUNT(*), SUM(id) FROM dish;", db).stdout
    for table in ("employee", "category", "dish", "setmeal", "orders", "shopping_cart"):
        assert sql(f"SELECT COUNT(*) FROM `{table}` WHERE tenant_id <> 1 OR tenant_id IS NULL;", db).stdout.strip() == "0"
    for table in ("orders", "shopping_cart"):
        assert sql(f"SELECT COUNT(*) FROM `{table}` WHERE store_id <> 1 OR store_id IS NULL;", db).stdout.strip() == "0"
    assert sql("SELECT COUNT(*) FROM tenant WHERE id=1; SELECT COUNT(*) FROM store WHERE id=1 AND tenant_id=1;", db).stdout.strip() == "1\n1"
    # Existing INSERT statements do not include the new fields.
    sql("INSERT INTO category(type,name,sort,status) VALUES (1,'migration test',1,1);", db)
    assert sql("SELECT tenant_id FROM category WHERE name='migration test';", db).stdout.strip() == "1"
    sql("INSERT INTO tenant(id,code,name) VALUES (2,'second','Second'); UPDATE category SET tenant_id=2 WHERE name='migration test';", db)
    sql(script, db)
    assert sql("SELECT tenant_id FROM category WHERE name='migration test';", db).stdout.strip() == "2", "re-entry must not overwrite ownership"
    assert sql("INSERT INTO store(tenant_id,code,name) VALUES (999,'invalid','invalid');", db, ok=False).returncode != 0
    fresh = database()
    sql(initializer(init), fresh)
    assert schema(db) == schema(fresh), "full fresh/upgraded schema differs"
    print("PASS: full schema parity (all tables, columns, indexes and relationships)")
    query = "SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'NULL') FROM information_schema.columns WHERE table_schema=DATABASE() AND (table_name IN ('tenant','store') OR column_name IN ('tenant_id','store_id')) ORDER BY table_name,ordinal_position;"
    assert sql(query, db).stdout == sql(query, fresh).stdout, "fresh and upgraded schemas differ"
    indexes = "SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('tenant','store') ORDER BY table_name,index_name,seq_in_index;"
    assert sql(indexes, db).stdout == sql(indexes, fresh).stdout, "fresh and upgraded indexes differ"
    relations = "SELECT table_name,column_name,referenced_table_name,referenced_column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name IN ('tenant','store') AND referenced_table_name IS NOT NULL ORDER BY table_name,column_name;"
    assert sql(relations, db).stdout == sql(relations, fresh).stdout, "fresh and upgraded relationships differ"
    conflict = database()
    sql(initializer(old), conflict)
    sql("CREATE TABLE tenant (id bigint NOT NULL PRIMARY KEY, code varchar(64) NOT NULL, name varchar(100) NOT NULL, status tinyint NOT NULL DEFAULT 1) ENGINE=InnoDB; INSERT INTO tenant VALUES (1,'other','existing',1);", conflict)
    result = sql(script, conflict, ok=False)
    assert result.returncode != 0, "migration must reject an existing incompatible tenant table"
    assert sql("SELECT code FROM tenant WHERE id=1;", conflict).stdout.strip() == "other"
    incompatible = database()
    sql(initializer(old), incompatible)
    sql("CREATE TABLE tenant (id bigint NOT NULL PRIMARY KEY, code varchar(64) NOT NULL, name varchar(100) NOT NULL, status tinyint NOT NULL DEFAULT 1) ENGINE=InnoDB; INSERT INTO tenant VALUES (1,'legacy-single-store','existing',1);", incompatible)
    result = sql(script, incompatible, ok=False)
    assert result.returncode != 0, "migration must reject a matching default row with incompatible table definition"
    partial = database()
    sql(initializer(old), partial)
    sql("ALTER TABLE employee ADD COLUMN tenant_id varchar(12);", partial)
    result = sql(script, partial, ok=False)
    assert result.returncode != 0 and "Existing ownership column is incompatible" in result.stderr
    sql("ALTER TABLE employee DROP COLUMN tenant_id;", partial)
    sql(script, partial)
    assert schema(partial) == schema(fresh)
    # Inject a deterministic crash after several committed ALTERs; use the real
    # script again for recovery, including the procedure left by the failed CALL.
    interrupted = database()
    sql(initializer(old), interrupted)
    seed(interrupted)
    interrupted_cells = snapshot(interrupted, columns)
    point = '    ALTER TABLE orders ADD COLUMN store_id bigint NOT NULL DEFAULT 1;'
    assert script.count(point) == 1
    broken = script.replace(point, "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fixture injected DDL failure';")
    failure = sql(broken, interrupted, ok=False)
    assert failure.returncode != 0 and 'fixture injected DDL failure' in failure.stderr
    assert sql("SELECT tenant_id FROM orders LIMIT 1;", interrupted).stdout.strip() == '1'
    assert sql("SELECT store_id FROM orders;", interrupted, ok=False).returncode != 0
    assert snapshot(interrupted, columns) == interrupted_cells
    sql(script, interrupted)
    sql(script, interrupted)
    assert schema(interrupted) == schema(fresh)
    assert snapshot(interrupted, columns) == interrupted_cells
    print("PASS: incompatible-column repair and recovery after committed partial DDL")
    # Re-entry must preserve ownership for every affected entity, not just category.
    sql("INSERT INTO store(id,tenant_id,code,name) VALUES (2,2,'second','Second');", db)
    for table in ('employee', 'category', 'dish', 'setmeal', 'orders', 'shopping_cart'):
        sql(f"UPDATE `{table}` SET tenant_id=2;", db)
    for table in ('orders', 'shopping_cart'):
        sql(f"UPDATE `{table}` SET store_id=2;", db)
    all_columns = original_columns(db)
    assigned = snapshot(db, all_columns)
    sql(script, db)
    assert snapshot(db, all_columns) == assigned, 're-entry changed existing ownership/data'
    print("PASS: existing ownership preserved for all six business tables")
    print("PASS: legacy writes, FK rejection and conflicting existing definitions")
    print("MySQL version: " + sql('SELECT VERSION();').stdout.strip())


if __name__ == "__main__":
    try:
        main()
    finally:
        for name in owned:
            assert re.fullmatch(r"tenant_verify_[a-f0-9]{16}", name)
            sql(f"DROP DATABASE `{name}`;")

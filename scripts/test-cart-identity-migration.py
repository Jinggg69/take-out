"""Verify the store menu migration in a disposable local MySQL schema only."""
import os
import re
import shutil
import subprocess
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
config = (ROOT / "backend/junzhou-take-out/junzhou-server/src/main/resources/application-dev.yml").read_text(encoding="utf-8")
block = re.search(r"(?ms)^  datasource:\s*\n(.*?)(?=^  \S|\Z)", config).group(1)
values = {key: re.search(r"(?m)^    " + key + r":\s*(.*?)\s*$", block).group(1).strip("\"'")
          for key in ("host", "port", "username", "password")}
if values["host"] not in ("localhost", "127.0.0.1") or not values["port"].isdigit():
    raise RuntimeError("Only local MySQL is permitted")
mysql = shutil.which("mysql") or str(Path(os.environ["ProgramFiles"]) / "MySQL/MySQL Server 8.4/bin/mysql.exe")
env = os.environ.copy()
env["MYSQL_PWD"] = values["password"]
schema = "cart_identity_verify_" + uuid.uuid4().hex
assert re.fullmatch(r"cart_identity_verify_[a-f0-9]{32}", schema)
owned = False


def sql(query, database=None, succeeds=True):
    args = [mysql, "--host=127.0.0.1", "--port=" + values["port"], "--user=" + values["username"],
            "--batch", "--skip-column-names", "--default-character-set=utf8mb4"]
    if database:
        assert owned and database == schema
        args += ["--database=" + schema]
    result = subprocess.run(args, input="SET SESSION sql_mode='STRICT_ALL_TABLES';\n" + query,
                            text=True, encoding="utf-8", env=env, capture_output=True)
    if (result.returncode == 0) != succeeds:
        raise AssertionError("Unexpected MySQL result: " + result.stderr)
    return result.stdout.strip()


try:
    sql(f"CREATE DATABASE `{schema}`;")
    owned = True
    migration = (ROOT / 'database/migrations/20261109_01_cart_identity.sql').read_text(encoding='utf-8')
    init = (ROOT / 'database/junzhou.sql').read_text(encoding='utf-8')
    init = re.sub(r'(?im)^\s*(?:CREATE DATABASE|USE)\b[^;]*;', '', init)
    legacy_init = init.split(migration)[0]
    sql(legacy_init, schema)
    insert = 'INSERT INTO shopping_cart(tenant_id,store_id,user_id,dish_id,setmeal_id,dish_flavor,number,amount) VALUES '
    sql(insert + "(1,1,7,3,NULL,concat(char(9),'hot',char(13)),2,1),(1,1,7,3,NULL,'hot',4,2),"
        "(1,1,7,NULL,3,'ignored',1,3),(1,1,7,3,NULL,NULL,1,1)", schema)
    sql(migration, schema, False)
    assert sql('SELECT count(*),sum(number) FROM shopping_cart WHERE user_id=7', schema) == '4\t8'
    sql('SET @cart_duplicates_reviewed=1;\n' + migration, schema)
    assert sql("SELECT number,amount FROM shopping_cart WHERE dish_flavor='hot'", schema) == '6\t1.00'
    assert sql("SELECT count(*) FROM shopping_cart WHERE dish_flavor=''", schema) == '2'
    for invalid_row in ["(1,1,7,3,NULL,'hot',1,1)", "(1,1,7,NULL,3,'',1,1)", "(1,1,7,3,3,'',1,1)",
                   "(1,1,7,NULL,NULL,'',1,1)", "(1,1,7,3,NULL,' padded',1,1)", "(1,1,7,NULL,4,'hot',1,1)"]:
        sql(insert + invalid_row, schema, False)
    sql(insert + "(1,1,7,3,NULL,'HOT',1,1),(1,2,7,3,NULL,'hot',1,1),"
        "(2,1,7,3,NULL,'hot',1,1),(1,1,8,3,NULL,'hot',1,1)", schema)
    sql(migration, schema)
    # Exercise a fresh initializer separately from the legacy upgrade.
    sql(f"DROP DATABASE `{schema}`;")
    owned = False
    schema = 'cart_identity_verify_' + uuid.uuid4().hex
    sql(f"CREATE DATABASE `{schema}`;")
    owned = True
    sql(init, schema)
    sql(migration, schema)
    assert sql("SELECT count(*) FROM information_schema.statistics WHERE table_schema=DATABASE() "
               "AND table_name='shopping_cart' AND index_name='uk_cart_identity'", schema) == '6'
    # Removing the new fields simulates a legacy cart for rejection checks.
    sql('ALTER TABLE shopping_cart DROP INDEX uk_cart_identity, DROP CHECK ck_cart_product, '
        'DROP CHECK ck_cart_quantity, DROP CHECK ck_cart_flavor, DROP COLUMN product_type, '
        'DROP COLUMN product_id, DROP COLUMN flavor_key', schema)
    sql(insert + "(1,1,7,3,NULL,'hot',2147483647,1),(1,1,7,3,NULL,'hot',1,1)", schema)
    sql('SET @cart_duplicates_reviewed=1;\n' + migration, schema, False)
    assert sql('SELECT count(*),sum(number) FROM shopping_cart', schema) == '2\t2147483648'
    sql('DELETE FROM shopping_cart', schema)
    sql(insert + "(1,1,7,NULL,NULL,'',1,1)", schema)
    sql(migration, schema, False)
    assert sql('SELECT count(*) FROM shopping_cart', schema) == '1'
    print('PASS: duplicate review, quantity merge, canonical flavor, isolation, rerun, fresh init and failure preservation')
finally:
    if owned:
        sql(f"DROP DATABASE `{schema}`;")

"""Verify the file-asset migration in a disposable local MySQL schema only."""
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
schema = "ownership_defaults_verify_" + uuid.uuid4().hex
assert re.fullmatch(r"ownership_defaults_verify_[a-f0-9]{32}", schema)
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


TABLES = ("employee", "category", "dish", "setmeal", "orders", "shopping_cart")
try:
    sql(f"CREATE DATABASE `{schema}`;")
    owned = True
    sql("CREATE TABLE tenant(id bigint PRIMARY KEY); INSERT INTO tenant VALUES(1),(2); "
        "CREATE TABLE store(id bigint PRIMARY KEY,tenant_id bigint); INSERT INTO store VALUES(1,1),(2,2);", schema)
    for table in TABLES:
        store = ",store_id bigint NOT NULL DEFAULT 1" if table in ("orders","shopping_cart") else ""
        sql(f"CREATE TABLE `{table}`(id bigint PRIMARY KEY,tenant_id bigint NOT NULL DEFAULT 1{store},payload varchar(64)); "
            f"INSERT INTO `{table}`(id,payload) VALUES(1,'legacy');", schema)
    before = {table: sql(f"SELECT * FROM `{table}` ORDER BY id;", schema) for table in TABLES}
    path = ROOT / "database/migrations/20261103_03_explicit_ownership.sql"
    migration = path.read_text(encoding="utf-8") if path.exists() else "SELECT 1;"
    sql(migration, schema)
    for table in TABLES:
        sql(f"INSERT INTO `{table}`(id,payload) VALUES(90,'missing');", schema, False)
        sql(f"INSERT INTO `{table}`(id,tenant_id) VALUES(91,NULL);", schema, False)
        if table in ("orders","shopping_cart"):
            sql(f"INSERT INTO `{table}`(id,tenant_id) VALUES(92,1);", schema, False)
        store = ",store_id" if table in ("orders","shopping_cart") else ""
        value = ",2" if store else ""
        sql(f"INSERT INTO `{table}`(id,tenant_id{store},payload) VALUES(2,2{value},'explicit');", schema)
        assert sql(f"SELECT * FROM `{table}` WHERE id=1;", schema) == before[table]
    snapshot = {table: sql(f"SELECT * FROM `{table}` ORDER BY id;", schema) for table in TABLES}
    sql(migration, schema)
    for table in TABLES:
        assert sql(f"SELECT * FROM `{table}` ORDER BY id;", schema) == snapshot[table]
    assert sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
               "AND column_name IN ('tenant_id','store_id') AND table_name IN ('employee','category','dish','setmeal','orders','shopping_cart') "
               "AND column_default IS NULL AND is_nullable='NO';", schema) == "8"
    assert sql("SELECT COUNT(*) FROM tenant WHERE id=1;", schema) == "1"
    assert sql("SELECT COUNT(*) FROM store WHERE id=1 AND tenant_id=1;", schema) == "1"
    # A partial DDL state can be retried, and incompatible schemas fail before changing defaults.
    sql("ALTER TABLE employee ALTER COLUMN tenant_id SET DEFAULT 1; ALTER TABLE category DROP COLUMN tenant_id;", schema)
    sql(migration, schema, False)
    assert sql("SELECT column_default FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='employee' AND column_name='tenant_id';", schema) == "1"
    sql("ALTER TABLE category ADD tenant_id bigint NOT NULL DEFAULT 1; UPDATE category SET tenant_id=2 WHERE id=2;", schema)
    sql(migration, schema)
    sql("ALTER TABLE employee ALTER COLUMN tenant_id SET DEFAULT 1; UPDATE shopping_cart SET tenant_id=2,store_id=1 WHERE id=1;", schema)
    sql(migration, schema, False)
    assert sql("SELECT column_default FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='employee' AND column_name='tenant_id';", schema) == "1"
    sql("UPDATE shopping_cart SET tenant_id=1 WHERE id=1;", schema)
    sql(migration, schema)
    # Verify the full initializer also finishes with all eight defaults absent.
    sql(f"DROP DATABASE `{schema}`;")
    owned = False
    schema = "ownership_defaults_verify_" + uuid.uuid4().hex
    sql(f"CREATE DATABASE `{schema}`;")
    owned = True
    initializer = (ROOT / "database/junzhou.sql").read_text(encoding="utf-8")
    initializer = re.sub(r"(?im)^\s*(?:CREATE DATABASE|USE)\b[^;]*;", "", initializer)
    sql(initializer, schema)
    sql(migration, schema)
    assert sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
               "AND column_name IN ('tenant_id','store_id') AND table_name IN ('employee','category','dish','setmeal','orders','shopping_cart') "
               "AND column_default IS NULL AND is_nullable='NO';", schema) == "8"
    print("PASS: 8 ownership defaults removed; omitted writes rejected; explicit writes, historical rows, default parents and reentry preserved; preflight failure and partial DDL retry verified")
finally:
    if owned:
        sql(f"DROP DATABASE `{schema}`;")

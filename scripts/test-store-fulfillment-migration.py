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
schema = "store_fulfillment_verify_" + uuid.uuid4().hex
assert re.fullmatch(r"store_fulfillment_verify_[a-f0-9]{32}", schema)
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
    migration = (ROOT / "database/migrations/20261109_02_store_fulfillment.sql").read_text(encoding="utf-8")
    init = (ROOT / "database/junzhou.sql").read_text(encoding="utf-8")
    init = re.sub(r"(?im)^\s*(?:CREATE DATABASE|USE)\b[^;]*;", "", init)
    sql(init.split(migration)[0], schema)
    sql(migration, schema)
    assert sql("SELECT delivery_radius_meters,minimum_order_amount,pack_fee_per_item,delivery_fee FROM store_settings WHERE store_id=1",schema) == "5000\t0.00\t1.00\t6.00"
    sql("UPDATE store_settings SET version=2,address='kept',delivery_fee=9.5 WHERE store_id=1",schema)
    sql(migration,schema)
    assert sql("SELECT version,address,delivery_fee FROM store_settings WHERE store_id=1",schema) == "2\tkept\t9.50"
    assert sql("SELECT count(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name IN ('store_snapshot','pricing_snapshot')",schema) == "2"
    for invalid in ["delivery_radius_meters=0","delivery_fee=-1","opens_at='24:00'","latitude=91,longitude=10","latitude=1,longitude=NULL"]:
        sql("UPDATE store_settings SET " + invalid + " WHERE store_id=1",schema,False)
    sql("INSERT INTO tenant(id,code,name) VALUES(99,'other','other'); INSERT INTO store(id,tenant_id,code,name) VALUES(999,99,'other','other')",schema)
    sql("INSERT INTO store_settings(tenant_id,store_id) VALUES(1,999)",schema,False)
    print("PASS: fulfillment upgrade, defaults, rerun preserves configuration, validation and tenant ownership")
finally:
    if owned:
        sql(f"DROP DATABASE `{schema}`;")

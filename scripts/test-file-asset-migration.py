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
schema = "file_asset_verify_" + uuid.uuid4().hex
assert re.fullmatch(r"file_asset_verify_[a-f0-9]{32}", schema)
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
    sql("CREATE TABLE tenant(id bigint PRIMARY KEY); INSERT INTO tenant VALUES(101),(102);", schema)
    migration = (ROOT / "database/migrations/20261103_01_file_asset.sql").read_text(encoding="utf-8")
    sql(migration, schema)
    insert = "INSERT INTO file_asset(id,tenant_id,uploader_employee_id,purpose,object_key,public_url,created_at) VALUES "
    sql(insert + "('a',101,42,'PUBLIC_CATALOG','tenants/101/a.png','https://test/a.png','2026-11-03 00:00:00');", schema)
    before = sql("SELECT * FROM file_asset;", schema)
    sql(migration, schema)
    assert sql("SELECT * FROM file_asset;", schema) == before
    sql(insert + "('b',102,84,'PUBLIC_CATALOG','tenants/102/b.png','https://test/b.png',NOW());", schema)
    sql(insert + "('x',999,42,'PUBLIC_CATALOG','x','https://test/x',NOW());", schema, False)
    sql(insert + "('x',101,42,'PRIVATE_DOCUMENT','x','https://test/x',NOW());", schema, False)
    sql("INSERT INTO file_asset(id,uploader_employee_id,purpose,object_key,public_url,created_at) VALUES('x',42,'PUBLIC_CATALOG','x','https://test/x',NOW());", schema, False)
    assert sql("SELECT COUNT(*) FROM file_asset;", schema) == "2"
    print("PASS: file-asset migration reentry, existing data preservation, tenant FK, explicit ownership and purpose constraints")
finally:
    if owned:
        sql(f"DROP DATABASE `{schema}`;")

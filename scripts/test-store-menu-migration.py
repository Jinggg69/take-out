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
schema = "store_menu_verify_" + uuid.uuid4().hex
assert re.fullmatch(r"store_menu_verify_[a-f0-9]{32}", schema)
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
    initializer = (ROOT / "database/junzhou.sql").read_text(encoding="utf-8")
    initializer = re.sub(r"(?im)^\s*(?:CREATE DATABASE|USE)\b[^;]*;", "", initializer)
    migration = (ROOT / "database/migrations/20261105_02_store_menu.sql").read_text(encoding="utf-8")
    assert migration in initializer
    sql(initializer.split(migration, 1)[0], schema)
    sql("INSERT INTO tenant(id,code,name) VALUES(99,'other','Other');"
        "INSERT INTO store(id,tenant_id,code,name) VALUES(21,1,'second','Second'),(22,99,'other','Other');", schema)
    for product in ("dish", "setmeal"):
        sql(f"INSERT INTO {product}(tenant_id,id,category_id,name,price) VALUES"
            "(1,9991,1,'menu test',20),(99,9992,1,'other menu test',30);", schema)
    sql(migration, schema)
    for product in ("dish", "setmeal"):
        table = "store_" + product
        key = product + "_id"
        assert sql(f"SELECT on_sale,sold_out,override_price,sort FROM {table} WHERE {key}=9991;", schema) == "1\t0\tNULL\t0"
        insert = f"INSERT INTO {table}(tenant_id,store_id,{key}) VALUES"
        sql(insert + "(1,21,9991);", schema)
        for invalid in ("(1,21,9991)", "(1,22,9991)", "(1,21,9992)"):
            sql(insert + invalid, schema, False)
        sql(f"UPDATE {table} SET sold_out=1,override_price=15,sort=3 WHERE store_id=1 AND {key}=9991;", schema)
        sql(migration, schema)
        assert sql(f"SELECT sold_out,override_price,sort FROM {table} WHERE {key}=9991 ORDER BY store_id;", schema) == "1\t15.00\t3\n0\tNULL\t0"
        assert sql(f"SELECT price FROM {product} WHERE id=9991;", schema) == "20.00"
    print("PASS: store menu upgrade, defaults, uniqueness, ownership, independent settings and rerun")
finally:
    if owned:
        sql(f"DROP DATABASE `{schema}`;")

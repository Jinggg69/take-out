"""Verify the payment-account migration in a disposable local MySQL schema only."""
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
schema = "payment_account_verify_" + uuid.uuid4().hex
assert re.fullmatch(r"payment_account_verify_[a-f0-9]{32}", schema)
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
    sql("CREATE TABLE tenant(id bigint PRIMARY KEY); INSERT INTO tenant VALUES(1),(2); "
        "CREATE TABLE store(id bigint PRIMARY KEY,tenant_id bigint); INSERT INTO store VALUES(1,1),(2,2); "
        "CREATE TABLE orders(id bigint PRIMARY KEY,number varchar(50),tenant_id bigint,store_id bigint,amount decimal(10,2)); "
        "INSERT INTO orders VALUES(10,'old',1,1,25.00);", schema)
    migration = (ROOT / "database/migrations/20261103_02_payment_account.sql").read_text(encoding="utf-8")
    setup = "SET @legacy_payment_mchid='original-merchant',@legacy_payment_appid='original-app';\n"
    sql(migration, schema, False)
    sql("INSERT INTO orders VALUES(20,'unresolved-foreign',2,2,9.99);", schema)
    sql(setup + migration, schema, False)
    assert sql("SELECT COUNT(*) FROM orders WHERE id=20 AND payment_account_id IS NULL;", schema) == "1"
    sql("INSERT INTO payment_account VALUES('other',2,2,'other-merchant','original-app'); "
        "UPDATE orders SET payment_account_id='other' WHERE id=20;", schema)
    sql(setup + migration, schema)
    assert sql("SELECT payment_account_id FROM orders WHERE id=10;", schema) == "legacy"
    before = sql("SELECT * FROM orders;", schema)
    sql(setup + migration, schema)
    assert sql("SELECT * FROM orders;", schema) == before
    sql("UPDATE payment_account SET mchid='replacement' WHERE id='legacy';", schema, False)
    sql("INSERT INTO payment_account VALUES('next',1,1,'next-merchant','original-app'); "
        "UPDATE store_payment_account SET payment_account_id='next' WHERE store_id=1;", schema)
    sql("UPDATE orders SET payment_account_id='next' WHERE id=10;", schema, False)
    sql("INSERT INTO orders VALUES(11,'new',1,1,12.34,'next');", schema)
    sql("INSERT INTO orders VALUES(12,'foreign',2,2,1.00,'next');", schema, False)
    sql("INSERT INTO orders(id,number,tenant_id,store_id,amount) VALUES(12,'missing',1,1,1.00);", schema, False)
    sql(setup + migration, schema)
    assert sql("SELECT payment_account_id FROM store_payment_account WHERE store_id=1;", schema) == "next"
    assert sql("SELECT payment_account_id FROM orders WHERE id=10;", schema) == "legacy"
    sql("SET @legacy_payment_mchid='replacement',@legacy_payment_appid='original-app';\n" + migration, schema, False)
    print("PASS: payment account migration reentry, historical binding, configuration switch, immutable identities, cross-store FK and missing-account rejection")
finally:
    if owned:
        sql(f"DROP DATABASE `{schema}`;")

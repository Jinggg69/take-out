"""Verify the mock-payment migration in a disposable local MySQL schema only."""
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
schema = "mock_payment_verify_" + uuid.uuid4().hex
assert re.fullmatch(r"mock_payment_verify_[a-f0-9]{32}", schema)
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
    sql("CREATE TABLE orders(id bigint PRIMARY KEY,amount decimal(10,2)); INSERT INTO orders VALUES(1,25.00);",schema)
    migration=(ROOT/"database/migrations/20261112_02_mock_payment.sql").read_text(encoding="utf-8")
    sql(migration,schema)
    assert sql("SELECT payment_channel FROM orders WHERE id=1",schema)=="WECHAT"
    sql("INSERT INTO orders VALUES(2,25.00,'MOCK'); INSERT INTO mock_payment VALUES(2,'SUCCESS',2500,'test-user',NOW(),NULL)",schema)
    before=sql("SELECT * FROM mock_payment",schema)
    sql(migration,schema)
    assert sql("SELECT * FROM mock_payment",schema)==before
    sql("UPDATE orders SET payment_channel='MOCK' WHERE id=1",schema,False)
    sql("UPDATE orders SET payment_channel='WECHAT' WHERE id=2",schema,False)
    sql("INSERT INTO orders VALUES(3,25.00,'OTHER')",schema,False)
    sql("INSERT INTO mock_payment VALUES(999,'NOTPAY',2500,'test-user',NULL,NULL)",schema,False)
    sql("UPDATE mock_payment SET amount_fen=-1 WHERE order_id=2",schema,False)
    sql("UPDATE mock_payment SET state='BAD' WHERE order_id=2",schema,False)
    print("PASS: migration reentry, historical WECHAT default, persisted mock state, immutable channels, FK and state/amount constraints")
finally:
    if owned:
        sql(f"DROP DATABASE `{schema}`;")

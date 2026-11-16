"""Verify the pickup migration in a disposable local MySQL schema only."""
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
schema = "order_fulfillment_verify_" + uuid.uuid4().hex
assert re.fullmatch(r"order_fulfillment_verify_[a-f0-9]{32}", schema)
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
    migration = (ROOT / "database/migrations/20261109_04_order_pickup.sql").read_text(encoding="utf-8")
    init = (ROOT / "database/junzhou.sql").read_text(encoding="utf-8")
    init = re.sub(r"(?im)^\s*(?:CREATE DATABASE|USE)\b[^;]*;", "", init)
    sql(init.split(migration)[0], schema)
    sql("INSERT INTO orders(tenant_id,store_id,number,status,user_id,address_book_id,order_time,amount,fulfillment_type,fulfillment_status) VALUES(1,1,'old-pickup',3,1,NULL,NOW(),10,'PICKUP','READY'),(1,1,'delivery',3,1,1,NOW(),10,'DELIVERY','PREPARING')",schema)
    sql(migration,schema)
    credential=sql("SELECT pickup_credential FROM orders WHERE number='old-pickup'",schema)
    assert re.fullmatch(r"[a-f0-9]{32}",credential)
    assert sql("SELECT pickup_number FROM orders WHERE number='old-pickup'",schema)
    assert sql("SELECT pickup_credential IS NULL FROM orders WHERE number='delivery'",schema)=="1"
    assert sql("SELECT COUNT(*) FROM rbac_permission WHERE code IN ('order:pickup:ready','order:pickup:verify')",schema)=="2"
    assert sql("SELECT COUNT(*) FROM role_permission WHERE permission_code LIKE 'order:pickup:%'",schema)=="0"
    sql("UPDATE orders SET pickup_verified_at=NOW(),pickup_verified_by=1,status=5,fulfillment_status='COMPLETED' WHERE number='old-pickup'",schema)
    sql(migration,schema)
    assert sql("SELECT pickup_credential FROM orders WHERE number='old-pickup'",schema)==credential
    assert sql("SELECT status,pickup_verified_by FROM orders WHERE number='old-pickup'",schema)=="5\t1"
    daily=(ROOT / "database/migrations/20261116_01_daily_pickup_number.sql").read_text(encoding="utf-8")
    sql("UPDATE orders SET order_time='2026-11-16 09:00:00' WHERE number='old-pickup'",schema)
    sql("INSERT INTO orders(tenant_id,store_id,number,status,user_id,order_time,amount,fulfillment_type,pickup_number) VALUES(1,1,'second-pickup',3,1,'2026-11-16 10:00:00',10,'PICKUP','OLD'),(1,1,'tomorrow-pickup',3,1,'2026-11-17 10:00:00',10,'PICKUP','OLD')",schema)
    sql(daily,schema)
    assert sql("SELECT pickup_number FROM orders WHERE number='old-pickup'",schema)=="1"
    assert sql("SELECT pickup_number FROM orders WHERE number='second-pickup'",schema)=="2"
    assert sql("SELECT pickup_number FROM orders WHERE number='tomorrow-pickup'",schema)=="1"
    assert sql("SELECT status,pickup_verified_by FROM orders WHERE number='old-pickup'",schema)=="5\t1"
    snapshot=sql("SELECT id,pickup_number,pickup_date FROM orders ORDER BY id",schema)
    sql(daily,schema)
    assert sql("SELECT id,pickup_number,pickup_date FROM orders ORDER BY id",schema)==snapshot
    assert sql("SELECT last_number FROM store_pickup_sequence WHERE pickup_date='2026-11-16'",schema)=="2"
    print("PASS: daily numeric backfill, next-day reset, migration rerun and audit preservation")
    tables=sql("SHOW TABLES",schema).splitlines()
    assert all(re.fullmatch(r"[A-Za-z0-9_]+", name) for name in tables)
    sql("SET FOREIGN_KEY_CHECKS=0;"+";".join("DROP TABLE `"+name+"`" for name in tables)+";SET FOREIGN_KEY_CHECKS=1;",schema)
    sql(init,schema)
    assert sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name IN ('pickup_number','pickup_credential','pickup_verified_at','pickup_verified_by')",schema)=="4"
    print("PASS: pickup credential backfill, delivery preservation, RBAC catalogue without implicit grants, rerun preserves credential/audit, fresh initialization")

finally:
    if owned:
        sql(f"DROP DATABASE `{schema}`;")


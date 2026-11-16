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
    daily = (ROOT / "database/migrations/20261116_01_daily_pickup_number.sql").read_text(encoding="utf-8")
    sql("UPDATE orders SET order_time='2026-11-16 09:00:00' WHERE number='old-pickup';"
        "INSERT INTO tenant(id,code,name) VALUES(99,'pickup-other','Other');"
        "INSERT INTO store(id,tenant_id,code,name) VALUES(21,1,'pickup-second','Second'),(22,99,'pickup-other','Other');",schema)
    for number,tenant,store,when in [
        ("same-day",1,1,"2026-11-16 10:00:00"),
        ("next-day",1,1,"2026-11-17 00:00:00"),
        ("second-store",1,21,"2026-11-16 10:00:00"),
        ("other-tenant",99,22,"2026-11-16 10:00:00"),
    ]:
        sql(f"INSERT INTO orders(tenant_id,store_id,number,status,user_id,order_time,amount,fulfillment_type,pickup_number) "
            f"VALUES({tenant},{store},'{number}',3,1,'{when}',10,'PICKUP','OLD');",schema)
    sql(daily,schema)
    assert sql("SELECT pickup_number,pickup_date,status,pickup_verified_by FROM orders WHERE number='old-pickup'",schema)=="1\t2026-11-16\t5\t1"
    assert sql("SELECT pickup_number FROM orders WHERE number='same-day'",schema)=="2"
    assert sql("SELECT COUNT(*) FROM orders WHERE number IN ('next-day','second-store','other-tenant') AND pickup_number='1'",schema)=="3"
    assert sql("SELECT pickup_date IS NULL FROM orders WHERE number='delivery'",schema)=="1"
    before=sql("SELECT id,pickup_number,pickup_date FROM orders ORDER BY id",schema)
    sql(daily,schema)
    assert sql("SELECT id,pickup_number,pickup_date FROM orders ORDER BY id",schema)==before

    # Run the application's actual upsert against MySQL, with separate concurrent connections.
    import concurrent.futures
    import xml.etree.ElementTree as ET
    mapper=ET.parse(ROOT / "backend/junzhou-take-out/junzhou-server/src/main/resources/mapper/OrderMapper.xml").getroot()
    reserve="".join(mapper.find("insert[@id='reservePickupNumber']").itertext())
    reserve=reserve.replace("#{tenant}","1").replace("#{store}","1").replace("#{date}","'2026-11-16'")
    read="SELECT last_number FROM store_pickup_sequence WHERE tenant_id=1 AND store_id=1 AND pickup_date='2026-11-16'"
    def allocate(_):
        return int(sql("START TRANSACTION;"+reserve+";"+read+";COMMIT;",schema))
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        assert sorted(pool.map(allocate,range(6)))==list(range(3,9))
    sql("START TRANSACTION;"+reserve+";ROLLBACK;",schema)
    assert sql(read,schema)=="8"
    sql(daily,schema)
    assert sql(read,schema)=="8"
    assert allocate(0)==9
    sql("UPDATE orders SET pickup_number='1' WHERE number='same-day'",schema,succeeds=False)
    tables=sql("SHOW TABLES",schema).splitlines()
    assert all(re.fullmatch(r"[A-Za-z0-9_]+", name) for name in tables)
    sql("SET FOREIGN_KEY_CHECKS=0;"+";".join("DROP TABLE `"+name+"`" for name in tables)+";SET FOREIGN_KEY_CHECKS=1;",schema)
    sql(init,schema)
    assert sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name IN ('pickup_number','pickup_credential','pickup_verified_at','pickup_verified_by')",schema)=="4"
    assert sql("SELECT COUNT(*) FROM store_pickup_sequence",schema)=="0"
    assert sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='orders' AND index_name='uk_order_pickup_number'",schema)=="4"
    print("PASS: legacy migration, daily numeric backfill, store/date/tenant isolation, concurrent MySQL allocation, rollback, idempotent migration, audit preservation and fresh initialization")

finally:
    if owned:
        sql(f"DROP DATABASE `{schema}`;")


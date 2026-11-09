"""Verify the dining session migration in a disposable local MySQL schema only."""
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
    migration = (ROOT / "database/migrations/20261109_05_dining_session.sql").read_text(encoding="utf-8")
    init = (ROOT / "database/junzhou.sql").read_text(encoding="utf-8")
    init = re.sub(r"(?im)^\s*(?:CREATE DATABASE|USE)\b[^;]*;", "", init)
    sql(init.split(migration)[0],schema)
    sql("INSERT INTO store_dining_table(id,tenant_id,store_id,name,enabled) VALUES(901,1,1,'A901',1)",schema)
    sql(migration,schema)
    token=sql("SELECT qr_token FROM store_dining_table WHERE id=901",schema)
    assert re.fullmatch(r"[a-f0-9]{32}",token)
    sql("INSERT INTO `user`(id,openid) VALUES(9001,'dining-temp'); INSERT INTO dining_session(id,tenant_id,store_id,table_id,table_snapshot,opened_at) VALUES('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',1,1,901,'A901',NOW()); INSERT INTO dining_session_member VALUES('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',9001)",schema)
    sql("UPDATE store_dining_table SET current_session_id='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' WHERE id=901",schema)
    sql("INSERT INTO dining_session(id,tenant_id,store_id,table_id,table_snapshot,opened_at) VALUES('bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',1,999,901,'bad',NOW())",schema,False)
    sql("INSERT INTO dining_session_member VALUES('bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',9001)",schema,False)
    sql("INSERT INTO orders(tenant_id,store_id,number,status,user_id,order_time,amount,fulfillment_type,table_id,dining_session_id) VALUES(1,1,'dining-test',1,9001,NOW(),10,'DINE_IN',901,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')",schema)
    sql("UPDATE orders SET table_id=999 WHERE number='dining-test'",schema,False)
    sql(migration,schema)
    assert sql("SELECT qr_token,current_session_id FROM store_dining_table WHERE id=901",schema)==token+"\taaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    assert sql("SELECT COUNT(*) FROM rbac_permission WHERE code IN ('dining:read','dining:manage','dining:close','order:dine:complete')",schema)=="4"
    assert sql("SELECT COUNT(*) FROM role_permission WHERE permission_code LIKE 'dining:%' OR permission_code='order:dine:complete'",schema)=="0"
    tables=sql("SHOW TABLES",schema).splitlines()
    assert all(re.fullmatch(r"[A-Za-z0-9_]+",name) for name in tables)
    sql("SET FOREIGN_KEY_CHECKS=0;"+";".join("DROP TABLE `"+name+"`" for name in tables)+";SET FOREIGN_KEY_CHECKS=1;",schema)
    sql(init,schema)
    assert sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='dining_session_id'",schema)=="1"
    print("PASS: dining token backfill/rerun, session/member/order ownership constraints, permission catalogue without grants, fresh initialization")

finally:
    if owned:
        sql(f"DROP DATABASE `{schema}`;")


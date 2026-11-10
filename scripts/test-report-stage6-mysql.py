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
schema = "report_verify_" + uuid.uuid4().hex
assert re.fullmatch(r"report_verify_[a-f0-9]{32}", schema)
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
    owned=True
    init=(ROOT/'database/junzhou.sql').read_text(encoding='utf-8')
    init=re.sub(r"(?im)^\s*(?:CREATE DATABASE|USE)\b[^;]*;", "", init)
    migration=(ROOT/'database/migrations/20261110_01_report_indexes.sql').read_text(encoding='utf-8')
    sql(init.split(migration)[0],schema)
    sql(migration,schema);sql(migration,schema)
    assert sql("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name LIKE 'ix_report_%'",schema)=='5'
    sql("INSERT INTO tenant(id,code,name) VALUES(2,'b','B'); INSERT INTO store(id,tenant_id,code,name) VALUES(2,1,'a2','A2'),(3,2,'b1','B1');",schema)
    sql("INSERT INTO orders(id,tenant_id,store_id,number,status,user_id,address_book_id,order_time,checkout_time,delivery_time,cancel_time,amount,fulfillment_type,fulfillment_status) VALUES(9001,1,1,'r-a',5,1,NULL,'2026-11-01 23:00','2026-11-02 00:00','2026-11-02 10:00',NULL,10,'PICKUP','COMPLETED'),(9002,1,1,'r-b',6,2,NULL,'2026-11-01 21:00','2026-11-02 12:00',NULL,'2026-11-01 22:00',20,'PICKUP','CANCELLED'),(9003,1,2,'r-c',3,3,NULL,'2026-11-02 10:00','2026-11-02 11:00',NULL,NULL,30,'DINE_IN','PREPARING'),(9004,2,3,'r-d',3,4,1,'2026-11-02 10:00','2026-11-02 11:00',NULL,NULL,999,'DELIVERY','PREPARING')",schema)
    sql("INSERT INTO refund_request(out_refund_no,order_id,out_trade_no,amount_fen,status,next_attempt_at,created_at,updated_at) VALUES('rr-a',9001,'r-a',1000,'SUCCESS',NOW(),NOW(),'2026-11-03 00:00'),('rr-b',9002,'r-b',2000,'PROCESSING',NOW(),NOW(),'2026-11-02 13:00')",schema)
    sql("INSERT INTO order_detail(order_id,dish_id,setmeal_id,name,number,amount) VALUES(9001,100,NULL,'fish',2,5),(9002,200,NULL,'late',1,20),(9003,NULL,100,'meal',1,30)",schema)
    sql("SET SESSION time_zone='+00:00'; SET SESSION time_zone='+08:00';",schema)
    assert sql("SELECT TIMESTAMPDIFF(HOUR,UTC_TIMESTAMP(),NOW())",schema)=='8'
    import xml.etree.ElementTree as ET
    tree=ET.parse(ROOT/'backend/junzhou-take-out/junzhou-server/src/main/resources/mapper/ReportMapper.xml').getroot()
    queries={}
    for node in tree.findall('select'):
        if node.attrib['id']=='menu':continue
        raw=ET.tostring(node,encoding='unicode')
        raw=re.sub(r'<include refid="scope"\s*/>', 'o.tenant_id=1 and o.store_id in (1,2)', raw)
        raw=re.sub(r'<[^>]*>',' ',raw).replace('&gt;','>').replace('&lt;','<')
        raw=raw.replace('#{from}',"'2026-11-01 00:00:00'").replace('#{until}',"'2026-11-04 00:00:00'")
        queries[node.attrib['id']]=raw
    daily=sql(queries['daily'],schema)
    rows={r.split('\t')[0]:r.split('\t')[1:] for r in daily.splitlines()}
    from decimal import Decimal as D
    assert D(rows['2026-11-02'][0])==60 and D(rows['2026-11-03'][1])==10
    assert rows['2026-11-01'][2:] == ['2','0','0','1']
    ledger=sql(queries['ledger'],schema);assert '999.00' not in ledger and 'REFUND' in ledger
    sales=sql(queries['sales'],schema);assert 'fish' not in sales and 'late' in sales and 'meal' in sales
    previous=sql(queries['sales'].replace("'2026-11-04 00:00:00'","'2026-11-03 00:00:00'"),schema);assert 'fish' in previous
    details=sql(queries['itemDetails'],schema);assert 'REFUNDED' in details and 'COUNTED' in details
    single=sql(queries['daily'].replace('in (1,2)','in (1)'),schema);assert '999' not in single
    sql(queries['users'],schema);sql(queries['overview'],schema)
    for kind in ['dish','setmeal']:
        raw=ET.tostring(tree.find("select[@id='menu']"),encoding='unicode')
        raw=re.sub(r'<choose>.*?</choose>',f'store_{kind} m join {kind} p on p.id=m.{kind}_id',raw,flags=re.S)
        raw=re.sub(r'<bind[^>]*/>','',raw)
        raw=re.sub(r'<foreach.*?</foreach>','(1,2)',raw,flags=re.S)
        raw=re.sub(r'<[^>]*>',' ',raw).replace('#{tenant}','1')
        sql(raw,schema)
    report=ROOT/'local-reports/stage6';report.mkdir(parents=True,exist_ok=True)
    plans=[]
    for key,q in queries.items():plans += [key,sql('EXPLAIN '+q,schema)]
    (report/'mysql-explain.txt').write_text('\n\n'.join(plans),encoding='utf-8')
    print('PASS: init, repeatable index migration, actual Mapper SQL, cross-day payment/refund, late cancelled payment, tenant/store scope; EXPLAIN saved.')
finally:
    if owned:sql(f"DROP DATABASE `{schema}`;")

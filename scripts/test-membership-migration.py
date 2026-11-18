"""Exercise membership schema on an owned disposable MySQL database only."""
from pathlib import Path
import re, runpy, subprocess, uuid
ROOT=Path(__file__).resolve().parents[1]
connection=runpy.run_path(str(ROOT/'scripts/test-merchant-discovery-migration.py'))['connection']
name='membership_verify_'+uuid.uuid4().hex
assert re.fullmatch(r'membership_verify_[a-f0-9]{32}',name)
def sql(query,database=None):
 args,env,password=connection()
 if database is not None:
  if database!=name:raise RuntimeError('Unowned database')
  args.append('--database='+database)
 r=subprocess.run(args,input=query,text=True,encoding='utf-8',env=env,capture_output=True)
 if r.returncode:raise RuntimeError(r.stderr.replace(password,'[redacted]') if password else r.stderr)
 return r.stdout.strip()
try:
 sql('CREATE DATABASE `'+name+'` CHARACTER SET utf8mb4;')
 initial=(ROOT/'database/junzhou.sql').read_text(encoding='utf-8')
 initial=re.sub(r'(?im)^\s*(?:CREATE DATABASE|USE)\b[^;]*;','',initial)
 sql(initial,name)
 before=sql('SELECT COUNT(*),SUM(amount) FROM orders; SELECT COUNT(*) FROM employee; SELECT COUNT(*) FROM user;',name)
 migration=(ROOT/'database/migrations/20261117_02_membership.sql').read_text(encoding='utf-8')
 sql(migration,name)
 snapshot=sql("SELECT COUNT(*) FROM member_level; SELECT COUNT(*) FROM role_permission WHERE permission_code LIKE 'member:%' OR permission_code LIKE 'coupon:%';",name)
 sql(migration,name)
 assert snapshot==sql("SELECT COUNT(*) FROM member_level; SELECT COUNT(*) FROM role_permission WHERE permission_code LIKE 'member:%' OR permission_code LIKE 'coupon:%';",name)
 assert before==sql('SELECT COUNT(*),SUM(amount) FROM orders; SELECT COUNT(*) FROM employee; SELECT COUNT(*) FROM user;',name)
 print('PASS: membership fresh initialization, repeated migration, and unchanged existing users/employees/orders')
finally:
 sql('DROP DATABASE IF EXISTS `'+name+'`;')

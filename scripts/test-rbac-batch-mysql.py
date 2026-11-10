"""Execute the batch authorization Mapper SQL against an isolated MySQL fixture."""
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
schema = "rbac_verify_" + uuid.uuid4().hex
assert re.fullmatch(r"rbac_verify_[a-f0-9]{32}", schema)
owned = False


def sql(query, database=None, succeeds=True):
    args = [mysql, "--host=127.0.0.1", "--port=" + values["port"], "--user=" + values["username"],
            "--batch", "--skip-column-names", "--default-character-set=utf8mb4"]
    if database:
        assert owned and database == schema
        args += ["--database=" + schema]
    result = subprocess.run(args, input=query,
                            text=True, encoding="utf-8", env=env, capture_output=True)
    if (result.returncode == 0) != succeeds:
        raise AssertionError("Unexpected MySQL result: " + result.stderr)
    return result.stdout.strip()


try:
    sql(f"CREATE DATABASE `{schema}`;")
    owned = True
    sql("CREATE TABLE employee(id BIGINT,tenant_id BIGINT,status INT);"
        "CREATE TABLE store(id BIGINT,tenant_id BIGINT,status INT,name VARCHAR(32));"
        "CREATE TABLE employee_role(tenant_id BIGINT,employee_id BIGINT,role_code VARCHAR(32),data_scope VARCHAR(32));"
        "CREATE TABLE role_permission(tenant_id BIGINT,role_code VARCHAR(32),permission_code VARCHAR(32));"
        "CREATE TABLE employee_role_store(tenant_id BIGINT,employee_id BIGINT,role_code VARCHAR(32),store_id BIGINT);"
        "INSERT INTO employee VALUES(1,1,1),(2,2,1);"
        "INSERT INTO store VALUES(10,1,1,'A'),(11,1,0,'disabled'),(20,2,1,'foreign');"
        "INSERT INTO employee_role VALUES(1,1,'CLERK','SPECIFIED_STORES'),(2,2,'CLERK','TENANT_STORES');"
        "INSERT INTO role_permission VALUES(1,'CLERK','order:read'),(1,'CLERK','order:accept'),(2,'CLERK','order:refund');"
        "INSERT INTO employee_role_store VALUES(1,1,'CLERK',10),(1,1,'CLERK',11),(2,2,'CLERK',20);",schema)
    source=(ROOT/'backend/junzhou-take-out/junzhou-server/src/main/java/com/junzhou/mapper/RbacMapper.java').read_text(encoding='utf-8')
    expected={'activeEmployee':['1'], 'activeStoresWithNames':['10\tA'],
              'employeeGrants':['CLERK\tSPECIFIED_STORES\torder:accept','CLERK\tSPECIFIED_STORES\torder:read'],
              'employeeRoleStores':['CLERK\t10','CLERK\t11']}
    for method,want in expected.items():
        end=source.index(' '+method+'(')
        start=source.rfind('@Select("',0,end)+len('@Select("')
        query=source[start:source.index('")',start)].replace('#{t}','1').replace('#{e}','1')
        got=sql(query,schema).splitlines()
        assert sorted(got)==sorted(want),(method,got,want)
    print('PASS: four actual batch Mapper queries, active stores, role-bound scope and tenant/employee isolation; default MySQL mode.')
finally:
    if owned: sql(f"DROP DATABASE `{schema}`;")

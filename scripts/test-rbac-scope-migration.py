"""Verify the RBAC migration in a disposable local MySQL schema only."""
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
schema = "rbac_scope_verify_" + uuid.uuid4().hex
assert re.fullmatch(r"rbac_scope_verify_[a-f0-9]{32}", schema)
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
    # Start from the 3.1 schema to exercise a real upgrade.
    initializer = initializer.split("-- Stage 3.2.")[0]
    sql(initializer, schema)
    sql("INSERT INTO employee_role(tenant_id,employee_id,role_code) VALUES(1,1,'MERCHANT_ADMIN');", schema)
    path=ROOT / "database/migrations/20261103_05_rbac_scope.sql"
    migration=path.read_text(encoding="utf-8") if path.exists() else "SELECT 1;"
    sql(migration, schema)
    assert sql("SELECT data_scope FROM employee_role WHERE employee_id=1;", schema) == "CURRENT_STORE"
    for scope in ['SPECIFIED_STORES','TENANT_STORES','CURRENT_STORE']:
        sql(f"UPDATE employee_role SET data_scope='{scope}' WHERE employee_id=1;", schema)
    for scope in ['*','ALL','OTHER_TENANT','',None]:
        value="NULL" if scope is None else "'"+scope+"'"
        sql(f"UPDATE employee_role SET data_scope={value} WHERE employee_id=1;", schema, False)
    for code in ['*','order:*','*:read','bad','order:read:all','ORDER:READ']:
        sql(f"INSERT INTO rbac_permission VALUES(1,'{code}','invalid');", schema, False)
    sql("DELETE FROM role_permission WHERE tenant_id=1 AND role_code='MERCHANT_ADMIN' AND permission_code='order:refund'; UPDATE employee_role SET data_scope='SPECIFIED_STORES';", schema)
    sql(migration, schema)
    assert sql("SELECT data_scope FROM employee_role WHERE employee_id=1;", schema) == "SPECIFIED_STORES"
    assert sql("SELECT COUNT(*) FROM role_permission WHERE role_code='MERCHANT_ADMIN' AND permission_code='order:refund';", schema) == "0"
    assert sql("SELECT COUNT(*) FROM rbac_permission WHERE code='order:cancel';", schema) == "1"
    sql("INSERT INTO store(id,tenant_id,code,name) VALUES(10,1,'test-a','A'),(11,1,'test-b','B'); INSERT INTO employee_store VALUES(1,1,10),(1,1,11); INSERT INTO employee_role(tenant_id,employee_id,role_code,data_scope) VALUES(1,1,'MANAGER','SPECIFIED_STORES');",schema)
    sql("INSERT INTO employee_role_store VALUES(1,1,'MERCHANT_ADMIN',10),(1,1,'MANAGER',11);",schema)
    assert sql("SELECT store_id FROM employee_role_store WHERE tenant_id=1 AND employee_id=1 AND role_code='MERCHANT_ADMIN';",schema)=="10"
    sql("INSERT INTO employee_role_store VALUES(1,1,'CLERK',10);",schema,False)
    sql("INSERT INTO employee_role_store VALUES(1,1,'MANAGER',1);",schema,False)
    sql("INSERT INTO employee_role_store VALUES(2,1,'MANAGER',11);",schema,False)
    sql(migration,schema)
    assert sql("SELECT COUNT(*) FROM employee_role_store;",schema)=="2"
    # Fresh initializer includes stage 3.2 as well.
    sql(f"DROP DATABASE `{schema}`;")
    owned=False
    schema="rbac_scope_verify_"+uuid.uuid4().hex
    sql(f"CREATE DATABASE `{schema}`;")
    owned=True
    initializer=(ROOT / "database/junzhou.sql").read_text(encoding="utf-8")
    initializer=re.sub(r"(?im)^\s*(?:CREATE DATABASE|USE)\b[^;]*;", "", initializer)
    sql(initializer,schema)
    sql(migration,schema)
    assert sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='employee_role' AND column_name='data_scope';",schema)=="1"
    sql("ALTER TABLE employee_role DROP CHECK ck_employee_role_scope; ALTER TABLE employee_role ADD CONSTRAINT ck_employee_role_scope CHECK (1=1);",schema)
    sql(migration,schema,False)
    print("PASS: scope upgrade/defaults, allowed scopes, wildcard rejection, non-escalating rerun, full initializer")
finally:
    if owned:
        sql(f"DROP DATABASE `{schema}`;")

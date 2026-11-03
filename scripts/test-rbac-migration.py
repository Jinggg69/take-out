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
schema = "rbac_verify_" + uuid.uuid4().hex
assert re.fullmatch(r"rbac_verify_[a-f0-9]{32}", schema)
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
        "CREATE TABLE store(id bigint PRIMARY KEY,tenant_id bigint NOT NULL); INSERT INTO store VALUES(1,1),(2,2); "
        "CREATE TABLE employee(id bigint PRIMARY KEY,tenant_id bigint NOT NULL); INSERT INTO employee VALUES(1,1),(2,2);", schema)
    # Reject invalid existing ownership before adding any indexes or tables.
    sql("INSERT INTO employee VALUES(3,99);", schema)
    path = ROOT / "database/migrations/20261103_04_rbac.sql"
    migration = path.read_text(encoding="utf-8") if path.exists() else "SELECT 1;"
    sql(migration, schema, False)
    assert sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='rbac_role';", schema) == "0"
    sql("DELETE FROM employee WHERE id=3;", schema)
    sql(migration, schema)
    assert sql("SELECT COUNT(*) FROM rbac_role;", schema) == "6"
    assert sql("SELECT COUNT(*) FROM rbac_permission WHERE tenant_id=1;", schema) == "8"
    # Grants must be explicit; never guess a historical administrator or store.
    assert sql("SELECT COUNT(*) FROM employee_role;", schema) == "0"
    assert sql("SELECT COUNT(*) FROM employee_store;", schema) == "0"
    sql("INSERT INTO employee_role VALUES(1,1,'CLERK'); INSERT INTO employee_store VALUES(1,1,1);", schema)
    for query in [
        "INSERT INTO employee_role VALUES(2,1,'CLERK');",
        "INSERT INTO employee_role VALUES(1,2,'CLERK');",
        "INSERT INTO employee_role VALUES(1,1,'UNKNOWN');",
        "INSERT INTO employee_store VALUES(1,1,2);",
        "INSERT INTO employee_store VALUES(2,1,2);",
        "INSERT INTO role_permission VALUES(1,'CLERK','unknown:action');",
        "INSERT INTO employee_role VALUES(1,1,'CLERK');",
        "INSERT INTO employee_store VALUES(1,1,1);",
        "INSERT INTO rbac_role(code,name) VALUES('NO_TENANT','invalid');",
        "DELETE FROM employee WHERE id=1;",
    ]:
        sql(query, schema, False)
    assert sql("SELECT COUNT(*) FROM role_permission WHERE role_code='CLERK' AND permission_code IN ('order:refund','report:export');", schema) == "0"
    # A rerun must not restore revoked permissions or overwrite configured names.
    sql("DELETE FROM role_permission WHERE tenant_id=1 AND role_code='MANAGER' AND permission_code='dish:update'; UPDATE rbac_role SET name='custom' WHERE tenant_id=1 AND code='MANAGER';", schema)
    sql(migration, schema)
    assert sql("SELECT name FROM rbac_role WHERE tenant_id=1 AND code='MANAGER';", schema) == "custom"
    assert sql("SELECT COUNT(*) FROM role_permission WHERE tenant_id=1 AND role_code='MANAGER' AND permission_code='dish:update';", schema) == "0"
    assert sql("SELECT COUNT(*) FROM employee_role;", schema) == "1"
    # Seeding failure must roll back roles and permissions together.
    sql("INSERT INTO tenant VALUES(3); INSERT INTO rbac_permission VALUES(3,'dish:read','conflict');", schema)
    sql(migration, schema, False)
    assert sql("SELECT COUNT(*) FROM rbac_role WHERE tenant_id=3;", schema) == "0"
    assert sql("SELECT COUNT(*) FROM rbac_permission WHERE tenant_id=3;", schema) == "1"
    sql("DELETE FROM rbac_permission WHERE tenant_id=3;", schema)
    sql(migration, schema)
    assert sql("SELECT COUNT(*) FROM rbac_role WHERE tenant_id=3;", schema) == "3"
    sql("ALTER TABLE employee_store ADD KEY ix_test_employee(tenant_id,employee_id); ALTER TABLE employee_store DROP PRIMARY KEY;", schema)
    sql(migration, schema, False)
    sql("ALTER TABLE employee_store ADD PRIMARY KEY(tenant_id,employee_id,store_id);", schema)
    sql("ALTER TABLE employee_store DROP FOREIGN KEY fk_es_store;", schema)
    sql(migration, schema, False)
    # The full initializer must create identical RBAC structures and be rerunnable.
    sql(f"DROP DATABASE `{schema}`;")
    owned = False
    schema = "rbac_verify_" + uuid.uuid4().hex
    sql(f"CREATE DATABASE `{schema}`;")
    owned = True
    initializer = (ROOT / "database/junzhou.sql").read_text(encoding="utf-8")
    initializer = re.sub(r"(?im)^\s*(?:CREATE DATABASE|USE)\b[^;]*;", "", initializer)
    sql(initializer, schema)
    sql(migration, schema)
    assert sql("SELECT COUNT(*) FROM rbac_role;", schema) == "3"
    assert sql("SELECT COUNT(*) FROM employee_role;", schema) == "0"
    print("PASS: templates, explicit grants, composite tenant constraints, duplicate rejection and non-escalating rerun")
finally:
    if owned:
        sql(f"DROP DATABASE `{schema}`;")

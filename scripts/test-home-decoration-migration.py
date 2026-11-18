"""Verify the homepage decoration migration in a disposable local MySQL schema."""
from pathlib import Path
import re
import runpy
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
connection = runpy.run_path(str(ROOT / "scripts/test-merchant-discovery-migration.py"))["connection"]
schema = "home_decoration_verify_" + uuid.uuid4().hex
assert re.fullmatch(r"home_decoration_verify_[a-f0-9]{32}", schema)


def sql(query, *, in_schema=True):
    args, env, password = connection()
    if in_schema:
        args.append("--database=" + schema)
    result = subprocess.run(args, input=query, encoding="utf-8", env=env, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.replace(password, "[redacted]") if password else result.stderr)
    return result.stdout.strip()


def verify():
    initial = (ROOT / "database/junzhou.sql").read_text(encoding="utf-8")
    initial = re.sub(r"(?im)^\s*(?:CREATE DATABASE|USE)\b[^;]*;", "", initial)
    migration = (ROOT / "database/migrations/20261118_01_home_decoration.sql").read_text(encoding="utf-8")
    grant_count = "SELECT COUNT(*) FROM role_permission WHERE permission_code='decoration:manage';"
    admin_count = "SELECT COUNT(*) FROM rbac_role WHERE code='MERCHANT_ADMIN';"
    business = "SELECT COUNT(*) FROM employee; SELECT COUNT(*), SUM(amount) FROM orders;"

    sql(f"CREATE DATABASE `{schema}` CHARACTER SET utf8mb4;", in_schema=False)
    try:
        sql(initial)
        before = sql(business)
        assert int(sql(grant_count)) == int(sql(admin_count)) > 0
        sql("DROP TABLE tenant_home_decoration;")
        sql("DELETE FROM role_permission WHERE permission_code='decoration:manage';"
            "DELETE FROM rbac_permission WHERE code='decoration:manage';")
        sql(migration)
        grants = sql(grant_count)
        assert grants == sql(admin_count)
        sql("INSERT INTO tenant_home_decoration(tenant_id,draft_json,version)"
            " SELECT MIN(id),JSON_OBJECT('theme','TEAL'),1 FROM tenant;")
        sql(migration)
        assert grants == sql(grant_count)
        assert sql("SELECT JSON_UNQUOTE(JSON_EXTRACT(draft_json,'$.theme')),version"
                   " FROM tenant_home_decoration;") == "TEAL\t1"
        assert before == sql(business)
        assert sql("SELECT COUNT(*) FROM role_permission WHERE permission_code='decoration:manage'"
                   " AND role_code<>'MERCHANT_ADMIN';") == "0"
        print("PASS: fresh schema, existing administrator grants, repeat migration, unchanged business data")
    finally:
        sql(f"DROP DATABASE `{schema}`;", in_schema=False)


if __name__ == "__main__":
    verify()

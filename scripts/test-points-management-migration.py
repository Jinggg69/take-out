"""Verify points management grants using an isolated local MySQL schema."""
from pathlib import Path
import re
import runpy
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
connection = runpy.run_path(str(ROOT / "scripts/test-merchant-discovery-migration.py"))["connection"]
schema = "points_management_verify_" + uuid.uuid4().hex


def sql(query, *, in_schema=True):
    args, env, password = connection()
    if in_schema:
        args.append("--database=" + schema)
    result = subprocess.run(args, input=query, encoding="utf-8", env=env, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.replace(password, "[redacted]") if password else result.stderr)
    return result.stdout.strip()


def verify():
    assert re.fullmatch(r"points_management_verify_[a-f0-9]{32}", schema)
    initial = (ROOT / "database/junzhou.sql").read_text(encoding="utf-8")
    initial = re.sub(r"(?im)^\s*(?:CREATE DATABASE|USE)\b[^;]*;", "", initial)
    migration = (ROOT / "database/migrations/20261118_02_points_management.sql").read_text(encoding="utf-8")
    permission_list = "('member:point:batch','member:point:export')"
    grants = f"SELECT tenant_id,role_code,permission_code FROM role_permission WHERE permission_code IN {permission_list} ORDER BY 1,2,3;"
    business_data = "SELECT COUNT(*) FROM employee; SELECT COUNT(*),SUM(amount) FROM orders; SELECT COUNT(*) FROM member_asset_ledger;"
    sql(f"CREATE DATABASE `{schema}` CHARACTER SET utf8mb4;", in_schema=False)
    try:
        sql(initial)
        boot_grants = sql(grants)
        merchant_admin_count = int(sql("SELECT COUNT(*) FROM rbac_role WHERE code='MERCHANT_ADMIN';"))
        assert merchant_admin_count > 0 and len(boot_grants.splitlines()) == merchant_admin_count * 2
        assert all(line.split("\t")[1] == "MERCHANT_ADMIN" for line in boot_grants.splitlines())
        before = sql(business_data)
        sql(f"DELETE FROM role_permission WHERE permission_code IN {permission_list};"
            f"DELETE FROM rbac_permission WHERE code IN {permission_list};")
        sql(migration)
        assert sql(grants) == boot_grants
        sql(migration)
        assert sql(grants) == boot_grants and sql(business_data) == before
        print("PASS: fresh grants, repeated upgrade, existing business data, merchant administrator scope")
    finally:
        sql(f"DROP DATABASE `{schema}`;", in_schema=False)


if __name__ == "__main__":
    verify()

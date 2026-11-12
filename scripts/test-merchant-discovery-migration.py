"""Verify merchant discovery migration using owned disposable localhost schemas.

Never selects a configured application database. Credentials are read from the
local development config and passed only through MYSQL_PWD, never printed.
"""
import os
import re
import shutil
import subprocess
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMA_PATTERN = r"merchant_discovery_verify_[a-f0-9]{32}"
OWNED = set()


def connection():
    config = (ROOT / "backend/junzhou-take-out/junzhou-server/src/main/resources/application-dev.yml").read_text(encoding="utf-8")
    block = re.search(r"(?ms)^  datasource:\s*\n(.*?)(?=^  \S|\Z)", config)
    if not block:
        raise RuntimeError("Local datasource settings were not found")
    values = {}
    for key in ("host", "port", "username", "password"):
        match = re.search(r"(?m)^    " + key + r":\s*(.*?)\s*$", block.group(1))
        if not match:
            raise RuntimeError("Required local datasource setting is missing")
        values[key] = match.group(1).strip("\"'")
    if values["host"] not in ("localhost", "127.0.0.1") or not values["port"].isdigit() or not 1 <= int(values["port"]) <= 65535:
        raise RuntimeError("Only local MySQL is permitted")
    binary = shutil.which("mysql") or str(Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "MySQL/MySQL Server 8.4/bin/mysql.exe")
    if not Path(binary).is_file():
        raise RuntimeError("Local mysql executable was not found")
    env = os.environ.copy()
    env["MYSQL_PWD"] = values["password"]
    # Prevent an inherited defaults/login file from changing connection options.
    args = [binary, "--no-defaults", "--no-login-paths", "--protocol=tcp", "--host=127.0.0.1",
            "--port=" + values["port"], "--user=" + values["username"],
            "--batch", "--skip-column-names", "--default-character-set=utf8mb4"]
    return args, env, values["password"]


def run(query, database=None):
    args, env, password = connection()
    if database is not None:
        if database not in OWNED or not re.fullmatch(SCHEMA_PATTERN, database):
            raise RuntimeError("Refusing a database not created by this test")
        args.append("--database=" + database)
    result = subprocess.run(args, input="SET SESSION sql_mode='STRICT_ALL_TABLES';\n" + query,
                            text=True, encoding="utf-8", env=env, capture_output=True)
    # Never include credentials in a failure, even if mysql unexpectedly echoes them.
    stderr = result.stderr.replace(password, "[redacted]") if password else result.stderr
    return result.returncode, result.stdout.strip(), stderr


def sql(query, database, succeeds=True, message=None):
    if database not in OWNED:
        raise RuntimeError("Queries require an owned disposable database")
    # SQL supplied by fixtures/files must not switch or manage databases or source files.
    if re.search(r"(?im)(?:^|;)\s*(?:USE\b|SOURCE\b|SYSTEM\b|\\[A-Za-z!.])|\b(?:CREATE|DROP|ALTER)\s+(?:DATABASE|SCHEMA)\b", query):
        raise RuntimeError("Unsafe database-switching statement in test input")
    for match in re.finditer(r"(?i)\b(?:FROM|JOIN|INTO|UPDATE|TABLE|REFERENCES)\s+`?([A-Za-z_][A-Za-z0-9_]*)`?\s*\.", query):
        if match.group(1).lower() != "information_schema":
            raise RuntimeError("Refusing an external schema-qualified table reference")
    code, output, error = run(query, database)
    if (code == 0) != succeeds:
        raise AssertionError("Unexpected MySQL result: " + error)
    if message is not None and message not in error:
        raise AssertionError("Expected migration rejection was absent: " + error)
    return output


def database():
    name = "merchant_discovery_verify_" + uuid.uuid4().hex
    if not re.fullmatch(SCHEMA_PATTERN, name):
        raise RuntimeError("Invalid disposable database name")
    code, _, error = run(f"CREATE DATABASE `{name}` CHARACTER SET utf8mb4;")
    if code:
        raise AssertionError("Could not create disposable database: " + error)
    OWNED.add(name)
    return name


def cleanup():
    failures = []
    for name in list(OWNED):
        if not re.fullmatch(SCHEMA_PATTERN, name):
            raise RuntimeError("Refusing cleanup outside the owned test namespace")
        code, _, error = run(f"DROP DATABASE `{name}`;")
        if code:
            failures.append(error)
        else:
            OWNED.remove(name)
    if failures:
        raise RuntimeError("Disposable database cleanup failed: " + "; ".join(failures))


def seed(db, incompatible=False):
    logo = ",logo_url VARCHAR(32) NOT NULL DEFAULT ''" if incompatible else ""
    sql("CREATE TABLE tenant(id BIGINT PRIMARY KEY,name VARCHAR(100),status INT" + logo + ") ENGINE=InnoDB;"
        "CREATE TABLE store(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,name VARCHAR(100),status INT,"
        "UNIQUE KEY(tenant_id,id),FOREIGN KEY(tenant_id) REFERENCES tenant(id)) ENGINE=InnoDB;"
        "CREATE TABLE store_settings(tenant_id BIGINT NOT NULL,store_id BIGINT NOT NULL,version BIGINT NOT NULL,"
        "address VARCHAR(255),delivery_fee DECIMAL(10,2),PRIMARY KEY(tenant_id,store_id),"
        "FOREIGN KEY(tenant_id,store_id) REFERENCES store(tenant_id,id)) ENGINE=InnoDB;"
        "INSERT INTO tenant(id,name,status) VALUES(1,'merchant-a',1),(2,'merchant-b',1);"
        "INSERT INTO store(id,tenant_id,name,status) VALUES(11,1,'a-one',1),(12,1,'a-two',0),(21,2,'b-one',1);"
        "INSERT INTO store_settings(tenant_id,store_id,version,address,delivery_fee) VALUES"
        "(1,11,7,'a-address',6.50),(1,12,9,'a-disabled',0.00),(2,21,3,'b-address',10.00);", db)


def original_cells(db):
    return sql("SELECT id,name,status FROM tenant ORDER BY id;"
               "SELECT id,tenant_id,name,status FROM store ORDER BY id;"
               "SELECT tenant_id,store_id,version,address,delivery_fee FROM store_settings ORDER BY tenant_id,store_id;", db)


def check_definitions(db):
    actual = sql("SELECT table_name,column_name,data_type,character_maximum_length,is_nullable,"
                 "IF(column_default IS NULL,'NO_DEFAULT',column_default) FROM information_schema.columns "
                 "WHERE table_schema=DATABASE() AND ((table_name='tenant' AND column_name IN ('logo_url','introduction')) "
                 "OR (table_name='store_settings' AND column_name='contact_phone')) ORDER BY table_name,column_name;", db)
    assert actual == ("store_settings\tcontact_phone\tvarchar\t32\tYES\tNO_DEFAULT\n"
                      "tenant\tintroduction\tvarchar\t500\tYES\tNO_DEFAULT\n"
                      "tenant\tlogo_url\tvarchar\t512\tYES\tNO_DEFAULT"), actual


def main():
    migration = (ROOT / "database/migrations/20261112_01_merchant_discovery.sql").read_text(encoding="utf-8")
    db = database()
    seed(db)
    before = original_cells(db)
    sql(migration, db)
    check_definitions(db)
    after = original_cells(db)
    assert after == before, "Synthetic fixture changed: before=" + repr(before) + "; after=" + repr(after)
    assert sql("SELECT COUNT(*) FROM tenant WHERE logo_url IS NULL AND introduction IS NULL;"
               "SELECT COUNT(*) FROM store_settings WHERE contact_phone IS NULL;", db) == "2\n3"
    print("PASS: first upgrade adds correctly typed nullable fields and preserves original cells")

    sql("UPDATE tenant SET logo_url='https://cdn.example.com/public/logo.png',introduction='kept introduction' WHERE id=1;"
        "UPDATE store_settings SET contact_phone='0719-1234567' WHERE tenant_id=1 AND store_id=11;", db)
    filled = sql("SELECT * FROM tenant ORDER BY id; SELECT * FROM store ORDER BY id;"
                 "SELECT * FROM store_settings ORDER BY tenant_id,store_id;", db)
    sql(migration, db)
    assert sql("SELECT * FROM tenant ORDER BY id; SELECT * FROM store ORDER BY id;"
               "SELECT * FROM store_settings ORDER BY tenant_id,store_id;", db) == filled
    assert original_cells(db) == before
    print("PASS: rerun preserves logo, introduction, phone, config versions and tenant/store ownership")

    conflict = database()
    seed(conflict, incompatible=True)
    before_conflict = original_cells(conflict)
    sql(migration, conflict, succeeds=False, message="Discovery column definitions differ")
    assert original_cells(conflict) == before_conflict
    assert sql("SELECT character_maximum_length,is_nullable FROM information_schema.columns "
               "WHERE table_schema=DATABASE() AND table_name='tenant' AND column_name='logo_url';", conflict) == "32\tNO"
    print("PASS: incompatible pre-existing column is rejected without rewriting original data")

    fresh = database()
    init = (ROOT / "database/junzhou.sql").read_text(encoding="utf-8")
    # Remove only the known initializer's own schema selection/creation. All
    # remaining text is safety-checked before execution in our owned database.
    init, removed = re.subn(r"(?im)^\s*(?:CREATE DATABASE\b[^;]*;|USE\b[^;]*;)", "", init)
    if removed != 2:
        raise RuntimeError("Initializer database preamble changed; review before executing")
    sql(init, fresh)
    sql(migration, fresh)
    check_definitions(fresh)
    sql(migration, fresh)
    print("PASS: full fresh initializer followed by discovery migration and rerun")


if __name__ == "__main__":
    try:
        main()
    finally:
        cleanup()

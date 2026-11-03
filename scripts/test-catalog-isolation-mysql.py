"""Run real catalog MyBatis SQL only in a randomly named disposable local MySQL schema.
Reads local development credentials in-process, never sends them as CLI arguments.
Requires local MySQL with CREATE/DROP DATABASE privileges and the bundled build tools.
"""
import os
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
config = ROOT / "backend/junzhou-take-out/junzhou-server/src/main/resources/application-dev.yml"
text = config.read_text(encoding="utf-8")
block = re.search(r"(?ms)^  datasource:\s*\n(.*?)(?=^  \S|\Z)", text).group(1)
values = {}
for key in ("host", "port", "username", "password"):
    match = re.search(r"(?m)^    " + key + r":\s*(.*?)\s*$", block)
    if not match:
        raise RuntimeError("Missing local datasource configuration key: " + key)
    values[key] = match.group(1).strip("\"'")
if values["host"] not in ("localhost", "127.0.0.1") or not values["port"].isdigit():
    raise RuntimeError("This test only permits a local MySQL server")
env = os.environ.copy()
env.update(CATALOG_MYSQL_TEST="true", CATALOG_MYSQL_PORT=values["port"],
           CATALOG_MYSQL_USER=values["username"], CATALOG_MYSQL_PASSWORD=values["password"])
log = ROOT / "local-reports/catalog-mysql.log"
log.parent.mkdir(exist_ok=True)
with log.open("w", encoding="utf-8") as output:
    result = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
        str(ROOT / "scripts/backend.ps1"), "-Goal", "test", "-Offline", "-Test", "CatalogMySqlIsolationTest"],
        cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
print(("PASS" if result.returncode == 0 else "FAIL") + ": real MyBatis catalog isolation on temporary MySQL schema; log=" + str(log))
raise SystemExit(result.returncode)

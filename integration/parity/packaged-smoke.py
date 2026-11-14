"""Repeat a previously prepared offline production-client fixture with a new mod jar.

The fixture must be an isolated native TaCZ QA directory, never a player profile.
Cached libraries are reused read-only. Worlds, logs and mods go into a fresh output.
"""
import argparse
import shutil
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--fixture", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--mod", type=Path, required=True)
parser.add_argument("--java", type=Path, required=True)
args = parser.parse_args()
fixture, output = args.fixture.resolve(), args.output.resolve()
launch = (fixture / "launch.args").read_text(encoding="utf-8")
if "-Dtimestop.nativeTaczClientQa=true" not in launch:
    parser.error("The source must be an isolated native TaCZ QA fixture")
output.mkdir(parents=True, exist_ok=False)
(output / "mods").mkdir()
shutil.copy2(args.mod, output / "mods")
for item in (fixture / "mods").glob("*.jar"):
    if not item.name.startswith("timestop-"):
        shutil.copy2(item, output / "mods")
for name in ["natives", "tacz/uts_native_qa"]:
    if (fixture / name).exists():
        shutil.copytree(fixture / name, output / name)
for name in ["1.21.1.jar", "options.txt"]:
    shutil.copy2(fixture / name, output / name)
launch = launch.replace(str(fixture).replace("\\", "\\\\"), str(output).replace("\\", "\\\\"))
(output / "launch.args").write_text(launch, encoding="utf-8")
with (output / "packaged-client.log").open("w", encoding="utf-8") as stream:
    process = subprocess.Popen([str(args.java), "@" + str(output / "launch.args")], cwd=output,
                               stdout=stream, stderr=subprocess.STDOUT)
    try:
        code = process.wait(timeout=240)
    except subprocess.TimeoutExpired:
        process.kill()
        code = process.wait()
body = (output / "packaged-client.log").read_text(encoding="utf-8", errors="replace")
marker = "NATIVE_TACZ_ABSENT_CLIENT_PASS" if "-Dtimestop.nativeTaczAbsent=true" in launch else "NATIVE_TACZ_CLIENT_PASS"
passed = code == 0 and marker in body and "NATIVE_TACZ_CLIENT_FAIL" not in body
print(f"Packaged client: {'PASS' if passed else 'FAIL'}; exit={code}; log={output / 'packaged-client.log'}")
raise SystemExit(0 if passed else 1)

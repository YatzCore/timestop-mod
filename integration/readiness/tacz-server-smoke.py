"""Production JAR smoke/GameTests in a separately installed, disposable server."""
import argparse
import shutil
import subprocess
import threading
import time
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("loader", choices=["fabric", "forge", "neoforge"])
p.add_argument("server", type=Path, help="Disposable server installed with the selected loader")
p.add_argument("--java", required=True, type=Path)
p.add_argument("--mod", required=True, type=Path)
p.add_argument("--qa", type=Path, help="QA-only JAR; enables native GameTests")
p.add_argument("--tacz", type=Path)
p.add_argument("--fabric-api", type=Path)
p.add_argument("--config-api", type=Path)
p.add_argument("--gametest-api", type=Path, help="Fabric's standalone GameTest module, for QA only")
p.add_argument("--keep-running", action="store_true", help="Leave server running for connected client checks")
a = p.parse_args()
if a.loader == "forge" and a.qa:
    p.error("Forge exposes GameTest launch targets only in development; run its Gradle native suite and omit --qa for the production smoke")
root = a.server.resolve()
if not (root / "run.bat").exists() and not (root / "fabric-server.jar").exists():
    p.error("Install a disposable server first; do not point this at a playing world")
mods = root / "mods"
mods.mkdir(exist_ok=True)
if any(mods.iterdir()):
    p.error("Use an empty disposable mods directory, or remove only your prior QA copies first")
artifacts=[a.mod,a.qa,a.tacz]
if a.loader=="fabric":
    artifacts += [a.fabric_api,a.config_api,a.gametest_api]
for artifact in artifacts:
    if artifact:
        shutil.copy2(artifact, mods)
if a.qa:
    if not a.tacz:
        p.error("Native GameTests require TaCZ")
    shutil.copytree(Path(__file__).parent / "tacz-pack", root / "tacz/uts_native_qa", dirs_exist_ok=True)
(root / "eula.txt").write_text("eula=true\n")
(root / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=25587\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=120000\nlevel-name=qa-fresh-" + str(time.time_ns()) + "\n")
cmd = [str(a.java.resolve()), "-Xmx3G"]
if a.qa:
    cmd += ["-Dtimestop.nativeTaczPackagedTests=true"]
    if a.loader == "fabric":
        cmd += ["-Dfabric-api.gametest", "-Dfabric-api.gametest.test-mod-id=timestop_tacz_tests"]
    elif a.loader == "neoforge":
        cmd += ["-Dneoforge.enableGameTest=true", "-Dneoforge.gameTestServer=true", "-Dneoforge.enabledGameTestNamespaces=timestop"]
    else:
        cmd += ["-Dforge.enabledGameTestNamespaces=timestop"]
if a.loader == "fabric":
    cmd += ["-jar", "fabric-server.jar", "nogui"]
elif a.loader == "forge":
    cmd += ["-jar", "forge-1.21.1-52.1.16-shim.jar", "--nogui"]
else:
    cmd += ["@libraries/net/neoforged/neoforge/21.1.233/win_args.txt", "--nogui"]
profile = "native" if a.qa else ("installed" if a.tacz else "absent")
log = root / ("packaged-" + profile + ".log")
process = subprocess.Popen(cmd, cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
timer = threading.Timer(240, process.kill)
if not a.keep_running:
    timer.start()
lines = []
with log.open("w", encoding="utf-8") as output:
    for line in process.stdout:
        output.write(line); output.flush(); lines.append(line)
        if "Done (" in line and not a.qa and not a.keep_running:
            process.stdin.write("list\nstop\n"); process.stdin.flush()
code = process.wait()
timer.cancel()
body = "".join(lines)
passed = code == 0 and ("All 30 required tests passed" in body if a.qa else "Done (" in body)
print(f"{a.loader} packaged {profile}: {'PASS' if passed else 'FAIL'}; exit={code}; log={log}")
raise SystemExit(0 if passed else 1)

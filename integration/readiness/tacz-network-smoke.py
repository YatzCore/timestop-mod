"""Run two offline production clients through a disposable LAN/reconnect check.

Pass the same artifact/profile options as tacz-client-smoke.py after loader/output.
The QA-only host publishes port 25589 with authentication disabled; stop it after QA.
"""
import argparse
import concurrent.futures
import subprocess
import sys
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument("loader", choices=["fabric", "forge", "neoforge"])
p.add_argument("output", type=Path)
a, client_args = p.parse_known_args()
root = a.output.resolve()
shared = root / "signals"
if shared.exists():
    p.error("Use a fresh output folder; signal files must not survive an earlier test")
if "--tacz" not in client_args:
    p.error("The two-client native check requires --tacz")

def run(role):
    command = [sys.executable, str(Path(__file__).with_name("tacz-client-smoke.py")), a.loader, str(root / role)]
    command += client_args + ["--username", "TaCZ" + role.title(),
        "--property", "timestop.nativeTaczNetworkRole=" + role,
        "--property", "timestop.nativeTaczNetworkDir=" + str(shared)]
    return subprocess.run(command).returncode

with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    codes = list(pool.map(run, ["host", "observer"]))
passed = codes == [0, 0]
print(f"{a.loader} two-client native/reconnect: {'PASS' if passed else 'FAIL'}; logs={root}")
raise SystemExit(0 if passed else 1)

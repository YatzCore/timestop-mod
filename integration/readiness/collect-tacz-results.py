"""Collect verified candidate artifacts and QA evidence without distributing dependencies."""
import argparse
import hashlib
import io
import json
import shutil
import subprocess
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from xml.etree import ElementTree

p = argparse.ArgumentParser(description=__doc__)
p.add_argument("--qa", required=True, type=Path)
p.add_argument("--baseline", required=True, type=Path)
p.add_argument("--deps", required=True, type=Path)
p.add_argument("--baseline-tacz", required=True, type=Path)
p.add_argument("--output", type=Path)
a = p.parse_args()
source = Path(__file__).resolve().parents[2]
output = a.output.resolve() if a.output else source / "build/tacz-candidates"
output.mkdir(parents=True, exist_ok=True)
logs = output / "logs"
logs.mkdir(exist_ok=True)
screens = output / "screenshots"
screens.mkdir(exist_ok=True)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def evidence(path, name, marker, forbidden=None):
    body = path.read_text(encoding="utf-8", errors="replace")
    if marker not in body or forbidden and forbidden in body:
        raise RuntimeError(f"Unverified {name}: {path}")
    shutil.copy2(path, logs / name)
    return "logs/" + name

def audit(jar, prefix=""):
    for name in jar.namelist():
        if (name.startswith(("com/tacz/", "assets/tacz/", "data/tacz/")) or "/test/" in name
                or "timestop_qa" in name or "tacz_empty" in name or "native-tacz-qa" in name):
            raise RuntimeError("Dependency/fixture in release artifact: " + prefix + name)
        if name.endswith(".jar"):
            with zipfile.ZipFile(io.BytesIO(jar.read(name))) as nested:
                audit(nested, prefix + name + "!")

report = {"generated_at_utc": datetime.now(timezone.utc).isoformat(), "status": "candidate",
    "full_gameplay_acceptance": "pending", "minecraft": "1.21.1", "mod_version": "1.6.6", "loaders": {}}
target_names = {"fabric": "TACZ-Refabricated-1.21.1-0.7.1-forge1.1.8-hotfix2.jar",
    "forge": "tacz-1.21.1-1.1.6-hotfix-r1-all.jar", "neoforge": "tacz-neoforge-1.21.1-1.1.8-hotfix-r7.jar"}
for loader in ("fabric", "forge", "neoforge"):
    name = f"timestop-{loader}-1.21.1-1.6.6.jar"
    artifact = a.qa / loader / "build/libs" / name
    with zipfile.ZipFile(artifact) as jar:
        audit(jar)
        common = json.loads(jar.read("timestop-common.mixins.json"))
        optional = json.loads(jar.read({"fabric": "timestop-fabric-tacz.mixins.json",
            "forge": "timestop-forge.mixins.json", "neoforge": "timestop-neoforge.mixins.json"}[loader]))
        if not common["required"] or not optional["required"] or common["injectors"]["defaultRequire"] < 1 or optional["injectors"]["defaultRequire"] < 1:
            raise RuntimeError("Required mixins weakened: " + loader)
    shutil.copy2(artifact, output / name)
    item = {"artifact": name, "sha256": sha(artifact), "bytes": artifact.stat().st_size,
        "native_game_tests": {"passed": 30, "total": 30}, "artifact_audit": "pass",
        "target": {"name": target_names[loader], "sha256": sha(a.deps / target_names[loader])}, "evidence": []}
    item["presentation_parity"] = ("incomplete: built-in AK-47 equip/draw requests missing ar_akilo_drop and ar_akilo_raise_shoulder resources"
        if loader != "forge" else "pending full gameplay acceptance; no missing draw-resource warning observed")
    item["evidence"].append(evidence(a.qa / f"native-{loader}-results.log", f"native-{loader}.log", "All 30 required tests passed"))
    for profile in ("installed", "absent"):
        root = a.qa / f"production/{loader}-client-{profile}"
        marker = "NATIVE_TACZ_CLIENT_PASS" if profile == "installed" else "NATIVE_TACZ_ABSENT_CLIENT_PASS"
        item["evidence"].append(evidence(root / "packaged-client.log", f"{loader}-client-{profile}.log", marker, "NATIVE_TACZ_CLIENT_FAIL"))
        if sha(root / "mods" / name) != item["sha256"]:
            raise RuntimeError("Client used a different candidate: " + str(root))
        item["evidence"].append(evidence(a.qa / f"production/{loader}-server/packaged-{profile}.log",
            f"{loader}-server-{profile}.log", "Done (", "Failed to start the minecraft server"))
    network = a.qa / f"production/{loader}-network-release-candidate"
    for role in ("host", "observer"):
        item["evidence"].append(evidence(network / role / "packaged-client.log", f"{loader}-network-{role}.log",
            "NATIVE_TACZ_NETWORK_PASS", "NATIVE_TACZ_NETWORK_FAIL"))
        if sha(network / role / "mods" / name) != item["sha256"]:
            raise RuntimeError("Network check used a different candidate: " + loader)
    if loader != "forge":
        item["evidence"].append(evidence(a.qa / f"production/{loader}-server/packaged-native.log",
            f"{loader}-packaged-native.log", "All 30 required tests passed"))
        item["packaged_game_tests"] = {"passed": 30, "total": 30}
    else:
        item["packaged_game_tests"] = "No Forge production GameTest launch target; dev suite and production client firing checked"
    screenshot = a.qa / f"production/{loader}-client-installed/screenshots/native-tacz-feedback.png"
    shutil.copy2(screenshot, screens / f"{loader}-feedback.png")
    report["loaders"][loader] = item

report["regressions"] = {"fabric": {"passed": 118, "total": 118,
    "log": evidence(a.qa / "regression-results.log", "fabric-regressions.log", "All 118 required tests passed")},
    "neoforge_deceleration": {"passed": 3, "total": 3,
    "log": evidence(a.qa / "neoforge-deceleration-r7.log", "neoforge-deceleration.log", "All 3 required tests passed")}}
unit = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for file in (a.qa / "common/build/test-results/test").glob("TEST-*.xml"):
    tree = ElementTree.parse(file).getroot()
    for key in unit: unit[key] += int(tree.attrib.get(key, 0))
if not unit["tests"] or unit["failures"] or unit["errors"]: raise RuntimeError("Shared unit checks did not pass")
report["regressions"]["shared_unit_checks"] = unit
evidence(a.qa / "final-candidate-build.log", "candidate-build.log", "BUILD SUCCESSFUL")
evidence(a.qa / "final-neoforge-build.log", "neoforge-final-build.log", "BUILD SUCCESSFUL")
baseline_log = a.baseline / "baseline-native-results.log"
body = baseline_log.read_text(encoding="utf-8", errors="replace")
if "2 required tests failed" not in body or "nativevolatileimpactonce failed!" not in body or "ricochetafternativedamage failed!" not in body:
    raise RuntimeError("Reference outcomes changed; inspect and update the matrix")
shutil.copy2(baseline_log, logs / "baseline-native.log")
report["baseline"] = {"minecraft": "1.20.1", "passed": 28, "total": 30,
    "expected_failures": ["nativeVolatileImpactOnce", "ricochetAfterNativeDamage"],
    "tacz_sha256": sha(a.baseline_tacz),
    "client_log": evidence(a.baseline / "baseline-observed-results.log", "baseline-client.log", "NATIVE_TACZ_CLIENT_PASS", "NATIVE_TACZ_CLIENT_FAIL")}
shutil.copy2(a.baseline / "forge/build/native-tacz-client/screenshots/native-tacz-feedback.png", screens / "baseline-1201-feedback.png")
fingerprint = {}
for module in ("common", "fabric", "forge", "neoforge"):
    for file in (source / module / "src/main").rglob("*"):
        if file.is_file():
            relative = file.relative_to(source)
            if sha(file) != sha(a.qa / relative): raise RuntimeError("QA/source differ: " + str(relative))
            fingerprint[relative.as_posix()] = sha(file)
for name in ("build.gradle", "settings.gradle", "gradle.properties", "common/build.gradle", "fabric/build.gradle", "forge/build.gradle", "neoforge/build.gradle"):
    fingerprint[name] = sha(source / name)
report["source_head"] = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
report["source_tree"] = "Existing modified working tree; source-file-sha256.json records actual build inputs"
(output / "source-file-sha256.json").write_text(json.dumps(fingerprint, indent=2) + "\n")
(output / "verification.json").write_text(json.dumps(report, indent=2) + "\n")
(output / "SHA256SUMS.txt").write_text("".join(item["sha256"] + "  " + item["artifact"] + "\n" for item in report["loaders"].values()))
shutil.copy2(source / "integration/readiness/TACZ_PARITY.md", output / "TACZ_PARITY.md")
print(f"Collected three verified candidates; shared checks={unit['tests']}; output={output}")

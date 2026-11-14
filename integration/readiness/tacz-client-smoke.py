"""Launch an offline QA client with production mappings and candidate/QA JARs.

Accepts official vanilla and loader profile JSON, reusing installed libraries read-only.
All downloads, mods, worlds, logs, and native extraction stay in the supplied QA folder.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import urllib.request
import uuid
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("loader", choices=["fabric", "forge", "neoforge"])
p.add_argument("output", type=Path)
p.add_argument("--java", required=True, type=Path)
p.add_argument("--minecraft", required=True, type=Path)
p.add_argument("--vanilla-info", required=True, type=Path)
p.add_argument("--vanilla-jar", required=True, type=Path)
p.add_argument("--loader-info", required=True, type=Path)
p.add_argument("--mod", required=True, type=Path)
p.add_argument("--qa", required=True, type=Path)
p.add_argument("--tacz", type=Path)
p.add_argument("--fabric-api", type=Path)
p.add_argument("--config-api", type=Path)
p.add_argument("--gametest-api", type=Path)
p.add_argument("--extra-mod", type=Path, action="append", default=[])
p.add_argument("--username", default="NativeTaCZQA")
p.add_argument("--property", action="append", default=[])
a = p.parse_args()
if not re.fullmatch(r"[A-Za-z0-9_]{1,16}", a.username):
    p.error("Use a QA username of 1–16 letters, digits, or underscores")
root = a.output.resolve()
root.mkdir(parents=True, exist_ok=True)
mods = root / "mods"
mods.mkdir(exist_ok=True)
if any(mods.iterdir()):
    p.error("Use a fresh QA output folder with an empty mods directory")
artifacts=[a.mod,a.qa,a.tacz] + a.extra_mod
if a.loader=="fabric":
    artifacts += [a.fabric_api,a.config_api,a.gametest_api]
for artifact in artifacts:
    if artifact:
        shutil.copy2(artifact,mods)
vanilla=json.loads(a.vanilla_info.read_text())
loader=json.loads(a.loader_info.read_text())

def allowed(entry):
    rules=entry.get("rules",[])
    result=not rules
    for rule in rules:
        system=rule.get("os",{})
        matches=system.get("name","windows")=="windows" and ("arch" not in system or re.fullmatch(system["arch"],"amd64"))
        matches=matches and not any(rule.get("features",{}).values())
        if matches:
            result=rule["action"]=="allow"
    return result

def fetch(url,dest):
    if not dest.exists():
        dest.parent.mkdir(parents=True,exist_ok=True)
        request=urllib.request.Request(url,headers={"User-Agent":"Native-TaCZ-QA"})
        dest.write_bytes(urllib.request.urlopen(request,timeout=60).read())
    return dest

libraries={}
for entry in vanilla.get("libraries",[])+loader.get("libraries",[]):
    if not allowed(entry):
        continue
    coordinate=entry["name"]
    group,name,version,*classifier=coordinate.split(":")
    path=group.replace(".","/")+f"/{name}/{version}/{name}-{version}"+("-"+classifier[0] if classifier else "")+".jar"
    artifact=entry.get("downloads",{}).get("artifact",entry.get("artifact",{}))
    path=artifact.get("path",path).removeprefix("libraries/")
    existing=a.minecraft/"libraries"/path
    file=existing if existing.exists() else fetch(artifact.get("url",entry.get("url","https://libraries.minecraft.net/")+path),root/"libraries"/path)
    libraries[(group,name,tuple(classifier))]=file.resolve()
classpath=list(libraries.values())
mcjar=root/"1.21.1.jar"
shutil.copy2(a.vanilla_jar,mcjar)
classpath.append(mcjar)
natives=root/"natives"
natives.mkdir(exist_ok=True)
assets=a.minecraft/"assets"
asset_index=vanilla.get("assetIndex",{}).get("id","17")
values={
    "classpath":os.pathsep.join(map(str,classpath)),"classpath_separator":os.pathsep,
    "library_directory":str((a.minecraft/"libraries").resolve()),"natives_directory":str(natives),
    "auth_player_name":a.username,"auth_uuid":str(uuid.uuid3(uuid.NAMESPACE_DNS,"OfflinePlayer:"+a.username)),"auth_access_token":"0",
    "auth_xuid":"0","clientid":"0","user_type":"legacy","version_type":"release",
    "version_name":"1.21.1","game_directory":str(root),"assets_root":str(assets.resolve()),"assets_index_name":asset_index,
    "launcher_name":"NativeTaCZQA","launcher_version":"1","resolution_width":"1280","resolution_height":"720",
}
def arguments(entries):
    result=[]
    for entry in entries:
        if isinstance(entry,str):
            parts=[entry]
        elif allowed(entry):
            parts=entry.get("values",entry.get("value",[]))
            if isinstance(parts,str):parts=[parts]
        else:continue
        for part in parts:
            for key,value in values.items():part=part.replace("${"+key+"}",value)
            if "${" in part:raise ValueError("Unresolved launch argument: "+part)
            result.append(part)
    return result

# NeoForge's module path refers to its installed library directory. Required loader
# files missing there are resolved into our own cache and substituted individually.
jvm=arguments(vanilla.get("arguments",{}).get("jvm",[]))+arguments(loader.get("arguments",{}).get("jvm",[]))
for i,arg in enumerate(jvm):
    for file in classpath:
        relative=next((path for path in [str(file)] if "\\libraries\\" in path),None)
        if relative:
            suffix=relative.split("\\libraries\\",1)[1].replace("\\","/")
            arg=arg.replace(str((a.minecraft/"libraries").resolve())+"/"+suffix,str(file))
    jvm[i]=arg
loader_game=arguments(loader.get("arguments",{}).get("game",[]))
# A complete installed profile already includes the vanilla arguments. Official
# loader-only profiles contain just their additions. Avoid duplicate gameDir/etc.
game=loader_game if "--gameDir" in loader_game else arguments(vanilla.get("arguments",{}).get("game",[]))+loader_game
cmd=jvm+["-Xmx3G","-Dtimestop.nativeTaczClientQa=true","-Dtimestop.nativeTaczAbsent="+str(a.tacz is None).lower()]+["-D"+value for value in a.property]
cmd += [loader["mainClass"]]+game+["--width","1280","--height","720"]
argsfile=root/"launch.args"
argsfile.write_text("\n".join('"'+arg.replace("\\","\\\\").replace('"','\\"')+'"' for arg in cmd),encoding="utf-8")
log=root/"packaged-client.log"
with log.open("w",encoding="utf-8") as output:
    process=subprocess.Popen([str(a.java.resolve()),"@"+str(argsfile)],cwd=root,stdout=output,stderr=subprocess.STDOUT)
    try:code=process.wait(timeout=240)
    except subprocess.TimeoutExpired:process.kill();code=process.wait()
body=log.read_text(encoding="utf-8",errors="replace")
network=any(value.startswith("timestop.nativeTaczNetworkRole=") for value in a.property)
marker="NATIVE_TACZ_NETWORK_PASS" if network else ("NATIVE_TACZ_CLIENT_PASS" if a.tacz else "NATIVE_TACZ_ABSENT_CLIENT_PASS")
passed=code==0 and marker in body and "NATIVE_TACZ_CLIENT_FAIL" not in body and "NATIVE_TACZ_NETWORK_FAIL" not in body
print(f"{a.loader} packaged client: {'PASS' if passed else 'FAIL'}; exit={code}; log={log}")
raise SystemExit(0 if passed else 1)

"""Collect the completed isolated QA captures and create a browsable contact sheet.

Copies original screenshots without altering their pixels. Run after both client QA tasks.
"""
from pathlib import Path
import hashlib, html, json, shutil
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'art/observatory'
VIEWS=('exterior','dome-breach','observation-hall','workshop','library','archive','upper-walkway','night-exterior','night-hall')

def main():
    captured=[]
    for scene,run in (('natural','observatory-natural-run'),('inspection','observatory-visual-run')):
        source=ROOT/f'fabric/build/{run}'
        log=(source/'logs/latest.log').read_text(encoding='utf-8')
        assert 'OBSERVATORY_VISUAL_QA_COMPLETE' in log,f'{run} did not finish'
        if scene=='natural':
            for variant in (0,1):
                assert f'OBSERVATORY_NATURAL_AUDIT_PASS variant={variant} reloaded=true' in log
        destination=ART/'in-game'/scene
        destination.mkdir(parents=True,exist_ok=True)
        for variant in ('highland','forest'):
            for view in VIEWS:
                name=f'{variant}-{view}.png'; image=source/'screenshots'/name
                with Image.open(image) as screenshot: assert screenshot.size==(1600,900)
                shutil.copy2(image,destination/name)
                captured.append((scene,variant,view,(destination/name).relative_to(ART).as_posix()))
    cards=[]
    for scene,variant,view,path in captured:
        title=f'{variant.title()} / {view.replace("-"," ")} / {scene}'
        cards.append(f'<figure><a href="{path}"><img loading="lazy" src="{path}" alt="{html.escape(title)}"></a><figcaption>{html.escape(title)}</figcaption></figure>')
    (ART/'gallery.html').write_text('''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Ruined Observatory — 1.6.0</title><style>
body{background:#152022;color:#e8ebe3;font:16px system-ui;margin:0;padding:40px;max-width:1800px;margin:auto}
h1{font-size:38px;color:#dfc28b}p{line-height:1.6;max-width:850px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(450px,1fr));gap:24px}
figure{margin:0;background:#233134;border:1px solid #3b5555;border-radius:8px;overflow:hidden}img{width:100%;display:block}figcaption{padding:14px;color:#dbd1b9}
@media(max-width:550px){body{padding:16px}main{grid-template-columns:1fr}}a{color:#a3d5c4}</style>
<h1>Ruined Observatory</h1><p>Highland and forest variants · Minecraft 1.20.1 · Ultimate Time Stop 1.6.0</p>
<p>Original in-game captures. Natural scenes use seed 16310624: a meadow ridge and a dark forest. Inspection scenes isolate the architecture on level ground. Click an image to open the full 1600 × 900 capture.</p>
<p><a href="../../OBSERVATORY.md">Placement and editable sources</a> · <a href="QA.md">Validation report</a></p><main>'''+''.join(cards)+'</main></html>\n',encoding='utf-8')
    paths=[p for p in (ROOT/'common/src/main/resources/data/timestop/structures/ruined_observatory').glob('*.nbt')]
    paths.extend(ROOT/loader/f'build/libs/timestop-{loader}-1.20.1-1.6.0.jar' for loader in ('fabric','forge'))
    manifest={'version':'1.6.0','minecraft':'1.20.1','screenshots':len(captured),'sha256':{p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}}
    (ART/'release_manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print(f'Collected {len(captured)} original screenshots; gallery and release hashes written.')

if __name__=='__main__': main()

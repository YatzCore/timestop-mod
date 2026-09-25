"""Validate armillary exports, shell clearance, item bounds and packaged assets."""
from pathlib import Path
import base64, io, itertools, json, re, sys, zipfile
import numpy as np
from PIL import Image
from generate_pedestals import ROOT, ASSETS, ART, MODELS, elements
from armillary_geometry import PROFILES, assembled, transform, matrix

def read(path): return json.loads(path.read_text(encoding='utf-8-sig'))

def shell(part, center):
    a,b=np.array(part['a']),np.array(part['b'])
    vertices=transform(list(itertools.product(*zip(a,b))),part)
    far=np.linalg.norm(vertices-center,axis=1).max()
    rot=part['rotation']; pivot=np.array(rot['origin'])
    local=(center-pivot) @ matrix(rot['axis'],rot['angle'])+pivot
    nearest=np.clip(local,a,b)
    near=np.linalg.norm(nearest-local)
    return near,far

def validate(packaged=True):
    resources=ROOT/'common/src/main/resources'
    for tier,base in MODELS.items():
        profile=PROFILES[tier]; complete=assembled(tier); name=tier+'_pedestal'
        source=read(ART/f'{name}.bbmodel')
        model=read(ASSETS/f'models/block/{name}.json')
        item=read(ASSETS/f'models/item/{name}.json')
        assert model['elements']==elements(base), f'{name}: stale base'
        assert item['elements']==elements(complete), f'{name}: incomplete item'
        assert len(source['elements'])==len(complete)
        assert len(source['outliner'])==4 and all(g['children'] for g in source['outliner'])
        embedded=Image.open(io.BytesIO(base64.b64decode(source['textures'][0]['source'].split(',')[1])))
        assert embedded.tobytes()==Image.open(ASSETS/f'textures/block/{name}.png').tobytes()
        for cube,bb in zip(item['elements'],source['elements']):
            assert cube['from']==bb['from'] and cube['to']==bb['to']
            assert all(-16<=a<b<=32 for a,b in zip(cube['from'],cube['to'])), f'{name}: Java model bounds'
            if 'rotation' in cube:
                rot=cube['rotation']; assert rot['angle'] in (-45,-22.5,0,22.5,45)
                assert bb['origin']==rot['origin']
                assert bb['rotation']==[rot['angle'] if axis==rot['axis'] else 0 for axis in 'xyz']
            for face,data in cube['faces'].items():
                assert [v*4 for v in data['uv']]==bb['faces'][face]['uv']
                u0,v0,u1,v1=data['uv']; assert 0<=u0<u1<=16 and 0<=v0<v1<=16
        center=np.array(profile['center']); shells=[]
        for ring in profile['rings']:
            bounds=[shell(part,center) for part in ring]
            shells.append((min(b[0] for b in bounds),max(b[1] for b in bounds)))
        for outer,inner in zip(shells,shells[1:]):
            assert outer[0]>inner[1]+.05, f'{name}: intersecting ring shells {shells}'
        assert shells[0][1]<=profile['width']*8, f'{name}: exceeds animated width'
        assert center[1]+shells[0][1]<=profile['height']*16
        assert center[1]-shells[0][1]>=max(p['b'][1] for p in base)-.15, f'{name}: support collision'
        assert profile['watch']*8*2**.5+.16<shells[-1][0], f'{name}: watch clearance'
        for state in (name,name+'_active'):
            current=read(ASSETS/f'models/block/{state}.json')
            assert current['elements']==model['elements']
            assert Image.open(ASSETS/f'textures/block/{state}.png').size==(64,64)
            for texture in current['textures'].values():
                namespace,asset=texture.split(':'); assert (resources/f'assets/{namespace}/textures/{asset}.png').is_file()
        assert set(read(ASSETS/f'blockstates/{name}.json')['variants'])=={'active=false','active=true'}
        assert (resources/f'data/timestop/loot_tables/blocks/{name}.json').exists()
        print(f'PASS {tier}: 3 disjoint ring shells; watch and rotation bounds clear; base/item/Blockbench agree.')
    assert not (resources/'data/timestop/recipes/creative_pedestal.json').exists()
    shapes=(ROOT/'common/src/main/java/com/timestop/pedestal/PedestalShapes.java').read_text()
    for args in re.findall(r'Block.box\(([^)]+)\)',shapes):
        coords=[float(x) for x in args.split(',')]
        assert all(0<=a<b<=16 for a,b in zip(coords[:3],coords[3:])), 'Collision must stay in owning block'
    if not packaged: return
    for loader in ('fabric','forge'):
        jars=sorted((ROOT/loader/'build/libs').glob('timestop-'+loader+'-1.20.1-*.jar'),key=lambda p:p.stat().st_mtime)
        assert jars, f'{loader}: missing build'
        with zipfile.ZipFile(jars[-1]) as jar:
            names=set(jar.namelist())
            assert not any('PedestalTests' in p or 'PedestalVisualQa' in p for p in names)
            for cls in ('PedestalRenderer','ArmillaryGeometry','ArmillaryMesh','ArmillaryAnimation'):
                assert 'com/timestop/client/renderer/'+cls+'.class' in names
            for tier in MODELS:
                for prefix,suffix in [('models/block/','.json'),('models/item/','.json'),('textures/block/','.png'),('blockstates/','.json')]:
                    resource='assets/timestop/'+prefix+tier+'_pedestal'+suffix
                    assert jar.read(resource)==(resources/resource).read_bytes(), f'{loader}: stale {resource}'
        print(f'PASS: {jars[-1].name}: runtime assets/classes included; QA excluded.')
if __name__=='__main__': validate('--assets-only' not in sys.argv)

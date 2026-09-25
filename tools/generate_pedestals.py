"""Deterministic Minecraft/Blockbench export. Run with Python + Pillow + numpy.

Geometry is the single source of truth for models, editable projects, collision
shapes, and previews. Texture UVs retain one pixel per Minecraft model unit.
"""
from pathlib import Path
import base64, io, json, math, random, uuid
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'common/src/main/resources/assets/timestop'
DATA = ROOT / 'common/src/main/resources/data'
ART = ROOT / 'art/pedestals'
PALETTES = {
 'copper': ['#666861','#bc7549','#455952','#59a99c','#382b23','#f3b957'],
 'golden': ['#b0a795','#e0b44b','#534935','#f3d88b','#392f23','#ffe38a'],
 'diamond': ['#c1ccd1','#a3bfc7','#475969','#52d8df','#263e4a','#b1fbff'],
 'netherite': ['#39343c','#68575b','#28232e','#9c714f','#211b2b','#bb8afb'],
 'creative': ['#342e4b','#c69c53','#241e38','#a775d4','#191727','#ff91dc']}
from armillary_geometry import PROFILES, MODELS, assembled, transform, matrix

def write_json(path, value):
 path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')

def atlas(tier, active):
 rng=random.Random(tier); image=Image.new('RGB',(64,64)); draw=ImageDraw.Draw(image)
 colors=PALETTES[tier]
 for mat in range(8):
  color=colors[min(mat,5)]; base=tuple(bytes.fromhex(color[1:])); ox=(mat%4)*16; oy=(mat//4)*16
  for y in range(16):
   for x in range(16):
    shade=rng.choice([-7,-3,0,0,0,3,6])
    if mat in (1,3): shade += 10 if x==0 or y==0 else -15 if x==15 or y==15 else 0
    if mat==0 and (y%8==7 or (x+(8 if y>=8 else 0))%16==15): shade-=16
    if mat==4: shade=-6 if (x+y)%2 else 0
    c=tuple(max(0,min(255,v+shade)) for v in base)
    if mat==5: c=base if active else tuple(int(v*.32) for v in base)
    image.putpixel((ox+x,oy+y),c)
  if mat==1:
   for x,y in [(2,2),(13,2),(2,13),(13,13)]: draw.point((ox+x,oy+y),fill=tuple(min(255,v+30) for v in base))
  if mat==3 and tier in ('netherite','creative'):
   draw.line([(ox+3,oy+12),(ox+3,oy+5),(ox+8,oy+5),(ox+8,oy+2)],fill=PALETTES[tier][5])
 # The four-pixel clock dial is mapped at full native pixel density.
 for y in range(4):
  for x in range(4): image.putpixel((32+x,16+y),(237,224,180))
 image.putpixel((33,17),(65,46,30)); image.putpixel((33,18),(65,46,30)); image.putpixel((34,18),(65,46,30))
 return image

FACE_AXES={'north':(0,1),'south':(0,1),'west':(2,1),'east':(2,1),'up':(0,2),'down':(0,2)}
def elements(parts):
 result=[]
 for p in parts:
  size=[b-a for a,b in zip(p['a'],p['b'])]; mat=p['material']; x=(mat%4)*16; y=(mat//4)*16
  faces={}
  for face,(u,v) in FACE_AXES.items(): faces[face]={'uv':[x/4,y/4,(x+size[u])/4,(y+size[v])/4],'texture':'#atlas'}
  element={'name':p['name'],'from':p['a'],'to':p['b'],'faces':faces}
  if 'rotation' in p: element['rotation']=p['rotation']
  result.append(element)
 return result

DISPLAY={'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.625]*3},
 'ground':{'translation':[0,3,0],'scale':[.25]*3},'fixed':{'scale':[.5]*3},
 'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375]*3},
 'firstperson_righthand':{'rotation':[0,45,0],'scale':[.4]*3},'firstperson_lefthand':{'rotation':[0,225,0],'scale':[.4]*3}}

def preview(parts, texture, view, size=400, height=16):
 # Orthographic software rasterizer with UV sampling and a depth buffer.
 img=np.full((size,size,3),(28,32,39),dtype=np.uint8); depth=np.full((size,size),-1e9)
 eye=np.array({'front':(0,.04,-1),'side':(1,.04,0),'isometric':(1,.8,-1)}[view],float); eye/=np.linalg.norm(eye)
 right=np.cross(eye,[0,1,0]); right/=np.linalg.norm(right); up=np.cross(right,eye)
 tex=np.asarray(texture); scale=size/max(25,height*1.35)
 specs={'north':([0,0,-1],lambda a,b:[[a[0],b[1],a[2]],[b[0],b[1],a[2]],[b[0],a[1],a[2]],[a[0],a[1],a[2]]]),
 'south':([0,0,1],lambda a,b:[[b[0],b[1],b[2]],[a[0],b[1],b[2]],[a[0],a[1],b[2]],[b[0],a[1],b[2]]]),
 'east':([1,0,0],lambda a,b:[[b[0],b[1],a[2]],[b[0],b[1],b[2]],[b[0],a[1],b[2]],[b[0],a[1],a[2]]]),
 'west':([-1,0,0],lambda a,b:[[a[0],b[1],b[2]],[a[0],b[1],a[2]],[a[0],a[1],a[2]],[a[0],a[1],b[2]]]),
 'up':([0,1,0],lambda a,b:[[a[0],b[1],b[2]],[b[0],b[1],b[2]],[b[0],b[1],a[2]],[a[0],b[1],a[2]]]),
 'down':([0,-1,0],lambda a,b:[[a[0],a[1],a[2]],[b[0],a[1],a[2]],[b[0],a[1],b[2]],[a[0],a[1],b[2]]])}
 for p in parts:
  for face,(normal,coords) in specs.items():
   normal=np.array(normal,float)
   if 'rotation' in p: normal=matrix(p['rotation']['axis'],p['rotation']['angle'])@normal
   if np.dot(normal,eye)<=0: continue
   xyz=transform(coords(p['a'],p['b']),p)-[8,height/2,8]
   points=np.stack([xyz@right*scale+size/2, size/2-xyz@up*scale, xyz@eye],axis=1)
   u,v=FACE_AXES[face]; du=p['b'][u]-p['a'][u]; dv=p['b'][v]-p['a'][v]
   uv=np.array([[0,0],[du,0],[du,dv],[0,dv]],float)
   for ids in ((0,1,2),(0,2,3)):
    tri=points[list(ids)]; tuv=uv[list(ids)]; lo=np.maximum(0,np.floor(tri[:,:2].min(axis=0)).astype(int)); hi=np.minimum(size-1,np.ceil(tri[:,:2].max(axis=0)).astype(int))
    if (hi<lo).any(): continue
    yy,xx=np.mgrid[lo[1]:hi[1]+1,lo[0]:hi[0]+1]; q=np.stack([xx+.5,yy+.5],axis=-1)-tri[0,:2]
    mat=np.stack([tri[1,:2]-tri[0,:2],tri[2,:2]-tri[0,:2]],axis=1)
    if abs(np.linalg.det(mat))<1e-8: continue
    weights=q@np.linalg.inv(mat).T; w1=weights[:,:,0]; w2=weights[:,:,1]; w0=1-w1-w2
    z=w0*tri[0,2]+w1*tri[1,2]+w2*tri[2,2]; dst=depth[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
    mask=(w0>=0)&(w1>=0)&(w2>=0)&(z>=dst)
    tx=np.clip((w0*tuv[0,0]+w1*tuv[1,0]+w2*tuv[2,0]).astype(int),0,max(0,math.ceil(du)-1))+(p['material']%4)*16
    ty=np.clip((w0*tuv[0,1]+w1*tuv[1,1]+w2*tuv[2,1]).astype(int),0,max(0,math.ceil(dv)-1))+(p['material']//4)*16
    shade=1 if face=='up' else .84 if face in ('north','south') else .68
    pixels=(tex[ty,tx]*shade).astype(np.uint8); target=img[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
    target[mask]=pixels[mask]; dst[mask]=z[mask]
 return Image.fromarray(img)

def main():
 ART.mkdir(parents=True,exist_ok=True)
 shapes=['package com.timestop.pedestal;','import com.timestop.item.WatchTier;','import net.minecraft.world.level.block.Block;','import net.minecraft.world.phys.shapes.*;','/** Generated: fixed interaction volume; moving overhang has no collision. */','public final class PedestalShapes {']
 java=['package com.timestop.client.renderer;', 'import com.timestop.item.WatchTier;', '/** Generated by tools/generate_pedestals.py. Coordinates are model units. */', 'public final class ArmillaryGeometry {', 'private ArmillaryGeometry() {}', 'public record Profile(double height, double width, double centerY, float watchScale, String texture, float[][][] rings) {}', 'public static Profile forTier(WatchTier tier) { return MODELS[tier.ordinal()]; }']
 factories=[]
 sheet=Image.new('RGB',(1600,1050),'#1c2027'); draw=ImageDraw.Draw(sheet)
 try: font=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',22)
 except OSError: font=ImageFont.load_default()
 for column,(tier,parts) in enumerate(MODELS.items()):
  profile=PROFILES[tier]; complete=assembled(tier)
  name=tier+'_pedestal'; elems=elements(parts)
  display=json.loads(json.dumps(DISPLAY))
  for d in display.values():
   d['scale']=[v/max(1,profile['height']) for v in d.get('scale',[1]*3)]
   d['translation']=d.get('translation',[0,0,0])
   d['translation'][1]-=(profile['height']*16-16)/2*d['scale'][1]
  for active in (False,True):
   suffix='_active' if active else ''; tex=atlas(tier,active)
   tex.save(ASSETS/f'textures/block/{name}{suffix}.png')
   model={'credit':'Ultimate Time Stop armillary - generated from tools/armillary_geometry.py','parent':'minecraft:block/block','textures':{'atlas':f'timestop:block/{name}{suffix}','particle':f'timestop:block/{name}{suffix}'},'elements':elems,'display':display}
   write_json(ASSETS/f'models/block/{name}{suffix}.json',model)
  item=dict(model); item['textures']={'atlas':f'timestop:block/{name}','particle':f'timestop:block/{name}'}; item['elements']=elements(complete)
  write_json(ASSETS/f'models/item/{name}.json',item)
  write_json(ASSETS/f'blockstates/{name}.json',{'variants':{'active=false':{'model':f'timestop:block/{name}'},'active=true':{'model':f'timestop:block/{name}_active'}}})
  bb=[]; groups=[]; offset=0
  for gi,group in enumerate([parts]+profile['rings']):
   children=[]
   for e in elements(group):
    uid=str(uuid.uuid5(uuid.NAMESPACE_URL,f'timestop/{tier}/{offset}')); offset+=1; children.append(uid)
    cube={'name':e['name'],'type':'cube','uuid':uid,'from':e['from'],'to':e['to'],'origin':profile['center'],'box_uv':False,'faces':{f:{'uv':[v*4 for v in d['uv']],'texture':0} for f,d in e['faces'].items()}}
    if 'rotation' in e:
     rot=e['rotation']; cube['origin']=rot['origin']; cube['rotation']=[rot['angle'] if a==rot['axis'] else 0 for a in 'xyz']
    bb.append(cube)
   groups.append({'name':'Stationary base' if gi==0 else f'Ring {gi}', 'origin':profile['center'], 'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,f'{tier}/group/{gi}')), 'children':children})
  png=io.BytesIO(); atlas(tier,False).save(png,format='PNG')
  write_json(ART/f'{name}.bbmodel',{'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':name,'resolution':{'width':64,'height':64},'elements':bb,'outliner':groups,'textures':[{'id':'0','name':name+'.png','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,name)),'width':64,'height':64,'uv_width':64,'uv_height':64,'source':'data:image/png;base64,'+base64.b64encode(png.getvalue()).decode()}],'display':display})
  for row,view in enumerate(('front','side','isometric')):
   pic=preview(complete,atlas(tier,False),view,600,profile['height']*16); pic.save(ART/f'{name}_{view}.png')
   # Shared scale on the lineup makes tier size progression visible.
   small=preview(complete,atlas(tier,False),view,320,32)
   sheet.paste(small,(column*320,35+row*330))
  preview(complete,atlas(tier,True),'isometric',600,profile['height']*16).save(ART/f'{name}_active.png')
  draw.text((column*320+18,9),tier.upper(),font=font,fill=PALETTES[tier][5])
  enum='GILDED' if tier=='golden' else tier.upper()
  half=min(8,profile['width']*8)
  shapes.append(f'private static final VoxelShape {enum} = Block.box({8-half},0,{8-half},{8+half},{min(16,profile["height"]*16)},{8+half});')
  # Each array entry is a cuboid: min/max, rotation axis/angle, material, segment, pivot.
  refs=[]
  for ri,ring in enumerate(profile['rings']):
   method=f'{tier}Ring{ri}'; refs.append(method+'()'); rows=[]
   for part in ring:
    detail=0 if ' band ' in part['name'] else 2 if ' tick ' in part['name'] and part['material']!=5 else 1
    rot=part['rotation']; values=part['a']+part['b']+['xyz'.index(rot['axis']),rot['angle'],part['material'],part['segment']]+rot['origin']+[detail]
    rows.append('{'+','.join(f'{v:.6f}F' for v in values)+'}')
   java.append('private static float[][] '+method+'() { return new float[][] {\n'+',\n'.join(rows)+'}; }')
  factories.append(f'new Profile({profile["height"]},{profile["width"]},{profile["center"][1]/16},{profile["watch"]}F,"{tier}_pedestal", new float[][][] {{'+','.join(refs)+'})')
 shapes.append('public static VoxelShape forTier(WatchTier tier) { return switch(tier) {'+' '.join(f'case {t} -> {t};' for t in ('COPPER','GILDED','DIAMOND','NETHERITE','CREATIVE'))+'}; }\n}')
 (ROOT/'common/src/main/java/com/timestop/pedestal/PedestalShapes.java').write_text('\n'.join(shapes)+'\n',encoding='utf-8')
 java.append('private static final Profile[] MODELS = {'+','.join(factories)+'};\n}')
 (ROOT/'common/src/main/java/com/timestop/client/renderer/ArmillaryGeometry.java').write_text('\n'.join(java)+'\n',encoding='utf-8')
 sheet.save(ART/'pedestal_lineup.png')
 write_json(ART/'armillary_profiles.json',{t:{k:v for k,v in p.items() if k not in ('base','rings')} for t,p in PROFILES.items()})
 write_json(DATA/'minecraft/tags/blocks/mineable/pickaxe.json',{'replace':False,'values':[f'timestop:{t}_pedestal' for t in MODELS]})
 write_json(DATA/'timestop/recipes/copper_pedestal.json',{'type':'minecraft:crafting_shaped','pattern':['CCC',' W ','SSS'],'key':{'C':{'item':'minecraft:copper_ingot'},'W':{'item':'minecraft:clock'},'S':{'item':'minecraft:stone_bricks'}},'result':{'item':'timestop:copper_pedestal'}})
 for tier,prev,material in [('golden','copper','gold_ingot'),('diamond','golden','diamond')]:
  write_json(DATA/f'timestop/recipes/{tier}_pedestal.json',{'type':'minecraft:crafting_shaped','pattern':[' M ','MPM',' M '],'key':{'M':{'item':'minecraft:'+material},'P':{'item':f'timestop:{prev}_pedestal'}},'result':{'item':f'timestop:{tier}_pedestal'}})
 write_json(DATA/'timestop/recipes/netherite_pedestal.json',{'type':'minecraft:smithing_transform','template':{'item':'minecraft:netherite_upgrade_smithing_template'},'base':{'item':'timestop:diamond_pedestal'},'addition':{'item':'minecraft:netherite_ingot'},'result':{'item':'timestop:netherite_pedestal'}})
 langpath=ASSETS/'lang/en_us.json'; lang=json.loads(langpath.read_text(encoding='utf-8-sig'))
 lang.update({f'block.timestop.{t}_pedestal':t.title()+' Clockwork Pedestal' for t in MODELS})
 lang.update({'pedestal.timestop.tier_rejected':'This watch needs a higher-tier pedestal.','pedestal.timestop.radius':'Radius: %s blocks'})
 lang.update({f'pedestal.timestop.mode.{i}':s for i,s in enumerate(('Slow Motion','Fast Forward','Deceleration','Time Stop'))})
 lang.update({f'pedestal.timestop.status.{i}':s for i,s in enumerate(('No redstone power','Powered / waiting','Field active','Cycle power to rearm'))})
 write_json(langpath,lang)
 print('Exported 5 armillary assemblies, cached runtime geometry, 10 atlases, grouped Blockbench sources and previews.')

if __name__=='__main__': main()

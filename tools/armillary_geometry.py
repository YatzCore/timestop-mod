"""Shared armillary geometry, in Minecraft model units (16 per block)."""
import math
import numpy as np

TIERS = ('copper', 'golden', 'diamond', 'netherite', 'creative')
HEIGHTS = (.9, 1.15, 1.4, 1.75, 2.)
WIDTHS = (.8, 1., 1.2, 1.45, 1.65)
WATCH_SCALES = (.24, .30, .34, .40, .44)
PROFILES = {}

def box(parts, name, a, b, material=0, rotation=None, segment=-1):
    part = dict(name=name, a=list(a), b=list(b), material=material, segment=segment)
    if rotation:
        part['rotation'] = rotation
    parts.append(part)

def matrix(axis, angle):
    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    if axis == 'x': return np.array([[1,0,0],[0,c,-s],[0,s,c]])
    if axis == 'y': return np.array([[c,0,s],[0,1,0],[-s,0,c]])
    return np.array([[c,-s,0],[s,c,0],[0,0,1]])

def transform(points, part):
    if 'rotation' not in part: return np.asarray(points, float)
    rot = part['rotation']; origin = np.array(rot['origin'])
    return (np.asarray(points)-origin) @ matrix(rot['axis'], rot['angle']).T + origin

for tier, height, width, watch in zip(TIERS, HEIGHTS, WIDTHS, WATCH_SCALES):
    rank = TIERS.index(tier)
    radius = width*8
    center = [8., height*16-radius, 8.]
    # Narrow rectangular bands live in non-overlapping spherical shells.
    thickness = .42 + rank*.11
    band = .75 + rank*.12
    outer = (radius - thickness*.75 - .12)*math.cos(math.pi/16)
    base = []
    foot = min(width*16, 14.)
    lo, hi = 8-foot/2, 8+foot/2
    pedestal_top = height*16-width*16
    step = min(1.15, pedestal_top*.55)
    box(base, 'Octagonal foundation', [lo+1,0,lo], [hi-1,step,hi])
    box(base, 'Foundation sides', [lo,0,lo+1], [hi,step,hi-1])
    box(base, 'Metal plinth', [lo+1,step,lo+1], [hi-1,step+.3,hi-1], 1)
    if pedestal_top > step+.35:
        box(base, 'Fluted neck', [5.8,step+.3,5.8], [10.2,pedestal_top,10.2], 2)
        for x in (5.5,10.):
            box(base, 'Neck inlay', [x,step+.3,6.5], [x+.5,pedestal_top,9.5], 3)
    box(base, 'Lower bearing', [6.5,pedestal_top-.1,6.5], [9.5,pedestal_top+.25,9.5], 1)
    box(base, 'Power lamp', [7.2,.22,lo-.01], [8.8,min(step,.65),lo+.2], 5)
    if rank >= 1:
        box(base, 'Front dial rim', [6.6,.15,lo-.05], [9.4,step+.2,lo+.22], 1)
        box(base, 'Front dial', [7,.3,lo-.08], [9,step+.1,lo-.04], 6)
    rings=[]
    for ring, factor in enumerate((1., .78, .56)):
        parts=[]; r=outer*factor
        # In their rest pose the rings occupy XY, YZ and XZ respectively.
        u,v,axis = ((0,1,'z'),(1,2,'x'),(2,0,'y'))[ring]
        normal = 3-u-v
        for seg in range(16):
            angle=seg*22.5
            theta=math.radians(angle)
            midpoint=np.array(center); midpoint[u]+=r*math.cos(theta); midpoint[v]+=r*math.sin(theta)
            # Tangential boxes: fold quarter turns into dimensions for legal Java model rotations.
            folded=(angle+45)%90-45
            quarter=round((angle-folded)/90)%2
            tangent=2*r*math.tan(math.pi/16)+.035
            dims=np.zeros(3); dims[u]=tangent if quarter else thickness; dims[v]=thickness if quarter else tangent; dims[normal]=band
            rotation={'origin':midpoint.tolist(),'axis':axis,'angle':folded}
            box(parts, f'Ring {ring+1} band {seg:02}', midpoint-dims/2, midpoint+dims/2, 1 if ring!=1 else 3, rotation, seg)
            if seg % (2 if tier=='netherite' else 4) == 0:
                clasp=dims.copy(); clasp[u]=.45 if quarter else thickness*1.4; clasp[v]=thickness*1.4 if quarter else .45; clasp[normal]=band+.16
                box(parts, f'Ring {ring+1} riveted clasp {seg:02}', midpoint-clasp/2, midpoint+clasp/2, 2 if tier=='netherite' else 1, rotation, seg)
            # Narrow raised ticks on both faces are readable from either side.
            for side in (-1,1):
                clasp_height=.08 if seg % (2 if tier=='netherite' else 4)==0 else 0
                mark=midpoint.copy(); mark[normal]+=side*(band/2+clasp_height+.035)
                md=dims.copy(); md[u]=min(md[u], .23 if quarter else thickness*.75); md[v]=min(md[v],thickness*.75 if quarter else .23); md[normal]=.07
                box(parts, f'Ring {ring+1} tick {seg:02}', mark-md/2, mark+md/2, 5 if seg%4==0 else 2, rotation, seg)
            if tier=='creative' and ring==0 and seg%4==0:
                ornament=np.array(center); ornament[u]+=outer*.89*math.cos(theta); ornament[v]+=outer*.89*math.sin(theta)
                box(parts, 'Suspended crystal', ornament-.23, ornament+.23, 3, {'origin':ornament.tolist(),'axis':axis,'angle':45}, seg)
        rings.append(parts)
    PROFILES[tier] = dict(height=height,width=width,center=center,watch=watch,base=base,rings=rings)

MODELS = {tier: p['base'] for tier,p in PROFILES.items()}

def assembled(tier):
    p=PROFILES[tier]
    return p['base'] + [part for ring in p['rings'] for part in ring]

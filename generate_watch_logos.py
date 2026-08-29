import math
import shutil
from PIL import Image, ImageDraw

# 1. Source texture (16x16)
src = Image.open('src/main/resources/assets/timestop/textures/item/chronos_watch.png')
src.save('golden_watch_16x16.png')

# 2. Pixel Art Integer Scaling (Nearest Neighbor - 100% crisp Minecraft blocks)
# 512x512
p512 = src.resize((512, 512), resample=Image.Resampling.NEAREST)
p512.save('golden_watch_pixel_512.png')

# 1024x1024
p1024 = src.resize((1024, 1024), resample=Image.Resampling.NEAREST)
p1024.save('golden_watch_pixel_1024.png')

# 3. Centered Pixel Art (Bounding box balanced in canvas for logos/avatars)
bbox = src.getbbox() # (2, 0, 13, 14) -> 11x14
cropped = src.crop(bbox)

# Centered 512
c512 = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
scale_512 = 32
cw512 = cropped.resize((cropped.width * scale_512, cropped.height * scale_512), resample=Image.Resampling.NEAREST)
c512.paste(cw512, ((512 - cw512.width) // 2, (512 - cw512.height) // 2), cw512)
c512.save('golden_watch_centered_512.png')

# Centered 1024
c1024 = Image.new('RGBA', (1024, 1024), (0, 0, 0, 0))
scale_1024 = 64
cw1024 = cropped.resize((cropped.width * scale_1024, cropped.height * scale_1024), resample=Image.Resampling.NEAREST)
c1024.paste(cw1024, ((1024 - cw1024.width) // 2, (1024 - cw1024.height) // 2), cw1024)
c1024.save('golden_watch_centered_1024.png')

# 4. Programmatic High-Resolution Vector-Style Watch (Pure Code Math & Geometry, No AI)
SIZE = 4096
img_hd = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
draw = ImageDraw.Draw(img_hd)

CX = SIZE // 2
CY = int(SIZE * 0.58)
R_OUTER = int(SIZE * 0.35)
R_INNER = int(SIZE * 0.27)

BO = (50, 32, 8, 255)     # Dark Bronze Outline
GM = (212, 175, 55, 255)  # Gold Midtone
GH = (255, 235, 130, 255) # Gold Highlight
GS = (140, 100, 20, 255)  # Gold Shadow
GD = (95, 65, 12, 255)    # Gold Deep Shadow
WF = (250, 248, 238, 255) # White Enamel Dial Face
WS = (220, 215, 195, 255) # Dial Shading
CP = (20, 50, 80, 255)    # Clock Hand (Steel Blue)
CT = (60, 110, 150, 255)  # Clock Hand Highlight

# A. Top Chain Loop & Ring
LOOP_CY = int(SIZE * 0.16)
LOOP_R_OUT = int(SIZE * 0.085)
LOOP_R_IN = int(SIZE * 0.05)

# Outer loop dark border
draw.ellipse([CX - LOOP_R_OUT - 24, LOOP_CY - LOOP_R_OUT - 24, CX + LOOP_R_OUT + 24, LOOP_CY + LOOP_R_OUT + 24], fill=BO)
# Outer loop gold
draw.ellipse([CX - LOOP_R_OUT, LOOP_CY - LOOP_R_OUT, CX + LOOP_R_OUT, LOOP_CY + LOOP_R_OUT], fill=GM)
# Loop highlight top-left
draw.arc([CX - LOOP_R_OUT, LOOP_CY - LOOP_R_OUT, CX + LOOP_R_OUT, LOOP_CY + LOOP_R_OUT], start=180, end=315, fill=GH, width=40)
# Inner loop cutout
draw.ellipse([CX - LOOP_R_IN, LOOP_CY - LOOP_R_IN, CX + LOOP_R_IN, LOOP_CY + LOOP_R_IN], fill=BO)
draw.ellipse([CX - LOOP_R_IN + 20, LOOP_CY - LOOP_R_IN + 20, CX + LOOP_R_IN - 20, LOOP_CY + LOOP_R_IN - 20], fill=(0, 0, 0, 0))

# Stem connecting loop to body
STEM_TOP = LOOP_CY + LOOP_R_OUT - 40
STEM_BOT = CY - R_OUTER + 40
draw.rectangle([CX - 90, STEM_TOP, CX + 90, STEM_BOT], fill=BO)
draw.rectangle([CX - 70, STEM_TOP + 10, CX + 70, STEM_BOT], fill=GM)
draw.rectangle([CX - 70, STEM_TOP + 10, CX - 20, STEM_BOT], fill=GH)
draw.rectangle([CX + 20, STEM_TOP + 10, CX + 70, STEM_BOT], fill=GS)

# B. Main Watch Outer Body (Gold with bevel)
draw.ellipse([CX - R_OUTER - 32, CY - R_OUTER - 32, CX + R_OUTER + 32, CY + R_OUTER + 32], fill=BO)
draw.ellipse([CX - R_OUTER, CY - R_OUTER, CX + R_OUTER, CY + R_OUTER], fill=GM)

# Bevel shading
for i in range(20):
    r_cur = R_OUTER - i * 4
    draw.arc([CX - r_cur, CY - r_cur, CX + r_cur, CY + r_cur], start=135, end=315, fill=GH, width=6)
    draw.arc([CX - r_cur, CY - r_cur, CX + r_cur, CY + r_cur], start=315, end=135, fill=GS, width=6)

# C. Watch Dial (Enamel White Face)
draw.ellipse([CX - R_INNER - 24, CY - R_INNER - 24, CX + R_INNER + 24, CY + R_INNER + 24], fill=BO)
draw.ellipse([CX - R_INNER, CY - R_INNER, CX + R_INNER, CY + R_INNER], fill=WF)

# Dial gradient/shadow on bottom-right
draw.pieslice([CX - R_INNER, CY - R_INNER, CX + R_INNER, CY + R_INNER], start=0, end=135, fill=WS)
draw.ellipse([CX - R_INNER + 44, CY - R_INNER + 44, CX + R_INNER - 44, CY + R_INNER - 44], fill=WF)

# D. Hour & Minute Tick Marks
for h in range(12):
    angle = math.radians(h * 30)
    is_cardinal = (h % 3 == 0)
    tick_len = 80 if is_cardinal else 45
    tick_w = 28 if is_cardinal else 16
    color = BO if is_cardinal else GS
    
    x1 = CX + int((R_INNER - 35) * math.sin(angle))
    y1 = CY - int((R_INNER - 35) * math.cos(angle))
    x2 = CX + int((R_INNER - 35 - tick_len) * math.sin(angle))
    y2 = CY - int((R_INNER - 35 - tick_len) * math.cos(angle))
    draw.line([x1, y1, x2, y2], fill=color, width=tick_w)

# E. Clock Hands (12:15 - exactly matching the pixel texture layout!)
# Hour Hand: pointing 12 o'clock (straight up)
H_LEN = int(R_INNER * 0.65)
draw.line([CX, CY + 30, CX, CY - H_LEN], fill=BO, width=58)
draw.line([CX, CY + 20, CX, CY - H_LEN + 10], fill=CP, width=44)
draw.line([CX - 8, CY + 10, CX - 8, CY - H_LEN + 20], fill=CT, width=12)

# Minute Hand: pointing 3 o'clock (straight right)
M_LEN = int(R_INNER * 0.82)
draw.line([CX - 30, CY, CX + M_LEN, CY], fill=BO, width=46)
draw.line([CX - 20, CY, CX + M_LEN - 10, CY], fill=CP, width=32)
draw.line([CX - 10, CY - 6, CX + M_LEN - 15, CY - 6], fill=CT, width=10)

# Center Pinion
draw.ellipse([CX - 44, CY - 44, CX + 44, CY + 44], fill=BO)
draw.ellipse([CX - 30, CY - 30, CX + 30, CY + 30], fill=GM)
draw.ellipse([CX - 16, CY - 16, CX + 16, CY + 16], fill=GH)

# Downsample for ultra-smooth anti-aliased finish
img_hd_1024 = img_hd.resize((1024, 1024), resample=Image.Resampling.LANCZOS)
img_hd_1024.save('golden_watch_vector_1024.png')
img_hd_512 = img_hd.resize((512, 512), resample=Image.Resampling.LANCZOS)
img_hd_512.save('golden_watch_vector_512.png')

# Copy all to artifacts directory
artifacts_dir = 'artifacts'
for fname in [
    'golden_watch_16x16.png',
    'golden_watch_pixel_512.png',
    'golden_watch_pixel_1024.png',
    'golden_watch_centered_512.png',
    'golden_watch_centered_1024.png',
    'golden_watch_vector_512.png',
    'golden_watch_vector_1024.png'
]:
    shutil.copy(fname, artifacts_dir)

print('Successfully generated all watch assets!')

"""Authored, reproducible 45 x 45 ruined observatories across 6 variants. Origin is archive floor.

Coordinates: entrance floor y=8, archive floor y=0, north is decreasing z.
Air is explicit inside architecture; omitted cells preserve surrounding terrain.
Run with Python; Pillow is used only for the annotated floor plans.
"""
from pathlib import Path
import json, math
from structure_nbt import Byte, write

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'common/src/main/resources/data/timestop'
ART = ROOT / 'art/observatory'
SIZE = (45, 40, 45)

JOURNALS = {
 'workshop': ('A Lens Out of Time', [
     'The western stair has begun to sink. I have braced the gallery with oak, but the great lens still leans toward the breach in the dome.',
     'We used to keep the instruments in the towers. The last good watch is below the library now. Follow the broken shelves; the papers are not the only things we hid.']),
 'library': ('The Lower Archive', [
     'When the roof opened, we carried the charts downstairs. The wall behind the southern shelves was never as solid as it looked.',
     'There is another stair under the workshop. Mind the rubble at its mouth. Both ways lead to the reading room, eight blocks beneath the old entrance.']),
 'archive': ('The Last Observation', [
     'At midnight every hand agreed. The stars did not. We spent the next winter proving our clocks were wrong, and the winter after that proving they were not.',
     'The dome is beyond repair. I am leaving a working golden watch and an unmarked rune for whoever finds this room. Keep a record. Do not trust a single observation.'])
}

VARIANT_PROFILES = {
    'highland': {
        'biomes': ['windswept_hills', 'windswept_gravelly_hills', 'meadow', 'stony_peaks'],
        'wood': 'spruce',
        'carpet': 'white_carpet',
        'accent': 'polished_andesite',
        'chiseled': 'chiseled_stone_bricks',
        'salt': 16310624
    },
    'forest': {
        'biomes': ['forest', 'birch_forest', 'old_growth_birch_forest', 'dark_forest'],
        'wood': 'oak',
        'carpet': 'white_carpet',
        'accent': 'polished_andesite',
        'chiseled': 'chiseled_stone_bricks',
        'salt': 16310625
    },
    'cherry': {
        'biomes': ['cherry_grove'],
        'wood': 'cherry',
        'carpet': 'pink_carpet',
        'accent': 'calcite',
        'chiseled': 'chiseled_quartz_block',
        'salt': 16310626
    },
    'floral': {
        'biomes': ['flower_forest'],
        'wood': 'birch',
        'carpet': 'yellow_carpet',
        'accent': 'polished_andesite',
        'chiseled': 'chiseled_stone_bricks',
        'salt': 16310627
    },
    'windswept': {
        'biomes': ['windswept_forest'],
        'wood': 'dark_oak',
        'carpet': 'gray_carpet',
        'accent': 'polished_deepslate',
        'chiseled': 'chiseled_deepslate',
        'salt': 16310628
    },
    'acropolis': {
        'biomes': ['stony_peaks', 'jagged_peaks', 'frozen_peaks'],
        'wood': 'birch',
        'carpet': 'white_carpet',
        'accent': 'calcite',
        'chiseled': 'chiseled_quartz_block',
        'salt': 16310629
    }
}

def state(name, **props):
    return ('minecraft:' + name if ':' not in name else name, tuple(sorted((k, str(v).lower()) for k, v in props.items())))

AIR = state('air')
FOUNDATION = state('deepslate_bricks')

class Observatory:
    def __init__(self, variant):
        self.variant = variant
        self.profile = VARIANT_PROFILES[variant]
        self.wood_type = self.profile['wood']
        self.carpet_type = self.profile['carpet']
        self.accent_type = self.profile['accent']
        self.chiseled_type = self.profile['chiseled']
        self.blocks = {}
        self.nbt = {}
        self.routes = []

    def put(self, x, y, z, block, nbt=None):
        x, y, z = int(x), int(y), int(z)
        if not (0 <= x < 45 and 0 <= y < 40 and 0 <= z < 45):
            raise ValueError((x, y, z))
        self.blocks[x, y, z] = state(block) if isinstance(block, str) else block
        self.nbt.pop((x, y, z), None)
        if nbt is not None:
            self.nbt[x, y, z] = nbt

    def fill(self, x0, y0, z0, x1, y1, z1, block):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.put(x, y, z, block)

    def stone(self, x, y, z):
        value = (x * 71 + y * 29 + z * 43) % 37
        if self.variant == 'cherry':
            if (x + y + z) % 7 == 0:
                return 'calcite'
            if (x * 3 + z * 5) % 11 == 0:
                return 'polished_diorite'
            if value < 4:
                return 'cracked_stone_bricks'
            return 'stone_bricks'
        elif self.variant == 'floral':
            if (y < 14 or z < 14) and value < 15:
                return 'mossy_stone_bricks'
            if value < 4:
                return 'mossy_cobblestone'
            if value < 7:
                return 'andesite'
            return 'stone_bricks'
        elif self.variant == 'windswept':
            if value < 6:
                return 'cobblestone'
            if value < 9:
                return 'cracked_stone_bricks'
            if value == 10:
                return 'tuff'
            if value == 11:
                return 'deepslate'
            return 'stone_bricks'
        elif self.variant == 'forest':
            if (y < 12 or z < 12) and value < 12:
                return 'mossy_stone_bricks'
            if value < 5:
                return 'cracked_stone_bricks'
            if value < 8:
                return 'andesite'
            if value == 9:
                return 'tuff'
            return 'stone_bricks'
        else: # highland
            if value < 5:
                return 'cracked_stone_bricks'
            if value < 8:
                return 'andesite'
            if value == 9:
                return 'tuff'
            return 'stone_bricks'

    def wall(self, x0, y0, z0, x1, y1, z1):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.put(x, y, z, self.stone(x, y, z))

    def base(self, x, z):
        self.put(x, 7, z, FOUNDATION)
        self.put(x, 8, z, self.accent_type if (x + z) % 7 == 0 else self.stone(x, 8, z))
        self.fill(x, 9, z, x, 12, z, AIR)

    def door(self, x, y, z, axis='x', width=3, height=4):
        for offset in range(-(width // 2), width // 2 + 1):
            for dy in range(height - (1 if abs(offset) == width // 2 else 0)):
                self.put(x + (offset if axis == 'x' else 0), y + dy, z + (offset if axis == 'z' else 0), AIR)

    def chest(self, key, pos, facing='south'):
        self.put(*pos, state('chest', facing=facing, type='single', waterlogged=False),
                 {'id': 'minecraft:chest', 'LootTable': f'timestop:chests/observatory_{key}'})
        self.fill(pos[0], pos[1] + 1, pos[2], pos[0], pos[1] + 2, pos[2], AIR)

    def book(self, key, pos, facing='south'):
        title, pages = JOURNALS[key]
        tag = {'title': title, 'author': 'Observatory Keeper', 'resolved': Byte(1),
               'pages': [json.dumps({'text': p}) for p in pages]}
        self.put(*pos, state('lectern', facing=facing, has_book=True, powered=False),
                 {'id': 'minecraft:lectern', 'Book': {'id': 'minecraft:written_book', 'Count': Byte(1), 'tag': tag}, 'Page': 0})

    def ground(self):
        for x in range(1, 44):
            for z in range(1, 44):
                terrace = (x - 22)**2 + (z - 21)**2 <= 14**2
                wings = (1 <= x <= 13 and 25 <= z <= 38) or (32 <= x <= 43 and 19 <= z <= 35)
                towers = (x - 7)**2 + (z - 8)**2 <= 6**2 or (x - 37)**2 + (z - 8)**2 <= 6**2
                approach = 14 <= x <= 30 and 33 <= z <= 43
                link = 7 <= x <= 37 and 7 <= z <= 11
                if terrace or wings or towers or approach or link:
                    self.base(x, z)
        # Entrance apron: graduated steps, two gate piers, broken balustrades.
        for z in (41, 42, 43, 44):
            y = 8 - (z - 41)
            self.fill(19, y + 1, z, 25, 12, z, AIR)
            self.fill(19, y - 1, z, 25, y - 1, z, FOUNDATION)
            self.fill(19, y, z, 25, y, z, state('stone_brick_stairs', facing='north', half='bottom', shape='straight', waterlogged=False))
        for x in (15, 29):
            self.wall(x, 9, 38, x + 1, 15 if x == 15 else 12, 39)
            self.fill(x, 16 if x == 15 else 13, 38, x + 1, 16 if x == 15 else 13, 39, self.chiseled_type)
        for z in range(34, 42):
            if z not in (36, 37):
                self.put(15, 9, z, 'stone_brick_wall')
                self.put(29, 9, z, 'stone_brick_wall')
        for x, z in ((16, 36), (27, 38), (29, 34), (14, 40)):
            rubble = 'calcite' if self.variant == 'cherry' else ('cobblestone' if self.variant != 'floral' else 'mossy_cobblestone')
            self.put(x, 9, z, rubble)
            self.put(x + 1, 9, z, state('stone_brick_slab', type='bottom', waterlogged=False))

    def hall(self):
        for x in range(10, 35):
            for z in range(9, 34):
                r = math.hypot(x - 22, z - 21)
                if r <= 11.6:
                    self.fill(x, 9, z, x, 32, z, AIR)
                if 10.5 <= r <= 12:
                    for y in range(9, 20):
                        self.put(x, y, z, self.stone(x, y, z))
                if 10.5 <= r <= 12.4:
                    for y in (9, 17, 19):
                        self.put(x, y, z, self.accent_type if y != 19 else 'waxed_oxidized_cut_copper')
                if r < 10.5:
                    if 7.6 < r < 8.6 or 3.8 < r < 4.6:
                        self.put(x, 8, z, 'waxed_weathered_cut_copper')
                    if (x == 22 or z == 21) and r < 8:
                        self.put(x, 8, z, 'polished_deepslate')
        # Tall windows and doorways; every opening gets a stone arch.
        for x in (10, 11, 33, 34):
            for z in (17, 24):
                self.fill(x, 12, z, x, 16, z + 1, AIR)
                for zz in (z, z + 1):
                    self.put(x, 12, zz, 'iron_bars')
        for z in (9, 10, 32, 33):
            for x in (17, 26):
                self.fill(x, 12, z, x + 1, 16, z, AIR)
        for z in (32, 33):
            self.door(22, 9, z)
        for x in (10, 11, 12):
            self.door(x, 9, 28, 'z')
        for x in (32, 33, 34):
            self.door(x, 9, 25, 'z')
        for z in (9, 10):
            self.door(22, 9, z)
        # Eight outer buttresses are visible under the copper drum.
        for dx, dz in ((0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1)):
            length = math.hypot(dx, dz)
            x = round(22 + dx / length * 12)
            z = round(21 + dz / length * 12)
            for y in range(9, 19):
                if (dx, dz) == (0, 1) and y < 14:
                    continue
                self.put(x, y, z, self.accent_type)
        # Hemisphere shell: authored northeast breach + variant collapse.
        for y in range(20, 32):
            r = math.sqrt(max(0, 12**2 - (y - 19)**2))
            for x in range(9, 36):
                for z in range(8, 35):
                    d = math.hypot(x - 22, z - 21)
                    if abs(d - r) > .7:
                        continue
                    angle = math.atan2(z - 21, x - 22)
                    rib = abs(math.sin(angle * 4)) < .16
                    breach = (x >= 24 and z <= 21 and y >= 22) or (x >= 27 and z >= 23 and y >= 25)
                    if self.variant in ('forest', 'floral') and (x <= 18 and z >= 24 and y >= 24):
                        breach = True
                    if self.variant == 'windswept' and ((x <= 17 and y >= 23) or (z <= 15 and y >= 25)):
                        breach = True
                    if breach and not (rib and y < 25):
                        continue
                    block = 'waxed_exposed_cut_copper' if rib else 'waxed_oxidized_cut_copper'
                    if not rib and (x + 2 * z + y) % 5 == 0:
                        block = 'waxed_weathered_cut_copper'
                    self.put(x, y, z, block)
        # A surviving lantern-capped finial marks the silhouette beside the breach.
        self.fill(21, 31, 20, 22, 32, 21, 'waxed_weathered_cut_copper')
        self.put(21, 33, 20, 'lightning_rod')
        # Large angled telescope, including a hollow dark lens and copper collars.
        self.fill(20, 9, 18, 24, 9, 22, 'polished_deepslate')
        self.fill(21, 10, 19, 23, 13, 21, 'deepslate_tile_wall')
        for i in range(10):
            z = 23 - i
            y = 14 + i // 2
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    shell = abs(dx) == 1 or abs(dy) == 1
                    self.put(22 + dx, y + dy, z, ('waxed_exposed_cut_copper' if i in (0, 4, 9) else 'waxed_oxidized_cut_copper') if shell else 'black_stained_glass')
        self.put(22, 9, 26, state('timestop:golden_pedestal', active=False), {'id': 'timestop:pedestal'})
        for x, z in ((15, 16), (28, 28), (17, 29)):
            self.put(x, 9, z, state('lantern', hanging=False, waterlogged=False))
        # Fallen dome ribs collect in one corner; keep the principal crossing clear.
        for i in range(5):
            self.put(27 + i, 9, 15 + i // 2, 'waxed_oxidized_cut_copper')
            if i < 3:
                self.put(28 + i, 10, 16 + i // 2, state('cut_copper_slab', type='bottom', waterlogged=False))

    def tower(self, cx, cz, top):
        for x in range(cx - 5, cx + 6):
            for z in range(cz - 5, cz + 6):
                d = math.hypot(x - cx, z - cz)
                if d < 4.1:
                    self.fill(x, 9, z, x, top + 1, z, AIR)
                if 4.1 <= d <= 5.1:
                    for y in range(9, top):
                        broken = y > top - 5 and x > cx + 1 and z < cz
                        if not broken:
                            self.put(x, y, z, self.stone(x, y, z))
                    for y in (18, top - 3):
                        self.put(x, y, z, self.accent_type)
                if d < 4.5:
                    for y in (18, top - 3):
                        self.put(x, y, z, f'{self.wood_type}_planks')
                if 4.2 < d <= 5.2 and (x + z) % 2 == 0:
                    self.put(x, top, z, 'stone_brick_wall')
        # Unbroken access ladder beside the remains of a staircase.
        for y in range(9, top - 1):
            self.put(cx, y, cz + 3, f'stripped_{self.wood_type}_log')
            self.put(cx, y, cz + 2, state('ladder', facing='north', waterlogged=False))
        for floor in (18, top - 3):
            self.put(cx, floor, cz + 2, state('ladder', facing='north', waterlogged=False))
        for i in range(4):
            self.put(cx - 2 + i, 9 + i, cz - 1, state('stone_brick_stairs', facing='east', half='bottom', shape='straight', waterlogged=False))
        for y in range(12, 17):
            self.put(cx - 4, y, cz, AIR)
            self.put(cx + 4, y, cz, AIR)
        # Deep slits open through the thick circular masonry on three faces.
        for bottom in (12, 21, 29):
            if bottom + 3 >= top - 3:
                continue
            for y in range(bottom, bottom + 4):
                for d in (4, 5):
                    self.put(cx - d, y, cz, AIR)
                    self.put(cx + d, y, cz, AIR)
                    self.put(cx, y, cz - d, AIR)
            for dx, dz in ((-5, 0), (5, 0), (0, -5)):
                self.put(cx + dx, bottom - 1, cz + dz, self.chiseled_type)
        # A broken clock face over the southern entrance of the shorter tower.
        if cx < 22:
            for dx in range(-2, 3):
                for dy in range(-2, 3):
                    if abs(dx) + abs(dy) > 3 or (dx == 2 and dy > 0):
                        continue
                    self.put(cx + dx, 22 + dy, cz + 5, 'waxed_weathered_cut_copper' if abs(dx) == 2 or abs(dy) == 2 else 'polished_blackstone')
            for dx, dy in ((0, 0), (0, 1), (1, 0)):
                self.put(cx + dx, 22 + dy, cz + 6, 'chiseled_quartz_block')
        self.door(cx, 9, cz + 4)
        self.door(cx, 9, cz + 5)
        for x in (cx - 5, cx - 4, cx + 4, cx + 5):
            self.door(x, 19, cz, 'z', 3, 3)
        self.put(cx - 1, top - 2, cz, state('timestop:copper_pedestal', active=False), {'id': 'timestop:pedestal'})
        self.put(cx + 2, top - 2, cz + 1, state('lantern', hanging=False, waterlogged=False))
        if cx < 22:
            self.chest('tower', (cx - 2, 19, cz - 2), 'east')
        # Copper roof fragments and a slender lightning mast on the taller tower.
        for x in range(cx - 4, cx + 5):
            for z in range(cz - 4, cz + 5):
                if (x - cx)**2 + (z - cz)**2 < 18 and z < cz - 1:
                    self.put(x, top + 1, z, state('waxed_oxidized_cut_copper_slab', type='bottom', waterlogged=False))
        if top == 37:
            self.put(cx - 2, 38, cz - 2, 'lightning_rod')

    def wings(self):
        for x0, z0, x1, z1 in ((1, 25, 13, 38), (32, 19, 43, 35)):
            self.fill(x0 + 1, 9, z0 + 1, x1 - 1, 17, z1 - 1, AIR)
            self.wall(x0, 9, z0, x1, 14, z0)
            self.wall(x0, 9, z1, x1, 13, z1)
            self.wall(x0, 9, z0, x0, 14, z1)
            self.wall(x1, 9, z0, x1, 14, z1)
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if z % 4 == 0:
                        self.put(x, 15, z, state(f'{self.wood_type}_log', axis='x'))
                    intact = (x < x0 + 4 or z < z0 + 3) and not (self.variant != 'highland' and (x + z) % 4 == 0)
                    if intact:
                        self.put(x, 16 + (min(x - x0, x1 - x) // 3), z, state(f'{self.wood_type}_slab', type='bottom', waterlogged=False))
            for z in (z0 + 3, z1 - 3):
                for x in (x0, x1):
                    self.fill(x, 11, z, x, 13, z + 1, AIR)
        # Workshop workbench, bracing, scattered tools and the first journal.
        self.door(13, 9, 28, 'z')
        self.door(12, 9, 28, 'z')
        for x in range(3, 8):
            self.put(x, 9, 26, f'{self.wood_type}_planks')
        self.put(4, 10, 26, 'grindstone')
        self.put(6, 10, 26, 'smithing_table')
        self.put(5, 10, 26, self.carpet_type)
        self.put(3, 9, 28, 'anvil')
        self.put(11, 9, 26, 'crafting_table')
        self.chest('workshop', (3, 9, 36), 'east')
        self.book('workshop', (6, 10, 26), 'south')
        # Library shelves, fallen shelves, reading desk, and a concealed rear aisle.
        self.door(32, 9, 25, 'z')
        self.door(33, 9, 25, 'z')
        for x in range(34, 42):
            for y in range(9, 12):
                self.put(x, y, 20, 'bookshelf')
        for z in range(22, 28):
            for y in range(9, 12):
                self.put(42, y, z, 'bookshelf')
        for x in range(34, 42):
            if x not in (39, 40):
                self.fill(x, 9, 28, x, 11, 28, 'bookshelf')
        self.put(38, 9, 27, 'bookshelf')
        self.put(37, 9, 27, state(f'{self.wood_type}_slab', type='bottom', waterlogged=False))
        self.chest('library', (34, 9, 21), 'south')
        self.book('library', (37, 9, 23), 'west')
        self.put(36, 9, 24, f'{self.wood_type}_planks')
        self.put(36, 10, 24, self.carpet_type)
        self.put(41, 9, 33, state('lantern', hanging=False, waterlogged=False))

    def bridges(self):
        for x in range(11, 34):
            for z in range(7, 11):
                self.put(x, 18, z, f'{self.wood_type}_planks' if z in (8, 9) else 'stone_bricks')
                self.fill(x, 19, z, x, 22, z, AIR)
                if z in (7, 10) and x % 6 not in (2, 3):
                    self.put(x, 19, z, 'stone_brick_wall')
            if x in (15, 29):
                self.wall(x, 9, 7, x, 17, 10)
            if x in (17, 18, 25):
                self.put(x, 18, 7, AIR)
        self.chest('rubble', (15, 9, 12), 'south')
        self.fill(15, 9, 13, 15, 11, 14, AIR)
        rubble_b = 'mossy_cobblestone' if self.variant in ('forest', 'floral') else ('calcite' if self.variant == 'cherry' else 'cobblestone')
        self.put(14, 9, 13, 'cobblestone')
        self.put(16, 9, 13, rubble_b)

    def archive(self):
        self.fill(15, 0, 18, 31, 0, 33, FOUNDATION)
        self.fill(15, 1, 18, 31, 7, 33, 'deepslate_tiles')
        self.fill(16, 1, 19, 30, 6, 32, AIR)
        for x in range(17, 30):
            self.put(x, 1, 19, 'bookshelf')
            self.put(x, 2, 19, 'bookshelf')
        for z in range(20, 32):
            if z not in (26, 27, 28):
                self.put(16, 1, z, 'bookshelf')
        for x in (17, 29):
            for z in (20, 31):
                self.fill(x, 1, z, x, 5, z, 'polished_basalt')
                self.put(x, 6, z + 1, state('lantern', hanging=True, waterlogged=False))
        self.fill(20, 1, 25, 25, 1, 26, 'dark_oak_slab')
        self.chest('archive', (23, 1, 21), 'south')
        self.book('archive', (21, 1, 23), 'south')
        # Clear full-width stairs last, keeping both discovery routes usable.
        for start, direction in ((39, -1), (9, 1)):
            for i in range(9):
                x = start + direction * i
                y = 8 - i
                for z in range(30, 33):
                    self.fill(x, y, z, x, min(12, y + 4), z, AIR)
                    self.put(x, y, z, state('stone_brick_stairs', facing='east' if direction == -1 else 'west', half='bottom', shape='straight', waterlogged=False))
                    if y > 0:
                        self.put(x, y - 1, z, FOUNDATION)
                self.routes.append((x, y + 1, 31))
        self.fill(17, 1, 30, 30, 3, 32, AIR)
        # Subtle rubble beside, not across, the workshop access.
        rubble_a = 'calcite' if self.variant == 'cherry' else 'cobblestone'
        self.put(8, 9, 30, rubble_a)
        self.put(10, 9, 33, 'cracked_stone_bricks')

    def vegetation(self):
        if self.variant == 'highland':
            return
        tree_log = 'oak_log'
        tree_leaf = 'oak_leaves'
        if self.variant == 'cherry':
            tree_log = 'cherry_log'
            tree_leaf = 'cherry_leaves'
        elif self.variant == 'floral':
            tree_log = 'birch_log'
            tree_leaf = 'flowering_azalea_leaves'
        elif self.variant == 'windswept':
            tree_log = 'spruce_log'
            tree_leaf = 'spruce_leaves'

        for x, z in ((41, 37), (42, 38), (40, 38), (43, 36)):
            self.put(x, 8, z, 'rooted_dirt' if self.variant != 'windswept' else 'coarse_dirt')
            self.put(x, 9, z, 'moss_block' if self.variant != 'windswept' else 'cobblestone')
        for y in range(9, 23):
            self.put(42, y, 38, tree_log)
        for x in range(37, 45):
            for z in range(34, 44):
                for y in range(20, 26):
                    if ((x - 41) / 4)**2 + ((z - 38) / 5)**2 + ((y - 22) / 3)**2 < 1.2 and (x + y + z) % 9:
                        self.put(x, y, z, state(tree_leaf, distance=1, persistent=True, waterlogged=False))
        if self.variant in ('forest', 'floral'):
            for x, z, face in ((9, 21, 'east'), (35, 21, 'west'), (0, 30, 'east'), (44, 25, 'west')):
                for y in range(10, 14):
                    self.put(x, y, z, state('vine', **{face: True}))
        # Courtyard vegetation spots
        flora_type = 'fern'
        ground_block = 'moss_block'
        if self.variant == 'cherry':
            flora_type = 'fern'
            ground_block = 'moss_block'
        elif self.variant == 'floral':
            flora_type = 'fern'
            ground_block = 'moss_block'
        elif self.variant == 'windswept':
            flora_type = 'fern'
            ground_block = 'coarse_dirt'

        for x, z in ((16, 35), (27, 36), (3, 24), (34, 17), (7, 16)):
            self.put(x, 8, z, ground_block)
            self.put(x, 9, z, flora_type)

    def build(self):
        self.ground()
        self.hall()
        self.tower(7, 8, 29)
        self.tower(37, 8, 37)
        self.wings()
        self.bridges()
        self.archive()
        self.vegetation()
        # Reassert route openings at junctions after all overlapping architecture.
        for x in (10, 11, 12, 13):
            self.door(x, 9, 28, 'z')
        for x in (32, 33, 34):
            self.door(x, 9, 25, 'z')
        for z in (32, 33):
            self.door(22, 9, z)
        # Precompute connections so chunk-clipped worldgen needs no neighbor writes.
        for (x, y, z), (name, props) in list(self.blocks.items()):
            if not (name.endswith('_wall') or name == 'minecraft:iron_bars'):
                continue
            connections = {}
            for direction, dx, dz in (('north', 0, -1), ('south', 0, 1), ('west', -1, 0), ('east', 1, 0)):
                other = self.blocks.get((x + dx, y, z + dz), AIR)[0]
                connected = other != 'minecraft:air' and not any(token in other for token in ('lantern', 'vine', 'ladder', 'pedestal'))
                above = self.blocks.get((x, y + 1, z), AIR)[0] != 'minecraft:air'
                connections[direction] = ('tall' if above else 'low') if connected and name.endswith('_wall') else ('none' if name.endswith('_wall') else connected)
            if name.endswith('_wall'):
                connections['up'] = True
            self.put(x, y, z, state(name, **connections, waterlogged=False))
        return self

    def export(self):
        palette = sorted(set(self.blocks.values()))
        indices = {p: i for i, p in enumerate(palette)}
        entries = []
        for pos, block in sorted(self.blocks.items(), key=lambda entry: (entry[0][1], entry[0][2], entry[0][0])):
            entry = {'pos': list(pos), 'state': indices[block]}
            if pos in self.nbt:
                entry['nbt'] = self.nbt[pos]
            entries.append(entry)
        root = {
            'DataVersion': 3465,
            'author': 'Ultimate Time Stop',
            'size': list(getattr(self, 'size', SIZE)),
            'palette': [dict(Name=name, **({'Properties': dict(props)} if props else {})) for name, props in palette],
            'blocks': entries,
            'entities': []
        }
        write(DATA / f'structures/ruined_observatory/{self.variant}.nbt', root)
        return root

class AcropolisObservatory(Observatory):
    def __init__(self):
        super().__init__('acropolis')
        self.size = (70, 55, 70)

    def put(self, x, y, z, block, nbt=None):
        x, y, z = int(x), int(y), int(z)
        if not (0 <= x < 70 and 0 <= y < 55 and 0 <= z < 70):
            raise ValueError(f"Out of bounds: ({x}, {y}, {z})")
        self.blocks[x, y, z] = state(block) if isinstance(block, str) else block
        self.nbt.pop((x, y, z), None)
        if nbt is not None:
            self.nbt[x, y, z] = nbt

    def fill(self, x0, y0, z0, x1, y1, z1, block):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, block)

    def door(self, x, y, z, axis='x', width=2, height=3):
        for dy in range(height):
            for d in range(width):
                self.put(x + (d if axis == 'x' else 0), y + dy, z + (d if axis == 'z' else 0), AIR)

    def wall(self, x0, y0, z0, x1, y1, z1):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, 'smooth_quartz' if y % 3 == 0 else ('calcite' if (x + z) % 2 == 0 else 'quartz_block'))

    def stone(self, x, y, z):
        v = (x * 71 + y * 29 + z * 43) % 37
        if v < 8: return 'calcite'
        if v < 15: return 'polished_diorite'
        if v < 22: return 'smooth_quartz'
        return 'quartz_block'

    def ground(self):
        # Base terrain and terraces at Y=11 (foundation) and Y=12 (floor)
        for x in range(2, 68):
            for z in range(2, 68):
                # Symmetrical zones layout:
                pantheon = (x - 22)**2 + (z - 22)**2 <= 17**2
                amphitheater = (x - 22)**2 + (z - 50)**2 <= 17**2
                gatehouse = 42 <= x <= 66 and 42 <= z <= 66
                spire = (x - 55)**2 + (z - 19)**2 <= 10**2
                terrace_links = (20 <= x <= 58 and 18 <= z <= 26) or (18 <= x <= 26 and 20 <= z <= 52) or (38 <= x <= 55 and 38 <= z <= 52)
                if pantheon or amphitheater or gatehouse or spire or terrace_links:
                    self.put(x, 10, z, 'deepslate_tiles')
                    self.put(x, 11, z, 'deepslate_tiles')
                    floor = 'smooth_quartz' if (x + z) % 4 == 0 else ('calcite' if (x + z) % 3 == 0 else 'quartz_block')
                    self.put(x, 12, z, floor)
                    self.fill(x, 13, z, x, 17, z, AIR)

        # Zone A: Sacred Procession Road (South-East, X=48..66, Z=54..68)
        # Rising ramp from (56, 4, 68) to (52, 12, 54)
        for step in range(9):
            z = 66 - step
            y = 4 + step
            for x in range(50, 56):
                self.fill(x, y - 2, z, x, y - 1, z, 'deepslate_tiles')
                self.put(x, y, z, state('smooth_quartz_stairs', facing='north', half='bottom', shape='straight', waterlogged=False))
                self.fill(x, y + 1, z, x, y + 5, z, AIR)
            self.put(49, y + 1, z, 'diorite_wall')
            self.put(56, y + 1, z, 'diorite_wall')
        # Approach landing at Y=4
        for z in range(67, 70):
            for x in range(50, 56):
                self.put(x, 3, z, 'deepslate_tiles')
                self.put(x, 4, z, 'smooth_quartz')
                self.fill(x, 5, z, x, 9, z, AIR)

        # Propylaea Gatehouse (X=46..58, Z=44..52)
        # Gate archway and dual guard bastions
        for x in (46, 57):
            for z in (44, 52):
                self.fill(x, 12, z, x + 1, 22, z + 1, state('quartz_pillar', axis='y'))
                self.put(x, 23, z, 'chiseled_quartz_block')
        self.wall(46, 12, 44, 46, 21, 52)
        self.wall(58, 12, 44, 58, 21, 52)
        # Gatehouse battlements
        for x in range(46, 59):
            self.put(x, 22, 44, 'diorite_wall' if x % 2 == 0 else 'smooth_quartz')
            self.put(x, 22, 52, 'diorite_wall' if x % 2 == 0 else 'smooth_quartz')
        # Portcullis arch
        self.door(51, 13, 48, 'x', 4, 4)
        for x in range(51, 55):
            self.put(x, 16, 48, 'iron_bars')
            self.put(x, 15, 48, 'iron_bars')

        # Gatehouse Guard Armory
        self.chest('workshop', (55, 13, 49), 'west')
        self.book('workshop', (55, 13, 46), 'north')
        self.put(47, 13, 46, 'anvil')
        self.put(47, 13, 47, 'grindstone')
        self.put(47, 13, 48, 'smithing_table')

    def amphitheater(self):
        # Zone B: Sunken Celestial Amphitheater (South-West, Center at 22, 50)
        cx, cz = 22, 50
        # Carve arena bowl from Y=8 to Y=12
        for x in range(cx - 16, cx + 17):
            for z in range(cz - 16, cz + 17):
                r = math.hypot(x - cx, z - cz)
                if r <= 15:
                    self.fill(x, 13, z, x, 25, z, AIR)
                # Semicircular stepped seating tiers (Z >= 42)
                if r <= 14 and z >= 42:
                    tier = int((14 - r) / 3)  # tier 0, 1, 2 -> Y=11, 10, 9, 8
                    floor_y = max(8, 12 - tier)
                    self.put(x, floor_y - 1, z, 'deepslate_tiles')
                    self.put(x, floor_y, z, 'smooth_quartz')
                    self.fill(x, floor_y + 1, z, x, 14, z, AIR)
                    if tier > 0 and (x + z) % 3 == 0:
                        self.put(x, floor_y + 1, z, state('smooth_quartz_stairs', facing='north', half='bottom', shape='straight', waterlogged=False))

        # Central Observation Floor (R <= 6, Y=8)
        for x in range(cx - 6, cx + 7):
            for z in range(cz - 6, cz + 7):
                if math.hypot(x - cx, z - cz) <= 5.5:
                    self.put(x, 7, z, 'deepslate_tiles')
                    # Lapis lazuli & copper celestial dial
                    dial_r = math.hypot(x - cx, z - cz)
                    if dial_r < 1.5:
                        self.put(x, 8, z, 'gold_block')
                    elif 2.8 < dial_r < 3.8:
                        self.put(x, 8, z, 'waxed_exposed_cut_copper')
                    elif (x == cx or z == cz):
                        self.put(x, 8, z, 'lapis_block')
                    else:
                        self.put(x, 8, z, 'polished_deepslate')
                    self.fill(x, 9, z, x, 18, z, AIR)

        # Northern Rostrum (Stage Dais at X=20..24, Z=41..43, Y=13..14)
        self.fill(19, 11, 41, 25, 12, 43, 'quartz_block')
        self.fill(19, 13, 41, 25, 13, 43, 'smooth_quartz')
        self.fill(19, 14, 41, 25, 18, 43, AIR)
        for x in range(20, 25):
            self.put(x, 13, 43, state('smooth_quartz_stairs', facing='south', half='bottom', shape='straight', waterlogged=False))
        self.put(19, 14, 42, 'diorite_wall')
        self.put(25, 14, 42, 'diorite_wall')
        # 1st Golden Pedestal atop the Rostrum!
        self.put(22, 14, 42, state('timestop:golden_pedestal', active=False), {'id': 'timestop:pedestal'})

        # Astrologer's Scriptorium (Western Colonnade at X=8..15, Z=44..52, Y=12)
        self.fill(7, 13, 43, 15, 17, 53, AIR)
        self.wall(7, 12, 43, 7, 17, 53)
        for z in range(45, 52):
            self.put(8, 13, z, 'bookshelf')
            self.put(8, 14, z, 'bookshelf')
        self.chest('library', (10, 13, 45), 'east')
        self.book('library', (10, 13, 47), 'east')
        self.put(10, 13, 49, 'lectern')
        for x in (11, 15):
            self.put(x, 13, 44, state('quartz_pillar', axis='y'))
            self.put(x, 14, 44, state('quartz_pillar', axis='y'))
            self.put(x, 15, 44, 'chiseled_quartz_block')

        # Amphitheater stairs up to the terrace at (33, 12, 48)
        for i in range(4):
            self.put(29 + i, 8 + i, 48, state('smooth_quartz_stairs', facing='east', half='bottom', shape='straight', waterlogged=False))
            self.fill(29 + i, 9 + i, 48, 29 + i, 14, 48, AIR)

    def pantheon(self):
        # Zone C: Grand Pantheon Rotunda & Orrery Heart (North-West, Center at 22, 22)
        cx, cz = 22, 22
        # Grand Rotunda Walls and Fluted Colonnade (R=14..16, Y=12..30)
        for x in range(cx - 17, cx + 18):
            for z in range(cz - 17, cz + 18):
                r = math.hypot(x - cx, z - cz)
                if r <= 14.5:
                    self.fill(x, 13, z, x, 44, z, AIR)
                if 14.0 <= r <= 16.2:
                    for y in range(12, 28):
                        if (x + z) % 3 == 0:
                            self.put(x, y, z, state('quartz_pillar', axis='y'))
                        else:
                            self.put(x, y, z, self.stone(x, y, z))
                    self.put(x, 28, z, 'chiseled_quartz_block')
                # Outer buttresses every 45 degrees
                angle = math.atan2(z - cz, x - cx)
                if 15.5 <= r <= 17.5 and abs(math.sin(angle * 4)) < 0.15:
                    for y in range(12, 24):
                        self.put(x, y, z, state('quartz_pillar', axis='y'))

        # Pantheon Arched Entrances (carve through full wall thickness)
        for z in range(35, 40):  # South entrance
            self.fill(21, 13, z, 24, 17, z, AIR)
        for x in range(35, 40):  # East entrance (to terrace and spire)
            self.fill(x, 13, 21, x, 17, 24, AIR)
        for x in range(5, 10):   # West entrance
            self.fill(x, 13, 21, x, 17, 24, AIR)
        for z in range(5, 10):   # North entrance (to reliquary)
            self.fill(21, 13, z, 24, 17, z, AIR)

        # Zodiac Floor Mosaic (R <= 14, Y=12)
        for x in range(cx - 14, cx + 15):
            for z in range(cz - 14, cz + 15):
                r = math.hypot(x - cx, z - cz)
                if r <= 14.0:
                    if 11.0 <= r <= 12.5:
                        self.put(x, 12, z, 'waxed_oxidized_cut_copper')
                    elif 7.0 <= r <= 8.5:
                        self.put(x, 12, z, 'waxed_exposed_cut_copper')
                    elif (x == cx or z == cz) and r < 11:
                        self.put(x, 12, z, 'gold_block')
                    elif (x + z) % 7 == 0:
                        self.put(x, 12, z, 'lapis_block')
                    else:
                        self.put(x, 12, z, 'smooth_quartz')

        # Soaring Open Oculus Dome (Y=28..46, R <= 15)
        for y in range(28, 47):
            r = math.sqrt(max(0, 15.5**2 - (y - 27)**2))
            for x in range(cx - 17, cx + 18):
                for z in range(cz - 17, cz + 18):
                    d = math.hypot(x - cx, z - cz)
                    if abs(d - r) <= 0.8:
                        angle = math.atan2(z - cz, x - cx)
                        rib = abs(math.sin(angle * 4)) < 0.18
                        oculus = (x - cx)**2 + (z - cz)**2 < 4.2**2 and y >= 42
                        if oculus:
                            continue
                        block = 'waxed_exposed_cut_copper' if rib else ('calcite' if (x + y + z) % 3 == 0 else 'smooth_quartz')
                        self.put(x, y, z, block)

        # Colossal Suspended 3-Axis Armillary Orrery (Y=24..36)
        # Ring 1: Giant Horizontal Equatorial Ring (R=10.5 at Y=26)
        for deg in range(0, 360, 4):
            rad = math.radians(deg)
            rx = round(cx + 10.5 * math.cos(rad))
            rz = round(cz + 10.5 * math.sin(rad))
            self.put(rx, 26, rz, 'gold_block' if deg % 45 == 0 else 'waxed_oxidized_cut_copper')
            if deg % 90 == 0:
                self.put(rx, 27, rz, 'sea_lantern')

        # Ring 2: Tilted Ecliptic Ring (R=8.0, Y=22..32)
        for deg in range(0, 360, 4):
            rad = math.radians(deg)
            rx = round(cx + 8.0 * math.cos(rad))
            ry = round(27 + 5.0 * math.sin(rad))
            rz = round(cz + 8.0 * math.sin(rad) * 0.7)
            self.put(rx, ry, rz, 'waxed_exposed_cut_copper')

        # Ring 3: Meridian Ring (R=7.0, Y=21..33 in north-south plane)
        for deg in range(0, 360, 5):
            rad = math.radians(deg)
            ry = round(27 + 6.0 * math.cos(rad))
            rz = round(cz + 7.0 * math.sin(rad))
            self.put(cx, ry, rz, 'gold_block' if deg % 60 == 0 else 'waxed_weathered_cut_copper')

        # Central Golden Celestial Sphere
        self.put(cx, 27, cz, 'gold_block')
        self.put(cx, 28, cz, 'sea_lantern')

        # Elevated Altar Dais (Y=18, Center at 22, 22)
        self.fill(cx - 3, 18, cz - 3, cx + 3, 18, cz + 3, 'smooth_quartz')
        self.put(cx, 18, cz, 'gold_block')
        # 6 radial steps from Y=13 to Y=18
        for d in range(1, 7):
            y = 12 + d
            dist = 9 - d
            self.fill(cx, y + 1, cz + dist, cx, y + 3, cz + dist, AIR)
            self.put(cx, y, cz + dist, state('smooth_quartz_stairs', facing='north', half='bottom', shape='straight', waterlogged=False))
            self.fill(cx, y + 1, cz - dist, cx, y + 3, cz - dist, AIR)
            self.put(cx, y, cz - dist, state('smooth_quartz_stairs', facing='south', half='bottom', shape='straight', waterlogged=False))
            self.fill(cx + dist, y + 1, cz, cx + dist, y + 3, cz, AIR)
            self.put(cx + dist, y, cz, state('smooth_quartz_stairs', facing='west', half='bottom', shape='straight', waterlogged=False))
            self.fill(cx - dist, y + 1, cz, cx - dist, y + 3, cz, AIR)
            self.put(cx - dist, y, cz, state('smooth_quartz_stairs', facing='east', half='bottom', shape='straight', waterlogged=False))

        # THE DIAMOND PEDESTAL (Centerpiece of the Pantheon!)
        self.fill(cx - 2, 19, cz - 2, cx + 2, 23, cz + 2, AIR)
        self.put(cx, 19, cz, state('timestop:diamond_pedestal', active=False), {'id': 'timestop:pedestal'})

        # Cosmos Reliquary Wing (North Colonnade at X=12..32, Z=6..10, Y=12)
        self.fill(12, 13, 6, 32, 17, 10, AIR)
        self.wall(12, 12, 6, 32, 17, 6)
        for x in range(14, 30, 2):
            self.put(x, 13, 7, 'bookshelf')
            self.put(x, 14, 7, 'bookshelf')
        self.chest('rubble', (15, 13, 8), 'south')

    def spire(self):
        # Zone D: Detached Soaring Astronomical Spire (North-East, Center at 55, 19)
        cx, cz = 55, 19
        top = 54
        # Tower circular masonry walls from Y=12 up to Y=54 (R=5.5)
        for x in range(cx - 6, cx + 7):
            for z in range(cz - 6, cz + 7):
                d = math.hypot(x - cx, z - cz)
                if d < 4.2:
                    self.fill(x, 13, z, x, top, z, AIR)
                if 4.2 <= d <= 5.8:
                    for y in range(12, top):
                        broken = (y > 44 and x > cx + 2 and z < cz)
                        if not broken:
                            if (x + z) % 3 == 0:
                                self.put(x, y, z, state('quartz_pillar', axis='y'))
                            else:
                                self.put(x, y, z, 'smooth_quartz' if y % 2 == 0 else 'calcite')
                    for floor_y in (22, 34, 48):
                        self.put(x, floor_y, z, 'chiseled_quartz_block')
                # Floor platforms
                if d < 4.5:
                    for floor_y in (22, 34, 48):
                        self.put(x, floor_y, z, 'smooth_quartz')

        # Continuous central access ladder inside the spire from Y=12 to Y=49
        for y in range(12, 50):
            self.put(cx, y, cz + 3, state('quartz_pillar', axis='y'))
            self.put(cx, y, cz + 2, state('ladder', facing='north', waterlogged=False))

        # Ground door
        self.door(cx - 5, 13, cz, 'z', 2, 3)

        # Elevated 3-Arch Sky-Bridge connecting Pantheon terrace (38, 22) to Spire (49, 19)
        # Spans X=38 to X=50 at Y=22
        for x in range(38, 51):
            for z in (18, 19, 20):
                self.put(x, 22, z, 'smooth_quartz')
                self.fill(x, 23, z, x, 26, z, AIR)
            self.put(x, 23, 17, 'diorite_wall')
            self.put(x, 23, 21, 'diorite_wall')
            # Bridge support pillars extending down to Y=12
            if x in (41, 46):
                for y in range(12, 22):
                    self.put(x, y, 18, state('quartz_pillar', axis='y'))
                    self.put(x, y, 20, state('quartz_pillar', axis='y'))
        self.door(cx - 5, 23, cz, 'z', 2, 3)

        # Observation Summit (Y=48..54)
        for x in range(cx - 5, cx + 6):
            for z in range(cz - 5, cz + 6):
                d = math.hypot(x - cx, z - cz)
                if 4.5 < d <= 5.8 and (x + z) % 2 == 0:
                    self.put(x, 49, z, 'diorite_wall')
                if d < 4.2:
                    self.put(x, 53, z, state('waxed_exposed_cut_copper_slab', type='bottom', waterlogged=False))

        # Summit deck floor at Y=48 and open observation chamber (Y=49..52)
        for x in range(cx - 4, cx + 5):
            for z in range(cz - 4, cz + 5):
                if math.hypot(x - cx, z - cz) < 4.5:
                    self.put(x, 48, z, 'smooth_quartz')
                    self.fill(x, 49, z, x, 52, z, AIR)

        # Re-assert ladder at summit deck
        self.put(cx, 48, cz + 3, state('quartz_pillar', axis='y'))
        self.put(cx, 48, cz + 2, state('ladder', facing='north', waterlogged=False))
        self.put(cx, 49, cz + 3, state('quartz_pillar', axis='y'))
        self.put(cx, 49, cz + 2, state('ladder', facing='north', waterlogged=False))
        self.fill(cx, 50, cz + 2, cx, 52, cz + 2, AIR)

        # 2nd Golden Pedestal atop the Spire Summit!
        self.put(cx, 49, cz, state('timestop:golden_pedestal', active=False), {'id': 'timestop:pedestal'})
        self.chest('tower', (cx - 2, 49, cz - 2), 'east')
        self.put(cx, 54, cz, 'lightning_rod')

    def archive(self):
        # Zone E: Subterranean Mountain Crypts & Chrono-Vault (Y=0..11)
        # Deepslate foundation bed
        self.fill(8, 0, 8, 40, 0, 40, FOUNDATION)
        self.fill(8, 1, 8, 40, 10, 40, 'deepslate_tiles')

        # Winding vaulted catacomb corridors (Y=1..4)
        corridors = [
            (12, 1, 14, 32, 4, 18),
            (20, 1, 18, 24, 4, 38),
            (14, 1, 30, 30, 4, 34),
        ]
        for c in corridors:
            self.fill(c[0], c[1], c[2], c[3], c[4], c[5], AIR)

        # Central Chrono-Vault room at (18..26, 1..6, 18..26)
        self.fill(18, 1, 18, 26, 6, 26, AIR)
        for x in (18, 26):
            for z in (18, 26):
                self.fill(x, 1, z, x, 5, z, state('quartz_pillar', axis='y'))
                self.put(x, 6, z, 'gold_block')
        for x in range(20, 25):
            self.put(x, 1, 18, 'bookshelf')
            self.put(x, 2, 18, 'bookshelf')
        for z in range(20, 25):
            self.put(18, 1, z, 'bookshelf')
            self.put(26, 1, z, 'bookshelf')

        # Sarcophagi in crypt niches
        for x, z in ((14, 16), (30, 16), (14, 32), (30, 32)):
            self.put(x, 1, z, 'calcite')
            self.put(x, 2, z, state('smooth_quartz_slab', type='bottom', waterlogged=False))

        # Altar in Chrono-Vault holding Archive Chest & Book
        self.fill(21, 0, 21, 24, 0, 23, 'polished_deepslate')
        self.put(23, 0, 21, FOUNDATION)  # Crucial foundation anchor block!
        self.put(22, 0, 22, 'gold_block')
        self.chest('archive', (23, 1, 21), 'south')
        self.book('archive', (21, 1, 23), 'south')

        # Stair 1: West stair rising from crypt to Pantheon floor
        # From (12, 1, 31) to (12, 11, 21)
        for i in range(11):
            y = 1 + i
            z = 31 - i
            self.fill(12, y, z, 13, y + 4, z, AIR)
            self.put(12, y, z, state('smooth_quartz_stairs', facing='north', half='bottom', shape='straight', waterlogged=False))
            self.put(13, y, z, state('smooth_quartz_stairs', facing='north', half='bottom', shape='straight', waterlogged=False))
            self.routes.append((12, y + 1, z))
        # Clear stair entrance on Pantheon floor
        self.fill(12, 12, 19, 13, 16, 21, AIR)

        # Stair 2: Cave fissure from crypt (22, 1, 39) rising to Amphitheater floor (22, 8, 46)
        for i in range(8):
            y = 1 + i
            z = 39 + i
            self.fill(21, y, z, 23, y + 4, z, AIR)
            self.put(22, y, z, state('smooth_quartz_stairs', facing='south', half='bottom', shape='straight', waterlogged=False))
            self.routes.append((22, y + 1, z))
        # Clear fissure mouth on amphitheater floor (preserve floor at Y=8)
        self.fill(21, 9, 46, 23, 14, 48, AIR)

    def vegetation(self):
        # Alpine flora: snow layers, blue ice crystal inlays, and ancient alpine pines
        for x, z in ((62, 58), (64, 56), (38, 60), (12, 60), (4, 34)):
            self.put(x, 12, z, 'calcite')
            self.put(x, 13, z, 'snow_block')
        # Ancient alpine pine on east terrace
        for y in range(13, 24):
            self.put(60, y, 36, state('quartz_pillar', axis='y'))
        for x in range(56, 65):
            for z in range(32, 41):
                for y in range(21, 27):
                    if ((x - 60)/3.5)**2 + ((z - 36)/3.5)**2 + ((y - 23)/2.5)**2 < 1.2:
                        self.put(x, y, z, state('spruce_leaves', distance=1, persistent=True, waterlogged=False))

    def build(self):
        self.ground()
        self.amphitheater()
        self.pantheon()
        self.spire()
        self.archive()
        self.vegetation()
        return self

def json_file(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')

def item(name, weight=1, count=None):
    result = {'type': 'minecraft:item', 'name': name if ':' in name else 'minecraft:' + name, 'weight': weight}
    if count:
        result['functions'] = [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': count[0], 'max': count[1]}}]
    return result

def loot():
    supplies = [
        item('copper_ingot', 5, (4, 12)),
        item('iron_ingot', 3, (2, 5)),
        item('gold_ingot', 2, (1, 4)),
        item('redstone', 4, (4, 10)),
        item('book', 3, (1, 3)),
        item('paper', 4, (3, 8)),
        item('amethyst_shard', 2, (1, 3)),
        item('spyglass', 1),
        item('cherry_sapling', 1, (1, 2)),
        item('pink_petals', 1, (2, 6)),
        item('honeycomb', 1, (1, 3)),
        item('shears', 1),
        item('dark_oak_sapling', 1, (1, 2)),
        item('sweet_berries', 2, (2, 6)),
        item('chain', 2, (2, 4)),
        item('quartz', 2, (2, 6))
    ]
    for name in ('workshop', 'library', 'tower', 'rubble', 'archive'):
        pools = [{'rolls': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}, 'entries': supplies[:-1] if name == 'archive' else supplies}]
        if name == 'archive':
            for entry in (item('timestop:chronos_watch'), item('timestop:blank_rune')):
                pools.append({'rolls': 1, 'entries': [entry]})
            pools.append({
                'rolls': 1,
                'conditions': [{'condition': 'minecraft:random_chance', 'chance': .25}],
                'entries': [item('timestop:rune_deflection'), item('timestop:rune_snatching')]
            })
        json_file(DATA / f'loot_tables/chests/observatory_{name}.json', {'type': 'minecraft:chest', 'pools': pools})

def floorplan(structure):
    from PIL import Image, ImageDraw, ImageFont
    w, h, d = getattr(structure, 'size', SIZE)
    cell = 14 if w > 45 else 20
    img_w = 100 + w * cell
    img_h = 160 + d * cell
    image = Image.new('RGB', (img_w, img_h), '#182127')
    draw = ImageDraw.Draw(image)
    try:
        title = ImageFont.truetype('C:/Windows/Fonts/consola.ttf', 27)
        font = ImageFont.truetype('C:/Windows/Fonts/consola.ttf', 16)
    except OSError:
        title = font = ImageFont.load_default()
    draw.text((45, 20), f'RUINED OBSERVATORY / {structure.variant.upper()}', fill='#e9d9b1', font=title)
    colors = {
        'air': '#222e34', 'copper': '#629c8d', 'bookshelf': '#8b6546', 'spruce': '#614f40',
        'oak': '#54724a', 'cherry': '#d47b8e', 'birch': '#c4b574', 'dark_oak': '#3c2b18',
        'deepslate': '#37434d', 'lantern': '#ffd585', 'pedestal': '#f3cb68', 'chest': '#d9a34c',
        'calcite': '#e6e4dc', 'quartz': '#ede8db', 'lapis': '#254a9e'
    }
    floor_y = 13 if structure.variant == 'acropolis' else 9
    for (x, y, z), (name, _) in structure.blocks.items():
        if y != floor_y:
            continue
        color = next((c for token, c in colors.items() if token in name), '#8c9993')
        draw.rectangle((50 + x * cell, 80 + z * cell, 50 + (x + 1) * cell - 1, 80 + (z + 1) * cell - 1), fill=color)
    draw.text((50, img_h - 60), f'{w} x {d} blocks | entrance floor +{12 if structure.variant == "acropolis" else 8} | archive floor +0', fill='#c7d3ce', font=font)
    draw.text((50, img_h - 30), 'Gold: loot / pedestal     North: top', fill='#c7d3ce', font=font)
    image.save(ART / f'{structure.variant}_floorplan.png')

def main():
    ART.mkdir(parents=True, exist_ok=True)
    old_set = DATA / 'worldgen/structure_set/ruined_observatories.json'
    if old_set.exists():
        old_set.unlink()

    for variant, profile in VARIANT_PROFILES.items():
        biomes = profile['biomes']
        salt = profile['salt']
        structure = AcropolisObservatory().build() if variant == 'acropolis' else Observatory(variant).build()
        root = structure.export()
        floorplan(structure)

        # Datapack Biome Tag
        json_file(DATA / f'tags/worldgen/biome/has_structure/ruined_observatory_{variant}.json', {
            'replace': False,
            'values': ['minecraft:' + b for b in biomes]
        })
        # Datapack Structure Definition
        json_file(DATA / f'worldgen/structure/ruined_observatory_{variant}.json', {
            'type': 'timestop:ruined_observatory',
            'variant': variant,
            'biomes': f'#timestop:has_structure/ruined_observatory_{variant}',
            'step': 'surface_structures',
            'spawn_overrides': {},
            'terrain_adaptation': 'none'
        })
        # Dedicated Structure Set
        spacing = 32 if variant == 'acropolis' else 24
        separation = 12 if variant == 'acropolis' else 8
        json_file(DATA / f'worldgen/structure_set/ruined_observatories_{variant}.json', {
            'structures': [{'structure': f'timestop:ruined_observatory_{variant}', 'weight': 1}],
            'placement': {'type': 'minecraft:random_spread', 'spacing': spacing, 'separation': separation, 'salt': salt}
        })
        # QA Manifest
        json_file(ART / f'{variant}_manifest.json', {
            'size': list(getattr(structure, 'size', SIZE)),
            'entrance_floor_y': 12 if variant == 'acropolis' else 8,
            'archive_floor_y': 0,
            'blocks': len(root['blocks']),
            'palette': len(root['palette']),
            'loot': {','.join(map(str, p)): v['LootTable'] for p, v in structure.nbt.items() if 'LootTable' in v},
            'pedestals': [list(p) for p, b in structure.blocks.items() if 'pedestal' in b[0]],
            'archive_stair_routes': structure.routes
        })
        print(f'{variant}: {len(root["blocks"])} placed cells; {len(root["palette"])} block states (salt {salt})')

    loot()
    print('Observatory generation complete for all 6 variants.')

if __name__ == '__main__':
    main()

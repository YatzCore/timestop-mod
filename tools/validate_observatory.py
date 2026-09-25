"""Structural and traversal checks against the exported NBT, plus JAR contents."""
import collections, json, sys, zipfile
from generate_observatory import ROOT, DATA, SIZE, Observatory, AcropolisObservatory, VARIANT_PROFILES
from structure_nbt import read

PASSABLE = {'minecraft:air', 'minecraft:ladder', 'minecraft:vine', 'minecraft:fern', 'minecraft:pink_petals'}

def reachable(blocks, variant='default'):
    def name(p):
        return blocks.get(p, ('minecraft:air', ()))[0]
    w, h, d = (70, 55, 70) if variant == 'acropolis' else (45, 40, 45)
    def stand(p):
        x, y, z = p
        return 0 <= x < w and 1 <= y < h - 1 and 0 <= z < d and name(p) in PASSABLE and name((x, y + 1, z)) in PASSABLE and (name((x, y - 1, z)) not in PASSABLE or name(p) == 'minecraft:ladder')
    start = (52, 5, 68) if variant == 'acropolis' else (22, 9, 40)
    assert stand(start), f'{variant}: Entrance is obstructed at {start}'
    seen = {start}
    todo = collections.deque([start])
    while todo:
        x, y, z = todo.popleft()
        choices = [(x + dx, y + dy, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) for dy in (0, 1, -1)]
        if name((x, y, z)) == 'minecraft:ladder':
            choices.extend(((x, y + 1, z), (x, y - 1, z)))
        for p in choices:
            if p not in seen and stand(p):
                seen.add(p)
                todo.append(p)
    return seen

def validate(packaged=False):
    for variant in VARIANT_PROFILES:
        path = DATA / f'structures/ruined_observatory/{variant}.nbt'
        root = read(path)
        expected = AcropolisObservatory().build() if variant == 'acropolis' else Observatory(variant).build()
        palette = [(p['Name'], tuple(sorted(p.get('Properties', {}).items()))) for p in root['palette']]
        blocks = {tuple(b['pos']): palette[b['state']] for b in root['blocks']}
        size = (70, 55, 70) if variant == 'acropolis' else SIZE
        assert root['size'] == list(size) and blocks == expected.blocks
        assert len(blocks) == len(root['blocks']), 'Duplicate block coordinates'
        assert all(all(0 <= a < b for a, b in zip(p, size)) for p in blocks)
        nbt = {tuple(b['pos']): b['nbt'] for b in root['blocks'] if 'nbt' in b}
        assert nbt == expected.nbt
        pedestals = [p for p, b in blocks.items() if b[0].endswith('_pedestal')]
        assert len(pedestals) == 3
        if variant == 'acropolis':
            assert sum(1 for p in pedestals if 'diamond' in blocks[p][0]) == 1
            assert sum(1 for p in pedestals if 'golden' in blocks[p][0]) == 2
        else:
            assert sum(1 for p in pedestals if 'golden' in blocks[p][0]) == 1
            assert sum(1 for p in pedestals if 'copper' in blocks[p][0]) == 2
        for p in pedestals:
            assert dict(blocks[p][1])['active'] == 'false'
            assert nbt[p] == {'id': 'timestop:pedestal'}, 'Do not serialize owners, watches, or reusable field IDs'
        caches = {p: tag['LootTable'] for p, tag in nbt.items() if 'LootTable' in tag}
        assert len(caches) == 5 and len(set(caches.values())) == 5
        seen = reachable(blocks, variant)
        if variant == 'acropolis':
            for name, p in {'pantheon': (22, 13, 21), 'amphitheater': (22, 9, 52), 'scriptorium': (11, 13, 45),
                            'gatehouse': (54, 13, 49), 'archive': (23, 1, 22), 'spire': (53, 13, 19), 'summit': (55, 49, 20)}.items():
                assert p in seen, f'{variant}: unreachable {name} {p}'
        else:
            for z in range(41, 45):
                assert (22, 9 - (z - 41), z) in seen, f'{variant}: courtyard step obstructed {z}'
            for name, p in {'hall': (22, 9, 29), 'workshop': (7, 9, 28), 'library': (37, 9, 25), 'archive': (23, 1, 22),
                           'west tower': (7, 27, 10), 'east tower': (37, 35, 10), 'bridge': (22, 19, 8)}.items():
                assert p in seen, f'{variant}: unreachable {name} {p}'
        for pos in expected.routes:
            assert pos in seen, f'{variant}: stair route blocked {pos}'
        for (x, y, z), table in caches.items():
            assert any((x + dx, y, z + dz) in seen for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))), f'{variant}: inaccessible chest {(x, y, z)}'
            assert blocks.get((x, y + 1, z), ('minecraft:air', ()))[0] == 'minecraft:air', 'Chest lid obstructed'
            assert (DATA / f'loot_tables/{table.split(":")[1]}.json').exists()
        print(f'PASS {variant}: {len(blocks)} cells, {len(seen)} traversable positions, both archive stairs, 5 accessible caches, 3 clean pedestals.')

    archive = json.loads((DATA / 'loot_tables/chests/observatory_archive.json').read_text())
    guaranteed = [p for p in archive['pools'] if p['rolls'] == 1 and 'conditions' not in p]
    assert [p['entries'][0]['name'] for p in guaranteed] == ['timestop:chronos_watch', 'timestop:blank_rune']
    runes = archive['pools'][-1]
    assert runes['conditions'][0]['chance'] == .25
    assert {e['name']: e['weight'] for e in runes['entries']} == {'timestop:rune_deflection': 1, 'timestop:rune_snatching': 1}

    for variant, profile in VARIANT_PROFILES.items():
        structure_set = json.loads((DATA / f'worldgen/structure_set/ruined_observatories_{variant}.json').read_text())
        expected_spacing = 32 if variant == 'acropolis' else 24
        expected_separation = 12 if variant == 'acropolis' else 8
        assert structure_set['placement'] == {'type': 'minecraft:random_spread', 'spacing': expected_spacing, 'separation': expected_separation, 'salt': profile['salt']}
        assert [s['structure'] for s in structure_set['structures']] == [f'timestop:ruined_observatory_{variant}']

        definition = json.loads((DATA / f'worldgen/structure/ruined_observatory_{variant}.json').read_text())
        assert definition['type'] == 'timestop:ruined_observatory' and definition['variant'] == variant
        assert definition['biomes'] == f'#timestop:has_structure/ruined_observatory_{variant}'
        tag = json.loads((DATA / f'tags/worldgen/biome/has_structure/ruined_observatory_{variant}.json').read_text())
        assert set(tag['values']) == {'minecraft:' + b for b in profile['biomes']}

    for file in (DATA / 'loot_tables/chests').glob('observatory_*.json'):
        assert not any(forbidden in file.read_text() for forbidden in ('diamond_watch', 'netherite_watch', 'creative_watch'))

    if packaged:
        for loader in ('fabric', 'forge'):
            jar = ROOT / loader / f'build/libs/timestop-{loader}-1.20.1-1.6.1.jar'
            with zipfile.ZipFile(jar) as z:
                for path in DATA.rglob('*'):
                    if path.is_file() and ('observator' in str(path) or path.suffix == '.nbt'):
                        resource = path.relative_to(ROOT / 'common/src/main/resources').as_posix()
                        assert z.read(resource) == path.read_bytes(), f'{loader}: stale {resource}'
                assert not any('ObservatoryTests' in n or 'ObservatoryVisualQa' in n for n in z.namelist())
            print('PASS packaged', jar.name)

if __name__ == '__main__':
    validate('--packaged' in sys.argv)

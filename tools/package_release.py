"""Verify built loader JARs and copy them, with checksums, into one release folder."""
from pathlib import Path
import hashlib
import json
import shutil
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROPS = dict(line.split('=', 1) for line in
             (ROOT / 'gradle.properties').read_text(encoding='utf-8').splitlines()
             if '=' in line and not line.startswith('#'))
VERSION = PROPS['mod_version']
MINECRAFT = PROPS['minecraft_version']
RESOURCES = ROOT / 'common/src/main/resources'
OUTPUT = ROOT / 'build/release' / MINECRAFT


def runtime_path(path):
    name = path.relative_to(RESOURCES).as_posix()
    if name.startswith('data/'):
        for old, new in [('recipes', 'recipe'), ('structures', 'structure'),
                         ('loot_tables', 'loot_table'), ('tags/blocks', 'tags/block'),
                         ('tags/items', 'tags/item')]:
            name = name.replace(f'/{old}/', f'/{new}/')
    return name


def validate(loader):
    jar = ROOT / loader / 'build/libs' / f'timestop-{loader}-{MINECRAFT}-{VERSION}.jar'
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        assert len(names) == len(set(names)), f'{loader}: duplicate entries'
        assert archive.testzip() is None, f'{loader}: damaged ZIP entry'
        assert not any('/test/' in name or 'VisualQa' in name for name in names), \
            f'{loader}: test classes in release'
        for path in RESOURCES.rglob('*'):
            if path.is_file():
                actual, expected = archive.read(runtime_path(path)), path.read_bytes()
                # Loom rewrites mixin JSON formatting during remapping.
                if path.name.endswith('.mixins.json'):
                    actual, expected = json.loads(actual), json.loads(expected)
                assert actual == expected, \
                    f'{loader}: missing/stale resource {path}'
        if loader == 'fabric':
            metadata = json.loads(archive.read('fabric.mod.json'))
            assert metadata['version'] == VERSION
            assert metadata['depends']['minecraft'] == MINECRAFT
            assert 'timestop.refmap.json' in names
        else:
            metadata = tomllib.loads(archive.read(
                'META-INF/' + ('mods.toml' if loader == 'forge' else 'neoforge.mods.toml')
            ).decode('utf-8'))
            assert metadata['mods'][0]['version'] == VERSION
        sounds = json.loads(archive.read('assets/timestop/sounds.json'))
        for event in sounds.values():
            for sound in event['sounds']:
                sound = sound if isinstance(sound, str) else sound['name']
                namespace, asset = sound.split(':', 1)
                assert f'assets/{namespace}/sounds/{asset}.ogg' in names
    print(f'PASS {jar.name}: metadata, all shared resources, sounds, and test exclusion')
    return jar


if __name__ == '__main__':
    jars = [validate(loader) for loader in ('fabric', 'forge', 'neoforge')]
    OUTPUT.mkdir(parents=True, exist_ok=True)
    hashes = []
    for jar in jars:
        destination = OUTPUT / jar.name
        shutil.copy2(jar, destination)
        hashes.append(f'{hashlib.sha256(destination.read_bytes()).hexdigest()}  {jar.name}')
    (OUTPUT / 'SHA256SUMS.txt').write_text('\n'.join(hashes) + '\n', encoding='utf-8')
    for document in ('PORTING_1_21_1.md', 'PEDESTALS.md', 'OBSERVATORY.md'):
        shutil.copy2(ROOT / document, OUTPUT / document)
    print(f'Release ready: {OUTPUT}')

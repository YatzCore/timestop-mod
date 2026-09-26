"""Start an isolated loader dev server, exercise registrations, save, and stop."""
from pathlib import Path
import secrets, socket, struct, subprocess, sys, time

ROOT = Path(__file__).resolve().parents[1]
loader = sys.argv[1]
assert loader in ('forge', 'neoforge')
run = ROOT / loader / 'build/server-smoke-run'
run.mkdir(parents=True, exist_ok=True)
password = secrets.token_hex(16)
port = 25861 if loader == 'forge' else 25862
(run / 'eula.txt').write_text('eula=true\n')
(run / 'server.properties').write_text(
    'online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n'
    'view-distance=2\nsimulation-distance=2\nlevel-seed=16310624\n'
    'level-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\nspawn-protection=0\nmax-tick-time=120000\n'
    f'enable-rcon=true\nrcon.port={port}\nrcon.password={password}\n')

def rcon(kind, command):
    with socket.create_connection(('127.0.0.1', port), timeout=20) as conn:
        def exchange(packet_type, body):
            packet = struct.pack('<ii', 1, packet_type) + body.encode() + b'\0\0'
            conn.sendall(struct.pack('<i', len(packet)) + packet)
            size = struct.unpack('<i', conn.recv(4))[0]
            data = b''
            while len(data) < size:
                chunk = conn.recv(size-len(data))
                if not chunk: raise ConnectionError('RCON closed before completing reply')
                data += chunk
            return data[8:-2].decode(errors='replace')
        exchange(3, password)
        return exchange(kind, command)
log_path = ROOT / f'build/port-{loader}-server.log'
with log_path.open('w', encoding='utf-8') as log:
    process = subprocess.Popen([str(ROOT / 'gradlew.bat'), f':{loader}:runServer',
        '--no-daemon', '--console=plain', '--no-watch-fs', '--no-problems-report'],
        cwd=ROOT, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True,
        creationflags=subprocess.CREATE_NO_WINDOW)
    sent = False
    deadline = time.monotonic() + 420
    while process.poll() is None:
        text = log_path.read_text(encoding='utf-8', errors='replace')
        if not sent and 'RCON running' in text:
            commands = ['forceload add 0 0 80 80',
                'setblock 4 80 4 timestop:golden_pedestal',
                'data get block 4 80 4',
                'place template timestop:ruined_observatory/highland 0 90 0',
                'loot spawn 4 81 4 loot timestop:chests/observatory_archive',
                'timestop allowrewind false', 'timestop allowrewind true', 'save-all', 'stop']
            for command in commands:
                reply = rcon(2, command)
                print(f'{loader}: {command}: {reply}', flush=True)
            sent = True
        if time.monotonic() > deadline:
            if sent: rcon(2, 'stop')
            raise RuntimeError(f'{loader} smoke timed out; inspect {log_path}')
        time.sleep(1)
    text = log_path.read_text(encoding='utf-8', errors='replace')
    assert process.returncode == 0 and sent, f'{loader} failed; inspect {log_path}'
    assert 'Saved the game' in text, f'{loader} did not save cleanly'
    print(f'PASS {loader}: booted, sent placement/loot commands, saved and stopped. Log: {log_path}')

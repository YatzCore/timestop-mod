"""Package the isolated client's real screenshots and a close-up animation."""
from pathlib import Path
import shutil
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'fabric/build/pedestal-visual-run/screenshots'
DEST=ROOT/'art/pedestals/in-game'

def main():
    DEST.mkdir(parents=True,exist_ok=True)
    for path in SOURCE.glob('*.png'):
        if path.name[:2].isdigit():
            shutil.copy2(path,DEST/path.name)
    paths=[SOURCE/f'armillary-motion-{i:03}.png' for i in range(0,61,2)]
    assert all(p.exists() for p in paths), 'Run :fabric:runPedestalVisual first'
    frames=[]
    for path in paths:
        with Image.open(path) as image:
            # Same fixed crop for every frame; unmodified originals remain available.
            frame=image.crop((480,170,1120,770)).convert('RGB')
            frames.append(frame)
    assert len({frame.tobytes() for frame in frames})>25, 'Animation did not progress'
    frames[0].save(DEST/'armillary-animation.gif',save_all=True,append_images=frames[1:],duration=100,loop=0)
    print(f'Packaged {len(paths)} animation frames and QA screenshots in {DEST}')

if __name__=='__main__': main()

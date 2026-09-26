# Ruined Observatory — 1.6.1

Five explorable Overworld ruins share a 45 × 45 block layout: a broken copper dome and telescope, unequal observation towers, a damaged upper gallery, workshop, library, courtyard, and buried archive. No watch, puzzle, or combat encounter is required to explore them:

- **Acropolis Citadel** (`timestop:ruined_observatory_acropolis`): Stony peaks, jagged peaks, and frozen peaks. A massive 70 × 55 × 70 mountaintop citadel sanctuary featuring the Grand Pantheon Rotunda with a 3-axis Armillary Orrery, Diamond Pedestal altar, Sunken Celestial Amphitheater with Golden Pedestal, 54-block Astronomical Spire with elevated sky-bridge and 2nd Golden Pedestal, and subterranean Mountain Crypts housing the Chrono-Vault.
- **Highland** (`timestop:ruined_observatory_highland`): Windswept hills, gravelly hills, meadows, and stony peaks. Weathered masonry, tuff, spruce timber, and cracked stone.
- **Forest** (`timestop:ruined_observatory_forest`): Forests, birch forests, old-growth birch, and dark forests. Mossy stone bricks, roots, vines, an encroaching oak tree, and roof damage.
- **Cherry** (`timestop:ruined_observatory_cherry`): Cherry groves. Cherry wood timber, pink petal scatter, calcite accents, and a blossoming cherry tree.
- **Floral** (`timestop:ruined_observatory_floral`): Flower forests. Birch timber, flowering azalea, lilacs, alliums, garden flowers, and overgrown mossy masonry.
- **Windswept** (`timestop:ruined_observatory_windswept`): Windswept forests. Dark oak and spruce alpine timber, rough stone bricks, cobblestone, wind-scoured copper, and severe gale damage.

The main hall contains an empty Golden pedestal. Each tower contains an empty Copper pedestal (the Acropolis Citadel features a Diamond Pedestal and two Golden Pedestals). Insert a compatible watch or interact normally to claim them; they use the existing redstone and field controls and can be mined and recovered. Every generated pedestal receives its own field ID.

## Finding a ruin

Natural generation affects new chunks only. Each variant has its own dedicated structure set with **24-chunk spacing and 8-chunk separation** (candidate checks every ~384 blocks, natural discovery within ~1,200–2,000 blocks):

```mcfunction
/locate structure timestop:ruined_observatory_acropolis
/locate structure timestop:ruined_observatory_highland
/locate structure timestop:ruined_observatory_forest
/locate structure timestop:ruined_observatory_cherry
/locate structure timestop:ruined_observatory_floral
/locate structure timestop:ruined_observatory_windswept

/place structure timestop:ruined_observatory_acropolis ~ ~ ~
/place structure timestop:ruined_observatory_highland ~ ~ ~
/place structure timestop:ruined_observatory_forest ~ ~ ~
/place structure timestop:ruined_observatory_cherry ~ ~ ~
/place structure timestop:ruined_observatory_floral ~ ~ ~
/place structure timestop:ruined_observatory_windswept ~ ~ ~
```

`/place structure` uses terrain eligibility and selects a rotation. The requested chunk determines the horizontal site; the generator determines elevation. Sites with sampled water, more than 12 blocks of relief, or insufficient vertical space are rejected. Foundations extend at most 24 blocks below their authored starting points. The structure does not flatten a whole mountain.

## Predictable template inspection

Use an expendable inspection area: these commands replace blocks, including explicit interior air. Templates bypass terrain checks and do not add procedural foundation supports.

```mcfunction
/place template timestop:ruined_observatory/highland ~ ~ ~ none none 1.0 42
/place template timestop:ruined_observatory/forest ~ ~ ~ none none 1.0 42
/place template timestop:ruined_observatory/cherry ~ ~ ~ none none 1.0 42
/place template timestop:ruined_observatory/floral ~ ~ ~ none none 1.0 42
/place template timestop:ruined_observatory/windswept ~ ~ ~ none none 1.0 42
```

The unrotated template origin is its northwest lower corner, **at archive floor height**. It occupies X/Z offsets 0–44 and a 40-block-high template envelope. The entrance floor is at origin Y + 8; the tallest mast is at Y + 38, 30 blocks above that floor. The archive floor is at Y + 0. The front entrance faces south (+Z), centered at X + 22, Z + 33; courtyard steps finish at Z + 44, Y + 5. To bury the archive in a flat site, choose an origin roughly five blocks below the surrounding ground surface. The upper occupied envelope needs 31 blocks of clearance above the entrance.

Vanilla template rotations (`clockwise_90`, `180`, `counterclockwise_90`) rotate around the command origin, so their footprint extends into different coordinate directions. Natural placement uses the center pivot (22, 0, 22) and keeps the same 45 × 45 footprint in all four rotations.

The hidden library stair starts behind the damaged southern shelves. A second stair descends from the workshop rubble. Both lead eight blocks down to the archive; ladders beside the broken tower stairs keep the upper gallery accessible.

## Treasure and journals

There are exactly five loot containers: workshop, library, tower, rubble niche, and archive. Ordinary caches provide modest crafting supplies, books and paper, with occasional amethyst or a spyglass, plus variant-themed discoveries (cherry saplings and pink petals, honeycomb and shears, dark oak, chains, and sweet berries). The archive guarantees one Golden Watch (`timestop:chronos_watch`) and one blank rune, plus materials. A separate 25% roll awards one Deflection or Snatching rune with equal weight. Diamond, Netherite, and Creative watches are excluded. Loot tables are persistent vanilla chest loot; emptying a cache and reloading does not refill it.

Three original journals sit on lecterns. They describe the failed observations and point toward both archive entrances.

## Datapack Customization

Observatory structures, loot, and spawn frequencies can be customized or overridden via standard datapacks:

- `data/timestop/structure/ruined_observatory/`: NBT structure templates.
- `data/timestop/loot_table/chests/observatory_*.json`: chest loot tables.
- `data/timestop/tags/worldgen/biome/has_structure/ruined_observatory_*.json`: biome eligibility tags.
- `data/timestop/worldgen/structure/ruined_observatory_*.json`: structure definitions.
- `data/timestop/worldgen/structure_set/ruined_observatories_*.json`: structure sets and spacing.

Templates use DataVersion 3955 and written books use item components. When authoring custom structure templates, keep pedestal NBT free of `Owner`, `Watch`, and `FieldId` so placed structures can be claimed fresh.

The lowest `deepslate_bricks` block in each template column marks its foundation start. Preserve those markers when altering architecture; use other deepslate variants for decorative blocks that should not generate downward foundation supports. The runtime piece precomputes the markers and clips placement and foundation writes to the current generation chunk.


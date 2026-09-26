# Clockwork pedestals

Five pedestal tiers are available in the Ultimate Time Stop creative tab and through `/give`: `timestop:copper_pedestal`, `golden_pedestal`, `diamond_pedestal`, `netherite_pedestal`, and `creative_pedestal` (all use the `timestop:` namespace).

## Use

1. Place a pedestal. Its placer owns the field for player-immunity purposes.
2. Right-click it with a watch of the same tier or lower. One watch moves into the pedestal, preserving its name, damage, settings, and rune NBT.
3. Right-click again to open the watch slot, mode buttons, and radius slider.
4. Supply redstone power, for example with an adjacent lever or redstone block. The field remains active while powered. Remove the signal or the watch to stop it.

Copper watches support Slow Motion and Fast Forward. Golden watches add Deceleration; Diamond, Netherite, and Creative add Time Stop. Pedestals do not activate watch runes, Matrix, SUPERHOT, or Rewind. Power is currently free: there is no fuel, duration limit, or cooldown.

The inserted watch determines the available modes, overlap priority, and maximum radius, adjustable down to one block in the menu. Pedestals use the actual `watch_radii` configuration: this checkout defaults to 8/14/22/32/64 blocks, overriding the larger fallback values shown in the older watch documentation. Raising those settings also raises pedestal limits. Even Creative pedestal fields stay local. Global watch-scope settings do not turn them into global effects. Speed uses the existing slow-motion and fast-forward configuration.

Each pedestal holds its watch inside three independently rotating armillary rings. An inserted watch spins and bobs gently; activating the field smoothly accelerates the assembly and brightens traveling light pulses. Empty rings settle into their display pose. Animation continues during local time stop and pauses with the game. Breaking the block drops its stored watch once. The menu is an unlocked container; hoppers cannot insert or extract watches.

Active fields also receive a continuous energy beam from the floating watch to the top of the sphere. Twin rotating filaments, upward pulses, clockwork rings, and expanding contact ripples use the inserted watch's tier color. The beam resizes with the field and stops when the field stops. The existing bubble-visuals toggle hides both the sphere and its beam.

## Player effects and overlapping fields

Operators can use `/timestop pedestal affectplayers` to inspect the world-wide setting. `/timestop pedestal affectplayers false` exempts every player from pedestal fields; `true` restores the default watch-style rules. The setting persists across server restarts and updates running fields immediately.

With player effects enabled, owners, synced friends, scoreboard allies, Creative/Spectator players, and players with the existing watch-resistance exemptions can act freely. Mobs and world mechanics remain affected. Deceleration slows hostile projectiles without requiring the owner to be online.

Each position uses one dominant field: higher watch tier first, then Time Stop at equal tier, then a stable field-ID tie-break. Rates never multiply across overlapping fields. Removing one field reveals the remaining effect; an entity still covered by the winning freeze stays frozen.

Pedestals operate only while their source chunk is loaded and do not load chunks themselves. The owner may log out. Global effects temporarily suppress local pedestal fields; powered sources resume afterwards. `/timestop stop` disarms loaded pedestals until their redstone power is switched off and back on. Rewind restores stored pedestal state and reconciles the resulting field.

## Crafting

- Copper: three copper ingots on the top row, a clock in the center, three stone bricks on the bottom row.
- Golden: Copper pedestal surrounded on its four cardinal sides by gold ingots.
- Diamond: Golden pedestal surrounded on its four cardinal sides by diamonds.
- Netherite: smith a Diamond pedestal with a Netherite Upgrade Smithing Template and a netherite ingot.
- Creative: creative inventory or commands only.

## Armillary sizes and placement

| Tier | Height | Maximum width |
|---|---:|---:|
| Copper | 0.9 blocks | 0.8 blocks |
| Golden | 1.15 blocks | 1.0 blocks |
| Diamond | 1.4 blocks | 1.2 blocks |
| Netherite | 1.75 blocks | 1.45 blocks |
| Creative | 2.0 blocks | 1.65 blocks |

Every pedestal still occupies one block. Its fixed collision and selection volume stays inside that block; the animated overhang is decorative. Leave the block above clear for Golden and higher tiers and use two-block center spacing for Diamond and higher tiers to avoid overlapping cages. Nearby walls can clip the decorative rings. The beam follows the watch at the center of the cage, even when a lower-tier watch is inserted.

## Rendering & Compatibility

Pedestal models and textures are packaged directly in the mod resources (`common/src/main/resources/assets/timestop/`). The native pixel density is one texture pixel per model unit, packed into a 64×64 atlas for each tier and state.

The dynamic renderer optimizes client performance by testing the full cage bounds against the camera view:
- Beyond 10 blocks: omits tiny dark tick marks.
- Beyond 20 blocks: uses the three metal bands without small fittings.
- Close-up: retains full high-detail geometry.
- Animation states are client-only and cleanly unloaded when their source block entities unload.

Install the matching Minecraft 1.21.1 loader's 1.6.1 JAR on both client and server. Forge uses network protocol 2 in this port; client and server versions must match. The armillary redesign requires no save migration or new runtime library. Existing worlds retain their watch data, and existing placed pedestals receive the new appearance automatically.


# Minecraft 1.21.1 — 1.6.3

- Restored per-entity tick budgeting for pedestal Slow Motion, including client projectile prediction.
- Pedestal Fast Forward no longer changes player movement, mining, use/cooldown speed, or client tick speed. Handheld watch Fast Forward keeps its existing behavior.
- Fixed Forge watch spheres jittering during movement by using game-tick interpolation instead of realtime frame duration.
- Fixed creative mode inventory tab displaying unlocalized `itemGroup.timestop` key.
- Fixed NeoForge combat crash when attacking entities in Slow Motion.
- Added entity-ticking/player-speed regression tests and moving watch-owner visual captures.

# Minecraft 1.21.1 — 1.6.2

- Fixed Forge drawing world effects at two render stages, creating a duplicate pedestal sphere and beam that moved with the camera.
- World effects now draw only after particles, using that stage's world transform.
- Added an isolated Forge client regression scene with GPU primitive-count assertions and camera-angle captures in Fancy and Fabulous graphics.

# Minecraft 1.21.1 port — 1.6.1

- Brought the 1.6.1 armillary pedestals, six observatory variants, Acropolis, and administrative rewind controls to Fabric, Forge, and NeoForge.
- Updated block interaction, block entity persistence, structure codecs, rendering, recipes, and journal books for 1.21.1.
- Registered pedestals, structures, menus, renderers, and network messages on all three loaders.
- Fixed the older port's Fabric configuration initialization, local rewind activation/cancellation, socketed rune identity persistence, consumed rune container restoration, and ricochets after lethal hits.
- Added isolated gameplay, dedicated-server, and client visual validation workflows.

# Changelog

All notable changes to the **Ultimate Time Stop** mod are documented in this file.

## [1.6.1] - 2026-09-25

### Added
- Three new redesigned Ruined Observatory variants:
  - **Cherry** (`timestop:ruined_observatory_cherry`): tailored for Cherry Groves with cherry wood timber, pink petal scatter, calcite accents, and a blossoming cherry tree.
  - **Floral** (`timestop:ruined_observatory_floral`): tailored for Flower Forests with birch timber, flowering azalea, garden flowers, and overgrown mossy masonry.
  - **Windswept** (`timestop:ruined_observatory_windswept`): tailored for Windswept Forests with dark oak/spruce alpine timber, wind-scoured stone, severe gale damage, and rugged terrain blending.
- Dedicated structure sets for all five variants (`ruined_observatories_highland`, `ruined_observatories_forest`, `ruined_observatories_cherry`, `ruined_observatories_floral`, `ruined_observatories_windswept`) with unique salts.
- Themed discovery loot across non-archive caches (cherry saplings, pink petals, honeycomb, shears, dark oak, chains, sweet berries).
- Admin command system to include or exclude Rewind mode (`/timestop rewind include <true|false>`, `/timestop allowrewind [true|false]`, `/timestop rewind on|off|enable|disable`):
  - Enabled by default (`true`).
  - When turned off / excluded: Rewind mode completely disappears from pocket watches (Diamond, Netherite, Creative), watch hover tooltips, and the Shift+Right-Click Mode Selection GUI (which dynamically shrinks from 4 rows to 3 rows). Active rewinds are halted and rewind commands are blocked.
  - When re-enabled / allowed: Rewind mode immediately reappears on watches and inside the GUI, seamlessly restoring previously selected watches.
  - World persistence via `TimeStopSavedData` (`RewindModeAllowed` tag) and client network synchronization via `SyncRewindAllowedPacket`.

### Changed
- Increased observatory natural discovery frequency by reducing placement spacing from 64 to 24 chunks (separation 8 chunks), bringing discovery radius to ~1,200–2,000 blocks and eliminating locate search latency.
- Independent structure set allocation ensures candidate chunks never fail due to cross-variant biome contention.

### Fixed
- **Fluid Slow Motion in Bubbles & Pedestals**: Resolved choppy 5 FPS entity movement stutter inside localized Slow Motion spheres by scaling client-side position and head-rotation lerp steps (lerpSteps / lerpHeadSteps) to match the server packet interval. Entities now glide and turn smoothly at full render framerate (60/120/144+ FPS).
- **Reduced Post-Hit Invulnerability in Slow Motion**: Eliminated the artificial 2-to-4-second combat immunity lockout when hitting slowed mobs. Mob invulnerableTime is now scaled down to 5 ticks (lockout window 2-3 ticks = 100-150ms) and hurtDuration/hurtTime scaled down to 2-3 ticks. In addition, invulnerableTime and hurtTime count down on every real-time server tick even when entity AI ticks are skipped, enabling players to execute rapid combos and attack at normal rhythm. Projectile damage immunity lockout in slow motion is reduced to zero.

## [1.6.0] - 2026-09-25

### Added
- Highland and forest Ruined Observatories in new Overworld chunks, with a collapsed copper dome, decorative telescope, unequal towers, workshop, library, and a hidden underground archive.
- Five persistent loot caches, three research journals, and three recoverable empty pedestals. The archive guarantees a Golden Watch and blank rune, with a 25% chance of one Deflection or Snatching rune.
- Shared Fabric/Forge structure registration, terrain screening, bounded foundations, vanilla locate/place commands, and datapack-configurable biomes, frequency, and loot.
- Reproducible editable NBT sources, route and resource validation, rotation/loot/terrain GameTests, and an isolated observatory visual-QA world. See `OBSERVATORY.md` for placement origins and commands.

### Fixed
- Rewind inventory observation leaves unopened chest loot deferred until ordinary container interaction.

## [1.5.3] - 2026-09-24

### Changed
- Redesigned all five Clockwork Pedestals as progressively larger armillary cages with three independently rotating, textured metal rings and a watch suspended inside.
- Watch displays and rings idle gently, accelerate smoothly while active, and settle when emptied. Tier-colored inlays pulse while powered; animation pauses with the game and continues during local time stop.
- Pedestal beams now originate at the actual watch position for each pedestal size, including lower-tier watches inside higher-tier cages.
- Updated inventory models, grouped Blockbench sources, reproducible previews, geometry validation, and isolated animation QA. Decorative overhang keeps single-block placement and collision.

## [1.5.2] - 2026-09-23

### Added
- Active pedestals feed their sphere with a continuous tier-colored energy beam: a luminous core, rotating twin filaments, traveling pulses, clockwork emitter rings, and ripples along the sphere at the contact point. The beam follows the configured radius and disappears with the field; no shader or animation dependency is required.

## [1.5.1] - 2026-09-23

### Fixed
- Pedestal radius sliders follow mouse dragging and release correctly through the container screen.
- TacZ bullets are intercepted at the first active Time Stop boundary before native collision damage, including piercing impacts. Stronger overlapping fields and explicit flowing-projectile exemptions retain their existing behavior.
- Boundary-suspended projectiles use consistent center-position checks when freezing and resuming.

## [1.5.0] - 2026-09-23

### Added
- Five Clockwork Pedestals with distinct models, pixel textures, active indicators, and floating watch displays.
- Redstone-powered stationary Slow Motion, Fast Forward, Deceleration, and Time Stop fields with watch-tier restrictions and adjustable range.
- A watch-slot/settings menu, survival upgrade recipes, editable Blockbench sources, and reproducible asset previews.
- `/timestop pedestal affectplayers [true|false]`, a persistent operator setting for pedestal player immunity.
- Pedestal GameTests and an isolated client visual-QA task.

### Changed
- Equal-strength overlapping fields use a shared deterministic tie-break on server and client.
- Local stasis filters random block ticks at each position rather than stopping entire intersecting chunks. Pedestal acceleration also affects crop random ticks.
- `/timestop stop` disarms loaded pedestals until their redstone power is cycled.
- Stationary fields survive owner logout and reconcile after chunk loading or rewind. Forge networking protocol is now 9; clients and servers must use matching builds.

See [PEDESTALS.md](PEDESTALS.md) for controls, recipes, compatibility, and asset tooling.

## [1.4.5] - 2026-09-18

### Added
- **Temporal Rewind Engine**:
  - Full server-wide timeline recording and rollback system across all loaded dimensions.
  - **Burst Mode**: Instantaneous time reversal for rapid tactical resets.
  - **Continuous Mode**: Smooth reverse temporal playback at 1 recorded tick per server tick, pausing world simulation while rewinding.
  - **Rune of Rewind & Auto Death Protection**: Socketable rune that intercepts fatal damage and rewinds the timeline before death, restoring player inventory, health, and reversing the fatal event.
  - **Comprehensive State Rollback**:
    - Block & Block Entity restoration: containers, furnaces, hoppers, and pistons reverse in true chronological order without dropping duplicate items.
    - TNT & Explosion reversal: un-ignites lit TNT, cancels blast block damage, and coalesces multi-explosion shockwaves.
    - Living Entity restoration: revives dead mobs with original equipment, health, NBT, and position.
  - **Visual & Audio Effects**: Inward-imploding animated block reconstruction, particle spirals, reverse audio chimes, and smooth player/mob unfolding animations.
- **Administrative Buffer & Rewind Commands**:
  - `/timestop buffer` / `/timestop rewind buffer`: Inspect buffer status (recorded frames, memory usage, and capacity).
  - `/timestop buffer reset` / `/timestop rewind buffer reset`: Reset timeline buffer to 30s default, clear history frames, cancel active rewinds, and save configuration.
  - `/timestop buffer clear` / `/timestop rewind buffer clear`: Clear recorded history frames while keeping the configured capacity.
  - `/timestop rewind [seconds]`: Trigger immediate rewind, respecting configured burst or continuous mode.
  - `/timestop rewind mode <burst|continuous>`: Switch between Burst and Continuous rewind modes.
  - `/timestop rewind ondeath [true|false]`: Toggle or inspect automatic death rewind. When enabled (`true`), any player taking fatal damage is automatically rewound to safety without requiring or consuming a Rune of Rewind.

### Changed
- **Empty Buffer Handling**: Continuous rewind now cleanly terminates immediately when recorded history is exhausted, preventing players from being stuck frozen in stasis.
- **Zero-Frame Guard**: Initiating rewind with 0 recorded frames cancels stasis immediately without consuming watch cooldown, notifying the player.
- **Localized Bubble Safety**: Fixed initiator boundary filtering so players rewinding within localized bubbles do not freeze upon reaching the sphere edge.

## [1.4.0] - 2026-09-15

### Added
- **Minecraft 1.21.1 Port**: Full engine port to Minecraft 1.21.1 and Java 21 for Forge (52.1.16+) and Fabric (0.16.10+).
- **Speed Calibration GUI**: In-game configuration interface (press H or access via Watch GUI) allowing players to customize Slow Motion, Matrix, and Superhot speeds with dynamic multipliers and network synchronization.
- **Fast Forward Block Entity Acceleration**: Block entities (furnaces, blast furnaces, smokers, brewing stands) tick at 5x acceleration during Fast Forward mode.
- **Configurable Superhot Mob Tint**: Added HOSTILE, PASSIVE, and ALL entity tint filtering options in the Settings GUI.

### Fixed
- **Fast Forward Celestial Sky Progression**: The celestial sky (sun, moon, stars) now moves at normal real-time speed (20 TPS) while inside localized Fast Forward bubbles, and flows smoothly at the calibrated speed during global Fast Forward.
- **Third-Person Perspective Shader**: Prevented vanilla post-processing clearing when cycling camera views (F5) during stasis or Dead Eye aiming. Custom shaders remain continuously active across all camera angles.
- **Server Tick Timing**: Resolved watchdog overload warnings in `MinecraftServerMixin` during Fast Forward execution.
- **Superhot Motion Responsiveness**: Free cursor and mouse aiming now maintain full stasis without falsely triggering motion acceleration or displaying motion indicators.
- **Superhot Idle Stasis**: Restored authentic near-zero crawl idle rate (down to 1 TPS / 1000ms delay) scaling with user configuration.
- **Bubble Clock Dilation**: Resolved mob jitter, stuttering, and player movement lag during localized bubble time dilation by synchronizing server and client delta trackers.
- **Superhot Visual Overlays**: Cleaned up entity layer rendering so weapons, shields, armor, and glowing eyes render naturally while mobs flash on damage.
- **Superhot HUD**: Reverted HUD to a clean 2-state status indicator (TIME FROZEN vs TIME IN MOTION).

## [1.3.12] - 2026-09-11

### Added
- **Timeless and Classics Zero (TACZ) Compatibility**: Full soft-dependency integration for firearms, kinetic bullets, and heavy ordnance across time stop, localized bubbles, and deceleration fields.
- **Rune of the Marksman & Chrono Coins**: Toss coins and shoot them with firearms to trigger lethal chained ricoshots (+RICOSHOT). Earn coin charges through combat kills.
- **Rune of the Kinetic Barrier**: Stop incoming bullets and projectiles in mid-air; release to drop or attack to launch a return volley.
- **Rune of Vector Control**: Dynamically steer deflected bullets and barrier volleys along crosshair aim.
- **Dead Eye Native Firearm Integration**: Lock-on aiming, burst-fire tracking, and native audio/visual feedback for TACZ guns.

### Changed
- **Soft-Dependency UI Isolation**: Marksman rune, Chrono Coins, crafting recipes, charge HUD overlay, and the coin flip keybind automatically hide when TACZ is not installed.
- **Projectile Stasis Flow**: Added projectile flow stasis mode toggleable in temporal settings.

## [1.2.1] - 2026-09-02

### Fixed
- **Client Performance & Transposition Lag**: Replaced per-frame spatial queries and raycasts in `TranspositionRenderer` with tick-cached candidate verification and upfront rune possession checks in `EntityMixin`, eliminating severe FPS drops.
- **Dedicated Server Tickrate Stability**: Decoupled `getServerTickMs()` from local temporal bubbles so localized spheres no longer throttle the dedicated server tickrate to 5 TPS.
- **Global Time Stop Priority**: Fixed stasis exemption logic so entities outside local bubbles remain frozen when global server-wide time stop is active.
- **Chunk & Environment Stasis**: Fixed 3D chunk boundary intersection for localized stasis and corrected sun/moon and weather freezing logic during global time stop.
- **Combat Balance & Exploits**:
  - Tied Phasing Rune projectile immunity strictly to evasion cooldown readiness.
  - Eliminated infinite item duplication when snatching hostile projectiles (`WitherSkull`, `ShulkerBullet`, `DragonFireball`, `LlamaSpit`).
  - Preserved custom potion effects on tipped arrows and enchantments/durability on thrown tridents.
- **Deceleration Field Search**: Optimized projectile checking to iterate dimension players directly with radius guards instead of spatial chunk entity searches.
- **Networking & Packet Security**:
  - Added server-side creative permission validation to `SetWatchScopePacket` to prevent unauthorized scope escalation.
  - Sanitized coordinates, normalized vectors, and enforced a 6-block reach validation in `KineticBlockPunchPacket`.
  - Removed premature client-side entity disposal in `ClientInteractionHandler` to maintain server authority.
- **Memory Management & Lifecycle**:
  - Converted static entity registries (`TimeStopManager`, `TemporalDamageBuffer`, `TemporalKineticBlockManager`) to `WeakReference` with multi-dimension search.
  - Added disconnect and server stopping listeners to strip leftover Matrix speed modifiers, collapse abandoned bubbles, and clear static caches.
  - **Time Sync & Resonators Overhaul**: Replaced the legacy friend/party system with the technical **Time Sync** system. Added `/sync` and `/timesync` commands with offline resonator disconnection and automatic migration of legacy saved data.

## [1.2.0] - 2026-09-01

### Added
- **Localized Temporal Bubbles**: Pocket watches in survival now project localized spherical temporal fields scaled to their tier (Copper: 10m, Gilded: 16m, Diamond: 24m, Netherite: 32m, Creative: 100m or Server-wide).
- **Multi-Bubble Concurrent Simulation**: Multiple players can independently activate and run temporal fields simultaneously across the server without performance degradation.
- **Multiplayer Time Sync and Resonators System**: Integrated `/sync` and `/timesync` commands with interactive chat invitations. Whitelisted Resonators and scoreboard teammates can freely move and act within active temporal fields.
- **Citadel and Entity Animation Compatibility**: Full stasis compatibility with Citadel, Alex's Mobs, and Alex's Caves animated multipart entities.
- **In-Game Settings Screen**: Added a configuration interface accessible via the watch menu to customize client and gameplay options.

### Changed
- **Superhot Mode Dynamics**: Refined movement-based time dilation to seamlessly scale world speed based on player actions (sprinting, jumping, falling, and attacking).
- **Kinetic Stasis Discharge**: Improved velocity calculation and network synchronization for multi-hit accumulated damage and knockback vectors upon stasis exit.
- **Visual and Shader Pipeline**: Optimized post-processing shaders and entity overlays across both localized spheres and global mode.

---

## [1.1.1] - 2026-08-29

### Added
- **SUPERHOT Visual Pipeline**: Authentic architectural off-white world shaders with bold geometric contour lines and pure crystal-red enemy rendering.
- **Spatial Transposition Outlines**: Native Minecraft glowing outlines for swap targets (arcane violet for single swap, luminous gold for dual swap).
- **Fullscreen Dead Eye Sepia**: Polished cinematic sepia grading with balanced clarity and zero dark-center vignetting.
- **Rune Real-Time Network Sync**: Instant optimistic UI updates and bidirectional socket synchronization between client and server.

### Changed
- **Watch GUI Modernization**: Minimalist, tier-adaptive selection screens customized for Copper, Golden, Diamond, Netherite, and Creative Watches.
- **Classic Watch Nomenclature**: Simplified watch naming to Copper, Golden, Diamond, Netherite, and Creative Watch.
- **Transposition Arrow Physics**: Swapping with projectiles now inverts trajectory and orientation 180°, reflecting arrows back towards foes.

### Fixed
- Fixed dirt cliffs, flowers, and terrain mistakenly turning red in SUPERHOT mode.
- Fixed directional lighting causing SUPERHOT mobs to appear half-red and half-white.
- Fixed SUPERHOT enemies flashing white upon taking damage by suppressing vanilla hurt overlay.
- Fixed item tossing snapping back to player inventory during time dilation and stasis.
- Fixed death particle jittering during time stop.
- Fixed dropped item pickup mechanics during time stop.

---

## [1.1.0] - 2026-08-28

### Added
- **Tiered Chronos Watches**:
  - **Copper Chronometer (Tier 1)**: Early-game survival watch (6s duration, 25s cooldown; Slow Motion & Fast Forward).
  - **Gilded Chronos Watch (Tier 2)**: Mid-game watch (10s duration, 18s cooldown; 3.5m bullet-dodge passive, 1 Rune Socket, unlocks Deceleration Field & Superhot).
  - **Diamond Chronos Watch (Tier 3)**: Late-game watch (14s duration, 12s cooldown; 4.5m bullet-dodge passive, 1 Rune Socket, unlocks Matrix & Time Stop).
  - **Netherite Chronos Sovereign (Tier 4)**: End-game watch (20s duration, rapid 6s cooldown, fire-resistant; 5.5m bullet-dodge passive, 1 Rune Socket, all modes unlocked).
  - **Infinite Chronos Watch (Tier 5)**: Creative/Admin exclusive watch with infinite duration, zero cooldown, and all modes unlocked.
- **12 Temporal Runes & Socketing System**:
  - Socketable into Tier 2–4 watches by sneak-right-clicking or through the Watch GUI.
  - **Blank Temporal Rune**: Base crafting slate infused with chronal resonance.
  - **Rune of Redirection**: Automatically parries incoming projectiles back at attackers at supersonic speeds.
  - **Rune of Snatching**: Automatically catches incoming projectiles and places them into your inventory.
  - **Rune of Phasing**: Automatically blinks/teleports you away from incoming projectiles upon imminent collision.
  - **Rune of Kinetic Amplification**: Supercharges melee strikes during stasis with 2.5x launch force.
  - **Rune of Chrono-Vampirism**: Siphons temporal duration from struck enemies, extending active freeze time up to double duration.
  - **Rune of Volatile Stasis**: Imbues struck frozen projectiles and falling blocks with delayed kinetic bomb blasts upon time resumption.
  - **Rune of the Tachyon**: Accelerates mining speed (3x) and attack recharge in Slow-Mo & Matrix modes.
  - **Rune of the Dead Eye**: Time dilates when aiming ranged weapons. Sweep your crosshair to paint up to 6 targets, then release a supersonic arrow volley.
  - **Rune of Voltaic Ricochet**: Arrows fired in stasis ricochet between nearby targets like chain lightning.
  - **Rune of Orbital Redirection**: Captures incoming projectiles into a spinning shield halo around you; launch them at nearest foes on command.
  - **Rune of Spatial Transposition**: Instantly swaps positions with any targeted entity or projectile within line of sight.
- **New Temporal Modes**:
  - **SUPERHOT**: Time moves only when you move! Standing still completely freezes the world; walking or looking around lets time flow.
  - **DECELERATION FIELD**: Normal world speed, but creates a passive temporal bubble slowing incoming projectiles by 80% within radius.
- **New Keybindings & Controls**:
  - `V`: Toggle Time Mode / Active Freeze.
  - `R`: Release Orbiting Projectiles.
  - `G`: Spatial Transposition Swap ("Boogie Woogie").
  - `Shift + Right-Click`: Opens the new graphical **Time Mode Selection Screen** or cycles modes.
- **HUD & Visual Overlays**:
  - **Chrono Meter**: Clean action bar / HUD element showing active mode and remaining duration/recharge bar.
  - **Captured Projectiles Overlay**: Displays the current count of orbiting shield projectiles.
  - **Dead Eye HUD**: Visual crosshair target-lock reticles on marked enemies.
  - **Transposition HUD**: Visual target indicator for swapping positions.
- **Audio & Combat Polish**:
  - Added spatial temporal audio cues and heartbeat sounds (`ChronoAudioHandler`).
  - Added kinetic block punching (launching primed blocks through the air).
  - Added projectile slapping and interception interactions.
  - Complete crafting recipes for all 4 survival watches and 12 runes.

### Changed
- Refactored watch architecture into a clean tiered hierarchy (`AbstractWatchItem` and `WatchTier`).
- Improved recipe progression: Copper Watch upgrades into Gilded Chronos Watch, which upgrades into Diamond Watch, which upgrades into Netherite Sovereign via Smithing Table.
- Streamlined `README.md` into a classic, minimalistic format focused on gameplay and usability.
- Renamed mod to **Ultimate Time Stop**.

### Fixed
- Fixed mob jitter and vibrating geometry during Time Stop via frame interpolation clamping (`LevelRendererMixin`, `EntityRenderDispatcherMixin`).
- Fixed celestial bodies (sun and moon) shaking due to client time drift (`ClientLevelMixin`).
- Fixed projectiles stopping prematurely in Slow Motion and Matrix modes (`ProjectileMixin`).
- Fixed burning fire animation on mobs and blocks continuing during time stop by pausing texture atlas animations (`TextureAtlasMixin`).
- Fixed weapon attack recharge, eating, and bow drawing running slowly in Matrix mode (`PlayerMixin`).
- Completely purged all vanilla potion effects; all modes now execute through genuine engine tick rate modulation (`MinecraftServerMixin`, `TimerMixin`).

---

## [1.0.0] - 2026-08-26

### Added
- Initial release of the Mixin-based Time Stop mod for Minecraft 1.20.1 (NeoForge / Forge).
- Core Mixin engine intercepting `ServerLevel`, `ClientLevel`, `Level`, and `LevelRenderer`.
- 4 Initial Modes: Time Stop, Slow Motion, Matrix, Fast Forward.
- Chronos Pocket Watch (Survival) and Infinite Chronos Watch (Creative).
- Kinetic force accumulation and simultaneous shockwave discharge.
- Solid liquid walking on water and lava surfaces during Time Stop.
- Mid-air projectile stasis.

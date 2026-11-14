# Minecraft 1.21.1 — 1.8.4

- Rune of the Tachyon: the documented 3x mining speed in Slow Motion / Matrix now actually applies (it was missing on all 1.21.1 loaders).
- Fast Forward above 5x: inside your own watch field the server now runs your personal clock at the same capped rate as your client (5x), instead of the raw configured rate. Eating, hunger, effects and cooldowns no longer run ahead of what you see.
- Joining a server no longer changes your own settings: a server's speed calibration and swimming setting are used only while you are connected, are never written to your config file, and your own values return when you leave.
- The cloud freeze now lives in its own optional mixin, so a renderer that replaces cloud rendering cannot stop the game from starting (this crashed with Sodium 0.5.x on 1.20.1).
- Fast Forward movement speed no longer blocks other mods that also adjust player speed while no Fast Forward field is active.
- Forge protocol (6) and NeoForge payload version (1.8.3-casual-acceleration) unchanged: 1.8.3 and 1.8.4 can play together.

# Minecraft 1.21.1 — 1.8.3

- Self Acceleration now also speeds up harmful processes on your personal clock: drowning (and recovering air), burning, freezing, harmful effects, starvation and the hit-immunity window, plus falling (jump height unchanged; fall speed capped below the server movement check).
- Fast Forward: inside your own watch field the server now runs your personal clock too (eating, attack recharge, cooldowns, effects, healing, hunger, breath, burning, freezing, mining), matching your already-fast client.
- New **Casual acceleration** setting (H → Visuals & FX): harmful processes keep normal speed while you are accelerated; helpful ones still speed up. Per-player, saved in your client config.
- Mode descriptions corrected: Slow Motion slows you too (only attack recharge, item use and cooldowns keep real-time speed); Matrix shows its real calibration (default 25%, previously said 20%) and 4x movement/attack; all speed-based modes show current calibration values.
- The **B** variant hotkey now changes the watch that **V** activates (previously it could edit a worn watch while V used the held one).
- Fast Forward in your own watch field no longer adds extra attack/cooldown/item-use ticks on the client, whose clock already runs fast (those ran twice there).
- Forge protocol 6 and NeoForge payload version 1.8.3-casual-acceleration (new serverbound Casual setting packet); use 1.8.3 on both client and server.

# Minecraft 1.21.1 — 1.8.2

- Self Acceleration now speeds up your whole personal clock at the Fast Forward rate, not just movement: mining and the delay between broken blocks, placing/using delay, eating, drinking and item use (bows, crossbows, shields), attack recharge, item cooldowns, status-effect timers, hunger and natural healing. Gravity, breath, fire and the world keep normal time.
- Easier variant switching: Slow Motion and Fast Forward cards show **World · Combat** / **World · Self**. Click a word to pick it; scroll, right-click or press the card's number again to switch while keeping the watch open. The watch remembers your last variant per card. New **B** hotkey (Switch Time Variant) flips the variant without opening the watch.
- Mode descriptions now match actual behavior and show the current Speed Calibration values (previously hard-coded 25% / 500%).

# Minecraft 1.21.1 — 1.8.1

- Fixed a startup crash with Lithium (`alloc.chunk_random`, reported on NeoForge 21.1.265): both mods redirected the same random-tick calls in `ServerLevel.tickChunk`. Time Stop now hooks `BlockState.randomTick` / `FluidState.randomTick` instead, so it composes with any mod that rewrites chunk ticking.
- Random-tick behavior is unchanged: frozen, slowed, accelerated and rewinding areas apply the same rates as before. Forge protocol and NeoForge payload version unchanged; 1.8.0 and 1.8.1 can play together.

# Minecraft 1.21.1 — 1.8.0

- Brought the 1.20.1 Prediction release across Fabric, Forge and NeoForge: animated white paths, mob/projectile silhouettes, held and enemy shots, distant arcs, swept interceptions, potion/cloud and imminent explosion cues, plus selected Enderman teleport tells.
- Added safe Prediction arrow catching/redirection, preserved pickup components and synchronized ownership.
- Added Combat Slow Motion, Self Acceleration and the synchronized stopped-time swimming setting using the existing watch cards/settings.
- Restored configurable local/global rewind playback speed, model-based Dead Eye head bounds, rewind mob transforms and the redstone rollback callback guard. Fast global playback stops at its requested history depth.
- Retained newer Sable, native TaCZ and accessory integrations; adapted components, physics capture, rendering and clocks to native 1.21.1 APIs.
- Release uses Forge protocol 5 and NeoForge payload version 1.8.0-prediction-variants. See [the comparison report](PARITY_1_20_1_TO_1_21_1.md) for scope and limits.

# Minecraft 1.21.1 — 1.6.6

- Reworked Deceleration Field to slow only projectiles within the selected watch/pedestal bubble, or everywhere in global mode. Owner-fired projectiles are included.
- Use the configured `speeds.decelerationDrag` as the projectile time rate (default 0.10). Players, mobs, TNT, blocks, and the server clock stay at normal speed, including players with lower-tier watches.
- Apply native movement, gravity, drag, acceleration and collision over the slowed step. Preserve momentum on exit, and sweep fast arrows and TaCZ bullets through field boundaries.
- Added a native tick budget for other projectile physics models and dedicated gameplay checks with actual TaCZ 1.1.8 bullets, vanilla projectile families, impacts, boundary crossing and unaffected entities.

# Minecraft 1.21.1 — 1.6.5

- Fixed Sable structures rapidly shaking/spinning in Slow Motion: stasis constraints now use the inverse rotation required for their world endpoint and anchor at the merged center of mass.
- Keep the native physics pose and published pose synchronized during skipped substeps, preventing terrain corrections from leaking into the next resumed step.
- Rewind uses the same corrected stasis constraint when rebuilding a held body.
- Expanded NeoForge regression coverage to rotated falling structures, native pose agreement, angular speed, settled motion and a body tilted on all three axes.

# Minecraft 1.21.1 — 1.6.4

- Fixed Sable structures disappearing after landing inside a time bubble: static terrain support is no longer buffered and replayed as incoming collision momentum.
- Stasis release now restores saved velocity exactly before applying buffered impacts, preventing solver residue from accumulating across slow-motion substeps.
- Added isolated NeoForge gameplay coverage for command-equivalent assembly/teleport, bubble entry, rotated ground contact, and resume in all six non-rewind modes plus a normal-time baseline.

# Minecraft 1.21.1 — 1.6.3

- Ported the Creative Watch rune matrix from 1.20.1: independently toggle all 15 runes, or enable/disable them together. Compatible abilities apply together, including Kinetic and Volatile Stasis.
- Added a Creative Watch bubble radius editor with slider and numeric input from 1 to 512 blocks. The saved radius applies to the next local activation, including local rewind.
- Fixed command and Vector rune aiming: redirected arrows converge on the block or entity under the crosshair instead of flying parallel to the player's view. Precision shots fly straight until impact.
- Added the Rewind `Self` toggle. `Self: OFF` preserves the initiating player's position, health, and inventory while the world rewinds, in local/global and burst/continuous rewind.
- Added `/timestop rewind exempt <true|false>` and server policy `/timestop rewind allowexempt <true|false>`, with policy synchronization on all loaders.
- Preserved old Creative Watch socket abilities during migration, returning the physical rune once the matrix is configured. Watch preferences survive inventory rewind.

# Minecraft 1.21.1 — 1.6.1

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

## TaCZ 1.21.1 compatibility candidates

- Added optional native Fabric bullet/shot hooks and restored Forge shot-task registration; tested NeoForge against 21.1.233 and MUKSC r7.
- Scope every scheduled native round safely, preserve piercing and momentum, apply ricochet after native damage, consume volatile impacts once, and retain ammunition/ownership through native combat interactions.
- Use native animation/sound APIs for firearm Dead Eye without another firing request or ammunition consumption.
- Correct field-boundary position synchronization for remote clients and reconnects. Preserve current aim before native collision sweeps.
- See the [verification matrix and reproducible commands](integration/readiness/TACZ_PARITY.md) for selected dependencies, automated results, and the remaining gameplay acceptance checklist. These are candidate builds; full parity is not yet claimed.

All notable changes to the **Ultimate Time Stop** mod are documented in this file.

## [Unreleased] - Sable 2.0.5 & Companion 1.6.0 Compatibility

### Readiness fixes in progress
- Reject invalid global rewind activation before collapsing existing bubbles, and retain history when a required ship unloads before a playback frame can be applied.
- Discard pre-mount deck anchors so dismounting on a moving or rotating ship cannot snap riders back to their old position.
- Enforce the retained-history budget immediately when history length shrinks during local rewind.
- Record native assembly/disassembly world counterparts and rewind their blocks and container contents with the ship, including locations outside the sphere. Preserve those locations throughout continuous playback and reject conflicting jobs.
- Refresh inventory history after native block movement so intermediate container edits rewind to the moved contents instead of an empty construction-time baseline.
- Restore chest contents and scheduled block/fluid ticks when ship plots are reallocated; remove spent rewind runes from full structural checkpoints.
- Reconcile existing ship structures against recorded checkpoints, including native assembly changes that bypass vanilla block-change recording.
- Retain split dependencies after Sable clears its notification; local rewind restores the parent and split child together across the sphere boundary. Keep selected plot ticks suppressed outside the sphere during playback.
- Added 1/16/64-body cycle tests and combined original/Sable regression runs; short maximum-rate performance limits are documented in SABLE_BENCHMARK.md.
- Integrate native contact forces over each physics substep, retain original collision effects, and release buffered impacts once. Preserve explicit impulses through pure global stasis.
- Suppress physics and incoming impulses on ships controlled by rewind. Account for playback-owned history after timeline eviction.
- Restore world-anchor joints and runtime motor, limit, frame, axis-lock and contact settings; exclude temporary stasis holds from history.
- Support the configured 50x rate at eight native substeps without a hidden 200-step ceiling, while publishing actual time-scaled displacement velocities.
- Reject missing/unloaded ship dependencies before rewind mutation and reserve unloaded plots when allocating restored ships (validation in progress).
- Added actual native-contact, playback, joint-state and full-rate regressions plus isolated dedicated-server startup checks. Full feature acceptance remains in progress.

### Fixed
- Corrected collision lever arms for both endpoints by converting native offsets to plot coordinates before subtracting COM; impulses remain in local axes for Sable's release API.
- Preserved separate identities for every body-to-body joint. Rewind now reconciles all saved joints after restoring bodies, preserves endpoint order, copies saved configurations and remaps anchors when plots change.
- Corrected shared plot memory accounting to subtract only duplicate NBT costs, preserving each snapshot's unique charge. Frame/snapshot totals now use 64-bit counters.
- Changed plot deduplication to weak references with queue pruning and removal cleanup, so the lookup cannot retain evicted checkpoints.
- All 32 GameTests and five Common unit tests pass; Fabric/Forge/NeoForge compilation passes.

### Added
- **Optional Sable Compatibility (in progress)**: Integration targeting Sable 2.0.5 / Companion 1.6.0 on Fabric and NeoForge, with standalone Forge support. Gameplay acceptance is still in progress (experimental).
- **Per-Body Temporal Physics (`SableBodyTimeManager` & `SablePhysicsSystemMixin`)**:
  - Rigid bodies evaluate their motion rate from their world Center of Mass, supporting both global time stop and localized temporal bubbles.
  - Native per-substep scheduling via `onSubstepBegin` connected to Rapier physics iterations.
  - Dynamic substep loop expansion (`totalLoopSubsteps`) supporting accelerated rates (up to 5x Fast Forward) while holding unaccelerated bodies during surplus substeps.
  - Strict spherical field boundary evaluation: bodies exiting a field vertically or horizontally return immediately to 1x rate without column retention.
  - SUPERHOT motion responsiveness scaling with owner movement and activity.
  - Bodies in local stasis use temporary fixed world constraints in Rapier and retain pre-stasis velocities.
  - Gating of actors, lift providers, reaction wheels, and queued force applications during stasis.
  - External impulse hooks and constraint tracking hooked in `SableRapierPipelineMixin`.
  - Native contact normal indices and local torque coordinate conversion corrected. Native contact magnitude/sign, substep aggregation and generation/release acceptance remain to be validated end to end.
- **Spatial World Bridging & Deck Transport (`SableWorldBridge`)**:
  - Two-way coordinate projection between parent worlds and isolated plot grids.
  - Frozen unmounted and mounted entity deck transport with corrected rider positioning (`vehicle.positionRider(entity)`) and stale deck anchor cleanup on entity removal.
  - Cross-sublevel block entity resolution (`getBlockEntityAtWorld`) allowing interaction, visual beam roots, and queries across parent and plot boundaries.
  - Dimension-isolated plot bounds checking and dimension-aware remapping keys (`PlotKey(dimension, center)`).
- **Mobile Pedestals on Moving Ships**:
  - Active pedestals mounted on moving vessels synchronize their `TemporalBubble` centers in real time without recreating fields or resetting buffered damage.
  - Spatialized audio (activate, deactivate, insert, ambient sounds) and beam LOD rendering projected into world space.
- **SubLevel Rewind Compatibility (`RewindExecutor`, `TickRecorder`, `RewindScope`, `LocalRewind`)**:
  - Structural plot NBT and constraint data added to snapshots. Recursive NBT accounting includes the tested large inventory payload; shared tags retain separate snapshot costs and the dedup cache does not own tag lifetime. Full playback-memory/performance acceptance remains pending.
  - Plot NBT loading restores unchanged blocks in the tested plot-reuse case via `ServerLevelPlot.load(CompoundTag)`.
  - Removed-body last-live Center of Mass recorded on removal and evaluated in `RewindScope.filter`.
  - Historical joint selection and reconstruction include separate identities for multiple body-to-body joints, post-target removal, ordered endpoints and plot-anchor remapping. Runtime motor/limit state and world-anchor coverage remain pending.
  - Continuous fixed playback membership in `LocalRewind.Job`, preventing assembly drifting across frames and rejecting conflicting concurrent rewinds across connected bodies.

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

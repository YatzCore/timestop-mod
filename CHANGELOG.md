# 1.8.4 — Sodium crash, Tachyon mining and config fixes

- Fixed a startup crash with Sodium 0.5.x on Fabric 1.20.1 ("Mixin transformation of LevelRenderer failed"): the cloud freeze now lives in its own optional mixin, so a renderer that replaces cloud rendering can no longer stop the game from starting.
- Rune of the Tachyon: the documented 3x mining speed in Slow Motion / Matrix now actually applies on Forge (it already worked on Fabric).
- Fast Forward above 5x: inside your own watch field the server now runs your personal clock at the same capped rate as your client (5x), instead of the raw configured rate. Eating, hunger, effects and cooldowns no longer run ahead of what you see.
- Joining a server no longer changes your own settings: a server's speed calibration and swimming setting are used only while you are connected, are never written to your config file, and your own values return when you leave.
- Network protocol unchanged (Forge 19): 1.8.3 and 1.8.4 can play together.

# 1.8.3 — Accelerated harm, falling, Fast Forward personal clock and Casual mode

- Self Acceleration now also speeds up harmful processes on your personal clock: drowning (and recovering air), burning, freezing, harmful effects, starvation and the hit-immunity window, plus falling (jump height unchanged; fall speed capped below the server movement check).
- Fast Forward: inside your own watch field the server now runs your personal clock too (eating, attack recharge, cooldowns, effects, healing, hunger, breath, burning, freezing, mining), matching your already-fast client.
- New **Casual acceleration** setting (H → Visuals & FX): harmful processes keep normal speed while you are accelerated; helpful ones still speed up. Per-player, saved in your client config.
- Mode descriptions corrected: Slow Motion slows you too (only attack recharge, item use and cooldowns keep real-time speed); Matrix shows its real calibration (default 25%, previously said 20%) and 4x movement/attack; all speed-based modes show current calibration values.
- The **B** variant hotkey now changes the watch that **V** activates (previously it could edit a worn watch while V used the held one).
- Forge protocol 19 (new serverbound Casual setting packet); use 1.8.3 on both client and server.

# 1.8.2 — Self Acceleration personal clock and variant switching

- Self Acceleration now speeds up your whole personal clock at the Fast Forward rate, not just movement: mining and the delay between broken blocks, placing/using delay, eating, drinking and item use (bows, crossbows, shields), attack recharge, item cooldowns, status-effect timers, hunger and natural healing. Gravity, breath, fire and the world keep normal time.
- Easier variant switching: Slow Motion and Fast Forward cards show **World · Combat** / **World · Self**. Click a word to pick it; scroll, right-click or press the card's number again to switch while keeping the watch open. The watch remembers your last variant per card. New **B** hotkey (Switch Time Variant) flips the variant without opening the watch.
- Mode descriptions now match actual behavior and show the current Speed Calibration values (previously hard-coded 25% / 500%).

# 1.8.1 — Lithium compatibility fix

- Fixed a startup crash with Lithium (`alloc.chunk_random`): both mods redirected the same random-tick calls in `ServerLevel.tickChunk`. Time Stop now hooks `BlockState.randomTick` / `FluidState.randomTick` instead, so it composes with any mod that rewrites chunk ticking.
- Random-tick behavior is unchanged: frozen, slowed, accelerated and rewinding areas apply the same rates as before. Network protocol unchanged; 1.8.0 and 1.8.1 can play together.

# 1.8.0 — Combat slow motion, self acceleration and stopped-water swimming

- Add Combat Slow Motion and Self Acceleration as alternate choices on the existing watch cards; keep old mode ordinals, original modes and compact layouts.
- Combat slows mob movement/AI and all projectile flight using continuous physics while players and non-combat world processes retain native clocks.
- Self scales only the owner's voluntary movement through native collision, without persistent attribute/ability changes or world acceleration.
- Add an operator/host-controlled Swim in stopped time toggle using the existing frozen-fluid collision preference. Persist and synchronize it on changes and joins.
- Share existing speed sliders with the corresponding variants; add command aliases. Forge protocol 18, Minecraft 1.20.1 only.

# 1.7.5 — Body collisions and arrow catching (Minecraft 1.20.1)

- Enable right-click catching during Prediction through the client key route, native interaction callback and validated custom packet. Preserve recovered arrow potion data and reject duplicate, distant, covered or inactive-field catches.
- Forecast mob bodies against loaded terrain using native collision and step selection, including the loader's actual step height. Copy native step height and jump boost without creating attribute instances. Keep gravity and planned jumps independent from ground navigation; slide along walls and settle onto floors after route steps.
- Solve arrow interceptions against those same future bodies. Preserve sharp turns and timed positions when compressing long trajectories, keeping exact contacts within the existing packet budget. Interpolate the cropped flight origin’s time so arrow silhouettes advance consistently.
- Retain the white holograms, overlay limits, movement clocks; carry immutable step height with Forge protocol 17. Minecraft 1.21.1 is unchanged.

# 1.7.4 — Holograms and impact outcomes (Minecraft 1.20.1)

- Refine animated white paths with distance-based flow, softer edges, translucent scan bands, bounded model outlines, and near-camera fades. Batch model draws and preserve anchored future contacts.
- Align block landing reticles to their collision surface. Preserve arrow redirection, distant flight, native archer preparation and Enderman tells.
- Preview splash-potion search/distance/intensity and native immunity rules, with affected future bodies. Show lingering potions’ initial three-block cloud footprint.
- Add potential large-fireball, primed TNT and swelling-creeper explosion zones using actual power and loaded-terrain exposure. Conditional creeper cues retract when swelling ends.
- Forecast copied TNT motion, collision/friction, standard fluid pushes and fuse ticks, including fractional tick credit and field-boundary rate changes. Restrict detonation cues to the next three real seconds; stop incomplete forecasts at unloaded or unsupported callback terrain.
- Cap outcome snapshots at four and rendered areas at two; prioritize player danger and held aim. Keep twelve paths, eight projectile silhouettes and twelve future-body silhouettes, without new prediction text.
- Carry immutable effect profiles, impact normals and outcome data through the owner-only snapshot; Forge protocol is now 16. Never dispatch potion-application events while predicting.

# 1.7.3 — Arrow interaction and pre-release projections (Minecraft 1.20.1)

- Handle arrow attack clicks with the custom redirection packet in Prediction, including hostile arrow ownership and refreshed forecast direction. Suppress invalid vanilla arrow attacks when a temporal mode ends.
- Synchronize reflected projectile ownership to tracking clients so owner exemptions and flight speed agree after reflection.
- Remove the conflicting server packet gate that rejected slow projectiles before the authoritative mode/reach/visibility checks.
- Add translucent arrow/trident geometry and throwable item silhouettes to both held aim and released flights, capped at eight projectile ghosts.
- Preview native skeleton bow and pillager crossbow central aim before release. Use dashed arcs, current target/muzzle/speed, no live projectile creation, no consumed ammunition, and no random-source mutation.
- Keep the twelve-path budget; allow up to three bounded prepared shots in server snapshots. Bump Forge protocol to 15.

# 1.7.2 — Prediction stability and interactions (Minecraft 1.20.1)

- Keep flight geometry and landing points anchored in world coordinates; remove index-based path blending and whole-path entity offsets.
- Use render-time player position, rotation, and inherited velocity for moving weapon previews.
- Send bounded server-computed paths, preserving owned flight outside local bubbles and past client tracking distance, up to 256 blocks from the viewer.
- Solve projectile intercepts against the same time-sampled navigation paths used for future silhouettes, including relative between-tick motion and vanilla collision margins. Highlight predicted mob contacts in warm gold.
- Keep navigation, move, look, and jump controls running every tick between slowed AI decisions, eliminating steering freeze frames.
- Limit mob forecasts to three seconds of continued motion or six seconds of navigation; remove invented speed for stationary mobs and omit unsupported distant future intercepts.
- Reveal selected Enderman teleport destinations with a lavender ring and ghost during a prediction-only 0.6-second windup. Preserve native validation, Forge events, sound, and immediate ordinary teleports.
- Bump Forge protocol to 14; both sides must update together.

# 1.7.1 — Time Prediction rework (Minecraft 1.20.1)

- Replace barely visible motion estimates with bright animated white navigation trails and translucent future mob models.
- Copy mobs' current navigation waypoints, including turns and destinations, without modifying their AI or ticking future worlds.
- Trace full loaded projectile flights to impact, including arrows, tridents, snowballs, eggs, pearls, potions, experience bottles, fireballs, and optional TaCZ bullets. Show landing rings and held-bow previews.
- Keep server/client clocks at 20 TPS. Integrate slowed living/projectile motion every tick; budget mob decisions separately. Global world time/weather remain slowed independently.
- Exclude embedded projectiles and preserve normal owner movement. Retain owner-only visibility, depth testing, and twelve-path cap.
- Bump Forge protocol to 13 for bounded navigation route synchronization; update both sides of multiplayer connections.

# 1.7.0 — Time Prediction (Minecraft 1.20.1)

- Add Netherite/Creative Time Prediction mode with independent 25% slowdown and two-second forecasts.
- Add owner-only cyan projectile paths, dashed movement estimates, aimed previews, and amber threat markers with a twelve-path limit.
- Support optional native TaCZ bullet physics and nominal gun-pack aimed previews.
- Keep the eight-card watch menu compact and suppress the bubble shell during prediction.
- Add prediction commands, speed configuration, bounded motion synchronization, and isolated physics/visual tests.

# Minecraft 1.20.1 — 1.6.6

- Reworked Deceleration Field to slow only projectiles within the selected watch/pedestal bubble, or everywhere in global mode. Owner-fired projectiles are included.
- Use the configured `speeds.decelerationDrag` as the projectile time rate (default 0.10). Players, mobs, TNT, blocks, and the server clock stay at normal speed, including players with lower-tier watches.
- Apply native movement, gravity, drag, acceleration and collision over the slowed step. Preserve momentum on exit, and sweep fast arrows and TaCZ bullets through field boundaries.
- Added a native tick budget for other projectile physics models and dedicated gameplay checks with actual TaCZ 1.1.8 bullets, vanilla projectile families, impacts, boundary crossing and unaffected entities.

# Changelog

All notable changes to the **Ultimate Time Stop** mod are documented in this file.

## [1.6.3] - 2026-10-03

### Added
- Minecraft 1.20.1: Creative Watch local radius editor (Shift + Right-Click → Bubble radius), with a slider and exact input from 1 to 512 blocks. Existing watches retain the server-configured default until edited. The setting persists on each watch and applies to the next local field activation, including rewind.

### Fixed
- Minecraft 1.20.1: `/timestop redirect look` and Vector Control now converge on the block or entity under the crosshair, accounting for each projectile's starting position. Applies to dynamic deflection, punched suspended projectiles, and kinetic barrier volleys.
- Crosshair-directed arrows fly without gravity until block impact, preventing downward drift away from the aim point. Return-to-shooter behavior is preserved.

## [1.6.2] - 2026-09-30

### Added
- **Creative Watch Rune Matrix**: Replaced the single physical rune socket on the Creative Watch (`timestop:creative_watch`) with an integrated 15-rune matrix grid. Players can toggle any combination of runes directly from the Shift + Right-Click GUI without needing physical items in inventory.
- **Master Matrix Controls**: Added `[Enable All]` and `[Disable All]` controls in the Creative Watch management GUI.
- **Prioritized Projectile Defense Engine**: Harmonized defensive runes to eliminate race conditions:
  - Kinetic Palm Guard takes top priority when actively guarding with Kinetic Barrier.
  - Snatching intercepts projectiles directly to inventory whenever sneaking/crouching (Shift).
  - Deflection auto-parries incoming projectiles and fireballs back at attackers with amplified speed and owner reassignment.
  - Orbital Redirection captures projectiles into a spinning stasis halo only when not guarding, not sneaking with Snatching, and when Deflection is not parrying.
  - Phasing triggers only as an emergency evasion when other defenses are bypassed or disabled, preventing unexpected teleports.
- **Persistent Non-Consumable Rewind on Creative**: Triggering lethal damage with Rewind active on a Creative Watch rewinds the timeline without consuming or shattering the slotted matrix rune.
- **Creative Watch Rewind Self-Exemption ("Tenet / Doctor Strange" Mode)**:
  - Added an initiator rewind exemption toggle directly on the Creative Watch (`timestop:creative_watch`).
  - When enabled (`[Self: OFF]` or Exempt), timeline rewinds restore all world blocks, explosions, and non-player entities backwards through time while the initiating operator remains completely exempt — preserving their exact coordinates, camera orientation, inventory, equipment, and health.
  - Interactive GUI Chip: On the Creative Watch Mode Selection GUI (Shift + Right-Click), clicking the `[Self: ON]` / `[Self: OFF]` chip toggles exemption in real time with distinct netherite audio cues.
  - Server Operator Policy: Protected server economy with `/timestop rewind allowexempt <true|false>`. When disabled by an administrator, initiator exemption is locked out server-wide to strictly prevent exploits.
  - Operator Commands: Added `/timestop rewind exempt <true|false>` for direct watch configuration, and integrated policy reporting into `/timestop rewind status`.
- **Rewind Speed Calibration**: Added `/timestop rewind speed <0.1-20.0>` command allowing players and server admins to adjust rewind playback speed dynamically from slow-motion inspection to 20x fast-rewind.
- **Redstone Timeline Restoration**: Structured reverse delta application bottom-up (supporting blocks before attached blocks) and paused scheduled ticks during rewind, ensuring redstone wire lines, repeaters, torches, and pistons rewind cleanly without breaking or dropping duplicate items.

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
- **Speed Calibration System**: In-game calibration tab with real-time sliders, direct numeric input boxes, and multiplier badges for customizing temporal speeds:
  - Fast Forward (1.1x - 50.0x, default 5.0x)
  - Slow Motion (0.01x - 0.99x, default 0.25x)
  - Matrix Dilation (0.01x - 0.99x, default 0.25x)
  - Superhot Idle Rate (0.005x - 0.80x, default 0.05x)
  - Deceleration Drag (0.001x - 0.95x, default 0.10x)
- **Fast Forward Block Entity Acceleration**: Furnaces, blast furnaces, smokers, and brewing stands process at 5x speed during Fast Forward stasis.
- **Open Settings Keybind**: Bound to `H` by default (`key.timestop.open_settings`) for quick access to temporal settings.
- **Speed Configuration Command**: Added `/timestop speed` command subtree for viewing, setting, and resetting multiplier values with client synchronization.

### Changed
- **Settings Screen Layout**: Reverted Temporal Engine Settings to a clean 2-tab layout (`Visuals & FX` and `Speed Calibration`), with Projectiles Flow in Stasis cleanly integrated into Visuals.
- **Superhot Mode Parity & Polish**:
  - Free cursor aiming: looking around no longer advances time.
  - Combat action waking: swinging weapons, attacking, or using items advances time to real time.
  - Weapon cooldowns and eating run at normal 20 TPS during Superhot stasis.
  - Configurable crystal-red mob tint filter (`HOSTILE`, `PASSIVE`, `ALL`).
  - Preserved armor, weapons, shields, custom heads, wings, and glowing eyes on crystal-red mobs.
  - Calibrated shader crystal-red threshold for accurate entity detection in dark shadows and caves.
  - Reverted Superhot HUD indicator to a clean 2-state display (`TIME FROZEN` vs `TIME IN MOTION`).

### Fixed
- **Third-Person Perspective Shader**: Fixed an issue where cycling to third person perspective cleared the stasis and Dead Eye shaders. Custom post-processing effects are now maintained continuously across all camera perspectives.
- **Temporal Bubble Clock Dilation**: Synchronized server and client delta trackers when inside localized temporal bubbles, resolving mob stuttering and player desync inside Slow Motion, Matrix, and Superhot fields.

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

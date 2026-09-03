# Changelog

All notable changes to the **Ultimate Time Stop** mod are documented in this file.

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

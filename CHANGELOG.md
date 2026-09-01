# Changelog

All notable changes to the **Ultimate Time Stop** mod are documented in this file.

## [1.2.0] - 2026-09-01

### Added
- **Localized Temporal Bubbles**:
  - Survival watches deploy localized temporal spheres centered on the user (Copper: 10m, Gilded: 16m, Diamond: 24m, Netherite: 32m, Creative: 100m or Full Server).
  - True multi-bubble concurrent simulation: Multiple players can project localized time fields simultaneously across the server without conflicts or world tick degradation.
  - Bubble boundary visual rendering with dimensional distortion rings and particle perimeters.
- **Multiplayer Chrono-Allies & Party System**:
  - Direct alliance commands integrated under `/timestop friend`, `/timestop party`, `/friend`, and `/party` (accessible to all players without requiring OP permissions).
  - Automatic stasis exemption for scoreboard teams and Chrono-Allies, allowing teammates to move, fight, and shoot alongside you within your active domain.
  - Interactive clickable chat invites with `[Accept]` and `[Decline]` actions.
- **Citadel & Animation Framework Compatibility**:
  - Fully integrated compatibility with Citadel, Alex's Mobs, and Alex's Caves custom animated entities and multipart models during stasis.
- **Omnipotent Administrative Protection**:
  - Creative Clocks default to Global Server Mode with supreme override authority.
  - Survival players are prevented from overriding or canceling active admin temporal fields.
- **Temporal Configuration Screen & Settings**:
  - Added in-game client settings screen accessible from the Watch GUI.

### Changed
- **Superhot Motion Dynamic Dilation**:
  - Polished authentic Superhot slow-motion physics: seamless real-time velocity scaling when running, jumping, and attacking, transitioning into cinematic slow-motion during mid-air falls.
  - Optimized crystalline red mob rendering with specular crystal overlays and pure white environment desaturation.
- **Kinetic Vector & Stasis Discharge**:
  - Unified spatial bounding box stasis calculations across all entities.
  - Stasis discharge now broadcasts client motion packets immediately, delivering high-velocity launches for all accumulated hits.
- **Water-Walking in Stasis**:
  - Solid surface collision logic for frozen water and lava updated to work seamlessly across localized bubbles and global server stasis.

### Fixed
- Fixed kinetic knockback vectors being wiped when moving a temporal bubble away from hit mobs.
- Fixed rain splash and stasis particles shaking/jittering due to interpolation lerp oscillation.
- Fixed Creative Clock defaulting to local bubble instead of global scope.
- Fixed global grayscale shader stripping when localized bubbles were created elsewhere on the server.
- Fixed Superhot mob red crystal overlay tinting terrain and organic foliage blocks.

---

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

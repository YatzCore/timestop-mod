# Ultimate Time Stop

**New in 1.6.0:** explore [Ruined Observatories](OBSERVATORY.md) in highlands and forests, discover their hidden archives, and recover watches, runes, and abandoned armillary pedestals.

**New in 1.5.0:** five [Clockwork Pedestals](PEDESTALS.md) with distinct Minecraft models, floating watch displays, adjustable stationary fields, and continuous redstone activation. Editable models and previews are in `art/pedestals`.

<p align="center">
  <img src="logo.png" alt="Ultimate Time Stop" width="240" />
</p>

<p align="center">
  <b>Minecraft Version Branches:</b><br />
  <a href="https://github.com/YatzCore/timestop-mod/tree/1.20.1-forge-fabric">Minecraft 1.20.1 (Forge / Fabric / NeoForge)</a> |
  <a href="https://github.com/YatzCore/timestop-mod/tree/1.21.1-forge-fabric">Minecraft 1.21.1 (Forge / Fabric / NeoForge)</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.1-brightgreen?style=flat-square" alt="Minecraft Versions" />
  <img src="https://img.shields.io/badge/Loaders-Forge%20%7C%20Fabric%20%7C%20NeoForge-orange?style=flat-square" alt="Loaders" />
  <img src="https://img.shields.io/badge/Java-17%20%2F%2021-red?style=flat-square" alt="Java Versions" />
  <img src="https://img.shields.io/badge/TACZ-1.20.1%20Only-purple?style=flat-square" alt="TACZ Support" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="License" /></a>
</p>

---

A temporal manipulation mod supporting both **Minecraft 1.20.1** and **Minecraft 1.21.1** across **Forge**, **Fabric**, and **NeoForge**. Freeze entities, projectile trajectories, fluids, block updates, and daylight across localized spherical bubbles or server-wide fields. Features six temporal modes, tiered pocket watches, socketable combat runes, in-game speed calibration, and version-specific firearm integration.

---

## Supported Versions & Compatibility Matrix

| Feature / Platform | Minecraft 1.20.1 | Minecraft 1.21.1 |
| :--- | :---: | :---: |
| **Java Runtime** | Java 17 | Java 21 |
| **Supported Loaders** | Forge (47.3.0+), Fabric (0.15.11+), NeoForge | Forge (52.1.16+), Fabric (0.16.10+), NeoForge (21.1.75+) |
| **Core Temporal Engine** | Full (6 modes, bubbles, watches, runes) | Full (6 modes, bubbles, watches, runes) |
| **Speed Calibration GUI (H)** | Yes | Yes |
| **Multiplayer Time Sync (/sync)** | Yes | Yes |
| **TACZ Firearm Compatibility** | **Yes** *(Native soft-dependency support)* | **No** *(Vanilla ballistics only)* |
| **Chrono Coins & +RICOSHOT** | **Yes** *(With TACZ)* | N/A |

> [!IMPORTANT]
> **TACZ (Timeless and Classics Zero) Support**:
> Upstream TACZ is currently available exclusively for Minecraft 1.20.1.
> - **1.20.1**: Includes deep ballistic integration for firearms, kinetic projectiles, explosive ordnance, and Chrono Coins.
> - **1.21.1**: Pure vanilla projectile mechanics (arrows, tridents, fireballs, potions). All gun-specific mechanics and keybinds are excluded.

---

## Temporal Modes

Modes can be activated using Pocket Watches or administrative commands (`/timestop start <mode>`):

- **Time Stop**: Completely freezes non-exempt entities, projectile trajectories, fluid physics, weather, and day/night cycles.
  - **Damage Buffer**: Damage dealt to living entities accumulates during stasis and detonates simultaneously with accumulated knockback upon resumption.
  - **Fluid Walking**: Water and lava surfaces act as solid collision blocks while stasis is active.
  - **Projectile Slapping**: Left-click suspended arrows, tridents, fireballs, or bullets in mid-air to punch and redirect their trajectory.
  - **Projectile Snatching**: Right-click suspended projectiles to catch them directly into your inventory without damage.
  - **Kinetic Stasis**: Attack suspended falling blocks (anvils, sand, gravel) or primed TNT to impart velocity, launching them as high-velocity missiles when time resumes.
- **Slow Motion**: Modulates world simulation down to 25% speed (5 TPS default; calibratable from 0.01x to 0.99x) for fluid bullet-time evasion and precision combat.
- **Matrix**: World simulation drops to 25% (5 TPS), while the player receives transient speed buffs (+300% movement speed, +300% attack speed) to move and attack in real time.
- **SUPERHOT**: Movement-driven time dilation. The world runs at 5% speed (1 TPS) when standing still; moving, sprinting, jumping, attacking, or using items advances time in real time.
  - **Free Camera Aiming**: Looking around with the crosshair does **not** progress time, allowing risk-free target acquisition.
  - **Entity Shaders**: Features a crystal-red entity outline shader with configurable filtering (`HOSTILE`, `PASSIVE`, or `ALL`).
- **Deceleration Field**: Projects a localized temporal barrier (3.5m–6.0m radius) that applies an 80% velocity drag to incoming hostile projectiles.
- **Fast Forward**: Accelerates server-side simulation to 500% speed (100 TPS default; calibratable up to 50x). Speeds up mob movement, daylight cycles, crop growth, and block entities (furnaces, blast furnaces, smokers, brewing stands).

---

## Tiered Pocket Watches

Craft and upgrade pocket watches through survival tiers. Higher tiers increase active duration, decrease recharge cooldowns, expand the localized field radius, and provide offhand/inventory bullet-dodge passives.

| Icon | Watch Tier | Registry Identifier | Duration | Cooldown | Field Radius | Passive Effect | Sockets | Unlocked Modes |
| :---: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| <img src="common/src/main/resources/assets/timestop/textures/item/copper_watch.png" width="24" height="24" /> | **Copper Watch** | `timestop:copper_watch` | 6s (120t) | 25s (500t) | 12m | None | None | Slow Motion, Fast Forward |
| <img src="common/src/main/resources/assets/timestop/textures/item/chronos_watch.png" width="24" height="24" /> | **Golden Watch** | `timestop:chronos_watch` | 10s (200t) | 18s (360t) | 24m | 3.5m Bullet Dodge | 1 | + Deceleration Field, SUPERHOT |
| <img src="common/src/main/resources/assets/timestop/textures/item/diamond_watch.png" width="24" height="24" /> | **Diamond Watch** | `timestop:diamond_watch` | 14s (280t) | 12s (240t) | 42m | 4.5m Bullet Dodge | 1 | + Matrix, Time Stop |
| <img src="common/src/main/resources/assets/timestop/textures/item/netherite_watch.png" width="24" height="24" /> | **Netherite Watch** | `timestop:netherite_watch` | 20s (400t) | 6s (120t) | 72m | 5.5m Bullet Dodge | 1 | All Modes *(Fire-Resistant)* |
| <img src="common/src/main/resources/assets/timestop/textures/item/creative_watch.png" width="24" height="24" /> | **Creative Watch** | `timestop:creative_watch` | Infinite | None | 128m / Global | 6.0m Bullet Dodge | 1 | All Modes *(Configurable Scope)* |

> [!NOTE]
> Pocket watches in survival generate localized spherical bubbles by default. Dedicated server tick rates remain at 20 TPS outside active spheres, allowing multiple players to independently control time without degrading server performance.

---

## Temporal Runes

Socketable into Tier 2–4 pocket watches via the **Watch Management GUI** (**Shift + Right-Click** while holding a watch) to grant active and passive combat abilities:

| Icon | Rune Name | Item Identifier | Ability & Mechanics | Availability |
| :---: | :--- | :--- | :--- | :---: |
| <img src="common/src/main/resources/assets/timestop/textures/item/blank_rune.png" width="24" height="24" /> | **Blank Temporal Rune** | `timestop:blank_rune` | Base crafting slate infused with chronal resonance. Used to carve all specialized runes. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_deflection.png" width="24" height="24" /> | **Rune of Redirection** | `timestop:rune_deflection` | Automatically parries incoming projectiles back toward attackers at supersonic speed. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_snatching.png" width="24" height="24" /> | **Rune of Snatching** | `timestop:rune_snatching` | Automatically intercepts incoming projectiles directly into your inventory without damage. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_phasing.png" width="24" height="24" /> | **Rune of Phasing** | `timestop:rune_phasing` | Automatically teleports you away from incoming projectiles right before collision impact. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_kinetic.png" width="24" height="24" /> | **Rune of Kinetic Amplification** | `timestop:rune_kinetic` | Multiplies accumulated melee strike force during stasis with 2.5x launch velocity upon resumption. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_vampirism.png" width="24" height="24" /> | **Rune of Chrono-Vampirism** | `timestop:rune_vampirism` | Siphons temporal energy from struck enemies, extending active stasis up to double base duration. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_volatile.png" width="24" height="24" /> | **Rune of Volatile Stasis** | `timestop:rune_volatile` | Imbues struck frozen projectiles and falling blocks with delayed kinetic bomb blasts. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_tachyon.png" width="24" height="24" /> | **Rune of the Tachyon** | `timestop:rune_tachyon` | Grants 3x mining speed and accelerated weapon attack recharge in Slow-Mo and Matrix modes. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_deadeye.png" width="24" height="24" /> | **Rune of the Dead Eye** | `timestop:rune_deadeye` | Dilates time when aiming ranged weapons. Sweeping crosshair paints up to 6 targets for a supersonic volley. | 1.20.1 & 1.21.1 *(+ TACZ)* |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_ricochet.png" width="24" height="24" /> | **Rune of Voltaic Ricochet** | `timestop:rune_ricochet` | Projectiles fired in stasis chain between nearby hostiles like lightning arcs. | 1.20.1 & 1.21.1 *(+ TACZ)* |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_orbital.png" width="24" height="24" /> | **Rune of Orbital Redirection** | `timestop:rune_orbital` | Intercepts incoming projectiles into a spinning shield halo around you; press **R** to launch the volley. | 1.20.1 & 1.21.1 *(+ TACZ)* |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_transposition.png" width="24" height="24" /> | **Rune of Spatial Transposition** | `timestop:rune_transposition` | Instantly swaps positions with any targeted entity or projectile within sight (**G**). Inverts projectile vectors 180°. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_barrier.png" width="24" height="24" /> | **Rune of the Kinetic Barrier** | `timestop:rune_barrier` | Hold **Middle Click** (Neo's Palm) to freeze incoming projectiles/bullets in mid-air. Attack to return fire. | 1.20.1 & 1.21.1 *(+ TACZ)* |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_vector.png" width="24" height="24" /> | **Rune of Vector Control** | `timestop:rune_vector` | Directs struck projectiles and kinetic barrier volleys straight along your crosshair aim vector. | 1.20.1 & 1.21.1 *(+ TACZ)* |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_coin.png" width="24" height="24" /> | **Rune of the Marksman (+RICOSHOT)** | `timestop:rune_coin` | Tap **C** to toss Chrono Coins into the air and shoot them with firearms to deflect lethal critical headshots. | **1.20.1 Only** *(TACZ)* |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_rewind.png" width="24" height="24" /> | **Rune of Rewind (Auto Death Rewind)** | `timestop:rune_rewind` | Automatically intercepts lethal damage, rewinding the timeline right before death to restore health, position, and reverse the fatal event. | 1.20.1 |

---

## Timeless and Classics Zero (TACZ) Integration *(1.20.1 Only)*

Ultimate Time Stop features native soft-dependency integration for **Timeless and Classics Zero (1.1.8-hotfix+)** on **Minecraft 1.20.1**:

- **Native Ballistics in Stasis**: Kinetic bullets, shotgun pellets, 40mm grenades, and RPG-7 rockets behave as physical entities in stasis. They freeze in mid-air, maintain ballistic velocity upon resumption, and interact with deflection, slapping, and orbital shields.
- **Rune of the Kinetic Barrier (Neo's Palm)**: Hold **Middle Click** to freeze incoming gunfire directly in front of you. Release to let spent rounds drop, or attack to redirect the entire volley forward.
- **Chrono Coins & +RICOSHOT**: Equip the Marksman rune, tap **C** to toss a Chrono Coin (`timestop:chrono_coin`), and shoot it out of the air with any firearm. Bullets ricochet toward the nearest enemy's head with amplified critical multipliers.
- **Dead Eye Firearm Guidance**: Target marking with TACZ weapons preserves weapon audio, fire animations, and ammunition consumption while eliminating recoil spread for precision hits.
- **Clean Soft-Dependency Architecture**: When running in vanilla environments or on Minecraft 1.21.1, the Marksman rune, Chrono Coins, crafting recipes, charge HUD, and coin flip keybind automatically hide from all creative tabs and menus.

---

## Controls & Keybinds

| Input | Action | Requirements | Availability |
| :--- | :--- | :--- | :---: |
| **Right-Click** | Activate / Stop selected temporal mode | Pocket Watch in hand | 1.20.1 & 1.21.1 |
| **Shift + Right-Click** | Open Watch Management GUI & Rune Socket Tray | Pocket Watch in hand | 1.20.1 & 1.21.1 |
| **V** | Toggle active time mode on / off | Pocket Watch equipped | 1.20.1 & 1.21.1 |
| **H** | Open Temporal Engine Configuration & Speed Calibration GUI | Available in-game | 1.20.1 & 1.21.1 |
| **R** | Release orbiting projectile shield | Equipped *Rune of Orbital Redirection* | 1.20.1 & 1.21.1 |
| **G** | Trigger Spatial Transposition position swap | Equipped *Rune of Spatial Transposition* | 1.20.1 & 1.21.1 |
| **Middle Click** *(Hold)* | Deploy Kinetic Barrier (Neo's Palm) | Equipped *Rune of the Kinetic Barrier* | 1.20.1 & 1.21.1 |
| **C** | Flip Chrono Coin into mid-air | Equipped *Rune of the Marksman* & TACZ installed | **1.20.1 Only** |
| **K** | Toggle projectile flow state (suspended vs flowing stasis) | Any active stasis field | 1.20.1 & 1.21.1 |

---

## Commands & Multiplayer

### Multiplayer Time Sync (`/sync`, `/timesync`)
Available to all survival players without operator permissions (Permission Level 0). Links players into a shared resonance network so teammates can freely move, attack, and shoot inside each other's localized bubbles:
- `/sync add <player>`: Send a sync invitation (expires after 60s).
- `/sync accept <player>`: Accept an incoming sync invitation.
- `/sync decline <player>`: Decline an incoming invitation.
- `/sync remove <name>`: Disconnect an active resonator.
- `/sync list`: View active resonators and pending requests.
- `/sync clear`: Remove all active resonators for the player.

### Administrative Commands (`/timestop`)
Requires Operator permission (Level 2):
- `/timestop start <mode> [seconds]`: Force server-wide time distortion (`timestop`, `slowmotion`, `matrix`, `superhot`, `fastforward`, `deceleration`).
- `/timestop stop`: Immediately collapse all active localized bubbles and server-wide freezes.
- `/timestop toggle [seconds]`: Toggle between running time and stopped time.
- `/timestop scope <watch|sphere|global>`: Configure watch scoping policy (`watch` = respect item tier; `sphere` = enforce local bubbles; `global` = force server-wide freeze).
- `/timestop redirect <look|return>`: Configure projectile deflection policy (`look` = crosshair aim; `return` = reflect to shooter).
- `/timestop exempt <add|remove> <player>`: Manage player whitelist for global time stop immunity.
- `/timestop speed <get|set|reset>`: Inspect or configure speed dilation multipliers for Slow Motion, Matrix, Superhot, and Fast Forward.
- `/timestop rewind [seconds]`: Rewind the world timeline backwards (respects configured continuous or burst mode).
- `/timestop rewind include <true|false>`: Enable or disable Rewind mode inclusion across all watches and commands (aliases: `/timestop allowrewind [true|false]`, `/timestop rewind on|off|enable|disable`). When disabled, Rewind mode completely disappears from watches and selection menus.
- `/timestop rewind mode <burst|continuous>`: Set active rewind playback mode.
- `/timestop rewind ondeath [true|false]`: Toggle or inspect server-wide auto death rewind without requiring a rune.
- `/timestop buffer`: Inspect the timeline recording buffer status, memory usage, and watch inclusion state.
- `/timestop buffer reset`: Clear timeline history, reset capacity to 30s defaults, and cancel active rewinds.
- `/timestop buffer clear`: Clear recorded frames while preserving configured capacity.
- `/timestop status`: Display diagnostics (active bubbles, remaining duration, tick rates, and scoping mode).

---

## Installation

### Minecraft 1.20.1 (Java 17)
- **Fabric**: Install Fabric Loader (0.15.11+) + Fabric API. Place `timestop-fabric-1.20.1-1.4.5.jar` in `.minecraft/mods`.
- **Forge / NeoForge**: Install Minecraft Forge (47.3.0+) or NeoForge. Place `timestop-forge-1.20.1-1.4.5.jar` in `.minecraft/mods`.
- *(Optional)*: Install **Timeless and Classics Zero (TACZ)** for native firearm integration.

### Minecraft 1.21.1 (Java 21)
- **Fabric**: Install Fabric Loader (0.16.10+) + Fabric API (0.116.17+). Place `timestop-fabric-1.21.1-1.4.0.jar` in `.minecraft/mods`.
- **Forge**: Install Minecraft Forge (52.1.16+). Place `timestop-1.21.1-1.4.0.jar` in `.minecraft/mods`.
- **NeoForge**: Install NeoForge (21.1.75+). Place `timestop-neoforge-1.21.1-1.4.0.jar` in `.minecraft/mods`.
- *Note*: Upstream TACZ does not currently offer a 1.21.1 build; 1.21.1 runs with vanilla ballistics.

---

## License

This project is licensed under the [MIT License](LICENSE).

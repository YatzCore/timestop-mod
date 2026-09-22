# Ultimate Time Stop

<p align="center">
  <img src="logo.png" alt="Ultimate Time Stop" width="240" />
</p>

<p align="center">
  <b>Minecraft Version Branches:</b><br />
  <a href="https://github.com/YatzCore/timestop-mod/tree/1.20.1-forge-fabric">Minecraft 1.20.1 (Forge / Fabric / NeoForge)</a> |
  <b><a href="https://github.com/YatzCore/timestop-mod/tree/1.21.1-forge-fabric">Minecraft 1.21.1 (Forge / Fabric / NeoForge) (Current)</a></b>
</p>

<p align="center">
  <a href="https://github.com/YatzCore/timestop-mod/releases"><img src="https://img.shields.io/badge/Release-v1.4.5-blue?style=flat-square&logo=github" alt="Release" /></a>
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-brightgreen?style=flat-square" alt="Minecraft 1.21.1" />
  <img src="https://img.shields.io/badge/Fabric-0.16.10%2B-lightgrey?style=flat-square" alt="Fabric" />
  <img src="https://img.shields.io/badge/Forge-52.1.16%2B-orange?style=flat-square" alt="Forge" />
  <img src="https://img.shields.io/badge/NeoForge-21.1.75%2B-blueviolet?style=flat-square" alt="NeoForge" />
  <img src="https://img.shields.io/badge/Java-21-red?style=flat-square" alt="Java 21" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="License" /></a>
</p>

---

A temporal manipulation mod for **Minecraft 1.21.1** supporting **Forge** (52.1.16+), **Fabric** (0.16.10+), and **NeoForge** (21.1.75+), running on Java 21. Freeze entities, projectile trajectories, fluids, block updates, and daylight across localized spherical bubbles or server-wide fields. Features six temporal modes, continuous and burst temporal rewind, tiered pocket watches, tactical combat runes, in-game speed calibration, and modern 1.21.1 delta-tracking.

> [!IMPORTANT]
> **TACZ (Timeless and Classics Zero) Compatibility**:
> Upstream TACZ is currently available exclusively for Minecraft 1.20.1 and has not published an official 1.21.1 build.
> - **1.21.1**: Uses vanilla projectile physics (arrows, tridents, fireballs, splash potions). Gun-specific items (Chrono Coins, Marksman rune) are excluded.
> - For full firearm stasis and ballistic integration, please use the **[1.20.1 branch](https://github.com/YatzCore/timestop-mod/tree/1.20.1-forge-fabric)** and releases (`v1.4.5-1.20.1`).

---

## Supported Versions & Compatibility Matrix

| Feature / Platform | Minecraft 1.20.1 | Minecraft 1.21.1 |
| :--- | :---: | :---: |
| **Java Runtime** | Java 17 | Java 21 |
| **Supported Loaders** | Forge (47.3.0+), Fabric (0.15.11+), NeoForge | Forge (52.1.16+), Fabric (0.16.10+), NeoForge (21.1.75+) |
| **Core Temporal Engine** | Full (6 modes, bubbles, watches, runes) | Full (6 modes, bubbles, watches, runes) |
| **Rewind Engine & Auto Death Protection** | Yes (Burst & Continuous) | Yes (Burst & Continuous) |
| **Speed Calibration GUI (H)** | Yes | Yes |
| **Multiplayer Time Sync (/sync)** | Yes | Yes |
| **TACZ Firearm Compatibility** | **Yes** *(Native soft-dependency support)* | **No** *(Vanilla ballistics only)* |
| **Chrono Coins & +RICOSHOT** | **Yes** *(With TACZ)* | N/A |

---

## Temporal Modes

Modes can be activated using Pocket Watches or administrative commands (`/timestop start <mode>`):

- **Time Stop**: Completely freezes non-exempt entities, projectile trajectories, fluid physics, weather, and day/night cycles.
  - **Damage Buffer**: Damage dealt to living entities accumulates during stasis and detonates simultaneously with accumulated knockback upon resumption.
  - **Fluid Walking**: Water and lava surfaces act as solid collision blocks while stasis is active.
  - **Projectile Slapping**: Left-click suspended arrows, tridents, fireballs, or projectiles in mid-air to punch and redirect their trajectory.
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
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_deadeye.png" width="24" height="24" /> | **Rune of the Dead Eye** | `timestop:rune_deadeye` | Dilates time when aiming ranged weapons. Sweeping crosshair paints up to 6 targets for a supersonic volley. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_ricochet.png" width="24" height="24" /> | **Rune of Voltaic Ricochet** | `timestop:rune_ricochet` | Projectiles fired in stasis chain between nearby hostiles like lightning arcs. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_orbital.png" width="24" height="24" /> | **Rune of Orbital Redirection** | `timestop:rune_orbital` | Intercepts incoming projectiles into a spinning shield halo around you; press **R** to launch the volley. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_transposition.png" width="24" height="24" /> | **Rune of Spatial Transposition** | `timestop:rune_transposition` | Instantly swaps positions with any targeted entity or projectile within sight (**G**). Inverts projectile vectors 180°. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_barrier.png" width="24" height="24" /> | **Rune of the Kinetic Barrier** | `timestop:rune_barrier` | Hold **Middle Click** (Neo's Palm) to freeze incoming projectiles in mid-air. Attack to return fire. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_vector.png" width="24" height="24" /> | **Rune of Vector Control** | `timestop:rune_vector` | Directs struck projectiles and kinetic barrier volleys straight along your crosshair aim vector. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_rewind.png" width="24" height="24" /> | **Rune of Rewind** | `timestop:rune_rewind` | Automatically intercepts fatal damage to rewind time, safely restoring health, position, and inventory. | 1.20.1 & 1.21.1 |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_coin.png" width="24" height="24" /> | **Rune of the Marksman (+RICOSHOT)** | `timestop:rune_coin` | Tap **C** to toss Chrono Coins into the air and shoot them to deflect critical headshots. | **1.20.1 Only** *(TACZ)* |

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
- `/timestop rewind <seconds> [player]`: Trigger an immediate temporal rollback (respects configured Burst or Continuous mode).
- `/timestop rewind ondeath [true|false]`: Toggle or inspect server-wide automatic death rewind protection.
- `/timestop rewind mode <burst|continuous>`: Switch between immediate Burst and smooth Continuous rewind playback.
- `/timestop buffer <set|reset|clear|status>`: Manage timeline recording capacity, clear history, or inspect buffer diagnostics.
- `/timestop status`: Display diagnostics (active bubbles, remaining duration, tick rates, and scoping mode).

---

## Installation

### Minecraft 1.20.1 (Java 17)
- **Fabric**: Install Fabric Loader (0.15.11+) + Fabric API. Place `timestop-fabric-1.20.1-1.4.5.jar` in `.minecraft/mods`.
- **Forge / NeoForge**: Install Minecraft Forge (47.3.0+) or NeoForge. Place `timestop-forge-1.20.1-1.4.5.jar` in `.minecraft/mods`.
- *(Optional)*: Install **Timeless and Classics Zero (TACZ)** for native firearm integration.

### Minecraft 1.21.1 (Java 21)
- **Fabric**: Install Fabric Loader (0.16.10+) + Fabric API (0.116.17+). Place `timestop-fabric-1.21.1-1.4.5.jar` in `.minecraft/mods`.
- **Forge**: Install Minecraft Forge (52.1.16+). Place `timestop-forge-1.21.1-1.4.5.jar` in `.minecraft/mods`.
- **NeoForge**: Install NeoForge (21.1.75+). Place `timestop-neoforge-1.21.1-1.4.5.jar` in `.minecraft/mods`.
- *Note*: Upstream TACZ does not currently offer a 1.21.1 build; 1.21.1 runs with vanilla ballistics.

---

## License

This project is licensed under the [MIT License](LICENSE).

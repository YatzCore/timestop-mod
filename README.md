# Ultimate Time Stop

<p align="center">
  <img src="logo.png" alt="Ultimate Time Stop" width="240" />
</p>

<p align="center">
  <b>Minecraft Version Branches:</b><br />
  <a href="https://github.com/YatzCore/timestop-mod/tree/main">Minecraft 1.20.1 (main)</a> |
  <a href="https://github.com/YatzCore/timestop-mod/tree/fabric-1.20.1">Fabric 1.20.1</a> |
  <a href="https://github.com/YatzCore/timestop-mod/tree/1.21.1">Forge 1.21.1</a> |
  <b><a href="https://github.com/YatzCore/timestop-mod/tree/fabric-1.21.1">Fabric 1.21.1 (Current)</a></b>
</p>

<p align="center">
  <a href="https://github.com/YatzCore/timestop-mod/releases"><img src="https://img.shields.io/badge/Release-v1.4.0-blue?style=flat-square&logo=github" alt="Release" /></a>
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-brightgreen?style=flat-square" alt="Minecraft 1.21.1" />
  <img src="https://img.shields.io/badge/Fabric-0.16.10%2B-lightgrey?style=flat-square" alt="Fabric" />
  <img src="https://img.shields.io/badge/Java-21-red?style=flat-square" alt="Java 21" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="License" /></a>
</p>

---

A true engine-level temporal manipulation mod for **Minecraft 1.21.1** on **Fabric** (running on Java 21). Freeze all entities, projectile trajectories, fluids, block updates, and weather across localized spherical bubbles or server-wide fields. Features six temporal modes, tiered pocket watches, tactical combat runes, in-game speed calibration controls, and modern 1.21.1 delta-tracking.

> [!IMPORTANT]
> **TACZ (Timeless and Classics Zero) Compatibility on 1.21.1**:
> Upstream TACZ is currently available exclusively for Minecraft 1.20.1 and has not yet published an official 1.21.1 build. For full TACZ firearm gameplay, please use the **[1.20.1 branch (main)](https://github.com/YatzCore/timestop-mod/tree/main)** and releases (`v1.3.12`).

---

## Temporal Modes

Each mode alters the flow of time through custom server-tick modulation, client prediction, and entity mixins:

- **Time Stop**: Freezes all mobs, projectiles, weather cycles, daylight, and falling blocks. Living entity damage accumulates in a kinetic buffer and detonates upon resumption. Enables walking across fluid surfaces (water and lava).
  - **Projectile Slapping**: Left-click suspended projectiles in mid-air to punch and redirect their trajectory.
  - **Projectile Snatching**: Right-click suspended projectiles to pluck arrows, tridents, and fireballs directly into inventory.
  - **Kinetic Stasis**: Strike falling blocks (anvils, sand, gravel) or primed TNT in stasis to launch them as high-velocity missiles when time resumes.
- **Slow Motion**: Modulates world simulation down to a configurable rate (default 0.25x / 5 TPS) for fluid bullet-time evasion.
- **Matrix**: World simulation slows down, while the player retains full movement and attack speed.
- **SUPERHOT**: Movement-driven time progression. Standing still keeps time in stasis; physical movement (walking, jumping, attacking) advances time in real time. Free camera aiming allows targeting without waking time.
- **Deceleration Field**: A localized defense field that slows incoming hostile projectiles by 80%, providing a reliable evasion window.
- **Fast Forward**: Accelerates server-side world simulation and block entities (furnaces, blast furnaces, smokers, brewing stands) to 500% speed (100 TPS) for accelerated processing.

---

## Tiered Pocket Watches

Craft and upgrade pocket watches through survival tiers. Higher tiers expand active duration, reduce cooldowns, enlarge the localized temporal bubble radius, and unlock additional modes and rune sockets.

| Icon | Watch Tier | Duration | Cooldown | Field Radius | Passive Effect | Unlocked Modes |
| :---: | :--- | :---: | :---: | :---: | :---: | :--- |
| <img src="common/src/main/resources/assets/timestop/textures/item/copper_watch.png" width="24" height="24" /> | **Copper Watch** | 6s | 25s | 10m | None | Slow Motion, Fast Forward |
| <img src="common/src/main/resources/assets/timestop/textures/item/chronos_watch.png" width="24" height="24" /> | **Golden Watch** | 10s | 18s | 16m | 3.5m Bullet Dodge | Deceleration Field, SUPERHOT |
| <img src="common/src/main/resources/assets/timestop/textures/item/diamond_watch.png" width="24" height="24" /> | **Diamond Watch** | 14s | 12s | 24m | 4.5m Bullet Dodge | Matrix, Time Stop |
| <img src="common/src/main/resources/assets/timestop/textures/item/netherite_watch.png" width="24" height="24" /> | **Netherite Watch** | 20s | 6s | 32m | 5.5m Bullet Dodge | All Modes (Fire-Resistant) |
| <img src="common/src/main/resources/assets/timestop/textures/item/creative_watch.png" width="24" height="24" /> | **Creative Watch** | Infinite | None | 100m / Global | 8.0m Bullet Dodge | All Modes (Configurable Scope) |

> [!NOTE]
> Pocket watches in survival generate localized spherical bubbles by default. Dedicated server tickrates remain at a stable 20 TPS outside active spheres, allowing multiple players to independently control time without interfering with global server performance.

---

## Temporal Runes

Socketable into Tier 2-4 pocket watches via the watch interface (**Shift + Right-Click**) to bestow active and passive combat abilities:

| Icon | Rune Name | Ability & Combat Function | Integration |
| :---: | :--- | :--- | :---: |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_deflection.png" width="24" height="24" /> | **Rune of Redirection** | Automatically parries incoming projectiles back toward attackers. | Core |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_snatching.png" width="24" height="24" /> | **Rune of Snatching** | Automatically intercepts incoming projectiles straight into inventory. | Core |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_phasing.png" width="24" height="24" /> | **Rune of Phasing** | Teleports you out of harm's way right before an unavoidable projectile impact. | Core |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_kinetic.png" width="24" height="24" /> | **Rune of Kinetic Amplification** | Multiplies melee strikes during stasis with 2.5x accumulated launch force. | Core |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_vampirism.png" width="24" height="24" /> | **Rune of Chrono-Vampirism** | Siphons temporal energy from struck enemies, extending active duration. | Core |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_volatile.png" width="24" height="24" /> | **Rune of Volatile Stasis** | Imbues struck projectiles and falling blocks with delayed concussive blasts. | Core |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_tachyon.png" width="24" height="24" /> | **Rune of the Tachyon** | Accelerates mining speed (3x) and attack recharge in Slow-Mo and Matrix. | Core |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_deadeye.png" width="24" height="24" /> | **Rune of the Dead Eye** | Paint multiple targets in slow motion, releasing a guided projectile volley. | Core / TACZ |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_ricochet.png" width="24" height="24" /> | **Rune of Voltaic Ricochet** | Projectiles fired in stasis chain between nearby hostiles like lightning. | Core / TACZ |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_orbital.png" width="24" height="24" /> | **Rune of Orbital Redirection** | Intercepts projectiles into a spinning halo; press **R** to launch. | Core / TACZ |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_transposition.png" width="24" height="24" /> | **Rune of Spatial Transposition** | Instantly swaps positions with any targeted entity or projectile (**G**). | Core |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_barrier.png" width="24" height="24" /> | **Rune of the Kinetic Barrier** | Hold **Middle Click** to freeze bullets in mid-air. Attack to fire back. | Core / TACZ |
| <img src="common/src/main/resources/assets/timestop/textures/item/blank_rune.png" width="24" height="24" /> | **Rune of Vector Control** | Directs struck projectiles and barrier volleys toward crosshair aim. | Core / TACZ |
| <img src="common/src/main/resources/assets/timestop/textures/item/rune_coin.png" width="24" height="24" /> | **Rune of the Marksman (+RICOSHOT)** | Toss coins into mid-air (**C**) and shoot them to deflect lethal critical shots. | 1.20.1 Only (TACZ) |

---

## Speed Calibration & Settings

Configure the speeds of time manipulation via the in-game settings screen or config files:

- **Slow Motion Speed**: 0.05x to 0.75x (Default: 0.25x)
- **Matrix Speed**: 0.05x to 0.75x (Default: 0.25x)
- **Superhot Idle Speed**: 0.01x to 0.20x (Default: 0.05x)
- **Superhot Mob Tint**: Hostile (monsters only), Passive (animals/neutrals only), or All entities.

Open the settings screen by pressing **H** in-game or clicking **Settings** in the Pocket Watch interface.

---

## Controls & Keybinds

| Input | Action | Requirements |
| :--- | :--- | :--- |
| **Right-Click** | Activate / Stop selected temporal mode | Pocket Watch in hand |
| **Shift + Right-Click** | Open Watch Management GUI & Rune Socket Tray | Pocket Watch in hand |
| **V** | Toggle active time mode | Any equipped Pocket Watch |
| **H** | Open Temporal Engine Configuration & Speed Calibration GUI | Available in-game |
| **R** | Release orbiting projectile shield | Rune of Orbital Redirection |
| **G** | Trigger Spatial Transposition swap | Rune of Spatial Transposition |
| **Middle Click** (Hold) | Deploy Kinetic Barrier (Neo's Palm) | Rune of the Kinetic Barrier |
| **C** | Flip Chrono Coin into mid-air | Rune of the Marksman & TACZ installed |
| **K** | Toggle projectile flow (suspended vs flowing stasis) | Any active stasis field |

---

## Commands & Multiplayer

The mod provides two full command trees for administration and cooperative multiplayer:

- **Administration (`/timestop`)**: Operator permission level 2. Control world time dilation, configure sphere scoping policies (`/timestop scope <global|sphere|watch>`), set projectile deflection rules (`/timestop redirect <look|return>`), manage exemption whitelists (`/timestop exempt`), and monitor engine diagnostics (`/timestop status`).
- **Multiplayer Time Sync (`/sync`, `/timesync`)**: Available to all players without operator status. Link players into a shared resonance network so teammates can freely move, attack, and shoot inside each other's localized time bubbles.

For detailed command breakdowns, syntax, permissions, and tutorials, see [COMMANDS.md](COMMANDS.md).

---

## Crafting Progression

1. **Copper Watch**: 4 Copper Ingots + 1 Clock.
2. **Golden Watch**: Copper Watch + 4 Gold Ingots + 2 Lapis Lazuli + 2 Nether Quartz.
3. **Diamond Watch**: Golden Watch + 4 Diamonds + 3 Obsidian + 1 Echo Shard.
4. **Netherite Watch**: Diamond Watch + 1 Netherite Ingot at a Smithing Table.
5. **Blank Temporal Rune**: 4 Stone + 4 Amethyst Shards + 1 Gold Ingot. Combine with catalysts at a crafting table to carve specialized combat runes.

---

## Installation

1. Install **Minecraft 1.21.1** with **Fabric Loader** (version 0.16.10 or higher) running on **Java 21**.
2. Install **Fabric API** (version 0.116.17+1.21.1 or higher).
3. Download `timestop-fabric-1.21.1-1.4.0.jar` from [GitHub Releases](https://github.com/YatzCore/timestop-mod/releases).
4. Place the `.jar` into your `.minecraft/mods` directory.
5. Launch Minecraft and manipulate time!

*(Note: For Forge 1.21.1, see the [Forge branch](https://github.com/YatzCore/timestop-mod/tree/1.21.1) and download `timestop-1.21.1-1.4.0.jar`).*

---

## License

This project is licensed under the [MIT License](LICENSE).

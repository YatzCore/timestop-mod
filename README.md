# Ultimate Time Stop

<p align="center">
  <img src="logo.png" alt="Ultimate Time Stop" width="220" />
</p>

A Minecraft mod for 1.20.1 that allows you to freeze, slow down, and manipulate time using pocket watches and temporal runes.

## Features

### Temporal Modes
- **Time Stop**: Freezes all mobs, projectiles, fluids, and world time. You can walk on water and lava. Damage dealt to mobs accumulates and discharges simultaneously when time resumes.
  - **Bullet Slapping**: Left-click suspended projectiles in mid-air to punch and redirect their trajectory.
  - **Bullet Snatching**: Right-click suspended projectiles to pluck them directly into your inventory.
  - **Kinetic Momentum**: Strike falling blocks (anvils, sand, gravel) or primed TNT in stasis to launch them as high-velocity missiles upon resumption.
- **Slow Motion**: Slows down the entire world to 25% speed for a smooth bullet-time effect.
- **Matrix**: The world runs in slow motion, but you move, attack, and use items at normal speed.
- **SUPERHOT**: Time moves only when you move. Stand still to freeze the world, or move/attack to let time flow.
- **Deceleration Field**: A passive temporal bubble that slows down incoming projectiles by 80%.
- **Fast Forward**: Speeds up the entire world (movement, smelting, day/night cycle) by 5x.

### Tiered Pocket Watches
- **Copper Chronometer (Tier 1)**: 6s duration, 25s cooldown. Unlocks Slow Motion and Fast Forward.
- **Gilded Chronos Watch (Tier 2)**: 10s duration, 18s cooldown. 3.5m bullet-dodge passive, 1 Rune Socket. Unlocks Deceleration Field and SUPERHOT.
- **Diamond Chronos Watch (Tier 3)**: 14s duration, 12s cooldown. 4.5m bullet-dodge passive, 1 Rune Socket. Unlocks Matrix and Time Stop.
- **Netherite Chronos Sovereign (Tier 4)**: 20s duration, rapid 6s cooldown, fire-resistant. 5.5m bullet-dodge passive, 1 Rune Socket. All modes unlocked.
- **Infinite Chronos Watch (Tier 5 - Creative)**: Unlimited duration, zero cooldown, all modes unlocked.

### Temporal Runes
Socketable into Tier 2–4 watches to grant unique powers:
- **Rune of Redirection**: Automatically parries incoming projectiles back at attackers.
- **Rune of Snatching**: Automatically intercepts incoming projectiles into your inventory.
- **Rune of Phasing**: Teleports you out of danger right before a projectile hits you.
- **Rune of Kinetic Amplification**: Supercharges melee strikes during stasis with 2.5x launch force.
- **Rune of Chrono-Vampirism**: Siphons extra time from struck enemies, extending active duration.
- **Rune of Volatile Stasis**: Infuses struck projectiles and falling blocks with delayed explosive blasts.
- **Rune of the Tachyon**: Accelerates mining speed (3x) and attack recharge in Slow-Mo and Matrix.
- **Rune of the Dead Eye**: Aim a bow or crossbow to paint up to 6 targets in slow motion, then release a supersonic arrow volley.
- **Rune of Voltaic Ricochet**: Arrows fired in stasis ricochet between nearby targets like chain lightning.
- **Rune of Orbital Redirection**: Captures incoming projectiles into a spinning shield halo around you.
- **Rune of Spatial Transposition**: Instantly swaps positions with any targeted entity or projectile.

## Controls

- **Right-Click** (holding watch): Start or stop the selected mode.
- **Shift + Right-Click** (holding watch): Open the Time Mode Selection Screen or cycle modes.
- **Sneak + Right-Click with Rune** (holding watch): Socket a rune into your watch.
- **V**: Toggle the active mode on or off.
- **R**: Release orbiting projectiles (with Orbital Rune).
- **G**: Spatial Transposition swap (with Transposition Rune).

## Commands & Permissions

The mod features a full command engine for both server administration and multiplayer party coordination:

### Quick Reference
| Command | Permission | Description |
| :--- | :--- | :--- |
| `/timestop start <mode> [seconds]` | Operator (Level 2) | Activates server-wide time distortion in the specified mode. |
| `/timestop stop` | Operator (Level 2) | Immediately dissolves all active temporal bubbles and resumes time. |
| `/timestop toggle [seconds]` | Operator (Level 2) | Toggles time stop on or off. |
| `/timestop exempt <add\|remove> <players>` | Operator (Level 2) | Whitelists players from global time stop effects. |
| `/timestop servermode <global\|bubble>` | Operator (Level 2) | Sets server policy for pocket watches (localized spheres vs server-wide). |
| `/timestop status` | Operator (Level 2) | Outputs real-time temporal engine diagnostics and active bubble stats. |
| `/sync add <player>` | All Players | Invites a player to establish Time Sync (immune inside your time spheres). |
| `/sync accept <player>` | All Players | Accepts an incoming Time Sync invitation. |
| `/sync remove <name>` | All Players | Disconnects an active Resonator (online or offline). |
| `/sync list` | All Players | Displays active Resonators and pending invitations. |

For full command documentation, subcommands, and tutorials, see [COMMANDS.md](COMMANDS.md).

## Crafting Progression

- **Copper Chronometer**: Crafted with 4 Copper Ingots and 1 Clock.
- **Gilded Chronos Watch**: Upgrade Copper Watch with Gold Ingots, Lapis Lazuli, and Quartz.
- **Diamond Chronos Watch**: Upgrade Gilded Watch with Diamonds, Obsidian, and an Echo Shard.
- **Netherite Chronos Sovereign**: Upgrade Diamond Watch with a Netherite Ingot at a Smithing Table.
- **Blank Temporal Rune**: Crafted with Stone, Amethyst Shards, and Gold. Combine with materials to create specialized runes.

## Installation

1. Install **Minecraft 1.20.1** with **Forge** or **NeoForge** (47.1.0 or higher).
2. Download `timestop-1.20.1-1.2.1.jar` from [Releases](https://github.com/YatzCore/timestop-mod/releases).
3. Place the `.jar` into your `.minecraft/mods` folder.

## License

MIT

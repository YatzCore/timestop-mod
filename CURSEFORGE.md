# Ultimate Time Stop

**Ultimate Time Stop** provides engine-level temporal manipulation for Minecraft. Freeze entities, suspend arrows and projectiles in mid-air, walk across water and lava, deflect bullets, or enter dynamic Superhot-style stasis.

Supports both **Minecraft Forge** and **Fabric** on **Minecraft 1.21.1** (Java 21).

---

## Key Features

- **Time Stop**: Complete stasis. Mobs, projectiles, and falling blocks freeze in place. Left-click suspended projectiles to redirect them; right-click to pluck them into your inventory.
- **Slow Motion**: Modulates world speed down to a fluid crawl (configurable, default 0.25x / 5 TPS).
- **Matrix**: World speed drops to slow motion while the player maintains standard movement and attack speed.
- **SUPERHOT**: Time moves only when you move. Stand still to freeze time; walk, sprint, jump, or attack to progress simulation. Aiming with your mouse maintains full stasis.
- **Deceleration Field**: A localized shield that slows hostile projectiles by 80% for evasion.
- **Fast Forward**: Accelerates simulation and block entities (furnaces, blast furnaces, smokers, brewing stands) to 500% speed (100 TPS).
- **Speed Calibration GUI**: Press **H** (or click Settings in the watch menu) to customize Slow-Mo, Matrix, and Superhot speeds to your exact preference.
- **Localized Bubbles**: Pocket watches in survival generate localized spherical fields without degrading server tick performance outside the bubble.

---

## Pocket Watches & Upgrades

Craft tiered pocket watches in survival:
1. **Copper Watch**: Slow Motion & Fast Forward (6s duration, 10m bubble).
2. **Golden Watch**: Adds Deceleration Field & SUPERHOT (10s duration, 16m bubble).
3. **Diamond Watch**: Adds Matrix & Time Stop (14s duration, 24m bubble).
4. **Netherite Watch**: Unlocks all modes with fire resistance (20s duration, 32m bubble).
5. **Creative Watch**: Infinite duration, configurable global or spherical scope.

Shift + Right-Click any watch to open the socket tray and equip up to 14 tactical combat runes (Redirection, Snatching, Phasing, Kinetic Amplification, and more).

---

## Controls

| Key | Action |
| :--- | :--- |
| **Right-Click** | Activate / Deactivate selected time mode (with watch in hand) |
| **Shift + Right-Click** | Open Watch Management GUI & Rune Tray |
| **V** | Cycle active time mode |
| **H** | Open Speed Calibration & Engine Settings |
| **K** | Toggle projectile flow (suspended vs flowing) |
| **R** | Launch orbiting projectile shield (Orbital Rune) |
| **G** | Swap positions with target entity (Transposition Rune) |

---

## Installation & Requirements

- **Minecraft**: 1.21.1
- **Java**: 21

### Forge
1. Install **Minecraft Forge 52.1.16+**.
2. Drop `timestop-1.21.1-1.4.0.jar` into your `.minecraft/mods` folder.

### Fabric
1. Install **Fabric Loader 0.16.10+**.
2. Install **Fabric API 0.116.17+1.21.1+**.
3. Drop `timestop-fabric-1.21.1-1.4.0.jar` into your `.minecraft/mods` folder.

---

## Source & Links

- **GitHub Repository**: [github.com/YatzCore/timestop-mod](https://github.com/YatzCore/timestop-mod)
- **Issue Tracker**: [GitHub Issues](https://github.com/YatzCore/timestop-mod/issues)
- **Minecraft 1.20.1**: Available on GitHub releases and the `1.20.1-forge-fabric` branch.

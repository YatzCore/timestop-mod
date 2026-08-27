# ⏳ Hardcore Time Stop (Minecraft 1.20.1)

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg)
![Loader](https://img.shields.io/badge/Loader-NeoForge%20%7C%20Forge%2047.1%2B-blue.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

A true **engine-level Mixin time manipulation mod** for Minecraft 1.20.1.

Unlike standard mods that rely on superficial tick events or cheap potion effects (which cause entity jitter, rubberbanding, and particle clutter), **Hardcore Time Stop** directly modulates Minecraft's internal execution loops (`ServerLevel`, `ClientLevel`, `MinecraftServer`, `Timer`, `TextureAtlas`, and `EntityRenderDispatcher`).

---

## 🌟 Features & Temporal Modes

The mod features **4 Temporal Modes**, switchable on the fly:

| Mode | Engine Mechanics & Effects |
| :--- | :--- |
| **`TIME STOP`** | **Total Temporal Stasis**: All non-exempt mobs, entities, falling blocks, weather, and day/night cycles are completely frozen. Fluids (water and lava) become solid walkable platforms. Projectiles freeze mid-air with velocity stored. Kinetic force accumulates on struck enemies and discharges simultaneously on resumption. Fire animations and burning timers are locked solid. |
| **`SLOW MOTION`** | **5 TPS Cinematic Bullet-Time**: Server and client game loops are modulated down to 5 TPS (200ms tick interval). Frame rendering (60–144+ FPS) smoothly interpolates all positions via `partialTick` with **zero stutter and zero lag**. Attacks register immediately in real time with red damage flashes and slow knockback. |
| **`MATRIX`** | **Slow-Mo World, Hyper-Speed Player**: The world and enemies crawl at 5 TPS (25% speed), while the player is granted native engine attribute multipliers. **Item usage (hit cooldown/weapon recharge, eating food, drinking potions, and drawing bows) runs at full 20 TPS normal speed!** You can sprint circles around slow-motion monsters and weave past gliding arrows without any cheap potion swirls! |
| **`FAST FORWARD`** | **100 TPS Hyper-Speed**: Accelerates the entire game engine to 100 TPS (10ms tick interval). Running, swimming, furnace smelting (~2 seconds per item), dying animations, eating, and the daylight cycle all physically run **5x faster** through native engine speed! |

---

## 🎮 Controls & Mode Selection

- **`Shift + Right-Click` (Held Watch)**:
  - **Cycles mode**: `TIME STOP` ➔ `SLOW MOTION` ➔ `MATRIX` ➔ `FAST FORWARD` ➔ `TIME STOP`.
  - Plays a mechanical clock click sound and displays an action bar message with the mode description.
  - **Does NOT activate the mode** (safe mode cycling!).
- **`Right-Click` (Held Watch)**:
  - **Starts or stops** the currently selected mode.
- **`V` Keybind**:
  - **Exclusively starts or stops** the active mode (never alters mode selection).

---

## 🕰️ The Watches: Survival vs. Creative

| Feature | 🟡 Chronos Pocket Watch (Survival) | 🟣 Infinite Chronos Watch (Creative / Admin) |
| :--- | :--- | :--- |
| **Item ID** | `timestop:chronos_watch` | `timestop:creative_watch` |
| **Texture** | Handcrafted circular gold pocket watch with top chain loop and white dial | Handcrafted cosmic obsidian/amethyst pocket watch with cyan temporal rift core |
| **Craftable?** | **YES** (Survival Crafting Recipe) | **NO** (Creative Tab / Admin exclusive) |
| **Duration** | **10 seconds** per activation (HUD progress bar) | **Infinite** (Runs until manually toggled off) |
| **Cooldown** | **15 seconds** (Begins **ONLY when time stop ends**) | **Zero cooldown** (Instantaneous unlimited toggling) |

### 🛠️ Crafting Recipe (Chronos Pocket Watch)

Crafted on a standard 3x3 Crafting Table:

```
[       ] [Amethyst] [       ]
[Amethyst][  Clock ] [Amethyst]  ===>  1x Chronos Pocket Watch
[       ] [Echo Shard][      ]
```

- **1x Clock** (Center)
- **3x Amethyst Shard** (Top, Left, Right)
- **1x Echo Shard** (Bottom)

---

## ⚡ Technical Highlights

- **Anti-Jitter Geometry**: Clamps `partialTicks = 1.0F` and synchronizes old coordinates (`setOldPosAndRot()`) in `EntityRenderDispatcher` and `LevelRenderer`, eliminating mob jitter and sky vibrating.
- **Rock-Solid Celestial Lock**: Injects into `ClientLevel#tickTime` and `LevelRenderer#renderSky`, preventing daylight sync packet desyncs.
- **Fire & Texture Freezing**: Injects into `TextureAtlas#cycleAnimationFrames`, freezing animated flame overlays on burning mobs, burning blocks, and lava into static crystal sculptures.
- **Kinetic Force Accumulation**: Bypasses vanilla `invulnerableTime` during `TIME_STOP`, allowing rapid multi-hit strikes. All damage, sound, and directional knockback vectors resolve in a single explosive discharge on resumption.
- **Zero Potion Effects**: No fake potion swirls, no status icons on screen—everything is executed through genuine engine tick modulation (`MinecraftServerMixin`, `TimerMixin`, and `PlayerMixin`).

---

## 📦 Building From Source

### Prerequisites:
- Java Development Kit (JDK) 17 or 21
- Git

### Build Instructions:
```bash
git clone https://github.com/YourUsername/timestop-mod.git
cd timestop-mod
./gradlew build
```
The compiled jar will be generated in `build/libs/timestop-1.20.1-1.0.0.jar`.

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).

# Ultimate Time Stop v1.4.0 (Fabric 1.21.1)

### What's New
- **Minecraft 1.21.1 Fabric Port**: Native Fabric 1.21.1 release with full feature parity across all 6 temporal modes, localized bubbles, and runes.
- **Speed Calibration GUI**: Press **H** in-game or click **Settings** in the Pocket Watch interface to customize Slow Motion, Matrix, and Superhot speeds.
- **Fast Forward Acceleration**: Furnaces, blast furnaces, smokers, and brewing stands process 5x faster during Fast Forward mode.
- **Superhot Mode Refinements**:
  - Looking around with the mouse now keeps time frozen; only physical movement (WASD, jumping, attacking) advances time.
  - Restored authentic deep stasis crawl (down to 1 TPS / 1000ms per tick).
  - Streamlined 2-state HUD indicator (TIME FROZEN vs TIME IN MOTION).
  - Configurable Superhot Mob Tint (Hostile, Passive, or All) in the Settings menu.
- **Bug Fixes**: Synchronized client-server delta tracking to eliminate entity lag and rubberbanding inside temporal bubbles.

### Requirements
- Minecraft 1.21.1
- Fabric Loader 0.16.10 or higher
- Fabric API 0.116.17+1.21.1 or higher
- Java 21

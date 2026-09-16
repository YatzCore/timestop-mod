# Ultimate Time Stop v1.4.0 (Fabric 1.20.1)

### What's New
- **Speed Calibration GUI**: Press **H** in-game or click **Settings** in the Pocket Watch interface to customize Slow Motion, Matrix, and Superhot speeds.
- **Fast Forward Celestial Sky Calibration**: The sun and moon now move at normal 1x speed (20 TPS) when inside localized Fast Forward bubbles, while smoothly accelerating at your configured speed multiplier during global server Fast Forward.
- **Fast Forward Block Acceleration**: Furnaces, blast furnaces, smokers, and brewing stands process at 5x speed during Fast Forward mode.
- **Third-Person Perspective Shader Fix**: Custom stasis desaturation and Dead Eye shaders are now maintained when cycling camera views (F5) without getting cleared.
- **Superhot Mode Refinements**:
  - Looking around with the mouse now keeps time frozen; only physical movement or combat actions advance time.
  - Near-zero stasis crawl (down to 1 TPS / 1000ms per tick).
  - Streamlined 2-state HUD indicator (TIME FROZEN vs TIME IN MOTION).
  - Configurable Superhot Mob Tint (Hostile, Passive, or All) in the Settings menu.
- **Bug Fixes**: Synchronized client-server delta tracking to eliminate entity jitter and desync inside temporal bubbles.

### Requirements
- Minecraft 1.20.1
- Fabric Loader 0.15.11 or higher
- Fabric API 0.92.2+1.20.1 or higher
- Java 17

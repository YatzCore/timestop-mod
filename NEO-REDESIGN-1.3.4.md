# Neo palm redesign — 1.3.4

The kinetic barrier now stops projectiles through invisible resistance. It has no hexagonal wall, glowing border, blue pulse, or camera-mounted surface.

- Protection is symmetric around the player's eye/look direction. The main-hand pose moves inward beneath the crosshair and mirrors for left-handed players. The offhand is no longer transformed along with it.
- Incoming projectiles retain their trajectory and brake exponentially for approximately 0.8 seconds, traveling at most 1.65 blocks after capture. Close projectiles have a shorter stopping distance.
- Bullets remain at their individual world positions after settling. Turning or moving does not arrange them into a ring or drag them along with the camera.
- Swept interception runs before projectile physics so fast projectiles cannot skip across the stopping zone. Solid blocks obstruct interception and braking.
- Brief, faint neutral pressure wakes fade after 0.6 seconds. These are translucent geometry, not a screen-space refraction shader. Stationary bullets have no persistent rings or glow.
- Capture is silent. Release drops the projectiles with a quiet metallic impact; attack retains the optional return volley with a subdued launch sound. Removed explosion, lightning, sonic-boom and electric-spark effects.
- Captured projectiles are excluded from the separate colored bullet-tracer renderer.
- Capture positions synchronize explicitly; clients interpolate toward the server positions. Protocol 3 requires clients and server to update together.

Controls remain: hold the configured barrier key (middle mouse by default), release to drop, attack while holding to return the projectiles.

Validation: all 16 Forge regression tests passed, including new tests for symmetric/rotated coverage, fast projectile interception, trajectory-preserving braking, bounded travel, stationary suspension, and capture packet position serialization. The Java client renderer compiles, but a graphical Minecraft playtest is still needed to judge the hand placement and visual feel with the user's FOV, shaders and modpack. This is a Matrix-inspired implementation, not a verified frame-for-frame reproduction of the film.

Source backup before this redesign: `build/before-neo-redesign.zip`. The previous installed 1.3.3 JAR is preserved alongside a copy of that snapshot under `../mod-backups/2026-09-10-before-1.3.4/`.

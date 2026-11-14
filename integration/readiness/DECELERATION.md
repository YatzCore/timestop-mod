# Deceleration gameplay regression (1.21.1)

Use Java 21 from the repository root.

```powershell
.\gradlew.bat :fabric:runGametest --console=plain --max-workers=4
.\gradlew.bat -I integration/readiness/deceleration-native.init.gradle '-PdecelerationTaczJar=D:\path\to\tacz.jar' :neoforge:runDecelerationTests --console=plain --max-workers=4
```

The opt-in native fixture loads real TaCZ 1.1.8 in its isolated build world. On Forge,
the production dependency is remapped to development mappings; on NeoForge the stock
jar is copied to the QA run's mods directory. No QA sources or TaCZ dependencies enter
release jars. Omitting the jar property uses the existing local test installation.

Seven Fabric GameTests cover watch-selected radius, owner shots, stationary and global
scope, fast field entry/exit, wall impacts, native gravity/drag/acceleration, fallback
mod projectile ticking, and unaffected survival players, mobs, TNT and server clock.
Three native TaCZ GameTests check stationary/watch/global scope, configured speed and
ballistics, fast boundary crossing, and impacts at the actual slowed distance.
Projectile fixtures fly at world Y=200 to avoid generated terrain and neighboring
GameTest structures. Mock native players do not log in and trigger gun-pack network sync.

These are server gameplay checks; other mods with custom movement outside Minecraft's
Projectile class require their own compatibility hooks.

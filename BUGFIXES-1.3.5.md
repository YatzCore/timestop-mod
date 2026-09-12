# Bug fixes — 1.3.5

## Barrier distance
Captured projectiles fall individually when more than five blocks from the defender's eyes, even if the barrier key remains held. Nearby projectiles remain suspended. Distance is independent of camera direction. Dimension changes also release captured projectiles in their original world. Released projectiles recover gravity and remain excluded from recapture.

## TACZ Dead Eye
Removed the gun branch that spawned vanilla arrows with artificial gun damage and explosion sounds. An optional adapter in this mod calls TACZ's `IGunOperator.shoot(pitch, yaw)` using the marked target's current position. TACZ owns bullet creation, ammunition consumption, ballistics, sounds and firing restrictions. Dead Eye waits for native cooldown/draw/bolt completion and uses TACZ's bolt action when required. Unsupported or rejected gun shots never fall back to arrows. Queued shots cancel after changing weapon, dimension, death or disconnect.

TACZ is optional and its original JAR is not modified or bundled. This adapter was checked against the installed TACZ 1.1.8-hotfix. TACZ guns must be held in the main hand, as required by its operator. Reloading, sprinting, overheating and other native restrictions can prevent a shot.

## Rune duplication
The watch menu no longer inserts, removes or returns items in client inventory while waiting for the server. One menu operation stays pending until the server acknowledges it. Both rune packets use a shared server transaction that consumes exactly one source rune, updates the socket and returns the previous rune once. Stale slot/type requests are rejected. Replies also acknowledge rejected actions. Socket acknowledgements no longer write NBT into whichever watch happens to be held after a hotbar switch; vanilla inventory synchronization owns the update.

## Validation
The TACZ-enabled Forge GameTest suite passed all 19 tests. The native gun test verifies a TACZ bullet instead of an arrow, real ammunition consumption, cooldown rejection and an empty-gun rejection. Core tests cover distance release, gravity, recapture prevention and twenty repeated rune insert/eject cycles with an unchanged item total. These server tests do not replace a graphical multiplayer/modpack playtest.

Commands:

```powershell
.\gradlew.bat runGameTestServer -PgameTests -PtaczTests --no-problems-report --no-watch-fs
.\gradlew.bat runGameTestServer -PgameTests -PtestDirectory=build/gametest-core --no-problems-report --no-watch-fs
.\gradlew.bat build --no-problems-report --no-watch-fs
```

The opt-in TACZ test dependency is remapped from a read-only copy in the Gradle cache, following [ModDevGradle's dependency remapping support](https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md#remapping-mod-dependencies). No TACZ code or test classes are included in the release JAR.

Source backup before these changes: `build/before-1.3.5.zip`. The previous installed mod and a copy of this snapshot are preserved under `../mod-backups/2026-09-10-before-1.3.5/` when installing the release.

Final verification: all 18 core tests passed without TACZ; all 19 tests passed with TACZ; release build and packaged resource checks passed. Version 1.3.5 is installed and the previous 1.3.4 JAR is backed up.

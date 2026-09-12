# Dead Eye precision and native TACZ feedback — 1.3.6

Dead Eye now removes TACZ spread only from bullets fired for marked targets. An aim supplier identifies each native firing operation, including delayed burst rounds. The projectile retains native speed, damage, ammo type and collision handling. Guidance updates toward the marked entity while it remains alive; walls and other collisions still apply. Ordinary shots retain their normal spread.

The server confirms actual rounds to the shooting player. The client invokes TACZ 1.1.8-hotfix's existing local effects callback: its GunFireEvent, Lua animation trigger, native gun sound and suppressor selection. This callback does not send another shooting request or consume ammunition. Shotgun pellets share one feedback event per round; burst rounds each get feedback, with the client cooldown timestamp updated only on the first round. TACZ continues handling remote-player effects.

This is implemented entirely in Time Stop through optional mixins and a client adapter. The original TACZ JAR, sounds, animation files and gun-pack data are unchanged. The adapter targets the installed 1.1.8-hotfix callback and must be reviewed if TACZ is upgraded. Network protocol 4 requires multiplayer clients and server to update together.

Verification commands:

```powershell
.\gradlew.bat runGameTestServer -PgameTests -PtaczTests --offline --no-problems-report --no-watch-fs
.\gradlew.bat runGameTestServer -PgameTests -PtestDirectory=build/gametest-core --offline --no-problems-report --no-watch-fs
.\gradlew.bat build --offline --no-problems-report --no-watch-fs
```

Client callback selection and its bytecode were inspected against the installed TACZ JAR. Actual first-person audio/animation playback still requires a graphical playtest; server tests cannot verify what the player sees or hears.

Before-change source snapshot: `build/before-1.3.6.zip`.

Validation results: all 21 tests passed with TACZ, and all 19 core tests passed without TACZ. Added checks cover precise initial native bullet direction, ordinary-fire isolation, native collision/damage against a moved marked target, and feedback packet snapshots/burst timing. The original TACZ ammo and cooldown tests still pass.

Release build/resource validation passed. Installed 1.3.6; previous 1.3.5 JAR and source snapshot are backed up in `../mod-backups/2026-09-10-before-1.3.6/`.

# Time Stop 1.3.3: code review and fixes

## How the mod works

`AbstractWatchItem` and the network packets activate a selected time mode. `TimeStopManager` owns the server-wide session, including duration, exemptions, projectile storage, and cooldowns. `TemporalBubbleManager` owns individual moving bubbles; `TemporalBubble` determines their radius, mode, and exemptions.

Mixins intercept Minecraft's entity, block-entity, weather, and clock ticks. Global slow/fast modes change the server's tick interval. Local modes must control the ticks of affected entities without changing the server-wide clock. Client managers receive snapshots and control prediction, shaders, overlays, and rendering.

Combat managers implement rune abilities and buffer damage, projectile velocity, and kinetic block impulses. Those effects must be released only when the affected entity actually leaves stasis. Item models and recipes are separate JSON resources packaged in the JAR.

## Fixed problems

| Problem | Correction |
| --- | --- |
| Barrier and Marksman runes displayed missing textures; their recipes and the coin recipe could not load. | Repaired five JSON files containing literal `\n` escapes between JSON tokens. Added resource validation to the build. The PNG artwork was retained. |
| An untouched projectile reversed direction and acquired a minimum launch speed when time resumed. | Restore its original velocity and gravity setting; reserve the boosted return trajectory for punched projectiles. Preserve untouched fireball acceleration. |
| Bubble cleanup released globally frozen projectiles every tick. | Use the actual entity stasis check, including the global session and exemptions. Remove expired projectile references. |
| Ending one overlapping bubble released effects still covered by another; dimension changes could discharge into the wrong world. | Check remaining stasis before releasing area effects and resolve each bubble's own dimension. |
| A local bubble stopped weather, world time, and furnaces throughout the server. | Restrict world-clock/weather cancellation to global stasis and check each block entity's position. Client position checks now respect dimension and bubble precedence. Random chunk ticks still use the existing intersecting-chunk granularity. |
| FAST_FORWARD could permanently stop the world clock at a non-multiple of five. | Removed the modulo gate that prevented the clock from advancing to its next permitted tick. |
| Local Slow Motion and Fast Forward had no server-side entity tick scheduling. | Apply fractional ticks or additional ticks to affected entities. Local SUPERHOT now uses the bubble's activity level. |
| Activating an inventory watch with V used fallback tier, scope, and cooldown settings. | Share watch selection between the key binding, global activation, and bubble activation. |
| A blank rune in one hand disabled a usable rune in the other. Tachyon could activate from an unrelated bubble elsewhere. | Skip blank rune candidates and resolve Tachyon against the player's actual local/global effect. |
| Barrier-captured projectiles continued running subclass physics. Releasing the barrier could leave stale capture state. | Stop captured projectile ticks, synchronize capture state to observers, release capture on rune removal/death/logout, and prevent repeated repulses while the same key press is held. |
| Global state could survive world shutdown or the initiator's death/logout. Replacing an active global mode skipped cleanup. | Finish the previous session, release buffered effects, remove modifiers, and reset temporal state at shutdown. |
| Switching modes through the watch menu left stale physics/modifiers, and clients kept the bubble's original mode. | Release the old stasis effects, update Matrix modifiers without restarting the duration, and replace the client's complete bubble snapshot. |
| Joining clients missed global state, and projectile-flow mode depended on server static fields unavailable on remote clients. | Send the current global state at login, include projectile-flow settings in synchronization, and snapshot exemption sets before queuing packets. |
| Dedicated servers crashed while loading client-only outline, Dead Eye, barrier, and rune-sync code. | Isolate client hooks/handlers and prevent removed client fields from being referenced by server class initializers. |

## Validation

Verified on 2026-09-10: all 13 Forge server regression tests passed; the release build and resource validation passed. The packaged JAR contains the required Mixin refmap, 47 valid JSON resources, and no regression-test classes or template.

Build and validate every JSON resource and referenced item texture:

```powershell
.\gradlew.bat build --no-problems-report
```

Run the isolated Forge server regression tests:

```powershell
.\gradlew.bat runGameTestServer -PgameTests --no-problems-report
```

Tests cover projectile velocity/gravity, global suspension, blank-rune selection, local/world clock separation, Fast Forward clock progress, local entity tick rates, inventory watch settings, overlapping bubbles, barrier capture, furnace boundaries, global mode replacement, live menu switching, and packet snapshots/serialization. Tests use `build/gametest`, not the profile's saved worlds. Test classes and structures are excluded from release JARs. The Gradle test task also rejects a server startup that exits without reporting successful tests.

The harness follows [Forge's GameTest documentation](https://docs.minecraftforge.net/en/1.20.x/misc/gametest/). This is automated server and resource validation; a graphical playthrough and full compatibility testing with TACZ, Alex's Mobs, and Alex's Caves are still needed.

## Compatibility and backups

Version 1.3.3 uses network protocol 2. Multiplayer clients and the server must both update.

The source state from before this review is preserved in `build/before-bugfix-20260909.zip`, including pre-existing uncommitted changes. The installed mod is now `../mods/timestop-1.20.1-1.3.3.jar`. The original 1.3.2 JAR and a copy of the source backup are preserved in `../mod-backups/2026-09-10-before-1.3.3/`. The installed JAR and backup were verified by SHA-256.

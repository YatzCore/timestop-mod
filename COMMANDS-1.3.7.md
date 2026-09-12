# Time Stop 1.3.7

Installed `timestop-1.20.1-1.3.7.jar` in this Minecraft profile. Restart Minecraft to load it. Multiplayer clients and server need the matching update (network protocol 5).

## Commands

Requires cheats/operator permission level 2. Settings persist with the world.

| Command | Effect |
| --- | --- |
| `/timestop scope global` | All five watch tiers activate a global temporal effect. |
| `/timestop scope sphere` | All watches activate their tier's local sphere, overriding global watch settings. |
| `/timestop scope watch` | Restore each watch's own scope setting. |
| `/timestop redirect look` | Struck projectiles and kinetic shield volleys follow the player's look direction. |
| `/timestop redirect return` | Restore return-to-sender behavior; Vector Control still grants look redirection to its wearer. |
| `/timestop status` | Show current scope and redirection settings. |

Scope changes apply on the next activation; stop an existing effect before activating again. `scope bubble` remains an alias for sphere. Explicit `/timestop start` retains its global command behavior.

## Rune of Vector Control

Get it with `/give @s timestop:rune_vector`, or craft it shapelessly from one blank rune, compass, ender pearl and amethyst shard.

Socket it in a watch in your inventory, or hold the rune. It is a passive modifier: a second watch containing Vector Control can accompany a kinetic barrier watch without disabling its active rune. A loose rune merely sitting in inventory does not activate it.

Left-click a stopped projectile to choose its direction. Each click replaces the chosen direction; turning afterward does not change it. Existing projectile flow settings still determine release timing: flowing player shots may resume during stopped time; suspended shots wait for release. The shield's existing left-click volley uses the same redirection policy, without aim scatter in look mode.

Native TacZ bullets remain native bullets, retain their speed through shield release, and lose old Dead Eye guidance when redirected. TacZ's original JAR is unchanged. These commands do not replace gun sound or animation handling.

## Verification

- 24 Forge GameTests passed with TacZ 1.1.8-hotfix installed, including native firing, moving-target hits, frozen bullet redirection and shield release.
- 22 core GameTests passed without TacZ, including all five watch tiers, command persistence, direction selection and rune coexistence.
- Release build and resource validation passed; JAR contains the new recipe/model and refmap, with no test classes.
- Automated dedicated-server testing; graphical client behavior has not been play-tested for this release.

Previous JAR and pre-change source snapshot: `../mod-backups/2026-09-10-before-1.3.7/`.

# Rewind repair (Minecraft 1.20.1)

Rewind uses a server-wide timeline. Watch activation in sphere scope also routes to the global rewind engine. History begins when the server starts and is bounded by both `rewindHistorySeconds` and `rewindMemoryCapMB`; the memory limit can shorten the available duration.

## Using it

- `/timestop rewind 10`: rewind up to ten seconds of available history immediately.
- `/timestop rewind mode continuous`: watch activation plays history backwards at one recorded tick per server tick, stopping at the watch duration or end of history.
- `/timestop rewind mode burst`: watch activation uses the configured burst duration.
- `/timestop rewind status`: inspect available history.

Restart Minecraft to load the rebuilt jar. Wait several seconds after entering a world before testing. History is in memory and does not survive a restart.

## Repairs

- One frame per whole server tick across all dimensions; player states are captured before simulation. Packet actions stay in the open frame instead of creating extra, environment-less frames.
- Continuous playback suspends recording and world simulation for its entire duration, then resumes recording on the restored branch.
- Block and block-entity changes are reversed in their original interleaved order, including multiple changes at one position in one tick.
- Block entity inventories are observed before mutation so the first inventory edit is captured. Container removal during restoration clears contents first to avoid spawning duplicate drops.
- Mobs use full saved NBT, UUIDs and dimension information. Dead mobs return at their recorded position and health. Chunk loads are distinguished from actual entity spawns.
- Player restoration synchronizes position/rotation, velocity, inventory, cursor stack, selected slot and experience. The inventory rollback setting is honored.
- Restored positions, blocks, daylight and weather are sent to clients. History is reset for a new server; configured capacity and NBT memory estimates are used.

## Verification

`build` passed for Common, Fabric and Forge. A dedicated Fabric GameTest server passed all 20 required tests, including ten rewind regression tests. These exercise block placement and breaking through the normal server packet handlers after burst and continuous rewind; hotbar synchronization; unfinished mining; relative movement synchronization; transient and unloaded entity handling; recovery from malformed entity data; event ordering/memory eviction; container restoration; mob revival; and player restoration. The tests are excluded from release jars.

The Forge and Fabric release jars were copied into the existing test profiles. Previous jars are backed up under `build/rewind-backups`.

## Boundaries

This is a history of observed loaded-world changes, not a full world-save rollback. It does not restore scheduled tick queues, advancements, external mod databases, or revive a player who is still on the death screen. Modded blocks must expose their state through normal block/NBT hooks. Forge compilation and packaging were verified; interactive gameplay with the full TACZ modpack was not tested.

If directory junctions or symlinks are used, run Gradle from the resolved directory to avoid junction-related file-generation errors during compilation.

## Interaction and compatibility polish

The hotbar selection is now explicitly synchronized to the client. Restoration ends stale item use, closes outdated container menus when rolling inventory back, and cancels unfinished mining. Continuous rewind releases its state as soon as it consumes the last frame.

Entity updates no longer create replacement mobs merely because an entity disappeared from the loaded-world lookup. Only recorded destruction permits respawning. Nonpersistent entities and third-party projectiles (including TACZ bullets) can have their existing states restored, but are not recreated from incomplete NBT after despawning. This avoids the null gun-ID spawn-packet failure observed in the TACZ log. Unexpected entity restoration failures are isolated so blocks and players still restore, and failed new entities are discarded.

Movement tracking sends an absolute update after rewind before returning to relative movement, avoiding a second position jump. Unused forward entity snapshots are no longer retained, reducing timeline memory use.

The final verification log is `build/rewind-polish-verification.log`. Both packaged loaders were rebuilt and installed; the full interactive TACZ modpack was not launched during these automated checks.

## TNT and explosion rewind

Continuous rewind now pauses the client TNT simulation instead of letting its local fuse expire while the server rewinds. Restored fuse metadata is sent immediately, including while normal entity tracking is paused. Rewound entity removals also reach clients immediately. TNT placement and neighbor callbacks are suppressed only while a rollback plan is being applied, preventing restored powered TNT from silently priming again. Normal ignition and redstone behavior resume afterward.

Rewinding past ignition restores an unlit TNT block and removes the primed entity. Stopping within the recorded fuse restores primed TNT with that remaining fuse; it can still explode normally after playback ends. The available timeline must extend back to ignition to undo it.

Recorded explosions produce a 12-tick inward violet-and-white particle spiral and a quiet chime. Physical restoration is atomic. Nearby chain explosions coalesce, with a maximum of four active effects and 24 particles emitted globally on an effect tick (every other tick); each effect emits only 36 particles over its lifetime. The radius is capped at six blocks. Effects expire automatically, use normal particle visibility settings/range, and clear on server reset.

Verification: both loader builds passed and all 24 dedicated Fabric server GameTests passed, including actual TNT explosion/ignition rollback, powered TNT callbacks, immediate fuse packets/respawn fuse, and chain-effect bounds. Log: `build/explosion-rewind-verification.log`. The client-side effect was not visually inspected in the full modpack.

## Animated block reconstruction

Ordinary full solid blocks restored into air now reconstruct from eight small textured pieces. The pieces rotate inward while a central block scales and fades into place over 12 client ticks. This applies to broken blocks and explosion damage, with the earlier violet explosion effect remaining in place.

The server restores blocks immediately. A client render-region mask temporarily replaces only the terrain mesh with the animation; it never changes the world block or its collision. The final mesh is rebuilt when the animation expires, and changed blocks cancel stale effects. Dimension changes and disconnects clear the mask.

Limits: 48 nearby blocks per batch/client, 24-block server selection range, 32-block drawing range, and 12 animated blocks with Minimal particles. Containers, block entities and non-full shapes retain normal rendering. Unselected blocks restore normally. Client/server Forge network protocol is now version 7, so multiplayer participants need matching updated jars.

Verification: Common, Forge and Fabric builds passed, and all 26 Fabric server GameTests passed. Added coverage checks material/position packet round trips, oversized packet rejection and animation convergence/opacity. The 3D animation was not visually inspected in-game. Verification log: `build/block-animation-verification.log`.

## Piston, lighting, revival and watch-settings polish

Piston rollback suppresses piston-head destruction and moving-piston completion callbacks while restoring blocks. It removes queued block events at restored positions and defers block-entity restoration until all block states are in place. Moving pistons record their NBT before each movement tick, including their final tick, and missing moving-piston block entities are recreated. A dedicated packet restores moving-piston data on clients, whose forward piston simulation pauses during continuous rewind.

Reconstruction meshes sample adjacent light instead of the interior of the restored solid block, with a modest block-light floor of six. Rendering explicitly enables the lightmap and resets shader color to white.

Revived living mobs receive a 16-client-tick unfolding animation: feet remain anchored, the body expands with a small twist, and two sparse violet particle strands rise around it. Only resurrection triggers the effect, not every rewind position update. At most 16 mobs animate simultaneously within 32 blocks; Minimal particles suppresses the spiral. Dimension changes and disconnects clear effects. Hitboxes, health, and AI are restored immediately.

Watch snapshots assign stable identities. Inventory rollback preserves the latest mode and local/global scope by identity, including watches moved between slots or held on the cursor. Two watches of the same tier keep separate preferences. Durability, rune contents, and actual items still follow inventory rollback.

Common, Fabric and Forge builds passed. All 32 Fabric server GameTests passed, including normal/sticky extension, active movement, sticky retraction, restoration and completion of a moving piston, and separate watch preferences without preserving newly socketed runes. Log: `build/rewind-polish-final-verification.log`. Client visuals were not inspected in the full modpack. Forge network protocol is now 8; clients and servers need matching jars.

## Death-triggered Rewind Rune reliability

Death protection no longer starts playback or freezes the recorder inside the damage callback. Burst mode keeps recording through its ten-tick fade; continuous mode waits until the next server tick. This captures explosion block destruction, TNT removal, drops and other changes that occur after the lethal damage callback. Pending victims share one world rewind. A manual rewind that starts while a rescue is pending services that rescue without scheduling another rollback.

Activation uses the lethal-damage/totem check and loader death callbacks after mitigation, rather than comparing raw incoming damage to health. Armor and absorption can therefore prevent activation. The player is protected throughout a pending request and continuous playback, with sixty server ticks of recovery protection after completion. Server tick counters remain reliable while world simulation is paused. Disconnection cancels pending work without disabling recording; server reset clears session state. With no usable history, the rune still rescues the player and reports that history was unavailable.

Runes receive identities before player, standard container, equipment or dropped-item snapshots. Only the consumed identity is removed during restoration, including older loose-item or chest snapshots. Spare runes are untouched. The consumption ledger survives rewind completion and logout for the lifetime of the server session, preventing later retained history from restoring a spent rune. It resets with the server, whose rewind history also resets. Restored NBT is sanitized on a copy. Recovery clears fire, falling velocity, suffocation air depletion, poison and wither; the existing fade and custom sound remain, with sound played once at completion.

Verification: Common, Fabric and Forge builds passed, and all 43 Fabric server GameTests passed. New integration coverage uses actual lethal TNT explosions in both modes, validates restored blocks and unlit TNT, confirms post-callback changes are recorded, and checks recovery protection, mitigation, precise rune consumption, historical chest restoration, and canceled requests. Previously unregistered rune tests are now included and use survival players rather than the permanently creative GameTest mock. The continuous-history test now excludes fixture placement from its recorded baseline. Log: `build/rewind-rune-final-verification.log`. Forge packaging was verified; the full interactive modpack was not launched.

## Bubble-scoped rewind and live buffer duration

Watch rewind, player rewind commands and the death rune honor the effective WATCH/GLOBAL/SPHERE scope setting. A local rewind captures a fixed sphere at activation using the existing bubble's dimension, center and radius, or the activating watch's configured radius. Death runes use their actual host watch and capture scope before the fade. Explicit global scope remains available; console rewind has no player sphere and is global.

Only block centers and block entities inside the sphere are restored. Living entities and players must have both their present and target positions inside; absent entities may be restored when their recorded position is inside. Entities that have left the sphere are not pulled back from outside. Other dimensions, daylight and weather are excluded from local plans. Only the consumed portion of each history interval is removed, preserving outside history for later rewind.

Local continuous playback uses independent queued steps and a visible fixed sphere. Outside entities, scheduled ticks and history recording continue normally. Inside entities/block entities pause, scheduled block/fluid ticks defer, and random ticks are skipped. Backward changes are excluded from new recordings. The normal watch key, watch use, stop command, clear-history command, logout and server reset clean up local playback. Overlapping local playback is rejected; explicit global playback cancels local jobs first. Client TNT fuse and piston animation simulation recognize local rewind bubbles.

`/timestop rewind buffer <seconds>` accepts each whole-second duration from 1 through 60. It updates live capacity immediately and saves the setting. Shrinking keeps the newest history; growing preserves available history and fills with future ticks. The existing memory cap can reduce actual retained duration. `/timestop rewind buffer` or `/timestop rewind status` reports current usage. Configuration loaded from disk is also clamped to 1–60 seconds.

Validation: both loader builds passed and all 48 Fabric server GameTests passed. Coverage includes inside/outside block restoration, retained outside history, outside simulation and recording during continuous playback, cross-boundary entities and other dimensions, scope override and stop controls, all 60 buffer values and invalid endpoints, and lethal explosion rune rewind leaving outside changes untouched in both modes. Log: `build/bubble-rewind-final-verification.log`. The full modpack and client visuals were not interactively inspected.

## Optional automatic death rewind

`/timestop rewind ondeath true` enables automatic death rewind for all players in the current world, without requiring or consuming a rune. `/timestop rewind ondeath false` disables automatic activation; normal socketed runes continue to work. `/timestop rewind ondeath` reports the current setting. These commands require permission level 2 (or server console). The setting defaults to false and persists in world saved data across restarts; rewind does not undo it.

Automatic activation reuses the existing deferred lethal-damage path, recovery protection, configured burst/continuous mode, and effective watch/server bubble scope. A player with no watch uses the existing fallback sphere radius when local scope applies. A globally scoped activation remains global. Rune consumption and rune-breaking sound are skipped while automatic mode is enabled. Already-started rescues finish if the option is disabled mid-playback. Available recorded history and the existing memory cap still bound restoration; with no usable history, death protection can save the player but cannot reconstruct unrecorded changes.

Validation: Common, Forge and Fabric builds passed; all 51 Fabric server GameTests passed. Added tests cover two actual lethal damage events with an empty inventory and real block rollback, preservation of an equipped rune while enabled, ordinary rune behavior after disabling, admin-only console commands, default OFF and saved-data round trips. Log: `build/auto-death-rewind-verification.log`. Full-modpack interactive gameplay was not tested.

## Buffer duration and memory-budget correction

The buffer duration now controls the default playback length for watches, `/timestop rewind`, and rune/automatic death rewind in both modes. Removed the separate ten-second default burst cutoff, the six-second continuous death-rewind cutoff, and the watch's ordinary ability timer as a limit on continuous rewind. Explicit command durations still override the default. The legacy `rewindBurstSeconds` JSON field is retained for file compatibility but no longer determines default playback.

The configured `rewindMemoryCapMB` is now the baseline budget at thirty seconds. Effective memory scales with longer buffers, retaining the baseline as a minimum for shorter ones: default 30s = 50 MB, 45s = 75 MB, 60s = 100 MB. Custom baselines scale proportionally. Startup and live resizing apply the same calculation; repeated resizing does not compound it. Shrinking retains newest history and enforces the adjusted budget.

`/timestop rewind buffer 60` now reports both the default playback duration and the effective memory budget. `/timestop rewind status` reports recorded seconds, used/budgeted memory, default playback length and the number of frames evicted under memory pressure since the last buffer clear. Memory consumption depends on loaded-world activity, so this remains a finite budget rather than a guarantee for arbitrarily busy worlds. Growing a buffer cannot reconstruct history already discarded; allow the desired interval to record after changing it or restarting.

Verification: all 55 Fabric server GameTests passed, including a complete 1,200-frame command rewind, a 1,200-step watch rewind despite a short watch timer, death playback beyond its former six-second limit, simulated 94 MB retained history, scaling on resize/restart, and memory-eviction reporting. Build log: `build/rewind-duration-verification.log`. Full-modpack interactive gameplay was not tested.


## Duration follow-up: persistent settings and real recording

Both loader entry points now bind `TimeStopConfig` to their config directory's `timestop.json` before loading. Previously no caller set the file path, so load/save were ineffective and command-selected buffer duration and playback mode disappeared across process restarts.

Continuous playback reports requested versus available recorded seconds at activation. Bubble playback reports remaining seconds, warns when the player's historical position is outside its fixed boundary, and logs actual played ticks and the termination reason. No outside-bubble restoration is enabled. A configured capacity cannot supply unrecorded history; pausing the integrated server also pauses history recording.

Validation: 57 Fabric server GameTests passed, including a new test that fills 1200 frames through the recorder's normal server-tick hook and plays them through the regular server events, restoring an event near the oldest end. This uses the GameTest runner's accelerated tick loop, not two minutes of interactive gameplay. Startup config-file creation and save/reload are also covered. Both loader builds passed. Log: `build/rewind-live-duration-verification.log`. The user's full modpack cutoff was not reproduced; activation/end logging now distinguishes insufficient history, completion, cancellation, and owner/dimension changes.

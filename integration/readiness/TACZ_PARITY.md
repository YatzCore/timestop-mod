# TaCZ 1.21.1 candidate verification

These are **candidate builds of Ultimate Time Stop 1.6.6**, with optional native TaCZ integration on all three loaders. They were built from the existing working tree, retaining its other changes. The automated checks below pass. Full gameplay acceptance remains incomplete: the checks do not establish every weapon, attachment, HUD interaction, or scope/mode combination in the requested playtest checklist.

## Fixed targets

| Loader | Loader version tested | Separate TaCZ dependency |
|---|---|---|
| NeoForge | 21.1.233 | [MUKSC 1.1.8-hotfix-r7](https://github.com/MUKSC/TACZ-1.21.1/releases/tag/neoforge-1.1.8-hotfix-r7) |
| Fabric | 0.17.2; Fabric API 0.116.17+1.21.1 | [Refabricated 1.21.1-0.7.1-forge1.1.8-hotfix2](https://modrinth.com/mod/tacz-refabricated/versions), Forge Config API Port 21.1.6 |
| Forge | 52.1.16 | [MUKSC 1.1.6-hotfix-r1](https://github.com/MUKSC/TACZ-1.21.1/releases/tag/1.1.6-hotfix-r1) |

Java 21 is required for 1.21.1. Forge parity concerns weapons present in its older port. No TaCZ source, binary, gun pack, or asset is included in the candidate mod JARs. The controlled QA pack is our own data and references a display supplied by the installed TaCZ pack.

## Changes

- Added Fabric native bullet, shot-task, and ricochet target hooks; registered the omitted Forge shot hook and adapted its older descriptors. The optional configurations use `@Pseudo` and remain required when their targets exist. Required vanilla mixin failures still fail startup.
- Wrapped the round task passed to `CycleTaskHelper.addCycleTask`, replacing the compiler-generated lambda target. `TaczPrecision.wrapShot` restores scheduled aim for each round and clears the scope in `finally`, including cancellation and exceptions. Native `IGunOperator` still decides ammunition and weapon state.
- Routed native entity/block collisions through our handlers once, kept piercing damage, and applied ricochet after native damage. Captured/redirected bullets cancel the remaining native tick. The last ricochet victim is excluded from the next segment.
- Integrated native gravity and drag over Deceleration's slowed step without permanently scaling velocity. Swept defenses include Creative Watch matrix runes. Ammunition uses the installed port's native ammo builder; native transposition retains suspended momentum and changes ownership correctly.
- Consumed the volatile-impact tag once. Removed the redundant boundary teleport packet that conflicted with the normal tracker's relative movement update; stopped positions also tolerate network coordinate quantization. Two real clients reproduced the failure and verified the correction.
- Replaced client lambda reflection with native animation and sound APIs and each port's fire-event mechanism. Feedback sends no firing request and consumes no ammunition. Packet layouts and public settings are unchanged.

## Feature-by-loader results

`Pass` means the specific automated scenario in `NativeTaczScenarios` passed; it is not a claim that the entire manual feature category is finished. The same 30 fixtures run against each selected port. Actual operator firing uses AK-47, DB Long, M700, and an authored controlled semi/burst gun; collision fixtures additionally construct native bullets with explicit properties.

| Scenario group | NeoForge | Fabric | Forge | What was measured |
|---|---|---|---|---|
| Stop/resume, fast crossing, overlapping fields | Pass | Pass | Pass | Position, saved full momentum, suspension, earlier wall precedence |
| Slow Motion, Matrix, SUPERHOT, Fast Forward | Pass | Pass | Pass | Native stationary-field tick budgets and retained velocity |
| Deceleration | Pass | Pass | Pass | Stationary/watch/global scopes, owner shots, slowed collision, gravity and drag |
| Flowing shots / frozen targets | Pass | Pass | Pass | Flow exemption, buffered native damage released once |
| Native piercing / Voltaic Ricochet | Pass | Pass | Pass | Native damage, multiple targets, damage before bounce, six ricochet hits, bullet survival |
| Snatch, deflection, phasing, matrix defense | Pass | Pass | Pass | Swept interception, ammunition identity, ownership, Creative Watch rune lookup |
| Kinetic Barrier / orbital release | Pass | Pass | Pass | Capture/repulse/release identity, velocity and owner |
| Vector slap / transposition / volatile | Pass | Pass | Pass | Direction, suspended momentum, transferred owner, exactly-once volatile impact |
| Chrono Coins | Pass | Pass | Pass | Fast swept hit, two-coin chain, frozen damage, charge consumption/refund on attributed kill |
| Native semi-auto / burst / shotgun | Pass | Pass | Pass | Native ammunition, three scheduled rounds, every pellet receives precision |
| Moving head aim / target death | Pass | Pass | Pass | Visible target movement updates aim; dead target clears guidance |
| Empty gun, cooldown, bolting, reload, switch, death, cancellation | Pass | Pass | Pass | Native gate results and ammo; pending burst stops on switch/death. Cooldown/bolt/reload gates use controlled native state fixtures. This does **not** verify a complete reload/bolt animation cycle. |
| Rewind | Pass | Pass | Pass | Surviving bullet motion, native gun inventory restoration, incomplete-NBT transient bullet guard |
| Packaged local client feedback | Pass | Pass | Pass | Native shot and feedback packet, animation/sound API calls, no extra ammo; screenshot records gun/ammo presentation |
| Built-in AK-47 draw-sound resources | Incomplete | Incomplete | No missing-resource warning observed | r7 and Refabricated log two missing sound resources during equip/draw; see the limitation below |
| Two production clients and reconnect | Pass | Pass | Pass | Socket connection, owner and frozen position agreement, resync after reconnect, native damage after release |
| Packaged client/server, TaCZ installed and absent | Pass | Pass | Pass | Successful startup with required vanilla mixins enabled |
| Packaged native GameTests | 30/30 | 30/30 | Dev suite + packaged client firing | Forge's production launcher has no GameTest target; its native suite runs under ForgeGradle |

The existing Fabric regression suite also passes **118/118**, and the existing NeoForge native Deceleration suite passes **3/3** against r7 on 21.1.233. Shared combat/unit checks pass. Regression fixtures were repaired to register mock players in both the level and PlayerList without attempting a client handshake; outgoing mock packets are absorbed and test completion removes the player. Boundary assertions now allow the small inset needed for network precision.

The 1.20.1 reference uses original TaCZ **1.1.8-hotfix**, not an unofficial port. Its unchanged production source passes **28/30** of the native fixtures, and its real client firing/feedback check passes. Its two failures are observable existing behavior: native volatile impact does not consume the tag, and ricochet allows the remaining native tick to discard the bullet. The candidates correct these behaviors. The baseline fixture adapts Minecraft APIs, uses the older socketed-watch counterpart of the Creative Watch matrix test, and supplies a QA-only wrapper for direct scope testing; its real gun shots still use the original mod's shot mixin. Moving-target fixtures wait for a visible non-hostile entity in a loaded chunk so Peaceful-world removal and chunk promotion do not masquerade as an aiming defect.

## Acceptance still required

All three loaders remain **candidates**, pending the full requested gameplay acceptance. In particular, run full native reload and bolt cycles, repeat body/head damage comparisons on moving enemies, exercise every non-Deceleration mode through watch, pedestal, and global controls, and check Dead Eye HUD selection and burst/shotgun/suppressed presentation while two players use the combat interactions. The automated networking scenario covers one suspended field shot; it does not cover all rune interactions over the network or a remote dedicated-server play session. API success and screenshots do not establish subjective animation or audible sound quality.

NeoForge r7 and Fabric Refabricated 0.7.1 log missing built-in `ar_akilo_drop` / `ar_akilo_raise_shoulder` resources when the AK-47 is equipped/drawn in a fresh world. Their shooting feedback API checks pass, but draw-sound presentation parity remains incomplete. These upstream gun-pack resources are outside our mod and were not repaired; the exact warnings are retained in the installed-client logs. Forge r1 did not emit these warnings in its check. No third-party addon compatibility, world migration, old gun-pack migration, or repair of unrelated port bugs is claimed.

## Reproduce

Use a short isolated copy of the project on Windows (for example `C:\uts1211-tacz-qa`) to avoid Loom/Gradle path-length problems. Keep the working tree contents, but exclude `.git`, `.gradle`, `build`, `run`, and `runs` when copying. Use fresh disposable worlds, and keep downloaded dependencies outside release artifacts. Set the environment variables below to the **runtime** JARs, not Maven sources JARs.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$neoTacz = 'C:\qa-deps\tacz-neoforge-1.21.1-1.1.8-hotfix-r7.jar'
$fabricTacz = 'C:\qa-deps\TACZ-Refabricated-1.21.1-0.7.1-forge1.1.8-hotfix2.jar'
$forgeTacz = 'C:\qa-deps\tacz-1.21.1-1.1.6-hotfix-r1-all.jar'
$configApi = 'C:\qa-deps\ForgeConfigAPIPort-v21.1.6-1.21.1-Fabric.jar'

./gradlew.bat :common:test :fabric:build :forge:build :neoforge:build
./gradlew.bat :fabric:runGametest

./gradlew.bat -I integration/readiness/tacz-native.init.gradle -PtaczLoader=neoforge "-PtaczJar=$neoTacz" :neoforge:runNativeTaczTests :neoforge:nativeTaczJar
./gradlew.bat -I integration/readiness/tacz-native.init.gradle -PtaczLoader=fabric "-PtaczJar=$fabricTacz" "-PtaczConfigJar=$configApi" :fabric:runNativeTaczTests :fabric:remapNativeTaczJar
./gradlew.bat -I integration/readiness/tacz-native.init.gradle -PtaczLoader=forge "-PtaczJar=$forgeTacz" :forge:runNativeTaczTests :forge:nativeTaczJar

./gradlew.bat -I integration/readiness/deceleration-native.init.gradle "-PdecelerationTaczJar=$neoTacz" :neoforge:runDecelerationTests
```

For development client checks, replace `runNativeTaczTests` with `runNativeTaczClient`. For an absent client, use `-PtaczAbsent=true` instead of the TaCZ/Config API properties. QA source sets, QA mods, runtime dependencies, and the controlled pack are excluded from the three release JARs. Fabric's QA mod needs its remapped QA JAR in production; its named QA JAR is insufficient.

For the unchanged 1.20.1 reference, prepare a **separate copy** of `timestop_mod`:

```powershell
python integration/readiness/prepare-tacz-baseline.py C:\uts1201-tacz-baseline
Set-Location C:\uts1201-tacz-baseline
./gradlew.bat --continue -I integration/readiness/tacz-baseline.init.gradle '-PtaczJar=C:\qa-deps\tacz-1.20.1-1.1.8-hotfix.jar' :forge:runNativeTaczTests :forge:runNativeTaczClient
```

The native reference suite is expected to report the two failures described above. Gradle provisions its Java 17 toolchain. Preparation changes QA files only.

Production launch scripts accept official vanilla and loader profile JSONs. Use Minecraft's installed **libraries/assets read-only**, the raw vanilla client JAR and JSON, and the profile for the pinned loader (NeoForge/Forge installers contain `version.json`; Fabric's official loader metadata supplies `profile/json`). The following example runs Fabric; use its standalone GameTest module `fabric-gametest-api-v1-2.0.5+6fc22b9919.jar` in addition to the Fabric API aggregate for QA.

```powershell
$clientArgs = @(
  '--java', "$env:JAVA_HOME\bin\java.exe",
  '--minecraft', 'C:\path\to\.minecraft',
  '--vanilla-info', 'C:\qa-deps\mojang_minecraft_info.json',
  '--vanilla-jar', 'C:\qa-deps\minecraft-client.jar',
  '--loader-info', 'C:\qa-deps\fabric-client.json',
  '--mod', 'fabric\build\libs\timestop-fabric-1.21.1-1.6.6.jar',
  '--qa', 'fabric\build\native-tacz-artifacts\native-tacz-qa-fabric.jar',
  '--fabric-api', 'C:\qa-deps\fabric-api-0.116.17+1.21.1.jar',
  '--gametest-api', 'C:\qa-deps\fabric-gametest-api-v1-2.0.5+6fc22b9919.jar'
)
python integration/readiness/tacz-client-smoke.py fabric C:\qa\fabric-absent @clientArgs
python integration/readiness/tacz-client-smoke.py fabric C:\qa\fabric-installed @clientArgs --tacz $fabricTacz --config-api $configApi
python integration/readiness/tacz-network-smoke.py fabric C:\qa\fabric-network @clientArgs --tacz $fabricTacz --config-api $configApi
```

For NeoForge/Forge, omit the three Fabric API flags and use the corresponding loader profile, candidate JAR, and `native-tacz-qa-<loader>-named.jar`. The network check launches two clients named `TaCZHost` and `TaCZObserver`, publishes a disposable offline LAN server on port 25589, verifies reconnect, and stops both clients. An existing signal directory is rejected. Launchers require a fresh/empty mods directory; they never alter playing installations or account files. Check explicit `NATIVE_TACZ_*_PASS` markers, not only Java's exit code.

Install separate disposable servers using the pinned loader's official installer. For Fabric use its server launcher and Minecraft 1.21.1; for NeoForge use 21.1.233; for Forge use 52.1.16. Do not supply a normal playing world to these scripts. The server check uses loopback port 25587, writes an offline flat-world configuration, and creates a new world name for every invocation.

```powershell
$serverArgs = @('--java', "$env:JAVA_HOME\bin\java.exe", '--mod', 'neoforge\build\libs\timestop-neoforge-1.21.1-1.6.6.jar')
python integration/readiness/tacz-server-smoke.py neoforge C:\qa\neoforge-server @serverArgs
# In an empty disposable mods directory, repeat with the dependency:
python integration/readiness/tacz-server-smoke.py neoforge C:\qa\neoforge-server @serverArgs --tacz $neoTacz
# QA-only production native suite:
python integration/readiness/tacz-server-smoke.py neoforge C:\qa\neoforge-server @serverArgs --tacz $neoTacz --qa neoforge\build\native-tacz-artifacts\native-tacz-qa-neoforge-named.jar
```

Fabric's equivalent requires its Fabric API JAR and, when installed, Config API Port; production native tests also need the standalone GameTest API and remapped QA JAR. Forge uses normal installed/absent production server smoke plus its Gradle native suite and production client firing, because its production launcher has no GameTest target.

## Evidence and artifacts

Candidate JARs, SHA-256 sums, dependency fingerprints, a source fingerprint, logs, and client screenshots are collected under `build/tacz-candidates/`. `verification.json` records counts and artifact hashes. The files are QA evidence; only the three `timestop-*.jar` candidates are distributable mod artifacts. None of the QA JARs or downloaded dependencies are included there.

To regenerate this evidence collection from the original project after completing the checks:

```powershell
python integration/readiness/collect-tacz-results.py --qa C:\uts1211-tacz-qa --baseline C:\uts1201-tacz-baseline --deps C:\qa-deps --baseline-tacz C:\qa-deps\tacz-1.20.1-1.1.8-hotfix.jar
```

The collector checks production source equality, candidate hashes in both client smoke profiles and network runs, explicit pass markers, required mixin configurations, and absence of TaCZ/QA content in every release JAR, including nested JARs.

# Validation — Minecraft 1.21.1 / 1.8.0

Validated 29 October 2026 (America/Halifax). Each GameTest run used an isolated world.

| Check | Result | Evidence |
|---|---|---|
| Shared Fabric gameplay suite | 162/162 | fabric-regressions-final.log |
| Shared physics credit unit tests | 5/5 | shared-unit-tests.xml |
| Forge parity | 44/44 | release-build.log |
| NeoForge parity | 44/44 | release-build.log |
| Native TaCZ, Forge / NeoForge / Fabric | 31/31 each | tacz-forge.log, tacz-neoforge.log, tacz-fabric.log |
| Combined stock Sable and vanilla review suite | 220/220 | sable-regressions.log |
| Native NeoForge Sable/Create | 2/2; 30 landing cases plus tilted rotation | sable-native-landing.log |
| All three actual-client scenes | Pass | fabric-client.log, native-parity-tests.log, screenshots/ |
| All three production-mapped jars, native shooting | Pass | packaged-fabric.log, packaged-forge.log, packaged-neoforge.log |
| Installed Fabric 0.19.5 profile dependencies | Pass, including Sodium/Trinkets | installed-profile-fabric.log |
| Installed NeoForge 21.1.250 profile dependencies | Pass, including Sable/Create/Curios/JEI | installed-profile-neoforge.log |
| Forge 52.1.16 packaged runtime | Pass | packaged-forge.log |
| Artifact inspection | Correct 1.8.0 metadata, new feature classes, no QA/baseline classes | SHA256SUMS.txt |
| Installation | All three profiles replaced with verified hashes; prior jars backed up | installation.json |

The overlapping suite counts are not a count of unique tests. Native TaCZ uses the existing selected ports:
Forge 1.1.6-hotfix-r1, NeoForge 1.1.8-hotfix-r7, Fabric Refabricated 1.21.1-0.7.1-forge1.1.8-hotfix2.
Core compilation used Fabric Loader 0.17.2/Fabric API 0.116.17+1.21.1, Forge 52.1.16 and NeoForge 21.1.233.
Newer installed Fabric/NeoForge loaders were additionally checked from their actual profile metadata and mod dependencies.
The prior packaged native-firing QA harness was reused for the production-jar checks; the new prediction/time-variant scenes
and 31 native server scenarios use the current source. Reconnect/multi-client acceptance was not rerun for this release.
Existing upstream TaCZ draw-sound resource warnings remain documented in the older compatibility report.

## Client and server CPU fixture

Warmed, isolated samples on the same computer, with FPS capped to 60. These measure CPU forecast work and overlay
submission, not whole server-tick/frame time or GPU cost. Scene timings vary and are not modpack performance guarantees.

| Loader | One projectile forecast median | Four body forecasts median | Overlay median / P95 | Capped FPS |
|---|---|---|---|---|
| Fabric 1.21.1 | 0.0641 ms | 1.3373 ms | 0.8162 / 1.0847 ms | 59 |
| Forge 1.21.1 | 0.0671 ms | 1.4229 ms | 0.8949 / 1.3284 ms | 59 |
| NeoForge 1.21.1 | 0.0933 ms | 1.3715 ms | 0.8437 / 1.0793 ms | 60 |
| Prior Fabric 1.20.1 1.8.0 fixture | 0.0713 ms | 1.1708 ms | 1.1558 / 1.4737 ms | 60 |
| Prior Forge 1.20.1 1.8.0 fixture | 0.0706 ms | 1.3195 ms | 1.1705 / 1.3966 ms | 60 |

The 1.21.1 fixture measures the current implementation alone; the 1.20.1 fixture alternated it with a saved baseline.
Body timing also includes native capture in the 1.21.1 run. These figures provide context rather than a controlled percentage comparison.

The client scenes checked movement/aim anchors, crowded overlays, distant untracked arrows, arrow/mob contacts,
selected Enderman destinations, hostile pre-release arrows, actual catch/redirect and expired clicks without disconnect,
day/night potion and cloud footprints, conditional hazard cancellation, and complete overlay cleanup.
Self Acceleration measured 4.05758 normal blocks versus 20.28791 accelerated blocks for equal input duration.
Swimming setting synchronization and actual movement through stopped water also passed on every loader.
Prediction tests compare native motion, water, gravity, jumping, projectile interception, splash eligibility, charged-creeper
radius, TNT fuse credit and cover. Read-only checks compare live state, ammunition and RNG before/after forecasting.

## Release scope

All feasible 1.20.1 features are present or use their native 1.21.1 equivalent. Newer Sable/TaCZ/accessory work was retained.
See PARITY_1_20_1_TO_1_21_1.md for the feature matrix and prediction limits. Forecasts remain estimates of current plans and
loaded terrain; full subjective gameplay acceptance and arbitrary third-party compatibility are not established by these fixtures.

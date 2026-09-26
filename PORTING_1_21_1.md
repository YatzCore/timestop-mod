# Minecraft 1.21.1 — Time Stop 1.6.3

This checkout ports the current 1.6.1 feature set to Fabric, Forge, and NeoForge: five animated armillary pedestals, six observatory variants (including the Acropolis), pedestal controls, and rewind administration. The separate 1.20.1 checkout remains unchanged.

## Installation

Use Java 21 and Minecraft **1.21.1**. Install exactly one matching Time Stop JAR in the instance's `mods` directory, on both client and server. Remove an older Time Stop JAR from that directory first.

| Loader | Build/test version | Release file |
|---|---|---|
| Fabric | Loader 0.16.10, Fabric API 0.116.17+1.21.1 | `timestop-fabric-1.21.1-1.6.3.jar` |
| Forge | 52.1.16 | `timestop-forge-1.21.1-1.6.3.jar` |
| NeoForge | 21.1.75 | `timestop-neoforge-1.21.1-1.6.3.jar` |

Fabric also requires Fabric API. Forge and NeoForge do not require Fabric API. Client and server must use matching mod versions. This port does not establish compatibility with other Minecraft releases or cross-loader multiplayer. Back up a world before upgrading its Minecraft version; direct conversion of a 1.20.1 modded world has not been tested.

Existing 1.21.1 item custom data remains supported. Socketed rune data now retains its identity so a consumed rewind rune cannot reappear through rewind. Newly generated chunks can contain observatories; see `OBSERVATORY.md` for locate/template commands and `PEDESTALS.md` for placement clearance and operation.

## Build and checks

Set `JAVA_HOME` to a Java 21 JDK. On Windows set `PYTHONUTF8=1` for the asset tools. Python asset validation requires Pillow and NumPy.

```text
gradlew :fabric:build :forge:build :neoforge:build
python tools/validate_pedestals.py
python tools/validate_observatory.py --packaged
python tools/package_release.py
gradlew :fabric:runGametest
gradlew :fabric:runPedestalVisual
gradlew :forge:runClient -PfieldVisualQa
python tools/smoke_server.py forge
python tools/smoke_server.py neoforge
```

Each loader writes its release JAR to `<loader>/build/libs/`. Fabric's `-dev.jar` is not a release artifact. Release copies and SHA-256 checksums are in `build/release/1.21.1/` after packaging. Test entry points are excluded from the release JARs.

The server smoke helpers create isolated development servers under each loader's `build/server-smoke-run/`, with loopback-only networking and generated RCON credentials. They place a pedestal and observatory template, roll archive loot, exercise rewind administration, save, and stop. The Fabric test and visual worlds also live under `fabric/build/`.

## Validation completed on September 26, 2026

- All three loader builds succeeded.
- All **93 Fabric GameTests passed**, covering rewind, rune consumption, projectile behavior, pedestal operation, observatory rotations, terrain selection, chunk clipping, persistence, pedestal Slow Motion entity tick budgeting, and pedestal Fast Forward player rate isolation.
- Forge and NeoForge development dedicated servers booted, accepted the placement/loot/admin commands, saved, and stopped cleanly.
- Fabric client QA passed the pedestal menu/slider, inserted-watch rendering, all five tiers, static inventory models, power transitions, beam placement with a Copper watch in a Creative cage, pause/resume, and animation-state cleanup after unloading.
- The isolated Fabric scene captured 18 screenshots and 31 animation frames in `fabric/build/pedestal-visual-run/screenshots/`. Its dense scene reported 91 tracked pedestals and approximately 117 FPS on the test RTX 4080 Laptop GPU; this is one machine's measurement, not a general performance guarantee.
- Asset validators check geometry, texture references, ring clearance, structure palettes and routes, loot references, pedestal NBT, and resources in all three JARs.
- The 1.6.2 Forge fix passed 1,039 active-frame GPU draw checks: no effects drawn at the chunk-translucency event, and effects present after particles.
- The 1.6.3 repair passed 1,365 active-frame GPU draw checks (`build/pedestal-repair-visual.log`): verified single particle-stage rendering across Fancy and Fabulous modes, stationary pedestal unpowering, and moving watch-owner interpolation across ticks 410-480 with zero jitter or duplicate draws. Captured 8 moving-owner screenshots in `forge/build/field-visual-run/screenshots/`.

The initial port received Fabric client QA and Forge/NeoForge dedicated-server smoke tests. The 1.6.3 repairs add targeted entity budgeting and player exemption regression tests, along with moving watch-owner visual QA; see `build/pedestal-repair-gametest.log`, `build/pedestal-repair-visual.log`, and `forge/build/field-visual-run/screenshots/`. Separate installed-production-server tests, cross-version world conversion, and TACZ integration were not run. The development-server checks exercise the loader registrations but are not a full multiplayer playthrough.

## Port details

The shared code uses 1.21.1 block interaction methods, registry-aware block-entity/item serialization, item components, map codecs, and vertex-buffer APIs. Resource processing converts recipes, structures, loot tables, and block/item tags to the 1.21.1 singular directory names. Observatory templates are regenerated with DataVersion 3955 and component-based written books. The port also fixes local rewind incorrectly starting globally, consumed rune restoration, and ricochet propagation after a lethal initial hit.

Forge and NeoForge use official runtime names; Fabric's distribution JAR is remapped by Loom. No additional gameplay dependency is introduced.

# Sable readiness measurements

2026-09-28. Isolated Fabric GameTest server, Minecraft 1.21.1, Java 21, stock Sable 2.0.5 / Companion 1.6.0. `SableScaleTests` runs independent stone bodies at four base native substeps, then ten stop/rewind/thaw cycles. No user world or installed jar is changed.

These are short two-tick samples after initialization, not warmed production benchmarks or a guarantee for complex airships. Numbers include the physics system tick only. Evidence: `sable-readiness-scale.log` (44/44 tests pass, 10:03:14); the same workload also passed in the combined 137-test run.

| Bodies | 1x ms/tick | 0.25x ms/tick | 5x ms/tick | 50x ms/tick | Estimated retained bytes, 10 frames | Released temporary handles, 10 cycles |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 0.112 | 0.215 | 0.562 | 5.100 | 8,558 | 20 |
| 16 | 1.186 | 3.594 | 5.922 | 52.466 | 127,328 | 320 |
| 64 | 1.554 | 4.117 | 6.683 | 64.992 | 507,392 | 1,280 |

All bodies retained finite native poses. Every replaced/thawed temporary constraint reported invalid, and removing all workload bodies returned the body-state map to its initial size. Timeline retained all ten frames within the configured 8 MiB estimator budget.

The 16- and 64-body maximum-rate samples exceed a 50 ms server-tick budget. Do not claim sustained 20 TPS at 50x or full performance acceptance. Actual JVM/native heap retention, large real Create/Aeronautics assemblies, longer samples and multiplayer load still require measurement. Later structural-reconciliation changes also need their own rewind-duration measurements.

Reproduce:

```powershell
.\integration\sable\gradlew.bat -p integration/sable -I "$PWD\integration\sable\review\review.init.gradle" runSableGametest --console=plain
```

Search the resulting log for `SABLE_SCALE`. The tests are excluded from release artifacts.

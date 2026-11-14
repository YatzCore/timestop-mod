# NeoForge Sable landing regression

Run from the repository root using Java 21. Supply a directory containing Sable 2.0.5,
Create 6.0.10, and Create Aeronautics bundled 1.3.2; the fixture copies these dependencies
to its own build directory and loads the current mod sources. It does not open a player save.

```powershell
.\gradlew.bat -I integration\readiness\sable-landing.init.gradle '-Pneoforge_version=21.1.228' '-PsableLandingMods=D:\path\to\mods' :neoforge:runSableLandingTests --console=plain --max-workers=4
```

If `sableLandingMods` is omitted, the fixture uses the existing readiness modpack in
`neoforge/build/readiness-sable-server/mods`. QA classes and template resources are
added only by the init script and are excluded from release jars.

The landing GameTest exercises twenty-one cases: normal time plus Deceleration Field, Fast Forward,
Slow Motion, Matrix, Superhot, and Time Stop, each with a nine-block stone structure dropped
from above a stationary bubble, a rotated falling structure, and a rotated structure starting on the ground inside it.
It uses the stock assembly helper and the reset/teleport operations used by Sable's commands.
Frozen/idle modes resume after 150 physics ticks. Assertions check survival, finite and bounded
position/velocity, all nine blocks' mass, and final landing height after 450 physics ticks.
It also checks angular speed, rotation jumps, native/published pose agreement, and settled
position/velocity after landing. A second test preserves pitch, yaw and roll for a tilted
six-block structure across sixty Slow Motion physics ticks with no applied torque, then
checks that an intentional angular velocity survives twenty slow physics ticks and advances
the structure's rotation without amplification.
The fixture uploads its bedrock floor explicitly because GameTest chunks have no player physics tickets.

Before the fix, Slow Motion terrain contact produced about 9,200 blocks/second upward velocity
and the body was removed. The 1.6.4 landing fix preserves those blocks, but the rotation
checks also reproduce an abrupt 1.4-radian turn during entry into Slow Motion. The 1.6.5
constraint frame and native-pose fixes keep the structures stable and synchronized.
Real moving-body collision buffering remains covered by the separate Sable readiness suite's
native contact test; rotated collision decoding and static terrain exclusion have focused checks there.

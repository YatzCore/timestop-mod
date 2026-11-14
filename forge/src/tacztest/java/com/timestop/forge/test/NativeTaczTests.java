package com.timestop.forge.test;
import net.minecraft.gametest.framework.*;
import com.timestop.test.NativeTaczScenarios;
import net.minecraftforge.gametest.*;

@GameTestHolder(value="timestop",namespace="timestop")
@GameTestDontPrefix
public class NativeTaczTests {
    @GameTest(template="tacz_empty",batch="timestop.tacz_00",timeoutTicks=100)
    public static void realTaczBulletsSlowInStationaryWatchAndGlobalScopes(GameTestHelper h) throws Exception {NativeTaczScenarios.realTaczBulletsSlowInStationaryWatchAndGlobalScopes(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_01",timeoutTicks=100)
    public static void realTaczFastBulletCannotSkipTheBubbleAndRestoresSpeedOnExit(GameTestHelper h) throws Exception {NativeTaczScenarios.realTaczFastBulletCannotSkipTheBubbleAndRestoresSpeedOnExit(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_02",timeoutTicks=100)
    public static void realTaczBulletDoesNotHitBeyondItsSlowedStep(GameTestHelper h) throws Exception {NativeTaczScenarios.realTaczBulletDoesNotHitBeyondItsSlowedStep(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_03",timeoutTicks=100)
    public static void fastStoppedBoundaryAndOverlap(GameTestHelper h) throws Exception {NativeTaczScenarios.fastStoppedBoundaryAndOverlap(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_04",timeoutTicks=100)
    public static void wallBeforeStoppedBoundary(GameTestHelper h) throws Exception {NativeTaczScenarios.wallBeforeStoppedBoundary(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_05",timeoutTicks=100)
    public static void nativeSemiAutoAmmoAndPrecision(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeSemiAutoAmmoAndPrecision(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_06",timeoutTicks=100)
    public static void nativeEmptyAndWeaponSwitch(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeEmptyAndWeaponSwitch(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_07",timeoutTicks=10000)
    public static void nativeBurstEveryRound(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeBurstEveryRound(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_08",timeoutTicks=100)
    public static void nativeShotgunEveryPellet(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeShotgunEveryPellet(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_09",timeoutTicks=100)
    public static void ammunitionIdentityAndSnatch(GameTestHelper h) throws Exception {NativeTaczScenarios.ammunitionIdentityAndSnatch(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_10",timeoutTicks=100)
    public static void orbitCaptureRetainsNativeIdentity(GameTestHelper h) throws Exception {NativeTaczScenarios.orbitCaptureRetainsNativeIdentity(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_11",timeoutTicks=100)
    public static void shotScopeFinallyAndScheduledContext(GameTestHelper h) throws Exception {NativeTaczScenarios.shotScopeFinallyAndScheduledContext(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_12",timeoutTicks=100)
    public static void nativePiercingPreservesDamage(GameTestHelper h) throws Exception {NativeTaczScenarios.nativePiercingPreservesDamage(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_13",timeoutTicks=100)
    public static void ricochetAfterNativeDamage(GameTestHelper h) throws Exception {NativeTaczScenarios.ricochetAfterNativeDamage(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_14",timeoutTicks=100)
    public static void nativeCoinSweepAndBufferedChain(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeCoinSweepAndBufferedChain(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_15",timeoutTicks=100)
    public static void nativeMovingTargetHeadGuidance(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeMovingTargetHeadGuidance(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_16",timeoutTicks=100)
    public static void nativeStationaryTemporalModes(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeStationaryTemporalModes(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_17",timeoutTicks=100)
    public static void nativeBarrierCaptureAndRepulse(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeBarrierCaptureAndRepulse(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_18",timeoutTicks=100)
    public static void nativeSweptDeflectionAndSnatching(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeSweptDeflectionAndSnatching(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_19",timeoutTicks=100)
    public static void flowingNativeHitBuffersOnce(GameTestHelper h) throws Exception {NativeTaczScenarios.flowingNativeHitBuffersOnce(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_20",timeoutTicks=10000)
    public static void nativeBurstWeaponSwitchCancelsRounds(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeBurstWeaponSwitchCancelsRounds(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_21",timeoutTicks=100)
    public static void nativeTransientRewindGuard(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeTransientRewindGuard(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_22",timeoutTicks=100)
    public static void nativeDecelerationGravityAndDrag(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeDecelerationGravityAndDrag(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_23",timeoutTicks=100)
    public static void nativeBoltAndReloadRules(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeBoltAndReloadRules(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_24",timeoutTicks=10000)
    public static void nativeBurstDeathCancelsRounds(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeBurstDeathCancelsRounds(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_25",timeoutTicks=100)
    public static void nativePhasingAndCreativeMatrix(GameTestHelper h) throws Exception {NativeTaczScenarios.nativePhasingAndCreativeMatrix(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_26",timeoutTicks=100)
    public static void nativeVectorSlapAndTransposition(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeVectorSlapAndTransposition(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_27",timeoutTicks=100)
    public static void nativeVolatileImpactOnce(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeVolatileImpactOnce(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_28",timeoutTicks=100)
    public static void nativeRewindMotionAndInventory(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeRewindMotionAndInventory(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_29",timeoutTicks=100)
    public static void nativeCancelledFireAndCoinCharges(GameTestHelper h) throws Exception {NativeTaczScenarios.nativeCancelledFireAndCoinCharges(h);}
    @GameTest(template="tacz_empty",batch="timestop.tacz_30",timeoutTicks=100)
    public static void nativePredictionProfilesAndTimeVariants(GameTestHelper h) throws Exception {NativeTaczScenarios.nativePredictionProfilesAndTimeVariants(h);}
}

package com.timestop.parity;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("timestop")
@PrefixGameTestTemplate(false)
public class ParityTests {
    @GameTest(template="parity_empty", batch="prediction_arrow_interaction")
    public static void hostileArrowRedirectUsesPacketInPredictionAndRejectsDistantOrBlockedShots(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().hostileArrowRedirectUsesPacketInPredictionAndRejectsDistantOrBlockedShots(h);}
    @GameTest(template="parity_empty", batch="prediction_prepared")
    public static void preparedSkeletonShotMatchesNativeAimWithoutShootingOrConsumingRandomness(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().preparedSkeletonShotMatchesNativeAimWithoutShootingOrConsumingRandomness(h);}
    @GameTest(template="parity_empty", batch="prediction_prepared")
    public static void preparedPillagerCrossbowExcludesFireworksAndAbortedCharge(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().preparedPillagerCrossbowExcludesFireworksAndAbortedCharge(h);}
    @GameTest(template="parity_empty", batch="prediction")
    public static void unlocksSelectionAndExistingOrdinalsRemainStable(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().unlocksSelectionAndExistingOrdinalsRemainStable(h);}
    @GameTest(template="parity_empty", batch="prediction")
    public static void continuousLocalSlowdownMatchesTheForecast(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().continuousLocalSlowdownMatchesTheForecast(h);}
    @GameTest(template="parity_empty", batch="prediction")
    public static void projectileFamiliesForecastGravityDragAndAcceleration(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().projectileFamiliesForecastGravityDragAndAcceleration(h);}
    @GameTest(template="parity_empty", batch="prediction")
    public static void waterDragMatchesActualArrowMotion(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().waterDragMatchesActualArrowMotion(h);}
    @GameTest(template="parity_empty", batch="prediction")
    public static void wallAndMovingTargetSweepsStopThePath(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().wallAndMovingTargetSweepsStopThePath(h);}
    @GameTest(template="parity_empty", batch="prediction")
    public static void personalOwnershipGlobalTimingAndIndependentSpeed(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().personalOwnershipGlobalTimingAndIndependentSpeed(h);}
    @GameTest(template="parity_empty", batch="prediction")
    public static void snapshotsRoundTripAndRejectUnboundedCounts(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().snapshotsRoundTripAndRejectUnboundedCounts(h);}
    @GameTest(template="parity_empty", batch="prediction")
    public static void fullArrowArcReachesTheActualLandingBeyondTwoSeconds(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().fullArrowArcReachesTheActualLandingBeyondTwoSeconds(h);}
    @GameTest(template="parity_empty", batch="prediction")
    public static void plannedMobRouteTurnsAndSerializesWithoutChangingNavigation(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().plannedMobRouteTurnsAndSerializesWithoutChangingNavigation(h);}
    @GameTest(template="parity_empty", batch="prediction")
    public static void slowedMobsKeepContinuousMovementAndTwentyEntityTicks(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().slowedMobsKeepContinuousMovementAndTwentyEntityTicks(h);}
    @GameTest(template="parity_empty",batch="prediction")
    public static void arrowInterceptUsesTheTurningRouteAndRelativeSweep(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().arrowInterceptUsesTheTurningRouteAndRelativeSweep(h);}
    @GameTest(template="parity_empty",batch="prediction")
    public static void distantOwnedFlightRetainsServerGeometryAndTargets(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().distantOwnedFlightRetainsServerGeometryAndTargets(h);}
    @GameTest(template="parity_empty",batch="prediction")
    public static void nearMobForecastMatchesNativeNavigationMovement(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().nearMobForecastMatchesNativeNavigationMovement(h);}
    @GameTest(template="parity_empty",batch="prediction")
    public static void highSpeedRangeLimitStillChecksTheNearerWall(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().highSpeedRangeLimitStillChecksTheNearerWall(h);}
    @GameTest(template="parity_empty",batch="prediction_teleport",timeoutTicks=60)
    public static void endermanTellRevealsTheExactReservedLanding(GameTestHelper h) {new com.timestop.parity.test.PredictionTests().endermanTellRevealsTheExactReservedLanding(h);}
    @GameTest(template="parity_empty",batch="prediction_outcomes")
    public static void splashHonorsDistanceDirectHitsAndDoesNotInventWallOcclusion(GameTestHelper h) {new com.timestop.parity.test.PredictionOutcomeTests().splashHonorsDistanceDirectHitsAndDoesNotInventWallOcclusion(h);}
    @GameTest(template="parity_empty",batch="prediction_outcomes")
    public static void splashExcludesShortEffectsVerticalMissesAndPotionImmuneTargets(GameTestHelper h) {new com.timestop.parity.test.PredictionOutcomeTests().splashExcludesShortEffectsVerticalMissesAndPotionImmuneTargets(h);}
    @GameTest(template="parity_empty",batch="prediction_outcomes")
    public static void lingeringShowsInitialCloudWithoutInventingFutureEntrants(GameTestHelper h) {new com.timestop.parity.test.PredictionOutcomeTests().lingeringShowsInitialCloudWithoutInventingFutureEntrants(h);}
    @GameTest(template="parity_empty",batch="prediction_outcomes")
    public static void explosionExposureSamplesFutureGeometryAndLeavesWorldUntouched(GameTestHelper h) {new com.timestop.parity.test.PredictionOutcomeTests().explosionExposureSamplesFutureGeometryAndLeavesWorldUntouched(h);}
    @GameTest(template="parity_empty",batch="prediction_outcomes")
    public static void tntForecastMatchesNativeCollisionAndFluidSteps(GameTestHelper h) {new com.timestop.parity.test.PredictionOutcomeTests().tntForecastMatchesNativeCollisionAndFluidSteps(h);}
    @GameTest(template="parity_empty",batch="prediction_outcomes")
    public static void tntForecastUsesPredictionTickCreditAndRetractsLongFuse(GameTestHelper h) {new com.timestop.parity.test.PredictionOutcomeTests().tntForecastUsesPredictionTickCreditAndRetractsLongFuse(h);}
    @GameTest(template="parity_empty",batch="prediction_outcomes")
    public static void creeperUsesNativeFusePowerAndClearsWhenSwellingStops(GameTestHelper h) {new com.timestop.parity.test.PredictionOutcomeTests().creeperUsesNativeFusePowerAndClearsWhenSwellingStops(h);}
    @GameTest(template="parity_empty",batch="prediction_outcomes")
    public static void largeFireballsUseConfiguredPowerButSmallFireballsDoNotInventExplosions(GameTestHelper h) {new com.timestop.parity.test.PredictionOutcomeTests().largeFireballsUseConfiguredPowerButSmallFireballsDoNotInventExplosions(h);}
    @GameTest(template="parity_empty",batch="prediction_outcomes")
    public static void tntForecastCrossesFieldBoundaryWithNativeCreditReset(GameTestHelper h) {new com.timestop.parity.test.PredictionOutcomeTests().tntForecastCrossesFieldBoundaryWithNativeCreditReset(h);}
    @GameTest(template="parity_empty",batch="prediction_outcomes")
    public static void tntStopsAtUnloadedGeometryWithoutLoadingIt(GameTestHelper h) {new com.timestop.parity.test.PredictionOutcomeTests().tntStopsAtUnloadedGeometryWithoutLoadingIt(h);}
    @GameTest(template="parity_empty",batch="prediction_outcomes")
    public static void impactMetadataAndSurfaceNormalsSurviveBoundedNetworkRoundTrip(GameTestHelper h) {new com.timestop.parity.test.PredictionOutcomeTests().impactMetadataAndSurfaceNormalsSurviveBoundedNetworkRoundTrip(h);}
    @GameTest(template="parity_empty",batch="prediction_catching")
    public static void predictionCatchWorksOnceAndPreservesTippedArrow(GameTestHelper h) {new com.timestop.parity.test.PredictionAccuracyTests().predictionCatchWorksOnceAndPreservesTippedArrow(h);}
    @GameTest(template="parity_empty",batch="prediction_catching")
    public static void catchRejectsRemoteFieldsCoverDistanceAndCancelledPrediction(GameTestHelper h) {new com.timestop.parity.test.PredictionAccuracyTests().catchRejectsRemoteFieldsCoverDistanceAndCancelledPrediction(h);}
    @GameTest(template="parity_empty",batch="prediction_accuracy")
    public static void bodyCollisionStepsSlabsAndSlidesAlongWallInsteadOfPassingThrough(GameTestHelper h) {new com.timestop.parity.test.PredictionAccuracyTests().bodyCollisionStepsSlabsAndSlidesAlongWallInsteadOfPassingThrough(h);}
    @GameTest(template="parity_empty",batch="prediction_accuracy")
    public static void risingBodyFallsNativelyAndArrowHitsItsFutureBodyWithoutMutation(GameTestHelper h) {new com.timestop.parity.test.PredictionAccuracyTests().risingBodyFallsNativelyAndArrowHitsItsFutureBodyWithoutMutation(h);}
    @GameTest(template="parity_empty",batch="prediction_accuracy")
    public static void distantPathCompactionPreservesSharpTurnsAndContactTime(GameTestHelper h) {new com.timestop.parity.test.PredictionAccuracyTests().distantPathCompactionPreservesSharpTurnsAndContactTime(h);}
    @GameTest(template="parity_empty",batch="prediction_accuracy")
    public static void copiedStepHeightSurvivesBuildersWireAndRejectsInvalidValues(GameTestHelper h) {new com.timestop.parity.test.PredictionAccuracyTests().copiedStepHeightSurvivesBuildersWireAndRejectsInvalidValues(h);}
    @GameTest(template="parity_empty",batch="prediction_accuracy")
    public static void upperNavigationNodeForecastsJumpOntoAFullBlock(GameTestHelper h) {new com.timestop.parity.test.PredictionAccuracyTests().upperNavigationNodeForecastsJumpOntoAFullBlock(h);}
    @GameTest(template="parity_empty",batch="time_variants")
    public static void variantsPreserveOrdinalsWatchSelectionAndNetwork(GameTestHelper h) {new com.timestop.parity.test.TimeVariantsTests().variantsPreserveOrdinalsWatchSelectionAndNetwork(h);}
    @GameTest(template="parity_empty",batch="time_variants")
    public static void combatSlowAffectsOnlyMobsAndAllProjectilesLocallyAndGlobally(GameTestHelper h) {new com.timestop.parity.test.TimeVariantsTests().combatSlowAffectsOnlyMobsAndAllProjectilesLocallyAndGlobally(h);}
    @GameTest(template="parity_empty",batch="time_variants")
    public static void combatArrowMovementStaysContinuousAndCancelsImmediately(GameTestHelper h) {new com.timestop.parity.test.TimeVariantsTests().combatArrowMovementStaysContinuousAndCancelsImmediately(h);}
    @GameTest(template="parity_empty",batch="time_variants")
    public static void selfAccelerationRunsTheOwnersPersonalClock(GameTestHelper h) {new com.timestop.parity.test.TimeVariantsTests().selfAccelerationRunsTheOwnersPersonalClock(h);}
    @GameTest(template="parity_empty",batch="time_variants")
    public static void selfAccelerationAlsoSpeedsUpHarmUnlessCasual(GameTestHelper h) {new com.timestop.parity.test.TimeVariantsTests().selfAccelerationAlsoSpeedsUpHarmUnlessCasual(h);}
    @GameTest(template="parity_empty",batch="time_variants")
    public static void selfAccelerationOnlyChangesOwnerInputAndLeavesNoPersistentBoost(GameTestHelper h) {new com.timestop.parity.test.TimeVariantsTests().selfAccelerationOnlyChangesOwnerInputAndLeavesNoPersistentBoost(h);}
    @GameTest(template="parity_empty",batch="time_variants")
    public static void stoppedWaterCanBeEnteredWhenSwimmingIsEnabled(GameTestHelper h) {new com.timestop.parity.test.TimeVariantsTests().stoppedWaterCanBeEnteredWhenSwimmingIsEnabled(h);}
    @GameTest(template="parity_empty",batch="time_variants")
    public static void swimmingSettingIsAuthorizedAndSynchronizedWithoutChangingSpeeds(GameTestHelper h) {new com.timestop.parity.test.TimeVariantsTests().swimmingSettingIsAuthorizedAndSynchronizedWithoutChangingSpeeds(h);}
    @GameTest(template="parity_empty",batch="timestop.parity_rewind_globalPlaybackHonorsFractionalSpeedAndRequestedHistory")
    public static void globalPlaybackHonorsFractionalSpeedAndRequestedHistory(GameTestHelper h) {new com.timestop.parity.test.RewindSpeedTests().globalPlaybackHonorsFractionalSpeedAndRequestedHistory(h);}
    @GameTest(template="parity_empty",batch="timestop.parity_rewind_localPlaybackConsumesMultipleFramesAndClearsCreditOnCancel")
    public static void localPlaybackConsumesMultipleFramesAndClearsCreditOnCancel(GameTestHelper h) {new com.timestop.parity.test.RewindSpeedTests().localPlaybackConsumesMultipleFramesAndClearsCreditOnCancel(h);}
    @GameTest(template="parity_empty",batch="edge_cases")
    public static void tachyonRuneTriplesRealBlockBreaking(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().tachyonRuneTriplesRealBlockBreaking(h);}
    @GameTest(template="parity_empty",batch="edge_cases")
    public static void fastForwardPersonalClockMatchesTheCappedClientClock(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().fastForwardPersonalClockMatchesTheCappedClientClock(h);}
    @GameTest(template="parity_empty",batch="edge_cases")
    public static void spectatorPassengerAndDeadPlayersAreNotAccelerated(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().spectatorPassengerAndDeadPlayersAreNotAccelerated(h);}
    @GameTest(template="parity_empty",batch="edge_cases")
    public static void acceleratedFallOnlyInFreeFallAndWithinTheCap(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().acceleratedFallOnlyInFreeFallAndWithinTheCap(h);}
    @GameTest(template="parity_empty",batch="edge_cases")
    public static void overlappingFieldsNeverStackThePersonalClock(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().overlappingFieldsNeverStackThePersonalClock(h);}
    @GameTest(template="parity_empty",batch="edge_cases")
    public static void configExtremesStayBoundedAndCleanUp(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().configExtremesStayBoundedAndCleanUp(h);}
    @GameTest(template="parity_empty",batch="edge_cases")
    public static void randomTickHookScalesFreezesAndResetsItsGuard(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().randomTickHookScalesFreezesAndResetsItsGuard(h);}
    @GameTest(template="parity_empty",batch="edge_cases")
    public static void localFieldDoesNotFollowTheOwnerAcrossDimensions(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().localFieldDoesNotFollowTheOwnerAcrossDimensions(h);}
    @GameTest(template="parity_empty",batch="edge_cases")
    public static void remoteServerSpeedsNeverReachTheLocalConfigFile(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().remoteServerSpeedsNeverReachTheLocalConfigFile(h);}
}

package com.timestop.fabric.test;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.*;
public class EdgeCaseTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE,batch="edge_cases")
    public void tachyonRuneTriplesRealBlockBreaking(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().tachyonRuneTriplesRealBlockBreaking(h);}
    @GameTest(template=EMPTY_STRUCTURE,batch="edge_cases")
    public void fastForwardPersonalClockMatchesTheCappedClientClock(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().fastForwardPersonalClockMatchesTheCappedClientClock(h);}
    @GameTest(template=EMPTY_STRUCTURE,batch="edge_cases")
    public void spectatorPassengerAndDeadPlayersAreNotAccelerated(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().spectatorPassengerAndDeadPlayersAreNotAccelerated(h);}
    @GameTest(template=EMPTY_STRUCTURE,batch="edge_cases")
    public void acceleratedFallOnlyInFreeFallAndWithinTheCap(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().acceleratedFallOnlyInFreeFallAndWithinTheCap(h);}
    @GameTest(template=EMPTY_STRUCTURE,batch="edge_cases")
    public void overlappingFieldsNeverStackThePersonalClock(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().overlappingFieldsNeverStackThePersonalClock(h);}
    @GameTest(template=EMPTY_STRUCTURE,batch="edge_cases")
    public void configExtremesStayBoundedAndCleanUp(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().configExtremesStayBoundedAndCleanUp(h);}
    @GameTest(template=EMPTY_STRUCTURE,batch="edge_cases")
    public void randomTickHookScalesFreezesAndResetsItsGuard(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().randomTickHookScalesFreezesAndResetsItsGuard(h);}
    @GameTest(template=EMPTY_STRUCTURE,batch="edge_cases")
    public void localFieldDoesNotFollowTheOwnerAcrossDimensions(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().localFieldDoesNotFollowTheOwnerAcrossDimensions(h);}
    @GameTest(template=EMPTY_STRUCTURE,batch="edge_cases")
    public void remoteServerSpeedsNeverReachTheLocalConfigFile(GameTestHelper h) {new com.timestop.parity.test.EdgeCaseTests().remoteServerSpeedsNeverReachTheLocalConfigFile(h);}
}

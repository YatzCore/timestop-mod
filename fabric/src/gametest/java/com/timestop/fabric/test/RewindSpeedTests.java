package com.timestop.fabric.test;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.*;
public class RewindSpeedTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE,batch="parity_rewind_speed")
    public void globalPlaybackHonorsFractionalSpeedAndRequestedHistory(GameTestHelper h) {new com.timestop.parity.test.RewindSpeedTests().globalPlaybackHonorsFractionalSpeedAndRequestedHistory(h);}
    @GameTest(template=EMPTY_STRUCTURE,batch="parity_rewind_speed_local")
    public void localPlaybackConsumesMultipleFramesAndClearsCreditOnCancel(GameTestHelper h) {new com.timestop.parity.test.RewindSpeedTests().localPlaybackConsumesMultipleFramesAndClearsCreditOnCancel(h);}
}

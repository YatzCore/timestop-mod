package com.timestop.fabric.test;

import com.timestop.core.*;
import com.timestop.core.rewind.*;
import com.timestop.combat.RewindRuneManager;
import com.timestop.config.TimeStopConfig;
import com.timestop.item.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class AutoDeathRewindTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE,batch="auto_death_command")
    public void commandIsAdminOnlyAndSettingPersists(GameTestHelper h) throws Exception {
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        com.timestop.command.TimeStopCommand.register(dispatcher);
        var source=h.getLevel().getServer().createCommandSourceStack();
        var settings=TimeStopSavedData.get();boolean old=settings.isAutoDeathRewind();
        try {
            h.assertTrue(!TimeStopSavedData.load(new net.minecraft.nbt.CompoundTag()).isAutoDeathRewind(),"New worlds must default to OFF");
            dispatcher.execute("timestop rewind ondeath true",source.withPermission(4));
            h.assertTrue(settings.isAutoDeathRewind(),"Console command must enable automatic protection");
            h.assertTrue(TimeStopSavedData.load(settings.save(new net.minecraft.nbt.CompoundTag())).isAutoDeathRewind(),"Enabled setting must survive save/load");
            boolean denied=false;
            try { dispatcher.execute("timestop rewind ondeath false",source.withPermission(0)); }
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied=true; }
            h.assertTrue(denied && settings.isAutoDeathRewind(),"Non-admins must not change the setting");
            dispatcher.execute("timestop rewind ondeath",source.withPermission(4));
            dispatcher.execute("timestop rewind ondeath false",source.withPermission(4));
            h.assertTrue(!TimeStopSavedData.load(settings.save(new net.minecraft.nbt.CompoundTag())).isAutoDeathRewind(),"Disabling must persist too");
        } finally { settings.setAutoDeathRewind(old); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="auto_death_rune")
    public void automaticModeNeverConsumesSocketedRunes(GameTestHelper h) {
        var player=RewindRuneTests.survivalPlayer(h);
        var settings=TimeStopSavedData.get();boolean old=settings.isAutoDeathRewind();
        try {
            var watch=new ItemStack(ModItems.CHRONOS_WATCH.get());
            AbstractWatchItem.setSocketedRune(watch,new ItemStack(ModItems.RUNE_REWIND.get()));
            player.getInventory().setItem(0,watch);
            settings.setAutoDeathRewind(true);
            h.assertTrue(RewindRuneManager.tryTriggerDeathRewind(player,player.damageSources().generic()),"Automatic rescue must activate");
            h.assertTrue(!AbstractWatchItem.getSocketedRune(watch).isEmpty(),"Automatic rescue must not spend an equipped rune");
            RewindRuneManager.clearPlayer(player.getUUID());
            settings.setAutoDeathRewind(false);
            h.assertTrue(RewindRuneManager.tryTriggerDeathRewind(player,player.damageSources().generic()),"Disabling auto mode must preserve ordinary rune behavior");
            h.assertTrue(AbstractWatchItem.getSocketedRune(watch).isEmpty(),"Ordinary rune activation must still consume the rune");
            RewindRuneManager.clearPlayer(player.getUUID());
            h.assertTrue(!RewindRuneManager.tryTriggerDeathRewind(player,player.damageSources().generic()),"Disabled mode with no rune must not rescue");
        } finally {RewindRuneManager.clearPlayer(player.getUUID());settings.setAutoDeathRewind(old);player.discard();}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="auto_death_repeated",timeoutTicks=100)
    public void repeatedRealLethalDamageRewindsWithoutAnyItems(GameTestHelper h) {
        var player=RewindRuneTests.survivalPlayer(h);var level=h.getLevel();
        var settings=TimeStopSavedData.get();boolean old=settings.isAutoDeathRewind();var oldScope=settings.getWatchScope();
        String oldMode=TimeStopConfig.COMMON.rewindMode.get();
        var recorder=TickRecorder.getInstance();var pos=h.absolutePos(new BlockPos(2,2,2));
        Runnable cleanup=()->{
            RewindRuneManager.clearPlayer(player.getUUID());settings.setAutoDeathRewind(old);settings.setWatchScope(oldScope);
            TimeStopConfig.COMMON.rewindMode.set(oldMode);player.discard();recorder.clearTrackingData();recorder.getTimelineBuffer().clear();
        };
        settings.setAutoDeathRewind(true);settings.setWatchScope(TimeStopSavedData.WatchScope.GLOBAL);TimeStopConfig.COMMON.rewindMode.set("BURST");
        player.setNoGravity(true);player.setPos(pos.getX(),pos.getY()+2,pos.getZ());player.getInventory().clearContent();
        level.setBlock(pos,Blocks.STONE.defaultBlockState(),18);
        recorder.clearTrackingData();recorder.getTimelineBuffer().clear();recorder.serverTick(level.getServer());
        level.setBlock(pos,Blocks.GOLD_BLOCK.defaultBlockState(),18);
        player.hurt(player.damageSources().genericKill(),Float.MAX_VALUE);
        h.runAfterDelay(14,()->{
            try {
                h.assertTrue(player.isAlive() && level.getBlockState(pos).is(Blocks.STONE),"First lethal hit must save the unarmed player and actually rewind the world");
                // End the grace period explicitly so this exercises a second activation rather than immunity.
                RewindRuneManager.clearPlayer(player.getUUID());player.invulnerableTime=0;
                recorder.clearTrackingData();recorder.getTimelineBuffer().clear();recorder.serverTick(level.getServer());
                level.setBlock(pos,Blocks.DIAMOND_BLOCK.defaultBlockState(),18);
                player.hurt(player.damageSources().genericKill(),Float.MAX_VALUE);
                h.runAfterDelay(14,()->{
                    try {
                        h.assertTrue(player.isAlive() && level.getBlockState(pos).is(Blocks.STONE),"Automatic mode must activate again without a consumable");
                        h.assertTrue(player.getInventory().isEmpty(),"Neither activation should require or create items");
                        h.succeed();
                    } finally {cleanup.run();}
                });
            } catch(RuntimeException error) {cleanup.run();throw error;}
        });
    }
}

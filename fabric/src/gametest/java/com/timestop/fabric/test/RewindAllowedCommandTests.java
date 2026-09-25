package com.timestop.fabric.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.timestop.command.TimeStopCommand;
import com.timestop.core.TimeMode;
import com.timestop.core.TimeStopManager;
import com.timestop.core.TimeStopSavedData;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.ModItems;
import com.timestop.item.WatchTier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class RewindAllowedCommandTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind_allowed_default")
    public void defaultStateIsOn(GameTestHelper h) {
        h.assertTrue(TimeStopSavedData.load(new CompoundTag()).isRewindModeAllowed(),
                "Default state of rewindModeAllowed in new worlds must be TRUE (ON)");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind_allowed_command")
    public void commandTogglingAndPermissions(GameTestHelper h) throws Exception {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        TimeStopCommand.register(dispatcher);
        var source = h.getLevel().getServer().createCommandSourceStack();
        var data = TimeStopSavedData.get();
        boolean original = data.isRewindModeAllowed();

        try {
            // 1. Verify non-admin is rejected
            boolean denied = false;
            try {
                dispatcher.execute("timestop rewind include false", source.withPermission(0));
            } catch (CommandSyntaxException expected) {
                denied = true;
            }
            h.assertTrue(denied, "Non-admins must not be allowed to change rewind inclusion");

            // 2. Admin disables rewind mode via /timestop rewind include false
            dispatcher.execute("timestop rewind include false", source.withPermission(2));
            h.assertTrue(!data.isRewindModeAllowed(), "Command must disable rewind mode");
            h.assertTrue(!TimeStopManager.isRewindAllowed(), "TimeStopManager must report rewind is disabled");

            // 3. Persistence check
            CompoundTag saved = data.save(new CompoundTag());
            h.assertTrue(!TimeStopSavedData.load(saved).isRewindModeAllowed(), "Disabled state must survive save/load");

            // 4. Admin enables rewind mode via /timestop allowrewind true
            dispatcher.execute("timestop allowrewind true", source.withPermission(2));
            h.assertTrue(data.isRewindModeAllowed(), "Command /timestop allowrewind true must re-enable rewind mode");
            h.assertTrue(TimeStopManager.isRewindAllowed(), "TimeStopManager must report rewind is allowed");

            // 5. Test shorthand keywords on/off
            dispatcher.execute("timestop rewind off", source.withPermission(2));
            h.assertTrue(!TimeStopManager.isRewindAllowed(), "Command /timestop rewind off must disable rewind mode");

            dispatcher.execute("timestop rewind on", source.withPermission(2));
            h.assertTrue(TimeStopManager.isRewindAllowed(), "Command /timestop rewind on must re-enable rewind mode");

            // 6. Test enable/disable keywords
            dispatcher.execute("timestop rewind disable", source.withPermission(2));
            h.assertTrue(!TimeStopManager.isRewindAllowed(), "Command /timestop rewind disable must disable rewind mode");

            dispatcher.execute("timestop rewind enable", source.withPermission(2));
            h.assertTrue(TimeStopManager.isRewindAllowed(), "Command /timestop rewind enable must re-enable rewind mode");
        } finally {
            TimeStopManager.setRewindAllowed(original);
        }

        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rewind_allowed_watch_filtering")
    public void watchTiersAndItemsFilterRewindMode(GameTestHelper h) {
        boolean original = TimeStopManager.isRewindAllowed();

        try {
            // When allowed:
            TimeStopManager.setRewindAllowed(true);
            h.assertTrue(WatchTier.DIAMOND.isModeUnlocked(TimeMode.REWIND), "Diamond watch must unlock REWIND when allowed");
            h.assertTrue(WatchTier.DIAMOND.getUnlockedModes().contains(TimeMode.REWIND), "Diamond getUnlockedModes must contain REWIND");
            h.assertTrue(WatchTier.NETHERITE.isModeUnlocked(TimeMode.REWIND), "Netherite watch must unlock REWIND when allowed");
            h.assertTrue(WatchTier.CREATIVE.isModeUnlocked(TimeMode.REWIND), "Creative watch must unlock REWIND when allowed");

            ItemStack diamondWatch = new ItemStack(ModItems.DIAMOND_WATCH.get());
            AbstractWatchItem.setMode(diamondWatch, TimeMode.REWIND);
            h.assertTrue(AbstractWatchItem.getMode(diamondWatch) == TimeMode.REWIND,
                    "Diamond watch set to REWIND must return REWIND when allowed");

            // When disallowed:
            TimeStopManager.setRewindAllowed(false);
            h.assertTrue(!WatchTier.DIAMOND.isModeUnlocked(TimeMode.REWIND), "Diamond watch must lock REWIND when disallowed");
            h.assertTrue(!WatchTier.DIAMOND.getUnlockedModes().contains(TimeMode.REWIND), "Diamond getUnlockedModes must not contain REWIND");
            h.assertTrue(!WatchTier.NETHERITE.isModeUnlocked(TimeMode.REWIND), "Netherite watch must lock REWIND when disallowed");
            h.assertTrue(!WatchTier.CREATIVE.isModeUnlocked(TimeMode.REWIND), "Creative watch must lock REWIND when disallowed");

            // Existing watch item set to REWIND must automatically fall back
            h.assertTrue(AbstractWatchItem.getMode(diamondWatch) != TimeMode.REWIND,
                    "Diamond watch must not return REWIND when disallowed");
            h.assertTrue(AbstractWatchItem.getMode(diamondWatch) == TimeMode.SLOW_MOTION,
                    "Diamond watch must fallback to SLOW_MOTION when REWIND is disallowed");

            // Other modes must remain unaffected
            h.assertTrue(WatchTier.DIAMOND.isModeUnlocked(TimeMode.TIME_STOP), "Diamond watch must still unlock TIME_STOP");
            h.assertTrue(WatchTier.DIAMOND.isModeUnlocked(TimeMode.MATRIX), "Diamond watch must still unlock MATRIX");

            // When re-allowed:
            TimeStopManager.setRewindAllowed(true);
            h.assertTrue(AbstractWatchItem.getMode(diamondWatch) == TimeMode.REWIND,
                    "Diamond watch must immediately restore REWIND mode once re-allowed");
        } finally {
            TimeStopManager.setRewindAllowed(original);
        }

        h.succeed();
    }
}

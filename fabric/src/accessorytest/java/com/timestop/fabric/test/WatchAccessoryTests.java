package com.timestop.fabric.test;

import com.timestop.combat.*;
import com.timestop.core.*;
import com.timestop.item.*;
import com.timestop.item.rune.*;
import com.timestop.network.ToggleTimeStopPacket;
import com.timestop.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import dev.emi.trinkets.api.TrinketsApi;

public class WatchAccessoryTests implements FabricGameTest {

    private static boolean present() { return Services.PLATFORM.isModLoaded("trinkets"); }
    private static void equip(ServerPlayer player, ItemStack stack) { TrinketsApi.getTrinketComponent(player).orElseThrow().getInventory().get("charm").get("watch").setItem(0, stack); }
    private static ServerPlayer player(GameTestHelper h) {
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "accessory-test"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "accessory_selection")
    public void selectionPassivesAndRunes(GameTestHelper h) {
        var player = player(h);
        try {
            var inventoryWatch = new ItemStack(ModItems.CREATIVE_WATCH.get());
            player.getInventory().setItem(10, inventoryWatch);
            h.assertTrue(AbstractWatchItem.findActivationWatch(player) == inventoryWatch, "Inventory fallback must work with and without the API");
            if (!present()) {
                h.assertTrue(Services.PLATFORM.getEquippedWatches(player).isEmpty(), "Absent API must return no accessories without loading its classes");
                h.succeed(); return;
            }
            player.getInventory().clearContent();
            var tag = TagKey.create(Registries.ITEM, new ResourceLocation("trinkets:charm/watch"));
            for (var item : java.util.List.of(ModItems.COPPER_WATCH.get(), ModItems.CHRONOS_WATCH.get(), ModItems.DIAMOND_WATCH.get(), ModItems.NETHERITE_WATCH.get(), ModItems.CREATIVE_WATCH.get())) {
                var stack = new ItemStack(item);
                h.assertTrue(stack.is(tag), "Every watch tier must be accepted by the accessory item tag: " + item);
                equip(player, stack);
                h.assertTrue(AbstractWatchItem.findActivationWatch(player) == stack, "Equipped watch must be returned as a live stack");
            }
            var worn = new ItemStack(ModItems.NETHERITE_WATCH.get());
            var rune = new ItemStack(ModItems.RUNE_DEFLECTION.get());
            TemporalRuneItem.setTargetFilter(rune, ChainTargetFilter.ALL);
            AbstractWatchItem.setSocketedRune(worn, rune);
            equip(player, worn);
            player.getInventory().setItem(10, inventoryWatch);
            h.assertTrue(AbstractWatchItem.findActivationWatch(player) == worn, "Equipped watch must take priority over a higher inventory tier");
            h.assertTrue(RuneManager.hasRune(player, RuneType.DEFLECTION), "Socketed rune must protect the accessory wearer");
            h.assertTrue(RuneManager.getActiveChainFilter(player) == ChainTargetFilter.ALL, "Worn rune target filter must be available");
            h.assertTrue(DecelerationFieldManager.getDecelerationRadius(player) >= WatchTier.NETHERITE.getDecelerationRadius(), "Worn watch must grant its tier passive");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.COPPER_WATCH.get()));
            h.assertTrue(AbstractWatchItem.findActivationWatch(player) == player.getOffhandItem(), "Offhand must retain activation priority");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIAMOND_WATCH.get()));
            h.assertTrue(AbstractWatchItem.findActivationWatch(player) == player.getMainHandItem(), "Main hand must retain activation priority");
            player.getInventory().clearContent();
            AbstractWatchItem.setSocketedRune(worn, new ItemStack(ModItems.RUNE_VECTOR.get()));
            boolean redirect = TimeStopSavedData.get().isRedirectToLook();
            TimeStopSavedData.get().setRedirectToLook(false);
            try { h.assertTrue(ProjectileRedirection.usesLook(player), "Worn Vector rune must control projectile redirection"); }
            finally { TimeStopSavedData.get().setRedirectToLook(redirect); }
            var creative = new ItemStack(ModItems.CREATIVE_WATCH.get());
            AbstractWatchItem.setMatrixRune(creative, RuneType.DEFLECTION, true);
            AbstractWatchItem.setMatrixRune(creative, RuneType.TACHYON, true);
            equip(player, creative);
            h.assertTrue(RuneManager.hasRune(player, RuneType.DEFLECTION) && RuneManager.hasRune(player, RuneType.TACHYON), "Creative matrix must work while worn");
            equip(player, ItemStack.EMPTY);
            h.assertTrue(!RuneManager.hasRune(player, RuneType.DEFLECTION) && DecelerationFieldManager.getDecelerationRadius(player) == 0, "Unequipping must remove rune and watch passives");
        } finally { player.discard(); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "accessory_activation")
    public void activationAndCooldown(GameTestHelper h) {
        var player = player(h);
        var saved = TimeStopSavedData.get();
        var previous = saved.getWatchScope();
        try {
            if (!present()) { h.succeed(); return; }
            var watch = new ItemStack(ModItems.DIAMOND_WATCH.get());
            AbstractWatchItem.setMode(watch, TimeMode.TIME_STOP);
            equip(player, watch);
            saved.setWatchScope(TimeStopSavedData.WatchScope.WATCH);
            player.getCooldowns().addCooldown(watch.getItem(), 100);
            new ToggleTimeStopPacket().handle(player);
            h.assertTrue(TemporalBubbleManager.getPlayerBubble(player.getUUID()) == null, "Worn watch must respect cooldown");
            player.getCooldowns().removeCooldown(watch.getItem());
            new ToggleTimeStopPacket().handle(player);
            var bubble = TemporalBubbleManager.getPlayerBubble(player.getUUID());
            h.assertTrue(bubble != null && bubble.getMode() == TimeMode.TIME_STOP && bubble.getRadius() == WatchTier.DIAMOND.getBubbleRadius(), "Activation must use worn watch mode and radius");
            new ToggleTimeStopPacket().handle(player);
            h.assertTrue(TemporalBubbleManager.getPlayerBubble(player.getUUID()) == null, "Second activation must stop the worn watch bubble");
        } finally {
            var bubble = TemporalBubbleManager.getPlayerBubble(player.getUUID());
            if (bubble != null) TemporalBubbleManager.stopBubble(h.getLevel(), bubble);
            saved.setWatchScope(previous); player.discard();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "accessory_rewind")
    public void rewindConsumesLiveAccessoryRune(GameTestHelper h) {
        var player = player(h);
        var saved = TimeStopSavedData.get();
        boolean automatic = saved.isAutoDeathRewind();
        try {
            if (!present()) { h.succeed(); return; }
            saved.setAutoDeathRewind(false);
            var watch = new ItemStack(ModItems.CHRONOS_WATCH.get());
            AbstractWatchItem.setSocketedRune(watch, new ItemStack(ModItems.RUNE_REWIND.get()));
            equip(player, watch);
            RewindRuneManager.identifyRunes(watch);
            var before = AbstractWatchItem.getSocketedRune(watch).copy();
            h.assertTrue(RewindRuneManager.findRewindRune(player).hostStack() == watch, "Death rewind must locate the live worn watch");
            h.assertTrue(RewindRuneManager.tryTriggerDeathRewind(player, player.damageSources().generic()), "Worn rewind rune must rescue the player");
            h.assertTrue(AbstractWatchItem.getSocketedRune(watch).isEmpty(), "Rescue must consume the live worn rune");
            AbstractWatchItem.setSocketedRune(watch, before);
            RewindRuneManager.applyRuneConsumption(player);
            h.assertTrue(AbstractWatchItem.getSocketedRune(watch).isEmpty(), "Restored accessory data must not resurrect a spent rewind rune");
        } finally { saved.setAutoDeathRewind(automatic); RewindRuneManager.clearPlayer(player.getUUID()); player.discard(); }
        h.succeed();
    }

    private static <T> T roundTrip(com.timestop.network.IServerboundPacket packet, java.util.function.Function<net.minecraft.network.FriendlyByteBuf, T> decode) {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try { packet.toBytes(buffer); return decode.apply(buffer); }
        finally { buffer.release(); }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "accessory_gui")
    public void equippedGuiPacketsEditOnlyTheirWatch(GameTestHelper h) {
        var player = player(h);
        boolean exemptAllowed = TimeStopManager.isRewindExemptAllowed();
        try {
            // The key remains useful without an API, including watches outside the hotbar.
            var fallback = new ItemStack(ModItems.DIAMOND_WATCH.get());
            player.getInventory().setItem(12, fallback);
            var inventoryTarget = WatchTarget.forInterface(player);
            roundTrip(new com.timestop.network.SelectTimeModePacket(TimeMode.REWIND, inventoryTarget), com.timestop.network.SelectTimeModePacket::new).handle(player);
            h.assertTrue(AbstractWatchItem.getMode(fallback) == TimeMode.REWIND, "Inventory watch GUI must work without an accessory API");
            if (!present()) { h.succeed(); return; }
            player.getInventory().clearContent();
            var held = new ItemStack(ModItems.CREATIVE_WATCH.get());
            AbstractWatchItem.setMode(held, TimeMode.SLOW_MOTION);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            var worn = new ItemStack(ModItems.NETHERITE_WATCH.get());
            com.timestop.core.rewind.WatchPreferences.snapshot(worn);
            equip(player, worn);
            var target = WatchTarget.forInterface(player);
            h.assertTrue(target.resolve(player) == worn, "Interface key must prefer the worn watch even when another is held");
            roundTrip(new com.timestop.network.SelectTimeModePacket(TimeMode.TIME_STOP, target), com.timestop.network.SelectTimeModePacket::new).handle(player);
            h.assertTrue(AbstractWatchItem.getMode(worn) == TimeMode.TIME_STOP && AbstractWatchItem.getMode(held) == TimeMode.SLOW_MOTION, "Mode selection must edit only the worn watch");
            roundTrip(new com.timestop.network.SetWatchScopePacket(target, true), com.timestop.network.SetWatchScopePacket::new).handle(player);
            h.assertTrue(!AbstractWatchItem.isGlobalScope(worn), "Accessory targeting must retain global-scope permission checks");
            player.getInventory().setItem(10, new ItemStack(ModItems.RUNE_RICOCHET.get(), 2));
            roundTrip(new com.timestop.network.SocketSpecificRunePacket(target, 10, RuneType.RICOCHET), com.timestop.network.SocketSpecificRunePacket::new).handle(player);
            h.assertTrue(AbstractWatchItem.getSocketedRuneType(worn) == RuneType.RICOCHET && player.getInventory().getItem(10).getCount() == 1, "Socketing must move exactly one rune into the live accessory");
            var filter = TemporalRuneItem.getTargetFilter(AbstractWatchItem.getSocketedRune(worn));
            roundTrip(new com.timestop.network.CycleRuneModePacket(target), com.timestop.network.CycleRuneModePacket::new).handle(player);
            h.assertTrue(TemporalRuneItem.getTargetFilter(AbstractWatchItem.getSocketedRune(worn)) == filter.next(), "Ricochet filter must edit the selected accessory");
            roundTrip(new com.timestop.network.SocketSpecificRunePacket(target, -1, RuneType.BLANK), com.timestop.network.SocketSpecificRunePacket::new).handle(player);
            int runes = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i).is(ModItems.RUNE_RICOCHET.get())) runes += player.getInventory().getItem(i).getCount();
            h.assertTrue(runes == 2 && AbstractWatchItem.getSocketedRune(worn).isEmpty(), "Ejecting must return the rune once");
            var replacement = new ItemStack(ModItems.NETHERITE_WATCH.get());
            com.timestop.core.rewind.WatchPreferences.snapshot(replacement);
            equip(player, replacement);
            roundTrip(new com.timestop.network.SocketSpecificRunePacket(target, 10, RuneType.RICOCHET), com.timestop.network.SocketSpecificRunePacket::new).handle(player);
            h.assertTrue(target.resolve(player).isEmpty() && AbstractWatchItem.getSocketedRune(replacement).isEmpty(), "A stale GUI must not edit a replacement watch in the same slot");
            var creative = new ItemStack(ModItems.CREATIVE_WATCH.get());
            equip(player, creative);
            target = WatchTarget.forInterface(player);
            roundTrip(new com.timestop.network.SetWatchRadiusPacket(target, 137), com.timestop.network.SetWatchRadiusPacket::new).handle(player);
            h.assertTrue(AbstractWatchItem.getBubbleRadius(creative) == 137 && AbstractWatchItem.getBubbleRadius(held) != 137, "Creative radius must edit the accessory and leave the held watch alone");
            roundTrip(new com.timestop.network.ToggleMatrixRunePacket(target, null, false, true), com.timestop.network.ToggleMatrixRunePacket::new).handle(player);
            roundTrip(new com.timestop.network.ToggleMatrixRunePacket(target, RuneType.DEFLECTION, true, false), com.timestop.network.ToggleMatrixRunePacket::new).handle(player);
            h.assertTrue(AbstractWatchItem.isMatrixRuneActive(creative, RuneType.DEFLECTION) && !AbstractWatchItem.isMatrixRuneActive(creative, RuneType.TACHYON), "Creative matrix controls must work in the accessory slot");
            TimeStopManager.setRewindExemptAllowed(true);
            roundTrip(new com.timestop.network.SetRewindExemptPacket(target, true), com.timestop.network.SetRewindExemptPacket::new).handle(player);
            h.assertTrue(AbstractWatchItem.isRewindInitiatorExempt(creative), "Creative rewind exemption must work while worn");
            var invalid = new WatchTarget("inventory:999999", null);
            roundTrip(new com.timestop.network.SocketSpecificRunePacket(invalid, 10, RuneType.RICOCHET), com.timestop.network.SocketSpecificRunePacket::new).handle(player);
            h.assertTrue(invalid.resolve(player).isEmpty(), "Invalid watch locations must be rejected");
        } finally { TimeStopManager.setRewindExemptAllowed(exemptAllowed); player.discard(); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "accessory_inventory_rewind")
    public void rewindKeepsEquippedWatchWithoutInventoryDuplicate(GameTestHelper h) {
        var player = player(h);
        try {
            if (!present()) { h.succeed(); return; }
            var watch = new ItemStack(ModItems.DIAMOND_WATCH.get());
            AbstractWatchItem.setMode(watch, TimeMode.SLOW_MOTION);
            AbstractWatchItem.setSocketedRune(watch, new ItemStack(ModItems.RUNE_VECTOR.get()));
            player.getInventory().setItem(0, watch);
            player.getInventory().setItem(10, new ItemStack(net.minecraft.world.item.Items.APPLE, 3));
            var historical = com.timestop.core.rewind.data.PlayerDelta.fromPlayer(player);
            player.getInventory().setItem(0, ItemStack.EMPTY);
            equip(player, watch);
            var slot = WatchTarget.forInterface(player);
            AbstractWatchItem.setMode(watch, TimeMode.REWIND);
            player.getInventory().setItem(10, new ItemStack(net.minecraft.world.item.Items.APPLE, 1));
            historical.restoreTo(player, true);
            h.assertTrue(slot.resolve(player) == watch && Services.PLATFORM.getEquippedWatches(player).size() == 1, "Rewind must keep the same live watch in its original accessory slot");
            h.assertTrue(AbstractWatchItem.getMode(watch) == TimeMode.REWIND && AbstractWatchItem.getSocketedRuneType(watch) == RuneType.VECTOR, "Rewind must retain the equipped watch's settings and socket");
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) h.assertTrue(!(player.getInventory().getItem(i).getItem() instanceof AbstractWatchItem), "Rewinding to before equip must not recreate an inventory watch");
            h.assertTrue(player.getInventory().getItem(10).getCount() == 3, "Other inventory items must still rewind normally");
            equip(player, ItemStack.EMPTY);
            player.containerMenu.setCarried(watch);
            var carriedHistory = com.timestop.core.rewind.data.PlayerDelta.fromPlayer(player);
            player.containerMenu.setCarried(ItemStack.EMPTY);
            equip(player, watch);
            carriedHistory.restoreTo(player, true);
            h.assertTrue(player.containerMenu.getCarried().isEmpty() && slot.resolve(player) == watch, "A historical cursor stack must not duplicate the equipped watch");
            historical.restoreTo(player, false);
            h.assertTrue(slot.resolve(player) == watch, "Inventory rollback disabled must also leave the accessory equipped");
            var wornHistory = com.timestop.core.rewind.data.PlayerDelta.fromPlayer(player);
            equip(player, ItemStack.EMPTY);
            player.getInventory().setItem(5, watch);
            wornHistory.restoreTo(player, true);
            h.assertTrue(player.getInventory().getItem(5).isEmpty(), "Inventory rollback must retain its normal behavior after unequipping");
        } finally { RewindRuneManager.clearPlayer(player.getUUID()); player.discard(); }
        h.succeed();
    }
}

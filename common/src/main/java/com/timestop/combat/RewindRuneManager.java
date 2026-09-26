package com.timestop.combat;

import com.timestop.config.TimeStopConfig;
import com.timestop.core.TimeStopManager;
import com.timestop.core.rewind.RewindExecutor;
import com.timestop.core.rewind.TickRecorder;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import com.timestop.network.ModMessages;
import com.timestop.network.RewindFadePacket;
import com.timestop.network.RewindMobPacket;
import com.timestop.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RewindRuneManager {

    public record RewindRuneLocation(boolean inWatch, ItemStack hostStack, int slotIndex) {}
    private record PendingRewind(long targetTick, boolean continuous, int burstSeconds, com.timestop.core.rewind.RewindScope scope) {}
    private static final String RUNE_ID = "TimeStopRewindRuneId";
    private static final Set<UUID> CONSUMED_RUNES = new java.util.HashSet<>();
    private static final Map<UUID, PendingRewind> PENDING = new java.util.HashMap<>();
    private static final Set<UUID> CONTINUOUS_PLAYERS = new java.util.HashSet<>();
    private static final Map<UUID, Long> INVULNERABLE_PLAYERS = new java.util.HashMap<>();

    public static boolean isPlayerInvulnerable(@Nullable Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        UUID id = player.getUUID();
        return PENDING.containsKey(id) || CONTINUOUS_PLAYERS.contains(id) || com.timestop.core.rewind.LocalRewind.isActive(id)
                || INVULNERABLE_PLAYERS.getOrDefault(id, -1L) >= serverPlayer.server.getTickCount();
    }

    @Nullable
    public static RewindRuneLocation findRewindRune(@Nullable Player player) {
        if (player == null) return null;

        // 1. Off-hand check
        ItemStack offhand = player.getOffhandItem();
        if (isWatchWithRewindRune(offhand)) {
            return new RewindRuneLocation(true, offhand, -1);
        }

        // 2. Main-hand check
        ItemStack mainhand = player.getMainHandItem();
        if (isWatchWithRewindRune(mainhand)) {
            return new RewindRuneLocation(true, mainhand, -1);
        }

        // 3. Entire inventory check
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isWatchWithRewindRune(stack)) {
                return new RewindRuneLocation(true, stack, i);
            }
        }

        // 4. Cursor carried item check
        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (isWatchWithRewindRune(carried)) {
                return new RewindRuneLocation(true, carried, -2);
            }
        }

        return null;
    }

    public static boolean hasRewindRune(@Nullable Player player) {
        return findRewindRune(player) != null;
    }

    private static boolean isWatchWithRewindRune(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof AbstractWatchItem
                && AbstractWatchItem.getSocketedRuneType(stack) == RuneType.REWIND
                && !wasConsumed(AbstractWatchItem.getSocketedRune(stack));
    }

    /** Called only after damage mitigation, or from a loader's cancellable death event. */
    public static boolean tryTriggerDeathRewind(ServerPlayer player, DamageSource damageSource) {
        if (player == null) return false;
        boolean automatic = com.timestop.core.TimeStopSavedData.get().isAutoDeathRewind();
        if (!automatic && (player.isCreative() || player.isSpectator())) return false;
        if (isPlayerInvulnerable(player)) {
            rescue(player);
            return true;
        }
        RewindRuneLocation loc = findRewindRune(player);
        if (!automatic && loc == null) return false;

        ItemStack scopeWatch = automatic ? AbstractWatchItem.findActivationWatch(player) : loc.hostStack();
        if (!automatic) {
            // Identity belongs to the rune, not its slot/watch; older snapshots can contain it loose.
            identifyRunes(loc.hostStack());
            ItemStack rune = AbstractWatchItem.getSocketedRune(loc.hostStack());
            UUID runeId = getRuneId(rune);
            if (runeId != null) {
                CONSUMED_RUNES.add(runeId);
            }
            AbstractWatchItem.setSocketedRune(loc.hostStack(), ItemStack.EMPTY);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastFullState();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastFullState();
        }

        boolean continuous = "CONTINUOUS".equalsIgnoreCase(TimeStopConfig.COMMON.rewindMode.get());
        int delay = continuous ? 1 : 10;
        PENDING.put(player.getUUID(), new PendingRewind(player.server.getTickCount() + delay,
                continuous, TimeStopConfig.rewindDurationSeconds(), com.timestop.core.rewind.RewindScope.forWatch(player, scopeWatch)));
        rescue(player);
        ServerLevel level = player.serverLevel();
        if (!automatic) level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 24, 0.4, 0.6, 0.4, 0.1);
        // Sound is played once by the server when restoration completes.
        if (!continuous) ModMessages.sendToPlayer(new RewindFadePacket(false), player);
        player.displayClientMessage(Component.literal(automatic ? "[Auto Rewind] Reversing fatal damage..." : "[Rune of Rewind] Reversing fatal damage...")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        // Never freeze recording here: explosion block destruction happens AFTER entity damage.
        return true;
    }

    public static void serverTick(MinecraftServer server) {
        INVULNERABLE_PLAYERS.entrySet().removeIf(e -> e.getValue() < server.getTickCount());
        if (!TickRecorder.getInstance().getTimelineBuffer().isRewinding() && !CONTINUOUS_PLAYERS.isEmpty()) {
            onRewindFinished(server);
        }
        // Cancel disconnected requests without changing global recording state.
        PENDING.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        if (PENDING.isEmpty()) return;
        if (TickRecorder.getInstance().getTimelineBuffer().isRewinding()) {
            // A manual/other-player rewind already covers these victims; do not queue a second rollback.
            CONTINUOUS_PLAYERS.addAll(PENDING.keySet());
            PENDING.clear();
            return;
        }
        var due = PENDING.entrySet().stream().filter(e -> server.getTickCount() >= e.getValue().targetTick()).toList();
        if (due.isEmpty()) return;

        for (var entry : due) {
            var request = entry.getValue();
            if (request.scope() == null) continue;
            PENDING.remove(entry.getKey());
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            INVULNERABLE_PLAYERS.put(player.getUUID(), (long) server.getTickCount() + 60);
            if (request.continuous() && com.timestop.core.rewind.LocalRewind.start(player, request.scope(), request.burstSeconds() * 20, () -> finishRescue(player))) continue;
            try {
                RewindExecutor.execute(server, request.burstSeconds() + 1, player, TimeStopConfig.COMMON.rollbackPlayerInventory.get(), request.scope());
            } finally { finishRescue(player); }
        }
        if (due.stream().noneMatch(e -> e.getValue().scope() == null)) return;
        // One world rollback services all waiting victims, including a shared explosion.
        var requests = new java.util.HashMap<>(PENDING);
        requests.entrySet().removeIf(e -> e.getValue().scope() != null);
        requests.keySet().forEach(PENDING::remove);
        var players = requests.keySet().stream().map(server.getPlayerList()::getPlayer).filter(java.util.Objects::nonNull).toList();
        if (players.isEmpty()) return;
        for (ServerPlayer player : players) INVULNERABLE_PLAYERS.put(player.getUUID(), (long) server.getTickCount() + 60);
        var leader = players.get(0);
        var recorder = TickRecorder.getInstance();
        if (TimeStopManager.isGlobalTimeStopActive()) TimeStopManager.resumeTime(leader.serverLevel());
        com.timestop.core.TemporalBubbleManager.stopAllBubbles(leader.serverLevel());
        recorder.finishFrame(server);
        boolean continuous = requests.values().stream().anyMatch(PendingRewind::continuous);
        if (continuous && recorder.getTimelineBuffer().getFrameCount() > 0) {
            // Include the completed lethal tick, even with no older completed frames.
            TimeStopManager.startContinuousRewind(leader.serverLevel(), leader,
                    Math.min(recorder.getTimelineBuffer().getFrameCount(), requests.values().stream().mapToInt(PendingRewind::burstSeconds).max().orElse(TimeStopConfig.rewindDurationSeconds()) * 20), null);
            for (ServerPlayer player : players) CONTINUOUS_PLAYERS.add(player.getUUID());
        } else {
            try {
                int seconds = requests.values().stream().mapToInt(PendingRewind::burstSeconds).max().orElse(TimeStopConfig.rewindDurationSeconds());
                // Include the fade interval as well as the configured historical duration.
                var result = RewindExecutor.execute(server, seconds + 1, leader, TimeStopConfig.COMMON.rollbackPlayerInventory.get(), null);
                if (!result.success()) for (ServerPlayer player : players)
                    player.displayClientMessage(Component.literal("Death rewind saved you, but no usable rewind history was available.")
                            .withStyle(ChatFormatting.YELLOW), true);
            } finally {
                for (ServerPlayer player : players) finishRescue(player);
            }
        }
    }

    private static void rescue(ServerPlayer player) {
        player.setHealth(Math.min(player.getMaxHealth(), Math.max(player.getHealth(), Math.max(6, player.getMaxHealth() * 0.5F))));
        player.deathTime = 0;
        player.invulnerableTime = 60;
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        player.resetFallDistance();
        player.removeEffect(MobEffects.POISON);
        player.removeEffect(MobEffects.WITHER);
        player.setDeltaMovement(Vec3.ZERO);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static void finishRescue(ServerPlayer player) {
        rescue(player);
        applyRuneConsumption(player);
        INVULNERABLE_PLAYERS.put(player.getUUID(), (long) player.server.getTickCount() + 60);
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 0));
        player.inventoryMenu.broadcastFullState();
        var level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RAMIEL_SCREAM.get(), SoundSource.PLAYERS, 2.5F, 1);
        ModMessages.sendToClients(new RewindMobPacket(level.dimension().location(), player.getUUID()));
    }

    public static void onRewindFinished(MinecraftServer server) {
        var completed = Set.copyOf(CONTINUOUS_PLAYERS);
        CONTINUOUS_PLAYERS.clear();
        for (UUID id : completed) {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) finishRescue(player);
        }
    }

    /** Assign before recording so the exact consumed rune is recognized in historical stacks. */
    public static void identifyRunes(ItemStack stack) {
        if (stack.getItem() instanceof AbstractWatchItem) {
            ItemStack rune = AbstractWatchItem.getSocketedRune(stack);
            if (isRewindRune(rune)) {
                CustomData customData = rune.get(DataComponents.CUSTOM_DATA);
                if (customData == null || !customData.copyTag().hasUUID(RUNE_ID)) {
                    CustomData.update(DataComponents.CUSTOM_DATA, rune, tag -> tag.putUUID(RUNE_ID, UUID.randomUUID()));
                    AbstractWatchItem.setSocketedRune(stack, rune);
                }
            }
        } else if (isRewindRune(stack)) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null || !customData.copyTag().hasUUID(RUNE_ID)) {
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(RUNE_ID, UUID.randomUUID()));
            }
        }
    }

    public static void identifyInventory(Object holder) {
        // Reading an unopened loot container rolls its table. Observation must leave it deferred.
        if (holder instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container
                && container.getLootTable() != null) return;
        if (holder instanceof net.minecraft.world.Container inventory) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) identifyRunes(inventory.getItem(slot));
        }
    }

    private static boolean isRewindRune(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof TemporalRuneItem rune && rune.getType() == RuneType.REWIND;
    }

    @Nullable
    private static UUID getRuneId(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().hasUUID(RUNE_ID)) {
            return customData.copyTag().getUUID(RUNE_ID);
        }
        return null;
    }

    private static boolean wasConsumed(ItemStack stack) {
        if (!isRewindRune(stack)) return false;
        UUID id = getRuneId(stack);
        return id != null && CONSUMED_RUNES.contains(id);
    }

    public static void removeConsumedRune(ItemStack stack) {
        if (stack.getItem() instanceof AbstractWatchItem) {
            if (wasConsumed(AbstractWatchItem.getSocketedRune(stack))) AbstractWatchItem.setSocketedRune(stack, ItemStack.EMPTY);
        } else if (wasConsumed(stack)) stack.setCount(0);
    }

    /** Strip spent rune identities from restored containers, equipment and dropped item NBT too. */
    public static CompoundTag restorationNbt(CompoundTag original) {
        var copy = original.copy();
        if (!CONSUMED_RUNES.isEmpty() && stripConsumed(copy)) return new CompoundTag();
        return copy;
    }

    private static boolean stripConsumed(Tag value) {
        if (value instanceof CompoundTag compound) {
            var legacy = compound.getCompound("tag");
            var data = compound.getCompound("components").getCompound("minecraft:custom_data");
            if (compound.contains("id", Tag.TAG_STRING)
                    && ((legacy.hasUUID(RUNE_ID) && CONSUMED_RUNES.contains(legacy.getUUID(RUNE_ID)))
                    || (data.hasUUID(RUNE_ID) && CONSUMED_RUNES.contains(data.getUUID(RUNE_ID))))) {
                return true;
            }
            if (compound.contains("SocketedRuneData", Tag.TAG_COMPOUND)) {
                var rune = compound.getCompound("SocketedRuneData");
                if (rune.hasUUID(RUNE_ID) && CONSUMED_RUNES.contains(rune.getUUID(RUNE_ID))) {
                    compound.remove("SocketedRuneData");
                    compound.remove("SocketedRuneType");
                    compound.remove("SocketedRuneFilter");
                }
            }
            for (String key : java.util.Set.copyOf(compound.getAllKeys())) {
                if (stripConsumed(compound.get(key))) compound.remove(key);
            }
        } else if (value instanceof ListTag list) {
            // Delete the entry: 1.21's optional count codec defaults invalid zero counts to one.
            for (int i = list.size() - 1; i >= 0; i--) {
                if (stripConsumed(list.get(i))) list.remove(i);
            }
        }
        return false;
    }

    public static void applyRuneConsumption(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) removeConsumedRune(player.getInventory().getItem(i));
        removeConsumedRune(player.containerMenu.getCarried());
        player.getInventory().setChanged();
    }

    /** Session reset only: completion/logout must not resurrect runes in remaining history. */
    public static void clearAllConsumptions() {
        CONSUMED_RUNES.clear();
        PENDING.clear();
        CONTINUOUS_PLAYERS.clear();
        INVULNERABLE_PLAYERS.clear();
    }

    public static void clearPlayer(UUID uuid) {
        com.timestop.core.rewind.LocalRewind.cancel(uuid);
        PENDING.remove(uuid);
        CONTINUOUS_PLAYERS.remove(uuid);
        INVULNERABLE_PLAYERS.remove(uuid);
    }
}

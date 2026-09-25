package com.timestop.pedestal;

import com.timestop.core.*;
import com.timestop.core.rewind.LocalRewind;
import com.timestop.core.rewind.TickRecorder;
import com.timestop.sync.SyncManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Loaded sources only; invoked from the server's real tick, never a time-dilated BE ticker. */
public final class PedestalManager {
    private static final Set<PedestalBlockEntity> loaded = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<PedestalBlockEntity, UUID> fields = new IdentityHashMap<>();
    public static void track(PedestalBlockEntity pedestal) { loaded.add(pedestal); }
    public static void untrack(PedestalBlockEntity pedestal) { loaded.remove(pedestal); stop(pedestal); }
    public static void reset() { loaded.clear(); fields.clear(); }
    public static void stop(PedestalBlockEntity pedestal) {
        UUID id = fields.remove(pedestal);
        if (id != null && pedestal.getLevel() instanceof ServerLevel level) {
            TemporalBubble bubble = TemporalBubbleManager.getBubble(id);
            if (bubble != null) TemporalBubbleManager.stopBubble(level, bubble);
            if (level.hasChunkAt(pedestal.getBlockPos())) {
                level.playSound(null, pedestal.getBlockPos().getX() + 0.5, pedestal.getBlockPos().getY() + 0.5, pedestal.getBlockPos().getZ() + 0.5,
                        com.timestop.sound.ModSounds.PEDESTAL_DEACTIVATE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
        if (pedestal.getLevel() instanceof ServerLevel level && level.hasChunkAt(pedestal.getBlockPos())) pedestal.setActive(false);
    }
    public static void disarmAll() { for (PedestalBlockEntity pedestal : new ArrayList<>(loaded)) { pedestal.setDisarmed(true); stop(pedestal); } }
    /** Extension point for future fuel/energy requirements. */
    public static boolean hasOperatingPower(PedestalBlockEntity pedestal) { return pedestal.isPowered() && !pedestal.isDisarmed(); }
    public static void serverTick() {
        for (PedestalBlockEntity pedestal : new ArrayList<>(loaded)) {
            if (!(pedestal.getLevel() instanceof ServerLevel level)) continue;
            if (pedestal.isRemoved() || !level.hasChunkAt(pedestal.getBlockPos()) || level.getBlockEntity(pedestal.getBlockPos()) != pedestal) { untrack(pedestal); continue; }
            if (!pedestal.isPowered()) pedestal.setDisarmed(false);
            if (!hasOperatingPower(pedestal) || !pedestal.accepts(pedestal.getWatch()) || pedestal.getOwner() == null
                    || TimeStopManager.isGlobalTimeStopActive() || LocalRewind.contains(level.dimension(), pedestal.getBlockPos())
                    || TickRecorder.getInstance().getTimelineBuffer().isRewinding()) { stop(pedestal); continue; }
            Set<UUID> exempt = new HashSet<>(SyncManager.getResonators(pedestal.getOwner()));
            var ownerTeam = level.getScoreboard().getPlayersTeam(pedestal.getOwnerName());
            for (var player : level.getServer().getPlayerList().getPlayers()) {
                if (ownerTeam != null && player.getTeam() != null && ownerTeam.isAlliedTo(player.getTeam())) exempt.add(player.getUUID());
            }
            boolean wasActive = fields.containsKey(pedestal);
            TemporalBubble old = wasActive ? TemporalBubbleManager.getBubble(fields.get(pedestal)) : null;
            boolean affectPlayers = TimeStopSavedData.get().pedestalsAffectPlayers();
            if (old == null || !old.getId().equals(pedestal.getFieldId()) || old.getMode() != pedestal.getMode()
                    || old.getRadius() != pedestal.getRadius() || old.getTier() != pedestal.watchTier()
                    || !old.getOwnerUuid().equals(pedestal.getOwner()) || !old.getExemptPlayers().equals(exempt) || old.affectsPlayers() != affectPlayers) {
                UUID previous = fields.get(pedestal);
                TemporalBubble bubble = new TemporalBubble(pedestal.getFieldId(), pedestal.getOwner(), level.dimension(),
                        Vec3.atCenterOf(pedestal.getBlockPos()), pedestal.getRadius(), pedestal.getMode(), 0,
                        pedestal.watchTier(), null, 0, exempt).stationary(affectPlayers);
                TemporalBubbleManager.putStationaryBubble(level, bubble, previous);
                fields.put(pedestal, bubble.getId());
                if (!wasActive) {
                    level.playSound(null, pedestal.getBlockPos().getX() + 0.5, pedestal.getBlockPos().getY() + 0.5, pedestal.getBlockPos().getZ() + 0.5,
                            com.timestop.sound.ModSounds.PEDESTAL_ACTIVATE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.2F, 1.0F);
                }
            }
            pedestal.setActive(true);
            if (level.getGameTime() % 80 == (Math.abs(pedestal.getBlockPos().hashCode()) % 80)) {
                level.playSound(null, pedestal.getBlockPos().getX() + 0.5, pedestal.getBlockPos().getY() + 0.5, pedestal.getBlockPos().getZ() + 0.5,
                        com.timestop.sound.ModSounds.PEDESTAL_AMBIENT.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.75F, 1.0F);
            }
        }
    }
    private PedestalManager() {}
}

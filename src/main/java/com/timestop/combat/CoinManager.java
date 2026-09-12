package com.timestop.combat;

import com.timestop.TimeStopMod;
import com.timestop.item.rune.RuneType;
import com.timestop.network.ModMessages;
import com.timestop.network.SyncCoinChargesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = TimeStopMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CoinManager {

    public static final int MAX_CHARGES = 4;
    private static final Map<UUID, Integer> playerCharges = new ConcurrentHashMap<>();

    // Client-side cache for HUD rendering
    public static int clientCharges = MAX_CHARGES;

    public static int getCharges(Player player) {
        if (player == null || !RuneManager.hasRune(player, RuneType.RICOSHOT)) return 0;
        if (player.level().isClientSide) {
            return clientCharges;
        }
        return playerCharges.computeIfAbsent(player.getUUID(), id -> MAX_CHARGES);
    }

    public static boolean hasCharge(Player player) {
        if (!RuneManager.hasRune(player, RuneType.RICOSHOT)) return false;
        if (player.isCreative()) return true;
        return getCharges(player) > 0;
    }

    public static boolean consumeCharge(ServerPlayer player) {
        if (!RuneManager.hasRune(player, RuneType.RICOSHOT)) return false;
        if (player.isCreative()) return true;
        int current = getCharges(player);
        if (current > 0) {
            int next = current - 1;
            playerCharges.put(player.getUUID(), next);
            syncToPlayer(player, next);
            return true;
        }
        return false;
    }

    public static void addCharge(ServerPlayer player, int amount) {
        int current = getCharges(player);
        int next = Math.min(MAX_CHARGES, current + amount);
        if (next != current) {
            playerCharges.put(player.getUUID(), next);
            syncToPlayer(player, next);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.8F);
        }
    }

    public static void syncToPlayer(ServerPlayer player, int charges) {
        ModMessages.sendToPlayer(new SyncCoinChargesPacket(charges), player);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() == null || event.getEntity().level().isClientSide) return;

        // When a player with the Marksman rune kills a hostile/living mob, award +1 coin charge!
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            if (RuneManager.hasRune(player, RuneType.RICOSHOT)) {
                addCharge(player, 1);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToPlayer(player, playerCharges.computeIfAbsent(player.getUUID(), id -> MAX_CHARGES));
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            playerCharges.put(player.getUUID(), MAX_CHARGES);
            syncToPlayer(player, MAX_CHARGES);
        }
    }
}

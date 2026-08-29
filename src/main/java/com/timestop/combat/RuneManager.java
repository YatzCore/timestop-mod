package com.timestop.combat;

import com.timestop.core.TimeStopManager;
import com.timestop.item.AbstractWatchItem;
import com.timestop.item.rune.RuneType;
import com.timestop.item.rune.TemporalRuneItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.timestop.TimeStopMod;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles automated tactical defenses provided by socketed Temporal Runes:
 * - DEFLECTION (Auto-Parry)
 * - SNATCHING (Auto-Collector)
 * - PHASING (Auto-Ender Dodge with absolute projectile intangibility)
 */
@Mod.EventBusSubscriber(modid = TimeStopMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RuneManager {

    private static final Map<UUID, Long> PHASE_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> INTANGIBILITY_UNTIL = new ConcurrentHashMap<>();

    /**
     * Intercepts any incoming projectile damage for players protected by the Phasing Rune.
     * Guarantees that volleys of arrows can never pierce the player's evasion.
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
                RuneType rune = getSocketedRuneType(player);
                if (rune == RuneType.PHASING) {
                    // Absolute projectile cancellation (like an Enderman!)
                    event.setCanceled(true);

                    Entity direct = event.getSource().getDirectEntity();
                    if (direct instanceof Projectile proj) {
                        proj.discard();
                    }

                    triggerPhasingEvasion(null, player);
                }
            }
        }
    }

    /**
     * Resolves the active socketed RuneType protecting this player.
     * Evaluates off-hand watch first, then main-hand watch.
     */
    @Nullable
    public static RuneType getSocketedRuneType(@Nullable Player player) {
        if (player == null || !player.isAlive()) return null;

        // 1. Off-hand Watch or Rune check
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof AbstractWatchItem) {
            RuneType rune = AbstractWatchItem.getSocketedRuneType(offhand);
            if (rune != null && rune != RuneType.BLANK) {
                return rune;
            }
        } else if (offhand.getItem() instanceof TemporalRuneItem runeItem) {
            return runeItem.getType();
        }

        // 2. Main-hand Watch or Rune check
        ItemStack mainhand = player.getMainHandItem();
        if (mainhand.getItem() instanceof AbstractWatchItem) {
            RuneType rune = AbstractWatchItem.getSocketedRuneType(mainhand);
            if (rune != null && rune != RuneType.BLANK) {
                return rune;
            }
        } else if (mainhand.getItem() instanceof TemporalRuneItem runeItem) {
            return runeItem.getType();
        }

        // 3. Check inventory for socketed watch or carried rune
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof AbstractWatchItem) {
                RuneType rune = AbstractWatchItem.getSocketedRuneType(stack);
                if (rune != null && rune != RuneType.BLANK) {
                    return rune;
                }
            } else if (stack.getItem() instanceof TemporalRuneItem runeItem) {
                return runeItem.getType();
            }
        }

        return null;
    }

    public static ItemStack getSocketedRuneStack(@Nullable Player player) {
        if (player == null || !player.isAlive()) return ItemStack.EMPTY;

        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof AbstractWatchItem) {
            ItemStack rune = AbstractWatchItem.getSocketedRune(offhand);
            if (!rune.isEmpty()) return rune;
        } else if (offhand.getItem() instanceof TemporalRuneItem) {
            return offhand;
        }

        ItemStack mainhand = player.getMainHandItem();
        if (mainhand.getItem() instanceof AbstractWatchItem) {
            ItemStack rune = AbstractWatchItem.getSocketedRune(mainhand);
            if (!rune.isEmpty()) return rune;
        } else if (mainhand.getItem() instanceof TemporalRuneItem) {
            return mainhand;
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof AbstractWatchItem) {
                ItemStack rune = AbstractWatchItem.getSocketedRune(stack);
                if (!rune.isEmpty()) return rune;
            }
        }

        return ItemStack.EMPTY;
    }

    public static ChainTargetFilter getActiveChainFilter(@Nullable Player player) {
        ItemStack runeStack = getSocketedRuneStack(player);
        if (!runeStack.isEmpty()) {
            return TemporalRuneItem.getTargetFilter(runeStack);
        }
        return ChainTargetFilter.HOSTILE;
    }

    /**
     * Evaluates and executes rune automated defense against an incoming projectile.
     */
    public static void evaluateRuneDefense(Projectile projectile, Player player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        if (!ProjectileCombatHelper.isActiveInFlight(projectile)) return;
        if (projectile.getOwner() == player) return;

        RuneType rune = getSocketedRuneType(player);
        if (rune == null || rune == RuneType.BLANK) return;

        long now = player.level().getGameTime();

        // If player is currently intangible after a phase shift, any projectile in range vanishes harmlessly!
        if (rune == RuneType.PHASING && now < INTANGIBILITY_UNTIL.getOrDefault(player.getUUID(), 0L)) {
            if (projectile.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL, projectile.getX(), projectile.getY(), projectile.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
            }
            projectile.discard();
            return;
        }

        // 3D distance to player torso / center of mass
        double targetY = player.getY() + player.getEyeHeight() * 0.5;
        double dx = projectile.getX() - player.getX();
        double dy = projectile.getY() - targetY;
        double dz = projectile.getZ() - player.getZ();
        double distSqr = dx * dx + dy * dy + dz * dz;

        switch (rune) {
            case DEFLECTION -> {
                // Auto-parry when within 3.5 blocks
                if (distSqr <= 12.25) { // 3.5^2
                    if (projectile.getPersistentData().getBoolean("AutoParried")) return;
                    projectile.getPersistentData().putBoolean("AutoParried", true);

                    TimeStopManager.deflectDynamicProjectile(projectile, player);
                    player.displayClientMessage(Component.literal("[Rune of Redirection] Parried!").withStyle(ChatFormatting.AQUA), true);
                }
            }
            case SNATCHING -> {
                // Auto-snatch when within 3.0 blocks
                if (distSqr <= 9.0) { // 3.0^2
                    TemporalInteractionEvents.snatchProjectile(projectile, player);
                    player.displayClientMessage(Component.literal("[Rune of Snatching] Captured!").withStyle(ChatFormatting.GOLD), true);
                }
            }
            case PHASING -> {
                // Auto-teleport when within 2.5 blocks (imminent strike)
                if (distSqr <= 6.25) { // 2.5^2
                    triggerPhasingEvasion(projectile, player);
                }
            }
        }
    }

    /**
     * Teleports the player 4-7 blocks safely away, dispels incoming projectiles, and grants intangibility.
     */
    public static void triggerPhasingEvasion(@Nullable Projectile projectile, Player player) {
        Level level = player.level();
        long now = level.getGameTime();
        long nextReady = PHASE_COOLDOWNS.getOrDefault(player.getUUID(), 0L);

        // Grant 25 ticks of intangibility against projectile volleys
        INTANGIBILITY_UNTIL.put(player.getUUID(), now + 25L);

        // Dispel all hostile in-flight projectiles within 6 blocks of origin
        List<Projectile> volley = level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(6.0),
                p -> p.isAlive() && p.getOwner() != player && !p.onGround());
        for (Projectile p : volley) {
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY(), p.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
            }
            p.discard();
        }

        if (now < nextReady) return; // Prevent teleport spam within 15 ticks
        PHASE_COOLDOWNS.put(player.getUUID(), now + 15L);

        Vec3 evadeDir;
        if (projectile != null && projectile.getDeltaMovement().lengthSqr() > 1e-4) {
            Vec3 pVel = projectile.getDeltaMovement();
            Vec3 horiz = new Vec3(-pVel.z, 0, pVel.x).normalize();
            evadeDir = (now % 2 == 0) ? horiz : horiz.reverse();
        } else {
            evadeDir = player.getLookAngle().reverse().multiply(1, 0, 1).normalize();
        }

        double distance = 4.5 + (level.random.nextDouble() * 2.0);
        Vec3 dest = player.position().add(evadeDir.scale(distance));

        BlockPos destPos = findSafeLandingPos(level, BlockPos.containing(dest.x, player.getY(), dest.z));
        if (destPos != null) {
            double origX = player.getX();
            double origY = player.getY();
            double origZ = player.getZ();

            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.teleportTo(destPos.getX() + 0.5, destPos.getY(), destPos.getZ() + 0.5);
            } else {
                player.setPos(destPos.getX() + 0.5, destPos.getY(), destPos.getZ() + 0.5);
            }

            // Audio & Visual Ender Juice at origin and destination
            level.playSound(null, origX, origY, origZ, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.2F, 1.2F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.2F, 1.2F);

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL, origX, origY + 1.0, origZ, 25, 0.4, 0.5, 0.4, 0.5);
                serverLevel.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.4, 0.5, 0.4, 0.5);
            }

            player.displayClientMessage(Component.literal("[Rune of Phasing] Evaded strike!").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    @Nullable
    private static BlockPos findSafeLandingPos(Level level, BlockPos origin) {
        for (int dy = 0; dy <= 2; dy++) {
            for (int sign : new int[]{1, -1}) {
                BlockPos check = origin.above(dy * sign);
                if (isSafeSpot(level, check)) {
                    return check;
                }
            }
        }
        return null;
    }

    private static boolean isSafeSpot(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());

        return below.isSolidRender(level, pos.below())
                && !below.isAir()
                && feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && !feet.liquid()
                && !head.liquid();
    }
}

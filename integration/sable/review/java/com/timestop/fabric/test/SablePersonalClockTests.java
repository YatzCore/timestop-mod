package com.timestop.fabric.test;

import com.timestop.compat.sable.SableBodyTimeManager;
import com.timestop.config.TimeStopConfig;
import com.timestop.core.*;
import com.timestop.item.ModItems;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

/** The 1.8.2/1.8.3 personal clock on and around Sable structures (stock Sable runtime). */
public class SablePersonalClockTests implements FabricGameTest {
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h, Vec3 p) {
        var player = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "sable-clock"), net.minecraft.server.level.ClientInformation.createDefault());
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}
        };
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); player.setPos(p); return player;
    }

    /** A 3x3 stone deck assembled into a Sable structure. */
    private static ServerSubLevel deck(GameTestHelper h, int x) {
        BlockPos p = new BlockPos(200 + x, 150, 260);
        h.getLevel().getChunkAt(p);
        Set<BlockPos> blocks = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos at = p.offset(dx, 0, dz);
            h.getLevel().setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
            blocks.add(at);
        }
        var bounds = new dev.ryanhcode.sable.companion.math.BoundingBox3i(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 1, p.getY(), p.getZ() + 1);
        return SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), p, blocks, bounds);
    }

    private static void stop(GameTestHelper h) {
        if (TimeStopManager.isGlobalTimeStopActive()) TimeStopManager.resumeTime(h.getLevel());
        TemporalBubbleManager.stopAllBubbles(h.getLevel());
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "sable_personal_clock", timeoutTicks = 150)
    public void personalClockFollowsThePlayerNotTheStructure(GameTestHelper h) {
        var body = deck(h, 0);
        var container = SubLevelContainer.getContainer(h.getLevel());
        var mass = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
        var owner = player(h, new Vec3(mass.x, mass.y + 1.0, mass.z));
        var other = player(h, new Vec3(mass.x + 1, mass.y + 1.0, mass.z));
        var boat = new net.minecraft.world.entity.vehicle.Boat(net.minecraft.world.entity.EntityType.BOAT, h.getLevel());
        boat.setPos(mass.x, mass.y + 1.0, mass.z);
        double old = TimeStopConfig.COMMON.fastForwardRate.get();
        try {
            TimeStopConfig.COMMON.fastForwardRate.set(5.0);
            TimeStopManager.startGlobalTimeStop(h.getLevel(), owner, 100, TimeMode.SELF_ACCELERATION);
            h.assertTrue(SelfAcceleration.rate(owner) == 5 && SelfAcceleration.rate(other) == 1, "Only the owner standing on the deck is accelerated");
            h.assertTrue(SableBodyTimeManager.getBodyTimeRate(h.getLevel(), body) == 1.0 && !SableBodyTimeManager.isBodyStasisActive(h.getLevel(), body),
                    "Global Self Acceleration leaves the structure at normal speed");
            stop(h);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.COPPER_WATCH.get()));
            TemporalBubbleManager.startBubble(h.getLevel(), owner, 100, TimeMode.SELF_ACCELERATION);
            h.assertTrue(SelfAcceleration.rate(owner) == 5 && SableBodyTimeManager.getBodyTimeRate(h.getLevel(), body) == 1.0,
                    "A Self Acceleration field around the deck accelerates the owner, not the structure");
            // The structure moves and turns under the player; the player (world coordinates) keeps the boost.
            var pose = new Vector3d(body.logicalPose().position()).add(3, 0, 0);
            container.physicsSystem().getPipeline().teleport(body, pose, new Quaterniond().rotateY(.5));
            container.physicsSystem().updatePose(body);
            h.assertTrue(SelfAcceleration.rate(owner) == 5 && PersonalTime.extraRate(owner) == 5, "The boost survives the structure moving and rotating");
            // Plot coordinates of the deck map back into the field: block-side checks agree with entity-side checks.
            var plotCenter = Vec3.atCenterOf(body.getPlot().getCenterBlock());
            var bubble = TemporalBubbleManager.getPlayerBubble(owner.getUUID());
            var movedCom = SableBodyTimeManager.getBodyCenterOfMassWorld(body);
            h.assertTrue(bubble.contains(h.getLevel().dimension(), plotCenter) == bubble.contains(h.getLevel().dimension(), new Vec3(movedCom.x, movedCom.y, movedCom.z)),
                    "Plot and world coordinates of the deck agree about the field");
            // Riding on the deck: passengers are not accelerated.
            h.getLevel().addFreshEntity(boat);
            h.assertTrue(owner.startRiding(boat, true), "Fixture player boards the boat");
            h.assertTrue(SelfAcceleration.rate(owner) == 1 && PersonalTime.extraRate(owner) == 1, "A rider on a structure is not accelerated");
            owner.setDeltaMovement(0, -.5, 0); owner.travel(Vec3.ZERO);
            h.assertTrue(owner.getDeltaMovement().y > -1.0, "A rider gets no accelerated fall: " + owner.getDeltaMovement().y);
            owner.stopRiding();
            stop(h);
            // Your own Fast Forward field: the structure runs fast, and the server runs your personal clock to match.
            owner.setPos(movedCom.x, movedCom.y + 1.0, movedCom.z);
            TemporalBubbleManager.startBubble(h.getLevel(), owner, 100, TimeMode.FAST_FORWARD);
            h.assertTrue(SableBodyTimeManager.getBodyTimeRate(h.getLevel(), body) == 5.0 && PersonalTime.extraRate(owner) == 5,
                    "Fast Forward field: structure 5x and personal clock 5x");
            stop(h);
            h.assertTrue(SableBodyTimeManager.getBodyTimeRate(h.getLevel(), body) == 1.0 && PersonalTime.extraRate(owner) == 1, "Everything returns to normal");
        } finally {
            stop(h); TimeStopConfig.COMMON.fastForwardRate.set(old);
            owner.discard(); other.discard(); boat.discard();
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        h.succeed();
    }
}

package com.timestop;

import com.timestop.combat.DeadEyeManager;
import com.timestop.combat.ProjectileCombatHelper;
import com.timestop.combat.VoltaicRicochetHandler;
import com.timestop.core.TimeStopManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;

import java.lang.ref.WeakReference;
import java.util.List;

@GameTestHolder(TimeStopMod.MOD_ID)

public class PortRegressionTests {
    @GameTest(template = "timestop:empty")
    public static void pigHeadOutsideBodyCanBePainted(GameTestHelper helper) {
        var pig = helper.spawnWithNoFreeWill(EntityType.PIG, 2, 2, 2);
        pig.yBodyRot = pig.yBodyRotO = pig.yHeadRot = pig.yHeadRotO = 0;
        pig.setXRot(0);
        Vec3 head = DeadEyeManager.headPosition(pig, 1.0F);
        helper.assertTrue(head.z > pig.getBoundingBox().maxZ, "Pig head must be in front of the body hitbox");
        helper.assertTrue(DeadEyeManager.isHeadAim(pig, head.add(-8, 0, 0), head.add(8, 0, 0)),
                "Side-on aim at protruding pig head was not recognized");
        Vec3 torso = pig.position().add(0, 0.45, 0);
        helper.assertTrue(!DeadEyeManager.isHeadAim(pig, torso.add(-8, 0, 0), torso.add(8, 0, 0)),
                "Pig torso must remain a body shot");
        pig.yBodyRot = pig.yBodyRotO = pig.yHeadRot = pig.yHeadRotO = 90;
        helper.assertTrue(DeadEyeManager.headPosition(pig, 1.0F).x < pig.getBoundingBox().minX,
                "Head anchor did not follow pig rotation");
        pig.setBaby(true);
        Vec3 babyHead = DeadEyeManager.headPosition(pig, 1.0F);
        helper.assertTrue(Math.abs(babyHead.y - pig.getY() - 0.501 * pig.getScale()) < 0.001,
                "Piglet head must use the lowered full-sized model head");
        helper.succeed();
    }

    @GameTest(template = "timestop:empty")
    public static void headshotCanReplaceBodyMarkAtAmmoLimit(GameTestHelper helper) {
        var marks = new java.util.ArrayList< com.timestop.combat.DeadEyeTag>();
        marks.add(new com.timestop.combat.DeadEyeTag(1, Vec3.ZERO, false));
        helper.assertTrue(DeadEyeManager.addOrUpgradeTag(marks,
                new com.timestop.combat.DeadEyeTag(1, new Vec3(0, 1, 0), true), 1),
                "Head aim was blocked by an existing body mark at the ammo limit");
        helper.assertTrue(marks.size() == 1 && marks.getFirst().isHead, "Upgrade must retain the ammunition limit");
        helper.succeed();
    }

    @GameTest(template = "timestop:empty")
    public static void headAimDistinguishesHeadFromTorso(GameTestHelper helper) {
        var mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 2);
        Vec3 head = mob.getEyePosition();
        helper.assertTrue(DeadEyeManager.isHeadAim(mob, head.add(0, 0, -8), head.add(0, 0, 2)),
                "An eye-level aim must paint a headshot");
        Vec3 chest = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
        helper.assertTrue(!DeadEyeManager.isHeadAim(mob, chest.add(0, 0, -8), chest.add(0, 0, 2)),
                "Torso aim must not paint a headshot");
        helper.assertTrue(DeadEyeManager.isHeadAim(mob, head.add(4, 3, -8), head),
                "Angled aim at the head must remain a headshot");
        helper.succeed();
    }

    @GameTest(template = "timestop:empty")
    public static void fastForwardAcceleratesPlayerAndRestoresNormalSpeed(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getCenter());
        float normalSpeed = player.getSpeed();
        float normalMining = player.getDestroySpeed(Blocks.STONE.defaultBlockState());
        var bubble = com.timestop.core.TemporalBubbleManager.startBubble(helper.getLevel(), player, 100,
                com.timestop.core.TimeMode.FAST_FORWARD);
        helper.assertTrue(bubble != null, "Fast-forward bubble was not created");
        try {
            helper.assertTrue(Math.abs(player.getSpeed() - normalSpeed * 5) < 0.0001,
                    "Bubble owner movement was not accelerated fivefold");
            helper.assertTrue(Math.abs(player.getDestroySpeed(Blocks.STONE.defaultBlockState()) - normalMining * 5) < 0.0001,
                    "Mining was not accelerated fivefold");
            player.getFoodData().setFoodLevel(10);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
            player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
            player.getCooldowns().addCooldown(net.minecraft.world.item.Items.ENDER_PEARL, 20);
            for (int i = 0; i < 7; i++) player.tick();
            helper.assertTrue(player.getFoodData().getFoodLevel() > 10, "Eating did not finish within seven ticks");
            helper.assertTrue(!player.getCooldowns().isOnCooldown(net.minecraft.world.item.Items.ENDER_PEARL),
                    "Item cooldowns did not advance with fast forward");
        } finally {
            com.timestop.core.TemporalBubbleManager.stopBubble(helper.getLevel(), bubble);
        }
        helper.assertTrue(Math.abs(player.getSpeed() - normalSpeed) < 0.0001, "Movement boost leaked after bubble ended");
        helper.assertTrue(Math.abs(player.getDestroySpeed(Blocks.STONE.defaultBlockState()) - normalMining) < 0.0001,
                "Mining boost leaked after bubble ended");
        helper.succeed();
    }

    private static Arrow embed(GameTestHelper helper) {
        helper.setBlock(2, 2, 2, Blocks.STONE);
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getCenter());
        arrow.setDeltaMovement(0, 0, 0.1);
        helper.getLevel().addFreshEntity(arrow);
        arrow.tick();
        helper.assertTrue(ProjectileCombatHelper.isStuckOrDead(arrow), "Arrow did not lodge in stone");
        helper.assertTrue(!arrow.onGround(), "Test must exercise inGround independently of onGround");
        return arrow;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void track(Class<?> handler, String fieldName, Arrow arrow) throws Exception {
        var field = handler.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((List) field.get(null)).add(new WeakReference<>(arrow));
    }

    @GameTest(template = "timestop:empty")
    public static void embeddedRicochetStopsGuidance(GameTestHelper helper) throws Exception {
        Arrow arrow = embed(helper);
        var target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 2);
        arrow.setNoGravity(true);
        arrow.getPersistentData().putInt("RicochetTargetId", target.getId());
        track(VoltaicRicochetHandler.class, "activeRicochetArrows", arrow);
        var tick = new TickEvent.ServerTickEvent.Post(() -> true, helper.getLevel().getServer());
        Vec3 position = arrow.position();
        for (int i = 0; i < 20; i++) {
            VoltaicRicochetHandler.onServerTick(tick);
            arrow.tick();
        }
        helper.assertTrue(!arrow.isNoGravity(), "Ricochet retained no-gravity after impact");
        helper.assertTrue(!arrow.getPersistentData().contains("RicochetTargetId"), "Ricochet retained guidance");
        helper.assertTrue(arrow.position().distanceToSqr(position) < 1e-8, "Embedded ricochet moved");
        arrow.discard();
        helper.succeed();
    }

    @GameTest(template = "timestop:empty")
    public static void embeddedDeadEyeStopsGuidance(GameTestHelper helper) throws Exception {
        Arrow arrow = embed(helper);
        var target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 2);
        arrow.setNoGravity(true);
        arrow.getPersistentData().putInt("DeadEyeTargetEntity", target.getId());
        track(DeadEyeManager.class, "activeHomingArrows", arrow);
        DeadEyeManager.onServerTick(new TickEvent.ServerTickEvent.Post(() -> true, helper.getLevel().getServer()));
        helper.assertTrue(!arrow.isNoGravity(), "Dead Eye retained no-gravity after impact");
        helper.assertTrue(!arrow.getPersistentData().contains("DeadEyeTargetEntity"), "Dead Eye retained guidance");
        arrow.discard();
        helper.succeed();
    }

    @GameTest(template = "timestop:empty")
    public static void staleSuspensionDoesNotRelaunchEmbeddedArrow(GameTestHelper helper) {
        Arrow arrow = embed(helper);
        TimeStopManager.redirectProjectile(arrow, new Vec3(0, 2, 0), null);
        TimeStopManager.resumeSingleProjectile(helper.getLevel(), arrow);
        helper.assertTrue(!TimeStopManager.isProjectileSuspended(arrow), "Stale suspension was not removed");
        helper.assertTrue(arrow.getDeltaMovement().lengthSqr() < 1e-8, "Embedded arrow relaunched");
        helper.assertTrue(!arrow.isNoGravity(), "Original gravity was not restored");
        arrow.discard();
        helper.succeed();
    }

    @GameTest(template = "timestop:empty")
    public static void watchRecipeLoadsFromSingularDirectory(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(
                ResourceLocation.fromNamespaceAndPath("timestop", "diamond_watch")).isPresent(),
                "Diamond watch crafting recipe failed to load in 1.21.1");
        helper.succeed();
    }
}

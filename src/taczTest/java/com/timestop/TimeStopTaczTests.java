package com.timestop;

import com.timestop.combat.TaczDeadEyeCompat;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TimeStopMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class TimeStopTaczTests {
    @GameTest(template = "empty")
    public static void ownRpgGrenadeAndGunRoundsCollectAndRelease(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "own-tacz-test"));
        player.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(1, 0, 1)).getCenter().add(0, 250, 0));
        player.setOldPosAndRot();
        player.setYRot(0);
        player.setXRot(0);
        level.addNewPlayer(player);
        try {
            for (String gunName : new String[] {"rpg7", "m320", "glock_17"}) {
                ResourceLocation id = new ResourceLocation("tacz", gunName);
                var gun = GunItemBuilder.create().setId(id).setAmmoCount(5).setAmmoInBarrel(true).build();
                player.setItemInHand(InteractionHand.MAIN_HAND, gun);
                IGunOperator.fromLivingEntity(player).initialData();
                IGunOperator.fromLivingEntity(player).draw(() -> gun);
                var gunData = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(id).orElseThrow().getGunData();
                for (boolean barrier : new boolean[] {false, true}) {
                    for (boolean frozen : new boolean[] {false, true}) {
                        var shot = new EntityKineticBullet(level, player, gun, gunData.getAmmoId(), id,
                                false, gunData, gunData.getBulletData());
                        shot.setDeltaMovement(0, 0, gunData.getBulletData().getSpeed() / 20.0);
                        try {
                            TimeStopRegressionTests.verifyOwnShotCapture(helper, player, shot, barrier, frozen);
                        } catch (Throwable failure) {
                            throw new RuntimeException(gunName + ", barrier=" + barrier + ", frozen=" + frozen + ": " + failure, failure);
                        }
                    }
                }
            }
        } finally {
            com.timestop.combat.KineticPalmManager.setGuarding(player, false);
            level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeFastRoundsReachDefensiveRunes(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "rune-test"));
        level.addNewPlayer(player);
        Vec3 center = helper.absolutePos(new net.minecraft.core.BlockPos(1, 0, 1)).getCenter().add(0, 220, 0);
        var runes = new net.minecraft.world.item.Item[] {
            com.timestop.item.ModItems.RUNE_DEFLECTION.get(), com.timestop.item.ModItems.RUNE_SNATCHING.get(),
            com.timestop.item.ModItems.RUNE_PHASING.get(), com.timestop.item.ModItems.RUNE_ORBITAL.get()
        };
        boolean oldLook = com.timestop.core.TimeStopSavedData.get().isRedirectToLook();
        try {
            com.timestop.core.TimeStopSavedData.get().setRedirectToLook(true);
            for (boolean explosive : new boolean[] {false, true}) {
                for (int index = 0; index < runes.length; index++) {
                    player.setPos(center);
                    player.setOldPosAndRot();
                    player.setYRot(90);
                    com.timestop.combat.RuneManager.clearPlayerCooldowns(player.getUUID());
                    var watch = new net.minecraft.world.item.ItemStack(com.timestop.item.ModItems.DIAMOND_WATCH.get());
                    com.timestop.item.AbstractWatchItem.setSocketedRune(watch, new net.minecraft.world.item.ItemStack(runes[index]));
                    player.setItemInHand(InteractionHand.OFF_HAND, watch);
                    var bullet = new EntityKineticBullet(EntityKineticBullet.TYPE, level);
                    bullet.setPos(center.add(0, player.getEyeHeight() * 0.5, -8));
                    bullet.setDeltaMovement(0, 0, 16);
                    var payload = EntityKineticBullet.class.getDeclaredField("explosion");
                    payload.setAccessible(true);
                    payload.setBoolean(bullet, explosive);
                    var ammo = EntityKineticBullet.class.getDeclaredField("ammoId");
                    ammo.setAccessible(true);
                    ammo.set(bullet, new ResourceLocation("tacz", explosive ? "rpg_rocket" : "9mm"));
                    for (String fieldName : new String[] {"gunId", "gunDisplayId"}) {
                        var field = EntityKineticBullet.class.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        field.set(bullet, new ResourceLocation("tacz", explosive ? "rpg7" : "glock_17"));
                    }
                    var origin = EntityKineticBullet.class.getDeclaredField("startPos");
                    origin.setAccessible(true);
                    origin.set(bullet, bullet.position());
                    level.addFreshEntity(bullet);
                    try {
                        helper.assertTrue(!com.timestop.combat.TaczProjectileCompat.ammunition(bullet).isEmpty(),
                                "Snatching must recover the native ammunition item");
                        bullet.tick();
                        if (index == 0) helper.assertTrue(bullet.getOwner() == player && bullet.getDeltaMovement().x < -15,
                                "Redirection must catch a native round crossing the entire field in one tick");
                        if (index == 1 || index == 2) helper.assertTrue(!bullet.isAlive(),
                                "Snatching and Phasing must intercept native bullets and RPGs before damage");
                        if (index == 3) {
                            helper.assertTrue(bullet.getPersistentData().getBoolean("InStasisOrbit") && payload.getBoolean(bullet) == explosive,
                                    "Orbital capture must preserve the native projectile and explosive payload");
                            com.timestop.combat.OrbitalProjectileManager.launchSingleProjectile(player);
                            helper.assertTrue(bullet.isAlive() && bullet.getDeltaMovement().length() >= 15.99,
                                    "Orbital native rounds must relaunch at their original speed");
                        }
                    } catch (Throwable failure) {
                        throw new RuntimeException("Defensive rune " + index + ", explosive=" + explosive + ": " + failure, failure);
                    } finally { bullet.discard(); }
                }
            }
            player.setPos(center);
            player.setOldPosAndRot();
            player.setItemInHand(InteractionHand.OFF_HAND, new net.minecraft.world.item.ItemStack(com.timestop.item.ModItems.DIAMOND_WATCH.get()));
            var slowed = new EntityKineticBullet(EntityKineticBullet.TYPE, level);
            slowed.setPos(center.add(2, player.getEyeHeight() * 0.5, -8));
            slowed.setDeltaMovement(0, 0, 16);
            try {
                Vec3 start = slowed.position();
                slowed.tick();
                helper.assertTrue(slowed.position().distanceTo(start) > 0 && slowed.position().distanceTo(start) < 8,
                        "A fast round must stop at the passive field boundary instead of tunneling across");
                Vec3 entry = slowed.position();
                slowed.tick();
                helper.assertTrue(Math.abs(slowed.position().distanceTo(entry) - 3.2) < 0.001
                                && slowed.getDeltaMovement().length() > 15.9,
                        "Field motion must be twenty percent while retaining the native full-speed velocity");
            } finally { slowed.discard(); }
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        finally {
            com.timestop.core.TimeStopSavedData.get().setRedirectToLook(oldLook);
            com.timestop.combat.RuneManager.clearPlayerCooldowns(player.getUUID());
            level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeBulletCoinSweepAndOrbitalRelease(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockSurvivalPlayer();
        Vec3 start = helper.absolutePos(new net.minecraft.core.BlockPos(1, 0, 1)).getCenter().add(0, 180, 0);
        player.setPos(start.add(30, 0, 0));
        var bullet = new EntityKineticBullet(EntityKineticBullet.TYPE, level);
        bullet.setPos(start);
        bullet.setOwner(player);
        bullet.setDeltaMovement(0, 0, 12);
        var coin = new com.timestop.entity.ChronoCoinEntity(level, player);
        coin.setPos(start.add(0, 0, 6));
        level.addFreshEntity(coin);
        try {
            bullet.tick();
            helper.assertTrue(!bullet.isAlive() && !coin.isAlive(), "A native bullet must hit a coin between tick endpoints");
            var captured = new EntityKineticBullet(EntityKineticBullet.TYPE, level);
            captured.setPos(start);
            captured.setDeltaMovement(0, 0, 12);
            com.timestop.combat.OrbitalProjectileManager.captureProjectile(player, captured, level);
            captured.tick();
            helper.assertTrue(captured.position().equals(start) && captured.tickCount == 0,
                    "Captured native bullets must not advance position or lifetime");
            com.timestop.combat.OrbitalProjectileManager.launchSingleProjectile(player);
            helper.assertTrue(captured.getDeltaMovement().length() >= 11.99,
                    "Native orbital release must preserve gun bullet speed");
            captured.discard();
        } finally { bullet.discard(); coin.discard(); player.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeImpactCancellationPreservesExplosiveRound(GameTestHelper helper) {
        var level = helper.getLevel();
        Vec3 start = helper.absolutePos(new net.minecraft.core.BlockPos(1, 0, 1)).getCenter().add(0, 180, 0);
        var target = new net.minecraft.world.entity.animal.Cow(net.minecraft.world.entity.EntityType.COW, level);
        target.setPos(start.add(0, 0, 4));
        target.setOldPosAndRot();
        target.setNoAi(true);
        target.setNoGravity(true);
        level.addFreshEntity(target);
        var bullet = new EntityKineticBullet(EntityKineticBullet.TYPE, level);
        bullet.setPos(start.add(0, 1, 0));
        bullet.setDeltaMovement(0, 0, 8);
        java.util.concurrent.atomic.AtomicBoolean intercepted = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<net.minecraftforge.event.entity.ProjectileImpactEvent> listener = event -> {
            if (event.getProjectile() == bullet && event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult) {
                intercepted.set(true);
                event.setCanceled(true);
                bullet.setDeltaMovement(-8, 0, 0);
            }
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            var explosive = EntityKineticBullet.class.getDeclaredField("explosion");
            explosive.setAccessible(true);
            explosive.setBoolean(bullet, true);
            Vec3 original = bullet.position();
            helper.assertTrue(com.tacz.guns.util.EntityUtil.findEntityOnPath(bullet, original, original.add(bullet.getDeltaMovement())) != null,
                    "Native test ray must intersect the target before checking the rune bridge");
            bullet.tick();
            helper.assertTrue(intercepted.get(), "TacZ entity hits must reach Forge rune impact hooks");
            helper.assertTrue(bullet.isAlive() && bullet.position().equals(original)
                    && bullet.getDeltaMovement().x == -8 && explosive.getBoolean(bullet),
                    "A canceled RPG hit must preserve the native round, payload and redirected velocity");
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener); bullet.discard(); target.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void deadEyeFiresNativeTaczBulletAndConsumesAmmo(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockSurvivalPlayer();
        player.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(1, 20, 1)).getCenter());
        player.setOldPosAndRot();
        var gun = GunItemBuilder.create().setId(new ResourceLocation("tacz", "glock_17"))
                .setAmmoCount(5).setAmmoInBarrel(true).build();
        helper.assertTrue(!gun.isEmpty(), "Installed TACZ default gun pack must load");
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        IGunOperator.fromLivingEntity(player).initialData();
        IGunOperator.fromLivingEntity(player).draw(() -> gun);
        helper.runAfterDelay(40, () -> {
            try {
                int before = IGun.getIGunOrNull(gun).getCurrentAmmoCount(gun);
                var result = TaczDeadEyeCompat.fire(player, player.getEyePosition().add(0, 0, 30));
                helper.assertTrue(result == TaczDeadEyeCompat.Result.FIRED, "TACZ must accept the Dead Eye shot: " + result);
                boolean nativeBullet = false;
                boolean arrow = false;
                for (var entity : level.getAllEntities()) {
                    if (entity instanceof EntityKineticBullet bullet && bullet.getOwner() == player) {
                        nativeBullet = true;
                        helper.assertTrue(bullet.getPersistentData().getBoolean("DeadEyeTaczPrecision"),
                                "Only the Dead Eye firing scope must mark its native bullet");
                        Vec3 aim = player.getEyePosition().add(0, 0, 30).subtract(bullet.position()).normalize();
                        helper.assertTrue(bullet.getDeltaMovement().normalize().distanceToSqr(aim) < 1e-10,
                                "Native random spread must be removed before the bullet enters the world");
                        verifyNativeRedirection(helper, player, bullet);
                    }
                    if (entity instanceof Arrow projectile && projectile.getOwner() == player) arrow = true;
                }
                helper.assertTrue(nativeBullet && !arrow, "Dead Eye must spawn a TACZ bullet and no vanilla arrow");
                helper.assertTrue(IGun.getIGunOrNull(gun).getCurrentAmmoCount(gun) < before,
                        "TACZ must consume the gun's own ammunition");
                // Establish a fresh cooldown after assertions; wall-clock time can advance during slow test runs.
                var timing = IGunOperator.fromLivingEntity(player).getDataHolder();
                timing.shootTimestamp = System.currentTimeMillis() - timing.baseTimestamp;
                helper.assertTrue(TaczDeadEyeCompat.fire(player, player.getEyePosition().add(0, 0, 30))
                        == TaczDeadEyeCompat.Result.RETRY, "Dead Eye must respect the native fire cooldown");
                IGunOperator.fromLivingEntity(player).getDataHolder().shootTimestamp -= 10000;
                helper.assertTrue(IGunOperator.fromLivingEntity(player).shoot(() -> 0f, () -> 0f)
                        == com.tacz.guns.api.entity.ShootResult.SUCCESS, "Ordinary native firing must still work");
                boolean ordinaryBullet = false;
                for (var entity : level.getAllEntities()) {
                    if (entity instanceof EntityKineticBullet bullet && bullet.getOwner() == player
                            && !bullet.getPersistentData().getBoolean("DeadEyeTaczPrecision")) ordinaryBullet = true;
                }
                helper.assertTrue(ordinaryBullet, "Precision must not leak into ordinary gunfire");
                IGun.getIGunOrNull(gun).setCurrentAmmoCount(gun, 0);
                IGun.getIGunOrNull(gun).setBulletInBarrel(gun, false);
                helper.runAfterDelay(20, () -> {
                    try {
                        // GameTest advances ticks faster than TACZ's wall-clock cooldown. Age only this test fixture.
                        IGunOperator.fromLivingEntity(player).getDataHolder().shootTimestamp -= 10000;
                        helper.assertTrue(TaczDeadEyeCompat.fire(player, player.getEyePosition().add(0, 0, 30))
                                == TaczDeadEyeCompat.Result.STOP, "Empty guns must not receive free ammunition or arrow substitutes");
                        helper.succeed();
                    } finally {
                        player.discard();
                    }
                });
            } catch (Throwable failure) {
                player.discard();
                throw failure;
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void deadEyeNativeBulletTracksAndHitsMovingTarget(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockSurvivalPlayer();
        Vec3 arena = helper.absolutePos(new net.minecraft.core.BlockPos(1, 0, 1)).getCenter();
        player.setPos(arena.x, 200, arena.z);
        player.setOldPosAndRot();
        var gun = GunItemBuilder.create().setId(new ResourceLocation("tacz", "glock_17"))
                .setAmmoCount(5).setAmmoInBarrel(true).build();
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        IGunOperator.fromLivingEntity(player).initialData();
        IGunOperator.fromLivingEntity(player).draw(() -> gun);
        helper.runAfterDelay(40, () -> {
            var target = new net.minecraft.world.entity.animal.Cow(net.minecraft.world.entity.EntityType.COW, level);
            target.setPos(player.position().add(0, 0, 20));
            target.setNoAi(true);
            target.setNoGravity(true);
            level.addFreshEntity(target);
            try {
                helper.assertTrue(TaczDeadEyeCompat.fire(player, target, false) == TaczDeadEyeCompat.Result.FIRED,
                        "Marked-target shot must be accepted");
                target.setPos(target.position().add(2, 0, 0));
                target.setOldPosAndRot();
                // Explicit native ticks make this independent of spawn-chunk ticking and test layout.
                EntityKineticBullet fired = null;
                for (var entity : level.getAllEntities()) {
                    if (entity instanceof EntityKineticBullet bullet && bullet.getOwner() == player) fired = bullet;
                }
                helper.assertTrue(fired != null, "Native marked bullet must exist");
                helper.assertTrue(level.clip(new net.minecraft.world.level.ClipContext(fired.position(), target.getEyePosition(),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, fired)).getType()
                        == net.minecraft.world.phys.HitResult.Type.MISS, "The test firing lane must be unobstructed");
                for (int tick = 0; tick < 8 && fired.isAlive(); tick++) level.tickNonPassenger(fired);
                helper.assertTrue(target.getHealth() < target.getMaxHealth(),
                        "Native collision/damage must hit the moved target; bullet=" + fired.position()
                                + ", target=" + target.position() + ", precision="
                                + fired.getPersistentData().getBoolean("DeadEyeTaczPrecision"));
                target.discard();
                player.discard();
                helper.succeed();
            } catch (Throwable failure) { target.discard(); player.discard(); throw failure; }
        });
    }

    private static void verifyNativeRedirection(GameTestHelper helper, net.minecraft.world.entity.player.Player player,
                                               EntityKineticBullet bullet) {
        var settings = com.timestop.core.TimeStopSavedData.get();
        boolean oldLook = settings.isRedirectToLook();
        try {
            settings.setRedirectToLook(true);
            double speed = bullet.getDeltaMovement().length();
            player.setYRot(90);
            com.timestop.core.TimeStopManager.punchSuspendedProjectile(bullet, player);
            com.timestop.core.TimeStopManager.resumeSingleProjectile(helper.getLevel(), bullet);
            helper.assertTrue(bullet.getDeltaMovement().normalize().distanceToSqr(player.getLookAngle().normalize()) < 1e-10
                    && bullet.getDeltaMovement().length() >= speed,
                    "A frozen native bullet must release along the chosen direction at native speed");
            var data = bullet.getPersistentData();
            data.putBoolean("KineticPalmCaptured", true);
            data.putUUID("KineticPalmOwner", player.getUUID());
            data.putDouble("NeoIncomingZ", speed);
            data.putBoolean("NeoOriginalNoGravity", true);
            bullet.setDeltaMovement(Vec3.ZERO);
            helper.assertTrue(com.timestop.combat.KineticPalmManager.releaseCaptured(bullet, player),
                    "The shield must release its native TACZ bullet");
            player.setYRot(-90);
            com.timestop.core.TimeStopManager.deflectDynamicProjectile(bullet, player);
            Vec3 redirected = bullet.getDeltaMovement();
            com.timestop.combat.TaczPrecision.guide(bullet);
            helper.assertTrue(redirected.normalize().distanceToSqr(player.getLookAngle().normalize()) < 1e-10
                    && bullet.getDeltaMovement().equals(redirected) && redirected.length() >= speed
                    && !data.getBoolean("DeadEyeTaczPrecision") && !data.getBoolean("KineticPalmCaptured"),
                    "Shield redirection must preserve native speed and prevent old Dead Eye guidance from taking over");
        } finally {
            settings.setRedirectToLook(oldLook);
            com.timestop.core.TimeStopManager.removeSuspendedProjectile(bullet);
        }
    }
}

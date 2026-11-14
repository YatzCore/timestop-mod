package com.timestop.forge.test;

import com.timestop.client.*;
import com.timestop.client.gui.TimeModeSelectionScreen;
import com.timestop.core.*;
import com.timestop.item.*;
import java.nio.file.Files;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/** Opt-in client scene, path limit assertions, and screenshots in an isolated development world. */
@Mod.EventBusSubscriber(modid="timestop", value=Dist.CLIENT)
public class PredictionForgeVisualQa {
    private static final PredictionForgeVisualQa INSTANCE = new PredictionForgeVisualQa();
    private boolean loading;
    private int boot,ticks;
    private boolean sawTeleport, sawHit;
    private int farArrow=-1, endermanId=-1, archerId=-1, hostileArrowId=-1;
    private boolean clickedHostile;
    private int caughtArrowId=-1;
    private boolean clickedCatch;
    private Vec3 normalWalkStart, fastWalkStart;
    private double normalWalk;
    @SubscribeEvent public static void onClientTick(TickEvent.ClientTickEvent event) {
        if(event.phase==TickEvent.Phase.END && Boolean.getBoolean("timestop.predictionVisualQa")) INSTANCE.tick(Minecraft.getInstance());
    }
    private void tick(Minecraft mc) {
        if(!loading && ++boot>80 && mc.level==null && mc.getOverlay()==null && !(mc.screen instanceof TitleScreen)) mc.setScreen(new TitleScreen());
        if(!loading && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
            loading=true; mc.options.guiScale().set(3); mc.options.renderDistance().set(4); mc.options.framerateLimit().set(60);
            mc.options.pauseOnLostFocus=false; mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            if(Files.exists(mc.gameDirectory.toPath().resolve("saves/PredictionQA/level.dat"))) mc.createWorldOpenFlows().loadLevel(mc.screen,"PredictionQA");
            else mc.createWorldOpenFlows().createFreshLevel("PredictionQA",
                    new LevelSettings("Prediction QA",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42,false,false),access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
        if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null || mc.getOverlay()!=null) return;
        ticks++;
        PredictionPerformanceQa.overlay(mc,ticks);
        if(ticks==145) mc.getSingleplayerServer().execute(() -> PredictionPerformanceQa.forecast(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())));
        if(ticks==20) mc.getSingleplayerServer().execute(() -> {
            var server=mc.getSingleplayerServer(); var player=server.getPlayerList().getPlayer(mc.player.getUUID()); var level=player.serverLevel();
            for(var e:level.getEntities(player,player.getBoundingBox().inflate(100))) if(e instanceof net.minecraft.world.entity.LivingEntity || e instanceof net.minecraft.world.entity.projectile.Projectile) e.discard();
            level.setDayTime(6000); level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            for(int x=-15;x<=15;x++) for(int z=-16;z<=24;z++) level.setBlock(new BlockPos(x,68,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
            for(int x=-3;x<=3;x++) for(int z=-15;z<=-9;z++) for(int y=69;y<=74;y++) level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
            player.setGameMode(GameType.CREATIVE);
            player.connection.teleport(.5,69,-12,0,8);
            var watch=new ItemStack(ModItems.NETHERITE_WATCH.get()); AbstractWatchItem.setMode(watch,TimeMode.TIME_PREDICTION);
            player.setItemInHand(InteractionHand.MAIN_HAND,watch); player.containerMenu.broadcastChanges();
        });
        if(ticks==50) mc.setScreen(new TimeModeSelectionScreen(InteractionHand.MAIN_HAND));
        if(ticks==70) capture(mc,"01-netherite-prediction-menu.png");
        if(ticks==75) {mc.setScreen(null);if(Boolean.getBoolean("timestop.variantsOnly")) {ticks=569;return;}}
        if(ticks==80) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            var watch=new ItemStack(ModItems.CREATIVE_WATCH.get()); AbstractWatchItem.setMode(watch,TimeMode.TIME_PREDICTION);
            AbstractWatchItem.setGlobalScope(watch,false); AbstractWatchItem.setBubbleRadius(watch,48);
            player.setItemInHand(InteractionHand.MAIN_HAND,watch);
            TemporalBubbleManager.startBubble(player.serverLevel(),player,0,TimeMode.TIME_PREDICTION);
            player.getInventory().setItem(9,watch); player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOW));
            player.getInventory().setItem(10,new ItemStack(Items.ARROW,64)); player.containerMenu.broadcastChanges();
            player.startUsingItem(InteractionHand.MAIN_HAND);
            for(int i=0;i<3;i++) {
                var cow=EntityType.COW.create(player.level()); cow.setPos(-4+i*4,69,-1+i*3); player.serverLevel().addFreshEntity(cow); cow.getNavigation().moveTo(10,69,12,1.2);
            }
        });
        if(ticks>85 && ticks<270 && ticks%40==0) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); var level=player.serverLevel();
            for(var cow:level.getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class,player.getBoundingBox().inflate(30))) cow.getNavigation().moveTo(10,69,12,1.2);
            var snowball=new net.minecraft.world.entity.projectile.Snowball(level,-5,70,-2); snowball.setDeltaMovement(.12,.35,.6); level.addFreshEntity(snowball);
            var potion=new net.minecraft.world.entity.projectile.ThrownPotion(level,-3,70,-2); potion.setItem(new ItemStack(Items.SPLASH_POTION)); potion.setDeltaMovement(.05,.3,.5); level.addFreshEntity(potion);
            var incoming=new Arrow(level,.5,70,-8); incoming.setNoGravity(true); incoming.setDeltaMovement(0,0,-.65); level.addFreshEntity(incoming);
            for(int i=0;i<2;i++) {
                var arrow=new Arrow(level,-3+i*2,70,-3); arrow.setDeltaMovement(.02,.42,.75); level.addFreshEntity(arrow);
            }
        });
        if(ticks==110) {
            mc.options.keyUse.setDown(true);
            mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
        }
        if(ticks==140) { assertPaths(mc); capture(mc,"02-prediction-combat.png"); }
        if(ticks==150) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            for(int i=0;i<35;i++) { var cow=EntityType.COW.create(player.level()); cow.setPos(-12+i%8*3,69,1+i/8*3);player.serverLevel().addFreshEntity(cow);cow.getNavigation().moveTo(10,69,12,1.2); }
        });
        if(ticks==175) { assertPaths(mc); capture(mc,"03-prediction-crowded.png"); }
        if(ticks==180) capture(mc,"03b-prediction-animation.png");
        if(ticks==185) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if(ticks==200) { assertPaths(mc); capture(mc,"04-prediction-third-person.png"); }
        if(ticks==205) { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.guiScale().set(2); mc.resizeDisplay(); }
        if(ticks==220) capture(mc,"05-prediction-gui-scale-two.png");
        if(ticks==230) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            TemporalBubbleManager.stopAllBubbles(player.serverLevel());
            TimeStopManager.startGlobalTimeStop(player.serverLevel(),player,0,TimeMode.TIME_PREDICTION);
        });
        if(ticks==255) { assertPaths(mc); if(ClientTimeStopManager.getClientTickMs()!=50 || TimeStopManager.getServerTickMs()!=50) throw new IllegalStateException("Prediction owner controls must remain at 20 TPS"); capture(mc,"06-prediction-global.png"); }
        if(ticks==270) mc.options.keyRight.setDown(true);
        if(ticks==282) {
            var a=PredictionClient.framePaths(.25F);var b=PredictionClient.framePaths(.75F);
            var aimA=a.stream().filter(PredictionClient.DisplayPath::aimed).findFirst().orElseThrow().path();
            var aimB=b.stream().filter(PredictionClient.DisplayPath::aimed).findFirst().orElseThrow().path();
            Vec3 expected=mc.player.getPosition(.75F).subtract(mc.player.getPosition(.25F));
            if(expected.lengthSqr()<.00001 || aimB.motion().position().subtract(aimA.motion().position()).distanceTo(expected)>.00001)
                throw new IllegalStateException("Moving aim must follow interpolated camera origin");
            for(var first:a) if(!first.aimed() && !first.path().motion().living() && first.path().impact()) {
                var second=b.stream().filter(p -> p.path().motion().entityId()==first.path().motion().entityId()).findFirst().orElseThrow();
                if(first.path().end().position().distanceTo(second.path().end().position())>.000001)
                    throw new IllegalStateException("Render interpolation must never shift an impact anchor");
            }
            capture(mc,"08-moving-aim-stable-impacts.png");com.timestop.TimeStopMod.LOGGER.info("PREDICTION_MOVING_AIM_PASS");
        }
        if(ticks==285) {mc.options.keyRight.setDown(false);mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
        if(ticks==300) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();
            for(var entity:level.getEntities(player,player.getBoundingBox().inflate(256))) if(entity instanceof net.minecraft.world.entity.LivingEntity || entity instanceof net.minecraft.world.entity.projectile.Projectile) entity.discard();
            player.connection.teleport(.5,69,-12,0,-1);
            level.getChunkAt(new BlockPos(0,70,90));level.getChunkAt(new BlockPos(0,70,105));
            for(int x=-1;x<=1;x++) for(int y=69;y<=73;y++) level.setBlock(new BlockPos(x,y,105),Blocks.STONE.defaultBlockState(),3);
            var arrow=new Arrow(level,.5,71,90);arrow.setNoGravity(true);arrow.setOwner(player);arrow.setDeltaMovement(0,0,.3);level.addFreshEntity(arrow);farArrow=arrow.getId();
        });
        if(ticks==318) {
            var path=PredictionClient.paths().stream().filter(p -> p.path().motion().entityId()==farArrow).findFirst().orElseThrow(() -> new IllegalStateException("Distant owned flight disappeared"));
            if(mc.level.getEntity(farArrow)!=null || !path.path().impact() || Math.abs(path.path().end().position().z-105)>.01)
                throw new IllegalStateException("Far arc must preserve authoritative landing without a tracked client entity");
            capture(mc,"09-distant-untracked-flight.png");com.timestop.TimeStopMod.LOGGER.info("PREDICTION_DISTANT_CLIENT_PASS");
        }
        if(ticks==330) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();
            var enderman=EntityType.ENDERMAN.create(level);enderman.setNoAi(true);enderman.setPos(4,69,2);level.addFreshEntity(enderman);endermanId=enderman.getId();
            if(!((com.timestop.mixin.EndermanTeleportAccessor)enderman).timestop$teleport(-3,74,7)) throw new IllegalStateException("Native Enderman reservation failed");
        });
        if(ticks>=331 && ticks<=345 && !sawTeleport && !PredictionClient.teleports().isEmpty()) {
            sawTeleport=true;capture(mc,"10-enderman-destination-tell.png");com.timestop.TimeStopMod.LOGGER.info("PREDICTION_TELEPORT_CLIENT_PASS");
        }
        if(ticks==347) mc.getSingleplayerServer().execute(() -> {
            var mob=mc.getSingleplayerServer().overworld().getEntity(endermanId);
            if(mob==null || mob.position().distanceTo(new Vec3(-3,69,7))>.01)
                throw new IllegalStateException("Global teleport tell must use twelve real server ticks and the exact reserved landing");
            com.timestop.TimeStopMod.LOGGER.info("PREDICTION_GLOBAL_TELEPORT_LANDING_PASS");
        });
        if(ticks==355 && !sawTeleport) throw new IllegalStateException("Enderman preview never arrived");
        if(ticks==360) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();
            var cow=EntityType.COW.create(level);cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(.5,69,4);level.addFreshEntity(cow);
            var arrow=new Arrow(level,.5,70.2,-3);arrow.setNoGravity(true);arrow.setOwner(player);arrow.setDeltaMovement(0,0,.2);level.addFreshEntity(arrow);
        });
        if(ticks>=365 && ticks<=385 && !sawHit && PredictionClient.paths().stream().anyMatch(p -> !p.path().motion().living() && p.path().hitEntity()>=0)) {
            sawHit=true;capture(mc,"11-arrow-mob-intercept.png");com.timestop.TimeStopMod.LOGGER.info("PREDICTION_INTERCEPT_CLIENT_PASS");
        }
        if(ticks==390 && !sawHit) throw new IllegalStateException("Arrow/mob intercept was never shown");
        if(ticks==395) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();
            var skeleton=EntityType.SKELETON.create(level);skeleton.setNoAi(true);skeleton.setPos(-3,69,-4);
            skeleton.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOW));skeleton.setTarget(player);
            level.addFreshEntity(skeleton);skeleton.startUsingItem(InteractionHand.MAIN_HAND);archerId=skeleton.getId();
        });
        if(ticks==405) {
            if(PredictionClient.paths().stream().noneMatch(p -> p.prepared() && p.path().motion().ownerId()==archerId))
                throw new IllegalStateException("Enemy's drawing bow must have a pre-release flight preview");
            if(PredictionSilhouettes.projectileCount()==0 || PredictionSilhouettes.projectileCount()>8)
                throw new IllegalStateException("Prepared arrows must have bounded translucent projectile geometry");
            capture(mc,"12-enemy-pre-release-arrow-silhouettes.png");com.timestop.TimeStopMod.LOGGER.info("PREDICTION_PRE_RELEASE_CLIENT_PASS ghosts={}",PredictionSilhouettes.projectileCount());
        }
        if(ticks==407) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();
            var arrow=new Arrow(level,.5,player.getEyeY()-.4,-10.2);arrow.setNoGravity(true);
            arrow.setOwner(level.getEntity(archerId));arrow.setDeltaMovement(0,0,-.2);level.addFreshEntity(arrow);hostileArrowId=arrow.getId();
        });
        if(ticks>=409 && ticks<=414 && !clickedHostile && mc.level.getEntity(hostileArrowId)!=null) {
            mc.hitResult=new net.minecraft.world.phys.EntityHitResult(mc.level.getEntity(hostileArrowId));
            try {
                var attack=Minecraft.class.getDeclaredMethod("startAttack");attack.setAccessible(true);attack.invoke(mc);
            } catch(ReflectiveOperationException e) {throw new IllegalStateException("Actual attack-key route failed",e);}
            clickedHostile=true;
        }
        if(ticks==415) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            var arrow=player.serverLevel().getEntity(hostileArrowId);
            if(!(arrow instanceof Arrow reflected) || reflected.getOwner()!=player || reflected.getPierceLevel()!=1
                    || reflected.getDeltaMovement().z<=0) throw new IllegalStateException("Hostile arrow attack must reflect once through the safe custom packet");
            var archer=(net.minecraft.world.entity.monster.AbstractSkeleton)player.serverLevel().getEntity(archerId);archer.stopUsingItem();
        });
        if(ticks==423) {
            if(!clickedHostile || mc.player==null || !mc.player.connection.getConnection().isConnected())
                throw new IllegalStateException("Reflecting a hostile arrow must not disconnect the player");
            if(!(mc.level.getEntity(hostileArrowId) instanceof Arrow reflected) || reflected.getOwner()!=mc.player
                    || com.timestop.prediction.PredictionTime.rate(reflected)!=1)
                throw new IllegalStateException("Reflected ownership and normal owner flight must synchronize to the client");
            if(PredictionClient.paths().stream().anyMatch(PredictionClient.DisplayPath::prepared))
                throw new IllegalStateException("An aborted enemy charge must clear the prepared flight");
            capture(mc,"13-hostile-arrow-redirected.png");com.timestop.TimeStopMod.LOGGER.info("PREDICTION_REDIRECT_CLIENT_PASS");
        }
        if(ticks==424) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            var arrow=new net.minecraft.world.entity.projectile.SpectralArrow(player.serverLevel(),.5,player.getEyeY()-.4,-10.2);
            arrow.setNoGravity(true);arrow.setDeltaMovement(0,0,-.001);player.serverLevel().addFreshEntity(arrow);caughtArrowId=arrow.getId();
            for(int i=0;i<player.getInventory().items.size();i++) if(player.getInventory().getItem(i).is(Items.SPECTRAL_ARROW)) player.getInventory().setItem(i,ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
        });
        if(ticks>=426 && ticks<=427 && !clickedCatch && mc.level.getEntity(caughtArrowId)!=null) {
            mc.hitResult=new net.minecraft.world.phys.EntityHitResult(mc.level.getEntity(caughtArrowId));
            try {var use=Minecraft.class.getDeclaredMethod("startUseItem");use.setAccessible(true);use.invoke(mc);}
            catch(ReflectiveOperationException e) {throw new IllegalStateException("Actual catch key route failed",e);}
            clickedCatch=true;
        }
        if(ticks==429) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());int count=0;
            for(var item:player.getInventory().items) if(item.is(Items.SPECTRAL_ARROW)) count+=item.getCount();
            if(!clickedCatch || player.serverLevel().getEntity(caughtArrowId)!=null || count!=1)
                throw new IllegalStateException("Prediction right-click must catch exactly one arrow through the actual client packet");
            com.timestop.TimeStopMod.LOGGER.info("PREDICTION_CATCH_CLIENT_PASS");
        });
        if(ticks==430) {
            mc.options.keyUse.setDown(false);
            mc.gameMode.releaseUsingItem(mc.player);
            mc.getSingleplayerServer().execute(() -> TimeStopManager.resumeTime(mc.getSingleplayerServer().overworld()));
        }
        if(ticks==440) {
            if(PredictionClient.active() || !PredictionClient.paths().isEmpty()) throw new IllegalStateException("Prediction must clear after deactivation");
            capture(mc,"07-prediction-deactivated.png"); com.timestop.TimeStopMod.LOGGER.info("PREDICTION_VISUAL_QA_PASS");
        }
        if(ticks==441) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();
            var arrow=new Arrow(level,.5,player.getEyeY()-.4,-10.2);arrow.setNoGravity(true);arrow.setDeltaMovement(0,0,-.001);
            level.addFreshEntity(arrow);hostileArrowId=arrow.getId();
        });
        if(ticks==445) {
            var arrow=mc.level.getEntity(hostileArrowId);if(arrow==null) throw new IllegalStateException("Deactivated arrow fixture was not tracked");
            mc.hitResult=new net.minecraft.world.phys.EntityHitResult(arrow);
            try {var attack=Minecraft.class.getDeclaredMethod("startAttack");attack.setAccessible(true);attack.invoke(mc);}
            catch(ReflectiveOperationException e) {throw new IllegalStateException("Deactivated arrow attack route failed",e);}
        }
        if(ticks==449) {
            if(mc.player==null || !mc.player.connection.getConnection().isConnected()) throw new IllegalStateException("Arrow attack after field expiry must not disconnect");
            com.timestop.TimeStopMod.LOGGER.info("PREDICTION_EXPIRED_ARROW_CLICK_PASS");
        }
        if(ticks==455) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();
            for(var e:level.getEntities(player,player.getBoundingBox().inflate(256))) if(e instanceof net.minecraft.world.entity.LivingEntity || e instanceof net.minecraft.world.entity.projectile.Projectile || e instanceof net.minecraft.world.entity.item.PrimedTnt) e.discard();
            level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,mc.getSingleplayerServer());
            player.connection.teleport(.5,69,-12,0,35);
            TimeStopManager.startGlobalTimeStop(level,player,0,TimeMode.TIME_PREDICTION);
            player.setItemInHand(InteractionHand.MAIN_HAND,net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION),net.minecraft.world.item.alchemy.Potions.POISON));
            var cow=EntityType.COW.create(level);cow.setNoAi(true);cow.setPos(.5,69,-8);level.addFreshEntity(cow);player.containerMenu.broadcastChanges();
        });
        if(ticks==480) {
            var paths=PredictionClient.framePaths(mc.getFrameTime());var outcomes=PredictionClient.frameOutcomes();
            if(paths.stream().noneMatch(p -> p.aimed() && p.path().motion().effects().kind()==com.timestop.prediction.PredictionEffects.Kind.SPLASH)
                    || outcomes.stream().noneMatch(o -> o.event().sourceId()==-1 && o.event().radius()==4 && !o.event().affected().isEmpty()))
                throw new IllegalStateException("Held potion must preview native splash area and future affected bodies");
            capture(mc,"14-potion-splash-holograms-day.png");com.timestop.TimeStopMod.LOGGER.info("PREDICTION_SPLASH_CLIENT_PASS");
        }
        if(ticks==485) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();level.setDayTime(18000);
            // The screenshot arrives before the periodic time sync in the slowed world. Send native time now.
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(level.getGameTime(),level.getDayTime(),false));
        });
        if(ticks==495) {
            if(Math.floorMod(mc.level.getDayTime(),24000)<17000 || Math.floorMod(mc.level.getDayTime(),24000)>19000)
                throw new IllegalStateException("Night visual fixture must actually reach the client before capture");
            capture(mc,"15-potion-splash-holograms-night.png");com.timestop.TimeStopMod.LOGGER.info("PREDICTION_NIGHT_CLIENT_PASS time={}",mc.level.getDayTime());
        }
        if(ticks==500) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND,net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION),net.minecraft.world.item.alchemy.Potions.HARMING));player.containerMenu.broadcastChanges();
        });
        if(ticks==515) {
            PredictionClient.framePaths(mc.getFrameTime());
            if(PredictionClient.frameOutcomes().stream().noneMatch(o -> o.event().kind()==com.timestop.prediction.PredictionEffects.Kind.LINGERING && o.event().radius()==3))
                throw new IllegalStateException("Held lingering potion must show its initial three-block footprint");
            capture(mc,"16-lingering-cloud-footprint.png");com.timestop.TimeStopMod.LOGGER.info("PREDICTION_LINGERING_CLIENT_PASS");
        }
        if(ticks==525) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();
            level.setDayTime(6000);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.containerMenu.broadcastChanges();
            player.connection.teleport(.5,69,-12,0,18);
            var tnt=new net.minecraft.world.entity.item.PrimedTnt(EntityType.TNT,level);tnt.setPos(2.5,69,-6);tnt.setDeltaMovement(.01,0,.01);tnt.setFuse(14);level.addFreshEntity(tnt);
            var creeper=EntityType.CREEPER.create(level);creeper.setNoAi(true);creeper.setPos(-2.5,69,-6);creeper.setSwellDir(1);level.addFreshEntity(creeper);
        });
        if(ticks==536) {
            PredictionClient.framePaths(mc.getFrameTime());var outcomes=PredictionClient.frameOutcomes();
            if(outcomes.size()!=2 || outcomes.stream().noneMatch(o -> o.event().kind()==com.timestop.prediction.PredictionEffects.Kind.TNT)
                    || outcomes.stream().noneMatch(o -> o.event().kind()==com.timestop.prediction.PredictionEffects.Kind.CREEPER && o.event().conditional()))
                throw new IllegalStateException("Two imminent hazards must produce bounded conditional/native-clock danger areas");
            capture(mc,"17-tnt-creeper-danger-areas.png");com.timestop.TimeStopMod.LOGGER.info("PREDICTION_HAZARD_CLIENT_PASS count={}",outcomes.size());
        }
        if(ticks==540) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            for(var e:player.serverLevel().getEntities(player,player.getBoundingBox().inflate(30))) {
                if(e instanceof net.minecraft.world.entity.item.PrimedTnt) e.discard();
                if(e instanceof net.minecraft.world.entity.monster.Creeper c) c.setSwellDir(-1);
            }
        });
        if(ticks==550 && !PredictionClient.frameOutcomes().isEmpty()) throw new IllegalStateException("Cancelled hazards must fade out without lingering areas");
        if(ticks==555) mc.getSingleplayerServer().execute(() -> TimeStopManager.resumeTime(mc.getSingleplayerServer().overworld()));
        if(ticks>=570) variantTick(mc);
        if(ticks==562) {
            if(!PredictionClient.frameOutcomes().isEmpty()) throw new IllegalStateException("Outcome cues must clear when prediction ends");
            com.timestop.TimeStopMod.LOGGER.info("PREDICTION_OUTCOME_CLEANUP_PASS");
        }
    }
    private void variantTick(Minecraft mc) {
        if(ticks==570) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();
            TemporalBubbleManager.stopAllBubbles(level);TimeStopManager.resumeTime(level);
            for(var e:level.getEntities(player,player.getBoundingBox().inflate(256))) if(e instanceof net.minecraft.world.entity.LivingEntity || e instanceof net.minecraft.world.entity.projectile.Projectile || e instanceof net.minecraft.world.entity.item.PrimedTnt) e.discard();
            player.connection.teleport(.5,69,-12,0,0);player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.COPPER_WATCH.get()));player.containerMenu.broadcastChanges();
        });
        if(ticks==580) mc.setScreen(new TimeModeSelectionScreen(InteractionHand.MAIN_HAND));
        if(ticks==585) {
            int mx=(mc.screen.width-250)/2,my=(mc.screen.height-140)/2;
            mc.screen.mouseClicked(mx+70,my+45,1);
        }
        if(ticks==590) mc.setScreen(new TimeModeSelectionScreen(InteractionHand.MAIN_HAND));
        if(ticks==595) {
            if(AbstractWatchItem.getMode(mc.player.getMainHandItem())!=TimeMode.COMBAT_SLOW) throw new IllegalStateException("Right-click mode card did not select combat variant");
            capture(mc,"18-combat-slow-variant.png");
            mc.setScreen(null);com.timestop.network.ModMessages.sendToServer(new com.timestop.network.ToggleTimeStopPacket());
        }
        if(ticks==605) {
            if(ClientTimeStopManager.getCurrentMode()!=TimeMode.COMBAT_SLOW || ClientTimeStopManager.getClientTickMs()!=50)
                throw new IllegalStateException("Combat mode must preserve the native player clock");
            normalWalkStart=mc.player.position();mc.player.setSprinting(false);mc.options.keyUp.setDown(true);
        }
        if(ticks==625) {
            mc.options.keyUp.setDown(false);normalWalk=mc.player.position().distanceTo(normalWalkStart);
            if(normalWalk<2 || normalWalk>6) throw new IllegalStateException("Combat player walking speed changed: "+normalWalk);
            com.timestop.network.ModMessages.sendToServer(new com.timestop.network.ToggleTimeStopPacket());
            com.timestop.TimeStopMod.LOGGER.info("COMBAT_NORMAL_PLAYER_CLIENT_PASS distance={}",normalWalk);
        }
        if(ticks==640) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());player.connection.teleport(.5,69,-12,0,0);
            player.setDeltaMovement(Vec3.ZERO);player.getCooldowns().removeCooldown(ModItems.COPPER_WATCH.get());
        });
        if(ticks==650) mc.setScreen(new TimeModeSelectionScreen(InteractionHand.MAIN_HAND));
        if(ticks==655) {
            // Click the "Self" label under the Fast Forward card (second Copper card).
            int mx=(mc.screen.width-250)/2,my=(mc.screen.height-140)/2;var font=mc.font;
            mc.screen.mouseClicked(mx+12+8+font.width("[2] ")+font.width("World")+9+3,my+32+40+6+11+4,0);
        }
        if(ticks==660) mc.setScreen(new TimeModeSelectionScreen(InteractionHand.MAIN_HAND));
        if(ticks==665) {
            if(AbstractWatchItem.getMode(mc.player.getMainHandItem())!=TimeMode.SELF_ACCELERATION) throw new IllegalStateException("Self label did not select self acceleration");
            capture(mc,"19-self-acceleration-variant.png");mc.setScreen(null);
            com.timestop.network.ModMessages.sendToServer(new com.timestop.network.ToggleTimeStopPacket());
        }
        if(ticks==675) {
            if(ClientTimeStopManager.getCurrentMode()!=TimeMode.SELF_ACCELERATION || ClientTimeStopManager.getClientTickMs()!=50 || SelfAcceleration.rate(mc.player)!=5)
                throw new IllegalStateException("Self acceleration must scale only movement input");
            fastWalkStart=mc.player.position();mc.player.setSprinting(false);mc.options.keyUp.setDown(true);
        }
        if(ticks==695) {
            mc.options.keyUp.setDown(false);double fast=mc.player.position().distanceTo(fastWalkStart);
            if(fast<normalWalk*4 || fast>normalWalk*6 || !mc.player.connection.getConnection().isConnected())
                throw new IllegalStateException("Actual self movement/network speed is wrong, normal="+normalWalk+" fast="+fast);
            com.timestop.TimeStopMod.LOGGER.info("SELF_ACCELERATION_CLIENT_PASS normalDistance={} fastDistance={}",normalWalk,fast);
            com.timestop.network.ModMessages.sendToServer(new com.timestop.network.ToggleTimeStopPacket());
        }
        if(ticks==710) {
            if(SelfAcceleration.rate(mc.player)!=1) throw new IllegalStateException("Self acceleration survived cancellation");
            mc.setScreen(new com.timestop.client.gui.TimeStopSettingsScreen());
        }
        if(ticks==715) {
            // The setting starts OFF in this isolated fixture, then waits for server confirmation.
            if(!com.timestop.config.TimeStopConfig.COMMON.enableWaterWalkingInStasis.get())
                com.timestop.network.ModMessages.sendToServer(new com.timestop.network.UpdateSwimmingConfigPacket(false));
        }
        if(ticks==725) {
            int mx=(mc.screen.width-320)/2,my=(mc.screen.height-304)/2; // settings modal height (304 since the Casual row was added)
            mc.screen.mouseClicked(mx+270,my+48+19*10+7,0);
        }
        if(ticks==735) {
            if(com.timestop.config.TimeStopConfig.COMMON.enableWaterWalkingInStasis.get()) throw new IllegalStateException("Swimming settings click did not reach server");
            capture(mc,"20-stopped-time-swimming-setting.png");mc.setScreen(null);
            mc.getSingleplayerServer().execute(() -> {
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();
                for(int x=-2;x<=2;x++) for(int z=-14;z<=-10;z++) for(int y=68;y<=72;y++) level.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),3);
                player.connection.teleport(.5,69,-12,0,0);player.setDeltaMovement(Vec3.ZERO);
                TimeStopManager.startGlobalTimeStop(level,player,100,TimeMode.TIME_STOP);
            });
        }
        if(ticks==745) {fastWalkStart=mc.player.position();mc.options.keyUp.setDown(true);}
        if(ticks==765) {
            mc.options.keyUp.setDown(false);
            if(!mc.player.isInWater() || mc.player.position().distanceTo(fastWalkStart)<.3 || !mc.player.connection.getConnection().isConnected())
                throw new IllegalStateException("Player must swim during actual Time Stop");
            capture(mc,"21-swimming-in-stopped-water.png");com.timestop.TimeStopMod.LOGGER.info("STASIS_SWIMMING_CLIENT_PASS");
            mc.getSingleplayerServer().execute(() -> TimeStopManager.resumeTime(mc.getSingleplayerServer().overworld()));
        }
        if(ticks==780) {com.timestop.TimeStopMod.LOGGER.info("TIME_VARIANTS_CLIENT_PASS");mc.stop();}
    }

    private void assertPaths(Minecraft mc) {
        if(!PredictionClient.active() || PredictionClient.paths().isEmpty()) throw new IllegalStateException("Prediction owner did not receive paths");
        if(PredictionClient.paths().size()>12) throw new IllegalStateException("Prediction overcrowded the world");
        if(PredictionClient.paths().stream().noneMatch(p -> p.path().motion().living())) throw new IllegalStateException("Moving subjects must receive movement forecasts");
        if(PredictionClient.paths().stream().noneMatch(PredictionClient.DisplayPath::aimed)) throw new IllegalStateException("Drawn weapon must receive aimed forecast");
        for(var shot:PredictionClient.paths()) if(!shot.path().motion().living()
                && shot.path().hitEntity()!=mc.player.getId()
                && mc.level.getEntity(shot.path().hitEntity()) instanceof net.minecraft.world.entity.LivingEntity target) {
            Vec3 contactBody=PredictionClient.targetPosition(target.getId(),shot.path().end().seconds(),shot.aimed());
            double radius=target.getBbWidth()*.5;
            var box=new net.minecraft.world.phys.AABB(contactBody.x-radius,contactBody.y,contactBody.z-radius,
                    contactBody.x+radius,contactBody.y+target.getBbHeight(),contactBody.z+radius).inflate(.36);
            if(!box.contains(shot.path().end().position())) throw new IllegalStateException("Intercept body and marker must share the shot's time origin");
        }
        if(PredictionSilhouettes.projectileCount()==0 || PredictionSilhouettes.projectileCount()>8) throw new IllegalStateException("Projectile previews must emit bounded silhouette geometry");
        if(PredictionSilhouettes.count()==0) throw new IllegalStateException("Future mob silhouettes must emit visible model geometry");
        if(PredictionClient.paths().stream().noneMatch(p -> p.path().motion().living() && !p.path().motion().route().isEmpty())) throw new IllegalStateException("Mob forecasts must include real navigation plans");
        if(PredictionClient.paths().stream().noneMatch(p -> !p.path().motion().living() && p.path().impact())) throw new IllegalStateException("Full arcs must expose predicted landing locations");
        com.timestop.TimeStopMod.LOGGER.info("PREDICTION_SILHOUETTES_PASS count={}",PredictionSilhouettes.count());
        com.timestop.TimeStopMod.LOGGER.info("PREDICTION_PATH_LIMIT_PASS count={} gui={}",PredictionClient.paths().size(),mc.options.guiScale().get());
    }
    private void capture(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message -> com.timestop.TimeStopMod.LOGGER.info("Prediction QA screenshot: {}",message.getString()));
    }
}

package com.timestop.test;

import com.timestop.TimeStopMod;
import com.timestop.combat.TaczDeadEyeCompat;
import com.timestop.core.*;
import com.timestop.item.WatchTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** Two real production clients, LAN socket, disconnect/reconnect, and native damage. QA only. */
public final class NativeTaczNetworkChecks {
    private static final String ROLE=System.getProperty("timestop.nativeTaczNetworkRole","");
    private static final Path SHARED=Path.of(System.getProperty("timestop.nativeTaczNetworkDir","."));
    private static int phase,boot,ticks,observations,serverPhase,serverTicks;
    private static boolean done;
    private static volatile Throwable failure;
    private static TemporalBubble bubble;
    private static Projectile bullet;
    private static Zombie target;
    private static Vec3 frozen;
    public static boolean active() {return !ROLE.isEmpty();}
    private static void signal(String name,String value) throws Exception {
        Files.createDirectories(SHARED);var temp=SHARED.resolve(name+".tmp-"+ROLE);
        Files.writeString(temp,value);Files.move(temp,SHARED.resolve(name),StandardCopyOption.REPLACE_EXISTING);
    }
    private static boolean exists(String name) {return Files.exists(SHARED.resolve(name));}
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
    public static void tick() {
        if(done)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if(++boot>3600)throw new IllegalStateException("Network QA timed out: "+ROLE+" phase="+phase+" server="+serverPhase);
            if(failure!=null)throw new IllegalStateException("LAN server QA failed",failure);
            if(exists("failed"))throw new IllegalStateException(Files.readString(SHARED.resolve("failed")));
            if(phase==0 && boot>80 && mc.getOverlay()==null && !(mc.screen instanceof TitleScreen)) mc.setScreen(new TitleScreen());
            if(phase==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(30);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                if(ROLE.equals("host")) {
                    phase=1;GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                    String world="NativeTaCZNetworkQA-"+System.currentTimeMillis();
                    mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings(world,GameType.SURVIVAL,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
                } else if(exists("ready")) {phase=1;connect(mc);}
            }
            if(ROLE.equals("host"))host(mc);else observer(mc);
        }catch(Throwable ex){done=true;TimeStopMod.LOGGER.error("NATIVE_TACZ_NETWORK_FAIL: "+ROLE,ex);try{signal("failed",ex.toString());}catch(Exception ignored){}mc.stop();}
    }
    private static void connect(Minecraft mc) {
        var address=ServerAddress.parseString("127.0.0.1:25589");
        ConnectScreen.startConnecting(new TitleScreen(),mc,address,new ServerData("Native TaCZ LAN QA","127.0.0.1:25589",ServerData.Type.OTHER),false,null);
    }
    private static void host(Minecraft mc) throws Exception {
        var server=mc.getSingleplayerServer();if(server==null||mc.player==null||mc.level==null)return;
        if(phase==1){server.setUsesAuthentication(false);require(server.publishServer(GameType.SURVIVAL,true,25589),"Could not publish QA LAN server");phase=2;mc.setScreen(null);signal("ready","ready");}
        server.execute(()->{try{serverTick(mc);}catch(Throwable ex){failure=ex;}});
        if(exists("observer-pass") && phase==2) {
            TimeStopMod.LOGGER.info("NATIVE_TACZ_NETWORK_PASS: host; two socket clients, frozen native bullet positions/owner, reconnect sync, one native damage hit");
            signal("host-pass","pass");phase=3;ticks=0;
        }
        if(phase==3 && ++ticks>40){done=true;mc.stop();}
    }
    private static void serverTick(Minecraft mc) throws Exception {
        var server=mc.getSingleplayerServer();var level=server.overworld();var players=server.getPlayerList().getPlayers();
        if(serverPhase==0 && players.size()==2) {
            TimeStopManager.setProjectileStasisMode(TimeStopManager.ProjectileStasisMode.SUSPENDED);
            level.setDayTime(18000);
            var shooter=server.getPlayerList().getPlayer(mc.player.getUUID());
            shooter.teleportTo(0,-60,0);shooter.setYRot(-90);shooter.setXRot(0);
            players.stream().filter(p->p!=shooter).forEach(p->p.teleportTo(0,-60,3));
            Class<?> builder=Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");Object b=builder.getMethod("create").invoke(null);
            builder.getMethod("setId",ResourceLocation.class).invoke(b,ResourceLocation.parse("tacz:ak47"));
            builder.getMethod("setAmmoCount",int.class).invoke(b,4);builder.getMethod("setAmmoInBarrel",boolean.class).invoke(b,true);
            Class<?> mode=Class.forName("com.tacz.guns.api.item.gun.FireMode");builder.getMethod("setFireMode",mode).invoke(b,mode.getField("SEMI").get(null));
            ItemStack gun;try{gun=(ItemStack)builder.getMethod("build",HolderLookup.Provider.class).invoke(b,server.registryAccess());}catch(NoSuchMethodException older){gun=(ItemStack)builder.getMethod("build").invoke(b);}
            shooter.setItemInHand(InteractionHand.MAIN_HAND,gun);
            Class<?> api=Class.forName("com.tacz.guns.api.entity.IGunOperator");Object op=api.getMethod("fromLivingEntity",LivingEntity.class).invoke(null,shooter);
            api.getMethod("draw",Supplier.class).invoke(op,(Supplier<ItemStack>)shooter::getMainHandItem);
            bubble=new TemporalBubble(UUID.randomUUID(),UUID.randomUUID(),level.dimension(),shooter.getEyePosition().add(5,0,0),2,TimeMode.TIME_STOP,10000,WatchTier.CREATIVE,null,0,Set.of()).stationary(false);
            TemporalBubbleManager.putStationaryBubble(level,bubble,null);
            target=new Zombie(EntityType.ZOMBIE,level);target.setPos(12,-60,0);target.setNoAi(true);target.setSilent(true);target.setHealth(20);target.setOldPosAndRot();level.addFreshEntity(target);
            serverPhase=1;serverTicks=0;
        }
        if(serverPhase==1 && ++serverTicks==100) {
            var shooter=server.getPlayerList().getPlayer(mc.player.getUUID());
            require(TaczDeadEyeCompat.fire(shooter,target,false)==TaczDeadEyeCompat.Result.FIRED,"Native LAN gun refused firing");
            serverPhase=2;serverTicks=0;
        }
        if(serverPhase==2 && ++serverTicks>5) {
            bullet=level.getEntitiesOfClass(Projectile.class,new AABB(-2,-62,-4,15,-55,4),p->p.getClass().getName().equals("com.tacz.guns.entity.EntityKineticBullet")).stream().findFirst().orElse(null);
            require(bullet!=null && TimeStopManager.isProjectileSuspended(bullet),"Native LAN bullet did not stop at field");
            frozen=bullet.position();signal("frozen",bullet.getId()+"\n"+frozen.x+"\n"+frozen.y+"\n"+frozen.z+"\n"+bullet.getOwner().getUUID()+"\n"+bubble.getId());
            serverPhase=3;
        }
        if(serverPhase==3) {
            require(bullet.isAlive() && bullet.position().distanceToSqr(frozen)<1e-8,"Frozen server bullet drifted");
            require(target.getHealth()==20,"Stopped native shot damaged target early");
            if(exists("reconnected") && players.size()==2){TemporalBubbleManager.stopBubble(level,bubble);serverPhase=4;serverTicks=0;}
        }
        if(serverPhase==4 && ++serverTicks>20) {
            require(target.getHealth()<20,"Released native bullet did not damage target");
            require(target.getLastHurtByMob()==server.getPlayerList().getPlayer(mc.player.getUUID()),"Native LAN damage attribution changed");
            signal("released","pass");serverPhase=5;
        }
    }
    private static void observer(Minecraft mc) throws Exception {
        if(phase==2 && mc.player==null && mc.screen instanceof TitleScreen && ++ticks>30){phase=3;connect(mc);}
        if(mc.player==null||mc.level==null||!exists("frozen"))return;
        if(phase==1 || phase==3) {
            var lines=Files.readAllLines(SHARED.resolve("frozen"));var entity=mc.level.getEntity(Integer.parseInt(lines.get(0)));
            if(!(entity instanceof Projectile p))return;
            Vec3 expected=new Vec3(Double.parseDouble(lines.get(1)),Double.parseDouble(lines.get(2)),Double.parseDouble(lines.get(3)));
            require(p.position().distanceToSqr(expected)<.04,"Observer native bullet position differs from server: "+p.position()+" expected "+expected);
            require(p.getOwner()!=null && p.getOwner().getUUID().equals(UUID.fromString(lines.get(4))),"Observer native owner changed");
            require(ClientBubbleManager.getActiveBubbles().stream().anyMatch(b->b.bubbleId.equals(UUID.fromString(lines.get(5)))),"Observer field state missing");
            if(++observations>=20) {
                if(phase==1){phase=2;observations=0;ticks=0;mc.level.disconnect();mc.disconnect();mc.setScreen(new TitleScreen());signal("disconnected","pass");}
                else{signal("reconnected","pass");phase=4;}
            }
        }
        if(phase==4 && exists("released")) {
            TimeStopMod.LOGGER.info("NATIVE_TACZ_NETWORK_PASS: observer; tracked native bullet, reconnect owner/position/field sync, release");
            signal("observer-pass","pass");phase=5;ticks=0;
        }
        if(phase==5 && ++ticks>40 && exists("host-pass")){done=true;mc.stop();}
    }
}

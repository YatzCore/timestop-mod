package com.timestop.test;

import com.timestop.TimeStopMod;
import com.timestop.combat.TaczDeadEyeCompat;
import com.timestop.client.TaczDeadEyeFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.LivingEntity;
import java.util.function.Supplier;

/** Isolated real client checks. This class and its loader adapters are never distributed. */
public final class NativeTaczClientChecks {
    private static int phase,boot,ticks;
    private static volatile Throwable failure;
    private static volatile boolean equipped,fired;
    private static boolean done;
    public static void tick() {
        if (NativeTaczNetworkChecks.active()) { NativeTaczNetworkChecks.tick(); return; }
        if (!Boolean.getBoolean("timestop.nativeTaczClientQa") || done) return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if (++boot>1800) throw new IllegalStateException("Native client QA timed out");
            if (failure!=null) throw new IllegalStateException("Server firing failed",failure);
            if (phase==0 && boot>80 && mc.getOverlay()==null && !(mc.screen instanceof TitleScreen)) mc.setScreen(new TitleScreen());
            if (phase==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
                if (Boolean.getBoolean("timestop.nativeTaczAbsent")) {
                    if (com.timestop.platform.Services.PLATFORM.isModLoaded("tacz")) throw new IllegalStateException("Absent smoke unexpectedly has TaCZ");
                    TimeStopMod.LOGGER.info("NATIVE_TACZ_ABSENT_CLIENT_PASS");done=true;mc.stop();return;
                }
                phase=1;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(60);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                String world="NativeTaCZQA-"+System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings(world,GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
            }
            if (mc.player==null || mc.level==null || mc.getSingleplayerServer()==null) return;
            if (phase==1) {
                phase=2;mc.setScreen(null);
                mc.getSingleplayerServer().execute(()->{try{
                    var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayer(mc.player.getUUID());
                    Class<?> builder=Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");Object b=builder.getMethod("create").invoke(null);
                    builder.getMethod("setId",ResourceLocation.class).invoke(b,ResourceLocation.parse("tacz:ak47"));
                    builder.getMethod("setAmmoCount",int.class).invoke(b,4);builder.getMethod("setAmmoInBarrel",boolean.class).invoke(b,true);
                    Class<?> mode=Class.forName("com.tacz.guns.api.item.gun.FireMode");builder.getMethod("setFireMode",mode).invoke(b,mode.getField("SEMI").get(null));
                    ItemStack gun;
                    try {gun=(ItemStack)builder.getMethod("build",HolderLookup.Provider.class).invoke(b,server.registryAccess());}
                    catch(NoSuchMethodException olderPort){gun=(ItemStack)builder.getMethod("build").invoke(b);}
                    if(gun.isEmpty())throw new IllegalStateException("Built-in gun missing");player.setItemInHand(InteractionHand.MAIN_HAND,gun);
                    Class<?> api=Class.forName("com.tacz.guns.api.entity.IGunOperator");Object op=api.getMethod("fromLivingEntity",LivingEntity.class).invoke(null,player);
                    api.getMethod("draw",Supplier.class).invoke(op,(Supplier<ItemStack>)player::getMainHandItem);equipped=true;
                }catch(Throwable ex){failure=ex;}});
            }
            if(!equipped)return;
            ticks++;
            if (ticks==100) mc.getSingleplayerServer().execute(()->{try{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var result=TaczDeadEyeCompat.fire(player,player.getEyePosition().add(30,0,0));
                if(result!=TaczDeadEyeCompat.Result.FIRED)throw new IllegalStateException("Native player shot returned "+result);fired=true;
            }catch(Throwable ex){failure=ex;}});
            if (ticks==150 && fired) {
                ItemStack gun=mc.player.getMainHandItem();int before=ammo(gun);
                TaczDeadEyeFeedback.play(gun.copy(),true);TaczDeadEyeFeedback.play(gun.copy(),false);
                var unavailable=TaczDeadEyeFeedback.class.getDeclaredField("unavailable");unavailable.setAccessible(true);
                if(unavailable.getBoolean(null))throw new IllegalStateException("Native animation/sound feedback API failed");
                if(ammo(gun)!=before)throw new IllegalStateException("Presentation consumed ammunition");
                Screenshot.grab(mc.gameDirectory,"native-tacz-feedback.png",mc.getMainRenderTarget(),m->TimeStopMod.LOGGER.info("Native client screenshot: {}",m.getString()));
                TimeStopMod.LOGGER.info("NATIVE_TACZ_CLIENT_PASS: native operator shot, feedback packet, animation/sound APIs, no presentation ammo consumption");
            }
            if(ticks>=180){done=true;mc.stop();}
        }catch(Throwable ex){done=true;TimeStopMod.LOGGER.error("NATIVE_TACZ_CLIENT_FAIL",ex);mc.stop();}
    }
    private static int ammo(ItemStack stack) throws Exception {
        Class<?> api=Class.forName("com.tacz.guns.api.item.IGun");Object gun=api.getMethod("getIGunOrNull",ItemStack.class).invoke(null,stack);
        return (int)api.getMethod("getCurrentAmmoCount",ItemStack.class).invoke(gun,stack);
    }
}

package com.timestop.fabric.test;

import com.timestop.pedestal.*;
import com.timestop.item.*;
import com.timestop.core.TimeMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.Files;

/** Opt-in client QA in build/pedestal-visual-run; excluded from release JARs. */
public class PedestalVisualQa implements ClientModInitializer {
    private int phase, ticks, bootTicks;
    private volatile boolean ready;
    private boolean checkingPause;
    private long pauseStarted;
    private double pausedAnimationTime;
    private long frameStart, sceneNanos;
    private int sceneFrames;
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("timestop.pedestalVisualQa")) return;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context -> frameStart=System.nanoTime());
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(context -> {
            if((ticks>=930 && ticks<960) || (ticks>=1070 && ticks<1120)) { sceneNanos+=System.nanoTime()-frameStart; sceneFrames++; }
            checkPause(Minecraft.getInstance());
        });
    }
    private void tick(Minecraft mc) {
        if (checkingPause) return;
        if (phase==0 && ++bootTicks>80 && mc.level==null && mc.getOverlay()==null && !(mc.screen instanceof TitleScreen)) {
            com.timestop.TimeStopMod.LOGGER.info("QA dismissing initial screen: {}",mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
            mc.setScreen(new TitleScreen());
        }
        if (phase==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
            phase=1; mc.options.pauseOnLostFocus=false; mc.options.renderDistance().set(6); mc.options.hideGui=true;
            mc.options.gamma().set(1.0); mc.options.fov().set(55);
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(120);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            com.timestop.config.TimeStopConfig.CLIENT.enableBubbleRender.set(false);
            if (Files.exists(mc.gameDirectory.toPath().resolve("saves/PedestalQA/level.dat"))) mc.createWorldOpenFlows().loadLevel(mc.screen,"PedestalQA");
            else {
                GameRules rules=new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null); rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel("PedestalQA",new LevelSettings("Pedestal QA",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42,false,false),access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
        }
        if (mc.level==null || mc.player==null || mc.getSingleplayerServer()==null) return;
        if (phase==1) {
            phase=2;
            mc.getSingleplayerServer().execute(() -> {
                var level=mc.getSingleplayerServer().overworld(); var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                level.setDayTime(6000);
                for(int x=-5;x<20;x++)for(int z=-6;z<24;z++) {
                    level.setBlockAndUpdate(new BlockPos(x,100,z),(Math.floorMod(x+z,3)==1 ? Blocks.SEA_LANTERN : Blocks.SMOOTH_STONE).defaultBlockState());
                    for(int y=101;y<107;y++) level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                Item[] watches={ModItems.COPPER_WATCH.get(),ModItems.CHRONOS_WATCH.get(),ModItems.DIAMOND_WATCH.get(),ModItems.NETHERITE_WATCH.get(),ModItems.CREATIVE_WATCH.get()};
                var blocks=ModPedestals.BLOCKS.values().stream().map(com.timestop.registry.RegistryEntry::get).toList();
                for(int col=0;col<5;col++) {
                    for(int row=-1;row<5;row++) {
                        if(row>col)continue;
                        BlockPos pos=new BlockPos(col*3,101,row<0?0:4+row*4);
                        level.setBlockAndUpdate(pos,blocks.get(col).defaultBlockState());
                        var p=(PedestalBlockEntity)level.getBlockEntity(pos); p.setOwner(player);
                        if (row>=0) p.inventory.setItem(0,new ItemStack(watches[row]));
                        p.configure(TimeMode.FAST_FORWARD,1);
                    }
                    player.getInventory().setItem(col,new ItemStack(blocks.get(col)));
                    player.getInventory().setItem(9+col,new ItemStack(watches[col]));
                }
                // Clear drops from rebuilding this isolated QA scene on subsequent runs.
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(-5,99,-6,20,108,24)).forEach(net.minecraft.world.entity.Entity::discard);
                player.setGameMode(GameType.CREATIVE); player.getAbilities().flying=true; player.onUpdateAbilities();
                player.connection.teleport(6.5,104,-10,0,15); ready=true;
            });
        }
        if(!ready)return;
        ticks++;
        // Keep captures stable even if the desktop mouse moves during the unattended run.
        if (ticks < 230) {
            mc.player.setYRot(0); mc.player.setYHeadRot(0);
            mc.player.setXRot(ticks < 170 ? 15 : 38);
        }
        if (ticks >= 390 && ticks < 700) {
            mc.player.setYRot(0); mc.player.setYHeadRot(0);
            mc.player.setXRot(ticks < 460 ? 8 : ticks < 570 ? 10 : 0);
        }
        if(ticks==60) grab(mc,"00-armillary-empty.png");
        if(ticks==65) mc.getSingleplayerServer().execute(() -> {
            var level=mc.getSingleplayerServer().overworld();
            Item[] watches={ModItems.COPPER_WATCH.get(),ModItems.CHRONOS_WATCH.get(),ModItems.DIAMOND_WATCH.get(),ModItems.NETHERITE_WATCH.get(),ModItems.CREATIVE_WATCH.get()};
            for(int col=0;col<5;col++) {
                var p=(PedestalBlockEntity)level.getBlockEntity(new BlockPos(col*3,101,0));
                p.inventory.setItem(0,new ItemStack(watches[col])); p.configure(TimeMode.FAST_FORWARD,1);
            }
        });
        if(ticks==100) grab(mc,"01-lineup-inactive.png");
        if(ticks==110) mc.getSingleplayerServer().execute(() -> {
            var level=mc.getSingleplayerServer().overworld();
            for(int col=0;col<5;col++) for(int row=-1;row<=col;row++) level.setBlockAndUpdate(new BlockPos(col*3,100,row<0?0:4+row*4),Blocks.REDSTONE_BLOCK.defaultBlockState());
        });
        if(ticks==160) grab(mc,"02-lineup-powered.png");
        if(ticks==170) move(mc,6.5,116,-13,0,38);
        if(ticks==220) grab(mc,"03-all-compatible-watches.png");
        if(ticks==230) {
            move(mc,12.5,103,-3,0,25);
            mc.options.hideGui=false;
            mc.getSingleplayerServer().execute(() -> {
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                player.openMenu((PedestalBlockEntity)player.serverLevel().getBlockEntity(new BlockPos(12,101,0)));
            });
        }
        if(ticks==270) grab(mc,"04-settings-screen.png");
        if(ticks==280 && mc.screen instanceof com.timestop.client.gui.PedestalScreen screen)
            screen.mouseClicked((screen.width-176)/2.0+12,(screen.height-224)/2.0+118,0);
        if(ticks==290 && mc.screen instanceof com.timestop.client.gui.PedestalScreen screen)
            screen.mouseDragged((screen.width-176)/2.0+164,(screen.height-224)/2.0+118,0,152,0);
        if(ticks==300 && mc.screen instanceof com.timestop.client.gui.PedestalScreen screen)
            screen.mouseReleased((screen.width-176)/2.0+164,(screen.height-224)/2.0+118,0);
        if(ticks==310) {
            var menu=(PedestalMenu)mc.player.containerMenu;
            if(menu.radius()!=menu.maxRadius()) throw new IllegalStateException("Slider drag did not reach server: "+menu.radius());
            com.timestop.TimeStopMod.LOGGER.info("PEDESTAL_SLIDER_DRAG_PASS radius={}",menu.radius());
            grab(mc,"05-settings-radius-updated.png"); mc.player.closeContainer();
        }
        if(ticks==330) mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        if(ticks==360) grab(mc,"06-inventory-models.png");
        if(ticks==380) {
            mc.setScreen(null); mc.options.hideGui=true;
            com.timestop.config.TimeStopConfig.CLIENT.enableBubbleRender.set(true);
            mc.getSingleplayerServer().execute(() -> {
                var level=mc.getSingleplayerServer().overworld();
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                for(int col=0;col<5;col++) for(int row=-1;row<=col;row++) level.setBlockAndUpdate(new BlockPos(col*3,100,row<0?0:4+row*4),Blocks.STONE.defaultBlockState());
                for(int x=-5;x<46;x++) for(int z=35;z<46;z++) level.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState());
                var watches=new Item[]{ModItems.COPPER_WATCH.get(),ModItems.CHRONOS_WATCH.get(),ModItems.DIAMOND_WATCH.get(),ModItems.NETHERITE_WATCH.get(),ModItems.CREATIVE_WATCH.get()};
                var blocks=ModPedestals.BLOCKS.values().stream().map(com.timestop.registry.RegistryEntry::get).toList();
                for(int col=0;col<5;col++) {
                    var pos=new BlockPos(col*10,101,40);
                    level.setBlockAndUpdate(pos,blocks.get(col).defaultBlockState());
                    var pedestal=(PedestalBlockEntity)level.getBlockEntity(pos); pedestal.setOwner(player);
                    pedestal.inventory.setItem(0,new ItemStack(watches[col])); pedestal.configure(TimeMode.SLOW_MOTION,4);
                    level.setBlockAndUpdate(pos.below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
                }
            });
            move(mc,20.5,108,9,0,8);
        }
        if(ticks==450) grab(mc,"07-beams-all-tiers.png");
        if(ticks==460) {
            move(mc,40.5,104,30,0,10);
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setDayTime(13000));
        }
        if(ticks==510) grab(mc,"08-beam-close.png");
        if(ticks==520 || ticks==570) {
            final int radius=ticks==520 ? 1 : 64;
            mc.getSingleplayerServer().execute(() -> ((PedestalBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(new BlockPos(40,101,40))).configure(TimeMode.SLOW_MOTION,radius));
            if(ticks==570) move(mc,40.5,128,-35,0,0);
        }
        if(ticks==550) grab(mc,"09-beam-minimum-radius.png");
        if(ticks==630) grab(mc,"10-beam-maximum-radius.png");
        if(ticks==650) mc.getSingleplayerServer().execute(() -> {
            var level=mc.getSingleplayerServer().overworld();
            for(int col=0;col<5;col++) level.setBlockAndUpdate(new BlockPos(col*10,100,40),Blocks.STONE.defaultBlockState());
        });
        if(ticks==680) grab(mc,"11-beams-powered-off.png");
        if(ticks==700) {
            com.timestop.config.TimeStopConfig.CLIENT.enableBubbleRender.set(false);
            move(mc,30.5,101.1,36,0,10);
            mc.getSingleplayerServer().execute(() -> {
                var level=mc.getSingleplayerServer().overworld(); level.setDayTime(6000);
                var p=(PedestalBlockEntity)level.getBlockEntity(new BlockPos(30,101,40));
                p.configure(TimeMode.TIME_STOP,4);
                level.setBlockAndUpdate(p.getBlockPos().below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            });
        }
        if(ticks>=700 && ticks<900) { mc.player.setYRot(0); mc.player.setYHeadRot(0); mc.player.setXRot(10); }
        if(ticks>=730 && ticks<=790 && ticks%2==0) grab(mc,String.format("armillary-motion-%03d.png",ticks-730));
        if(ticks==795) {
            checkingPause=true; pauseStarted=0;
            mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
        }
        if(ticks==800) mc.getSingleplayerServer().execute(() -> {
            var level=mc.getSingleplayerServer().overworld();
            level.setBlockAndUpdate(new BlockPos(30,100,40),Blocks.STONE.defaultBlockState());
        });
        if(ticks==820) grab(mc,"12-armillary-decelerating.png");
        if(ticks==830) mc.getSingleplayerServer().execute(() ->
                ((PedestalBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(new BlockPos(30,101,40))).inventory.removeItem(0,1));
        if(ticks==860) grab(mc,"13-armillary-settled-empty.png");
        if(ticks==870) {
            com.timestop.config.TimeStopConfig.CLIENT.enableBubbleRender.set(true);
            move(mc,40.5,101.3,36,0,10);
            mc.getSingleplayerServer().execute(() -> {
                var level=mc.getSingleplayerServer().overworld(); var p=(PedestalBlockEntity)level.getBlockEntity(new BlockPos(40,101,40));
                p.inventory.setItem(0,new ItemStack(ModItems.COPPER_WATCH.get())); p.configure(TimeMode.SLOW_MOTION,1);
                level.setBlockAndUpdate(p.getBlockPos().below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            });
        }
        if(ticks==895) grab(mc,"14-creative-copper-watch-beam.png");
        if(ticks==900) {
            move(mc,8,106,57,0,25);
            com.timestop.config.TimeStopConfig.CLIENT.enableBubbleRender.set(false);
            mc.getSingleplayerServer().execute(() -> {
                var level=mc.getSingleplayerServer().overworld(); var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                for(int x=0;x<8;x++) for(int z=0;z<8;z++) {
                    var pos=new BlockPos(x*2,101,65+z*2);
                    level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(pos,ModPedestals.CREATIVE.get().defaultBlockState());
                    var p=(PedestalBlockEntity)level.getBlockEntity(pos); p.setOwner(player);
                    p.inventory.setItem(0,new ItemStack(ModItems.CREATIVE_WATCH.get()));
                }
                // Adjacent placement deliberately shows decorative overlap without extra occupied blocks.
                level.setBlockAndUpdate(new BlockPos(17,101,65),ModPedestals.CREATIVE.get().defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(18,101,65),ModPedestals.CREATIVE.get().defaultBlockState());
            });
        }
        if(ticks>=900) { mc.player.setYRot(0); mc.player.setYHeadRot(0); mc.player.setXRot(25); }
        if(ticks==960) {
            grab(mc,"15-armillary-dense-scene.png");
            com.timestop.TimeStopMod.LOGGER.info("ARMILLARY_DENSE_SCENE fps={} tracked={}",mc.getFps(),com.timestop.client.renderer.ArmillaryAnimation.trackedCount());
            logRenderCost(mc,"DENSE"); sceneNanos=0; sceneFrames=0;
        }
        if(ticks==970) move(mc,1000,106,1000,0,25);
        if(ticks==1120) {
            logRenderCost(mc,"EMPTY_BASELINE");
            int tracked=com.timestop.client.renderer.ArmillaryAnimation.trackedCount();
            if(tracked!=0) throw new IllegalStateException("Unloaded armillary animation states retained: "+tracked);
            com.timestop.TimeStopMod.LOGGER.info("ARMILLARY_UNLOAD_PASS");
            move(mc,30.5,101.1,36,0,10);
        }
        if(ticks>=1120) mc.player.setXRot(10);
        if(ticks==1170) grab(mc,"16-armillary-reloaded.png");
        if(ticks==1180) move(mc,40.5,101.3,36,51,10);
        if(ticks>=1180 && ticks<1220) { mc.player.setYRot(51); mc.player.setYHeadRot(51); }
        if(ticks==1210) grab(mc,"17-armillary-screen-edge.png");
        if(ticks==1240) { com.timestop.TimeStopMod.LOGGER.info("PEDESTAL_VISUAL_QA_COMPLETE"); mc.stop(); }
    }
    private void logRenderCost(Minecraft mc,String scene) {
        com.timestop.TimeStopMod.LOGGER.info("ARMILLARY_RENDER_COST scene={} fps={} worldRenderMs={} focused={} renderer={}",
                scene,mc.getFps(),sceneFrames==0?0:sceneNanos/1_000_000.0/sceneFrames,mc.isWindowActive(),org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
    }
    private void checkPause(Minecraft mc) {
        if(!checkingPause || !mc.isPaused()) return;
        double time=com.timestop.client.renderer.ArmillaryAnimation.seconds();
        if(pauseStarted==0) { pauseStarted=System.nanoTime(); pausedAnimationTime=time; }
        if(System.nanoTime()-pauseStarted<1_000_000_000L) return;
        if(time!=pausedAnimationTime) throw new IllegalStateException("Armillary moved while paused");
        com.timestop.TimeStopMod.LOGGER.info("ARMILLARY_PAUSE_PASS");
        mc.setScreen(null); checkingPause=false;
    }
    private void move(Minecraft mc,double x,double y,double z,float yaw,float pitch) {
        mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).connection.teleport(x,y,z,yaw,pitch));
    }
    private void grab(Minecraft mc,String name) {
        com.timestop.TimeStopMod.LOGGER.info("QA light sky={} block={}",mc.level.getBrightness(LightLayer.SKY,new BlockPos(6,102,0)),mc.level.getBrightness(LightLayer.BLOCK,new BlockPos(6,102,0)));
        mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message -> com.timestop.TimeStopMod.LOGGER.info("Pedestal QA: {}",message.getString()));
    }
}

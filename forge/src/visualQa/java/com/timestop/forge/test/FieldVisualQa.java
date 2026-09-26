package com.timestop.forge.test;

import com.timestop.TimeStopMod;
import com.timestop.config.TimeStopConfig;
import com.timestop.core.ClientBubbleManager;
import com.timestop.core.TimeMode;
import com.timestop.item.ModItems;
import com.timestop.pedestal.ModPedestals;
import com.timestop.pedestal.PedestalBlockEntity;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import java.nio.file.Files;

/** A real Forge client scene with GPU draw-count checks at both render stages. */
@Mod("timestop_visualqa")
public class FieldVisualQa {
    private int phase, bootTicks, ticks, query, checkedFrames;
    private boolean querying;
    private volatile boolean ready;
    private static final BlockPos SOURCE = new BlockPos(0, 101, 0);

    public FieldVisualQa() { MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (phase == 0 && ++bootTicks > 80 && mc.level == null && mc.getOverlay() == null
                && !(mc.screen instanceof TitleScreen)) mc.setScreen(new TitleScreen());
        if (phase == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            phase = 1;
            mc.options.pauseOnLostFocus = false; mc.options.hideGui = true;
            mc.options.renderDistance().set(6); mc.options.fov().set(70);
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(60);
            mc.options.graphicsMode().set(GraphicsStatus.FANCY);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            TimeStopConfig.CLIENT.enableBubbleRender.set(true);
            java.nio.file.Path savePath = mc.gameDirectory.toPath().resolve("saves/FieldQA");
            if (Files.exists(savePath)) {
                try (var walk = Files.walk(savePath)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).map(java.nio.file.Path::toFile).forEach(java.io.File::delete);
                } catch (Exception ignored) {}
            }
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("FieldQA", new LevelSettings("Field QA", GameType.CREATIVE,
                    false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (phase == 1) {
            phase = 2;
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer(); var level = server.overworld();
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                level.setDayTime(6000);
                for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 100, z),
                            (x == 0 || z == 0 ? Blocks.SEA_LANTERN : Blocks.STONE_BRICKS).defaultBlockState());
                }
                level.setBlockAndUpdate(SOURCE, ModPedestals.GOLDEN.get().defaultBlockState());
                var pedestal = (PedestalBlockEntity) level.getBlockEntity(SOURCE);
                pedestal.setOwner(player); pedestal.inventory.setItem(0, new ItemStack(ModItems.CHRONOS_WATCH.get()));
                pedestal.configure(TimeMode.FAST_FORWARD, 5);
                level.setBlockAndUpdate(SOURCE.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                player.getAbilities().flying = true; player.onUpdateAbilities();
                player.connection.teleport(0.5, 105, -13, 0, 15);
                ready = true;
            });
        }
        if (!ready) return;
        ticks++;
        float yaw = ticks < 100 ? 0 : ticks < 160 ? 18 : ticks < 220 ? -18 : 0;
        float pitch = ticks < 220 ? 15 : 30;
        mc.player.setYRot(yaw); mc.player.setYHeadRot(yaw); mc.player.setXRot(pitch);
        if (ticks == 80 || ticks == 140 || ticks == 200 || ticks == 260 || ticks == 360) {
            mc.getToasts().clear();
            Screenshot.grab(mc.gameDirectory, "field-camera-" + ticks + ".png", mc.getMainRenderTarget(),
                    message -> TimeStopMod.LOGGER.info("FIELD_QA_CAPTURE {}", message.getString()));
        }
        if (ticks == 280) {
            mc.options.graphicsMode().set(GraphicsStatus.FABULOUS);
            mc.levelRenderer.allChanged();
        }
        if (ticks == 390) {
            if (checkedFrames < 30) throw new IllegalStateException("Not enough active field draw checks: " + checkedFrames);
            TimeStopMod.LOGGER.info("FIELD_QA_PEDESTAL_PASS checkedFrames={}", checkedFrames);
            mc.getSingleplayerServer().execute(() -> {
                var server=mc.getSingleplayerServer();
                server.overworld().setBlockAndUpdate(SOURCE.below(),Blocks.STONE_BRICKS.defaultBlockState());
                var player=server.getPlayerList().getPlayer(mc.player.getUUID());
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModItems.DIAMOND_WATCH.get()));
                com.timestop.core.TemporalBubbleManager.startBubble(server.overworld(),player,1200,TimeMode.TIME_STOP);
            });
        }
        if (ticks >= 410 && ticks < 490) {
            // Real client movement, including interpolation between ticks; faster in the second half.
            double speed=ticks < 450 ? .15 : .6;
            mc.player.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(speed,0,0));
            mc.player.setDeltaMovement(0,0,0);
            mc.player.setXRot(0);
            if (ticks % 10 == 0) Screenshot.grab(mc.gameDirectory,"watch-moving-"+ticks+".png",mc.getMainRenderTarget(),
                    message -> TimeStopMod.LOGGER.info("WATCH_QA_CAPTURE {}",message.getString()));
        }
        if (ticks == 500) {
            TimeStopMod.LOGGER.info("FIELD_QA_PASS checkedFrames={} single draw; Fancy/Fabulous; moving watch owner",checkedFrames);
            mc.stop();
        }
    }

    private boolean check(RenderLevelStageEvent event) {
        return ready && ticks > 40 && ClientBubbleManager.hasActiveBubbles()
                && (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void before(RenderLevelStageEvent event) {
        if (!check(event)) return;
        if (query == 0) query = GL15.glGenQueries();
        GL15.glBeginQuery(GL30.GL_PRIMITIVES_GENERATED, query); querying = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void after(RenderLevelStageEvent event) {
        if (!querying) return;
        querying = false; GL15.glEndQuery(GL30.GL_PRIMITIVES_GENERATED);
        int primitives = GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT);
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            if (primitives != 0) throw new IllegalStateException("Duplicate chunk-stage effects: " + primitives);
        } else {
            if (primitives == 0) throw new IllegalStateException("Missing particle-stage field effects");
            checkedFrames++;
        }
    }
}

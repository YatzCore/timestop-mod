package com.timestop.fabric.test;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.PersonalTime;
import com.timestop.core.SelfAcceleration;
import com.timestop.core.TimeMode;
import com.timestop.item.AbstractWatchItem;
import com.timestop.network.ModMessages;
import com.timestop.network.SelectTimeModePacket;
import com.timestop.network.SetCasualAccelerationPacket;
import com.timestop.network.ToggleTimeStopPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in real-client measurement of walking and falling speed in normal time, Self Acceleration and
 * your own Fast Forward field, on wall-clock time. Runs in an isolated singleplayer world, or against
 * a dedicated server with -Dtimestop.movementQa.server=host:port (the player must be an operator).
 * Excluded from release jars.
 */
public class MovementQa implements ClientModInitializer {
    private static final String SERVER = System.getProperty("timestop.movementQa.server", "");
    private record Step(long delayMs, Runnable action) {}
    private final List<Step> steps = new ArrayList<>();
    private boolean loading, walking, falling, airborne;
    private int bootTicks, index, corrections;
    private long due, fallStart, fallMs;
    private double markZ, lastZ, lastY;
    private long markTime;
    private final StringBuilder result = new StringBuilder();

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("timestop.movementQa")) return;
        script();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private static void log(String message) { com.timestop.TimeStopMod.LOGGER.info("MOVEMENT_QA {}", message); }
    private static void command(String command) { Minecraft.getInstance().player.connection.sendCommand(command); }
    private void step(long delayMs, Runnable action) { steps.add(new Step(delayMs, action)); }

    private void home() { command("tp @s 0.5 -60 0.5 0 0"); }
    private void walk(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        walking = on;
        mc.options.keyUp.setDown(on);
        if (on) { corrections = 0; lastZ = mc.player.getZ(); }
    }
    private void mark() { markZ = Minecraft.getInstance().player.getZ(); markTime = System.nanoTime(); }
    private double speed() { return (Minecraft.getInstance().player.getZ() - markZ) / ((System.nanoTime() - markTime) / 1e9); }
    private String state() {
        Minecraft mc = Minecraft.getInstance();
        return "clientTickMs=" + ClientTimeStopManager.getClientTickMs() + " selfRate=" + SelfAcceleration.rate(mc.player)
                + " clientExtraRate=" + PersonalTime.extraRate(mc.player) + " speedAttr=" + mc.player.getSpeed()
                + " mode=" + ClientTimeStopManager.getCurrentMode() + " held=" + AbstractWatchItem.getMode(mc.player.getMainHandItem());
    }
    private void select(TimeMode mode) {
        Minecraft mc = Minecraft.getInstance();
        AbstractWatchItem.setMode(mc.player.getMainHandItem(), mode);
        ModMessages.sendToServer(new SelectTimeModePacket(mode, InteractionHand.MAIN_HAND));
    }
    private void toggle() { ModMessages.sendToServer(new ToggleTimeStopPacket()); }
    private void measureWalk(String label) {
        step(600, () -> { log(label + " state: " + state()); walk(true); });
        step(700, this::mark);
        step(1500, () -> { double v = speed(); result.append(' ').append(label).append("Walk=").append(String.format(java.util.Locale.ROOT, "%.2f", v)).append("b/s(corr=").append(corrections).append(')');
            log(label + " walk " + v + " blocks/s, corrections " + corrections); walk(false); });
    }
    private void measureFall(String label) {
        step(600, () -> { log(label + " fall state: " + state()); command("tp @s 0.5 100 0.5 0 0");
            falling = true; airborne = false; fallMs = -1; corrections = 0; lastY = 1000; });
        step(7000, () -> { result.append(' ').append(label).append("Fall=").append(fallMs).append("ms(corr=").append(corrections).append(')');
            log(label + " fall of ~160 blocks took " + fallMs + " ms, corrections " + corrections); falling = false; });
    }

    private void script() {
        step(3000, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.pauseOnLostFocus = false;
            command("gamemode survival"); command("gamerule fallDamage false"); command("gamerule doMobSpawning false");
            command("time set day"); command("effect clear @s"); home();
        });
        measureWalk("normal");
        measureFall("normal");
        // Self Acceleration from a held watch (local field).
        step(500, () -> { home(); command("item replace entity @s weapon.mainhand with timestop:chronos_watch"); });
        step(800, () -> select(TimeMode.SELF_ACCELERATION));
        step(300, this::toggle);
        measureWalk("self");
        step(300, this::toggle);
        step(500, () -> { home(); command("item replace entity @s weapon.mainhand with timestop:diamond_watch"); });
        step(800, () -> select(TimeMode.SELF_ACCELERATION));
        step(300, this::toggle);
        measureFall("self");
        step(300, this::toggle);
        // Your own Fast Forward field.
        step(500, () -> { home(); command("item replace entity @s weapon.mainhand with timestop:copper_watch"); });
        step(800, () -> select(TimeMode.FAST_FORWARD));
        step(300, this::toggle);
        measureWalk("fastForward");
        step(300, this::toggle);
        // B hotkey and Casual sync.
        step(500, () -> { home(); command("item replace entity @s weapon.mainhand with timestop:netherite_watch"); });
        step(800, () -> select(TimeMode.FAST_FORWARD));
        step(500, () -> net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_B)));
        step(800, () -> {
            Minecraft mc = Minecraft.getInstance();
            TimeMode client = AbstractWatchItem.getMode(mc.player.getMainHandItem());
            String server = "n/a (dedicated server: see MOVEMENT_QA_SERVER lines)";
            if (mc.getSingleplayerServer() != null) server = String.valueOf(AbstractWatchItem.getMode(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).getMainHandItem()));
            result.append(" variantKey=").append(client).append('/').append(server);
            log("B hotkey: client watch " + client + ", server watch " + server);
            ModMessages.sendToServer(new SetCasualAccelerationPacket(true));
        });
        step(800, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSingleplayerServer() != null) {
                boolean casual = PersonalTime.isCasual(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()));
                result.append(" casualOnServer=").append(casual);
                log("Casual flag on server after packet: " + casual);
            }
            ModMessages.sendToServer(new SetCasualAccelerationPacket(com.timestop.config.TimeStopConfig.CLIENT.casualAcceleration.get()));
        });
        if (!SERVER.isEmpty()) {
            // A remote server's speed settings must never end up in this client's own config file.
            double[] file = new double[2];
            step(500, () -> { file[0] = fileFastForward(); command("timestop speed fastforward 3"); });
            step(1000, () -> {
                double memory = com.timestop.config.TimeStopConfig.COMMON.fastForwardRate.get();
                com.timestop.config.TimeStopConfig.CLIENT_SPEC.save(); // what every client settings toggle does
                file[1] = fileFastForward();
                result.append(" remoteSpeedInMemory=").append(memory).append(" localFileFastForward=").append(file[0]).append("->").append(file[1]);
                log("Remote speed sync: memory " + memory + ", local config file " + file[0] + " -> " + file[1]);
                command("timestop speed fastforward " + file[0]);
            });
            step(1000, () -> com.timestop.config.TimeStopConfig.CLIENT_SPEC.save());
        }
        step(800, () -> { log("RESULT" + result); Minecraft.getInstance().stop(); });
    }

    private static double fileFastForward() {
        try (var reader = Files.newBufferedReader(Minecraft.getInstance().gameDirectory.toPath().resolve("config/timestop.json"))) {
            return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("speed_multipliers").get("fastForwardRate").getAsDouble();
        } catch (Exception e) { return Double.NaN; }
    }

    private void tick(Minecraft mc) {
        if (!loading && ++bootTicks > 80 && mc.level == null && mc.getOverlay() == null && !(mc.screen instanceof TitleScreen)) mc.setScreen(new TitleScreen());
        if (!loading && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            loading = true;
            mc.options.renderDistance().set(12);
            mc.options.framerateLimit().set(60);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            if (!SERVER.isEmpty()) {
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen, mc, net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(SERVER),
                        new net.minecraft.client.multiplayer.ServerData("Movement QA", SERVER, false), false);
            } else if (Files.exists(mc.gameDirectory.toPath().resolve("saves/MovementQA/level.dat"))) mc.createWorldOpenFlows().loadLevel(mc.screen, "MovementQA");
            else mc.createWorldOpenFlows().createFreshLevel("MovementQA",
                    new LevelSettings("Movement QA", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
        if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) mc.setScreen(null);
        long now = System.nanoTime();
        if (walking) {
            mc.options.keyUp.setDown(true);
            mc.player.setYRot(0); mc.player.setXRot(0);
            if (mc.player.getZ() < lastZ - 0.5) corrections++;
            lastZ = mc.player.getZ();
        }
        if (falling) {
            if (!airborne && mc.player.getY() > 90) { airborne = true; fallStart = now; lastY = mc.player.getY(); }
            if (airborne && mc.player.getY() > lastY + 0.5) corrections++;
            if (airborne) lastY = mc.player.getY();
            if (airborne && fallMs < 0 && mc.player.onGround() && mc.player.getY() < -50) fallMs = (now - fallStart) / 1_000_000L;
        }
        if (index >= steps.size()) return;
        if (due == 0) due = now + steps.get(0).delayMs() * 1_000_000L;
        if (now < due) return;
        steps.get(index).action().run();
        index++;
        if (index < steps.size()) due = System.nanoTime() + steps.get(index).delayMs() * 1_000_000L;
    }
}

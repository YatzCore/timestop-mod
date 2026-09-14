package com.timestop.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.timestop.TimeStopMod;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TimeStopConfig {

    public static class ConfigValue<T> {
        private T value;
        private final T defaultValue;

        public ConfigValue(T defaultValue) {
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
        }

        public T getDefault() {
            return defaultValue;
        }
    }

    public static class SpecDummy {
        public boolean isLoaded() {
            return true;
        }

        public void save() {
            TimeStopConfig.save();
        }
    }

    public static class Client {
        public final ConfigValue<Boolean> enableBubbleRender = new ConfigValue<>(true);
        public final ConfigValue<Boolean> enableBubbleGrid = new ConfigValue<>(false);
        public final ConfigValue<Double> bubbleOpacity = new ConfigValue<>(0.35);
        public final ConfigValue<Boolean> enableSpecularSheen = new ConfigValue<>(true);
        public final ConfigValue<Boolean> enableEquatorRing = new ConfigValue<>(true);
        public final ConfigValue<Boolean> enableShaders = new ConfigValue<>(true);
        public final ConfigValue<Boolean> enableSounds = new ConfigValue<>(true);
        public final ConfigValue<Boolean> enableTimerHud = new ConfigValue<>(true);
    }

    public static class Common {
        public final ConfigValue<Double> copperRadius = new ConfigValue<>(8.0);
        public final ConfigValue<Double> gildedRadius = new ConfigValue<>(14.0);
        public final ConfigValue<Double> diamondRadius = new ConfigValue<>(22.0);
        public final ConfigValue<Double> netheriteRadius = new ConfigValue<>(32.0);
        public final ConfigValue<Double> creativeRadius = new ConfigValue<>(64.0);

        public final ConfigValue<Integer> copperDuration = new ConfigValue<>(6);
        public final ConfigValue<Integer> gildedDuration = new ConfigValue<>(10);
        public final ConfigValue<Integer> diamondDuration = new ConfigValue<>(14);
        public final ConfigValue<Integer> netheriteDuration = new ConfigValue<>(20);
        public final ConfigValue<Integer> creativeDuration = new ConfigValue<>(0);

        public final ConfigValue<Integer> copperCooldown = new ConfigValue<>(25);
        public final ConfigValue<Integer> gildedCooldown = new ConfigValue<>(18);
        public final ConfigValue<Integer> diamondCooldown = new ConfigValue<>(12);
        public final ConfigValue<Integer> netheriteCooldown = new ConfigValue<>(6);
        public final ConfigValue<Integer> creativeCooldown = new ConfigValue<>(0);

        public final ConfigValue<Boolean> enableWaterWalkingInStasis = new ConfigValue<>(true);
        public final ConfigValue<Integer> friendRequestExpirySeconds = new ConfigValue<>(60);
        public final ConfigValue<Boolean> allowPlayerProjectilesInStasis = new ConfigValue<>(true);

        // Speed Multipliers
        public final ConfigValue<Double> fastForwardRate = new ConfigValue<>(5.0);
        public final ConfigValue<Double> slowMotionRate = new ConfigValue<>(0.25);
        public final ConfigValue<Double> matrixRate = new ConfigValue<>(0.25);
        public final ConfigValue<Double> superhotIdleRate = new ConfigValue<>(0.05);
        public final ConfigValue<Double> decelerationDrag = new ConfigValue<>(0.10);
    }

    public static double clampFastForward(double val) { return Math.max(1.1, Math.min(50.0, val)); }
    public static double clampSlowMotion(double val) { return Math.max(0.01, Math.min(0.99, val)); }
    public static double clampMatrix(double val) { return Math.max(0.01, Math.min(0.99, val)); }
    public static double clampSuperhotIdle(double val) { return Math.max(0.005, Math.min(0.80, val)); }
    public static double clampDecelerationDrag(double val) { return Math.max(0.001, Math.min(0.95, val)); }

    public static void resetSpeedsToDefaults() {
        COMMON.fastForwardRate.set(COMMON.fastForwardRate.getDefault());
        COMMON.slowMotionRate.set(COMMON.slowMotionRate.getDefault());
        COMMON.matrixRate.set(COMMON.matrixRate.getDefault());
        COMMON.superhotIdleRate.set(COMMON.superhotIdleRate.getDefault());
        COMMON.decelerationDrag.set(COMMON.decelerationDrag.getDefault());
    }

    public static final Client CLIENT = new Client();
    public static final Common COMMON = new Common();
    public static final SpecDummy CLIENT_SPEC = new SpecDummy();
    public static final SpecDummy COMMON_SPEC = new SpecDummy();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile = null;

    public static void setConfigFile(File file) {
        configFile = file;
    }

    public static void load() {
        if (configFile == null || !configFile.exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null) {
                if (json.has("visuals")) {
                    JsonObject v = json.getAsJsonObject("visuals");
                    if (v.has("enableBubbleRender")) CLIENT.enableBubbleRender.set(v.get("enableBubbleRender").getAsBoolean());
                    if (v.has("enableBubbleGrid")) CLIENT.enableBubbleGrid.set(v.get("enableBubbleGrid").getAsBoolean());
                    if (v.has("bubbleOpacity")) CLIENT.bubbleOpacity.set(v.get("bubbleOpacity").getAsDouble());
                    if (v.has("enableSpecularSheen")) CLIENT.enableSpecularSheen.set(v.get("enableSpecularSheen").getAsBoolean());
                    if (v.has("enableEquatorRing")) CLIENT.enableEquatorRing.set(v.get("enableEquatorRing").getAsBoolean());
                    if (v.has("enableShaders")) CLIENT.enableShaders.set(v.get("enableShaders").getAsBoolean());
                    if (v.has("enableTimerHud")) CLIENT.enableTimerHud.set(v.get("enableTimerHud").getAsBoolean());
                }
                if (json.has("audio")) {
                    JsonObject a = json.getAsJsonObject("audio");
                    if (a.has("enableSounds")) CLIENT.enableSounds.set(a.get("enableSounds").getAsBoolean());
                }
                if (json.has("mechanics")) {
                    JsonObject m = json.getAsJsonObject("mechanics");
                    if (m.has("enableWaterWalkingInStasis")) COMMON.enableWaterWalkingInStasis.set(m.get("enableWaterWalkingInStasis").getAsBoolean());
                    if (m.has("friendRequestExpirySeconds")) COMMON.friendRequestExpirySeconds.set(m.get("friendRequestExpirySeconds").getAsInt());
                    if (m.has("allowPlayerProjectilesInStasis")) COMMON.allowPlayerProjectilesInStasis.set(m.get("allowPlayerProjectilesInStasis").getAsBoolean());
                }
                if (json.has("watch_radii")) {
                    JsonObject r = json.getAsJsonObject("watch_radii");
                    if (r.has("copperRadius")) COMMON.copperRadius.set(r.get("copperRadius").getAsDouble());
                    if (r.has("gildedRadius")) COMMON.gildedRadius.set(r.get("gildedRadius").getAsDouble());
                    if (r.has("diamondRadius")) COMMON.diamondRadius.set(r.get("diamondRadius").getAsDouble());
                    if (r.has("netheriteRadius")) COMMON.netheriteRadius.set(r.get("netheriteRadius").getAsDouble());
                    if (r.has("creativeRadius")) COMMON.creativeRadius.set(r.get("creativeRadius").getAsDouble());
                }
                if (json.has("watch_durations_seconds")) {
                    JsonObject d = json.getAsJsonObject("watch_durations_seconds");
                    if (d.has("copperDuration")) COMMON.copperDuration.set(d.get("copperDuration").getAsInt());
                    if (d.has("gildedDuration")) COMMON.gildedDuration.set(d.get("gildedDuration").getAsInt());
                    if (d.has("diamondDuration")) COMMON.diamondDuration.set(d.get("diamondDuration").getAsInt());
                    if (d.has("netheriteDuration")) COMMON.netheriteDuration.set(d.get("netheriteDuration").getAsInt());
                    if (d.has("creativeDuration")) COMMON.creativeDuration.set(d.get("creativeDuration").getAsInt());
                }
                if (json.has("watch_cooldowns_seconds")) {
                    JsonObject c = json.getAsJsonObject("watch_cooldowns_seconds");
                    if (c.has("copperCooldown")) COMMON.copperCooldown.set(c.get("copperCooldown").getAsInt());
                    if (c.has("gildedCooldown")) COMMON.gildedCooldown.set(c.get("gildedCooldown").getAsInt());
                    if (c.has("diamondCooldown")) COMMON.diamondCooldown.set(c.get("diamondCooldown").getAsInt());
                    if (c.has("netheriteCooldown")) COMMON.netheriteCooldown.set(c.get("netheriteCooldown").getAsInt());
                    if (c.has("creativeCooldown")) COMMON.creativeCooldown.set(c.get("creativeCooldown").getAsInt());
                }
                if (json.has("speed_multipliers")) {
                    JsonObject s = json.getAsJsonObject("speed_multipliers");
                    if (s.has("fastForwardRate")) COMMON.fastForwardRate.set(clampFastForward(s.get("fastForwardRate").getAsDouble()));
                    if (s.has("slowMotionRate")) COMMON.slowMotionRate.set(clampSlowMotion(s.get("slowMotionRate").getAsDouble()));
                    if (s.has("matrixRate")) COMMON.matrixRate.set(clampMatrix(s.get("matrixRate").getAsDouble()));
                    if (s.has("superhotIdleRate")) COMMON.superhotIdleRate.set(clampSuperhotIdle(s.get("superhotIdleRate").getAsDouble()));
                    if (s.has("decelerationDrag")) COMMON.decelerationDrag.set(clampDecelerationDrag(s.get("decelerationDrag").getAsDouble()));
                }
            }
        } catch (Exception e) {
            TimeStopMod.LOGGER.error("Failed to load timestop configuration", e);
        }
    }

    public static void save() {
        if (configFile == null) return;
        try {
            if (configFile.getParentFile() != null) configFile.getParentFile().mkdirs();
            JsonObject root = new JsonObject();

            JsonObject visuals = new JsonObject();
            visuals.addProperty("enableBubbleRender", CLIENT.enableBubbleRender.get());
            visuals.addProperty("enableBubbleGrid", CLIENT.enableBubbleGrid.get());
            visuals.addProperty("bubbleOpacity", CLIENT.bubbleOpacity.get());
            visuals.addProperty("enableSpecularSheen", CLIENT.enableSpecularSheen.get());
            visuals.addProperty("enableEquatorRing", CLIENT.enableEquatorRing.get());
            visuals.addProperty("enableShaders", CLIENT.enableShaders.get());
            visuals.addProperty("enableTimerHud", CLIENT.enableTimerHud.get());
            root.add("visuals", visuals);

            JsonObject audio = new JsonObject();
            audio.addProperty("enableSounds", CLIENT.enableSounds.get());
            root.add("audio", audio);

            JsonObject mechanics = new JsonObject();
            mechanics.addProperty("enableWaterWalkingInStasis", COMMON.enableWaterWalkingInStasis.get());
            mechanics.addProperty("friendRequestExpirySeconds", COMMON.friendRequestExpirySeconds.get());
            mechanics.addProperty("allowPlayerProjectilesInStasis", COMMON.allowPlayerProjectilesInStasis.get());
            root.add("mechanics", mechanics);

            JsonObject radii = new JsonObject();
            radii.addProperty("copperRadius", COMMON.copperRadius.get());
            radii.addProperty("gildedRadius", COMMON.gildedRadius.get());
            radii.addProperty("diamondRadius", COMMON.diamondRadius.get());
            radii.addProperty("netheriteRadius", COMMON.netheriteRadius.get());
            radii.addProperty("creativeRadius", COMMON.creativeRadius.get());
            root.add("watch_radii", radii);

            JsonObject durations = new JsonObject();
            durations.addProperty("copperDuration", COMMON.copperDuration.get());
            durations.addProperty("gildedDuration", COMMON.gildedDuration.get());
            durations.addProperty("diamondDuration", COMMON.diamondDuration.get());
            durations.addProperty("netheriteDuration", COMMON.netheriteDuration.get());
            durations.addProperty("creativeDuration", COMMON.creativeDuration.get());
            root.add("watch_durations_seconds", durations);

            JsonObject cooldowns = new JsonObject();
            cooldowns.addProperty("copperCooldown", COMMON.copperCooldown.get());
            cooldowns.addProperty("gildedCooldown", COMMON.gildedCooldown.get());
            cooldowns.addProperty("diamondCooldown", COMMON.diamondCooldown.get());
            cooldowns.addProperty("netheriteCooldown", COMMON.netheriteCooldown.get());
            cooldowns.addProperty("creativeCooldown", COMMON.creativeCooldown.get());
            root.add("watch_cooldowns_seconds", cooldowns);

            JsonObject speeds = new JsonObject();
            speeds.addProperty("fastForwardRate", COMMON.fastForwardRate.get());
            speeds.addProperty("slowMotionRate", COMMON.slowMotionRate.get());
            speeds.addProperty("matrixRate", COMMON.matrixRate.get());
            speeds.addProperty("superhotIdleRate", COMMON.superhotIdleRate.get());
            speeds.addProperty("decelerationDrag", COMMON.decelerationDrag.get());
            root.add("speed_multipliers", speeds);

            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            TimeStopMod.LOGGER.error("Failed to save timestop configuration", e);
        }
    }
}
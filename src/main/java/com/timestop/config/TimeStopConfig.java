package com.timestop.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class TimeStopConfig {

    public static class Client {
        public final ForgeConfigSpec.BooleanValue enableBubbleRender;
        public final ForgeConfigSpec.BooleanValue enableBubbleGrid;
        public final ForgeConfigSpec.DoubleValue bubbleOpacity;
        public final ForgeConfigSpec.BooleanValue enableSpecularSheen;
        public final ForgeConfigSpec.BooleanValue enableEquatorRing;
        public final ForgeConfigSpec.BooleanValue enableShaders;
        public final ForgeConfigSpec.BooleanValue enableSounds;
        public final ForgeConfigSpec.BooleanValue enableTimerHud;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.push("visuals");

            enableBubbleRender = builder
                    .comment("Enable rendering of temporal sphere bubbles in the world")
                    .define("enableBubbleRender", true);

            enableBubbleGrid = builder
                    .comment("Enable rendering of sci-fi energy grid lattice lines on spheres")
                    .define("enableBubbleGrid", false);

            bubbleOpacity = builder
                    .comment("Overall opacity/alpha multiplier for temporal spheres (0.05 to 1.0)")
                    .defineInRange("bubbleOpacity", 0.35, 0.05, 1.0);

            enableSpecularSheen = builder
                    .comment("Enable 3D glossy specular lighting highlight on spheres for realistic 3D volume")
                    .define("enableSpecularSheen", true);

            enableEquatorRing = builder
                    .comment("Enable rotating celestial orbit / equator energy rings on spheres")
                    .define("enableEquatorRing", true);

            enableShaders = builder
                    .comment("Enable full-screen post-processing shaders (Desaturation & Superhot)")
                    .define("enableShaders", true);

            enableTimerHud = builder
                    .comment("Enable floating in-game HUD countdown ring and status badges")
                    .define("enableTimerHud", true);

            builder.pop();

            builder.push("audio");

            enableSounds = builder
                    .comment("Enable sound effects (bass drops, clock ticks, heartbeats)")
                    .define("enableSounds", true);

            builder.pop();
        }
    }

    public static class Common {
        public final ForgeConfigSpec.DoubleValue copperRadius;
        public final ForgeConfigSpec.DoubleValue gildedRadius;
        public final ForgeConfigSpec.DoubleValue diamondRadius;
        public final ForgeConfigSpec.DoubleValue netheriteRadius;
        public final ForgeConfigSpec.DoubleValue creativeRadius;

        public final ForgeConfigSpec.IntValue copperDuration;
        public final ForgeConfigSpec.IntValue gildedDuration;
        public final ForgeConfigSpec.IntValue diamondDuration;
        public final ForgeConfigSpec.IntValue netheriteDuration;
        public final ForgeConfigSpec.IntValue creativeDuration;

        public final ForgeConfigSpec.IntValue copperCooldown;
        public final ForgeConfigSpec.IntValue gildedCooldown;
        public final ForgeConfigSpec.IntValue diamondCooldown;
        public final ForgeConfigSpec.IntValue netheriteCooldown;
        public final ForgeConfigSpec.IntValue creativeCooldown;

        public final ForgeConfigSpec.BooleanValue enableWaterWalkingInStasis;
        public final ForgeConfigSpec.IntValue friendRequestExpirySeconds;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("watch_radii");
            copperRadius = builder.defineInRange("copperRadius", 8.0, 2.0, 128.0);
            gildedRadius = builder.defineInRange("gildedRadius", 14.0, 2.0, 128.0);
            diamondRadius = builder.defineInRange("diamondRadius", 22.0, 2.0, 128.0);
            netheriteRadius = builder.defineInRange("netheriteRadius", 32.0, 2.0, 128.0);
            creativeRadius = builder.defineInRange("creativeRadius", 64.0, 2.0, 256.0);
            builder.pop();

            builder.push("watch_durations_seconds");
            copperDuration = builder.defineInRange("copperDuration", 6, 1, 3600);
            gildedDuration = builder.defineInRange("gildedDuration", 10, 1, 3600);
            diamondDuration = builder.defineInRange("diamondDuration", 14, 1, 3600);
            netheriteDuration = builder.defineInRange("netheriteDuration", 20, 1, 3600);
            creativeDuration = builder.defineInRange("creativeDuration", 0, 0, 3600);
            builder.pop();

            builder.push("watch_cooldowns_seconds");
            copperCooldown = builder.defineInRange("copperCooldown", 25, 0, 3600);
            gildedCooldown = builder.defineInRange("gildedCooldown", 18, 0, 3600);
            diamondCooldown = builder.defineInRange("diamondCooldown", 12, 0, 3600);
            netheriteCooldown = builder.defineInRange("netheriteCooldown", 6, 0, 3600);
            creativeCooldown = builder.defineInRange("creativeCooldown", 0, 0, 3600);
            builder.pop();

            builder.push("mechanics");
            enableWaterWalkingInStasis = builder.define("enableWaterWalkingInStasis", true);
            friendRequestExpirySeconds = builder.defineInRange("friendRequestExpirySeconds", 60, 10, 600);
            builder.pop();
        }
    }

    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        final Pair<Client, ForgeConfigSpec> clientPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();

        final Pair<Common, ForgeConfigSpec> commonPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();
    }
}
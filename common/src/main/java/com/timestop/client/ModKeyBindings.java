package com.timestop.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static final KeyMapping TIME_STOP_KEY = new KeyMapping(
            "key.timestop.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.timestop"
    );

    public static final KeyMapping RELEASE_PROJECTILES_KEY = new KeyMapping(
            "key.timestop.release_projectiles",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.timestop"
    );

    public static final KeyMapping TRANSPOSITION_KEY = new KeyMapping(
            "key.timestop.transposition",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.timestop"
    );

    public static final KeyMapping FLIP_COIN_KEY = new KeyMapping(
                    "key.timestop.flip_coin",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_C,
                    "key.categories.timestop"
            );

    public static final KeyMapping PROJECTILE_FLOW_TOGGLE_KEY = new KeyMapping(
            "key.timestop.toggle_projectile_flow",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.timestop"
    );

    public static final KeyMapping KINETIC_BARRIER_KEY = new KeyMapping(
            "key.timestop.kinetic_barrier",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            "key.categories.timestop"
    );

    public static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping(
            "key.timestop.open_settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.timestop"
    );
}
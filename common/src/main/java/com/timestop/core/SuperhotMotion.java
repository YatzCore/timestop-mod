package com.timestop.core;

import net.minecraft.world.phys.Vec3;

public final class SuperhotMotion {
    private SuperhotMotion() {}

    /**
     * In SUPERHOT mode, actual entity/player velocity NEVER advances time.
     * Falling, knockback, sliding, riding, water currents, and residual gravity remain slow.
     * Always returns false to safeguard against velocity-based time advancement.
     */
    public static boolean isMoving(Vec3 motion, boolean grounded) {
        return false;
    }

    /**
     * Checks whether any movement key is pressed.
     */
    public static boolean hasMovementKey(boolean forward, boolean backward, boolean left, boolean right, boolean jump) {
        return forward || backward || left || right || jump;
    }

    /**
     * Evaluates whether player input should advance SUPERHOT time.
     *
     * @param forward        whether the forward movement binding is held
     * @param backward       whether the backward movement binding is held
     * @param left           whether the left movement binding is held
     * @param right          whether the right movement binding is held
     * @param jump           whether the jump binding is held
     * @param isScreenOpen   whether a screen/GUI is currently open
     * @param isWindowActive whether the game window is currently focused/active
     * @return true if and only if a movement key is held with window focused and no menu open
     */
    public static boolean isMovementInputActive(
            boolean forward,
            boolean backward,
            boolean left,
            boolean right,
            boolean jump,
            boolean isScreenOpen,
            boolean isWindowActive
    ) {
        if (isScreenOpen || !isWindowActive) {
            return false;
        }
        return hasMovementKey(forward, backward, left, right, jump);
    }
}

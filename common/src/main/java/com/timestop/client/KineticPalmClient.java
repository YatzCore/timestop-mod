package com.timestop.client;
import com.timestop.combat.RuneManager;
import com.timestop.item.rune.RuneType;
import com.timestop.network.KineticPalmActionPacket;
import com.timestop.network.ModMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class KineticPalmClient {
    // Client-side tracking
    public static boolean clientGuarding = false;
    private static boolean repulsedUntilRelease = false;

    public static boolean isLocalGuarding(Player player) {
        return clientGuarding && Minecraft.getInstance().player == player;
    }
    // ==========================================
    // CLIENT TICK: GUARD DETECTION & REPULSE INPUT
    // ==========================================
        public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            if (clientGuarding) {
                clientGuarding = false;
                if (mc.player != null && mc.getConnection() != null) {
                    ModMessages.sendToServer(new KineticPalmActionPacket(KineticPalmActionPacket.Action.STOP_GUARD_DROP, Vec3.ZERO));
                }
            }
            repulsedUntilRelease = false;
            return;
        }

        // Check guard condition: Holding Middle Mouse Button / Barrier Key with Rune socketed
        boolean barrierKeyDown = com.timestop.client.ModKeyBindings.KINETIC_BARRIER_KEY.isDown();
        if (!barrierKeyDown) repulsedUntilRelease = false;
        boolean hasRune = RuneManager.hasRune(mc.player, RuneType.KINETIC_BARRIER);

        boolean canGuard = barrierKeyDown && hasRune && !repulsedUntilRelease;

        if (canGuard) {
            if (!clientGuarding) {
                clientGuarding = true;
                ModMessages.sendToServer(new KineticPalmActionPacket(KineticPalmActionPacket.Action.START_GUARD, Vec3.ZERO));
            }

            // Repulse triggered by Left Click / Attack while guarding
            if (mc.options.keyAttack.isDown()) {
                clientGuarding = false;
                repulsedUntilRelease = true;
                mc.player.swing(InteractionHand.MAIN_HAND, true);
                ModMessages.sendToServer(new KineticPalmActionPacket(KineticPalmActionPacket.Action.REPULSE, mc.player.getLookAngle()));
            }
        } else {
            if (clientGuarding) {
                clientGuarding = false;
                ModMessages.sendToServer(new KineticPalmActionPacket(KineticPalmActionPacket.Action.STOP_GUARD_DROP, Vec3.ZERO));
            }
        }

    }

}

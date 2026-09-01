package com.timestop.client;

import com.timestop.core.ClientTimeStopManager;
import com.timestop.core.TimeMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ChronoAudioHandler {
    private static int heartbeatTimer = 0;

    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent event) {
        boolean inStasis = false;
        if (com.timestop.core.ClientBubbleManager.hasActiveBubbles()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.cameraEntity != null) {
                net.minecraft.world.phys.Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
                inStasis = com.timestop.core.ClientBubbleManager.isPositionInStasis(camPos);
            }
        } else {
            inStasis = ClientTimeStopManager.isTimeStopped() && ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP;
        }

        if (!com.timestop.config.TimeStopConfig.CLIENT.enableSounds.get()) {
            return;
        }

        if (!inStasis) {
            return;
        }

        SoundInstance sound = event.getSound();
        if (sound == null) return;

        SoundSource source = sound.getSource();

        // Deafening Silence: Mute ambient world audio, weather, flowing fluids, and mob vocalizations
        if (source == SoundSource.AMBIENT 
                || source == SoundSource.WEATHER 
                || source == SoundSource.MUSIC 
                || source == SoundSource.RECORDS 
                || source == SoundSource.BLOCKS 
                || source == SoundSource.HOSTILE 
                || source == SoundSource.NEUTRAL) {
            event.setSound(null);
        }
        // Exempt player footsteps, punches, watch clicks, and UI remain crisp and clear!
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        boolean inStasis = false;
        int remaining = 0;
        int total = 0;

        if (com.timestop.core.ClientBubbleManager.hasActiveBubbles()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.cameraEntity != null) {
                net.minecraft.world.phys.Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
                com.timestop.core.ClientBubbleManager.ClientBubble bubble = com.timestop.core.ClientBubbleManager.getDominantBubble(camPos);
                if (bubble != null && bubble.mode == TimeMode.TIME_STOP) {
                    inStasis = true;
                    remaining = bubble.remainingTicks;
                    total = bubble.totalDuration;
                }
            }
        } else {
            inStasis = ClientTimeStopManager.isTimeStopped() && ClientTimeStopManager.getCurrentMode() == TimeMode.TIME_STOP;
            remaining = ClientTimeStopManager.getRemainingTicks();
            total = ClientTimeStopManager.getTotalDuration();
        }

        if (!inStasis || !com.timestop.config.TimeStopConfig.CLIENT.enableSounds.get()) {
            heartbeatTimer = 0;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.isPaused()) return;

        // 1. Subtle rhythmic heartbeat in deafening silence
        heartbeatTimer++;
        if (heartbeatTimer >= 40) {
            heartbeatTimer = 0;
            player.playSound(SoundEvents.WARDEN_HEARTBEAT, 0.6F, 0.85F);
        }

        // 2. Accelerating clock tick countdown warning during the final 3 seconds (60 ticks)
        if (total > 0 && remaining <= 60 && remaining > 0) {
            boolean shouldClick = false;
            float pitch = 1.0F;

            if (remaining > 40) {
                shouldClick = (remaining % 10 == 0);
                pitch = 1.0F;
            } else if (remaining > 20) {
                shouldClick = (remaining % 6 == 0);
                pitch = 1.3F;
            } else if (remaining > 8) {
                shouldClick = (remaining % 3 == 0);
                pitch = 1.6F;
            } else {
                shouldClick = (remaining % 2 == 0);
                pitch = 2.0F;
            }

            if (shouldClick) {
                player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, pitch);
            }
        }
    }
}

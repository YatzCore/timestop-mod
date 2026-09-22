package com.timestop.sound;

import com.timestop.TimeStopMod;
import com.timestop.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModSounds {
    public static final Map<ResourceLocation, RegistryEntry<SoundEvent>> SOUNDS = new LinkedHashMap<>();

    private static RegistryEntry<SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, name);
        RegistryEntry<SoundEvent> entry = new RegistryEntry<>(id, () -> SoundEvent.createVariableRangeEvent(id));
        SOUNDS.put(id, entry);
        return entry;
    }

    public static final RegistryEntry<SoundEvent> RAMIEL_SCREAM = register("ramiel_scream");
}

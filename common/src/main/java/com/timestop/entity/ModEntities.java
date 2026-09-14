package com.timestop.entity;

import com.timestop.TimeStopMod;
import com.timestop.registry.RegistryEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ModEntities {
    public static final Map<ResourceLocation, RegistryEntry<EntityType<?>>> ENTITIES = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    private static <T extends EntityType<?>> RegistryEntry<T> register(String name, Supplier<T> supplier) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(TimeStopMod.MOD_ID, name);
        RegistryEntry<T> entry = new RegistryEntry<>(id, supplier);
        ENTITIES.put(id, (RegistryEntry<EntityType<?>>) (Object) entry);
        return entry;
    }

    public static final RegistryEntry<EntityType<ChronoCoinEntity>> CHRONO_COIN = register("chrono_coin",
            () -> EntityType.Builder.<ChronoCoinEntity>of(ChronoCoinEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("chrono_coin"));
}
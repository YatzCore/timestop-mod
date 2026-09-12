package com.timestop.entity;

import com.timestop.TimeStopMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TimeStopMod.MOD_ID);

    public static final RegistryObject<EntityType<ChronoCoinEntity>> CHRONO_COIN = ENTITIES.register("chrono_coin",
            () -> EntityType.Builder.<ChronoCoinEntity>of(ChronoCoinEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("chrono_coin"));
}
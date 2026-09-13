package com.timestop.fabric.mixin;

import com.timestop.platform.IEntityDataSaver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityDataSaverMixin implements IEntityDataSaver {
    @Unique
    private CompoundTag timestop$persistentData;

    @Override
    public CompoundTag timestop$getPersistentData() {
        if (this.timestop$persistentData == null) {
            this.timestop$persistentData = new CompoundTag();
        }
        return this.timestop$persistentData;
    }

    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    private void timestop$savePersistentData(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        if (this.timestop$persistentData != null && !this.timestop$persistentData.isEmpty()) {
            compound.put("TimeStopPersistentData", this.timestop$persistentData);
        }
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void timestop$loadPersistentData(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("TimeStopPersistentData", Tag.TAG_COMPOUND)) {
            this.timestop$persistentData = compound.getCompound("TimeStopPersistentData");
        }
    }
}
package com.timestop.mixin;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(EnderMan.class)
public interface EndermanTeleportAccessor {
    @Invoker("teleport") boolean timestop$teleport(double x,double y,double z);
}

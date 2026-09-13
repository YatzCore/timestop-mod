package com.timestop.mixin;

import com.timestop.combat.KineticPalmManager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {

    public PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void onSetupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof Player player && KineticPalmManager.isGuarding(player)) {
            // Raise arm horizontally forward facing the crosshair gaze (Neo bullet stop pose)
            ModelPart arm = (player.getMainArm() == HumanoidArm.RIGHT) ? this.rightArm : this.leftArm;
            arm.xRot = -((float) Math.PI / 2.0F) + this.head.xRot * 0.75F;
            arm.yRot = this.head.yRot * 0.75F;
            arm.zRot = (player.getMainArm() == HumanoidArm.RIGHT) ? 0.05F : -0.05F;
        }
    }
}

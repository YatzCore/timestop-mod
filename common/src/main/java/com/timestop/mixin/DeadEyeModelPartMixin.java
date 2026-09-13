package com.timestop.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ModelPart.class)
public abstract class DeadEyeModelPartMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private void timestop$captureHead(PoseStack pose, VertexConsumer buffer, int light, int overlay,
                                    float red, float green, float blue, float alpha, CallbackInfo ci) {
        com.timestop.client.DeadEyeHeadGeometry.capture((ModelPart) (Object) this, pose);
    }
}

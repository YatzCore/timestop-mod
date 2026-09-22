package com.timestop.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.timestop.core.rewind.BlockRebuildMotion;
import com.timestop.network.RewindBlocksPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.ConcurrentHashMap;

/** Visual-only mesh replacement. The real block, collision and interaction state never change. */
public final class RewindBlockRenderer {
    private static final ConcurrentHashMap<BlockPos, Animation> active = new ConcurrentHashMap<>();
    private static final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
    private static ClientLevel owner;
    private RewindBlockRenderer() {}

    public static boolean isHidden(BlockPos pos, BlockState state) {
        Animation animation = active.get(pos);
        return animation != null && animation.state == state;
    }

    public static void begin(RewindBlocksPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(packet.dimension())) return;
        if (owner != mc.level) { active.clear(); owner = mc.level; }
        int limit = mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL ? 12 : BlockRebuildMotion.MAX_BLOCKS;
        for (var entry : packet.blocks()) {
            if (active.size() >= limit) break;
            if (entry.state().getRenderShape() != RenderShape.MODEL || entry.state().hasBlockEntity()
                    || !entry.state().isCollisionShapeFullBlock(mc.level, entry.pos())) continue;
            if (active.putIfAbsent(entry.pos(), new Animation(entry.state())) == null) dirty(entry.pos());
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != owner) { active.clear(); owner = mc.level; return; }
        if (owner == null) return;
        active.entrySet().removeIf(entry -> {
            Animation animation = entry.getValue();
            boolean finished = ++animation.age >= BlockRebuildMotion.DURATION;
            boolean replaced = animation.age > 2 && owner.getBlockState(entry.getKey()) != animation.state;
            if (finished || replaced) {
                // Remove the mask BEFORE requesting the final mesh rebuild.
                active.remove(entry.getKey(), animation);
                dirty(entry.getKey());
                return true;
            }
            return false;
        });
    }

    public static void clear() { active.clear(); owner = null; }
    private static void dirty(BlockPos pos) {
        Minecraft.getInstance().levelRenderer.setBlocksDirty(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    public static void render(PoseStack pose, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != owner || active.isEmpty()) return;
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        var cameraPos = camera.getPosition();
        for (var entry : active.entrySet()) {
            BlockPos pos = entry.getKey();
            Animation animation = entry.getValue();
            if (owner.getBlockState(pos) != animation.state || pos.distToCenterSqr(cameraPos) > 32 * 32) continue;
            float p = BlockRebuildMotion.progress(animation.age + partialTick);
            float eased = BlockRebuildMotion.converge(p);
            int blockLight = 6, skyLight = 0;
            for (var direction : net.minecraft.core.Direction.values()) {
                int adjacent = LevelRenderer.getLightColor(owner, pos.relative(direction));
                blockLight = Math.max(blockLight, LightTexture.block(adjacent));
                skyLight = Math.max(skyLight, LightTexture.sky(adjacent));
            }
            int light = LightTexture.pack(blockLight, skyLight);
            pose.pushPose();
            pose.translate(pos.getX() + 0.5 - cameraPos.x, pos.getY() + 0.5 - cameraPos.y, pos.getZ() + 0.5 - cameraPos.z);
            float fragments = BlockRebuildMotion.fragmentAlpha(p);
            if (fragments > 0.01F) {
                for (int piece = 0; piece < 8; piece++) {
                    double x = (piece & 1) == 0 ? -1 : 1;
                    double y = (piece & 2) == 0 ? -1 : 1;
                    double z = (piece & 4) == 0 ? -1 : 1;
                    float distance = 0.25F + (1 - eased) * 0.9F;
                    pose.pushPose();
                    pose.translate(x * distance, y * distance + (1 - eased) * 0.25, z * distance);
                    pose.mulPose(Axis.YP.rotationDegrees((1 - eased) * ((piece % 2 == 0) ? 55 : -55)));
                    draw(animation.state, pose, 0.38F + eased * 0.12F, fragments, light);
                    pose.popPose();
                }
            }
            float core = BlockRebuildMotion.coreAlpha(p);
            if (core > 0) draw(animation.state, pose, 0.7F + core * 0.3F, core, light);
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static void draw(BlockState state, PoseStack pose, float scale, float alpha, int light) {
        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.translate(-0.5, -0.5, -0.5);
        MultiBufferSource source = type -> new FadedVertex(buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS)), alpha);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, source, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static final class Animation {
        final BlockState state;
        int age;
        Animation(BlockState state) { this.state = state; }
    }

    private record FadedVertex(VertexConsumer target, float alpha) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return target.addVertex(x, y, z); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return target.setColor(r, g, b, (int)(a * alpha)); }
        @Override public VertexConsumer setColor(int argb) {
            int a = (argb >> 24) & 0xFF;
            int rgb = argb & 0x00FFFFFF;
            return target.setColor(((int)(a * alpha) << 24) | rgb);
        }
        @Override public VertexConsumer setUv(float u, float v) { return target.setUv(u, v); }
        @Override public VertexConsumer setUv1(int u, int v) { return target.setUv1(u, v); }
        @Override public VertexConsumer setUv2(int u, int v) { return target.setUv2(u, v); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return target.setNormal(x, y, z); }
    }
}

package com.timestop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;

/** Head bounds from the actual rendered model, including baby scaling and head rotation. */
public final class DeadEyeHeadGeometry {
    private static final Map<LivingEntity, AABB> bounds = new WeakHashMap<>();
    private static final Set<ModelPart> heads = Collections.newSetFromMap(new IdentityHashMap<>());
    private static LivingEntity rendering;
    private static org.joml.Matrix4f rootInverse;

    public static void begin(LivingEntity entity, EntityModel<?> model, PoseStack pose) {
        heads.clear();
        rendering = entity;
        rootInverse = new org.joml.Matrix4f(pose.last().pose()).invert();
        bounds.remove(entity);
        if (model instanceof HeadedModel headed) heads.add(headed.getHead());
        if (model instanceof AgeableListModel<?> ageable) {
            ((com.timestop.mixin.AgeableListModelAccessor) ageable).timestop$headParts().forEach(part -> {
                int before = heads.size();
                findHeads(part);
                if (heads.size() == before) heads.add(part);
            });
        }
        if (model instanceof HierarchicalModel<?> hierarchical) findHeads(hierarchical.root());
    }

    private static void findHeads(ModelPart part) {
        ((com.timestop.mixin.ModelPartAccessor) (Object) part).timestop$children().forEach((name, child) -> {
            if ((name.toLowerCase(Locale.ROOT).contains("head") && !name.endsWith("parts")) || name.equals("skull")) heads.add(child);
            else findHeads(child);
        });
    }

    public static void capture(ModelPart part, PoseStack pose) {
        if (rendering == null || !heads.contains(part) || !part.visible) return;
        part.visit(pose, (transform, path, index, cube) -> {
            for (int corner = 0; corner < 8; corner++) {
                Vector3f point = new org.joml.Matrix4f(rootInverse).mul(transform.pose()).transformPosition(new Vector3f(
                        ((corner & 1) == 0 ? cube.minX : cube.maxX) / 16f,
                        ((corner & 2) == 0 ? cube.minY : cube.maxY) / 16f,
                        ((corner & 4) == 0 ? cube.minZ : cube.maxZ) / 16f));
                Vec3 local = new Vec3(point.x, point.y, point.z);
                AABB next = new AABB(local, local);
                bounds.merge(rendering, next, AABB::minmax);
            }
        });
    }

    public static void end() { rendering = null; heads.clear(); }

    public static AABB getBounds(LivingEntity entity) {
        if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) {
            return dragon.head.getBoundingBox();
        }
        AABB local = bounds.get(entity);
        if (local != null) return local.move(entity.position());
        // Custom renderers and creatures with no separate head use their own eye height.
        double radius = Math.max(0.10, Math.min(entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.2));
        Vec3 eye = entity.getEyePosition();
        return new AABB(eye, eye).inflate(radius);
    }
}

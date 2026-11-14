package com.timestop.compat.sable;

import dev.ryanhcode.sable.api.physics.constraint.*;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import java.util.*;

/** State written through stock handle APIs, which offer setters but no corresponding getters. */
public final class SableConstraintState {
    public interface Tracked { SableConstraintState timestop$constraintState(); }
    public record Motor(double target, double stiffness, double damping, boolean limited, double maxForce) {}
    public record Limit(double min, double max) {}
    public record Frame(Vector3d position, Quaterniond orientation) {
        Frame copy() { return new Frame(new Vector3d(position), new Quaterniond(orientation)); }
    }
    public record Snapshot(Map<ConstraintJointAxis, Motor> motors, Boolean contacts,
                           Map<ConstraintJointAxis, Limit> limits, Frame first, Frame second, Set<ConstraintJointAxis> locks) {
        public static final Snapshot EMPTY = new Snapshot(Map.of(), null, Map.of(), null, null, null);
        public void apply(PhysicsConstraintHandle handle) {
            if (contacts != null) handle.setContactsEnabled(contacts);
            motors.forEach((axis, m) -> handle.setMotor(axis, m.target(), m.stiffness(), m.damping(), m.limited(), m.maxForce()));
            if (handle instanceof GenericConstraintHandle generic) {
                if (first != null) generic.setFrame1(first.position(), first.orientation());
                if (second != null) generic.setFrame2(second.position(), second.orientation());
                if (locks != null) generic.lockAxes(locks.toArray(ConstraintJointAxis[]::new));
                limits.forEach((axis, limit) -> generic.setLimit(axis, limit.min(), limit.max()));
            }
        }
        public long estimatedBytes() { return 96L + motors.size() * 96L + limits.size() * 64L
                + (first == null ? 0 : 128) + (second == null ? 0 : 128) + (locks == null ? 0 : 64); }
    }

    public final Map<ConstraintJointAxis, Motor> motors = new EnumMap<>(ConstraintJointAxis.class);
    public final Map<ConstraintJointAxis, Limit> limits = new EnumMap<>(ConstraintJointAxis.class);
    public Boolean contacts;
    public Frame first;
    public Frame second;
    public Set<ConstraintJointAxis> locks;

    public Snapshot snapshot() {
        return new Snapshot(Map.copyOf(motors), contacts, Map.copyOf(limits),
                first == null ? null : first.copy(), second == null ? null : second.copy(), locks == null ? null : Set.copyOf(locks));
    }
    public static Snapshot capture(PhysicsConstraintHandle handle) {
        return handle instanceof Tracked tracked ? tracked.timestop$constraintState().snapshot() : Snapshot.EMPTY;
    }
}

package com.timestop.core.rewind;

import net.minecraft.world.entity.Entity;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class RewindEntitySync {
    private static final Set<Entity> pending = Collections.newSetFromMap(new WeakHashMap<>());
    private RewindEntitySync() {}
    public static void mark(Entity entity) { pending.add(entity); }
    public static boolean consume(Entity entity) { return pending.remove(entity); }
}

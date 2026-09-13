package com.timestop.registry;

import net.minecraft.resources.ResourceLocation;
import java.util.function.Supplier;

public class RegistryEntry<T> implements Supplier<T> {
    private final ResourceLocation id;
    private final Supplier<T> supplier;
    private T value;

    public RegistryEntry(ResourceLocation id, Supplier<T> supplier) {
        this.id = id;
        this.supplier = supplier;
    }

    public ResourceLocation getId() {
        return id;
    }

    public void bind(T value) {
        this.value = value;
    }

    @Override
    public T get() {
        if (value == null && supplier != null) {
            value = supplier.get();
        }
        return value;
    }
}
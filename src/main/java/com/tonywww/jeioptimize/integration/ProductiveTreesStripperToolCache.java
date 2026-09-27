package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

public final class ProductiveTreesStripperToolCache {
    private static volatile Entry entry;

    private ProductiveTreesStripperToolCache() {
    }

    public static Ingredient get(Supplier<Ingredient> factory) {
        long currentGeneration = JeiOptRuntimeState.currentGeneration();
        Entry cached = entry;
        if (cached != null && cached.generation() == currentGeneration) {
            return cached.ingredient();
        }
        synchronized (ProductiveTreesStripperToolCache.class) {
            cached = entry;
            if (cached != null && cached.generation() == currentGeneration) {
                return cached.ingredient();
            }
            Ingredient ingredient = factory.get();
            if (JeiOptRuntimeState.isCurrent(currentGeneration)) {
                entry = ingredient == null ? null : new Entry(currentGeneration, ingredient);
            }
            return ingredient;
        }
    }

    public static synchronized void clear() { entry = null; }

    private record Entry(long generation, Ingredient ingredient) {}
}

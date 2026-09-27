package com.tonywww.jeioptimize.recipe;

import java.util.ArrayList;
import java.util.List;

public final class TooltipRecipeIndexLifecycleTest {
    public static void main(String[] args) throws Exception {
        GenerationAwareRecipeIndex<String> index = new GenerationAwareRecipeIndex<>();
        List<String> recipes = new ArrayList<>(List.of("old-server-recipe"));
        check(index.usable(recipes, 1, true), "initial index");
        check(index.find("old-server-recipe") != null, "recipe indexed");
        index.clear();
        check(index.find("old-server-recipe") == null, "disconnect releases indexed recipe references");
        var tracked = GenerationAwareRecipeIndex.class.getDeclaredField("trackedRecipes");
        tracked.setAccessible(true);
        check(tracked.get(index) == null, "disconnect releases the source recipe collection");
        List<String> changed = new ArrayList<>(List.of("new-server-recipe"));
        check(index.usable(changed, 2, true) && index.find("old-server-recipe") == null, "new server has its own index");
        changed.add("external-mutation");
        check(!index.usable(changed, 2, true) && index.broken(), "untracked mutation falls back");
        index.clear();
        check(!index.broken() && index.usable(changed, 3, true), "cleanup allows a fresh healthy index");
        System.out.println("TooltipRecipeIndexLifecycleTest passed: teardown releases entries and source, reload and fallback recovery");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

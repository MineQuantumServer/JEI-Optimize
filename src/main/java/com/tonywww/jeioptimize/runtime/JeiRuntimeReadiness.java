package com.tonywww.jeioptimize.runtime;

import mezz.jei.api.runtime.IJeiRuntime;

/** JEI can publish a non-null runtime with dummy APIs after its GUI plugin throws. */
public final class JeiRuntimeReadiness {
    private JeiRuntimeReadiness() {}

    public static boolean hasGui(IJeiRuntime runtime) {
        if (runtime == null) return false;
        try {
            return real(runtime.getIngredientListOverlay(), "mezz.jei.library.gui.IngredientListOverlayDummy")
                && real(runtime.getIngredientFilter(), "mezz.jei.library.ingredients.IngredientFilterApiDummy")
                && real(runtime.getRecipesGui(), "mezz.jei.library.gui.recipes.RecipesGuiDummy");
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private static boolean real(Object component, String dummyClass) {
        return component != null && !component.getClass().getName().equals(dummyClass);
    }
}

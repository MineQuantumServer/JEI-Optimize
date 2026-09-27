package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiSessionCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.neoforge.startup.StartEventObserver", remap = false)
public abstract class JeiSessionCacheMixin {
    @Shadow(remap = false) public abstract void restart();

    @Redirect(method = "onRecipesUpdatedEvent", remap = false, require = 1,
        at = @At(value = "INVOKE", target = "Lmezz/jei/neoforge/startup/StartEventObserver;restart()V"))
    private void jeiopt$reuseOnIdenticalRecipes(@org.spongepowered.asm.mixin.injection.Coerce Object observer) {
        JeiSessionCache.onRecipeRestart(this::restart);
    }

    @Inject(method = "onRecipesUpdatedEvent", remap = false, at = @At("RETURN"), require = 1)
    private void jeiopt$captureBaseline(CallbackInfo ci) {
        JeiSessionCache.captureIfNeeded();
    }

    @Inject(method = "onResourceManagerReload", remap = false, at = @At("HEAD"), require = 1)
    private void jeiopt$invalidateResources(CallbackInfo ci) {
        com.tonywww.jeioptimize.index.PinyinDictionaryCache.clear();
        JeiSessionCache.clear();
    }
}

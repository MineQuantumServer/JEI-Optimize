package com.tonywww.jeioptimize.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tonywww.jeioptimize.index.DeferredNativeSearchStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "mezz.jei.gui.search.ElementSearch", remap = false)
public abstract class JeiNativeSearchBuilderMixin {
    @Coerce
    // Keep both descriptors explicit: Loom resolves a bare <init> against the compile-time
    // JEI and otherwise writes only its old one-argument signature into the shipped jar.
    @WrapOperation(method = {
        "<init>(Lmezz/jei/gui/search/ElementPrefixParser;)V",
        "<init>(Lmezz/jei/gui/search/ElementPrefixParser;Ljava/util/Collection;Lmezz/jei/api/runtime/IIngredientManager;)V"
    }, remap = false, at = @At(value = "INVOKE",
        target = "Lmezz/jei/api/search/ISearchStorageBuilder;build()Lmezz/jei/api/search/ISearchStorage;"), require = 1)
    private Object jeiopt$retainBulkBuilder(@Coerce Object builder, Operation<Object> original) {
        Object deferred = DeferredNativeSearchStorage.defer(builder);
        return deferred != null ? deferred : original.call(builder);
    }
}

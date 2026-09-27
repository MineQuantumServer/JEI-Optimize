package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.index.PinyinDictionaryCache;
import com.tonywww.jeioptimize.index.SharedSearchDictionary;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Collection;
import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "me.towdium.jecharacters.JechSearchStorage", remap = false)
public abstract class JechDictionaryCacheMixin {
    @Unique private SharedSearchDictionary<Object> jeiopt$dictionary;

    @Inject(method = "<init>()V", remap = false, at = @At("RETURN"), require = 1)
    private void jeiopt$init(CallbackInfo ci) { jeiopt$dictionary = PinyinDictionaryCache.create(this); }

    @Inject(method = "put(Ljava/lang/String;Ljava/lang/Object;)V", remap = false,
        at = @At("HEAD"), cancellable = true, require = 1)
    private void jeiopt$put(String text, Object value, CallbackInfo ci) {
        if (jeiopt$dictionary != null) { jeiopt$dictionary.put(text, value); ci.cancel(); }
    }

    @Inject(method = "getSearchResults(Ljava/lang/String;Ljava/util/function/Consumer;)V", remap = false,
        at = @At("HEAD"), cancellable = true, require = 1)
    private void jeiopt$search(String query, Consumer<Collection<Object>> results, CallbackInfo ci) {
        if (jeiopt$dictionary != null) { results.accept(jeiopt$dictionary.search(query)); ci.cancel(); }
    }

    @Inject(method = "getAllElements(Ljava/util/function/Consumer;)V", remap = false,
        at = @At("HEAD"), cancellable = true, require = 1)
    private void jeiopt$all(Consumer<Collection<Object>> results, CallbackInfo ci) {
        if (jeiopt$dictionary != null) { results.accept(jeiopt$dictionary.allValues()); ci.cancel(); }
    }

    @Inject(method = "statistics()Ljava/lang/String;", remap = false,
        at = @At("HEAD"), cancellable = true, require = 1)
    private void jeiopt$statistics(CallbackInfoReturnable<String> cir) {
        if (jeiopt$dictionary != null) cir.setReturnValue(jeiopt$dictionary.statistics());
    }
}

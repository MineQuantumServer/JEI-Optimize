package com.tonywww.jeioptimize.mixin;

//? if neoforge {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
*///?}
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.instrumentation.JeiPluginCallContext;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels disposed deferred initialization, and keeps it atomic with image replacement/close. */
@Mixin(DynamicTexture.class)
public abstract class DynamicTextureStartupMixin {
    @Shadow private NativeImage pixels;
    @Unique private volatile String jeiopt$creationOrigin;

    @Inject(method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage;)V", at = @At("RETURN"))
    private void jeiopt$rememberCreation(NativeImage image, CallbackInfo ci) {
        if (!RenderSystem.isOnRenderThread()) {
            String plugin = JeiPluginCallContext.currentCall()
                .map(call -> call.pluginUid() + " / " + call.phase()).orElse("outside JEI callback");
            String caller = StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> !frame.getClassName().equals(DynamicTexture.class.getName())
                    && !frame.getClassName().startsWith("com.tonywww.jeioptimize.mixin.DynamicTexture"))
                .findFirst().map(Object::toString).orElse("unknown caller"));
            jeiopt$creationOrigin = Thread.currentThread().getName() + "; " + plugin + "; " + caller;
        }
    }

    // This synthetic method's name is verified against bytecode before applying the mixin.
    //? if neoforge {
    /*@WrapMethod(method = {"lambda$new$0()V", "method_22793()V"}, remap = false, require = 1)
    *///?}
    private void jeiopt$initializeLiveImage(Operation<Void> original) {
        if (!JeiOptFeatureFlags.enabled()) {
            original.call();
            return;
        }
        synchronized (this) {
            if (pixels == null) {
                JeiOptimize.LOGGER.warn(
                    "JEI Optimize skipped deferred initialization of a disposed DynamicTexture; creator: {}",
                    jeiopt$creationOrigin != null ? jeiopt$creationOrigin : "unknown");
                return;
            }
            original.call();
        }
    }

    //? if neoforge {
    /*@WrapMethod(method = "close", require = 1)
    *///?}
    private void jeiopt$closeAtomically(Operation<Void> original) {
        if (!JeiOptFeatureFlags.enabled()) {
            original.call();
            return;
        }
        synchronized (this) {
            original.call();
        }
    }

    //? if neoforge {
    /*@WrapMethod(method = "setPixels", require = 1)
    *///?}
    private void jeiopt$replaceAtomically(NativeImage replacement, Operation<Void> original) {
        if (!JeiOptFeatureFlags.enabled()) {
            original.call(replacement);
            return;
        }
        synchronized (this) {
            original.call(replacement);
        }
    }
}

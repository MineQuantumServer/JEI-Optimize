package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.mixin.accessor.JeiRuntimeAccessor;
import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** JEI 19.57's renamed screen callbacks can fire before runtime publication. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.events.GuiEventHandler", remap = false)
public abstract class JeiGuiRenderGuardScreenMixin {
    @Inject(method = {"onGuiInit(Lnet/minecraft/client/gui/screens/Screen;)V",
        "onGuiOpen(Lnet/minecraft/client/gui/screens/Screen;)V"},
        remap = false, at = @At("HEAD"), cancellable = true, require = 2)
    private void jeiopt$guardLayout(Screen screen, CallbackInfo ci) {
        if (JeiOptStartupProgressState.blocksJeiRendering(JeiRuntimeAccessor.jeiopt$getNullableRuntime() != null)) {
            ci.cancel();
        }
    }

    @Inject(method = "updateForScreenRender(Lnet/minecraft/client/gui/screens/Screen;II)V",
        remap = false, at = @At("HEAD"), cancellable = true, require = 1)
    private void jeiopt$guardPreparation(Screen screen, int mouseX, int mouseY, CallbackInfo ci) {
        if (JeiOptStartupProgressState.blocksJeiRendering(JeiRuntimeAccessor.jeiopt$getNullableRuntime() != null)) {
            ci.cancel();
        }
    }

    @Inject(method = "drawForScreenBackground(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;)V",
        remap = false, at = @At("HEAD"), cancellable = true, require = 1)
    private void jeiopt$guardBackground(Screen screen, GuiGraphics graphics, CallbackInfo ci) {
        if (JeiOptStartupProgressState.blocksJeiRendering(JeiRuntimeAccessor.jeiopt$getNullableRuntime() != null)) {
            ci.cancel();
        }
    }

    @Inject(method = "drawForScreenForeground(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;II)V",
        remap = false, at = @At("HEAD"), cancellable = true, require = 1)
    private void jeiopt$guardForeground(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (JeiOptStartupProgressState.blocksJeiRendering(JeiRuntimeAccessor.jeiopt$getNullableRuntime() != null)) {
            ci.cancel();
        }
    }
}

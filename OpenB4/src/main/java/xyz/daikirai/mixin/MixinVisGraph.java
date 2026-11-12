package xyz.daikirai.mixin;

import xyz.daikirai.module.render.XrayModule;
import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.client.renderer.chunk.VisGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xray (from OpenMyau, GPLv3): stops solid blocks from hiding the chunks behind them.
 * func_178606_a is VisGraph.setOpaqueCube, given by its SRG name.
 */
@Mixin(VisGraph.class)
public abstract class MixinVisGraph {

    @Inject(method = "func_178606_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void clickgui$noOpaque(CallbackInfo ci) {
        if (XrayModule.active) {
            ci.cancel();
        }
    }

    @Inject(method = "computeVisibility", at = @At("HEAD"), cancellable = true)
    private void clickgui$allVisible(CallbackInfoReturnable<SetVisibility> cir) {
        if (XrayModule.active) {
            SetVisibility visibility = new SetVisibility();
            visibility.setAllVisible(true);
            cir.setReturnValue(visibility);
        }
    }
}

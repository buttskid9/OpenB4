package xyz.daikirai.mixin;

import xyz.daikirai.module.render.XrayModule;
import net.minecraft.block.BlockWeb;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Xray (from OpenMyau, GPLv3): this block type draws on the translucent layer so the opacity applies. */
@Mixin(BlockWeb.class)
public abstract class MixinBlockWeb {

    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private void clickgui$xrayLayer(CallbackInfoReturnable<EnumWorldBlockLayer> cir) {
        if (XrayModule.active) {
            cir.setReturnValue(EnumWorldBlockLayer.TRANSLUCENT);
        }
    }
}

package xyz.daikirai.mixin;

import xyz.daikirai.module.render.XrayModule;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Xray (from OpenMyau, GPLv3): notes each wanted ore as chunks are built so it can be boxed. */
@Mixin(BlockRendererDispatcher.class)
public abstract class MixinBlockRendererDispatcher {

    @Inject(method = "renderBlock", at = @At("HEAD"))
    private void clickgui$xrayTrack(IBlockState state, BlockPos pos, IBlockAccess world, WorldRenderer renderer, CallbackInfoReturnable<Boolean> cir) {
        if (XrayModule.active) {
            XrayModule.track(pos, state.getBlock());
        }
    }
}

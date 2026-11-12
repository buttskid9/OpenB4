package xyz.daikirai.mixin;

import xyz.daikirai.module.render.XrayModule;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Xray (from OpenMyau, GPLv3): while on, always use the vertex-colour path so the opacity applies. */
@Mixin(BlockModelRenderer.class)
public abstract class MixinBlockModelRenderer {

    @Shadow
    public boolean renderModelAmbientOcclusion(IBlockAccess world, IBakedModel model, Block block, BlockPos pos, WorldRenderer renderer, boolean checkSides) {
        return false;
    }

    @Inject(
        method = "renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/resources/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;Lnet/minecraft/client/renderer/WorldRenderer;Z)Z",
        at = @At("HEAD"),
        cancellable = true)
    private void clickgui$xrayModel(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos, WorldRenderer renderer, boolean checkSides, CallbackInfoReturnable<Boolean> cir) {
        if (XrayModule.active) {
            cir.setReturnValue(this.renderModelAmbientOcclusion(world, model, state.getBlock(), pos, renderer, checkSides));
        }
    }
}

package xyz.daikirai.mixin;

import xyz.daikirai.module.render.XrayModule;
import net.minecraft.block.Block;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Xray (from OpenMyau, GPLv3): ores draw every face, and terrain moves to the translucent layer. */
@Mixin(Block.class)
public abstract class MixinBlock {

    @Inject(method = "shouldSideBeRendered", at = @At("HEAD"), cancellable = true)
    private void clickgui$xraySide(IBlockAccess world, BlockPos pos, EnumFacing side, CallbackInfoReturnable<Boolean> cir) {
        if (XrayModule.active && XrayModule.fullMode && XrayModule.shouldRenderSide(Block.getIdFromBlock((Block) (Object) this))) {
            // pos is the neighbouring block, so step back to the ore itself
            BlockPos self = new BlockPos(
                pos.getX() - side.getDirectionVec().getX(),
                pos.getY() - side.getDirectionVec().getY(),
                pos.getZ() - side.getDirectionVec().getZ());
            if (XrayModule.checkBlock(self)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private void clickgui$xrayLayer(CallbackInfoReturnable<EnumWorldBlockLayer> cir) {
        if (XrayModule.active) {
            int id = Block.getIdFromBlock((Block) (Object) this);
            if (!XrayModule.shouldRenderSide(id) || !XrayModule.fullMode && !XrayModule.isXrayBlock(id)) {
                cir.setReturnValue(EnumWorldBlockLayer.TRANSLUCENT);
            }
        }
    }
}

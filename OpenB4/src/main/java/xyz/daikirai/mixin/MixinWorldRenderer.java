package xyz.daikirai.mixin;

import xyz.daikirai.module.render.XrayModule;
import net.minecraft.client.renderer.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.IntBuffer;

/** Xray (from OpenMyau, GPLv3): writes the opacity into each block vertex colour. */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer {

    @Redirect(
        method = "putColorMultiplier",
        at = @At(value = "INVOKE", target = "java/nio/IntBuffer.put(II)Ljava/nio/IntBuffer;", remap = false))
    private IntBuffer clickgui$xrayAlpha(IntBuffer buffer, int index, int color) {
        if (XrayModule.active) {
            return buffer.put(index, color & 0xFFFFFF | XrayModule.terrainAlpha << 24);
        }
        return buffer.put(index, color);
    }
}

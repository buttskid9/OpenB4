package xyz.daikirai.module.render;

import xyz.daikirai.client.Overlay;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.HudModule;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.HudFont;
import xyz.daikirai.util.RenderUtils;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * A two-line label in the bottom-right corner of the screen:
 *   Rank - Owner
 *   Release - 111226
 * Purely cosmetic text drawn on your own screen.
 */
public class InfoDisplayModule extends Module {

    private static final String[] RANKS = {"Owner", "Admin", "Developer", "Moderator", "Youtuber", "Member"};
    private static final String RELEASE = "111226";
    private static final int WHITE = 0xFFFFFFFF;
    private static final int PURPLE = 0xFFD088FF;
    private static final float MARGIN = 6.0f;

    private SliderSetting rank, font, scale, style;
    private ButtonSetting themeColor;

    public InfoDisplayModule(int key) {
        super("Info Display", Category.render, key);
    }

    public void link() {
        rank = (SliderSetting) ModuleManager.setting(this, "Rank");
        font = (SliderSetting) ModuleManager.setting(this, "Font");
        scale = (SliderSetting) ModuleManager.setting(this, "Scale");
        style = (SliderSetting) ModuleManager.setting(this, "Font style");
        themeColor = (ButtonSetting) ModuleManager.setting(this, "Theme color");
    }

    private boolean baloo() {
        return (int) font.getInput() == 1 && HudFont.available();
    }

    private float width(String s, boolean baloo) {
        return baloo ? HudFont.width(s) : (float) mc.fontRendererObj.getStringWidth(s);
    }

    private float lineHeight(boolean baloo) {
        return (baloo ? HudFont.height() : mc.fontRendererObj.FONT_HEIGHT) + 2.0f;
    }

    private void text(String s, float x, float y, int color, boolean baloo) {
        if (baloo) {
            HudFont.draw(s, x, y, color);
        } else {
            RenderUtils.shadowText(s, x, y, color);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Overlay.canRender()) {
            return;
        }
        boolean baloo = this.baloo();
        if (baloo) {
            HudFont.setSize(11.0f);
            HudFont.style = (int) style.getInput();
        }
        float s = (float) scale.getInput();
        int valueColor = themeColor.isToggled() ? (0xFF000000 | HudModule.theme.getRGB(0.0)) : PURPLE;

        String[] labels = {"Rank - ", "Release - "};
        String[] values = {RANKS[(int) rank.getInput()], RELEASE};

        ScaledResolution sr = new ScaledResolution(mc);
        float screenW = sr.getScaledWidth() / s;
        float screenH = sr.getScaledHeight() / s;
        float lh = this.lineHeight(baloo);
        float y = screenH - MARGIN / s - (lh * labels.length - 2.0f);

        GlStateManager.pushMatrix();
        GlStateManager.scale(s, s, 1.0f);
        for (int i = 0; i < labels.length; i++) {
            float labelW = this.width(labels[i], baloo);
            float x = screenW - MARGIN / s - labelW - this.width(values[i], baloo);
            this.text(labels[i], x, y, WHITE, baloo);
            this.text(values[i], x + labelW, y, valueColor, baloo);
            y += lh;
        }
        GlStateManager.popMatrix();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }
}

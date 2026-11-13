package xyz.daikirai.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

/**
 * A small TrueType text renderer for the HUD list. It loads one .ttf, draws the characters 32-255
 * into a texture once, then draws text as textured quads. Written from scratch; it does not use
 * any code from other clients.
 *
 * The font file is looked for in two places, in this order:
 *   1. .minecraft/raven-font.ttf   (just drop a file there, no rebuild needed)
 *   2. /assets/clickgui/fonts/Baloo.ttf inside the jar
 * If neither exists, available() is false and callers fall back to Minecraft's own font.
 */
public final class HudFont {

    private static final String RESOURCE = "/assets/clickgui/fonts/Baloo.ttf";
    private static final int FIRST = 32;
    private static final int LAST = 255;
    private static final int PAD = 2;

    private static final int[] PALETTE = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private static Font base;
    private static boolean loadTried;
    private static boolean loadOk;

    private static float wantedSize = 12.0f;
    private static float builtSize = -1.0f;
    private static DynamicTexture texture;
    private static int atlasW, atlasH, cellH;
    private static int[] gx = new int[LAST + 1];
    private static int[] gy = new int[LAST + 1];
    private static float[] adv = new float[LAST + 1];
    private static float topOffset;
    private static int boxHeight = 9;

    private HudFont() {
    }

    /** True if a font file was found and could be read. Only tries once. */
    public static boolean available() {
        if (!loadTried) {
            loadTried = true;
            loadOk = load();
        }
        return loadOk;
    }

    private static boolean load() {
        try {
            File external = new File(Minecraft.getMinecraft().mcDataDir, "raven-font.ttf");
            InputStream in = null;
            if (external.isFile()) {
                in = new java.io.FileInputStream(external);
            } else {
                in = HudFont.class.getResourceAsStream(RESOURCE);
            }
            if (in == null) {
                return false;
            }
            try {
                base = Font.createFont(Font.TRUETYPE_FONT, in);
            } finally {
                in.close();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Sets the pixel size. The texture is rebuilt on the next draw if it changed. */
    public static void setSize(float px) {
        wantedSize = Math.max(6.0f, px);
    }

    /** Height of a line of text, in pixels (the same idea as FontRenderer.FONT_HEIGHT). */
    public static int height() {
        ensureBuilt();
        return boxHeight;
    }

    public static float width(String text) {
        ensureBuilt();
        float w = 0.0f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            w += adv[clamp(c)];
        }
        return w;
    }

    private static int clamp(char c) {
        return c < FIRST || c > LAST ? '?' : c;
    }

    private static void ensureBuilt() {
        if (texture != null && builtSize == wantedSize) {
            return;
        }
        build(wantedSize);
    }

    private static void build(float size) {
        Font font = base.deriveFont(Font.PLAIN, size);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        hints(pg);
        pg.setFont(font);
        FontMetrics fm = pg.getFontMetrics();
        FontRenderContext frc = pg.getFontRenderContext();
        int ascent = fm.getAscent();
        int descent = fm.getDescent();

        // Visual height of ordinary text, so the line box hugs the letters instead of the font's
        // (often much taller) ascent and descent.
        Rectangle2D vb = new TextLayout("Hgjy", font, frc).getBounds();
        float visualTop = (float) -vb.getY();
        float visualBottom = (float) (vb.getY() + vb.getHeight());
        boxHeight = Math.max(1, Math.round(visualTop + visualBottom));
        topOffset = ascent - visualTop;

        cellH = ascent + descent + PAD * 2;
        int x = 0;
        int y = 0;
        int rowW = 512;
        int[] cw = new int[LAST + 1];
        for (int c = FIRST; c <= LAST; c++) {
            adv[c] = (float) font.getStringBounds(String.valueOf((char) c), frc).getWidth();
            cw[c] = (int) Math.ceil(adv[c]) + PAD * 2 + 1;
            if (x + cw[c] > rowW) {
                x = 0;
                y += cellH;
            }
            gx[c] = x;
            gy[c] = y;
            x += cw[c];
        }
        pg.dispose();

        atlasW = rowW;
        atlasH = 1;
        while (atlasH < y + cellH) {
            atlasH <<= 1;
        }

        BufferedImage img = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        g.setFont(font);
        g.setColor(Color.WHITE);
        for (int c = FIRST; c <= LAST; c++) {
            g.drawString(String.valueOf((char) c), gx[c] + PAD, gy[c] + PAD + ascent);
        }
        g.dispose();

        if (texture != null) {
            texture.deleteGlTexture();
        }
        texture = new DynamicTexture(img);
        GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glTexParameteri(3553, 10241, 9729);
        GL11.glTexParameteri(3553, 10240, 9729);
        builtSize = size;
    }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /** 0 = shadow, 1 = outline, 2 = thick shadow. Set by the module before it draws. */
    public static int style = 0;
    private static boolean blackShade;

    /** Draws text with the current style (shadow, outline or thick shadow). Colour codes are supported. */
    public static void draw(String text, float x, float y, int argb) {
        ensureBuilt();
        if (style == 1) {
            blackShade = true;
            float[][] o = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
            for (float[] d : o) {
                pass(text, x + d[0], y + d[1], argb, true);
            }
            blackShade = false;
        } else if (style == 2) {
            pass(text, x + 2.0f, y + 2.0f, argb, true);
            pass(text, x + 1.0f, y + 1.0f, argb, true);
        } else {
            pass(text, x + 1.0f, y + 1.0f, argb, true);
        }
        pass(text, x, y, argb, false);
        GlStateManager.disableBlend();
        GlStateManager.resetColor();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void pass(String text, float x, float y, int argb, boolean shadow) {
        float a = (argb >>> 24 & 0xFF) / 255.0f;
        if (a < 0.02f) {
            return;
        }
        int baseRgb = argb & 0xFFFFFF;
        int rgb = baseRgb;

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.bindTexture(texture.getGlTextureId());

        float px = x;
        float top = y - topOffset - PAD;
        GL11.glBegin(7);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                int idx = "0123456789abcdef".indexOf(code);
                if (idx >= 0) {
                    rgb = PALETTE[idx];
                } else if (code == 'r') {
                    rgb = baseRgb;
                }
                continue;
            }
            int ci = clamp(c);
            int shown = shadow ? (blackShade ? 0 : (rgb & 0xFCFCFC) >> 2) : rgb;
            GL11.glColor4f((shown >> 16 & 0xFF) / 255.0f, (shown >> 8 & 0xFF) / 255.0f, (shown & 0xFF) / 255.0f, a);
            float u0 = (float) gx[ci] / atlasW;
            float v0 = (float) gy[ci] / atlasH;
            float u1 = (float) (gx[ci] + (int) Math.ceil(adv[ci]) + PAD * 2 + 1) / atlasW;
            float v1 = (float) (gy[ci] + cellH) / atlasH;
            float w = (int) Math.ceil(adv[ci]) + PAD * 2 + 1;
            float left = px - PAD;
            GL11.glTexCoord2f(u0, v0);
            GL11.glVertex2f(left, top);
            GL11.glTexCoord2f(u0, v1);
            GL11.glVertex2f(left, top + cellH);
            GL11.glTexCoord2f(u1, v1);
            GL11.glVertex2f(left + w, top + cellH);
            GL11.glTexCoord2f(u1, v0);
            GL11.glVertex2f(left + w, top);
            px += adv[ci];
        }
        GL11.glEnd();
    }
}

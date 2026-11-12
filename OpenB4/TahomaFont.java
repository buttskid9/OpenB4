package xyz.daikirai.util;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;

/**
 * A small text renderer that draws the system font Tahoma from a texture atlas. Written from
 * scratch for the HUD font option; it loads no files and makes no network or native calls.
 *
 * The atlas holds characters 32-255 as white glyphs on a transparent background, so a vertex colour
 * tints them. The font is built at 18 px and drawn at half size, which keeps it sharp when the
 * game's GUI scale is 2. If Tahoma is not installed Java substitutes its default font.
 * Minecraft colour codes (a section sign followed by 0-9 or a-f, or r) are understood.
 */
public final class TahomaFont {

    private static final int CELL = 32;
    private static final int COLS = 16;
    private static final int FIRST = 32;
    private static final int LAST = 255;
    private static final int TEX = 512;
    private static final int PAD = 4;
    private static final float SCALE = 0.5f;
    private static final float SHADOW = 0.5f;

    private static final int[] PALETTE = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private static final float[] ADVANCE = new float[LAST + 1];
    private static DynamicTexture texture;
    private static int textureId = -1;
    private static float lineHeight;
    private static boolean failed;

    private TahomaFont() {
    }

    /** Builds the atlas on first use. Must be called on the render thread. */
    public static boolean ready() {
        if (textureId != -1) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            build();
            return true;
        } catch (Throwable t) {
            failed = true;
            return false;
        }
    }

    private static void build() {
        Font font = new Font("Tahoma", Font.PLAIN, 18);
        BufferedImage image = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        FontRenderContext frc = g.getFontRenderContext();
        int ascent = fm.getAscent();
        lineHeight = fm.getHeight();
        for (int c = FIRST; c <= LAST; c++) {
            if (!font.canDisplay((char) c)) {
                continue;
            }
            int i = c - FIRST;
            int cx = (i % COLS) * CELL;
            int cy = (i / COLS) * CELL;
            String s = String.valueOf((char) c);
            ADVANCE[c] = (float) font.getStringBounds(s, frc).getWidth();
            g.drawString(s, cx + PAD, cy + PAD + ascent);
        }
        g.dispose();
        texture = new DynamicTexture(image);
        textureId = texture.getGlTextureId();
        GlStateManager.bindTexture(textureId);
        GL11.glTexParameteri(3553, 10241, 9729);
        GL11.glTexParameteri(3553, 10240, 9729);
    }

    /** Height of one line of text, in GUI units. */
    public static float height() {
        return ready() ? lineHeight * SCALE : 9.0f;
    }

    /** Width of the text in GUI units, ignoring colour codes. */
    public static float width(String text) {
        if (!ready() || text == null) {
            return 0.0f;
        }
        float w = 0.0f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            if (c >= FIRST && c <= LAST) {
                w += ADVANCE[c] * SCALE;
            }
        }
        return w;
    }

    /** Draws text with its top-left corner at x, y. The colour is ARGB. */
    public static void draw(String text, float x, float y, int argb, boolean shadow) {
        if (!ready() || text == null || text.isEmpty()) {
            return;
        }
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.bindTexture(textureId);
        if (shadow) {
            run(text, x + SHADOW, y + SHADOW, argb, 0.25f);
        }
        run(text, x, y, argb, 1.0f);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void run(String text, float x, float y, int base, float shade) {
        int alpha = base >>> 24 & 0xFF;
        if (alpha == 0) {
            alpha = 255;
        }
        int rgb = base & 0xFFFFFF;
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        float cx = x;
        float size = CELL * SCALE;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                int index = "0123456789abcdef".indexOf(code);
                if (index >= 0) {
                    rgb = PALETTE[index];
                } else if (code == 'r') {
                    rgb = base & 0xFFFFFF;
                }
                continue;
            }
            if (c < FIRST || c > LAST) {
                continue;
            }
            int cell = c - FIRST;
            float u0 = (cell % COLS) * CELL / (float) TEX;
            float v0 = (cell / COLS) * CELL / (float) TEX;
            float u1 = u0 + CELL / (float) TEX;
            float v1 = v0 + CELL / (float) TEX;
            float x0 = cx - PAD * SCALE;
            float y0 = y - PAD * SCALE;
            int r = (int) ((rgb >> 16 & 0xFF) * shade);
            int g = (int) ((rgb >> 8 & 0xFF) * shade);
            int b = (int) ((rgb & 0xFF) * shade);
            wr.pos(x0, y0 + size, 0.0).tex(u0, v1).color(r, g, b, alpha).endVertex();
            wr.pos(x0 + size, y0 + size, 0.0).tex(u1, v1).color(r, g, b, alpha).endVertex();
            wr.pos(x0 + size, y0, 0.0).tex(u1, v0).color(r, g, b, alpha).endVertex();
            wr.pos(x0, y0, 0.0).tex(u0, v0).color(r, g, b, alpha).endVertex();
            cx += ADVANCE[c] * SCALE;
        }
        Tessellator.getInstance().draw();
    }
}

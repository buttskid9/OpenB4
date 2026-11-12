package xyz.daikirai.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

/**
 * Visual markers drawn on the current target: a spinning pyramid above the head, a flat disc above
 * the head, and a filled box. They are drawn through walls and turn red while the target is
 * flashing from a hit. Render-only: this class reads the entity's position and does nothing else.
 */
public final class TargetEsp {

    public static final int PYRAMID = 1;
    public static final int DISC = 2;
    public static final int FILLED_BOX = 3;

    private static final int HIT_RGB = 0xFF3030;

    private TargetEsp() {
    }

    /** Draws one marker. Call from RenderWorldLastEvent. rgb is the normal colour (alpha ignored). */
    public static void draw(Entity entity, int mode, int rgb, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager rm = mc.getRenderManager();

        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - rm.viewerPosX;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - rm.viewerPosY;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - rm.viewerPosZ;

        boolean hit = entity instanceof EntityLivingBase && ((EntityLivingBase) entity).hurtTime > 0;
        int color = (hit ? HIT_RGB : rgb) & 0xFFFFFF;
        float r = (color >> 16 & 0xFF) / 255.0f;
        float g = (color >> 8 & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        boolean blend = GL11.glIsEnabled(3042);
        boolean depth = GL11.glIsEnabled(2929);
        boolean texture = GL11.glIsEnabled(3553);
        boolean lighting = GL11.glIsEnabled(2896);
        boolean cull = GL11.glIsEnabled(2884);
        boolean smooth = GL11.glIsEnabled(2848);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDisable(2896);
            GL11.glDisable(2884);
            GL11.glEnable(2848);
            GL11.glDepthMask(false);
            GL11.glLineWidth(1.6f);

            if (mode == PYRAMID) {
                long t = System.currentTimeMillis();
                double bob = Math.sin((t % 100000L) / 333.0) / 10.0;
                double spin = (t % 3000L) / 3000.0 * Math.PI * 2.0;
                pyramid(x, y + entity.height + 0.25 + bob, z, spin, r, g, b);
            } else if (mode == DISC) {
                double radius = Math.max(0.3, entity.width / 1.5);
                disc(x, y + entity.height + 0.25, z, radius, 0.1, r, g, b);
            } else if (mode == FILLED_BOX) {
                double half = entity.width / 2.0 + 0.1;
                box(x - half, y - 0.05, z - half, x + half, y + entity.height + 0.15, z + half, r, g, b);
            }
        } finally {
            GL11.glDepthMask(depthMask);
            GL11.glLineWidth(1.0f);
            set(2848, smooth);
            set(2884, cull);
            set(2896, lighting);
            set(3553, texture);
            set(2929, depth);
            set(3042, blend);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.resetColor();
        }
    }

    private static void set(int cap, boolean on) {
        if (on) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }

    /** A three-sided pyramid pointing down at the head, turning slowly. */
    private static void pyramid(double cx, double cy, double cz, double spin, float r, float g, float b) {
        double radius = 0.22;
        double height = 0.4;
        double[][] base = new double[3][];
        for (int i = 0; i < 3; i++) {
            double a = spin + i * (Math.PI * 2.0 / 3.0);
            base[i] = new double[]{cx + Math.cos(a) * radius, cy + height, cz + Math.sin(a) * radius};
        }
        GL11.glColor4f(r, g, b, 0.42f);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < 3; i++) {
            double[] p = base[i];
            double[] q = base[(i + 1) % 3];
            GL11.glVertex3d(cx, cy, cz);
            GL11.glVertex3d(p[0], p[1], p[2]);
            GL11.glVertex3d(q[0], q[1], q[2]);
        }
        GL11.glVertex3d(base[0][0], base[0][1], base[0][2]);
        GL11.glVertex3d(base[1][0], base[1][1], base[1][2]);
        GL11.glVertex3d(base[2][0], base[2][1], base[2][2]);
        GL11.glEnd();

        GL11.glColor4f(r, g, b, 0.95f);
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < 3; i++) {
            double[] p = base[i];
            double[] q = base[(i + 1) % 3];
            GL11.glVertex3d(cx, cy, cz);
            GL11.glVertex3d(p[0], p[1], p[2]);
            GL11.glVertex3d(p[0], p[1], p[2]);
            GL11.glVertex3d(q[0], q[1], q[2]);
        }
        GL11.glEnd();
    }

    /** A flat, round disc hovering above the head. */
    private static void disc(double cx, double cy, double cz, double radius, double thickness, float r, float g, float b) {
        int segments = 36;
        double top = cy + thickness;
        GL11.glColor4f(r, g, b, 0.40f);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(cx, top, cz);
        for (int i = 0; i <= segments; i++) {
            double a = i * (Math.PI * 2.0 / segments);
            GL11.glVertex3d(cx + Math.cos(a) * radius, top, cz + Math.sin(a) * radius);
        }
        GL11.glEnd();
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double a = i * (Math.PI * 2.0 / segments);
            double px = cx + Math.cos(a) * radius;
            double pz = cz + Math.sin(a) * radius;
            GL11.glVertex3d(px, top, pz);
            GL11.glVertex3d(px, cy, pz);
        }
        GL11.glEnd();

        GL11.glColor4f(r, g, b, 1.0f);
        for (int ring = 0; ring < 2; ring++) {
            double yy = ring == 0 ? cy : top;
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < segments; i++) {
                double a = i * (Math.PI * 2.0 / segments);
                GL11.glVertex3d(cx + Math.cos(a) * radius, yy, cz + Math.sin(a) * radius);
            }
            GL11.glEnd();
        }
    }

    /** A translucent box with an outline, around the whole body. */
    private static void box(double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b) {
        GL11.glColor4f(r, g, b, 0.30f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x1, y2, z2); GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x2, y2, z1); GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x1, y2, z2); GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y2, z1); GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x2, y1, z2);
        GL11.glEnd();

        GL11.glColor4f(r, g, b, 1.0f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y2, z1); GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x1, y2, z2); GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x1, y2, z2);
        GL11.glEnd();
    }
}

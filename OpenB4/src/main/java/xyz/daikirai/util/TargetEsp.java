package xyz.daikirai.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

/**
 * Visual markers drawn on the current target: a spinning pyramid above the head, a flat disc above
 * the head, and a solid box that wraps the whole body. They are drawn through walls, in blue, and
 * turn red while the target is flashing from a hit. Render-only: this class reads the entity's
 * position and does nothing else.
 */
public final class TargetEsp {

    public static final int PYRAMID = 1;
    public static final int DISC = 2;
    public static final int FILLED_BOX = 3;
    public static final int HAT = 4;
    /** Height of the Rectangle mode in blocks; set by the module from its setting. */
    public static volatile double thickness = 0.1;

    private static final int NORMAL_RGB = 0x0099FF;
    private static final int HIT_RGB = 0xFF4422;

    private TargetEsp() {
    }

    /**
     * Draws one marker. Call from RenderWorldLastEvent. opacity (0 to 1) is how solid the filled
     * parts are; the pyramid and disc also get a thin outline, the box does not.
     */
    public static void draw(Entity entity, int mode, float opacity, float partialTicks) {
        draw(entity, mode, opacity, partialTicks, -1);
    }

    /** rgbOverride is 0xRRGGBB, or -1 to use the normal blue / red-when-hit colour. */
    public static void draw(Entity entity, int mode, float opacity, float partialTicks, int rgbOverride) {
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager rm = mc.getRenderManager();

        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - rm.viewerPosX;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - rm.viewerPosY;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - rm.viewerPosZ;

        boolean hit = entity instanceof EntityLivingBase && ((EntityLivingBase) entity).hurtTime > 0;
        int color = rgbOverride >= 0 ? rgbOverride : (hit ? HIT_RGB : NORMAL_RGB);
        float r = (color >> 16 & 0xFF) / 255.0f;
        float g = (color >> 8 & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = Math.max(0.05f, Math.min(1.0f, opacity));

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
                pyramid(x, y + entity.height + 0.25 + bob, z, spin, r, g, b, a);
            } else if (mode == DISC) {
                double radius = Math.max(0.3, entity.width / 1.5);
                disc(x, y + entity.height + 0.25, z, radius, 0.1, r, g, b, a);
            } else if (mode == HAT) {
                // half-size 0.52 for a player: as wide as the body plus arms, with armour on
                hat(x, y + entity.height + 0.3, z, entity.width * 0.87, r, g, b, a);
            } else if (mode == FILLED_BOX) {
                // Wider and taller than the body so it fully wraps the player: width / 1.2 out from
                // the centre (0.5 for a player) and 0.2 taller than the entity.
                double half = entity.width / 1.3;
                box(x - half, y, z - half, x + half, y + entity.height + 0.12, z + half, r, g, b, a);
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

    /**
     * Raven's original filled box (the entity's own bounding box, grown a little, drawn through
     * walls) with an adjustable opacity. rgb is 0xRRGGBB, opacity is 0 to 1.
     */
    public static void originalBox(Entity entity, int rgb, float opacity, float partialTicks) {
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager rm = mc.getRenderManager();
        double dx = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - rm.viewerPosX;
        double dy = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - rm.viewerPosY;
        double dz = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - rm.viewerPosZ;
        AxisAlignedBB bb = entity.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
        AxisAlignedBB shifted = new AxisAlignedBB(bb.minX - entity.posX + dx, bb.minY - entity.posY + dy, bb.minZ - entity.posZ + dz,
                bb.maxX - entity.posX + dx, bb.maxY - entity.posY + dy, bb.maxZ - entity.posZ + dz);
        float a = Math.max(0.02f, Math.min(1.0f, opacity));

        boolean blend = GL11.glIsEnabled(3042);
        boolean depth = GL11.glIsEnabled(2929);
        boolean texture = GL11.glIsEnabled(3553);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            Render3D.filledBox(shifted, (rgb >> 16 & 0xFF) / 255.0f, (rgb >> 8 & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f, a);
        } finally {
            GL11.glDepthMask(depthMask);
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
    private static void pyramid(double cx, double cy, double cz, double spin, float r, float g, float b, float a) {
        double radius = 0.22;
        double height = 0.4;
        double[][] base = new double[3][];
        for (int i = 0; i < 3; i++) {
            double ang = spin + i * (Math.PI * 2.0 / 3.0);
            base[i] = new double[]{cx + Math.cos(ang) * radius, cy + height, cz + Math.sin(ang) * radius};
        }
        GL11.glColor4f(r, g, b, a);
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

    /** A square slab hovering above the head: solid, no outline, with an adjustable thickness. */
    private static void hat(double cx, double cy, double cz, double radius, float r, float g, float b, float a) {
        box(cx - radius, cy, cz - radius, cx + radius, cy + thickness, cz + radius, r, g, b, a);
    }

    /** A flat, round disc hovering above the head. */
    private static void disc(double cx, double cy, double cz, double radius, double thickness, float r, float g, float b, float a) {
        int segments = 36;
        double top = cy + thickness;
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(cx, top, cz);
        for (int i = 0; i <= segments; i++) {
            double ang = i * (Math.PI * 2.0 / segments);
            GL11.glVertex3d(cx + Math.cos(ang) * radius, top, cz + Math.sin(ang) * radius);
        }
        GL11.glEnd();
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double ang = i * (Math.PI * 2.0 / segments);
            double px = cx + Math.cos(ang) * radius;
            double pz = cz + Math.sin(ang) * radius;
            GL11.glVertex3d(px, top, pz);
            GL11.glVertex3d(px, cy, pz);
        }
        GL11.glEnd();

        GL11.glColor4f(r, g, b, 1.0f);
        for (int ring = 0; ring < 2; ring++) {
            double yy = ring == 0 ? cy : top;
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < segments; i++) {
                double ang = i * (Math.PI * 2.0 / segments);
                GL11.glVertex3d(cx + Math.cos(ang) * radius, yy, cz + Math.sin(ang) * radius);
            }
            GL11.glEnd();
        }
    }

    /** A solid, borderless box: six translucent faces and no outline. */
    private static void box(double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x1, y2, z2); GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y2, z1); GL11.glVertex3d(x2, y2, z1); GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x2, y1, z2); GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y1, z2); GL11.glVertex3d(x1, y2, z2); GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y1, z1); GL11.glVertex3d(x2, y2, z1); GL11.glVertex3d(x2, y2, z2); GL11.glVertex3d(x2, y1, z2);
        GL11.glEnd();
    }
}

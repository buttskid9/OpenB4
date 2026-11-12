package xyz.daikirai.module.render;

import xyz.daikirai.client.Chat;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.Render3D;
import xyz.daikirai.util.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockMobSpawner;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3i;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Xray, ported from OpenMyau (GPLv3). The terrain is drawn see-through (Opacity) and the chosen
 * ores are drawn with all faces, plus a box and an optional tracer line through walls.
 *
 * Modes: Soft = terrain turns translucent, ores keep their normal faces.
 *        Full = terrain turns translucent and ores draw every face as well.
 *
 * The chunk work is done by the mixins (MixinBlock, MixinBlockModelRenderer,
 * MixinBlockRendererDispatcher, MixinWorldRenderer, MixinVisGraph and the layer mixins), which
 * read the static fields in this class.
 */
public class XrayModule extends Module {

    private static final class Ore {
        final String name;
        final String tracerName;
        final int rgb;
        final int[] ids;
        ButtonSetting on;
        ButtonSetting tracer;

        Ore(String name, String tracerName, int rgb, int... ids) {
            this.name = name;
            this.tracerName = tracerName;
            this.rgb = rgb;
            this.ids = ids;
        }
    }

    private static final Ore[] ORES = {
        new Ore("Diamonds", "Diamond tracers", 0x55FFFF, 56),
        new Ore("Gold", "Gold tracers", 0xFFFF55, 14),
        new Ore("Iron", "Iron tracers", 0xFFFFFF, 15),
        new Ore("Coal", "Coal tracers", 0x000000, 16),
        new Ore("Redstone", "Redstone tracers", 0xFF5555, 73, 74),
        new Ore("Lapis", "Lapis tracers", 0x5555FF, 21),
        new Ore("Emeralds", "Emerald tracers", 0x55FF55, 129),
        new Ore("Spawners", "Spawner tracers", 0xFF55FF, 52),
        new Ore("Canes", "Cane tracers", 0xAAFFAA, 83),
        new Ore("Warts", "Wart tracers", 0xAA0000, 115)
    };

    /** Block id to ore, so per-block lookups on the chunk threads are a single array read. */
    private static final Ore[] BY_ID = new Ore[256];

    static {
        for (Ore o : ORES) {
            for (int id : o.ids) {
                BY_ID[id] = o;
            }
        }
    }

    private static final Vec3i[] CAVE_SMALL = {
        new Vec3i(0, -1, 0), new Vec3i(1, 0, 0), new Vec3i(0, 0, -1),
        new Vec3i(0, 0, 1), new Vec3i(-1, 0, 0), new Vec3i(0, 1, 0)
    };

    private static final Vec3i[] CAVE_LARGE = {
        new Vec3i(0, -2, 0), new Vec3i(1, -1, 0), new Vec3i(0, -1, -1), new Vec3i(0, -1, 0),
        new Vec3i(0, -1, 1), new Vec3i(-1, -1, 0), new Vec3i(2, 0, 0), new Vec3i(0, 0, 2),
        new Vec3i(0, 0, -2), new Vec3i(-2, 0, 0), new Vec3i(1, 0, -1), new Vec3i(1, 0, 0),
        new Vec3i(1, 0, 1), new Vec3i(0, 0, -1), new Vec3i(0, 0, 1), new Vec3i(-1, 0, -1),
        new Vec3i(-1, 0, 0), new Vec3i(-1, 0, 1), new Vec3i(1, 1, 0), new Vec3i(0, 1, -1),
        new Vec3i(0, 1, 0), new Vec3i(0, 1, 1), new Vec3i(-1, 1, 0), new Vec3i(0, 2, 0)
    };

    /** Read by the mixins. */
    public static volatile boolean active;
    public static volatile boolean fullMode;
    /** Vertex alpha for terrain, 0-255. */
    public static volatile int terrainAlpha = 128;
    private static volatile boolean cavesOnly;
    private static volatile int caveRadius = 2;

    /** Ore blocks found while chunks are built, drawn as boxes. */
    public static final CopyOnWriteArraySet<BlockPos> tracked = new CopyOnWriteArraySet<BlockPos>();

    private SliderSetting mode, opacity, range, radius;
    private ButtonSetting caves;
    private long lastSig = -1L;
    private long changedAt;
    private boolean pendingReload;

    public XrayModule(int key) {
        super("Xray", Category.render, key);
    }

    @Override
    public void link() {
        this.mode = (SliderSetting) ModuleManager.setting(this, "Mode");
        this.opacity = (SliderSetting) ModuleManager.setting(this, "Opacity");
        this.range = (SliderSetting) ModuleManager.setting(this, "Range");
        this.caves = (ButtonSetting) ModuleManager.setting(this, "Caves only");
        this.radius = (SliderSetting) ModuleManager.setting(this, "Cave radius");
        for (Ore o : ORES) {
            o.on = (ButtonSetting) ModuleManager.setting(this, o.name);
            o.tracer = (ButtonSetting) ModuleManager.setting(this, o.tracerName);
        }
    }

    // ----- queries used by the mixins -----

    private static Ore oreFor(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }

    /** True for every ore this module knows about, whether or not it is switched on. */
    public static boolean shouldRenderSide(int id) {
        return oreFor(id) != null;
    }

    /** True for an ore that is switched on in the settings. */
    public static boolean isXrayBlock(int id) {
        Ore o = oreFor(id);
        return o != null && o.on != null && o.on.isToggled();
    }

    /** With Caves only on, an ore counts only if it touches open space. */
    public static boolean checkBlock(BlockPos pos) {
        if (!cavesOnly) {
            return true;
        }
        Vec3i[] offsets = caveRadius >= 2 ? CAVE_LARGE : CAVE_SMALL;
        for (Vec3i v : offsets) {
            if (isOpenSpace(pos.add(v))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOpenSpace(BlockPos pos) {
        if (mc.theWorld == null || !mc.theWorld.isBlockLoaded(pos, false)) {
            return false;
        }
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block instanceof BlockMobSpawner || !block.isFullBlock() || !block.getMaterial().isOpaque() || block.canProvidePower();
    }

    /** Called by the dispatcher mixin for every ore block a chunk rebuild passes over. */
    public static void track(BlockPos pos, Block block) {
        if (!isXrayBlock(Block.getIdFromBlock(block))) {
            return;
        }
        if (checkBlock(pos)) {
            tracked.add(new BlockPos(pos));
        } else {
            tracked.remove(pos);
        }
    }

    // ----- module -----

    /** Reads the settings into the static fields, then rebuilds every loaded chunk. */
    private void refresh() {
        fullMode = (int) this.mode.getInput() == 1;
        terrainAlpha = (int) (this.opacity.getInput() * 255.0 / 100.0);
        cavesOnly = this.caves.isToggled();
        caveRadius = (int) this.radius.getInput();
        this.lastSig = this.signature();
        tracked.clear();
        active = true;
        reloadChunks();
    }

    /** Settings that change chunk geometry; the range only affects drawing, so it is left out. */
    private long signature() {
        long sig = (long) this.mode.getInput() | ((long) this.opacity.getInput() << 8) | ((long) this.radius.getInput() << 24);
        sig |= this.caves.isToggled() ? 1L << 40 : 0L;
        return sig;
    }

    private static void reloadChunks() {
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    @Override
    public void onEnable() {
        this.pendingReload = false;
        ForgeModContainer.forgeLightPipelineEnabled = false;
        this.refresh();
    }

    @Override
    public void onDisable() {
        active = false;
        this.pendingReload = false;
        tracked.clear();
        ForgeModContainer.forgeLightPipelineEnabled = true;
        reloadChunks();
    }

    @Override
    public void guiButtonToggled(ButtonSetting b) {
        if (this.isEnabled()) {
            this.refresh();
        }
    }

    /** Called every tick while the GUI is open; waits for a slider to settle before rebuilding. */
    @Override
    public void guiUpdate() {
        if (!this.isEnabled() || this.mode == null) {
            return;
        }
        long sig = this.signature();
        if (sig != this.lastSig) {
            this.lastSig = sig;
            this.changedAt = System.currentTimeMillis();
            this.pendingReload = true;
        }
    }

    @Override
    public void onUpdate() {
        if (this.pendingReload && System.currentTimeMillis() - this.changedAt > 400L) {
            this.pendingReload = false;
            this.refresh();
        }
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            tracked.clear();
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Chat.inWorld() || tracked.isEmpty()) {
            return;
        }
        double max = this.range.getInput();
        for (BlockPos pos : tracked) {
            Ore ore = oreFor(Block.getIdFromBlock(mc.theWorld.getBlockState(pos).getBlock()));
            if (ore == null || !ore.on.isToggled()) {
                tracked.remove(pos);
                continue;
            }
            if (mc.thePlayer.getDistance(pos.getX(), pos.getY(), pos.getZ()) > max) {
                continue;
            }
            Render3D.block(pos, 0xFF000000 | ore.rgb, true, true);
            if (ore.tracer.isToggled()) {
                tracerTo(pos, ore.rgb);
            }
        }
    }

    /** A line from the camera to the middle of the block, drawn through walls. */
    private static void tracerTo(BlockPos pos, int rgb) {
        RenderManager rm = mc.getRenderManager();
        double x = pos.getX() + 0.5 - rm.viewerPosX;
        double y = pos.getY() + 0.5 - rm.viewerPosY;
        double z = pos.getZ() + 0.5 - rm.viewerPosZ;
        GL11.glEnable(3042);
        GL11.glEnable(2848);
        GL11.glDisable(2929);
        GL11.glDisable(3553);
        GL11.glBlendFunc(770, 771);
        GL11.glLineWidth(1.5f);
        GlStateManager.color((rgb >> 16 & 0xFF) / 255.0f, (rgb >> 8 & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f, 1.0f);
        GL11.glBegin(1);
        GL11.glVertex3d(0.0, mc.thePlayer.getEyeHeight(), 0.0);
        GL11.glVertex3d(x, y, z);
        GL11.glEnd();
        RenderUtils.resetColor();
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDisable(2848);
        GL11.glDisable(3042);
    }
}

package xyz.daikirai.module.other;

import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Mouse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * A small panel on the right-hand side of the inventory screen. Each row is an item with a name;
 * clicking a row sends that row's chat command. The rows come from the file
 * .minecraft/raven-fastcraft.txt, one per line:
 *
 *     minecraft:golden_apple:0 | 2 | Golden Apple | /craft gapple
 *
 * (item id[:damage] | count shown on the icon | label | command). Lines starting with # are ignored. The file is created with
 * examples the first time the module is turned on. Edit it to match your server's commands.
 */
public class FastCraftModule extends Module {

    private static final class Entry {
        final ItemStack stack;
        final String label;
        final String command;

        Entry(ItemStack stack, String label, String command) {
            this.stack = stack;
            this.label = label;
            this.command = command;
        }
    }

    private static final int COLS = 5;
    private static final int ROWS = 4;
    private static final int CELL = 18;
    private static final int PANEL_W = COLS * CELL + 8;
    private static final int HEADER_H = 16;
    private static final int FOOTER_H = 18;
    private static final int PER_PAGE = COLS * ROWS;
    private static final int BLUE = 0xFF2D8CFF;

    private final List<Entry> entries = new ArrayList<Entry>();
    private ButtonSetting showNames;
    private int page;

    public FastCraftModule(int key) {
        super("Fast Craft", Category.other, key);
    }

    @Override
    public void link() {
        this.showNames = (ButtonSetting) ModuleManager.setting(this, "Show names");
    }

    @Override
    public void onEnable() {
        this.load();
        this.page = 0;
    }

    private static File file() {
        return new File(mc.mcDataDir, "raven-fastcraft.txt");
    }

    private void load() {
        this.entries.clear();
        File f = file();
        try {
            if (!f.exists()) {
                FileWriter w = new FileWriter(f);
                w.write("# item id[:damage] | count | label | command   (lines starting with # are ignored)\n");
                w.write("# These are examples - change the commands to the ones your server uses.\n");
                w.write("minecraft:golden_apple:0 | 1 | Golden Apple | /craft gapple\n");
                w.write("minecraft:bow | 1 | Bow | /craft bow\n");
                w.write("minecraft:enchanting_table | 1 | Enchant Table | /craft enchant\n");
                w.write("minecraft:anvil | 1 | Anvil | /craft anvil\n");
                w.close();
            }
            BufferedReader r = new BufferedReader(new FileReader(f));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|", 4);
                if (p.length < 4) {
                    continue;
                }
                String id = p[0].trim();
                int meta = 0;
                int colon = id.lastIndexOf(':');
                if (colon > id.indexOf(':')) {
                    meta = Integer.parseInt(id.substring(colon + 1).trim());
                    id = id.substring(0, colon);
                }
                Item item = (Item) Item.itemRegistry.getObject(new ResourceLocation(id));
                if (item == null) {
                    continue;
                }
                int count = Math.max(1, Math.min(64, Integer.parseInt(p[1].trim())));
                this.entries.add(new Entry(new ItemStack(item, count, meta), p[2].trim(), p[3].trim()));
            }
            r.close();
        } catch (Exception ex) {
            // unreadable file: show an empty panel rather than crash
        }
    }

    private int[] origin(GuiContainer gui) {
        int left = (gui.width - 176) / 2;
        int top = (gui.height - 166) / 2;
        try {
            left = ReflectionHelper.findField(GuiContainer.class, "guiLeft", "field_147003_i").getInt(gui);
            top = ReflectionHelper.findField(GuiContainer.class, "guiTop", "field_147009_r").getInt(gui);
        } catch (Exception ex) {
            // fall back to the centred position
        }
        int x = left + 176 + 4;
        if (x + PANEL_W > gui.width) {
            x = Math.max(2, left - PANEL_W - 4);
        }
        return new int[]{x, top};
    }

    private int pages() {
        return Math.max(1, (this.entries.size() + PER_PAGE - 1) / PER_PAGE);
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (!(e.gui instanceof GuiInventory)) {
            return;
        }
        GuiContainer gui = (GuiContainer) e.gui;
        int[] o = this.origin(gui);
        int x = o[0];
        int y = o[1];
        int h = HEADER_H + ROWS * CELL + 4 + FOOTER_H;
        int pages = this.pages();

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        Gui.drawRect(x, y, x + PANEL_W, y + h, 0xC0141418);
        Gui.drawRect(x, y, x + PANEL_W, y + HEADER_H, BLUE);
        mc.fontRendererObj.drawStringWithShadow("Crafts (" + this.entries.size() + ")", x + 5, y + 4, 0xFFFFFFFF);

        Entry hovered = null;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = this.page * PER_PAGE + i;
            if (idx >= this.entries.size()) {
                break;
            }
            Entry en = this.entries.get(idx);
            int cx = x + 4 + (i % COLS) * CELL;
            int cy = y + HEADER_H + 2 + (i / COLS) * CELL;
            boolean hover = e.mouseX >= cx && e.mouseX < cx + CELL && e.mouseY >= cy && e.mouseY < cy + CELL;
            if (hover) {
                Gui.drawRect(cx, cy, cx + CELL, cy + CELL, 0x60FFFFFF);
                hovered = en;
            }
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(en.stack, cx + 1, cy + 1);
            mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, en.stack, cx + 1, cy + 1, null);
            RenderHelper.disableStandardItemLighting();
        }
        if (this.entries.isEmpty()) {
            mc.fontRendererObj.drawStringWithShadow("raven-fastcraft.txt", x + 4, y + HEADER_H + 6, 0xFFAAAAAA);
            mc.fontRendererObj.drawStringWithShadow("is empty", x + 4, y + HEADER_H + 16, 0xFFAAAAAA);
        }

        int fy = y + h - FOOTER_H + 2;
        Gui.drawRect(x + 4, fy, x + 20, fy + 14, BLUE);
        Gui.drawRect(x + PANEL_W - 20, fy, x + PANEL_W - 4, fy + 14, BLUE);
        mc.fontRendererObj.drawStringWithShadow("<", x + 10, fy + 3, 0xFFFFFFFF);
        mc.fontRendererObj.drawStringWithShadow(">", x + PANEL_W - 14, fy + 3, 0xFFFFFFFF);
        String pg = (this.page + 1) + "/" + pages;
        mc.fontRendererObj.drawStringWithShadow(pg, x + (PANEL_W - mc.fontRendererObj.getStringWidth(pg)) / 2, fy + 3, 0xFFFFFFFF);

        if (hovered != null) {
            int tw = mc.fontRendererObj.getStringWidth(hovered.label);
            int tx = e.mouseX + 8;
            int ty = e.mouseY - 14;
            Gui.drawRect(tx - 3, ty - 3, tx + tw + 3, ty + 11, 0xE0100010);
            mc.fontRendererObj.drawStringWithShadow(hovered.label, tx, ty, 0xFFFFFFFF);
        }
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre e) {
        if (!(e.gui instanceof GuiInventory) || !Mouse.getEventButtonState() || Mouse.getEventButton() != 0) {
            return;
        }
        GuiContainer gui = (GuiContainer) e.gui;
        ScaledResolution sr = new ScaledResolution(mc);
        int mx = Mouse.getEventX() * sr.getScaledWidth() / mc.displayWidth;
        int my = sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / mc.displayHeight - 1;
        int[] o = this.origin(gui);
        int x = o[0];
        int y = o[1];
        int h = HEADER_H + ROWS * CELL + 4 + FOOTER_H;
        if (mx < x || mx >= x + PANEL_W || my < y || my >= y + h) {
            return;
        }
        e.setCanceled(true);
        int fy = y + h - FOOTER_H + 2;
        if (my >= fy && my < fy + 14) {
            if (mx >= x + 4 && mx < x + 20) {
                this.page = (this.page + this.pages() - 1) % this.pages();
            } else if (mx >= x + PANEL_W - 20 && mx < x + PANEL_W - 4) {
                this.page = (this.page + 1) % this.pages();
            }
            return;
        }
        int rx = mx - (x + 4);
        int ry = my - (y + HEADER_H + 2);
        if (rx < 0 || ry < 0 || rx >= COLS * CELL || ry >= ROWS * CELL) {
            return;
        }
        int idx = this.page * PER_PAGE + (ry / CELL) * COLS + (rx / CELL);
        if (idx < this.entries.size()) {
            mc.thePlayer.sendChatMessage(this.entries.get(idx).command);
        }
    }
}

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
 *     minecraft:golden_apple | Golden Apple | /craft gapple
 *
 * (item id | label | command). Lines starting with # are ignored. The file is created with
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

    private static final int PANEL_W = 104;
    private static final int ROW_H = 18;
    private static final int PER_PAGE = 8;

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
                w.write("# item id | label | command   (lines starting with # are ignored)\n");
                w.write("# These are examples - change the commands to the ones your server uses.\n");
                w.write("minecraft:golden_apple | Golden Apple | /craft gapple\n");
                w.write("minecraft:bow | Bow | /craft bow\n");
                w.write("minecraft:enchanting_table | Enchant Table | /craft enchant\n");
                w.write("minecraft:anvil | Anvil | /craft anvil\n");
                w.close();
            }
            BufferedReader r = new BufferedReader(new FileReader(f));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|", 3);
                if (p.length < 3) {
                    continue;
                }
                Item item = (Item) Item.itemRegistry.getObject(new ResourceLocation(p[0].trim()));
                if (item == null) {
                    continue;
                }
                this.entries.add(new Entry(new ItemStack(item), p[1].trim(), p[2].trim()));
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
        int rows = Math.min(PER_PAGE, Math.max(1, this.entries.size() - this.page * PER_PAGE));
        int h = rows * ROW_H + 18;

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        Gui.drawRect(x, y, x + PANEL_W, y + h, 0xB0101018);
        mc.fontRendererObj.drawStringWithShadow("Fast Craft", x + 4, y + 4, 0xFFFFFFFF);
        if (this.pages() > 1) {
            String pg = (this.page + 1) + "/" + this.pages();
            mc.fontRendererObj.drawStringWithShadow(pg, x + PANEL_W - 4 - mc.fontRendererObj.getStringWidth(pg), y + 4, 0xFFAAAAAA);
        }
        for (int i = 0; i < rows; i++) {
            int idx = this.page * PER_PAGE + i;
            if (idx >= this.entries.size()) {
                break;
            }
            Entry en = this.entries.get(idx);
            int ry = y + 16 + i * ROW_H;
            boolean hover = e.mouseX >= x && e.mouseX < x + PANEL_W && e.mouseY >= ry && e.mouseY < ry + ROW_H;
            Gui.drawRect(x + 2, ry, x + PANEL_W - 2, ry + ROW_H - 1, hover ? 0x80B070FF : 0x40FFFFFF);
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectInto(en.stack, x + 3, ry + 1);
            RenderHelper.disableStandardItemLighting();
            if (this.showNames.isToggled()) {
                mc.fontRendererObj.drawStringWithShadow(en.label, x + 23, ry + 5, 0xFFFFFFFF);
            }
        }
        if (this.entries.isEmpty()) {
            mc.fontRendererObj.drawStringWithShadow("raven-fastcraft.txt", x + 4, y + 20, 0xFFAAAAAA);
            mc.fontRendererObj.drawStringWithShadow("is empty", x + 4, y + 30, 0xFFAAAAAA);
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
        if (mx < x || mx >= x + PANEL_W || my < y) {
            return;
        }
        // header: click to flip to the next page
        if (my < y + 16) {
            if (my < y + 16 && this.pages() > 1) {
                this.page = (this.page + 1) % this.pages();
            }
            e.setCanceled(true);
            return;
        }
        int row = (my - y - 16) / ROW_H;
        int idx = this.page * PER_PAGE + row;
        if (row >= 0 && row < PER_PAGE && idx < this.entries.size()) {
            mc.thePlayer.sendChatMessage(this.entries.get(idx).command);
            e.setCanceled(true);
        }
    }
}

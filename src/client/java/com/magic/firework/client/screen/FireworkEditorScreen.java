package com.magic.firework.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.item.FireworkDataHelper;
import com.magic.firework.screen.FireworkEditorMenu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The firework editor "homepage".
 *
 * <p>Layout mirrors the reference workbench: a framed centre slot holds the firework, the
 * surrounding category slots emerge once one is placed, attributes are shown along the top,
 * and the player inventory (27 slots + hotbar) is at the bottom. The category slots are
 * clickable but their sub-screens are still stubs, so clicking them is a no-op for now.
 */
public class FireworkEditorScreen extends AbstractContainerScreen<FireworkEditorMenu> {
    private static final int IMAGE_W = 380;
    private static final int IMAGE_H = 260;

    /** Ring slot angles in degrees, clockwise from the top. */
    private static final float[] ANGLES = { -90.0F, -45.0F, 0.0F, 45.0F, 90.0F, 135.0F, 180.0F, 225.0F };
    private static final int RING_RADIUS = 40;
    private static final int SLOT_SIZE = 22;

    private static final int ATTR_X = 40;
    private static final int ATTR_COL_GAP = 192;
    private static final int ATTR_ROW_GAP = 10;

    private static final List<Category> CATEGORIES = List.of(
            new Category("screen.magic-firework.editor.slot.shape", new ItemStack(Items.FIREWORK_STAR)),
            new Category("screen.magic-firework.editor.slot.color", new ItemStack(Items.RED_DYE)),
            new Category("screen.magic-firework.editor.slot.effect", new ItemStack(Items.FIREWORK_ROCKET)),
            new Category("screen.magic-firework.editor.slot.flight", new ItemStack(Items.FEATHER)),
            new Category("screen.magic-firework.editor.slot.fuse", new ItemStack(Items.STRING)),
            new Category("screen.magic-firework.editor.slot.sound", new ItemStack(Items.NOTE_BLOCK)),
            new Category("screen.magic-firework.editor.slot.light", new ItemStack(Items.GLOWSTONE)),
            new Category("screen.magic-firework.editor.slot.trail", new ItemStack(Items.GUNPOWDER)));

    /** Category slot top-left corners, relative to leftPos/topPos. */
    private final int[] catX = new int[ANGLES.length];
    private final int[] catY = new int[ANGLES.length];

    /** 0 → surrounding slots hidden, 1 → fully revealed. */
    private float reveal;

    public FireworkEditorScreen(FireworkEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        this.imageWidth = IMAGE_W;
        this.imageHeight = IMAGE_H;
        super.init();
        layout();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        float target = this.menu.getCenterStack().isEmpty() ? 0.0F : 1.0F;
        reveal += (target - reveal) * 0.2F;
        if (Math.abs(target - reveal) < 0.01F) {
            reveal = target;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
        this.renderCategoryTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        Font font = this.font;

        drawPanel(g);
        drawSlotBackgrounds(g);

        // ── top band: title + attributes ───────────────────────────────────
        g.drawCenteredString(font, Component.translatable("screen.magic-firework.editor.title"), x + 190, y + 6, 0xFFFFFFFF);
        ItemStack center = this.menu.getCenterStack();

        if (!center.isEmpty()) {
            List<Component> lines = attributeLines(center);
            for (int i = 0; i < lines.size(); i++) {
                int col = i / 4;
                int row = i % 4;
                g.drawString(font, lines.get(i), x + ATTR_X + col * ATTR_COL_GAP, y + 22 + row * ATTR_ROW_GAP, 0xFFC8C8C8);
            }
        }

        g.fill(x + 12, y + 64, x + IMAGE_W - 12, y + 65, 0xFF2E2E34);

        // ── centre slot + surrounding categories ───────────────────────────
        if (center.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.magic-firework.editor.place_hint"),
                    x + 190, y + 150, 0xFFE0E0E0);
            g.drawCenteredString(font, Component.translatable("screen.magic-firework.editor.reveal_hint"),
                    x + 190, y + 162, 0xFF8B8B8B);
        } else if (reveal > 0.02F) {
            int alpha = (int) (reveal * 255);
            boolean clickable = reveal > 0.5F;
            for (int i = 0; i < CATEGORIES.size(); i++) {
                boolean hover = clickable && isHovering(catX[i], catY[i], SLOT_SIZE, SLOT_SIZE, mouseX, mouseY);
                int border = hover ? 0xFFFFFFFF : ((alpha << 24) | 0x9A9AA4);
                drawSlot(g, x + catX[i], y + catY[i], SLOT_SIZE, CATEGORIES.get(i).icon(), border, reveal >= 0.4F);
                g.drawCenteredString(font, Component.translatable(CATEGORIES.get(i).labelKey()),
                        x + catX[i] + SLOT_SIZE / 2, y + catY[i] + SLOT_SIZE + 3,
                        hover ? 0xFFFFFFFF : 0xFFB0B0B0);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Labels are drawn in renderBg at absolute coordinates.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && reveal > 0.5F) {
            for (int i = 0; i < CATEGORIES.size(); i++) {
                if (isHovering(catX[i], catY[i], SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
                    openCategory(CATEGORIES.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderCategoryTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (reveal > 0.5F) {
            for (int i = 0; i < CATEGORIES.size(); i++) {
                if (isHovering(catX[i], catY[i], SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
                    g.renderTooltip(this.font, Component.translatable(CATEGORIES.get(i).labelKey()), mouseX, mouseY);
                    break;
                }
            }
        }
    }

    /** Placeholder for the surrounding-slot sub-screens; the homepage is the only screen for now. */
    private void openCategory(Category category) {
        // TODO: 点击后的编辑界面尚未设计。
    }

    // ── rendering helpers ─────────────────────────────────────────────────────

    private void layout() {
        int ringCx = FireworkEditorMenu.CENTER_X + 9;
        int ringCy = FireworkEditorMenu.CENTER_Y + 9;
        for (int i = 0; i < ANGLES.length; i++) {
            double rad = Math.toRadians(ANGLES[i]);
            int cx = (int) Math.round(ringCx + Math.cos(rad) * RING_RADIUS);
            int cy = (int) Math.round(ringCy + Math.sin(rad) * RING_RADIUS);
            catX[i] = cx - SLOT_SIZE / 2;
            catY[i] = cy - SLOT_SIZE / 2;
        }
    }

    private void drawPanel(GuiGraphics g) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x, y, x + IMAGE_W, y + IMAGE_H, 0xE6101014);
        int border = 0xFF4A4A52;
        g.fill(x, y, x + IMAGE_W, y + 1, border);
        g.fill(x, y + IMAGE_H - 1, x + IMAGE_W, y + IMAGE_H, border);
        g.fill(x, y, x + 1, y + IMAGE_H, border);
        g.fill(x + IMAGE_W - 1, y, x + IMAGE_W, y + IMAGE_H, border);
    }

    /** Draws the 18px background box for every real slot (centre + inventory). */
    private void drawSlotBackgrounds(GuiGraphics g) {
        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) {
                int sx = this.leftPos + slot.x;
                int sy = this.topPos + slot.y;
                g.fill(sx, sy, sx + 18, sy + 18, 0xFF1C1C22);
                int border = 0xFF5A5A62;
                g.fill(sx, sy, sx + 18, sy + 1, border);
                g.fill(sx, sy + 17, sx + 18, sy + 18, border);
                g.fill(sx, sy, sx + 1, sy + 18, border);
                g.fill(sx + 17, sy, sx + 18, sy + 18, border);
            }
        }
    }

    private void drawSlot(GuiGraphics g, int x, int y, int size, ItemStack item, int border, boolean showIcon) {
        g.fill(x, y, x + size, y + size, 0xFF232329);
        g.fill(x, y, x + size, y + 1, border);
        g.fill(x, y + size - 1, x + size, y + size, border);
        g.fill(x, y, x + 1, y + size, border);
        g.fill(x + size - 1, y, x + size, y + size, border);
        if (showIcon && !item.isEmpty()) {
            int pad = (size - 16) / 2;
            g.renderItem(item, x + pad, y + pad);
            g.renderItemDecorations(this.font, item, x + pad, y + pad);
        }
    }

    private List<Component> attributeLines(ItemStack stack) {
        AttributeBag overlay = FireworkDataHelper.overlay(stack);
        List<Component> lines = new ArrayList<>(8);
        addAttr(lines, "attr.magic-firework.flight.speed", Component.literal(fmt(Attributes.SPEED.get(overlay))));
        addAttr(lines, "attr.magic-firework.flight.gravity", Component.literal(fmt(Attributes.GRAVITY.get(overlay))));
        addAttr(lines, "attr.magic-firework.flight.drag", Component.literal(fmt(Attributes.DRAG.get(overlay))));
        addAttr(lines, "attr.magic-firework.trail.enabled",
                Component.translatable(Attributes.TRAIL.get(overlay) ? "gui.magic-firework.on" : "gui.magic-firework.off"));
        addAttr(lines, "attr.magic-firework.fuse.ticks",
                Component.literal(Integer.toString(Attributes.FUSE_TICKS.get(overlay))));
        addAttr(lines, "attr.magic-firework.afterglow",
                Component.literal(Integer.toString(Attributes.AFTERGLOW.get(overlay))));
        addAttr(lines, "attr.magic-firework.max.depth",
                Component.literal(Integer.toString(Attributes.MAX_DEPTH.get(overlay))));
        addAttr(lines, "attr.magic-firework.show", Component.literal(showText(overlay)));
        return lines;
    }

    private static void addAttr(List<Component> lines, String labelKey, Component value) {
        lines.add(Component.translatable(labelKey).append(Component.literal(": ")).append(value));
    }

    private static String showText(AttributeBag overlay) {
        Tag show = overlay.raw().get(Attributes.SHOW);
        if (show instanceof StringTag string) {
            return string.getAsString();
        }
        if (show instanceof CompoundTag) {
            return "inline";
        }
        return "—";
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** A surrounding editing category: a label plus the icon drawn in its slot. */
    private record Category(String labelKey, ItemStack icon) {
    }
}

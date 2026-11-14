package com.magic.firework.client.screen;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.blockentity.FireworkCraftingTableBlockEntity;
import com.magic.firework.item.FireworkDataHelper;
import com.magic.firework.network.FireworkNetworking;
import com.magic.firework.screen.FireworkEditorMenu;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The firework editor screen.
 *
 * <p>Both pages live in one screen: the main page shows the firework and the category grid,
 * the flight page shows the speed / flight-time steppers. Switching pages cross-fades
 * between them instead of opening a new screen, so the mouse cursor never jumps.
 */
public class FireworkEditorScreen extends AbstractContainerScreen<FireworkEditorMenu> {
    private static final int IMAGE_W = 380;
    private static final int IMAGE_H = 260;

    /** Category slots: a 4×2 grid to the right of the firework slot. */
    private static final int[] GRID_COL_X = { 180, 214, 248, 282 };
    private static final int[] GRID_ROW_Y = { 74, 122 };
    private static final int SLOT_SIZE = 22;

    private static final int ATTR_X = 40;
    private static final int ATTR_COL_GAP = 192;
    private static final int ATTR_ROW_GAP = 10;

    /** Flight page rows: label, value, then the minus / plus steppers. */
    private static final int ROW_LABEL_X = 176;
    private static final int ROW_VALUE_X = 252;
    private static final int STEP_MINUS_X = 292;
    private static final int STEP_PLUS_X = 316;
    private static final int STEP_BTN_W = 18;
    private static final int STEP_BTN_H = 16;
    private static final int SPEED_ROW_Y = 78;
    private static final int FUSE_ROW_Y = 126;

    private static final int BACK_X = 42;
    private static final int BACK_Y = 150;
    private static final int BACK_W = 44;
    private static final int BACK_H = 20;

    private static final String FLIGHT_LABEL = "screen.magic-firework.editor.slot.flight";

    private static final List<Category> CATEGORIES = List.of(
            new Category("screen.magic-firework.editor.slot.shape", new ItemStack(Items.FIREWORK_STAR)),
            new Category("screen.magic-firework.editor.slot.color", new ItemStack(Items.RED_DYE)),
            new Category("screen.magic-firework.editor.slot.effect", new ItemStack(Items.FIREWORK_ROCKET)),
            new Category(FLIGHT_LABEL, new ItemStack(Items.FEATHER)),
            new Category("screen.magic-firework.editor.slot.fuse", new ItemStack(Items.STRING)),
            new Category("screen.magic-firework.editor.slot.sound", new ItemStack(Items.NOTE_BLOCK)),
            new Category("screen.magic-firework.editor.slot.light", new ItemStack(Items.GLOWSTONE)),
            new Category("screen.magic-firework.editor.slot.trail", new ItemStack(Items.GUNPOWDER)));

    /** Category slot top-left corners, relative to leftPos/topPos. */
    private final int[] catX = new int[CATEGORIES.size()];
    private final int[] catY = new int[CATEGORIES.size()];

    /** 0 → surrounding slots hidden, 1 → fully revealed. */
    private float reveal;

    /** True while the flight page is showing. */
    private boolean flightPage;

    /** 0 → main page, 1 → flight page. Eased each tick for the page transition. */
    private float pageProgress;

    /** Long-press auto-repeat: wait this many ticks before repeating, then this often. */
    private static final int HOLD_DELAY_TICKS = 8;
    private static final int HOLD_REPEAT_TICKS = 2;

    /** The stepper currently held down, or {@code -1}. {@code heldDelta} is +1 or -1. */
    private int heldWhich = -1;
    private int heldDelta;
    private int heldTicks;

    /** Last known cursor position in GUI coordinates, used to cancel a hold that slides off. */
    private double cursorX;
    private double cursorY;

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

        // Taking the firework out ends the flight page; nothing is left to edit on it.
        if (flightPage && this.menu.getCenterStack().isEmpty()) {
            flightPage = false;
        }

        pageProgress = ease(pageProgress, flightPage ? 1.0F : 0.0F);
        reveal = ease(reveal, !flightPage && !this.menu.getCenterStack().isEmpty() ? 1.0F : 0.0F);
        tickHold();
    }

    /**
     * Auto-repeats the held stepper. The hold ends when the page closes, the cursor slides off
     * the button, or the value can no longer move that way (max reached, material used up).
     */
    private void tickHold() {
        if (heldWhich < 0) {
            return;
        }
        if (!flightInteractive() || !isOverHeldButton(this.cursorX, this.cursorY)
                || !stepAvailable(heldWhich, heldDelta)) {
            heldWhich = -1;
            return;
        }
        if (++heldTicks >= HOLD_DELAY_TICKS
                && (heldTicks - HOLD_DELAY_TICKS) % HOLD_REPEAT_TICKS == 0) {
            sendAdjust(heldWhich, heldDelta);
        }
    }

    /** Eases {@code current} toward {@code target}; snaps once the remaining distance is tiny. */
    private static float ease(float current, float target) {
        float next = current + (target - current) * 0.25F;
        return Math.abs(target - next) < 0.01F ? target : next;
    }

    /** The main page fades out over the first half of the transition. */
    private float mainAlpha() {
        return Math.max(0.0F, 1.0F - pageProgress * 2.0F);
    }

    /** The flight page fades in over the second half, so the two pages never overlap. */
    private float flightAlpha() {
        return Math.max(0.0F, pageProgress * 2.0F - 1.0F);
    }

    private boolean flightInteractive() {
        return flightPage && pageProgress > 0.9F;
    }

    private boolean mainInteractive() {
        return !flightPage && pageProgress < 0.1F;
    }

    private int categorySlide() {
        return Math.round((1.0F - mainAlpha()) * 12.0F);
    }

    private int flightSlide() {
        return Math.round((1.0F - flightAlpha()) * 12.0F);
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

        if (mainAlpha() > 0.02F) {
            renderMainPage(g, x, y, center, mouseX, mouseY);
        }
        if (flightAlpha() > 0.02F) {
            renderFlightPage(g, x, y, mouseX, mouseY);
        }
    }

    private void renderMainPage(GuiGraphics g, int x, int y, ItemStack center, int mouseX, int mouseY) {
        float alpha = mainAlpha();
        int a = (int) (alpha * 255);

        if (center.isEmpty()) {
            if (alpha > 0.5F) {
                g.drawCenteredString(this.font, Component.translatable("screen.magic-firework.editor.place_hint"),
                        x + 190, y + 110, (a << 24) | 0xE0E0E0);
                g.drawCenteredString(this.font, Component.translatable("screen.magic-firework.editor.reveal_hint"),
                        x + 190, y + 124, (a << 24) | 0x8B8B8B);
            }
            return;
        }
        if (reveal <= 0.02F) {
            return;
        }
        int slide = categorySlide();
        int slotAlpha = (int) (reveal * alpha * 255);
        boolean clickable = mainInteractive() && reveal > 0.5F;
        for (int i = 0; i < CATEGORIES.size(); i++) {
            boolean hover = clickable && isHovering(catX[i] + slide, catY[i], SLOT_SIZE, SLOT_SIZE, mouseX, mouseY);
            int border = hover ? 0xFFFFFFFF : ((slotAlpha << 24) | 0x9A9AA4);
            drawSlot(g, x + catX[i] + slide, y + catY[i], SLOT_SIZE, CATEGORIES.get(i).icon(), border, reveal >= 0.4F);
            g.drawCenteredString(this.font, Component.translatable(CATEGORIES.get(i).labelKey()),
                    x + catX[i] + slide + SLOT_SIZE / 2, y + catY[i] + SLOT_SIZE + 3,
                    hover ? 0xFFFFFFFF : ((slotAlpha << 24) | 0xB0B0B0));
        }
    }

    private void renderFlightPage(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int alpha = (int) (flightAlpha() * 255);
        int slide = flightSlide();
        int labelColor = (alpha << 24) | 0xC8C8C8;
        int hintColor = (alpha << 24) | 0x8B8B8B;
        int valueColor = (alpha << 24) | 0xFFFFFF;

        float speed = this.menu.displayedSpeed();
        int fuse = this.menu.displayedFuse();

        g.drawString(this.font, Component.translatable("screen.magic-firework.flight.speed"),
                x + ROW_LABEL_X + slide, y + SPEED_ROW_Y, labelColor);
        g.drawString(this.font, Component.translatable("screen.magic-firework.flight.speed_hint"),
                x + ROW_LABEL_X + slide, y + SPEED_ROW_Y + 13, hintColor);
        g.drawString(this.font, String.format(Locale.ROOT, "%.2f", speed),
                x + ROW_VALUE_X + slide, y + SPEED_ROW_Y, valueColor);
        boolean interactive = flightInteractive();
        boolean speedDown = canDecreaseSpeed();
        boolean speedUp = canIncreaseSpeed();
        drawStepButton(g, x + STEP_MINUS_X + slide, y + SPEED_ROW_Y - 4, "-", speedDown,
                interactive && speedDown && isHovering(STEP_MINUS_X + slide, SPEED_ROW_Y - 4,
                        STEP_BTN_W, STEP_BTN_H, mouseX, mouseY), alpha);
        drawStepButton(g, x + STEP_PLUS_X + slide, y + SPEED_ROW_Y - 4, "+", speedUp,
                interactive && speedUp && isHovering(STEP_PLUS_X + slide, SPEED_ROW_Y - 4,
                        STEP_BTN_W, STEP_BTN_H, mouseX, mouseY), alpha);

        g.drawString(this.font, Component.translatable("screen.magic-firework.flight.fuse"),
                x + ROW_LABEL_X + slide, y + FUSE_ROW_Y, labelColor);
        g.drawString(this.font, Component.translatable("screen.magic-firework.flight.fuse_hint"),
                x + ROW_LABEL_X + slide, y + FUSE_ROW_Y + 13, hintColor);
        g.drawString(this.font, secondsText(fuse),
                x + ROW_VALUE_X + slide, y + FUSE_ROW_Y, valueColor);
        boolean fuseDown = canDecreaseFuse();
        boolean fuseUp = canIncreaseFuse();
        drawStepButton(g, x + STEP_MINUS_X + slide, y + FUSE_ROW_Y - 4, "-", fuseDown,
                interactive && fuseDown && isHovering(STEP_MINUS_X + slide, FUSE_ROW_Y - 4,
                        STEP_BTN_W, STEP_BTN_H, mouseX, mouseY), alpha);
        drawStepButton(g, x + STEP_PLUS_X + slide, y + FUSE_ROW_Y - 4, "+", fuseUp,
                interactive && fuseUp && isHovering(STEP_PLUS_X + slide, FUSE_ROW_Y - 4,
                        STEP_BTN_W, STEP_BTN_H, mouseX, mouseY), alpha);

        boolean backHover = interactive && isHovering(BACK_X, BACK_Y, BACK_W, BACK_H, mouseX, mouseY);
        drawTextButton(g, x + BACK_X, y + BACK_Y, BACK_W, BACK_H,
                Component.translatable("screen.magic-firework.flight.back"), backHover, alpha);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Labels are drawn in renderBg at absolute coordinates.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.cursorX = mouseX;
            this.cursorY = mouseY;
            if (flightInteractive()) {
                if (pressStep(mouseX, mouseY)) {
                    return true;
                }
                if (isHovering(BACK_X, BACK_Y, BACK_W, BACK_H, mouseX, mouseY)) {
                    flightPage = false;
                    return true;
                }
            }
            if (mainInteractive() && reveal > 0.5F) {
                int slide = categorySlide();
                for (int i = 0; i < CATEGORIES.size(); i++) {
                    if (isHovering(catX[i] + slide, catY[i], SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
                        openCategory(CATEGORIES.get(i));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            heldWhich = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.cursorX = mouseX;
        this.cursorY = mouseY;
        super.mouseMoved(mouseX, mouseY);
    }

    /** A press on a stepper applies one step at once and arms the hold. */
    private boolean pressStep(double mouseX, double mouseY) {
        int slide = flightSlide();
        if (tryStep(mouseX, mouseY, FireworkEditorMenu.ADJUST_SPEED, SPEED_ROW_Y, slide,
                canDecreaseSpeed(), canIncreaseSpeed())) {
            return true;
        }
        return tryStep(mouseX, mouseY, FireworkEditorMenu.ADJUST_FUSE, FUSE_ROW_Y, slide,
                canDecreaseFuse(), canIncreaseFuse());
    }

    private boolean tryStep(double mouseX, double mouseY, int which, int rowY, int slide,
            boolean canDecrease, boolean canIncrease) {
        int top = rowY - 4;
        if (canDecrease && isHovering(STEP_MINUS_X + slide, top, STEP_BTN_W, STEP_BTN_H, mouseX, mouseY)) {
            beginHold(which, -1);
            return true;
        }
        if (canIncrease && isHovering(STEP_PLUS_X + slide, top, STEP_BTN_W, STEP_BTN_H, mouseX, mouseY)) {
            beginHold(which, 1);
            return true;
        }
        return false;
    }

    private void beginHold(int which, int delta) {
        heldWhich = which;
        heldDelta = delta;
        heldTicks = 0;
        sendAdjust(which, delta);
    }

    private boolean isOverHeldButton(double mouseX, double mouseY) {
        int rowY = heldWhich == FireworkEditorMenu.ADJUST_SPEED ? SPEED_ROW_Y : FUSE_ROW_Y;
        int bx = (heldDelta > 0 ? STEP_PLUS_X : STEP_MINUS_X) + flightSlide();
        return isHovering(bx, rowY - 4, STEP_BTN_W, STEP_BTN_H, mouseX, mouseY);
    }

    private boolean stepAvailable(int which, int delta) {
        if (which == FireworkEditorMenu.ADJUST_SPEED) {
            return delta > 0 ? canIncreaseSpeed() : canDecreaseSpeed();
        }
        return delta > 0 ? canIncreaseFuse() : canDecreaseFuse();
    }

    private void sendAdjust(int which, int delta) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(which);
        buf.writeByte(delta);
        ClientPlayNetworking.send(FireworkNetworking.ADJUST_FLIGHT_CHANNEL, buf);
    }

    // ── stepper availability ─────────────────────────────────────────────────

    private boolean canDecreaseSpeed() {
        return this.menu.displayedSpeed() > 1.0E-4F;
    }

    private boolean canIncreaseSpeed() {
        return this.menu.displayedSpeed() < Attributes.SPEED.max() - 1.0E-4F
                && hasMaterial(FireworkCraftingTableBlockEntity.SPEED_MATERIAL);
    }

    private boolean canDecreaseFuse() {
        return this.menu.displayedFuse() > 0;
    }

    private boolean canIncreaseFuse() {
        return this.menu.displayedFuse() < Attributes.FUSE_TICKS.max()
                && hasMaterial(FireworkCraftingTableBlockEntity.FUSE_MATERIAL);
    }

    /** Creative players can raise a value without holding the material. */
    private boolean hasMaterial(Item item) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (this.minecraft.player.getAbilities().instabuild) {
            return true;
        }
        Inventory inventory = this.minecraft.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    private void renderCategoryTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (!mainInteractive() || reveal <= 0.5F || mainAlpha() < 0.5F) {
            return;
        }
        int slide = categorySlide();
        for (int i = 0; i < CATEGORIES.size(); i++) {
            if (isHovering(catX[i] + slide, catY[i], SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
                g.renderTooltip(this.font, Component.translatable(CATEGORIES.get(i).labelKey()), mouseX, mouseY);
                break;
            }
        }
    }

    /** Opens a category sub-page. Only the flight page is implemented so far. */
    private void openCategory(Category category) {
        if (category.labelKey().equals(FLIGHT_LABEL)) {
            flightPage = true;
        }
        // 其他分类的编辑界面尚未设计。
    }

    // ── rendering helpers ─────────────────────────────────────────────────────

    private void layout() {
        for (int i = 0; i < CATEGORIES.size(); i++) {
            catX[i] = GRID_COL_X[i % GRID_COL_X.length];
            catY[i] = GRID_ROW_Y[i / GRID_COL_X.length];
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

    /** A small square stepper button; disabled buttons are dimmed and ignore clicks. */
    private void drawStepButton(GuiGraphics g, int x, int y, String label, boolean enabled, boolean hover, int alpha) {
        int fill = !enabled ? 0xFF1E1E24 : (hover ? 0xFF3A3A44 : 0xFF2A2A32);
        int border = !enabled ? ((alpha << 24) | 0x4A4A52) : (hover ? 0xFFFFFFFF : ((alpha << 24) | 0x6A6A72));
        int text = !enabled ? ((alpha << 24) | 0x6A6A72) : (hover ? 0xFFFFFFFF : ((alpha << 24) | 0xD0D0D0));
        g.fill(x, y, x + STEP_BTN_W, y + STEP_BTN_H, fill);
        g.fill(x, y, x + STEP_BTN_W, y + 1, border);
        g.fill(x, y + STEP_BTN_H - 1, x + STEP_BTN_W, y + STEP_BTN_H, border);
        g.fill(x, y, x + 1, y + STEP_BTN_H, border);
        g.fill(x + STEP_BTN_W - 1, y, x + STEP_BTN_W, y + STEP_BTN_H, border);
        g.drawCenteredString(this.font, label, x + STEP_BTN_W / 2, y + (STEP_BTN_H - 8) / 2, text);
    }

    private void drawTextButton(GuiGraphics g, int x, int y, int w, int h, Component label, boolean hover, int alpha) {
        int fill = hover ? 0xFF3A3A44 : 0xFF2A2A32;
        int border = hover ? 0xFFFFFFFF : ((alpha << 24) | 0x6A6A72);
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
        g.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2, hover ? 0xFFFFFFFF : ((alpha << 24) | 0xD0D0D0));
    }

    private List<Component> attributeLines(ItemStack stack) {
        AttributeBag overlay = FireworkDataHelper.overlay(stack);
        List<Component> lines = new ArrayList<>(8);
        // Speed and flight time come from the menu's synced values, which include the profile
        // defaults — the overlay alone would show the generic default before any edit.
        addAttr(lines, "attr.magic-firework.flight.speed", Component.literal(fmt(this.menu.displayedSpeed())));
        addAttr(lines, "attr.magic-firework.flight.gravity", Component.literal(fmt(Attributes.GRAVITY.get(overlay))));
        addAttr(lines, "attr.magic-firework.flight.drag", Component.literal(fmt(Attributes.DRAG.get(overlay))));
        addAttr(lines, "attr.magic-firework.trail.enabled",
                Component.translatable(Attributes.TRAIL.get(overlay) ? "gui.magic-firework.on" : "gui.magic-firework.off"));
        addAttr(lines, "attr.magic-firework.fuse.ticks",
                Component.literal(secondsText(this.menu.displayedFuse())));
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

    /** Flight time is stored in ticks but shown to the player in seconds (20 ticks = 1 s). */
    private static String secondsText(int ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20))
                .stripTrailingZeros().toPlainString() + "s";
    }

    /** A surrounding editing category: a label plus the icon drawn in its slot. */
    private record Category(String labelKey, ItemStack icon) {
    }
}

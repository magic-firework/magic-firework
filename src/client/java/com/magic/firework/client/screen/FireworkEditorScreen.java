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
 * <p>Every page lives in one screen: the main page shows the firework and the category grid, the
 * flight page shows the speed / flight-time steppers, and the direction page shows two dials plus
 * a single preview arrow for the resulting spherical direction. Switching pages cross-fades
 * instead of opening a new screen, so the mouse cursor never jumps.
 */
public class FireworkEditorScreen extends AbstractContainerScreen<FireworkEditorMenu> {
    private enum Page {
        MAIN,
        FLIGHT,
        DIRECTION
    }

    /** Long-press channel: which packet a held stepper keeps sending. */
    private static final int CHANNEL_FLIGHT = 0;
    private static final int CHANNEL_DIRECTION = 1;

    private static final int IMAGE_W = 380;
    private static final int IMAGE_H = 260;

    /** Category slots: a 4×2 grid to the right of the firework slot. */
    private static final int[] GRID_COL_X = { 180, 214, 248, 282 };
    private static final int[] GRID_ROW_Y = { 74, 122 };
    private static final int SLOT_SIZE = 22;

    private static final int ATTR_X = 40;
    private static final int ATTR_COL_GAP = 192;
    private static final int ATTR_ROW_GAP = 10;

    /** Sub-page rows: label, value, then the minus / plus steppers. */
    private static final int ROW_LABEL_X = 176;
    private static final int ROW_VALUE_X = 252;
    private static final int STEP_MINUS_X = 292;
    private static final int STEP_PLUS_X = 316;
    private static final int STEP_BTN_W = 18;
    private static final int STEP_BTN_H = 16;
    private static final int ROW1_Y = 78;
    private static final int ROW2_Y = 126;

    /** Direction preview: one arrow from the origin, on a fixed-radius sphere. */
    private static final int ORIGIN_X = 128;
    private static final int ORIGIN_Y = 118;
    private static final int ARROW_RADIUS = 36;

    /** Degrees per direction-dial press, shared with the server-side validation. */
    private static final int DIRECTION_STEP = FireworkCraftingTableBlockEntity.DIRECTION_STEP_DEGREES;

    private static final int BACK_X = 42;
    private static final int BACK_Y = 150;
    private static final int BACK_W = 44;
    private static final int BACK_H = 20;

    private static final String FLIGHT_LABEL = "screen.magic-firework.editor.slot.flight";
    private static final String DIRECTION_LABEL = "screen.magic-firework.editor.slot.direction";

    private static final List<Category> CATEGORIES = List.of(
            new Category("screen.magic-firework.editor.slot.shape", new ItemStack(Items.FIREWORK_STAR)),
            new Category("screen.magic-firework.editor.slot.color", new ItemStack(Items.RED_DYE)),
            new Category("screen.magic-firework.editor.slot.effect", new ItemStack(Items.FIREWORK_ROCKET)),
            new Category(FLIGHT_LABEL, new ItemStack(Items.FEATHER)),
            new Category(DIRECTION_LABEL, new ItemStack(Items.COMPASS)),
            new Category("screen.magic-firework.editor.slot.sound", new ItemStack(Items.NOTE_BLOCK)),
            new Category("screen.magic-firework.editor.slot.light", new ItemStack(Items.GLOWSTONE)),
            new Category("screen.magic-firework.editor.slot.trail", new ItemStack(Items.GUNPOWDER)));

    /** Category slot top-left corners, relative to leftPos/topPos. */
    private final int[] catX = new int[CATEGORIES.size()];
    private final int[] catY = new int[CATEGORIES.size()];

    /** 0 → surrounding slots hidden, 1 → fully revealed. */
    private float reveal;

    /** Page being navigated to; the preview of the old page stays until the fade ends. */
    private Page page = Page.MAIN;
    private Page shownPage = Page.MAIN;

    /** 0 → main page, 1 → sub-page. Eased each tick for the page transition. */
    private float pageProgress;

    /** Eased arrow angles, so the preview rotates smoothly toward the adjusted value. */
    private float shownYaw;
    private float shownPitch = 90.0F;

    /** Long-press auto-repeat: wait this many ticks before repeating, then this often. */
    private static final int HOLD_DELAY_TICKS = 8;
    private static final int HOLD_REPEAT_TICKS = 2;

    /** The stepper currently held down, or {@code -1}. */
    private int heldChannel = -1;
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

        // Taking the firework out ends the sub-page; nothing is left to edit on it.
        if (page != Page.MAIN && this.menu.getCenterStack().isEmpty()) {
            page = Page.MAIN;
        }

        pageProgress = ease(pageProgress, page == Page.MAIN ? 0.0F : 1.0F);
        if (page == Page.MAIN && pageProgress <= 0.001F) {
            shownPage = Page.MAIN;
        }
        reveal = ease(reveal, page == Page.MAIN && !this.menu.getCenterStack().isEmpty() ? 1.0F : 0.0F);
        shownYaw = ease(shownYaw, this.menu.displayedYaw());
        shownPitch = ease(shownPitch, this.menu.displayedPitch());
        tickHold();
    }

    /**
     * Auto-repeats the held stepper. The hold ends when the page closes, the cursor slides off
     * the button, or the value can no longer move that way (max reached, material used up).
     */
    private void tickHold() {
        if (heldChannel < 0) {
            return;
        }
        if (!subInteractive() || !isOverHeldButton(this.cursorX, this.cursorY)
                || !stepAvailable(heldChannel, heldWhich, heldDelta)) {
            heldChannel = -1;
            heldWhich = -1;
            return;
        }
        if (++heldTicks >= HOLD_DELAY_TICKS
                && (heldTicks - HOLD_DELAY_TICKS) % HOLD_REPEAT_TICKS == 0) {
            sendAdjust(heldChannel, heldWhich, heldDelta);
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

    /** The sub-page fades in over the second half, so the two pages never overlap. */
    private float subAlpha() {
        return Math.max(0.0F, pageProgress * 2.0F - 1.0F);
    }

    private boolean subInteractive() {
        return page != Page.MAIN && pageProgress > 0.9F;
    }

    private boolean mainInteractive() {
        return page == Page.MAIN && pageProgress < 0.1F;
    }

    private int categorySlide() {
        return Math.round((1.0F - mainAlpha()) * 12.0F);
    }

    private int subSlide() {
        return Math.round((1.0F - subAlpha()) * 12.0F);
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
        if (subAlpha() > 0.02F) {
            if (shownPage == Page.DIRECTION) {
                renderDirectionPage(g, x, y, mouseX, mouseY);
            } else {
                renderFlightPage(g, x, y, mouseX, mouseY);
            }
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
        int alpha = (int) (subAlpha() * 255);
        int slide = subSlide();
        int labelColor = (alpha << 24) | 0xC8C8C8;
        int hintColor = (alpha << 24) | 0x8B8B8B;
        int valueColor = (alpha << 24) | 0xFFFFFF;

        float speed = this.menu.displayedSpeed();
        int flight = this.menu.displayedFlight();

        g.drawString(this.font, Component.translatable("screen.magic-firework.flight.speed"),
                x + ROW_LABEL_X + slide, y + ROW1_Y, labelColor);
        g.drawString(this.font, Component.translatable("screen.magic-firework.flight.speed_hint"),
                x + ROW_LABEL_X + slide, y + ROW1_Y + 13, hintColor);
        g.drawString(this.font, String.format(Locale.ROOT, "%.2f", speed),
                x + ROW_VALUE_X + slide, y + ROW1_Y, valueColor);
        boolean interactive = subInteractive();
        boolean speedDown = canDecreaseSpeed();
        boolean speedUp = canIncreaseSpeed();
        drawStepButton(g, x + STEP_MINUS_X + slide, y + ROW1_Y - 4, "-", speedDown,
                interactive && speedDown && isHovering(STEP_MINUS_X + slide, ROW1_Y - 4,
                        STEP_BTN_W, STEP_BTN_H, mouseX, mouseY), alpha);
        drawStepButton(g, x + STEP_PLUS_X + slide, y + ROW1_Y - 4, "+", speedUp,
                interactive && speedUp && isHovering(STEP_PLUS_X + slide, ROW1_Y - 4,
                        STEP_BTN_W, STEP_BTN_H, mouseX, mouseY), alpha);

        g.drawString(this.font, Component.translatable("screen.magic-firework.flight.time"),
                x + ROW_LABEL_X + slide, y + ROW2_Y, labelColor);
        g.drawString(this.font, Component.translatable("screen.magic-firework.flight.time_hint"),
                x + ROW_LABEL_X + slide, y + ROW2_Y + 13, hintColor);
        g.drawString(this.font, secondsText(flight),
                x + ROW_VALUE_X + slide, y + ROW2_Y, valueColor);
        boolean flightDown = canDecreaseFlight();
        boolean flightUp = canIncreaseFlight();
        drawStepButton(g, x + STEP_MINUS_X + slide, y + ROW2_Y - 4, "-", flightDown,
                interactive && flightDown && isHovering(STEP_MINUS_X + slide, ROW2_Y - 4,
                        STEP_BTN_W, STEP_BTN_H, mouseX, mouseY), alpha);
        drawStepButton(g, x + STEP_PLUS_X + slide, y + ROW2_Y - 4, "+", flightUp,
                interactive && flightUp && isHovering(STEP_PLUS_X + slide, ROW2_Y - 4,
                        STEP_BTN_W, STEP_BTN_H, mouseX, mouseY), alpha);

        drawBackButton(g, x, y, alpha, interactive, mouseX, mouseY);
    }

    private void renderDirectionPage(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int alpha = (int) (subAlpha() * 255);
        int slide = subSlide();
        int labelColor = (alpha << 24) | 0xC8C8C8;
        int valueColor = (alpha << 24) | 0xFFFFFF;

        drawDirectionArrow(g, x, y, alpha);

        g.drawString(this.font, Component.translatable("screen.magic-firework.direction.horizontal"),
                x + ROW_LABEL_X + slide, y + ROW1_Y, labelColor);
        g.drawString(this.font, degreesText(this.menu.displayedYaw()),
                x + ROW_VALUE_X + slide, y + ROW1_Y, valueColor);
        boolean interactive = subInteractive();
        boolean yawDown = canDecreaseYaw();
        boolean yawUp = canIncreaseYaw();
        drawStepButton(g, x + STEP_MINUS_X + slide, y + ROW1_Y - 4, "-", yawDown,
                interactive && yawDown && isHovering(STEP_MINUS_X + slide, ROW1_Y - 4,
                        STEP_BTN_W, STEP_BTN_H, mouseX, mouseY), alpha);
        drawStepButton(g, x + STEP_PLUS_X + slide, y + ROW1_Y - 4, "+", yawUp,
                interactive && yawUp && isHovering(STEP_PLUS_X + slide, ROW1_Y - 4,
                        STEP_BTN_W, STEP_BTN_H, mouseX, mouseY), alpha);

        g.drawString(this.font, Component.translatable("screen.magic-firework.direction.vertical"),
                x + ROW_LABEL_X + slide, y + ROW2_Y, labelColor);
        g.drawString(this.font, degreesText(this.menu.displayedPitch()),
                x + ROW_VALUE_X + slide, y + ROW2_Y, valueColor);
        boolean pitchDown = canDecreasePitch();
        boolean pitchUp = canIncreasePitch();
        drawStepButton(g, x + STEP_MINUS_X + slide, y + ROW2_Y - 4, "-", pitchDown,
                interactive && pitchDown && isHovering(STEP_MINUS_X + slide, ROW2_Y - 4,
                        STEP_BTN_W, STEP_BTN_H, mouseX, mouseY), alpha);
        drawStepButton(g, x + STEP_PLUS_X + slide, y + ROW2_Y - 4, "+", pitchUp,
                interactive && pitchUp && isHovering(STEP_PLUS_X + slide, ROW2_Y - 4,
                        STEP_BTN_W, STEP_BTN_H, mouseX, mouseY), alpha);

        drawBackButton(g, x, y, alpha, interactive, mouseX, mouseY);
    }

    private void drawBackButton(GuiGraphics g, int x, int y, int alpha, boolean interactive, int mouseX, int mouseY) {
        boolean backHover = interactive && isHovering(BACK_X, BACK_Y, BACK_W, BACK_H, mouseX, mouseY);
        drawTextButton(g, x + BACK_X, y + BACK_Y, BACK_W, BACK_H,
                Component.translatable("screen.magic-firework.editor.back"), backHover, alpha);
    }

    /**
     * One arrow for the spherical direction. Both angles only choose where it points; it always
     * has the same length, so it shows direction without implying a magnitude.
     */
    private void drawDirectionArrow(GuiGraphics g, int x, int y, int alpha) {
        int ox = x + ORIGIN_X;
        int oy = y + ORIGIN_Y;
        int guide = ((alpha / 4) << 24) | 0x9A9AA4;

        drawAxes(g, ox, oy, alpha);
        // Fixed horizontal circle, plus a vertical circle that turns with the arrow's azimuth.
        drawEquator(g, ox, oy, guide);
        drawMeridian(g, ox, oy, Math.toRadians(this.shownYaw), guide);

        double yaw = Math.toRadians(this.shownYaw);
        double polar = Math.toRadians(this.shownPitch);
        double horizontal = Math.sin(polar);
        double vx = horizontal * Math.cos(yaw);
        double vy = Math.cos(polar);
        double vz = horizontal * Math.sin(yaw);

        int tx = ox + (int) Math.round(projectX(vx, vz) * ARROW_RADIUS);
        int ty = oy + (int) Math.round(projectY(vx, vy, vz) * ARROW_RADIUS);
        drawArrow(g, ox, oy, tx, ty, (alpha << 24) | 0x4DD0E1);

        g.fill(ox - 1, oy - 1, ox + 2, oy + 2, (alpha << 24) | 0xFFFFFF);
    }

    /**
     * The 3D coordinate reference, drawn in the editor's Z-up frame: X and Y span the horizontal
     * plane, Z points up. The item still stores the launch vector in Minecraft's Y-up axes, where
     * the editor's Y is {@code z} and its Z is {@code y}.
     */
    private void drawAxes(GuiGraphics g, int ox, int oy, int alpha) {
        int a = (int) (alpha * 0.7F);
        drawAxis(g, ox, oy, 1.0, 0.0, 0.0, (a << 24) | 0xE57373, "X", 3, -3);
        drawAxis(g, ox, oy, 0.0, 0.0, 1.0, (a << 24) | 0x81C784, "Y", -9, -3);
        drawAxis(g, ox, oy, 0.0, 1.0, 0.0, (a << 24) | 0x64B5F6, "Z", 3, -8);
    }

    private void drawAxis(GuiGraphics g, int ox, int oy, double x, double y, double z, int color,
            String label, int labelX, int labelY) {
        int tx = ox + (int) Math.round(projectX(x, z) * ARROW_RADIUS);
        int ty = oy + (int) Math.round(projectY(x, y, z) * ARROW_RADIUS);
        drawLine(g, ox, oy, tx, ty, color);
        g.drawString(this.font, label, tx + labelX, ty + labelY, color);
    }

    /** The fixed horizontal reference circle. */
    private static void drawEquator(GuiGraphics g, int ox, int oy, int color) {
        int steps = 48;
        for (int i = 0; i < steps; i++) {
            double angle = i * Math.PI * 2.0 / steps;
            plot(g, ox, oy, Math.cos(angle), 0.0, Math.sin(angle), color);
        }
    }

    /**
     * The vertical reference circle, in the plane that contains the arrow's horizontal direction,
     * so it swings around the vertical axis together with the arrow.
     */
    private static void drawMeridian(GuiGraphics g, int ox, int oy, double yawRadians, int color) {
        double ux = Math.cos(yawRadians);
        double uz = Math.sin(yawRadians);
        int steps = 48;
        for (int i = 0; i < steps; i++) {
            double angle = i * Math.PI * 2.0 / steps;
            double horizontal = Math.cos(angle);
            plot(g, ox, oy, ux * horizontal, Math.sin(angle), uz * horizontal, color);
        }
    }

    private static void plot(GuiGraphics g, int ox, int oy, double x, double y, double z, int color) {
        int px = ox + (int) Math.round(projectX(x, z) * ARROW_RADIUS);
        int py = oy + (int) Math.round(projectY(x, y, z) * ARROW_RADIUS);
        g.fill(px, py, px + 1, py + 1, color);
    }

    /** Isometric-ish camera: +X down-right, +Z down-left, +Y up. */
    private static double projectX(double x, double z) {
        return 0.70710678 * (x - z);
    }

    private static double projectY(double x, double y, double z) {
        return 0.35355339 * (x + z) - 0.8660254 * y;
    }

    private static void drawArrow(GuiGraphics g, int ox, int oy, int tx, int ty, int color) {
        drawLine(g, ox, oy, tx, ty, color);
        double angle = Math.atan2(ty - oy, tx - ox);
        double head = 7.0;
        for (double spread : new double[] { Math.PI - 0.45, Math.PI + 0.45 }) {
            int hx = tx + (int) Math.round(Math.cos(angle + spread) * head);
            int hy = ty + (int) Math.round(Math.sin(angle + spread) * head);
            drawLine(g, tx, ty, hx, hy, color);
        }
    }

    private static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) {
            g.fill(x0, y0, x0 + 2, y0 + 2, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int px = Math.round(x0 + (x1 - x0) * t);
            int py = Math.round(y0 + (y1 - y0) * t);
            g.fill(px, py, px + 2, py + 2, color);
        }
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
            if (subInteractive()) {
                if (shownPage == Page.DIRECTION ? pressDirectionStep(mouseX, mouseY) : pressFlightStep(mouseX, mouseY)) {
                    return true;
                }
                if (isHovering(BACK_X, BACK_Y, BACK_W, BACK_H, mouseX, mouseY)) {
                    page = Page.MAIN;
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
            heldChannel = -1;
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

    /** A press on a flight stepper applies one step at once and arms the hold. */
    private boolean pressFlightStep(double mouseX, double mouseY) {
        int slide = subSlide();
        if (tryStep(mouseX, mouseY, CHANNEL_FLIGHT, FireworkEditorMenu.ADJUST_SPEED, ROW1_Y, slide,
                canDecreaseSpeed(), canIncreaseSpeed(), 1)) {
            return true;
        }
        return tryStep(mouseX, mouseY, CHANNEL_FLIGHT, FireworkEditorMenu.ADJUST_FLIGHT_TIME, ROW2_Y, slide,
                canDecreaseFlight(), canIncreaseFlight(), 1);
    }

    /** A press on a direction stepper turns one dial by {@link #DIRECTION_STEP}. */
    private boolean pressDirectionStep(double mouseX, double mouseY) {
        int slide = subSlide();
        if (tryStep(mouseX, mouseY, CHANNEL_DIRECTION, FireworkEditorMenu.ADJUST_YAW, ROW1_Y, slide,
                canDecreaseYaw(), canIncreaseYaw(), DIRECTION_STEP)) {
            return true;
        }
        return tryStep(mouseX, mouseY, CHANNEL_DIRECTION, FireworkEditorMenu.ADJUST_PITCH, ROW2_Y, slide,
                canDecreasePitch(), canIncreasePitch(), DIRECTION_STEP);
    }

    private boolean tryStep(double mouseX, double mouseY, int channel, int which, int rowY, int slide,
            boolean canDecrease, boolean canIncrease, int step) {
        int top = rowY - 4;
        if (canDecrease && isHovering(STEP_MINUS_X + slide, top, STEP_BTN_W, STEP_BTN_H, mouseX, mouseY)) {
            beginHold(channel, which, -step);
            return true;
        }
        if (canIncrease && isHovering(STEP_PLUS_X + slide, top, STEP_BTN_W, STEP_BTN_H, mouseX, mouseY)) {
            beginHold(channel, which, step);
            return true;
        }
        return false;
    }

    private void beginHold(int channel, int which, int delta) {
        heldChannel = channel;
        heldWhich = which;
        heldDelta = delta;
        heldTicks = 0;
        sendAdjust(channel, which, delta);
    }

    private boolean isOverHeldButton(double mouseX, double mouseY) {
        int rowY;
        if (heldChannel == CHANNEL_FLIGHT) {
            rowY = heldWhich == FireworkEditorMenu.ADJUST_FLIGHT_TIME ? ROW2_Y : ROW1_Y;
        } else {
            rowY = heldWhich == FireworkEditorMenu.ADJUST_YAW ? ROW1_Y : ROW2_Y;
        }
        int bx = (heldDelta > 0 ? STEP_PLUS_X : STEP_MINUS_X) + subSlide();
        return isHovering(bx, rowY - 4, STEP_BTN_W, STEP_BTN_H, mouseX, mouseY);
    }

    private boolean stepAvailable(int channel, int which, int delta) {
        if (channel == CHANNEL_FLIGHT) {
            if (which == FireworkEditorMenu.ADJUST_FLIGHT_TIME) {
                return delta > 0 ? canIncreaseFlight() : canDecreaseFlight();
            }
            return delta > 0 ? canIncreaseSpeed() : canDecreaseSpeed();
        }
        if (which == FireworkEditorMenu.ADJUST_YAW) {
            return delta > 0 ? canIncreaseYaw() : canDecreaseYaw();
        }
        return delta > 0 ? canIncreasePitch() : canDecreasePitch();
    }

    private void sendAdjust(int channel, int which, int delta) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(which);
        buf.writeByte(delta);
        ClientPlayNetworking.send(channel == CHANNEL_FLIGHT
                ? FireworkNetworking.ADJUST_FLIGHT_CHANNEL
                : FireworkNetworking.ADJUST_DIRECTION_CHANNEL, buf);
    }

    // ── stepper availability ─────────────────────────────────────────────────

    private boolean canDecreaseSpeed() {
        return this.menu.displayedSpeed() > 1.0E-4F;
    }

    private boolean canIncreaseSpeed() {
        return this.menu.displayedSpeed() < Attributes.SPEED.max() - 1.0E-4F
                && hasMaterial(FireworkCraftingTableBlockEntity.SPEED_MATERIAL);
    }

    private boolean canDecreaseFlight() {
        return this.menu.displayedFlight() > 0;
    }

    private boolean canIncreaseFlight() {
        return this.menu.displayedFlight() < Attributes.FLIGHT_TICKS.max()
                && hasMaterial(FireworkCraftingTableBlockEntity.FLIGHT_MATERIAL);
    }

    private boolean canDecreaseYaw() {
        return this.menu.displayedYaw() > 0.01F;
    }

    private boolean canIncreaseYaw() {
        return this.menu.displayedYaw() < 359.99F;
    }

    private boolean canDecreasePitch() {
        return this.menu.displayedPitch() > 0.01F;
    }

    private boolean canIncreasePitch() {
        return this.menu.displayedPitch() < 359.99F;
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

    /** Opens a category sub-page. Flight and direction are implemented so far. */
    private void openCategory(Category category) {
        String key = category.labelKey();
        if (key.equals(FLIGHT_LABEL)) {
            page = Page.FLIGHT;
        } else if (key.equals(DIRECTION_LABEL)) {
            page = Page.DIRECTION;
            // Start the preview at the stored angles instead of spinning in from zero.
            this.shownYaw = this.menu.displayedYaw();
            this.shownPitch = this.menu.displayedPitch();
        } else {
            // 其他分类的编辑界面尚未设计。
            return;
        }
        shownPage = page;
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
        addAttr(lines, "attr.magic-firework.trail.enabled",
                Component.translatable(Attributes.TRAIL.get(overlay) ? "gui.magic-firework.on" : "gui.magic-firework.off"));
        addAttr(lines, "attr.magic-firework.flight.ticks",
                Component.literal(secondsText(this.menu.displayedFlight())));
        addAttr(lines, "attr.magic-firework.afterglow",
                Component.literal(Integer.toString(Attributes.AFTERGLOW.get(overlay))));
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

    private static String degreesText(float degrees) {
        return Math.round(degrees) + "°";
    }

    /** A surrounding editing category: a label plus the icon drawn in its slot. */
    private record Category(String labelKey, ItemStack icon) {
    }
}

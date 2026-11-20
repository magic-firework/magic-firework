package com.magic.firework.client.screen;

import com.magic.firework.network.FireworkNetworking;
import com.magic.firework.screen.FireworkEditorMenu;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * The firework crafting table's editor: page state, input and hold-repeat. Drawing lives in
 * {@link EditorRenderer}, geometry in {@link EditorLayout}, stepper bounds in
 * {@link EditorSteps}.
 */
public class FireworkEditorScreen extends AbstractContainerScreen<FireworkEditorMenu> {
	enum Page {
		MAIN, FLIGHT, DIRECTION
	}

	private static final int HOLD_DELAY_TICKS = 8;
	private static final int HOLD_REPEAT_TICKS = 2;

	private final EditorRenderer renderer = new EditorRenderer(this);
	private final DirectionPreview direction = new DirectionPreview();

	/** 0 → surrounding slots hidden, 1 → fully revealed. */
	private float reveal;

	/** Page being navigated to; the preview of the old page stays until the fade ends. */
	private Page page = Page.MAIN;
	private Page shownPage = Page.MAIN;

	/** 0 → main page, 1 → sub-page. Eased each tick for the page transition. */
	private float pageProgress;

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
		this.imageWidth = EditorLayout.IMAGE_W;
		this.imageHeight = EditorLayout.IMAGE_H;
		super.init();
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
		direction.tick(this.menu.displayedYaw(), this.menu.displayedPitch());
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

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(g);
		super.render(g, mouseX, mouseY, partialTick);
		this.renderTooltip(g, mouseX, mouseY);
		renderer.renderCategoryTooltip(g, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		renderer.renderBg(g, mouseX, mouseY);
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
				if (shownPage == Page.DIRECTION
						? pressDirectionStep(mouseX, mouseY)
						: pressFlightStep(mouseX, mouseY)) {
					return true;
				}
				if (isHovering(EditorLayout.BACK_X, EditorLayout.BACK_Y,
						EditorLayout.BACK_W, EditorLayout.BACK_H, mouseX, mouseY)) {
					page = Page.MAIN;
					return true;
				}
			}
			if (mainInteractive() && reveal > 0.5F) {
				int slide = categorySlide();
				for (int i = 0; i < EditorLayout.CATEGORIES.size(); i++) {
					if (isHovering(EditorLayout.categoryX(i) + slide, EditorLayout.categoryY(i),
							EditorLayout.SLOT_SIZE, EditorLayout.SLOT_SIZE, mouseX, mouseY)) {
						openCategory(EditorLayout.CATEGORIES.get(i));
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

	private boolean pressFlightStep(double mouseX, double mouseY) {
		return tryStep(mouseX, mouseY, EditorSteps.SPEED) || tryStep(mouseX, mouseY, EditorSteps.FLIGHT);
	}

	private boolean pressDirectionStep(double mouseX, double mouseY) {
		return tryStep(mouseX, mouseY, EditorSteps.YAW) || tryStep(mouseX, mouseY, EditorSteps.PITCH);
	}

	private boolean tryStep(double mouseX, double mouseY, EditorSteps step) {
		int top = step.rowY() - 4;
		int slide = subSlide();
		if (step.canDecrease(this.menu)
				&& isHovering(EditorLayout.STEP_MINUS_X + slide, top,
						EditorLayout.STEP_BTN_W, EditorLayout.STEP_BTN_H, mouseX, mouseY)) {
			beginHold(step.channel(), step.which(), -step.amount());
			return true;
		}
		if (step.canIncrease(this.menu, player())
				&& isHovering(EditorLayout.STEP_PLUS_X + slide, top,
						EditorLayout.STEP_BTN_W, EditorLayout.STEP_BTN_H, mouseX, mouseY)) {
			beginHold(step.channel(), step.which(), step.amount());
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
		EditorSteps step = EditorSteps.of(heldChannel, heldWhich);
		if (step == null) {
			return false;
		}
		int bx = (heldDelta > 0 ? EditorLayout.STEP_PLUS_X : EditorLayout.STEP_MINUS_X) + subSlide();
		return isHovering(bx, step.rowY() - 4, EditorLayout.STEP_BTN_W, EditorLayout.STEP_BTN_H, mouseX, mouseY);
	}

	private boolean stepAvailable(int channel, int which, int delta) {
		EditorSteps step = EditorSteps.of(channel, which);
		if (step == null) {
			return false;
		}
		return delta > 0 ? step.canIncrease(this.menu, player()) : step.canDecrease(this.menu);
	}

	private void sendAdjust(int channel, int which, int delta) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeByte(which);
		buf.writeByte(delta);
		ClientPlayNetworking.send(channel == EditorSteps.CHANNEL_FLIGHT
				? FireworkNetworking.ADJUST_FLIGHT_CHANNEL
				: FireworkNetworking.ADJUST_DIRECTION_CHANNEL, buf);
	}

	/** Opens a category sub-page. Flight and direction are implemented so far. */
	private void openCategory(EditorLayout.Category category) {
		String key = category.labelKey();
		if (key.equals(EditorLayout.FLIGHT_LABEL)) {
			page = Page.FLIGHT;
		} else if (key.equals(EditorLayout.DIRECTION_LABEL)) {
			page = Page.DIRECTION;
			direction.snap(this.menu.displayedYaw(), this.menu.displayedPitch());
		} else {
			return;
		}
		shownPage = page;
	}


	FireworkEditorMenu menu() {
		return this.menu;
	}

	Font font() {
		return this.font;
	}

	int left() {
		return this.leftPos;
	}

	int top() {
		return this.topPos;
	}

	Player player() {
		return this.minecraft == null ? null : this.minecraft.player;
	}

	float reveal() {
		return reveal;
	}

	Page shownPage() {
		return shownPage;
	}

	DirectionPreview direction() {
		return direction;
	}

	float mainAlpha() {
		return Math.max(0.0F, 1.0F - pageProgress * 2.0F);
	}

	float subAlpha() {
		return Math.max(0.0F, pageProgress * 2.0F - 1.0F);
	}

	boolean mainInteractive() {
		return page == Page.MAIN && pageProgress < 0.1F;
	}

	boolean subInteractive() {
		return page != Page.MAIN && pageProgress > 0.9F;
	}

	int categorySlide() {
		return Math.round((1.0F - mainAlpha()) * 12.0F);
	}

	int subSlide() {
		return Math.round((1.0F - subAlpha()) * 12.0F);
	}

	boolean over(int x, int y, int width, int height, double mouseX, double mouseY) {
		return isHovering(x, y, width, height, mouseX, mouseY);
	}
}

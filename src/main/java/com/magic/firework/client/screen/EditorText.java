package com.magic.firework.client.screen;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.item.FireworkDataHelper;
import com.magic.firework.screen.FireworkEditorMenu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Text formatting and the attribute lines shown above the editor. */
final class EditorText {
	private EditorText() {
	}

	static List<Component> attributeLines(FireworkEditorMenu menu, ItemStack stack) {
		AttributeBag overlay = FireworkDataHelper.overlay(stack);
		List<Component> lines = new ArrayList<>(8);
		addAttr(lines, "attr.magic-firework.flight.speed", Component.literal(fmt(menu.displayedSpeed())));
		addAttr(lines, "attr.magic-firework.trail.enabled",
				Component.translatable(
						Attributes.TRAIL.get(overlay) ? "gui.magic-firework.on" : "gui.magic-firework.off"));
		addAttr(lines, "attr.magic-firework.flight.ticks",
				Component.literal(secondsText(menu.displayedFlight())));
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
	static String secondsText(int ticks) {
		return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20))
				.stripTrailingZeros().toPlainString() + "s";
	}

	static String degreesText(float degrees) {
		return Math.round(degrees) + "°";
	}
}

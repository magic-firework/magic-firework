package com.magic.firework.item;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.content.DefinitionIds;
import com.magic.firework.content.FireworkProfile;
import com.magic.firework.registry.Definitions;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Item NBT layout.
 *
 * <pre>
 * MagicFirework: {
 *   profile: "magic-firework:ring12",
 *   overlay: { "flight.speed": 0.2, "show": "magic-firework:cube" }   // only what changed
 * }
 * </pre>
 *
 * The {@code Fireworks} tag is left untouched so the same stack is still a legal vanilla
 * firework rocket. Only the overlay is stored per item, so editing the data pack updates
 * existing items; a key a player never touched always follows the profile.
 */
public final class FireworkDataHelper {
	public static final String ROOT = "MagicFirework";
	public static final String PROFILE = "profile";
	public static final String OVERLAY = "overlay";

	private FireworkDataHelper() {
	}

	public static ResourceLocation profileId(ItemStack stack) {
		return profileId(root(stack));
	}

	/** Same read as {@link #profileId(ItemStack)}, for a detached {@code MagicFirework} tag. */
	public static ResourceLocation profileId(CompoundTag source) {
		String value = source == null ? "" : source.getString(PROFILE);
		if (value.isEmpty()) {
			return DefinitionIds.RING12;
		}
		ResourceLocation id = ResourceLocation.tryParse(value);
		return id == null ? DefinitionIds.RING12 : id;
	}

	public static AttributeBag overlay(ItemStack stack) {
		return overlay(root(stack));
	}

	/** Same read as {@link #overlay(ItemStack)}, for a detached {@code MagicFirework} tag. */
	public static AttributeBag overlay(CompoundTag source) {
		return source != null && source.contains(OVERLAY) ? AttributeBag.load(source.get(OVERLAY)) : AttributeBag.EMPTY;
	}

	public static void setProfile(ItemStack stack, ResourceLocation profileId) {
		root(stack).putString(PROFILE, profileId.toString());
	}

	public static void setOverlay(ItemStack stack, AttributeBag overlay) {
		root(stack).put(OVERLAY, overlay.save());
	}

	/** @return a copy of the {@code MagicFirework} tag, or an empty tag when the stack has none */
	public static CompoundTag source(ItemStack stack) {
		CompoundTag source = stack.getTagElement(ROOT);
		return source == null ? new CompoundTag() : source.copy();
	}

	/** Replace the {@code MagicFirework} tag wholesale (used when copying a firework). */
	public static void applySource(ItemStack stack, CompoundTag source) {
		CompoundTag tag = stack.getOrCreateTag();
		tag.put(ROOT, source == null ? new CompoundTag() : source.copy());
	}

	/** Store a show reference on the item: an id string, or a compound for inline content. */
	public static void setShow(ItemStack stack, Tag show) {
		setOverlay(stack, overlay(stack).with(Attributes.SHOW, show));
	}

	public static ItemStack create(Item item, ResourceLocation profileId, AttributeBag overlay) {
		ItemStack stack = new ItemStack(item);
		setProfile(stack, profileId);
		setOverlay(stack, overlay);
		return stack;
	}

	/**
	 * @return the profile id, the profile loaded from content (may be {@code null} when the
	 *         data pack does not provide it) and the per-item overlay
	 */
	public static Resolved resolve(ItemStack stack, Definitions definitions) {
		ResourceLocation id = profileId(stack);
		return new Resolved(id, definitions.get(id), overlay(stack));
	}

	private static CompoundTag root(ItemStack stack) {
		CompoundTag tag = stack.getOrCreateTag();
		if (!tag.contains(ROOT)) {
			tag.put(ROOT, new CompoundTag());
		}
		return tag.getCompound(ROOT);
	}

	public record Resolved(ResourceLocation id, FireworkProfile profile, AttributeBag overlay) {
	}
}

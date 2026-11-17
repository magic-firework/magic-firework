package com.magic.firework.item;

import java.util.List;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.content.DefinitionIds;
import com.magic.firework.content.FireworkProfile;
import com.magic.firework.registry.Definitions;
import com.magic.firework.rocket.RocketSpec;

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
 *   overlay: { "flight.speed": 0.2, "show": "magic-firework:cube" },   // only what changed
 *   children: [                                                        // optional, crafted nesting
 *     { profile: "magic-firework:ring", overlay: { ... }, children: [ ... ] },
 *     ...
 *   ]
 * }
 * </pre>
 *
 * The root tag and every child entry use the same shape, so {@link RocketSpec} reads and writes
 * the whole tree with one pair of methods. Only the overlay is stored per item, so editing the
 * data pack updates existing items; a key a player never touched always follows the profile.
 *
 * <p>The {@code Fireworks} tag is left untouched so the same stack is still a legal vanilla
 * firework rocket.
 */
public final class FireworkDataHelper {
	public static final String ROOT = "MagicFirework";
	public static final String PROFILE = RocketSpec.PROFILE;
	public static final String OVERLAY = RocketSpec.OVERLAY;

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

	/** @return the whole tree this stack launches, clamped to the item nesting limits */
	public static RocketSpec spec(ItemStack stack) {
		return spec(root(stack));
	}

	/** Same read as {@link #spec(ItemStack)}, for a detached {@code MagicFirework} tag. */
	public static RocketSpec spec(CompoundTag source) {
		RocketSpec loaded = RocketSpec.load(source, profileId(source));
		// `source` always carries a fallback id, so the only way to get null back is a tag that
		// is not a compound at all; a plain rocket of the fallback profile is the safe answer.
		return loaded == null ? RocketSpec.of(profileId(source)) : loaded;
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
		return create(item, RocketSpec.of(profileId, overlay));
	}

	public static ItemStack create(Item item, RocketSpec spec) {
		ItemStack stack = new ItemStack(item);
		applySource(stack, spec.save());
		return stack;
	}

	/**
	 * @return the tree the stack launches and the profile loaded from content (may be
	 *         {@code null} when the data pack does not provide it)
	 */
	public static Resolved resolve(ItemStack stack, Definitions definitions) {
		RocketSpec spec = spec(stack);
		return new Resolved(spec, definitions.get(spec.profileId()));
	}

	/** A stack's tree plus the installed profile it names. */
	public record Resolved(RocketSpec spec, FireworkProfile profile) {
		public ResourceLocation id() {
			return spec.profileId();
		}

		public AttributeBag overlay() {
			return spec.overlay();
		}
	}

	private static CompoundTag root(ItemStack stack) {
		CompoundTag tag = stack.getOrCreateTag();
		if (!tag.contains(ROOT)) {
			tag.put(ROOT, new CompoundTag());
		}
		return tag.getCompound(ROOT);
	}
}

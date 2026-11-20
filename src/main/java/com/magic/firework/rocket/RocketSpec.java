package com.magic.firework.rocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.config.FireworkLimits;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * One rocket as data: the profile it runs, the per-instance overlay, and the child rockets its {@code spawn}
 * ops may name by slot. An item carries it in its {@code MagicFirework} tag, an entity keeps it for life,
 * a blueprint records it. Item NBT is forgeable, so {@link #save()} and {@link #load(Tag, ResourceLocation)}
 * are both bounded by {@link FireworkLimits#maxNestingNodes}: one node per level caps the chain length.
 */
public record RocketSpec(ResourceLocation profileId, AttributeBag overlay, List<RocketSpec> children) {
	public static final String PROFILE = "profile";
	public static final String OVERLAY = "overlay";
	public static final String CHILDREN = "children";

	public RocketSpec {
		Objects.requireNonNull(profileId, "a rocket spec needs a profile id");
		overlay = overlay == null ? AttributeBag.EMPTY : overlay;
		children = children == null ? List.of() : List.copyOf(children);
	}

	/** A plain rocket: one profile, an optional overlay, no children. */
	public static RocketSpec of(ResourceLocation profileId, AttributeBag overlay) {
		return new RocketSpec(profileId, overlay, List.of());
	}

	/** A plain rocket with no overlay at all. */
	public static RocketSpec of(ResourceLocation profileId) {
		return of(profileId, AttributeBag.EMPTY);
	}

	/**
	 * @return the child a {@code spawn} op names by slot, or {@code null} when this rocket has
	 *         no such slot. A missing slot is not an error: a program may offer more slots than
	 *         the crafted item fills, and the empty ones simply spawn nothing.
	 */
	public RocketSpec child(int index) {
		return index < 0 || index >= children.size() ? null : children.get(index);
	}

	public CompoundTag save() {
		return write(this, new int[]{FireworkLimits.maxNestingNodes});
	}

	/**
	 * @param fallbackProfile used when the tag names no parseable id, so an item that lost its
	 *                        profile still loads as something; {@code null} makes a missing id
	 *                        drop the whole node instead
	 * @return the spec, or {@code null} when the tag is not a rocket at all
	 */
	public static RocketSpec load(Tag tag, ResourceLocation fallbackProfile) {
		return read(tag, fallbackProfile, new int[]{FireworkLimits.maxNestingNodes});
	}

	public static RocketSpec load(Tag tag) {
		return load(tag, null);
	}

	private static CompoundTag write(RocketSpec spec, int[] nodes) {
		CompoundTag tag = new CompoundTag();
		tag.putString(PROFILE, spec.profileId().toString());
		if (!spec.overlay().isEmpty()) {
			tag.put(OVERLAY, spec.overlay().save());
		}
		nodes[0]--;
		if (nodes[0] > 0 && !spec.children().isEmpty()) {
			ListTag list = new ListTag();
			for (RocketSpec child : spec.children()) {
				if (nodes[0] <= 0) {
					break;
				}
				list.add(write(child, nodes));
			}
			tag.put(CHILDREN, list);
		}
		return tag;
	}

	private static RocketSpec read(Tag tag, ResourceLocation fallbackProfile, int[] nodes) {
		if (!(tag instanceof CompoundTag compound) || nodes[0] <= 0) {
			return null;
		}
		ResourceLocation profileId = profileId(compound, fallbackProfile);
		if (profileId == null) {
			return null;
		}
		nodes[0]--;
		AttributeBag overlay = AttributeBag.load(compound.get(OVERLAY));
		List<RocketSpec> children = List.of();
		if (compound.get(CHILDREN) instanceof ListTag list && !list.isEmpty()) {
			List<RocketSpec> built = new ArrayList<>(Math.min(list.size(), nodes[0]));
			for (int i = 0; i < list.size(); i++) {
				if (nodes[0] <= 0) {
					break;
				}
				RocketSpec child = read(list.get(i), fallbackProfile, nodes);
				if (child != null) {
					built.add(child);
				}
			}
			children = built;
		}
		return new RocketSpec(profileId, overlay, children);
	}

	private static ResourceLocation profileId(CompoundTag compound, ResourceLocation fallbackProfile) {
		String raw = compound.getString(PROFILE).trim();
		if (raw.isEmpty()) {
			return fallbackProfile;
		}
		ResourceLocation parsed = ResourceLocation.tryParse(raw);
		return parsed == null ? fallbackProfile : parsed;
	}
}

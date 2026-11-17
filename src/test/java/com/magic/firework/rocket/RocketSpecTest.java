package com.magic.firework.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.magic.firework.attribute.AttributeBag;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.config.FireworkLimits;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

/** The tree is item data a player can forge, so every read is bounded. */
class RocketSpecTest {
	private static final ResourceLocation ROOT = new ResourceLocation("test", "root");
	private static final ResourceLocation CHILD = new ResourceLocation("test", "child");

	@Test
	void roundTripsProfileOverlayAndChildren() {
		RocketSpec spec = new RocketSpec(ROOT,
				AttributeBag.EMPTY.withFloat(Attributes.SPEED.key(), 0.3F),
				List.of(RocketSpec.of(CHILD)));

		RocketSpec restored = RocketSpec.load(spec.save());

		assertNotNull(restored);
		assertEquals(spec, restored);
	}

	@Test
	void emptyOverlayIsNotWritten() {
		CompoundTag tag = RocketSpec.of(ROOT).save();

		assertTrue(tag.contains(RocketSpec.PROFILE));
		assertTrue(!tag.contains(RocketSpec.OVERLAY), "an untouched attribute must follow the profile");
		assertTrue(!tag.contains(RocketSpec.CHILDREN));
	}

	@Test
	void childBySlotIsNullWhenTheItemIsSmallerThanTheProgram() {
		RocketSpec spec = new RocketSpec(ROOT, AttributeBag.EMPTY, List.of(RocketSpec.of(CHILD)));

		assertEquals(RocketSpec.of(CHILD), spec.child(0));
		assertNull(spec.child(1));
		assertNull(spec.child(-1));
	}

	@Test
	void aTagWithoutAProfileOrFallbackIsNotARocket() {
		assertNull(RocketSpec.load(new CompoundTag()));
		assertNull(RocketSpec.load(new CompoundTag()));
		assertEquals(RocketSpec.of(CHILD), RocketSpec.load(new CompoundTag(), CHILD));
	}

	@Test
	void aForgedDeeperTreeIsTruncatedToTheLimit() {
		CompoundTag tag = chainOfCompounds(FireworkLimits.maxNestingDepth + 5);

		RocketSpec loaded = RocketSpec.load(tag);

		assertNotNull(loaded);
		assertEquals(FireworkLimits.maxNestingDepth, depth(loaded));
	}

	@Test
	void aForgedWiderTreeIsTruncatedToTheNodeLimit() {
		CompoundTag root = new CompoundTag();
		root.putString(RocketSpec.PROFILE, ROOT.toString());
		ListTag children = new ListTag();
		for (int i = 0; i < FireworkLimits.maxNestingNodes + 10; i++) {
			CompoundTag child = new CompoundTag();
			child.putString(RocketSpec.PROFILE, CHILD.toString());
			children.add(child);
		}
		root.put(RocketSpec.CHILDREN, children);

		RocketSpec loaded = RocketSpec.load(root);

		assertNotNull(loaded);
		assertEquals(FireworkLimits.maxNestingNodes, nodes(loaded));
	}

	@Test
	void depthAndNodeCountDescribeTheTree() {
		RocketSpec tree = new RocketSpec(ROOT, AttributeBag.EMPTY, List.of(
				RocketSpec.of(CHILD),
				new RocketSpec(CHILD, AttributeBag.EMPTY, List.of(RocketSpec.of(CHILD)))));

		assertEquals(4, nodes(tree));
		assertEquals(3, depth(tree));
	}

	private static int nodes(RocketSpec spec) {
		int total = 1;
		for (RocketSpec child : spec.children()) {
			total += nodes(child);
		}
		return total;
	}

	private static int depth(RocketSpec spec) {
		int deepest = 0;
		for (RocketSpec child : spec.children()) {
			deepest = Math.max(deepest, depth(child));
		}
		return 1 + deepest;
	}

	/** A chain of {@code levels} rockets, each the only child of the one above it. */
	private static CompoundTag chainOfCompounds(int levels) {
		CompoundTag node = new CompoundTag();
		node.putString(RocketSpec.PROFILE, ROOT.toString());
		for (int i = 1; i < levels; i++) {
			CompoundTag parent = new CompoundTag();
			parent.putString(RocketSpec.PROFILE, ROOT.toString());
			ListTag children = new ListTag();
			children.add(node);
			parent.put(RocketSpec.CHILDREN, children);
			node = parent;
		}
		return node;
	}
}

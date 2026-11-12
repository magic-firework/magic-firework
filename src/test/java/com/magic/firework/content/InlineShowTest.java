package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.magic.firework.core.Budget;
import com.magic.firework.core.Id;
import com.magic.firework.core.Seed;
import com.magic.firework.core.Vec3;
import com.magic.firework.core.eval.Evaluate;
import com.magic.firework.core.eval.Plan;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.Show;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

/**
 * The path an item's inline document takes: NBT in, a baked point stream out. This is the
 * contract the editor will rely on, so it is asserted without a running game.
 */
class InlineShowTest {
	private static final Ink STAR = star();

	private static Ink star() {
		Vec3[] points = new Vec3[5];
		double[] sizes = new double[5];
		for (int i = 0; i < points.length; i++) {
			double angle = Math.PI * 2.0 * i / points.length;
			points[i] = new Vec3(Math.cos(angle), Math.sin(angle), 0.0);
			sizes[i] = 1.0;
		}
		return Ink.of(new Id("test", "star"), points, new int[0], sizes);
	}

	private static CompoundTag document() {
		return (CompoundTag) JsonToNbt.convert(JsonParser.parseString("""
				{
				  "palette": [16720384],
				  "stamps": [
				    { "shape": { "type": "ring", "shape": "test:star", "count": 6, "radius": 2.0 }, "at": 0 },
				    { "shape": { "type": "array", "shape": "test:star", "count": 3, "step": [0.0, 1.0, 0.0] },
				      "at": 5, "duration": 2 }
				  ]
				}
				"""));
	}

	private static Show show(CompoundTag document) throws Exception {
		return TagCodec.parseShow(new Id("magic-firework", "inline"), document, id -> STAR);
	}

	@Test
	void anInlineDocumentBakesIntoPoints() throws Exception {
		Plan plan = Evaluate.evaluate(show(document()), new Seed(1L), new Budget(4096, 64));

		// 6-ring of 5 points, plus 3-array of 5 points over 2 steps.
		assertEquals(6 * 5 + 3 * 5 * 2, plan.stream().size());
	}

	@Test
	void anInlineDocumentIsReproducible() throws Exception {
		Show show = show(document());

		Plan first = Evaluate.evaluate(show, new Seed(7L), new Budget(4096, 64));
		Plan second = Evaluate.evaluate(show, new Seed(7L), new Budget(4096, 64));

		assertEquals(first.stream().size(), second.stream().size());
		for (int i = 0; i < first.stream().size(); i++) {
			assertEquals(first.stream().point(i), second.stream().point(i));
		}
	}

	@Test
	void theFingerprintSurvivesAJsonRoundTrip() throws Exception {
		CompoundTag document = document();
		long fingerprint = new ContentRef.Inline(document).fingerprint();

		CompoundTag reimported = (CompoundTag) JsonToNbt.convert(
				JsonParser.parseString(NbtTree.toJson(document).toString()));

		assertEquals(fingerprint, new ContentRef.Inline(reimported).fingerprint());
	}

	@Test
	void formExpansionRespectsTheBudget() throws Exception {
		Plan plan = Evaluate.evaluate(show(document()), new Seed(1L), new Budget(10, 64));

		assertEquals(10, plan.stream().size());
		assertTrue(plan.stream().isTimeOrdered());
	}
}

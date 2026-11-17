package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.magic.firework.attribute.Attributes;
import com.magic.firework.item.FireworkDataHelper;
import com.magic.firework.rocket.RocketSpec;
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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;

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

private static final String COMMAND_DOCUMENT =
        "{palette:[16776960,16755200,16733525,16777215],stamps:["
        + "{shape:{type:\"ink\",ink:\"magic-firework:ball\",place:{scale:0.55},size:1.4,colorOffset:3},at:0,thickness:0.2,events:[{kind:\"sound\",id:\"minecraft:entity.firework_rocket.large_blast\",volume:2.0,pitch:1.0}]},"
        + "{shape:{type:\"ink\",ink:\"magic-firework:ring16\",place:{scale:2.6},size:0.9,colorOffset:1},at:0,thickness:0.12,jitter:0.04},"
        + "{shape:{type:\"ink\",ink:\"magic-firework:ring16\",place:{rot:[90.0,0.0,0.0],scale:2.6},size:0.9,colorOffset:0},at:0,thickness:0.12},"
        + "{shape:{type:\"ink\",ink:\"magic-firework:ring8\",place:{rot:[45.0,30.0,0.0],scale:2.2},size:1.0,colorOffset:2},at:0,thickness:0.18},"
        + "{shape:{type:\"ring\",shape:\"magic-firework:petal\",count:12,radius:0.05,sweep:360.0,colorOffset:2,size:1.1,place:{rot:[72.0,0.0,0.0],scale:2.0}},at:1,thickness:0.1},"
        + "{shape:{type:\"ring\",shape:\"magic-firework:dot\",count:16,radius:0.05,colorOffset:1,size:0.8},at:2,duration:20,path:{type:\"line\",length:3.2},place:{rot:[90.0,0.0,0.0]}},"
        + "{shape:{type:\"ring\",shape:\"magic-firework:dot\",count:16,radius:0.05,colorOffset:0,size:0.7},at:4,duration:20,path:{type:\"line\",length:2.6},place:{rot:[0.0,0.0,90.0]}},"
        + "{shape:{type:\"ink\",ink:\"magic-firework:ring16\",place:{scale:0.9},size:1.2,colorOffset:3},at:24,thickness:0.15,events:[{kind:\"sound\",id:\"minecraft:entity.firework_rocket.blast\",volume:1.2,pitch:1.4}]},"
        + "{shape:{type:\"ring\",shape:\"magic-firework:dot\",count:10,radius:0.5,colorOffset:3,size:0.7},at:24,thickness:0.15,jitter:0.3}"
        + "]}";

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

	/**
	 * The document a player pastes into {@code /give ... {MagicFirework:{overlay:{show:{...}}}}}:
	 * SNBT, one line, exactly the command's form. It must survive the same codec a data pack file
	 * does, or the command would hand out an item whose burst does nothing.
	 */
	@Test
	void theDocumentFromTheGiveCommandParses() throws Exception {
		CompoundTag document = (CompoundTag) TagParser.parseTag(COMMAND_DOCUMENT);

		Show show = show(document);
		Plan plan = Evaluate.evaluate(show, new Seed(3L), new Budget(4096, 64));

		assertFalse(plan.isEmpty(), "the inline document must bake points");
		assertEquals(2, plan.events().size(), "a big blast and the second pop");
		assertTrue(plan.stream().isTimeOrdered());
	}

	/**
	 * The rest of the /give tag: every optional attribute, plus a child tree. This is the shape
	 * the docs hand out, so it is asserted rather than trusted.
	 */
	@Test
	void theGiveCommandTagResolvesTheRocketItPromises() throws Exception {
		CompoundTag overlay = new CompoundTag();
		overlay.putFloat("flight.speed", 0.28F);
		overlay.putInt("flight.ticks", 45);
		overlay.putFloat("flight.gravity", 0.0F);
		overlay.putFloat("flight.max_speed", 1.4F);
		overlay.putFloat("flight.drag", 0.97F);
		overlay.putString("flight.direction.mode", "relative");
		overlay.putFloat("flight.direction.yaw", 0.0F);
		overlay.putFloat("flight.direction.pitch", 0.0F);
		overlay.putBoolean("decorative", true);
		overlay.putInt("afterglow", 12);
		overlay.putInt("max.depth", 3);
		overlay.putInt("max.children", 8);
		overlay.putBoolean("trail.enabled", true);
		overlay.putInt("trail.interval", 1);
		overlay.putFloat("trail.scale", 0.7F);
		overlay.putIntArray("trail.colors", new int[] { 16776960, 16755200 });
		overlay.putBoolean("flicker", true);
		overlay.putString("launch.sound", "minecraft:entity.firework_rocket.launch");
		overlay.putString("sound.id", "minecraft:entity.firework_rocket.blast");
		overlay.putFloat("sound.volume", 1.5F);
		overlay.putFloat("sound.pitch", 1.2F);
		overlay.putInt("light.ticks", 30);
		overlay.put("show", TagParser.parseTag(COMMAND_DOCUMENT));

		ListTag children = new ListTag();
		children.add(child("magic-firework:ring"));
		children.add(child("magic-firework:cube"));
		CompoundTag nested = child("magic-firework:staged");
		ListTag grandchildren = new ListTag();
		grandchildren.add(child("magic-firework:ring12"));
		nested.put("children", grandchildren);
		children.add(nested);
		children.add(child("magic-firework:golden_peony"));

		CompoundTag root = new CompoundTag();
		root.putString("profile", "magic-firework:staged");
		root.put("overlay", overlay);
		root.put("children", children);

		RocketSpec spec = FireworkDataHelper.spec(root);

		assertEquals("magic-firework:staged", spec.profileId().toString());
		assertTrue(Attributes.FLICKER.get(spec.overlay()));
		assertEquals(45, Attributes.FLIGHT_TICKS.get(spec.overlay()));
		assertEquals(4, spec.children().size());
		assertEquals(1, spec.children().get(2).children().size(), "the third child nests its own rocket");
		assertFalse(Evaluate.evaluate(show((CompoundTag) spec.overlay().raw().get(Attributes.SHOW)),
				new Seed(3L), new Budget(4096, 64)).isEmpty());
	}

	private static CompoundTag child(String profile) {
		CompoundTag child = new CompoundTag();
		child.putString("profile", profile);
		return child;
	}
}

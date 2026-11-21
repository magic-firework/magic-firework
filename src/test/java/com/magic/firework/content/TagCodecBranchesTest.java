package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.magic.firework.core.ContentLimits;
import com.magic.firework.core.Id;
import com.magic.firework.core.path.Path;
import com.magic.firework.core.shape.Form;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.Spark;
import com.magic.firework.core.show.Stamp;
import com.magic.firework.core.show.Show;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Edge branches of the content codec: every malformed document must be a hard error. */
class TagCodecBranchesTest {
	private static final Id INK = new Id("test", "ink");
	private static final Id SHOW = new Id("test", "show");

	private static Tag tag(String json) {
		return JsonToNbt.convert(JsonParser.parseString(json));
	}

	private static Ink lookup(Id id) {
		try {
			return INK.equals(id) ? TagCodec.parseInk(INK, tag("""
					{ "points": [[0.0, 0.0], [1.0, 0.0]] }
					""")) : null;
		} catch (Exception exception) {
			return null;
		}
	}

	private static Show show(String json) throws Exception {
		return TagCodec.parseShow(SHOW, tag(json), TagCodecBranchesTest::lookup);
	}

	private static ListTag pair(double u, double v) {
		ListTag pair = new ListTag();
		pair.add(DoubleTag.valueOf(u));
		pair.add(DoubleTag.valueOf(v));
		return pair;
	}

	@Test
	void aUvInkPairsItsCoordinates() throws Exception {
		Ink ink = TagCodec.parseInk(INK, tag("""
				{ "uv": [1.0, 2.0, 3.0, 4.0] }
				"""));

		assertEquals(2, ink.points());
		assertEquals(1.0, ink.point(0).x, 0.0);
		assertEquals(4.0, ink.point(1).y, 0.0);
	}

	@Test
	void anInkOverThePointLimitIsRejectedBeforeItIsBuilt() {
		CompoundTag ink = new CompoundTag();
		ListTag points = new ListTag();
		for (int i = 0; i <= ContentLimits.MAX_INK_POINTS; i++) {
			points.add(pair(0.0, 0.0));
		}
		ink.put("points", points);

		assertThrows(TagCodec.CodecException.class, () -> TagCodec.parseInk(INK, ink));
	}

	@Test
	void aColourArrayThatDoesNotMatchThePointsIsRejected() {
		assertThrows(TagCodec.CodecException.class, () -> TagCodec.parseInk(INK, tag("""
				{ "points": [[0.0, 0.0]], "colors": [1, 2] }
				""")));
	}

	@Test
	void intAndLongArraysAreAcceptedForColoursSizesAndPalettes() throws Exception {
		CompoundTag first = new CompoundTag();
		ListTag points = new ListTag();
		points.add(pair(0.0, 0.0));
		points.add(pair(1.0, 0.0));
		first.put("points", points);
		first.put("colors", new IntArrayTag(new int[]{7, 9}));
		first.put("sizes", new LongArrayTag(new long[]{3L, 4L}));
		Ink ink = TagCodec.parseInk(INK, first);
		assertEquals(7, ink.color(0));
		assertEquals(4.0, ink.size(1), 0.0);

		CompoundTag second = new CompoundTag();
		second.put("points", points.copy());
		second.put("colors", new LongArrayTag(new long[]{11L, 12L}));
		second.put("sizes", new IntArrayTag(new int[]{5, 6}));
		Ink other = TagCodec.parseInk(INK, second);
		assertEquals(12, other.color(1));
		assertEquals(5.0, other.size(0), 0.0);
	}

	@Test
	void aSparkKeepsEveryExplicitOverride() throws Exception {
		Stamp stamp = show("""
				{
				  "stamps": [ {
				    "shape": "test:ink",
				    "spark": { "speed": 0.4, "speedSpread": 0.1, "directionSpread": 0.2, "trail": true }
				  } ]
				}
				""").stamps().get(0);

		Spark spark = stamp.spark();
		assertEquals(0.4, spark.speed(), 0.0);
		assertEquals(0.1, spark.speedSpread(), 0.0);
		assertEquals(0.2, spark.directionSpread(), 0.0);
		assertTrue(spark.trail());
	}

	@Test
	void aNonNumericSparkFlagFallsBackToItsDefault() throws Exception {
		Spark spark = show("""
				{ "stamps": [ { "shape": "test:ink", "spark": { "trail": "yes", "flicker": "yes" } } ] }
				""").stamps().get(0).spark();

		assertFalse(spark.trail());
		assertFalse(spark.flicker());
	}

	@Test
	void aStampWithoutAShapeIsAHardError() {
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "at": 1 } ] }
				"""));
	}

	@Test
	void aShapeThatIsNotAnIdIsAHardError() {
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": "noseparator" } ] }
				"""));
	}

	@Test
	void anExplicitNonePathIsAllowedAndMirrorTakesZAlone() throws Exception {
		Stamp stamp = show("""
				{
				  "stamps": [ {
				    "shape": { "type": "mirror", "shape": "test:ink", "axes": ["z"] },
				    "path": { "type": "none" }
				  } ]
				}
				""").stamps().get(0);

		assertInstanceOf(Path.None.class, stamp.path());
		Form.Mirror mirror = assertInstanceOf(Form.Mirror.class, stamp.form());
		assertTrue(mirror.z());
		assertFalse(mirror.x());
	}

	@Test
	void theShowRootMustBeACompound() {
		assertThrows(TagCodec.CodecException.class, () -> TagCodec.parseShow(SHOW, tag("[]"),
				TagCodecBranchesTest::lookup));
	}

	@Test
	void aNonStringMirrorAxisIsAHardError() {
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": { "type": "mirror", "shape": "test:ink", "axes": [1] } } ] }
				"""));
	}

	@Test
	void nonNumericSizesAndMissingPathLengthsAreHardErrors() {
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": "test:ink", "size": "big" } ] }
				"""));
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": "test:ink", "path": { "type": "line" } } ] }
				"""));
	}

	@Test
	void anArrayNeedsAThreeComponentStep() {
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": { "type": "array", "shape": "test:ink" } } ] }
				"""));
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": { "type": "array", "shape": "test:ink", "step": [1.0, 0.0] } } ] }
				"""));
	}

	@Test
	void aSoundEventNeedsAValidId() {
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": "test:ink", "events": [ { "kind": "sound" } ] } ] }
				"""));
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": "test:ink", "events": [ { "kind": "sound", "id": "noseparator" } ] } ] }
				"""));
	}

	@Test
	void tooManyFormNodesIsAHardError() {
		CompoundTag stamp = new CompoundTag();
		CompoundTag shape = new CompoundTag();
		shape.putString("type", "union");
		ListTag parts = new ListTag();
		for (int i = 0; i <= ContentLimits.MAX_FORM_NODES; i++) {
			parts.add(StringTag.valueOf("test:ink"));
		}
		shape.put("parts", parts);
		stamp.put("shape", shape);
		ListTag stamps = new ListTag();
		stamps.add(stamp);
		CompoundTag root = new CompoundTag();
		root.put("stamps", stamps);

		assertThrows(TagCodec.CodecException.class, () -> TagCodec.parseShow(SHOW, root,
				TagCodecBranchesTest::lookup));
	}
}

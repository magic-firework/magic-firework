package com.magic.firework.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.magic.firework.core.ContentLimits;
import com.magic.firework.core.Id;
import com.magic.firework.core.show.EventSpec;
import com.magic.firework.core.shape.Form;
import com.magic.firework.core.shape.Ink;
import com.magic.firework.core.show.Show;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.Tag;

class TagCodecTest {
	private static final Id INK = new Id("test", "ink");
	private static final Id SHOW = new Id("test", "show");

	private static Tag tag(String json) {
		return JsonToNbt.convert(JsonParser.parseString(json));
	}

	private static Ink ink() throws Exception {
		return TagCodec.parseInk(INK, tag("""
				{ "points": [[0.0, 0.0], [1.0, 0.0, 0.5]], "sizes": [1.0, 2.0], "density": 2.0 }
				"""));
	}

	private static Ink lookup(Id id) {
		try {
			return INK.equals(id) ? ink() : null;
		} catch (Exception exception) {
			return null;
		}
	}

	private static Show show(String json) throws Exception {
		return TagCodec.parseShow(SHOW, tag(json), TagCodecTest::lookup);
	}

	@Test
	void parsesInkWithFlatAndThreeDimensionalPoints() throws Exception {
		Ink ink = ink();

		assertEquals(2, ink.points());
		assertEquals(0.0, ink.point(0).z, 0.0);
		assertEquals(0.5, ink.point(1).z, 0.0);
		assertEquals(2.0, ink.size(1), 0.0);
	}

	@Test
	void parsesAFlatShow() throws Exception {
		Show show = show("""
				{
				  "palette": [16755200],
				  "stamps": [
				    {
				      "shape": "test:ink",
				      "at": 3,
				      "duration": 20,
				      "size": 1.5,
				      "thickness": 0.1,
				      "jitter": 0.02,
				      "place": { "pos": [0.0, 1.0, 0.0], "rot": [0.0, 90.0, 0.0], "scale": 2.0 },
				      "events": [ { "kind": "sound", "at": 2, "id": "minecraft:block.note_block.bell" } ]
				    }
				  ]
				}
				""");

		assertEquals(1, show.paletteSize());
		assertEquals(1, show.stampCount());
		assertEquals(3, show.stamps().get(0).at());
		assertEquals(20, show.stamps().get(0).duration());
		assertEquals(1.5, show.stamps().get(0).size(), 0.0);
		assertEquals(0.1, show.stamps().get(0).thickness(), 0.0);
		assertEquals(1, show.stamps().get(0).events().size());
	}

	@Test
	void parsesAnInlinePathShape() throws Exception {
		Form form = show(
				"""
						{
						  "stamps": [
						    { "shape": { "type": "path", "path": { "type": "arc", "radius": 1.0, "degrees": 180 }, "samples": 12 } }
						  ]
						}
						""")
				.stamps().get(0).form();

		Form.Leaf leaf = assertInstanceOf(Form.Leaf.class, form);
		assertEquals(12, leaf.ink().estimatePoints());
	}

	@Test
	void parsesRingArrayMirrorAndGroup() throws Exception {
		Show show = show(
				"""
						{
						  "stamps": [
						    { "shape": { "type": "ring", "shape": "test:ink", "count": 4, "radius": 2.0 } },
						    { "shape": { "type": "array", "shape": "test:ink", "count": 3, "step": [1.0, 0.0, 0.0] } },
						    { "shape": { "type": "mirror", "shape": "test:ink", "axes": ["x", "y"] } },
						    { "shape": { "type": "union", "parts": [ { "shape": "test:ink" }, { "shape": "test:ink", "size": 2.0 } ] } }
						  ]
						}
						""");

		assertEquals(2, show.stamps().get(0).form().nodes(), "ring + child");
		assertEquals(2, show.stamps().get(1).form().nodes(), "array + child");
		assertEquals(2, show.stamps().get(2).form().nodes(), "mirror + child");
		Form.Group group = assertInstanceOf(Form.Group.class, show.stamps().get(3).form());
		assertEquals(2, group.parts().size());
		assertEquals(3, group.nodes(), "group + two parts");
	}

	@Test
	void unknownShapeIsAHardError() {
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": "test:missing" } ] }
				"""));
	}

	@Test
	void unknownInlineTypeIsAHardError() {
		TagCodec.CodecException error = assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": { "type": "kaleidoscope" } } ] }
				"""));
		assertTrue(error.getMessage().contains("unknown type"));
	}

	@Test
	void mirrorWithoutAxesIsAHardError() {
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": { "type": "mirror", "shape": "test:ink", "axes": [] } } ] }
				"""));
	}

	@Test
	void missingStampsIsAHardError() {
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "palette": [16755200] }
				"""));
	}

	@Test
	void tooDeepNestingIsAHardError() {
		String nested = "{ \"type\": \"group\", \"parts\": [ { \"shape\": \"test:ink\" } ] }";
		for (int i = 0; i < 12; i++) {
			nested = "{ \"type\": \"group\", \"parts\": [ " + nested + " ] }";
		}
		String body = nested;
		assertThrows(TagCodec.CodecException.class, () -> show(
				"{ \"stamps\": [ { \"shape\": " + body + " } ] }"));
	}

	@Test
	void malformedInkPointsAreAHardError() {
		assertThrows(TagCodec.CodecException.class, () -> TagCodec.parseInk(INK,
				tag("""
						{ "points": [[1.0, 2.0, 3.0, 4.0]] }
						""")), "four components is neither [u, v] nor [u, v, w]");
		assertThrows(TagCodec.CodecException.class, () -> TagCodec.parseInk(INK,
				tag("""
						{ "uv": [1.0, 2.0, 3.0] }
						""")), "uv needs an even length");
		assertThrows(TagCodec.CodecException.class, () -> TagCodec.parseInk(INK,
				tag("""
						{ "points": [] }
						""")), "no points at all");
	}

	@Test
	void aNonArrayPaletteOrSizeListIsAHardError() throws Exception {
		TagCodec.CodecException palette = assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "palette": "gold", "stamps": [] }
				"""));
		assertTrue(palette.getMessage().contains("palette"));

		TagCodec.CodecException sizes = assertThrows(TagCodec.CodecException.class,
				() -> TagCodec.parseInk(INK, tag("""
						{ "points": [[0.0, 0.0]], "sizes": "big" }
						""")));
		assertTrue(sizes.getMessage().contains("sizes"));
	}

	@Test
	void thePaletteAndStampCountsAreBounded() {
		String palette = "0,".repeat(ContentLimits.MAX_PALETTE + 1);
		assertThrows(TagCodec.CodecException.class, () -> show(
				"{ \"palette\": [" + palette + "0], \"stamps\": [] }"));

		String stamps = "{},".repeat(ContentLimits.MAX_STAMPS + 1);
		assertThrows(TagCodec.CodecException.class, () -> show(
				"{ \"stamps\": [" + stamps + "{}] }"));
	}

	@Test
	void aLightEventKeepsItsDurationAndAnUnknownKindIsAHardError() throws Exception {
		Show show = show("""
				{ "stamps": [ { "shape": "test:ink", "events": [ { "kind": "light", "at": 4, "ticks": 40 } ] } ] }
				""");
		EventSpec event = show.stamps().get(0).events().get(0);
		assertEquals(EventSpec.Kind.LIGHT, event.kind());
		assertEquals(4, event.tick());
		assertEquals(40.0, event.a(), 0.0);

		TagCodec.CodecException unknown = assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": "test:ink", "events": [ { "kind": "smoke" } ] } ] }
				"""));
		assertTrue(unknown.getMessage().contains("unknown kind"));
	}

	@Test
	void anUnknownPathTypeOrMirrorAxisIsAHardError() {
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": { "type": "path", "path": { "type": "spiral" } } } ] }
				"""));
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": { "type": "mirror", "shape": "test:ink", "axes": ["q"] } } ] }
				"""));
	}

	@Test
	void repeatCountsAreClampedInsteadOfExpandingWithoutBound() throws Exception {
		Form.Ring ring = assertInstanceOf(Form.Ring.class, show("""
				{ "stamps": [ { "shape": { "type": "ring", "shape": "test:ink", "count": 9999, "radius": 1.0 } } ] }
				""").stamps().get(0).form());
		assertEquals(ContentLimits.MAX_REPEAT, ring.count());

		Form.Array array = assertInstanceOf(Form.Array.class,
				show("""
						{ "stamps": [ { "shape": { "type": "array", "shape": "test:ink", "count": 0, "step": [1.0, 0.0, 0.0] } } ] }
						""")
						.stamps().get(0).form());
		assertEquals(1, array.count(), "a zero count means one copy, never none");
	}

	@Test
	void aNonFiniteNumberIsAHardError() {
		assertThrows(TagCodec.CodecException.class, () -> show("""
				{ "stamps": [ { "shape": "test:ink", "size": 1e999 } ] }
				"""));
	}
}

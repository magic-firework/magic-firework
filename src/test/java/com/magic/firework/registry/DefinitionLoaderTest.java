package com.magic.firework.registry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;
import com.magic.firework.content.JsonToNbt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * A reload builds a new registry; a broken file must disappear from it and leave an explicit
 * warning, not silently keep the previous profile alive.
 */
class DefinitionLoaderTest {
	private static final ResourceLocation OK_FILE = new ResourceLocation("test", "fireworks/ok.json");
	private static final ResourceLocation BAD_FILE = new ResourceLocation("test", "fireworks/bad.json");
	private static final ResourceLocation OK_ID = new ResourceLocation("test", "ok");
	private static final ResourceLocation BAD_ID = new ResourceLocation("test", "bad");

	private static Tag tag(String json) {
		return JsonToNbt.convert(JsonParser.parseString(json));
	}

	@Test
	void aBrokenProfileIsDroppedAndLoggedWithItsId() {
		Map<ResourceLocation, Tag> documents = new LinkedHashMap<>();
		documents.put(OK_FILE, tag("{\"program\":[{\"op\":\"burst\"}]}"));
		documents.put(BAD_FILE, tag("{\"program\":[{\"op\":\"burst\",\"colour\":1}]}"));

		CapturingAppender appender = new CapturingAppender();
		appender.start();
		Logger logger = (Logger) LogManager.getLogger("magic-firework");
		logger.addAppender(appender);
		try {
			Definitions definitions = DefinitionLoader.load(documents);

			assertNotNull(definitions.get(OK_ID));
			assertNull(definitions.get(BAD_ID), "a broken file is dropped, not kept from the previous load");
			assertTrue(appender.messages.stream().anyMatch(message -> message.contains(BAD_ID.toString())),
					"the warning names the rejected profile: " + appender.messages);
		} finally {
			logger.removeAppender(appender);
			appender.stop();
		}
	}

	private static final class CapturingAppender extends AbstractAppender {
		private final List<String> messages = new ArrayList<>();

		private CapturingAppender() {
			super("definition-loader-test", null, null, false, Property.EMPTY_ARRAY);
		}

		@Override
		public void append(LogEvent event) {
			messages.add(event.getMessage().getFormattedMessage());
		}
	}
}

package com.magic.firework.registry;

import java.util.Map;
import java.util.Set;

import com.magic.firework.content.FireworkProfile;
import com.magic.firework.program.Program;

import net.minecraft.resources.ResourceLocation;

/**
 * One immutable view of the installed firework profiles.
 *
 * <p>Like {@code ContentSnapshot}, a reload builds a new map and swaps it in whole, so a
 * running rocket never observes a half-cleared registry.
 */
public record Definitions(Map<ResourceLocation, FireworkProfile> profiles) {
	public Definitions {
		profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
	}

	public static Definitions empty() {
		return new Definitions(Map.of());
	}

	public FireworkProfile get(ResourceLocation id) {
		return id == null ? null : profiles.get(id);
	}

	/** Program lookup for the interpreter; {@code null} means "not installed". */
	public Program program(ResourceLocation id) {
		FireworkProfile profile = get(id);
		return profile == null ? null : profile.program();
	}

	public Set<ResourceLocation> ids() {
		return profiles.keySet();
	}

	public int size() {
		return profiles.size();
	}
}

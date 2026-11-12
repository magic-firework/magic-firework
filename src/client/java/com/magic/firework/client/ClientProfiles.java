package com.magic.firework.client;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.magic.firework.entity.MagicFireworkEntity;
import com.magic.firework.network.FireworkNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Bridges the profile packet and the client entity.
 *
 * <p>The spawn packet and the profile packet may arrive in either order for a late
 * tracker, so profiles for unknown entities wait here until the entity shows up. The map is
 * bounded: a profile whose entity never arrives is dropped rather than growing forever.
 */
public final class ClientProfiles {
	private static final int MAX_PENDING = 256;

	/** Accessed only from the client thread (both callbacks run through client.execute). */
	private static final Map<Integer, FireworkNetworking.Profile> PENDING = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, FireworkNetworking.Profile> eldest) {
			return size() > MAX_PENDING;
		}
	};

	private ClientProfiles() {
	}

	public static void accept(Minecraft client, FireworkNetworking.Profile profile) {
		MagicFireworkEntity entity = find(client, profile.entityId());
		if (entity == null) {
			PENDING.put(profile.entityId(), profile);
			return;
		}
		apply(entity, profile);
	}

	public static void tick(Minecraft client) {
		if (PENDING.isEmpty() || client.level == null) {
			return;
		}
		Iterator<Map.Entry<Integer, FireworkNetworking.Profile>> iterator = PENDING.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, FireworkNetworking.Profile> entry = iterator.next();
			MagicFireworkEntity entity = find(client, entry.getKey());
			if (entity != null) {
				iterator.remove();
				apply(entity, entry.getValue());
			}
		}
	}

	public static int pending() {
		return PENDING.size();
	}

	public static void clear() {
		PENDING.clear();
	}

	private static void apply(MagicFireworkEntity entity, FireworkNetworking.Profile profile) {
		entity.acceptProfile(profile.seed(), profile.age(), profile.bag());
	}

	private static MagicFireworkEntity find(Minecraft client, int entityId) {
		if (client.level == null) {
			return null;
		}
		Entity entity = client.level.getEntity(entityId);
		return entity instanceof MagicFireworkEntity rocket ? rocket : null;
	}
}

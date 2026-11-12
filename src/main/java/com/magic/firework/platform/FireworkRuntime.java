package com.magic.firework.platform;

import com.magic.firework.content.ContentLibrary;
import com.magic.firework.content.ContentSnapshot;
import com.magic.firework.effect.LightSources;
import com.magic.firework.registry.Definitions;

import net.minecraft.server.MinecraftServer;

/**
 * Everything that used to be a static field, scoped to one running server.
 *
 * <p>Content, definitions, tick counters, scheduled effects, temporary lights and the plan
 * cache live here. A reload swaps the immutable snapshots; a server stop closes the runtime,
 * which is also what finally drops the {@code ServerLevel} references held by lights and
 * pending sounds.
 */
public final class FireworkRuntime {
	private final MinecraftServer server;
	private final ContentService content = new ContentService();
	private final TickLedger ledger = new TickLedger();
	private final LightSources lights = new LightSources();
	private final ScheduledEffects scheduled = new ScheduledEffects(lights);
	private final ShowPlanCache plans = new ShowPlanCache();
	private final ShowService shows = new ShowService(content, ledger, plans, scheduled);
	private volatile Definitions definitions;
	private ContentLibrary library;

	public FireworkRuntime(MinecraftServer server, Definitions definitions, ContentSnapshot content) {
		this.server = server;
		this.definitions = definitions == null ? Definitions.empty() : definitions;
		this.content.accept(content);
	}

	public void tick() {
		ledger.reset();
		scheduled.tick(server);
		lights.tick(server);
	}

	public void setDefinitions(Definitions next) {
		definitions = next == null ? Definitions.empty() : next;
		plans.clear();
	}

	public void setContent(ContentSnapshot next) {
		content.accept(next);
		plans.clear();
	}

	public void bindLibrary(ContentLibrary next) {
		library = next;
		content.bindLibrary(next);
		plans.clear();
	}

	/** The library document changed: drop cached shows and their baked plans. */
	public void invalidateLibrary() {
		content.invalidateLibrary();
		plans.clear();
	}

	public MinecraftServer server() {
		return server;
	}

	public Definitions definitions() {
		return definitions;
	}

	public ContentService content() {
		return content;
	}

	public TickLedger ledger() {
		return ledger;
	}

	public ShowService shows() {
		return shows;
	}

	public LightSources lights() {
		return lights;
	}

	public ContentLibrary library() {
		return library;
	}

	public void close() {
		scheduled.clear();
		lights.clear();
		plans.clear();
	}
}

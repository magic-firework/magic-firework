package com.magic.firework.core.show;

import java.util.List;

import com.magic.firework.core.Id;

/**
 * A drawing: a palette plus a flat list of stamps. No tree, no motion, no per-node children:
 * everything the client sees is a stamped point cloud with a tick offset.
 */
public record Show(Id id, int[] palette, List<Stamp> stamps) {
	public static final int DEFAULT_COLOR = 0xFFFFFF;

	public Show {
		if (id == null) {
			throw new IllegalArgumentException("show needs an id");
		}
		palette = palette == null || palette.length == 0 ? new int[] { DEFAULT_COLOR } : palette.clone();
		stamps = stamps == null ? List.of() : List.copyOf(stamps);
	}

	public int paletteSize() {
		return palette.length;
	}

	public int[] paletteCopy() {
		return palette.clone();
	}

	/** Palette lookup with wrap-around, returned as opaque ARGB. */
	public int color(int index) {
		int rgb = palette[Math.floorMod(index, palette.length)];
		return 0xFF000000 | (rgb & 0xFFFFFF);
	}

	public int stampCount() {
		return stamps.size();
	}

	/**
	 * True when a point depends on the burst seed (jitter or thickness uses the RNG).
	 * A seed-free show can be baked once and replayed by every rocket.
	 */
	public boolean seedSensitive() {
		for (Stamp stamp : stamps) {
			if (stamp.thickness() > 0.0 || stamp.jitter() > 0.0) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return "Show[" + id + ", stamps=" + stamps.size() + "]";
	}
}

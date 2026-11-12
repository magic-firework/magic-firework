package com.magic.firework.program;

import net.minecraft.world.phys.Vec3;

/** Wait conditions for {@link Op.Await}. */
public sealed interface Cond {
	/** {@code elapsed} is 0 on the first tick the await is evaluated. */
	boolean satisfied(Vec3 position, boolean collided, int elapsedTicks);

	record Ticks(int ticks) implements Cond {
		@Override
		public boolean satisfied(Vec3 position, boolean collided, int elapsedTicks) {
			return elapsedTicks >= Math.max(0, ticks);
		}
	}

	record HeightAbove(double y) implements Cond {
		@Override
		public boolean satisfied(Vec3 position, boolean collided, int elapsedTicks) {
			return position.y > y;
		}
	}

	record HeightBelow(double y) implements Cond {
		@Override
		public boolean satisfied(Vec3 position, boolean collided, int elapsedTicks) {
			return position.y < y;
		}
	}

	record Collision() implements Cond {
		@Override
		public boolean satisfied(Vec3 position, boolean collided, int elapsedTicks) {
			return collided;
		}
	}

	/** Failsafe: proceed even if the primary condition never happens. */
	record Or(Cond primary, Cond fallback) implements Cond {
		@Override
		public boolean satisfied(Vec3 position, boolean collided, int elapsedTicks) {
			return primary.satisfied(position, collided, elapsedTicks)
					|| fallback.satisfied(position, collided, elapsedTicks);
		}
	}
}

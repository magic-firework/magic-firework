package com.magic.firework.core.shape;

import java.util.List;

import com.magic.firework.core.ContentLimits;
import com.magic.firework.core.Meter;
import com.magic.firework.core.Transform;
import com.magic.firework.core.Vec3;

/**
 * A composable shape: a point cloud plus the transforms that place copies of it.
 *
 * <p>Forms are authoring units, not execution units. {@link #emit} flattens them into points
 * under the caller's {@link Meter}, so a ring of two hundred rings still cannot exceed the
 * server's point budget. The tree is plain data: no cycles, no code, no world.
 *
 * <p>{@code place}, {@code colorOffset} and {@code size} are the three knobs every node
 * shares: they compose down the tree, which is what lets one leaf ink become a ring of
 * recoloured, scaled copies without duplicating it.
 */
public sealed interface Form {
	Transform place();

	int colorOffset();

	double size();

	/** Flatten into {@code sink}, in this form's own coordinate space. */
	default void emit(Transform parent, int parentColor, double parentSize, Meter meter, Ink.Sink sink) {
		emit(parent, parentColor, parentSize, meter, sink, 0);
	}

	void emit(Transform parent, int parentColor, double parentSize, Meter meter, Ink.Sink sink, int depth);

	/** A single point cloud. */
	record Leaf(Ink ink, Transform place, int colorOffset, double size) implements Form {
		public Leaf {
			if (ink == null) {
				throw new IllegalArgumentException("leaf needs an ink");
			}
			place = place == null ? Transform.IDENTITY : place;
			size = size <= 0.0 ? 1.0 : size;
		}

		@Override
		public void emit(Transform parent, int parentColor, double parentSize, Meter meter, Ink.Sink sink,
				int depth) {
			Transform transform = parent.compose(place);
			int base = parentColor + colorOffset;
			double scale = parentSize * size;
			int granted = meter.takePoints(ink.estimatePoints());
			if (granted <= 0) {
				return;
			}
			ink.emit(new Ink.Emit(base, granted), (point, color, pointSize) ->
					sink.point(transform.apply(point), color, pointSize * scale));
		}
	}

	/** Children composited on top of each other. */
	record Group(List<Form> parts, Transform place, int colorOffset, double size) implements Form {
		public Group {
			parts = parts == null ? List.of() : List.copyOf(parts);
			place = place == null ? Transform.IDENTITY : place;
			size = size <= 0.0 ? 1.0 : size;
		}

		@Override
		public void emit(Transform parent, int parentColor, double parentSize, Meter meter, Ink.Sink sink,
				int depth) {
			if (depth > ContentLimits.MAX_FORM_DEPTH || meter.exhausted()) {
				return;
			}
			Transform transform = parent.compose(place);
			int base = parentColor + colorOffset;
			double scale = parentSize * size;
			for (Form part : parts) {
				part.emit(transform, base, scale, meter, sink, depth + 1);
			}
		}
	}

	/** {@code count} copies evenly spaced on a circle in the local XY plane, each rotated. */
	record Ring(Form child, int count, double radius, double sweepDegrees, Transform place, int colorOffset,
			double size) implements Form {
		public Ring {
			if (child == null) {
				throw new IllegalArgumentException("ring needs a child");
			}
			count = Math.max(1, Math.min(count, ContentLimits.MAX_REPEAT));
			radius = radius < 0.0 ? 0.0 : radius;
			place = place == null ? Transform.IDENTITY : place;
			size = size <= 0.0 ? 1.0 : size;
		}

		@Override
		public void emit(Transform parent, int parentColor, double parentSize, Meter meter, Ink.Sink sink,
				int depth) {
			if (depth > ContentLimits.MAX_FORM_DEPTH || meter.exhausted()) {
				return;
			}
			Transform transform = parent.compose(place);
			int base = parentColor + colorOffset;
			double scale = parentSize * size;
			double step = Math.toRadians(sweepDegrees) / count;
			for (int index = 0; index < count; index++) {
				double angle = step * index;
				Transform at = Transform.of(
						new Vec3(radius * Math.cos(angle), radius * Math.sin(angle), 0.0),
						0.0, 0.0, angle, 1.0);
				child.emit(transform.compose(at), base, scale, meter, sink, depth + 1);
			}
		}
	}

	/** {@code count} copies translated by {@code step} each. */
	record Array(Form child, int count, Vec3 step, Transform place, int colorOffset, double size)
			implements Form {
		public Array {
			if (child == null) {
				throw new IllegalArgumentException("array needs a child");
			}
			count = Math.max(1, Math.min(count, ContentLimits.MAX_REPEAT));
			step = step == null ? Vec3.ZERO : step;
			place = place == null ? Transform.IDENTITY : place;
			size = size <= 0.0 ? 1.0 : size;
		}

		@Override
		public void emit(Transform parent, int parentColor, double parentSize, Meter meter, Ink.Sink sink,
				int depth) {
			if (depth > ContentLimits.MAX_FORM_DEPTH || meter.exhausted()) {
				return;
			}
			Transform transform = parent.compose(place);
			int base = parentColor + colorOffset;
			double scale = parentSize * size;
			for (int index = 0; index < count; index++) {
				Transform at = Transform.translation(step.scale(index));
				child.emit(transform.compose(at), base, scale, meter, sink, depth + 1);
			}
		}
	}

	/** Reflections of one child across the selected local axes. */
	record Mirror(Form child, boolean x, boolean y, boolean z, Transform place, int colorOffset, double size)
			implements Form {
		public Mirror {
			if (child == null) {
				throw new IllegalArgumentException("mirror needs a child");
			}
			place = place == null ? Transform.IDENTITY : place;
			size = size <= 0.0 ? 1.0 : size;
		}

		@Override
		public void emit(Transform parent, int parentColor, double parentSize, Meter meter, Ink.Sink sink,
				int depth) {
			if (depth > ContentLimits.MAX_FORM_DEPTH || meter.exhausted()) {
				return;
			}
			Transform transform = parent.compose(place);
			int base = parentColor + colorOffset;
			double scale = parentSize * size;
			for (int flipX = 0; flipX <= (x ? 1 : 0); flipX++) {
				for (int flipY = 0; flipY <= (y ? 1 : 0); flipY++) {
					for (int flipZ = 0; flipZ <= (z ? 1 : 0); flipZ++) {
						Transform at = Transform.mirror(flipX == 1, flipY == 1, flipZ == 1);
						child.emit(transform.compose(at), base, scale, meter, sink, depth + 1);
					}
				}
			}
		}
	}

	/** Shorthand for a leaf with identity placement. */
	static Leaf leaf(Ink ink) {
		return new Leaf(ink, Transform.IDENTITY, 0, 1.0);
	}

	/** @return the number of nodes in this tree (used by tests and diagnostics) */
	default int nodes() {
		// Java 17 target: an instanceof chain instead of a pattern switch.
		if (this instanceof Leaf) {
			return 1;
		}
		if (this instanceof Group group) {
			int count = 1;
			for (Form part : group.parts()) {
				count += part.nodes();
			}
			return count;
		}
		if (this instanceof Ring ring) {
			return 1 + ring.child().nodes();
		}
		if (this instanceof Array array) {
			return 1 + array.child().nodes();
		}
		return 1 + ((Mirror) this).child().nodes();
	}
}

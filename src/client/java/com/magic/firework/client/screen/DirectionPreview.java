package com.magic.firework.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** The spherical direction preview: two eased angles and all the projection maths. */
final class DirectionPreview {
	private static final int ORIGIN_X = 128;
	private static final int ORIGIN_Y = 118;
	private static final int ARROW_RADIUS = 36;

	private float shownYaw;
	private float shownPitch = 90.0F;

	void tick(float targetYaw, float targetPitch) {
		shownYaw = ease(shownYaw, targetYaw);
		shownPitch = ease(shownPitch, targetPitch);
	}

	void snap(float yaw, float pitch) {
		shownYaw = yaw;
		shownPitch = pitch;
	}

	void render(GuiGraphics g, Font font, int x, int y, int alpha) {
		int ox = x + ORIGIN_X;
		int oy = y + ORIGIN_Y;
		int guide = ((alpha / 4) << 24) | 0x9A9AA4;

		drawAxes(g, font, ox, oy, alpha);
		drawEquator(g, ox, oy, guide);
		drawMeridian(g, ox, oy, Math.toRadians(shownYaw), guide);

		double yaw = Math.toRadians(shownYaw);
		double polar = Math.toRadians(shownPitch);
		double horizontal = Math.sin(polar);
		double vx = horizontal * Math.cos(yaw);
		double vy = Math.cos(polar);
		double vz = horizontal * Math.sin(yaw);

		int tx = ox + (int) Math.round(projectX(vx, vz) * ARROW_RADIUS);
		int ty = oy + (int) Math.round(projectY(vx, vy, vz) * ARROW_RADIUS);
		drawArrow(g, ox, oy, tx, ty, (alpha << 24) | 0x4DD0E1);

		g.fill(ox - 1, oy - 1, ox + 2, oy + 2, (alpha << 24) | 0xFFFFFF);
	}

	private void drawAxes(GuiGraphics g, Font font, int ox, int oy, int alpha) {
		int a = (int) (alpha * 0.7F);
		drawAxis(g, font, ox, oy, 1.0, 0.0, 0.0, (a << 24) | 0xE57373, "X", 3, -3);
		drawAxis(g, font, ox, oy, 0.0, 0.0, 1.0, (a << 24) | 0x81C784, "Y", -9, -3);
		drawAxis(g, font, ox, oy, 0.0, 1.0, 0.0, (a << 24) | 0x64B5F6, "Z", 3, -8);
	}

	private void drawAxis(GuiGraphics g, Font font, int ox, int oy, double x, double y, double z, int color,
			String label, int labelX, int labelY) {
		int tx = ox + (int) Math.round(projectX(x, z) * ARROW_RADIUS);
		int ty = oy + (int) Math.round(projectY(x, y, z) * ARROW_RADIUS);
		drawLine(g, ox, oy, tx, ty, color);
		g.drawString(font, label, tx + labelX, ty + labelY, color);
	}

	private static void drawEquator(GuiGraphics g, int ox, int oy, int color) {
		int steps = 48;
		for (int i = 0; i < steps; i++) {
			double angle = i * Math.PI * 2.0 / steps;
			plot(g, ox, oy, Math.cos(angle), 0.0, Math.sin(angle), color);
		}
	}

	private static void drawMeridian(GuiGraphics g, int ox, int oy, double yawRadians, int color) {
		double ux = Math.cos(yawRadians);
		double uz = Math.sin(yawRadians);
		int steps = 48;
		for (int i = 0; i < steps; i++) {
			double angle = i * Math.PI * 2.0 / steps;
			double horizontal = Math.cos(angle);
			plot(g, ox, oy, ux * horizontal, Math.sin(angle), uz * horizontal, color);
		}
	}

	private static void plot(GuiGraphics g, int ox, int oy, double x, double y, double z, int color) {
		int px = ox + (int) Math.round(projectX(x, z) * ARROW_RADIUS);
		int py = oy + (int) Math.round(projectY(x, y, z) * ARROW_RADIUS);
		g.fill(px, py, px + 1, py + 1, color);
	}

	private static double projectX(double x, double z) {
		return 0.70710678 * (x - z);
	}

	private static double projectY(double x, double y, double z) {
		return 0.35355339 * (x + z) - 0.8660254 * y;
	}

	private static void drawArrow(GuiGraphics g, int ox, int oy, int tx, int ty, int color) {
		drawLine(g, ox, oy, tx, ty, color);
		double angle = Math.atan2(ty - oy, tx - ox);
		double head = 7.0;
		for (double spread : new double[]{Math.PI - 0.45, Math.PI + 0.45}) {
			int hx = tx + (int) Math.round(Math.cos(angle + spread) * head);
			int hy = ty + (int) Math.round(Math.sin(angle + spread) * head);
			drawLine(g, tx, ty, hx, hy, color);
		}
	}

	private static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
		int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		if (steps == 0) {
			g.fill(x0, y0, x0 + 2, y0 + 2, color);
			return;
		}
		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			int px = Math.round(x0 + (x1 - x0) * t);
			int py = Math.round(y0 + (y1 - y0) * t);
			g.fill(px, py, px + 2, py + 2, color);
		}
	}

	private static float ease(float current, float target) {
		float next = current + (target - current) * 0.25F;
		return Math.abs(target - next) < 0.01F ? target : next;
	}
}

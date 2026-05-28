package com.nao.questcycle.client.hud;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/** Draws stacked toasts in the bottom-right of the HUD. */
public final class ToastOverlay extends Gui implements ITickHandler {
	private static final int WIDTH = 180;
	private static final int LINE_HEIGHT = 18;
	private static final int MARGIN = 6;

	private final Minecraft mc = Minecraft.getMinecraft();

	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {
		if (!type.contains(TickType.RENDER)) return;
		if (mc == null || mc.thePlayer == null || mc.currentScreen != null) return;
		// drawScaledOnHud reads scale info; let MC pass the partial tick (we don't use it)
		drawOverlay();
	}

	private void drawOverlay() {
		ToastQueue.Toast[] toasts = ToastQueue.snapshot();
		if (toasts.length == 0) return;
		ScaledResolution sr = new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight);
		int sw = sr.getScaledWidth();
		int sh = sr.getScaledHeight();
		int x = sw - WIDTH - MARGIN;
		int y = sh - MARGIN - (toasts.length * LINE_HEIGHT);

		FontRenderer fr = mc.fontRenderer;
		GL11.glPushMatrix();
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		for (int i = 0; i < toasts.length; i++) {
			ToastQueue.Toast t = toasts[i];
			long age = System.currentTimeMillis() - t.bornMs;
			float fade = 1f;
			if (age > ToastQueue.DURATION_MS - 500) {
				fade = Math.max(0f, (ToastQueue.DURATION_MS - age) / 500f);
			}
			int alpha = (int) (0xE0 * fade) & 0xFF;
			int bg = (alpha << 24) | 0x101418;
			int accent = (t.accentArgb & 0x00FFFFFF) | (alpha << 24);
			drawRect(x, y, x + WIDTH, y + LINE_HEIGHT - 2, bg);
			drawRect(x, y, x + 3, y + LINE_HEIGHT - 2, accent);
			fr.drawStringWithShadow(t.text, x + 8, y + 5, 0xFFFFFF);
			y += LINE_HEIGHT;
		}
		GL11.glPopMatrix();
	}

	@Override
	public EnumSet<TickType> ticks() {
		return EnumSet.of(TickType.RENDER);
	}

	@Override
	public String getLabel() {
		return "QuestCycle.Toast";
	}
}

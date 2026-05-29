package com.nao.questcycle.client.hud;

import com.nao.questcycle.client.ClientState;
import com.nao.questcycle.client.gui.GuiPalette;
import com.nao.questcycle.core.LeaderboardBuilder;
import com.nao.questcycle.network.PacketBuilder;
import com.nao.questcycle.network.QuestPacketHandler;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.network.PacketDispatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Wider, prettier player list rendered while Tab is held. Replaces the vanilla
 * list visually by zero-ing out keyBindPlayerList.pressed BEFORE the HUD
 * render runs (CLIENT tick), then redrawing our own on the RENDER tick.
 *
 * Columns: title (color preserved), username (full length), prestige, cycle %,
 * online dot. Sourced from ClientState.leaderboard (refreshed every ~3s).
 */
public final class CustomTabOverlay extends Gui implements ITickHandler {
	private static final int LINE_H = 12;
	private static final int PANEL_W = 280;
	private static final int MAX_ROWS = 16;
	private static final long REFRESH_MS = 3000L;

	private final Minecraft mc = Minecraft.getMinecraft();
	private boolean tabHeldThisFrame;
	private long lastRefreshMs;

	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
		// Suppress the vanilla tab list aggressively every frame BEFORE the HUD draws.
		// This needs to happen on RENDER tickStart (runs immediately before GuiIngame).
		if (type.contains(TickType.RENDER)) {
			// Not in a world (main menu / between servers): drop any stale leaderboard so it
			// doesn't bleed across servers, and let the vanilla tab list behave normally.
			if (mc == null || mc.thePlayer == null) {
				if (!ClientState.leaderboard.isEmpty()) {
					ClientState.leaderboard = new ArrayList<LeaderboardBuilder.Row>();
				}
				tabHeldThisFrame = false;
				return;
			}
			tabHeldThisFrame = isTabHeld();
			KeyBinding kb = mc.gameSettings.keyBindPlayerList;
			if (kb != null && tabHeldThisFrame) kb.pressed = false;
		}
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {
		if (!type.contains(TickType.RENDER)) return;
		if (mc == null || mc.thePlayer == null || mc.currentScreen != null) return;
		if (!tabHeldThisFrame) return;
		long now = System.currentTimeMillis();
		if (now - lastRefreshMs > REFRESH_MS) {
			lastRefreshMs = now;
			PacketDispatcher.sendPacketToServer(QuestPacketHandler.wrap(PacketBuilder.leaderboardRequest(64)));
		}
		drawOverlay();
	}

	private boolean isTabHeld() {
		try {
			KeyBinding kb = mc.gameSettings.keyBindPlayerList;
			if (kb == null) return false;
			return Keyboard.isKeyDown(kb.keyCode);
		} catch (Throwable t) {
			return Keyboard.isKeyDown(Keyboard.KEY_TAB);
		}
	}

	private void drawOverlay() {
		List<LeaderboardBuilder.Row> all = ClientState.leaderboard;
		if (all == null) all = new ArrayList<LeaderboardBuilder.Row>();
		// The tab list is "who's online right now" - drop offline players (the leaderboard
		// data set includes everyone ever seen).
		List<LeaderboardBuilder.Row> rows = new ArrayList<LeaderboardBuilder.Row>();
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).online) rows.add(all.get(i));
		}
		Collections.sort(rows, new Comparator<LeaderboardBuilder.Row>() {
			@Override
			public int compare(LeaderboardBuilder.Row a, LeaderboardBuilder.Row b) {
				if (a.online != b.online) return a.online ? -1 : 1;
				if (a.prestige != b.prestige) return b.prestige - a.prestige;
				return a.username.compareToIgnoreCase(b.username);
			}
		});
		if (rows.size() > MAX_ROWS) rows = rows.subList(0, MAX_ROWS);

		FontRenderer fr = mc.fontRenderer;
		ScaledResolution sr = new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight);
		int sw = sr.getScaledWidth();
		int panelH = 30 + rows.size() * LINE_H;
		int x = (sw - PANEL_W) / 2;
		int y = 50; // clear of the WAILA/top tooltip bar (item name + harvestability + mod = ~3 lines)

		// Column x-offsets (relative to panel x). Single source of truth so header
		// and rows always agree visually.
		final int COL_NAME   = 10;
		final int COL_PREST  = 150;
		final int COL_CYCLE  = 196;
		final int COL_TITLES = 240;

		GL11.glPushMatrix();
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		drawRect(x, y, x + PANEL_W, y + panelH, 0xCC101418);
		drawRect(x, y, x + PANEL_W, y + 1, GuiPalette.BORDER);
		drawRect(x, y + panelH - 1, x + PANEL_W, y + panelH, GuiPalette.BORDER);
		drawRect(x, y, x + 1, y + panelH, GuiPalette.BORDER);
		drawRect(x + PANEL_W - 1, y, x + PANEL_W, y + panelH, GuiPalette.BORDER);

		String header = "Players (" + rows.size() + " online)";
		drawCenteredString(fr, "§l" + header, x + PANEL_W / 2, y + 5, 0xFFFFFF);

		int hy = y + 18;
		drawString(fr, "§7§nPlayer", x + COL_NAME, hy, 0xFF909090);
		drawString(fr, "§7§nPrest", x + COL_PREST, hy, 0xFF909090);
		drawString(fr, "§7§nCycle", x + COL_CYCLE, hy, 0xFF909090);
		drawString(fr, "§7§nTitles", x + COL_TITLES, hy, 0xFF909090);

		int cy = hy + LINE_H;
		for (int i = 0; i < rows.size(); i++) {
			LeaderboardBuilder.Row r = rows.get(i);
			int rowY = cy + i * LINE_H;
			drawString(fr, fit(r.username, 20), x + COL_NAME, rowY + 1, r.online ? 0xFFFFFF : 0xFF909090);
			drawString(fr, "§6" + r.prestige, x + COL_PREST, rowY + 1, 0xFFFFAA00);
			drawString(fr, r.cyclePct + "%", x + COL_CYCLE, rowY + 1, r.cyclePct >= 100 ? 0xFF40DD60 : 0xFFA0A0A0);
			drawString(fr, "§7" + r.titlesCount, x + COL_TITLES, rowY + 1, 0xFF909090);
		}

		GL11.glPopMatrix();
	}

	private static String fit(String s, int approxChars) {
		if (s == null) return "";
		// Don't count § color codes against the visible length.
		int visible = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) { i++; continue; }
			visible++;
		}
		if (visible <= approxChars) return s;
		StringBuilder out = new StringBuilder();
		int kept = 0;
		for (int i = 0; i < s.length() && kept < approxChars; i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) { out.append(c).append(s.charAt(++i)); continue; }
			out.append(c);
			kept++;
		}
		return out.append("..").toString();
	}

	@Override
	public EnumSet<TickType> ticks() {
		return EnumSet.of(TickType.RENDER);
	}

	@Override
	public String getLabel() {
		return "QuestCycle.TabOverlay";
	}
}

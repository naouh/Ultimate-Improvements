package com.nao.questcycle.client.gui;

import com.nao.questcycle.client.ClientState;
import com.nao.questcycle.client.hud.ToastQueue;
import com.nao.questcycle.core.LeaderboardBuilder;
import com.nao.questcycle.data.QuestDef;
import com.nao.questcycle.data.QuestRegistry;
import com.nao.questcycle.data.QuestSection;
import com.nao.questcycle.data.TitleDef;
import com.nao.questcycle.network.PacketBuilder;
import com.nao.questcycle.network.QuestPacketHandler;
import com.nao.questcycle.task.NamedMatchTask;
import com.nao.questcycle.task.QuestTask;
import cpw.mods.fml.common.network.PacketDispatcher;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Single GuiScreen with four tabs. Pure drawRect-style rendering.
 * Layout: header tabs at top, left panel = scrollable category->quest list,
 * right panel = detail of the selected quest. Profile/Leaderboard tabs replace
 * both panels with their own dedicated layout.
 */
public final class QuestBookGui extends GuiScreen {
	private static final int W = 380;
	private static final int H = 230;
	private static final int TAB_H = 18;
	private static final int LIST_W = 170;
	private static final int LINE_H = 14;

	private int left;
	private int top;
	private Tab activeTab = Tab.PRESTIGE;
	private QuestDef selected;
	private int scrollList;
	private int scrollDetail;
	private int scrollLeader;
	private long lastLeaderboardRequestMs;

	enum Tab { PRESTIGE, ACHIEVEMENTS, PROFILE, LEADERBOARD }

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	public void initGui() {
		super.initGui();
		Keyboard.enableRepeatEvents(true);
		left = (width - W) / 2;
		top = (height - H) / 2;
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
	}

	@Override
	public void drawScreen(int mx, int my, float pt) {
		drawDefaultBackground();
		drawRect(left, top, left + W, top + H, GuiPalette.BG);
		drawBorder(left, top, W, H, GuiPalette.BORDER);

		drawTabs(mx, my);
		int contentTop = top + TAB_H + 4;
		int contentBottom = top + H - 4;
		switch (activeTab) {
			case PRESTIGE:
			case ACHIEVEMENTS: drawListAndDetail(mx, my, contentTop, contentBottom); break;
			case PROFILE: drawProfile(mx, my, contentTop, contentBottom); break;
			case LEADERBOARD: drawLeaderboard(mx, my, contentTop, contentBottom); break;
		}

		super.drawScreen(mx, my, pt);
	}

	// ---- tabs ------------------------------------------------------

	private void drawTabs(int mx, int my) {
		String[] labels = { "Prestige", "Achievements", "Profile", "Leaderboard" };
		int[] accents = { GuiPalette.ACCENT_PRESTIGE, GuiPalette.ACCENT_ACHIEV, GuiPalette.ACCENT_PROFILE, GuiPalette.ACCENT_LEADER };
		int tabW = (W - 4) / labels.length;
		FontRenderer fr = mc.fontRenderer;
		for (int i = 0; i < labels.length; i++) {
			int x = left + 2 + i * tabW;
			boolean active = activeTab.ordinal() == i;
			boolean hover = mx >= x && mx < x + tabW && my >= top + 2 && my < top + 2 + TAB_H;
			int bg = active ? GuiPalette.TAB_ACTIVE : (hover ? GuiPalette.TAB_HOVER : GuiPalette.TAB_IDLE);
			drawRect(x, top + 2, x + tabW - 1, top + 2 + TAB_H, bg);
			drawRect(x, top + 2 + TAB_H - 2, x + tabW - 1, top + 2 + TAB_H, accents[i]);
			int textColor = active ? GuiPalette.TEXT : GuiPalette.TEXT_DIM;
			drawCenteredString(fr, labels[i], x + tabW / 2, top + 6, textColor);
		}
	}

	private void clickTab(int mx, int my) {
		if (my < top + 2 || my > top + 2 + TAB_H) return;
		int tabW = (W - 4) / 4;
		int idx = (mx - left - 2) / tabW;
		if (idx < 0 || idx >= 4) return;
		Tab[] all = Tab.values();
		activeTab = all[idx];
		selected = null;
		scrollList = 0;
		scrollDetail = 0;
		if (activeTab == Tab.LEADERBOARD) requestLeaderboard();
	}

	// ---- list + detail (quests) -----------------------------------

	private void drawListAndDetail(int mx, int my, int yTop, int yBot) {
		int listX = left + 4;
		int listW = LIST_W;
		int detailX = left + 4 + listW + 4;
		int detailW = W - 8 - listW - 4;
		drawRect(listX, yTop, listX + listW, yBot, GuiPalette.PANEL);
		drawRect(detailX, yTop, detailX + detailW, yBot, GuiPalette.PANEL);
		drawBorder(listX, yTop, listW, yBot - yTop, GuiPalette.BORDER);
		drawBorder(detailX, yTop, detailW, yBot - yTop, GuiPalette.BORDER);

		drawList(mx, my, listX, yTop, listW, yBot - yTop);
		drawDetail(mx, my, detailX, yTop, detailW, yBot - yTop);
	}

	private List<Object> visibleRows() {
		QuestSection sec = activeTab == Tab.PRESTIGE ? QuestSection.PRESTIGE : QuestSection.ACHIEVEMENT;
		QuestRegistry reg = QuestRegistry.CURRENT;
		List<Object> rows = new ArrayList<Object>();
		List<String> cats = reg.categoriesOf(sec);
		for (int i = 0; i < cats.size(); i++) {
			String cat = cats.get(i);
			rows.add(new CategoryHeader(reg.categoryName(sec, cat)));
			List<QuestDef> qs = reg.questsInCategory(sec, cat);
			for (int j = 0; j < qs.size(); j++) rows.add(qs.get(j));
		}
		return rows;
	}

	private void drawList(int mx, int my, int x, int y, int w, int h) {
		FontRenderer fr = mc.fontRenderer;
		List<Object> rows = visibleRows();
		int rowH = LINE_H + 4;
		int visible = h / rowH;
		int maxScroll = Math.max(0, rows.size() - visible);
		if (scrollList > maxScroll) scrollList = maxScroll;
		int y0 = y + 2;
		for (int i = scrollList; i < Math.min(rows.size(), scrollList + visible); i++) {
			Object row = rows.get(i);
			int rowY = y0 + (i - scrollList) * rowH;
			if (row instanceof CategoryHeader) {
				drawString(fr, "§7§l" + ((CategoryHeader) row).name, x + 4, rowY + 3, GuiPalette.TEXT_DIM);
			} else {
				QuestDef q = (QuestDef) row;
				boolean hover = mx >= x && mx < x + w && my >= rowY && my < rowY + rowH;
				boolean isSelected = (selected != null && selected.id.equals(q.id));
				if (isSelected) drawRect(x + 1, rowY, x + w - 1, rowY + rowH - 2, GuiPalette.TAB_ACTIVE);
				else if (hover) drawRect(x + 1, rowY, x + w - 1, rowY + rowH - 2, GuiPalette.TAB_HOVER);
				int[] iconForQuest = resolveQuestIcon(q);
				drawItemIcon(iconForQuest[0], iconForQuest[1], x + 4, rowY + 1);
				int complete = questCompletePct(q);
				int nameColor = complete == 100 ? 0xFF6FE05F : GuiPalette.TEXT;
				String name = q.name;
				if (name.length() > 18) name = name.substring(0, 17) + "…";
				drawString(fr, name, x + 22, rowY + 4, nameColor);
				String tag = complete + "%";
				drawString(fr, tag, x + w - fr.getStringWidth(tag) - 4, rowY + 4, GuiPalette.TEXT_MUTED);
			}
		}
		if (maxScroll > 0) {
			int trackX = x + w - 4;
			drawRect(trackX, y + 2, trackX + 2, y + h - 2, GuiPalette.PROGRESS_BG);
			int thumbH = Math.max(8, (h - 4) * visible / Math.max(1, rows.size()));
			int thumbY = y + 2 + (h - 4 - thumbH) * scrollList / Math.max(1, maxScroll);
			drawRect(trackX, thumbY, trackX + 2, thumbY + thumbH, GuiPalette.BORDER_HI);
		}
	}

	private void clickList(int mx, int my, int x, int y, int w, int h) {
		List<Object> rows = visibleRows();
		int rowH = LINE_H + 4;
		int visible = h / rowH;
		int y0 = y + 2;
		for (int i = scrollList; i < Math.min(rows.size(), scrollList + visible); i++) {
			int rowY = y0 + (i - scrollList) * rowH;
			if (!(mx >= x && mx < x + w && my >= rowY && my < rowY + rowH)) continue;
			Object row = rows.get(i);
			if (row instanceof QuestDef) {
				selected = (QuestDef) row;
				scrollDetail = 0;
			}
			return;
		}
	}

	// ---- detail ---------------------------------------------------

	private void drawDetail(int mx, int my, int x, int y, int w, int h) {
		FontRenderer fr = mc.fontRenderer;
		if (selected == null) {
			drawCenteredString(fr, "Select a quest", x + w / 2, y + h / 2 - 4, GuiPalette.TEXT_MUTED);
			return;
		}
		int cursorY = y + 6;
		int[] questIcon = resolveQuestIcon(selected);
		drawItemIcon(questIcon[0], questIcon[1], x + 6, cursorY);
		drawString(fr, "§l" + selected.name, x + 26, cursorY + 4, GuiPalette.TEXT);
		cursorY += 22;

		// desc, wrapped
		List<String> lines = wrap(selected.desc, w - 12, fr);
		for (int i = 0; i < lines.size(); i++) {
			drawString(fr, lines.get(i), x + 6, cursorY, GuiPalette.TEXT_DIM);
			cursorY += LINE_H;
		}
		cursorY += 6;

		drawString(fr, "§7Tasks:", x + 6, cursorY, GuiPalette.TEXT_DIM);
		cursorY += LINE_H;
		for (int i = 0; i < selected.tasks.size(); i++) {
			QuestTask t = selected.tasks.get(i);
			drawTaskRow(x + 6, cursorY, w - 12, t, ClientState.taskCount(selected.id, i), selected);
			cursorY += 22;
		}

		// requires line, if any - wrapped so long lists don't bleed off the panel
		if (!selected.requires.isEmpty()) {
			cursorY += 4;
			StringBuilder sb = new StringBuilder("§7Requires: ");
			for (int i = 0; i < selected.requires.size(); i++) {
				if (i > 0) sb.append(", ");
				QuestDef r = QuestRegistry.CURRENT.get(selected.requires.get(i));
				sb.append(r == null ? selected.requires.get(i) : r.name);
			}
			List<String> lines2 = wrap(sb.toString(), w - 12, fr);
			for (int i = 0; i < lines2.size(); i++) {
				drawString(fr, lines2.get(i), x + 6, cursorY, GuiPalette.TEXT_MUTED);
				cursorY += LINE_H;
			}
		}

		if (selected.titleReward != null) {
			cursorY += 2;
			List<String> rewLines = wrap("§7Reward: §rTitle " + selected.titleReward.displayName, w - 12, fr);
			for (int i = 0; i < rewLines.size(); i++) {
				drawString(fr, rewLines.get(i), x + 6, cursorY, GuiPalette.TEXT_DIM);
				cursorY += LINE_H;
			}
		}
	}

	/**
	 * Resolve a quest's effective icon, preferring auto-resolution from task names so
	 * craft_named/obtain_named always show the real item, then falling back to any
	 * explicit JSON icon (which is usually a vanilla proxy).
	 */
	private static int[] resolveQuestIcon(QuestDef q) {
		for (int i = 0; i < q.tasks.size(); i++) {
			QuestTask t = q.tasks.get(i);
			if (t instanceof NamedMatchTask) {
				int[] hit = ItemNameIndex.lookup(((NamedMatchTask) t).displayLabel);
				if (hit != null) return hit;
			}
			if (t.targetItemId() > 0) return new int[]{t.targetItemId(), t.targetItemMeta()};
		}
		if (q.iconItemId > 0) return new int[]{q.iconItemId, q.iconItemMeta};
		return new int[]{0, 0};
	}

	private void drawTaskRow(int x, int y, int w, QuestTask t, int currentCount, QuestDef quest) {
		FontRenderer fr = mc.fontRenderer;
		// Task icon priority: explicit task icon, then auto-resolve from display name,
		// then the quest's icon as last-resort fallback.
		int iconId = t.targetItemId();
		int iconMeta = t.targetItemMeta();
		if (iconId <= 0) {
			int[] hit = ItemNameIndex.lookup(t.displayName());
			if (hit != null) { iconId = hit[0]; iconMeta = hit[1]; }
			else if (quest != null) { iconId = quest.iconItemId; iconMeta = quest.iconItemMeta; }
		}
		drawItemIcon(iconId, iconMeta, x, y);
		drawString(fr, t.displayName(), x + 22, y + 2, GuiPalette.TEXT);
		String progress = currentCount + "/" + t.targetCount();
		drawString(fr, progress, x + w - fr.getStringWidth(progress), y + 2, GuiPalette.TEXT_DIM);
		int barX = x + 22;
		int barW = w - 22 - fr.getStringWidth(progress) - 6;
		drawRect(barX, y + 13, barX + barW, y + 17, GuiPalette.PROGRESS_BG);
		int filled = (int) ((long) barW * currentCount / Math.max(1, t.targetCount()));
		int color = currentCount >= t.targetCount() ? GuiPalette.PROGRESS_DONE : GuiPalette.PROGRESS_FG;
		drawRect(barX, y + 13, barX + filled, y + 17, color);
	}

	// ---- profile ---------------------------------------------------

	private void drawProfile(int mx, int my, int yTop, int yBot) {
		FontRenderer fr = mc.fontRenderer;
		int x = left + 6;
		drawRect(x, yTop, left + W - 6, yBot, GuiPalette.PANEL);
		drawBorder(x, yTop, W - 12, yBot - yTop, GuiPalette.BORDER);
		int cy = yTop + 10;
		drawString(fr, "§6§lPrestige: §r§e" + ClientState.prestige, x + 10, cy, GuiPalette.TEXT);
		cy += LINE_H + 4;
		drawString(fr, "§7Active title: §r" + activeTitleDisplay(), x + 10, cy, GuiPalette.TEXT_DIM);
		cy += LINE_H + 8;
		drawString(fr, "§7Earned titles (" + ClientState.earnedTitleIds.size() + "):", x + 10, cy, GuiPalette.TEXT_DIM);
		cy += LINE_H + 2;
		for (String tid : ClientState.earnedTitleIds) {
			TitleDef def = QuestRegistry.CURRENT.title(tid);
			String label = def == null ? tid : def.displayName;
			boolean active = tid.equals(ClientState.activeTitleId);
			int btnX = x + W - 70;
			int btnY = cy - 2;
			drawString(fr, "  " + label, x + 10, cy, active ? 0xFF80FFA0 : GuiPalette.TEXT);
			boolean hover = mx >= btnX && mx < btnX + 50 && my >= btnY && my < btnY + 12;
			drawRect(btnX, btnY, btnX + 50, btnY + 12, hover ? GuiPalette.TAB_HOVER : GuiPalette.TAB_IDLE);
			drawCenteredString(fr, active ? "Clear" : "Set", btnX + 25, btnY + 2, GuiPalette.TEXT);
			cy += LINE_H + 2;
			if (cy > yBot - 12) break;
		}
	}

	private void clickProfile(int mx, int my, int yTop, int yBot) {
		int x = left + 6;
		int cy = yTop + 10 + LINE_H + 4 + LINE_H + 8 + LINE_H + 2;
		for (String tid : ClientState.earnedTitleIds) {
			int btnX = x + W - 70;
			int btnY = cy - 2;
			if (mx >= btnX && mx < btnX + 50 && my >= btnY && my < btnY + 12) {
				boolean active = tid.equals(ClientState.activeTitleId);
				String send = active ? "" : tid;
				PacketDispatcher.sendPacketToServer(QuestPacketHandler.wrap(PacketBuilder.setTitleRequest(send)));
				return;
			}
			cy += LINE_H + 2;
			if (cy > yBot - 12) break;
		}
	}

	private String activeTitleDisplay() {
		if (ClientState.activeTitleId == null) return "§7(none)";
		TitleDef d = QuestRegistry.CURRENT.title(ClientState.activeTitleId);
		return d == null ? ClientState.activeTitleId : d.displayName;
	}

	// ---- leaderboard ----------------------------------------------

	private void drawLeaderboard(int mx, int my, int yTop, int yBot) {
		FontRenderer fr = mc.fontRenderer;
		int x = left + 6;
		drawRect(x, yTop, left + W - 6, yBot, GuiPalette.PANEL);
		drawBorder(x, yTop, W - 12, yBot - yTop, GuiPalette.BORDER);

		// Shared column x-offsets relative to panel x. Single source so header + rows align.
		// Panel usable width ~368px; columns sized to comfortably fit content widths.
		final int COL_RANK   = 10;   // "Rank" ~25px → next col ~50
		final int COL_NAME   = 50;   // "Player" 6 chars × ~6px ~36px, max-name 16 chars × ~6 ~96
		final int COL_TITLE  = 150;  // "Title" can be wide (color codes free)
		final int COL_PREST  = 250;
		final int COL_TITLES = 290;
		final int COL_CYCLE  = 325;

		int hy = yTop + 6;
		drawString(fr, "§l§nRank",     x + COL_RANK,   hy, GuiPalette.TEXT_DIM);
		drawString(fr, "§l§nPlayer",   x + COL_NAME,   hy, GuiPalette.TEXT_DIM);
		drawString(fr, "§l§nTitle",    x + COL_TITLE,  hy, GuiPalette.TEXT_DIM);
		drawString(fr, "§l§nPrest",    x + COL_PREST,  hy, GuiPalette.TEXT_DIM);
		drawString(fr, "§l§nTitles",   x + COL_TITLES, hy, GuiPalette.TEXT_DIM);
		drawString(fr, "§l§nCycle",    x + COL_CYCLE,  hy, GuiPalette.TEXT_DIM);

		int cy = hy + LINE_H + 4;
		List<LeaderboardBuilder.Row> rows = ClientState.leaderboard;
		int rowH = LINE_H + 2;
		int visible = (yBot - cy) / rowH;
		int max = Math.max(0, rows.size() - visible);
		if (scrollLeader > max) scrollLeader = max;
		for (int i = scrollLeader; i < Math.min(rows.size(), scrollLeader + visible); i++) {
			LeaderboardBuilder.Row r = rows.get(i);
			boolean me = r.username.equals(ClientState.username);
			int col = me ? 0xFFFFDD66 : GuiPalette.TEXT;
			drawString(fr, "#" + (i + 1), x + COL_RANK, cy, col);
			drawString(fr, r.username, x + COL_NAME, cy, col);
			String title = r.activeTitleDisplay == null ? "" : r.activeTitleDisplay;
			drawString(fr, title, x + COL_TITLE, cy, GuiPalette.TEXT_DIM);
			drawString(fr, "§6" + r.prestige, x + COL_PREST, cy, 0xFFFFAA00);
			drawString(fr, "§7" + r.titlesCount, x + COL_TITLES, cy, GuiPalette.TEXT_DIM);
			drawString(fr, r.cyclePct + "%", x + COL_CYCLE, cy,
					r.cyclePct >= 100 ? 0xFF40DD60 : GuiPalette.TEXT_DIM);
			cy += rowH;
		}
		// auto-refresh
		long now = System.currentTimeMillis();
		if (now - lastLeaderboardRequestMs > 5000L) requestLeaderboard();
	}

	private void requestLeaderboard() {
		lastLeaderboardRequestMs = System.currentTimeMillis();
		PacketDispatcher.sendPacketToServer(QuestPacketHandler.wrap(PacketBuilder.leaderboardRequest(50)));
	}

	// ---- input ----------------------------------------------------

	@Override
	protected void mouseClicked(int mx, int my, int button) {
		if (button != 0) return;
		clickTab(mx, my);
		int yTop = top + TAB_H + 4;
		int yBot = top + H - 4;
		if (activeTab == Tab.PRESTIGE || activeTab == Tab.ACHIEVEMENTS) {
			int listX = left + 4;
			int listW = LIST_W;
			clickList(mx, my, listX, yTop, listW, yBot - yTop);
		} else if (activeTab == Tab.PROFILE) {
			clickProfile(mx, my, yTop, yBot);
		}
	}

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();
		int dWheel = Mouse.getEventDWheel();
		if (dWheel == 0) return;
		int dir = dWheel > 0 ? -1 : 1;
		if (activeTab == Tab.PRESTIGE || activeTab == Tab.ACHIEVEMENTS) scrollList = Math.max(0, scrollList + dir * 3);
		else if (activeTab == Tab.LEADERBOARD) scrollLeader = Math.max(0, scrollLeader + dir * 3);
	}

	@Override
	protected void keyTyped(char c, int code) {
		if (code == Keyboard.KEY_ESCAPE || code == com.nao.questcycle.client.QuestKeyHandler.KEY_OPEN_BOOK.keyCode) {
			mc.displayGuiScreen(null);
		}
	}

	// ---- helpers --------------------------------------------------

	private int questCompletePct(QuestDef q) {
		if (q.tasks.isEmpty()) return 0;
		int sum = 0;
		int target = 0;
		for (int i = 0; i < q.tasks.size(); i++) {
			QuestTask t = q.tasks.get(i);
			sum += Math.min(t.targetCount(), ClientState.taskCount(q.id, i));
			target += t.targetCount();
		}
		return target == 0 ? 0 : (int) ((sum * 100L) / target);
	}

	private static final RenderItem ITEM_RENDERER = new RenderItem();

	private void drawItemIcon(int itemId, int meta, int x, int y) {
		if (itemId <= 0 || itemId >= Item.itemsList.length || Item.itemsList[itemId] == null) return;
		ItemStack stack = new ItemStack(Item.itemsList[itemId], 1, meta < 0 ? 0 : meta);
		GL11.glEnable(GL11.GL_LIGHTING);
		ITEM_RENDERER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.renderEngine, stack, x, y);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glColor4f(1f, 1f, 1f, 1f);
	}

	private String displayNameOf(int itemId, int meta) {
		if (itemId <= 0 || itemId >= Item.itemsList.length || Item.itemsList[itemId] == null) return "Item#" + itemId;
		ItemStack stack = new ItemStack(Item.itemsList[itemId], 1, meta < 0 ? 0 : meta);
		try {
			String n = stack.getItem().getItemDisplayName(stack);
			if (n != null && n.length() > 0) return n;
		} catch (Throwable t) {}
		return "Item#" + itemId;
	}

	private List<String> wrap(String text, int maxPx, FontRenderer fr) {
		List<String> out = new ArrayList<String>();
		if (text == null || text.length() == 0) return out;
		String[] words = text.split(" ");
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			String w = words[i];
			String candidate = line.length() == 0 ? w : line + " " + w;
			if (fr.getStringWidth(candidate) > maxPx) {
				if (line.length() > 0) out.add(line.toString());
				line = new StringBuilder(w);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (line.length() > 0) out.add(line.toString());
		return out;
	}

	private static void drawBorder(int x, int y, int w, int h, int color) {
		// 1px-thick frame using 4 drawRect calls.
		drawRect(x, y, x + w, y + 1, color);
		drawRect(x, y + h - 1, x + w, y + h, color);
		drawRect(x, y, x + 1, y + h, color);
		drawRect(x + w - 1, y, x + w, y + h, color);
	}

	private static final class CategoryHeader {
		final String name;
		CategoryHeader(String name) { this.name = name; }
	}
}

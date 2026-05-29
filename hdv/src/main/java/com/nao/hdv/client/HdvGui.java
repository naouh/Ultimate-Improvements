package com.nao.hdv.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.nao.hdv.HdvMod;
import com.nao.hdv.client.ClientPacketHandler.Row;
import com.nao.hdv.network.PacketHandler;

import cpw.mods.fml.common.network.PacketDispatcher;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet250CustomPayload;

/**
 * The auction house screen. Two tabs: Buy (browse all listings, pick a quantity, purchase) and Sell
 * (pick an inventory item, set quantity + price, list it; plus your active listings with cancel).
 *
 * The screen is purely a view: it sends intent packets and renders the pages the server pushes back.
 * The server validates everything, so nothing here can dupe items or money.
 */
public class HdvGui extends GuiScreen {

	private static final int PANEL_W = 480;
	private static final int PANEL_H = 300;

	// button actions
	private static final int A_TAB_BUY = 1, A_TAB_SELL = 2;
	private static final int A_PREV = 3, A_NEXT = 4, A_REFRESH = 5, A_BUYOPEN = 6;
	private static final int A_MODAL_M10 = 7, A_MODAL_M1 = 8, A_MODAL_P1 = 9, A_MODAL_P10 = 10;
	private static final int A_MODAL_OK = 11, A_MODAL_CANCEL = 12;
	private static final int A_SELLSLOT = 13, A_SELL_M1 = 14, A_SELL_P1 = 15, A_SELL_MAX = 16, A_SELL_OK = 17;
	private static final int A_MINE_PREV = 18, A_MINE_NEXT = 19, A_CANCEL = 20;

	private final RenderItem itemRender = new RenderItem();

	private int px, py;
	private int tab = 0; // 0 = buy, 1 = sell

	private GuiTextField searchField;
	private GuiTextField priceField;

	private int sellSlot = -1;
	private int sellQty = 1;

	private boolean buyModal = false;
	private long buyId = -1;
	private Row buyRow;
	private int buyQty = 1;

	private static final class Btn {
		int x, y, w, h, action, color;
		long arg;
		String label;
	}
	private final List<Btn> buttons = new ArrayList<Btn>();

	@Override
	public void initGui() {
		Keyboard.enableRepeatEvents(true);
		px = (width - PANEL_W) / 2;
		py = (height - PANEL_H) / 2;
		searchField = new GuiTextField(fontRenderer, px + 12, py + 48, 180, 16);
		searchField.setMaxStringLength(40);
		priceField = new GuiTextField(fontRenderer, px + 12, py + 208, 170, 16);
		priceField.setMaxStringLength(12);
		priceField.setText("1");
		requestBrowse(0);
		requestMine(0);
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
	}

	@Override
	public boolean doesGuiPauseGame() { return false; }

	@Override
	public void updateScreen() {
		if (searchField != null) searchField.updateCursorCounter();
		if (priceField != null) priceField.updateCursorCounter();
	}

	// ---- networking ----

	private String searchText() {
		return searchField == null ? "" : searchField.getText().trim();
	}

	private void send(byte[] data) {
		Packet250CustomPayload pkt = new Packet250CustomPayload();
		pkt.channel = HdvMod.CHANNEL;
		pkt.data = data;
		pkt.length = data.length;
		PacketDispatcher.sendPacketToServer(pkt);
	}

	private void requestBrowse(int page) {
		send(PacketHandler.buildRequestList(page, searchText(), PacketHandler.KIND_BROWSE));
	}

	private void requestMine(int page) {
		send(PacketHandler.buildRequestList(page, "", PacketHandler.KIND_MINE));
	}

	// ---- input ----

	@Override
	protected void keyTyped(char c, int code) {
		if (buyModal) {
			if (code == Keyboard.KEY_ESCAPE) { buyModal = false; return; }
			if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) { confirmBuy(); return; }
			return;
		}
		if (code == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
		if (tab == 0 && searchField.isFocused()) {
			if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) { requestBrowse(0); return; }
			searchField.textboxKeyTyped(c, code);
			return;
		}
		if (tab == 1 && priceField.isFocused()) {
			priceField.textboxKeyTyped(c, code);
			return;
		}
	}

	@Override
	protected void mouseClicked(int mx, int my, int mbtn) {
		if (buyModal) {
			for (int i = 0; i < buttons.size(); i++) {
				Btn b = buttons.get(i);
				if (isModal(b.action) && hit(b, mx, my)) { handle(b); return; }
			}
			return; // modal swallows other clicks
		}
		if (tab == 0 && searchField != null) searchField.mouseClicked(mx, my, mbtn);
		if (tab == 1 && priceField != null) priceField.mouseClicked(mx, my, mbtn);
		for (int i = 0; i < buttons.size(); i++) {
			Btn b = buttons.get(i);
			if (hit(b, mx, my)) { handle(b); return; }
		}
	}

	private static boolean isModal(int a) {
		return a == A_MODAL_M10 || a == A_MODAL_M1 || a == A_MODAL_P1 || a == A_MODAL_P10
				|| a == A_MODAL_OK || a == A_MODAL_CANCEL;
	}

	private static boolean hit(Btn b, int mx, int my) {
		return mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h;
	}

	private void handle(Btn b) {
		switch (b.action) {
			case A_TAB_BUY:  tab = 0; requestBrowse(0); break;
			case A_TAB_SELL: tab = 1; requestMine(0); break;
			case A_PREV:     requestBrowse(ClientPacketHandler.browsePage - 1); break;
			case A_NEXT:     requestBrowse(ClientPacketHandler.browsePage + 1); break;
			case A_REFRESH:  requestBrowse(ClientPacketHandler.browsePage); break;
			case A_BUYOPEN:  openBuyModal(b.arg); break;
			case A_MODAL_M10: buyQty -= 10; clampBuyQty(); break;
			case A_MODAL_M1:  buyQty -= 1;  clampBuyQty(); break;
			case A_MODAL_P1:  buyQty += 1;  clampBuyQty(); break;
			case A_MODAL_P10: buyQty += 10; clampBuyQty(); break;
			case A_MODAL_OK:  confirmBuy(); break;
			case A_MODAL_CANCEL: buyModal = false; break;
			case A_SELLSLOT:  selectSellSlot((int) b.arg); break;
			case A_SELL_M1:   sellQty -= 1; clampSellQty(); break;
			case A_SELL_P1:   sellQty += 1; clampSellQty(); break;
			case A_SELL_MAX:  { ItemStack s = sellStack(); if (s != null) sellQty = s.stackSize; break; }
			case A_SELL_OK:   confirmSell(); break;
			case A_MINE_PREV: requestMine(ClientPacketHandler.minePage - 1); break;
			case A_MINE_NEXT: requestMine(ClientPacketHandler.minePage + 1); break;
			case A_CANCEL:    send(PacketHandler.buildCancel(b.arg)); requestMine(ClientPacketHandler.minePage); break;
			default: break;
		}
	}

	private ItemStack sellStack() {
		if (sellSlot < 0 || sellSlot >= 36) return null;
		return mc.thePlayer.inventory.mainInventory[sellSlot];
	}

	private void selectSellSlot(int slot) {
		ItemStack s = (slot >= 0 && slot < 36) ? mc.thePlayer.inventory.mainInventory[slot] : null;
		if (s == null) return;
		sellSlot = slot;
		sellQty = s.stackSize;
	}

	private void clampSellQty() {
		ItemStack s = sellStack();
		int max = s == null ? 1 : s.stackSize;
		if (sellQty < 1) sellQty = 1;
		if (sellQty > max) sellQty = max;
	}

	private void clampBuyQty() {
		int max = buyRow == null ? 1 : buyRow.qty;
		if (buyQty < 1) buyQty = 1;
		if (buyQty > max) buyQty = max;
	}

	private void openBuyModal(long id) {
		Row found = null;
		for (Row r : ClientPacketHandler.browseRows) {
			if (r.id == id) { found = r; break; }
		}
		if (found == null) return;
		buyRow = found;
		buyId = id;
		buyQty = 1;
		buyModal = true;
	}

	private void confirmBuy() {
		if (buyRow == null) { buyModal = false; return; }
		clampBuyQty();
		send(PacketHandler.buildBuy(buyId, buyQty));
		buyModal = false;
		requestBrowse(ClientPacketHandler.browsePage);
	}

	private void confirmSell() {
		ItemStack s = sellStack();
		if (s == null) return;
		clampSellQty();
		double price;
		try {
			price = Double.parseDouble(priceField.getText().trim());
		} catch (NumberFormatException e) {
			price = 0;
		}
		if (price <= 0) return;
		send(PacketHandler.buildSell(sellSlot, sellQty, price));
		sellSlot = -1;
		sellQty = 1;
		requestMine(ClientPacketHandler.minePage);
	}

	// ---- rendering ----

	@Override
	public void drawScreen(int mx, int my, float pt) {
		buttons.clear();
		drawDefaultBackground();
		drawRect(px, py, px + PANEL_W, py + PANEL_H, 0xF00E0E0E);
		drawRect(px, py, px + PANEL_W, py + 20, 0xFF1A1A1A);
		drawCenteredString(fontRenderer, "§eAUCTION HOUSE", px + PANEL_W / 2, py + 6, 0xFFFFFF);

		// tabs
		addButton(px + 12, py + 24, 110, 16, tab == 0 ? 0xFF2E8B57 : 0xFF333333, "Buy", A_TAB_BUY, 0);
		addButton(px + 128, py + 24, 110, 16, tab == 1 ? 0xFF2E8B57 : 0xFF333333, "Sell", A_TAB_SELL, 0);

		if (tab == 0) drawBuy(mx, my);
		else drawSell(mx, my);

		// draw all registered buttons (modal ones drawn later on top)
		for (int i = 0; i < buttons.size(); i++) {
			Btn b = buttons.get(i);
			if (!isModal(b.action)) drawButton(b, mx, my);
		}

		if (buyModal) drawBuyModal(mx, my);
	}

	private void drawBuy(int mx, int my) {
		searchField.drawTextBox();
		addButton(px + 200, py + 48, 70, 16, 0xFF335577, "Search", A_REFRESH, 0);
		addButton(px + 280, py + 48, 70, 16, 0xFF335577, "Refresh", A_REFRESH, 0);

		// column headers
		int hy = py + 70;
		drawString(fontRenderer, "§7Name", px + 30, hy, 0xFFFFFF);
		drawString(fontRenderer, "§7Seller", px + 210, hy, 0xFFFFFF);
		drawString(fontRenderer, "§7Qty", px + 286, hy, 0xFFFFFF);
		drawString(fontRenderer, "§7Price/u", px + 326, hy, 0xFFFFFF);

		Row[] rows = ClientPacketHandler.browseRows;
		int rowY = py + 82;
		int rowH = 18;
		for (int i = 0; i < rows.length; i++) {
			Row r = rows[i];
			int y = rowY + i * rowH;
			if ((i & 1) == 0) drawRect(px + 8, y, px + PANEL_W - 8, y + rowH - 1, 0x22FFFFFF);
			drawItem(r.stack, px + 10, y + 1);
			String name = r.stack == null ? "?" : r.stack.getDisplayName();
			drawString(fontRenderer, trim(name, 170), px + 30, y + 5, 0xFFFFFF);
			drawString(fontRenderer, trim(r.seller, 70), px + 210, y + 5, 0xFFCCCCCC);
			drawString(fontRenderer, String.valueOf(r.qty), px + 286, y + 5, 0xFFFFFF);
			drawString(fontRenderer, fmtMoney(r.price), px + 326, y + 5, 0xFFFFD24A);
			addButton(px + 400, y + 1, 70, 15, 0xFF2E8B57, "Buy", A_BUYOPEN, r.id);
		}

		// footer paging
		int fy = py + PANEL_H - 18;
		if (ClientPacketHandler.browsePage > 0) addButton(px + 12, fy, 60, 14, 0xFF444444, "< Prev", A_PREV, 0);
		drawCenteredString(fontRenderer,
				"Page " + (ClientPacketHandler.browsePage + 1) + "/" + ClientPacketHandler.browseTotalPages
						+ "  (" + ClientPacketHandler.browseTotal + ")",
				px + PANEL_W / 2, fy + 3, 0xFFAAAAAA);
		if (ClientPacketHandler.browsePage < ClientPacketHandler.browseTotalPages - 1)
			addButton(px + PANEL_W - 72, fy, 60, 14, 0xFF444444, "Next >", A_NEXT, 0);
	}

	private void drawSell(int mx, int my) {
		// left: inventory grid (36 main slots)
		drawString(fontRenderer, "§7Your inventory (click an item)", px + 12, py + 44, 0xFFFFFF);
		int gx = px + 12, gy = py + 56, cell = 18;
		ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
		for (int idx = 0; idx < 36; idx++) {
			int col = idx % 9, rowi = idx / 9;
			int x = gx + col * cell, y = gy + rowi * cell;
			int bg = (idx == sellSlot) ? 0xFF2E8B57 : 0xFF202020;
			drawRect(x, y, x + cell - 1, y + cell - 1, bg);
			if (inv[idx] != null) {
				drawItem(inv[idx], x + 1, y + 1);
				addButton(x, y, cell - 1, cell - 1, 0, null, A_SELLSLOT, idx); // invisible hit area
			}
		}

		// selected item + qty + price + list button
		int fx = px + 12;
		drawString(fontRenderer, "§7Selection:", fx, py + 136, 0xFFFFFF);
		ItemStack sel = sellStack();
		if (sel != null) {
			drawItem(sel, fx, py + 148);
			drawString(fontRenderer, trim(sel.getDisplayName(), 200), fx + 22, py + 152, 0xFFFFFF);
		} else {
			drawString(fontRenderer, "§8(none)", fx + 22, py + 152, 0xFF888888);
		}
		// qty controls
		int qy = py + 172;
		addButton(fx, qy, 16, 16, 0xFF553333, "-", A_SELL_M1, 0);
		drawCenteredString(fontRenderer, "Qty: §e" + sellQty, fx + 90, qy + 4, 0xFFFFFF);
		addButton(fx + 150, qy, 16, 16, 0xFF335533, "+", A_SELL_P1, 0);
		addButton(fx + 172, qy, 36, 16, 0xFF444444, "Max", A_SELL_MAX, 0);
		// price
		drawString(fontRenderer, "§7Price per unit:", fx, py + 196, 0xFFFFFF);
		priceField.drawTextBox();
		addButton(fx, py + 230, 170, 18, 0xFF2E8B57, "List for sale", A_SELL_OK, 0);

		// right: my listings
		int rx = px + 250;
		drawString(fontRenderer, "§eMy listings", rx, py + 44, 0xFFFFFF);
		Row[] mine = ClientPacketHandler.mineRows;
		int ry = py + 58, rh = 16;
		for (int i = 0; i < mine.length; i++) {
			Row r = mine[i];
			int y = ry + i * rh;
			drawItem(r.stack, rx, y + 1);
			String name = r.stack == null ? "?" : r.stack.getDisplayName();
			drawString(fontRenderer, trim(name, 70), rx + 20, y + 4, 0xFFFFFF);
			drawString(fontRenderer, "x" + r.qty, rx + 96, y + 4, 0xFFCCCCCC);
			drawString(fontRenderer, fmtMoney(r.price), rx + 128, y + 4, 0xFFFFD24A);
			addButton(px + PANEL_W - 58, y + 1, 52, 14, 0xFF8B2E2E, "Cancel", A_CANCEL, r.id);
		}
		int fy = py + PANEL_H - 18;
		if (ClientPacketHandler.minePage > 0) addButton(rx, fy, 50, 14, 0xFF444444, "< Prev", A_MINE_PREV, 0);
		drawString(fontRenderer, "Page " + (ClientPacketHandler.minePage + 1) + "/" + ClientPacketHandler.mineTotalPages,
				rx + 60, fy + 3, 0xFFAAAAAA);
		if (ClientPacketHandler.minePage < ClientPacketHandler.mineTotalPages - 1)
			addButton(px + PANEL_W - 62, fy, 50, 14, 0xFF444444, "Next >", A_MINE_NEXT, 0);
	}

	private void drawBuyModal(int mx, int my) {
		drawRect(0, 0, width, height, 0xC0000000);
		int mw = 260, mh = 130;
		int mxp = (width - mw) / 2, myp = (height - mh) / 2;
		drawRect(mxp, myp, mxp + mw, myp + mh, 0xFF101418);
		drawRect(mxp + 1, myp + 1, mxp + mw - 1, myp + 2, 0xFF606060);
		drawCenteredString(fontRenderer, "Buy", width / 2, myp + 8, 0xFFFFFF);

		if (buyRow != null) {
			drawItem(buyRow.stack, mxp + 12, myp + 24);
			String name = buyRow.stack == null ? "?" : buyRow.stack.getDisplayName();
			drawString(fontRenderer, trim(name, mw - 50), mxp + 34, myp + 28, 0xFFFFFF);
			drawString(fontRenderer, "§7Avail: §f" + buyRow.qty + "   §7Price/u: §e" + fmtMoney(buyRow.price),
					mxp + 12, myp + 46, 0xFFFFFF);
			// qty steppers
			int qy = myp + 62;
			addModalBtn(mxp + 12, qy, 26, 16, 0xFF553333, "-10", A_MODAL_M10);
			addModalBtn(mxp + 40, qy, 22, 16, 0xFF553333, "-1", A_MODAL_M1);
			drawCenteredString(fontRenderer, "§e" + buyQty, mxp + mw / 2, qy + 4, 0xFFFFFF);
			addModalBtn(mxp + mw - 62, qy, 22, 16, 0xFF335533, "+1", A_MODAL_P1);
			addModalBtn(mxp + mw - 38, qy, 26, 16, 0xFF335533, "+10", A_MODAL_P10);
			drawCenteredString(fontRenderer, "Total: §e" + fmtMoney(buyQty * buyRow.price),
					width / 2, myp + 86, 0xFFFFD24A);
			addModalBtn(mxp + 12, myp + mh - 24, 110, 18, 0xFF2E8B57, "Confirm", A_MODAL_OK);
			addModalBtn(mxp + mw - 122, myp + mh - 24, 110, 18, 0xFF555555, "Cancel", A_MODAL_CANCEL);
		}

		for (int i = 0; i < buttons.size(); i++) {
			Btn b = buttons.get(i);
			if (isModal(b.action)) drawButton(b, mx, my);
		}
	}

	// ---- button helpers ----

	private void addButton(int x, int y, int w, int h, int color, String label, int action, long arg) {
		Btn b = new Btn();
		b.x = x; b.y = y; b.w = w; b.h = h; b.color = color; b.label = label; b.action = action; b.arg = arg;
		buttons.add(b);
	}

	private void addModalBtn(int x, int y, int w, int h, int color, String label, int action) {
		addButton(x, y, w, h, color, label, action, 0);
	}

	private void drawButton(Btn b, int mx, int my) {
		if (b.color == 0 && b.label == null) return; // invisible hit area
		int color = b.color;
		if (hit(b, mx, my)) color = brighten(color);
		drawRect(b.x, b.y, b.x + b.w, b.y + b.h, 0xFF101418);
		drawRect(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + b.h - 1, color);
		if (b.label != null) drawCenteredString(fontRenderer, b.label, b.x + b.w / 2, b.y + (b.h - 8) / 2, 0xFFFFFF);
	}

	// ---- item icon rendering ----

	private void drawItem(ItemStack stack, int x, int y) {
		if (stack == null) return;
		GL11.glColor4f(1F, 1F, 1F, 1F);
		RenderHelper.enableGUIStandardItemLighting();
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		itemRender.zLevel = 100.0F;
		itemRender.renderItemAndEffectIntoGUI(fontRenderer, mc.renderEngine, stack, x, y);
		itemRender.renderItemOverlayIntoGUI(fontRenderer, mc.renderEngine, stack, x, y);
		itemRender.zLevel = 0.0F;
		RenderHelper.disableStandardItemLighting();
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glColor4f(1F, 1F, 1F, 1F);
	}

	// ---- small utils ----

	private String trim(String s, int maxW) {
		if (s == null) return "";
		if (fontRenderer.getStringWidth(s) <= maxW) return s;
		while (s.length() > 1 && fontRenderer.getStringWidth(s + "..") > maxW) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "..";
	}

	private static String fmtMoney(double v) {
		if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
		return String.valueOf(Math.round(v * 100.0) / 100.0);
	}

	private static int brighten(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = Math.min(255, ((argb >> 16) & 0xFF) + 30);
		int g = Math.min(255, ((argb >> 8) & 0xFF) + 30);
		int b = Math.min(255, (argb & 0xFF) + 30);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}

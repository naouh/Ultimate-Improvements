package com.favouredcraft.serverlist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/**
 * Custom slot list that renders each server with a bundled icon and a multi-line, colored MOTD.
 * Only internet servers are listed (LAN scanning is intentionally not reimplemented).
 */
public class GuiSlotFavoured extends GuiSlot {

	/** Total height of one entry; tall enough for icon + name + 2 MOTD lines + address. */
	static final int SLOT_HEIGHT = 48;

	private final GuiFavouredServerList parent;

	public GuiSlotFavoured(GuiFavouredServerList parent) {
		super(Minecraft.getMinecraft(), parent.width, parent.height, 32, parent.height - 64, SLOT_HEIGHT);
		this.parent = parent;
	}

	@Override
	protected int getSize() {
		return parent.serverList.countServers();
	}

	@Override
	protected void elementClicked(int index, boolean doubleClicked) {
		parent.selectedServer = index;
		boolean valid = index >= 0 && index < getSize();
		ServerData data = valid ? parent.serverList.getServerData(index) : null;
		boolean joinable = valid && (data == null || data.field_82821_f == 51);

		parent.buttonSelect.enabled = joinable;
		parent.buttonEdit.enabled = valid;
		parent.buttonDelete.enabled = valid;

		if (doubleClicked && joinable) {
			parent.joinServer(index);
		}
	}

	@Override
	protected boolean isSelected(int index) {
		return index == parent.selectedServer;
	}

	@Override
	protected void drawBackground() {
		parent.drawScreenBackground();
	}

	@Override
	protected void drawSlot(int index, int x, int y, int contentHeight, Tessellator tessellator) {
		ServerData data = parent.serverList.getServerData(index);
		FavouredPinger.tryPing(data);

		Minecraft mc = Minecraft.getMinecraft();
		FontRenderer fr = mc.fontRenderer;

		int textX = x + 2;
		if (FavouredConfig.showIconFor(data)) {
			drawIcon(mc, x, y, contentHeight, FavouredConfig.ICON_SIZE);
			textX = x + FavouredConfig.ICON_SIZE + 4;
		}

		// Server name (top line).
		fr.drawString(data.serverName, textX, y + 1, 0xFFFFFF);

		// Population, right-aligned on the name line, kept clear of the ping bars (drawn at x+205).
		if (data.populationInfo != null) {
			int popX = x + 200 - fr.getStringWidth(data.populationInfo);
			fr.drawString(data.populationInfo, popX, y + 1, 0x808080);
		}

		// MOTD: split on newlines and draw each line on its own row.
		String motd = data.serverMOTD == null ? "" : data.serverMOTD;
		String[] lines = motd.split("\n");
		int lineY = y + 12;
		for (int i = 0; i < lines.length && i < FavouredConfig.MAX_MOTD_LINES; i++) {
			fr.drawString(lines[i], textX, lineY, 0xA0A0A0);
			lineY += 10;
		}

		// Address (bottom line).
		String address;
		if (!mc.gameSettings.hideServerAddress && !data.isHidingAddress()) {
			address = data.serverIP;
		} else {
			address = "Hidden Address";
		}
		fr.drawString(address, textX, y + contentHeight - 9, 0x707070);

		drawPing(mc, data, index, x, y);
	}

	/** Binds and draws the bundled server icon scaled to {@code size} pixels, vertically centered. */
	private void drawIcon(Minecraft mc, int x, int y, int contentHeight, int size) {
		int texture = mc.renderEngine.getTexture(FavouredConfig.ICON_PATH);
		if (texture <= 0) {
			return;
		}
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		mc.renderEngine.bindTexture(texture);
		int top = y + (contentHeight - size) / 2;
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.addVertexWithUV(x, top + size, 0.0D, 0.0D, 1.0D);
		t.addVertexWithUV(x + size, top + size, 0.0D, 1.0D, 1.0D);
		t.addVertexWithUV(x + size, top, 0.0D, 1.0D, 0.0D);
		t.addVertexWithUV(x, top, 0.0D, 0.0D, 0.0D);
		t.draw();
	}

	/** Draws the vanilla-style ping bars from /gui/icons.png and sets the hover tooltip. */
	private void drawPing(Minecraft mc, ServerData data, int index, int x, int y) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		mc.renderEngine.bindTexture(mc.renderEngine.getTexture("/gui/icons.png"));

		int column = 0;
		int row;
		String tooltip;
		boolean wrongVersion = data.field_82821_f != 51;

		if (wrongVersion) {
			tooltip = data.field_82821_f > 51 ? "Client out of date!" : "Server out of date!";
			row = 5;
		} else if (data.field_78841_f && data.pingToServer != -2L) {
			long ping = data.pingToServer;
			if (ping < 0L) {
				row = 5;
				tooltip = "(no connection)";
			} else {
				row = ping < 150L ? 0 : ping < 300L ? 1 : ping < 600L ? 2 : ping < 1000L ? 3 : 4;
				tooltip = ping + "ms";
			}
		} else {
			column = 1;
			row = (int) (Minecraft.getSystemTime() / 100L + (long) (index * 2) & 7L);
			if (row > 4) {
				row = 8 - row;
			}
			tooltip = "Polling..";
		}

		drawTexturedRect(x + 205, y, column * 10, 176 + row * 8, 10, 8);

		int pad = 4;
		if (this.mouseX >= x + 205 - pad && this.mouseY >= y - pad
				&& this.mouseX <= x + 205 + 10 + pad && this.mouseY <= y + 8 + pad) {
			parent.setLagTooltip(tooltip);
		}
	}

	/** Draws a rectangle from a 256x256 texture sheet (mirrors Gui.drawTexturedModalRect). */
	private void drawTexturedRect(int x, int y, int u, int v, int w, int h) {
		float scale = 1.0F / 256.0F;
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.addVertexWithUV(x, y + h, 0.0D, u * scale, (v + h) * scale);
		t.addVertexWithUV(x + w, y + h, 0.0D, (u + w) * scale, (v + h) * scale);
		t.addVertexWithUV(x + w, y, 0.0D, (u + w) * scale, v * scale);
		t.addVertexWithUV(x, y, 0.0D, u * scale, v * scale);
		t.draw();
	}
}

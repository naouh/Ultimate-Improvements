package com.favouredcraft.serverlist;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenAddServer;
import net.minecraft.client.gui.GuiScreenServerList;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

import org.lwjgl.input.Keyboard;

/**
 * Drop-in replacement for the vanilla 1.4.7 multiplayer screen. Behaviour mirrors GuiMultiplayer
 * (join / add / edit / delete / direct connect / refresh), but the server list is drawn by
 * {@link GuiSlotFavoured} which adds an icon and a multi-line MOTD.
 */
public class GuiFavouredServerList extends GuiScreen {

	private final GuiScreen parentScreen;

	// Package-visible so GuiSlotFavoured can read/update them.
	ServerList serverList;
	int selectedServer = -1;
	GuiButton buttonSelect;
	GuiButton buttonEdit;
	GuiButton buttonDelete;

	private GuiSlotFavoured slotContainer;
	private ServerData pendingServerData;
	private boolean deleteClicked;
	private boolean addClicked;
	private boolean editClicked;
	private boolean directClicked;
	private String lagTooltip;

	public GuiFavouredServerList(GuiScreen parentScreen) {
		this.parentScreen = parentScreen;
	}

	@Override
	public void initGui() {
		Keyboard.enableRepeatEvents(true);
		this.controlList.clear();

		if (this.serverList == null) {
			this.serverList = new ServerList(this.mc);
			FavouredPinger.reset();
		}

		this.slotContainer = new GuiSlotFavoured(this);

		this.buttonSelect = new GuiButton(1, this.width / 2 - 154, this.height - 52, 100, 20, "Join Server");
		this.controlList.add(this.buttonSelect);
		this.controlList.add(new GuiButton(4, this.width / 2 - 50, this.height - 52, 100, 20, "Direct Connect"));
		this.controlList.add(new GuiButton(3, this.width / 2 + 4 + 50, this.height - 52, 100, 20, "Add server"));
		this.buttonEdit = new GuiButton(7, this.width / 2 - 154, this.height - 28, 70, 20, "Edit");
		this.controlList.add(this.buttonEdit);
		this.buttonDelete = new GuiButton(2, this.width / 2 - 74, this.height - 28, 70, 20, "Delete");
		this.controlList.add(this.buttonDelete);
		this.controlList.add(new GuiButton(8, this.width / 2 + 4, this.height - 28, 70, 20, "Refresh"));
		this.controlList.add(new GuiButton(0, this.width / 2 + 4 + 76, this.height - 28, 75, 20, "Cancel"));

		boolean hasSelection = this.selectedServer >= 0 && this.selectedServer < this.slotContainer.getSize();
		this.buttonSelect.enabled = hasSelection;
		this.buttonEdit.enabled = hasSelection;
		this.buttonDelete.enabled = hasSelection;
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (!button.enabled) {
			return;
		}

		switch (button.id) {
			case 2: { // Delete
				ServerData data = this.serverList.getServerData(this.selectedServer);
				if (data != null) {
					this.deleteClicked = true;
					this.mc.displayGuiScreen(new GuiYesNo(this,
							"Are you sure you want to remove this server?",
							"'" + data.serverName + "' will be removed permanently!",
							"Delete", "Cancel", this.selectedServer));
				}
				break;
			}
			case 1: // Join
				this.joinServer(this.selectedServer);
				break;
			case 4: // Direct connect
				this.directClicked = true;
				this.pendingServerData = new ServerData("Minecraft Server", "");
				this.mc.displayGuiScreen(new GuiScreenServerList(this, this.pendingServerData));
				break;
			case 3: // Add server
				this.addClicked = true;
				this.pendingServerData = new ServerData("Minecraft Server", "");
				this.mc.displayGuiScreen(new GuiScreenAddServer(this, this.pendingServerData));
				break;
			case 7: { // Edit
				this.editClicked = true;
				ServerData data = this.serverList.getServerData(this.selectedServer);
				this.pendingServerData = new ServerData(data.serverName, data.serverIP);
				this.pendingServerData.setHideAddress(data.isHidingAddress());
				this.mc.displayGuiScreen(new GuiScreenAddServer(this, this.pendingServerData));
				break;
			}
			case 0: // Cancel
				this.mc.displayGuiScreen(this.parentScreen);
				break;
			case 8: // Refresh
				this.mc.displayGuiScreen(new GuiFavouredServerList(this.parentScreen));
				break;
			default:
				this.slotContainer.actionPerformed(button);
				break;
		}
	}

	@Override
	public void confirmClicked(boolean result, int id) {
		if (this.deleteClicked) {
			this.deleteClicked = false;
			if (result) {
				this.serverList.removeServerData(id);
				this.serverList.saveServerList();
				this.selectedServer = -1;
			}
			this.mc.displayGuiScreen(this);
		} else if (this.directClicked) {
			this.directClicked = false;
			if (result) {
				this.connect(this.pendingServerData);
			} else {
				this.mc.displayGuiScreen(this);
			}
		} else if (this.addClicked) {
			this.addClicked = false;
			if (result) {
				this.serverList.addServerData(this.pendingServerData);
				this.serverList.saveServerList();
				this.selectedServer = -1;
			}
			this.mc.displayGuiScreen(this);
		} else if (this.editClicked) {
			this.editClicked = false;
			if (result) {
				ServerData data = this.serverList.getServerData(this.selectedServer);
				data.serverName = this.pendingServerData.serverName;
				data.serverIP = this.pendingServerData.serverIP;
				data.setHideAddress(this.pendingServerData.isHidingAddress());
				this.serverList.saveServerList();
			}
			this.mc.displayGuiScreen(this);
		}
	}

	@Override
	protected void keyTyped(char character, int keyCode) {
		if (keyCode == Keyboard.KEY_ESCAPE) {
			this.mc.displayGuiScreen(this.parentScreen);
		} else {
			super.keyTyped(character, keyCode);
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.lagTooltip = null;
		this.drawDefaultBackground();
		this.slotContainer.drawScreen(mouseX, mouseY, partialTicks);
		this.drawCenteredString(this.fontRenderer, "Play Multiplayer", this.width / 2, 20, 0xFFFFFF);
		super.drawScreen(mouseX, mouseY, partialTicks);

		if (this.lagTooltip != null) {
			this.drawLagTooltip(this.lagTooltip, mouseX, mouseY);
		}
	}

	void joinServer(int index) {
		if (index >= 0 && index < this.serverList.countServers()) {
			this.connect(this.serverList.getServerData(index));
		}
	}

	private void connect(ServerData data) {
		this.mc.displayGuiScreen(new GuiConnecting(this.mc, data));
	}

	/** Exposes the protected background drawing to GuiSlotFavoured (different package). */
	public void drawScreenBackground() {
		this.drawDefaultBackground();
	}

	/** Called by GuiSlotFavoured when the mouse hovers a ping icon. */
	void setLagTooltip(String tooltip) {
		this.lagTooltip = tooltip;
	}

	private void drawLagTooltip(String text, int mouseX, int mouseY) {
		int x = mouseX + 12;
		int y = mouseY - 12;
		int width = this.fontRenderer.getStringWidth(text);
		this.drawGradientRect(x - 3, y - 3, x + width + 3, y + 8 + 3, 0xC0000000, 0xC0000000);
		this.fontRenderer.drawStringWithShadow(text, x, y, -1);
	}
}

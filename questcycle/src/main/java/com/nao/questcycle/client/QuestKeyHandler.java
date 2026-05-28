package com.nao.questcycle.client;

import com.nao.questcycle.client.gui.QuestBookGui;
import cpw.mods.fml.client.registry.KeyBindingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Default keybind 'Q' opens the QuestCycle book. Players can rebind via Controls menu. */
public final class QuestKeyHandler extends KeyBindingRegistry.KeyHandler {
	public static final KeyBinding KEY_OPEN_BOOK = new KeyBinding("QuestCycle Book", Keyboard.KEY_Q);

	public QuestKeyHandler() {
		super(new KeyBinding[] { KEY_OPEN_BOOK }, new boolean[] { false });
	}

	@Override
	public String getLabel() {
		return "QuestCycle.KeyHandler";
	}

	@Override
	public void keyDown(java.util.EnumSet<cpw.mods.fml.common.TickType> type, KeyBinding kb, boolean tickEnd, boolean isRepeat) {
		if (!tickEnd) return;
		if (kb != KEY_OPEN_BOOK) return;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null) return;
		if (mc.currentScreen != null) return;
		mc.displayGuiScreen(new QuestBookGui());
	}

	@Override
	public void keyUp(java.util.EnumSet<cpw.mods.fml.common.TickType> type, KeyBinding kb, boolean tickEnd) {
	}

	@Override
	public java.util.EnumSet<cpw.mods.fml.common.TickType> ticks() {
		return java.util.EnumSet.of(cpw.mods.fml.common.TickType.CLIENT);
	}
}

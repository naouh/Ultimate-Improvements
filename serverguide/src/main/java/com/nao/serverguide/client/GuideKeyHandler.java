package com.nao.serverguide.client;

import com.nao.serverguide.client.gui.GuideGui;

import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.common.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Default keybind 'G' opens the Server Guide. Players can rebind it via the Controls menu. */
public final class GuideKeyHandler extends KeyBindingRegistry.KeyHandler {
    public static final KeyBinding KEY_OPEN_GUIDE = new KeyBinding("Server Guide", Keyboard.KEY_G);

    public GuideKeyHandler() {
        super(new KeyBinding[] { KEY_OPEN_GUIDE }, new boolean[] { false });
    }

    @Override
    public String getLabel() {
        return "ServerGuide.KeyHandler";
    }

    @Override
    public void keyDown(java.util.EnumSet<TickType> type, KeyBinding kb, boolean tickEnd, boolean isRepeat) {
        if (!tickEnd) return;
        if (kb != KEY_OPEN_GUIDE) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        if (mc.currentScreen != null) return;
        mc.displayGuiScreen(new GuideGui());
    }

    @Override
    public void keyUp(java.util.EnumSet<TickType> type, KeyBinding kb, boolean tickEnd) {}

    @Override
    public java.util.EnumSet<TickType> ticks() {
        return java.util.EnumSet.of(TickType.CLIENT);
    }
}

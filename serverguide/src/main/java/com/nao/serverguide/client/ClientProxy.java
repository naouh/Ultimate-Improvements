package com.nao.serverguide.client;

import com.nao.serverguide.CommonProxy;
import com.nao.serverguide.client.gui.GuideGui;

import cpw.mods.fml.client.registry.KeyBindingRegistry;
import net.minecraft.client.Minecraft;

public class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        super.init();
        KeyBindingRegistry.registerKeyBinding(new GuideKeyHandler());
    }

    @Override
    public void openGuide() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        mc.displayGuiScreen(new GuideGui());
    }
}

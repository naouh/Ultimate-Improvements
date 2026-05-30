package com.cagecontrol;

import cpw.mods.fml.client.registry.KeyBindingRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.client.Minecraft;

public class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        super.init();
        KeyBindingRegistry.registerKeyBinding(new CageKeyHandler());
        TickRegistry.registerTickHandler(new CageKeyTickHandler(), Side.CLIENT);
    }

    @Override
    public void openCageGui(int x, int y, int z) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiCageName(x, y, z));
    }
}

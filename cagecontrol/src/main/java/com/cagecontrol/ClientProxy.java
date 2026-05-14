package com.cagecontrol;

import net.minecraft.client.Minecraft;

public class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        super.init();
    }

    @Override
    public void openCageGui(int x, int y, int z) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiCageName(x, y, z));
    }
}

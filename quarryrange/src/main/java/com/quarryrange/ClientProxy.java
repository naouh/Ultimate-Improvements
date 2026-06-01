package com.quarryrange;

import net.minecraft.client.Minecraft;

public class ClientProxy extends CommonProxy {
    @Override
    public void init() {
        super.init();
    }

    @Override
    public void openEditor(int x, int y, int z, int meta, int curSize, int anchor, int min, int max) {
        Minecraft.getMinecraft().displayGuiScreen(
                new GuiQuarryEditor(x, y, z, meta, curSize, anchor, min, max));
    }
}

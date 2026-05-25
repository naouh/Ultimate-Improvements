package com.nao.windowitemsfix;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Fixes a client crash where Packet104 (WindowItems) is dispatched to a
 * Container whose inventorySlots list is still empty — happens with mods
 * (e.g. GregTech-Addon) that briefly expose a placeholder block before the
 * TileEntity sync packet arrives after teleport/login.
 *
 * Patches Container.putStacksInSlots to bail when there are no slots,
 * so the bogus packet is silently dropped instead of crashing the client.
 */
public class WindowItemsFixCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getLibraryRequestClass() { return null; }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
            "com.nao.windowitemsfix.ContainerTransformer",
            "com.nao.windowitemsfix.DisplayGuiScreenTransformer"
        };
    }

    @Override
    public String getModContainerClass() { return null; }

    @Override
    public String getSetupClass() { return null; }

    @Override
    public void injectData(Map<String, Object> data) {}
}

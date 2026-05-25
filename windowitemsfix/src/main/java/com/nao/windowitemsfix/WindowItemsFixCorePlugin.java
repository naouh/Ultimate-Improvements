package com.nao.windowitemsfix;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Fixes the post-teleport / post-login interaction race with TileEntity-backed
 * blocks (notably GregTech machines).
 *
 * After a chunk is sent, block IDs arrive before the TileEntity sync packets.
 * Right-clicking a machine during that window goes to the server, which sends
 * back open-window + slot-update packets that the client mishandles — either
 * crashing (IndexOutOfBoundsException in Container.putStacksInSlots) or
 * leaking fake items into the real inventory once the crash is patched.
 *
 * Two transformers:
 * <ul>
 *   <li>{@code RightClickGuardTransformer} — primary fix: swallows the
 *       right-click client-side when the targeted block has no usable TE yet
 *       (null TE on a BlockContainer, or a GregTech BaseMetaTileEntity whose
 *       {@code mMetaTileEntity} hasn't been populated by Packet132 yet).</li>
 *   <li>{@code ContainerTransformer} — safety net: early-returns from
 *       {@code Container.putStacksInSlots} when the slot list is empty,
 *       preventing the original IOOBE crash if a 0-slot WindowItems packet
 *       somehow reaches the client through any other path.</li>
 * </ul>
 */
public class WindowItemsFixCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getLibraryRequestClass() { return null; }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
            "com.nao.windowitemsfix.RightClickGuardTransformer",
            "com.nao.windowitemsfix.ContainerTransformer"
        };
    }

    @Override
    public String getModContainerClass() { return null; }

    @Override
    public String getSetupClass() { return null; }

    @Override
    public void injectData(Map<String, Object> data) {}
}

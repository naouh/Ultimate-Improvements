package com.nao.mystutils;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Coremod entry for Myst Utils.
 *
 * <p>It registers two client-side ASM transformers that add a search bar to the Mystcraft Writing
 * Desk. The plain {@code @Mod} half (the {@code /instabilities} command) is loaded from this same
 * jar because the manifest sets {@code FMLCorePluginContainsFMLMod: true}.
 *
 * <p>Both transformers are fail-safe: if their target class/method isn't found at load time (a
 * different Mystcraft version, Mystcraft absent, a dedicated server with no GUI classes) they log
 * and return the original bytes, so the pack keeps running. This class and the transformers must
 * never reference Minecraft classes — they load before Minecraft is available.
 */
public class MystUtilsCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getLibraryRequestClass() { return null; }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
            "com.nao.mystutils.asm.GuiPageSurfaceTransformer",
            "com.nao.mystutils.asm.GuiWritingDeskTransformer"
        };
    }

    @Override
    public String getModContainerClass() { return null; }

    @Override
    public String getSetupClass() { return null; }

    @Override
    public void injectData(Map<String, Object> data) {}
}

package com.nao.mpsfix;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Forge coremod entry point. FML reads the {@code FMLCorePlugin} manifest
 * attribute, instantiates this class before any mod is constructed, and
 * registers the transformers returned by {@link #getASMTransformerClass()}.
 *
 * <p>The transformers fire whenever the JVM loads the targeted classes
 * (one for MPS' {@code PlayerTickHandler}, one for {@code EntityPlayer}).
 *
 * <p>{@code FMLCorePluginContainsFMLMod=true} tells FML the jar also
 * contains a regular {@code @Mod} class (our {@link MpsFlightFixMod}),
 * which handles Air Stride module registration during the Forge lifecycle.
 */
public class FlightFixCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getLibraryRequestClass() { return null; }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
            "com.nao.mpsfix.transform.PlayerTickHandlerTransformer",
            "com.nao.mpsfix.transform.EntityPlayerTransformer",
            "com.nao.mpsfix.transform.PowerToolInterfaceTransformer"
        };
    }

    @Override
    public String getModContainerClass() { return null; }

    @Override
    public String getSetupClass() { return null; }

    @Override
    public void injectData(Map<String, Object> data) {}
}

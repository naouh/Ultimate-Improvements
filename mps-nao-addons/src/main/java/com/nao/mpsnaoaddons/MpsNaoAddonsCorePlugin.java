package com.nao.mpsnaoaddons;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Forge coremod entry point. FML reads the {@code FMLCorePlugin} manifest
 * attribute, instantiates this class before any mod is constructed, and
 * registers the transformers returned by {@link #getASMTransformerClass()}.
 *
 * <p>Seven transformers fire on JVM class-load for their respective targets:
 * <ul>
 *   <li>{@code PlayerTickHandlerTransformer} — patches MPS' tick handler so
 *       Flight Control no longer applies the ground-friction penalty (the
 *       fix bundled from the standalone {@code mpsflightfix} mod).</li>
 *   <li>{@code EntityPlayerTransformer} — patches the airborne dig-speed
 *       penalty so the Air Stride helmet module can bypass it.</li>
 *   <li>{@code PowerToolInterfaceTransformer} — bolts BC/MFR/Railcraft/UE
 *       wrench interfaces onto {@code ItemPowerTool} for the OmniWrench
 *       module.</li>
 *   <li>{@code GuiTinkerTableTransformer} — enlarges the Tinker Table GUI
 *       so our Tool-category modules fit without their hover tooltips
 *       overflowing onto the icon column.</li>
 *   <li>{@code MEWirelessAccessTransformer} — patches Applied Energistics'
 *       wireless slot + controller-encode methods so the power tool with
 *       the ME Wireless Terminal module can be linked to a network.</li>
 *   <li>{@code MuseRendererTransformer} — intercepts MPS' icon draw entry
 *       points to detect {@code ItemMuseIcon} (our subclass storing an
 *       ItemStack) and render the actual item texture in place of the
 *       generic MuseIcon sprite, both in the Tinker Table grid and the
 *       mode-switcher HUD above the hotbar.</li>
 *   <li>{@code MuseItemUtilsTransformer} — adds a salvage hook so removing
 *       the ME Wireless module wipes the AE link NBT keys, so re-installing
 *       starts from an unlinked state instead of carrying stale link data.</li>
 * </ul>
 *
 * <p>{@code FMLCorePluginContainsFMLMod=true} tells FML the jar also
 * contains a regular {@code @Mod} class (our {@link MpsNaoAddonsMod}),
 * which handles module registration during the Forge lifecycle.
 */
public class MpsNaoAddonsCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getLibraryRequestClass() { return null; }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
            "com.nao.mpsnaoaddons.transform.PlayerTickHandlerTransformer",
            "com.nao.mpsnaoaddons.transform.EntityPlayerTransformer",
            "com.nao.mpsnaoaddons.transform.PowerToolInterfaceTransformer",
            "com.nao.mpsnaoaddons.transform.GuiTinkerTableTransformer",
            "com.nao.mpsnaoaddons.transform.MEWirelessAccessTransformer",
            "com.nao.mpsnaoaddons.transform.MuseRendererTransformer",
            "com.nao.mpsnaoaddons.transform.MuseItemUtilsTransformer"
        };
    }

    @Override
    public String getModContainerClass() { return null; }

    @Override
    public String getSetupClass() { return null; }

    @Override
    public void injectData(Map<String, Object> data) {}
}

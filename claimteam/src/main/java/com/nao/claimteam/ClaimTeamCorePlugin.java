package com.nao.claimteam;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Coremod entry. Loads early enough to install ASM transformers that handle the indirect
 * protection cases (explosions, pistons, fluid flow, PVP) that no Forge event covers in 1.4.7.
 *
 * Each transformer is independently fail-safe: if its target method/class isn't found at
 * runtime (e.g. because the runtime obfuscation differs from what we expect), the transformer
 * logs and returns the original bytes — the mod continues to function with only the direct
 * event-based protection from {@code ProtectionHandler}.
 */
public class ClaimTeamCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getLibraryRequestClass() { return null; }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
            "com.nao.claimteam.asm.ExplosionTransformer",
            "com.nao.claimteam.asm.PistonTransformer",
            "com.nao.claimteam.asm.FluidTransformer",
            "com.nao.claimteam.asm.PvpTransformer"
        };
    }

    @Override
    public String getModContainerClass() { return null; }

    @Override
    public String getSetupClass() { return null; }

    @Override
    public void injectData(Map<String, Object> data) {}
}

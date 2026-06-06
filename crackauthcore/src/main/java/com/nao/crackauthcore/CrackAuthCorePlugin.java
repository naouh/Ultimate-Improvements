package com.nao.crackauthcore;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Coremod entry for CrackAuthCore - the server-side packet gate that backs the CrackAuth plugin.
 *
 * <p>Registers a single transformer that hooks {@code NetServerHandler.handleCustomPayload} so that
 * inbound mod packets from players who haven't logged in yet (per {@link CrackAuthGate}) are dropped.
 * The transformer is fail-safe: if its target isn't found it logs and returns the class untouched.
 */
public class CrackAuthCorePlugin implements IFMLLoadingPlugin {

	@Override
	public String[] getLibraryRequestClass() { return null; }

	@Override
	public String[] getASMTransformerClass() {
		System.out.println("[CrackAuthCore] coreplugin loaded - registering CustomPayloadGateTransformer");
		return new String[] {
			"com.nao.crackauthcore.CustomPayloadGateTransformer"
		};
	}

	@Override
	public String getModContainerClass() { return null; }

	@Override
	public String getSetupClass() { return null; }

	@Override
	public void injectData(Map<String, Object> data) {}
}

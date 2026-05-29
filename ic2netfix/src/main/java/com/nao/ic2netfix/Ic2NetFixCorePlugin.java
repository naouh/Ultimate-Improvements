package com.nao.ic2netfix;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Fixes the {@code ClassCastException: <client player> cannot be cast to EntityPlayerMP}
 * crash thrown from IC2's {@code NetworkManager.sendUpdatePacket} when a server->client
 * tile-entity field sync is triggered on the logical client.
 *
 * IC2's server->client network helpers ({@code NetworkHelper.updateTileEntityField} etc.)
 * funnel into {@code NetworkManager.sendUpdatePacket}, which iterates the player list and
 * casts every entry to {@code EntityPlayerMP}. On a remote client that list holds the local
 * {@code EntityClientPlayerMP}, so the cast explodes. Mods that call these helpers without a
 * side guard (AdvancedMachines' {@code TileEntityBlock.setFacing} on block placement, and
 * GregTech's {@code BaseMetaTileEntity} ticking) drag the crash into otherwise normal play.
 *
 * The fix transforms {@code ic2.core.network.NetworkManager} so its server->client send
 * methods early-return when running on the logical client - exactly where they should be a
 * no-op anyway (a client has no clients to push updates to). See {@link NetworkManagerTransformer}.
 */
public class Ic2NetFixCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getLibraryRequestClass() { return null; }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
            "com.nao.ic2netfix.NetworkManagerTransformer",
            "com.nao.ic2netfix.BlockMultiIDTransformer",
            "com.nao.ic2netfix.GtMetaMachineItemTransformer"
        };
    }

    @Override
    public String getModContainerClass() { return null; }

    @Override
    public String getSetupClass() { return null; }

    @Override
    public void injectData(Map<String, Object> data) {}
}

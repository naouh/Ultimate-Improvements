package com.nao.claimteam.perm;

import com.nao.claimteam.Config;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

public final class PermissionResolver {

    private PermissionResolver() {}

    /**
     * Returns the group name for a player. Resolution order:
     *   1. Essentials GroupManager (if useGroupManager=true and bridge enabled)
     *   2. Config.playerGroups (case-insensitive)
     *   3. "Default"
     */
    public static String getGroup(EntityPlayer player) {
        if (player == null) return "Default";
        String username = player.username;
        if (Config.useGroupManager && GroupManagerBridge.isEnabled()) {
            String worldName = worldNameOf(player.worldObj);
            if (worldName != null) {
                String g = GroupManagerBridge.getGroupName(username, worldName);
                if (g != null && g.length() > 0) return g;
            }
        }
        String g = Config.groupForPlayer(username);
        return g != null ? g : "Default";
    }

    public static Config.GroupLimits limitsFor(EntityPlayer player) {
        return Config.limitsFor(getGroup(player));
    }

    public static boolean isOp(EntityPlayer player) {
        if (player == null) return false;
        MinecraftServer s = MinecraftServer.getServer();
        if (s == null) return false;
        return s.getConfigurationManager().areCommandsAllowed(player.username);
    }

    /** Resolve world name via Minecraft server. Works for overworld (folder name) and nether/end suffixes. */
    private static String worldNameOf(World w) {
        if (w == null) return null;
        MinecraftServer s = MinecraftServer.getServer();
        if (s == null) return null;
        // 1.4.7: WorldServer.provider.getDimensionName() returns "Nether"/"The End" etc.; not the folder name.
        // GroupManager keys by Bukkit world.getName() which for the main world is the save folder name.
        // The most reliable accessor in 1.4.7 is the server's overworld save handler folder name plus a suffix.
        // For dim 0 we use the save folder, for others we fall back to the provider name (best effort).
        try {
            if (w.provider.dimensionId == 0) {
                return s.worldServers[0].getSaveHandler().getSaveDirectoryName();
            }
            return w.provider.getDimensionName();
        } catch (Throwable t) {
            return null;
        }
    }
}

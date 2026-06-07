package com.cagecontrol;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

/**
 * Server-side cage mutations (start / stop / rename / co-owner add+remove), shared by both the
 * {@code /shard} command and the GUI's custom packet path so they behave identically.
 *
 * Permission must be checked by the caller (the command and the packet handler both verify
 * {@code canControl} / {@code isOwner} before calling these). Each method performs the change,
 * marks the registry dirty, pushes a refreshed cage list to the actor on success, and returns a
 * status message (with colour codes, no {@code [CageControl]} prefix) for the caller to display.
 */
final class CageActions {

    private CageActions() {}

    private static final String GREEN = "§a";
    private static final String RED   = "§c";
    private static final String AQUA  = "§b";
    private static final String RESET = "§r";

    static String start(EntityPlayerMP epm, CageRegistry reg, CageData d, boolean isAdmin) {
        WorldServer ws = findWorldByDim(d.dim);
        if (ws == null) return "Dimension " + d.dim + " is not loaded.";
        TileEntity te = ws.getBlockTileEntity(d.x, d.y, d.z);
        if (!ReflectSS.isSoulCage(te)) { reg.remove(d); return "Cage no longer exists at " + d.x + "," + d.y + "," + d.z + "."; }

        String cd = cooldown(d, isAdmin);
        if (cd != null) return cd;

        ReflectSS.setSignal(te, ws.isBlockGettingPowered(d.x, d.y, d.z) || ws.isBlockIndirectlyGettingPowered(d.x, d.y, d.z));
        ReflectSS.setMobType(te, d.mobType, d.special);
        ReflectSS.setTier(te, d.tier);  // restores delay
        ReflectSS.rCount(te);
        ws.markBlockForUpdate(d.x, d.y, d.z);
        d.active = true;
        reg.markDirty();
        PacketHandler.sendCageList(epm);
        return "Cage '" + AQUA + d.name + RESET + "' " + GREEN + "started" + RESET + ".";
    }

    static String stop(EntityPlayerMP epm, CageRegistry reg, CageData d, boolean isAdmin) {
        WorldServer ws = findWorldByDim(d.dim);
        if (ws == null) return "Dimension " + d.dim + " is not loaded.";
        TileEntity te = ws.getBlockTileEntity(d.x, d.y, d.z);
        if (!ReflectSS.isSoulCage(te)) { reg.remove(d); return "Cage no longer exists at " + d.x + "," + d.y + "," + d.z + "."; }

        String cd = cooldown(d, isAdmin);
        if (cd != null) return cd;

        // keep mobType so the spawner visual stays; just block spawning via delay = MAX
        ReflectSS.disableSpawn(te);
        ReflectSS.resetCount(te);
        ws.markBlockForUpdate(d.x, d.y, d.z);
        d.active = false;
        reg.markDirty();
        PacketHandler.sendCageList(epm);
        return "Cage '" + AQUA + d.name + RESET + "' " + RED + "stopped" + RESET + ".";
    }

    static String rename(EntityPlayerMP epm, CageRegistry reg, CageData d, String newName) {
        newName = newName == null ? "" : newName.trim();
        if (!newName.matches("[A-Za-z0-9_\\-]{1,24}")) {
            return "Invalid name (A-Z 0-9 _ -, max 24 chars).";
        }
        String old = d.name;
        if (!reg.rename(d, newName)) {
            return "You already have a cage named '" + newName + "'.";
        }
        PacketHandler.sendCageList(epm);
        return "Cage '" + AQUA + old + RESET + "' renamed to '" + AQUA + newName + RESET + "'.";
    }

    static String addCoOwner(EntityPlayerMP epm, CageRegistry reg, CageData d, String target) {
        target = target == null ? "" : target.trim();
        if (target.length() < 2) return "Enter a player name.";
        if (d.isOwner(target)) return target + " is already the primary owner.";
        if (d.addCoOwner(target)) {
            reg.markDirty();
            PacketHandler.sendCageList(epm);
            return GREEN + "Added " + target + " as co-owner of '" + d.name + "'." + RESET;
        }
        return target + " is already a co-owner.";
    }

    static String removeCoOwner(EntityPlayerMP epm, CageRegistry reg, CageData d, String target) {
        target = target == null ? "" : target.trim();
        if (target.length() == 0) return "Enter a player name.";
        if (d.removeCoOwner(target)) {
            reg.markDirty();
            PacketHandler.sendCageList(epm);
            return RED + "Removed " + target + " from co-owners of '" + d.name + "'." + RESET;
        }
        return target + " is not a co-owner.";
    }

    /**
     * Consume one held shard after it's been bound into a cage. We must null the emptied slot and
     * resync the inventory ourselves: the vanilla item-use cleanup that nulls a 0-size stack only
     * runs inside {@code ItemShard.onItemUse}, not on our command/packet code paths. Just setting
     * {@code stackSize = 0} left a ghost stack server-side and never told the client, so the shard
     * stayed visible in the inventory and looked duplicated. {@code decrStackSize} clears the slot
     * when it empties; {@code detectAndSendChanges} pushes the update to the client.
     */
    static void consumeHeldShard(EntityPlayerMP epm) {
        epm.inventory.decrStackSize(epm.inventory.currentItem, 1);
        epm.inventoryContainer.detectAndSendChanges();
    }

    /** Anti-spam start/stop cooldown (admins exempt). Returns an error message, or null to proceed. */
    private static String cooldown(CageData d, boolean isAdmin) {
        if (isAdmin) return null;
        long now = System.currentTimeMillis();
        long since = now - d.lastToggleMs;
        if (since < CageControl.ACTION_COOLDOWN_MS) {
            long wait = (CageControl.ACTION_COOLDOWN_MS - since + 999L) / 1000L;
            return RED + "Please wait " + wait + "s before toggling '" + d.name + "' again." + RESET;
        }
        d.lastToggleMs = now;
        return null;
    }

    private static WorldServer findWorldByDim(int dim) {
        WorldServer[] worlds = MinecraftServer.getServer().worldServers;
        for (WorldServer w : worlds) if (w.provider.dimensionId == dim) return w;
        return null;
    }
}

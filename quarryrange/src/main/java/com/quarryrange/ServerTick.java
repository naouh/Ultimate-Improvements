package com.quarryrange;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Server heartbeat. Every tick it sweeps loaded quarries:
 *  - a quarry we've never seen that still has a {@code placedBy} is freshly placed → hold it and pop
 *    the editor for its placer (this is reliable, unlike trying to guess the placement position);
 *  - a pending (being-configured) quarry is kept held ({@code isAlive=false}) and its native laser
 *    box cleared, so only the editor's preview shows and it can't start even if powered.
 */
public class ServerTick implements ITickHandler {

    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData) {}

    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData) {
        if (!type.contains(TickType.SERVER)) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers == null) return;
        if (ReflectQuarry.getQuarryBlockId() <= 0) return; // BuildCraft absent

        Set<String> seen = new HashSet<String>();
        for (WorldServer w : server.worldServers) {
            if (w != null) scanWorld(w, seen);
        }
        // Forget quarries whose block is gone, so a replacement on the same spot is fresh again
        // (and a pending one broken mid-edit doesn't get stuck).
        for (String k : QuarryManager.snapshotHandled()) {
            if (!seen.contains(k)) {
                int[] p = QuarryManager.parseKey(k);
                QuarryManager.forget(p[0], p[1], p[2], p[3]);
            }
        }
        for (String k : QuarryManager.snapshotPending()) {
            if (!seen.contains(k)) {
                int[] p = QuarryManager.parseKey(k);
                QuarryManager.clearPending(p[0], p[1], p[2], p[3]);
            }
        }
        // Drop candidates whose block vanished before confirmation (e.g. a cancelled placement),
        // so the same spot is a fresh candidate again next time.
        for (String k : QuarryManager.snapshotCandidates()) {
            if (!seen.contains(k)) {
                int[] p = QuarryManager.parseKey(k);
                QuarryManager.clearCandidate(p[0], p[1], p[2], p[3]);
            }
        }
    }

    private void scanWorld(WorldServer w, Set<String> seen) {
        int dim = w.provider.dimensionId;
        int quarryId = ReflectQuarry.getQuarryBlockId();
        List<?> tiles = w.loadedTileEntityList;
        if (tiles == null || tiles.isEmpty()) return;
        // Snapshot: holding/opening editors can mutate the tile list.
        Object[] arr = tiles.toArray();
        for (Object o : arr) {
            if (!(o instanceof TileEntity)) continue;
            TileEntity te = (TileEntity) o;
            if (!ReflectQuarry.isQuarry(te)) continue;
            int x = te.xCoord, y = te.yCoord, z = te.zCoord;
            // Skip ghost tile entities whose block was already reverted/removed (e.g. a placement
            // another plugin cancelled): only a real quarry block at the spot counts as present.
            if (w.getBlockId(x, y, z) != quarryId) continue;
            seen.add(QuarryManager.key(dim, x, y, z));

            if (QuarryManager.isPending(dim, x, y, z)) {
                ReflectQuarry.setAlive(te, false);
                ReflectQuarry.setInProcess(te, true); // stop the client re-drawing the box
                ReflectQuarry.clearLasers(te);
                continue;
            }
            // A quarry that still has a placedBy is freshly placed (BuildCraft sets it on placement;
            // it's null after a reload). The "handled" guard makes sure we open the editor exactly
            // once, never again after Apply, but again if the block is replaced.
            //
            // confirmCandidate requires the quarry to survive into a SECOND scan first: a placement
            // another plugin cancels (ItemGuard world blacklist, claims, WorldGuard, ...) reverts the
            // block only after our first scan saw it, so it's gone next tick and never opens the GUI.
            EntityPlayer placer = ReflectQuarry.getPlacedBy(te);
            if (placer instanceof EntityPlayerMP
                    && QuarryManager.confirmCandidate(dim, x, y, z)
                    && QuarryManager.markHandledIfNew(dim, x, y, z)) {
                openEditorFor(placer, w, x, y, z, true);
            }
        }
    }

    /**
     * Holds the quarry, drops its native box, and tells the player's client to open the editor.
     *
     * @param fresh true for a just-placed quarry (editor starts at the configured default size);
     *              false when re-opening an existing one, in which case the editor starts from the
     *              quarry's CURRENT area so what the player sees is what is actually set.
     */
    public static void openEditorFor(EntityPlayer player, World world, int x, int y, int z, boolean fresh) {
        if (!(player instanceof EntityPlayerMP)) return;
        int dim = world.provider.dimensionId;
        int meta = world.getBlockMetadata(x, y, z);

        QuarryManager.markPending(dim, x, y, z);

        TileEntity te = world.getBlockTileEntity(x, y, z);

        int size = Config.defaultSize;
        int anchor = QuarryArea.ANCHOR_FRONT;
        if (!fresh) {
            int[] b = ReflectQuarry.getBox(te);
            if (b != null) {
                size = Config.clamp(Math.max(b[3] - b[0], b[5] - b[2]) + 1);
                // The anchor is whichever layout reproduces the box; FRONT if none does (a box set
                // by BC landmarks, say) — the size is still the right one.
                for (int a = QuarryArea.ANCHOR_FRONT; a <= QuarryArea.ANCHOR_CORNER_RIGHT; a++) {
                    int[] c = QuarryArea.compute(x, y, z, meta, size, a);
                    if (c[0] == b[0] && c[2] == b[2] && c[3] == b[3] && c[5] == b[5]) {
                        anchor = a;
                        break;
                    }
                }
            }
        }

        // Hold + hide the native box: isAlive=false stops work and makes the client delete its box;
        // inProcess=true stops the client re-creating it each tick. The editor draws its own preview.
        ReflectQuarry.setAlive(te, false);
        ReflectQuarry.setInProcess(te, true);
        ReflectQuarry.clearLasers(te);
        ReflectQuarry.sync(te);

        PacketHandler.sendOpen((EntityPlayerMP) player, x, y, z, meta,
                size, anchor, Config.minSize, Config.maxSize);
    }

    @Override
    public EnumSet<TickType> ticks() { return EnumSet.of(TickType.SERVER); }

    @Override
    public String getLabel() { return QuarryRange.MODID + ":ServerTick"; }
}

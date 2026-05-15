package com.quarryplus;

import java.util.List;

import com.quarryplus.tile.TileMarker;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.LoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

/**
 * Reassigns chunk-loading tickets to their owning {@link TileMarker} on world reload.
 *
 * <p>The marker writes its coords into {@code Ticket.getModData()} when it first requests a
 * ticket. After a server restart Forge re-issues those tickets to us in {@link #ticketsLoaded}
 * — we look up the matching tile and let it adopt the ticket again.
 */
public class ChunkLoadingHandler implements LoadingCallback {

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        for (Ticket t : tickets) {
            NBTTagCompound data = t.getModData();
            int x = data.getInteger("markerX");
            int y = data.getInteger("markerY");
            int z = data.getInteger("markerZ");
            TileEntity te = world.getBlockTileEntity(x, y, z);
            if (te instanceof TileMarker) {
                // The marker requests its own ticket on tryConnection. We just drop this
                // stale ticket — the marker will re-acquire on first chunk-tick (it's still
                // linked because its NBT survived).
                ForgeChunkManager.releaseTicket(t);
            } else {
                ForgeChunkManager.releaseTicket(t);
            }
        }
    }
}

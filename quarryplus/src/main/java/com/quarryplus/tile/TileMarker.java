package com.quarryplus.tile;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.quarryplus.Config;
import com.quarryplus.PacketHandler;
import com.quarryplus.QuarryPlusI;
import com.quarryplus.block.BlockMarker;

import buildcraft.api.core.IAreaProvider;
import com.quarryplus.QuarryPlus;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

/**
 * Tile entity for {@link BlockMarker}.
 *
 * <p>Three or more markers placed in an axis-aligned L (or full box) form a single bounding
 * region used by a QuarryPlus to set its mining area. The geometry lives entirely on each
 * tile — when one marker computes a new box it pushes the coordinates to every other marker
 * in the box via {@link PacketHandler#StC_UPDATE_MARKER}. We don't share a {@code Link}
 * object across tiles like the 1.7.10 original; for 1.4.7 the duplicated state is cheap and
 * NBT-saved per-tile, which means a single-block reload doesn't have to consult a static
 * registry to rebuild.
 *
 * <p>Scanning extends up to {@link Config#markerMaxRange} blocks along each axis and uses the
 * same cross-axis recursion as the reference: once we find an X-pair we attempt Y/Z scans
 * relative to the pair's row, and so on. The recursion is bounded by the axis check
 * ({@code xMax == xMin} stops further X scans).
 */
public class TileMarker extends APacketTile implements IAreaProvider {

    public boolean linked;
    public int xMin, yMin, zMin;
    public int xMax, yMax, zMax;
    public boolean poweredLaser;
    private Ticket chunkTicket;

    // ----- public accessors (used by RenderMarker + future QuarryPlus area-provider hook) -----
    public int xMin() { return linked ? xMin : xCoord; }
    public int yMin() { return linked ? yMin : yCoord; }
    public int zMin() { return linked ? zMin : zCoord; }
    public int xMax() { return linked ? xMax : xCoord; }
    public int yMax() { return linked ? yMax : yCoord; }
    public int zMax() { return linked ? zMax : zCoord; }

    /**
     * BC IAreaProvider hook — called by the QuarryPlus when it consumes this marker pair to
     * define its work area. We tear down every marker in the box (and ourselves) and let the
     * blocks drop as items.
     */
    @Override
    public void removeFromWorld() {
        if (!linked || worldObj == null || worldObj.isRemote) return;
        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    if (worldObj.getBlockId(x, y, z) == QuarryPlusI.blockMarker.blockID) {
                        int meta = worldObj.getBlockMetadata(x, y, z);
                        QuarryPlusI.blockMarker.dropBlockAsItem(worldObj, x, y, z, meta, 0);
                        worldObj.setBlockWithNotify(x, y, z, 0);
                    }
                }
            }
        }
    }

    // ===== Connection logic =====

    /**
     * Right-click entry point. Builds a fresh box rooted at this marker and pushes it out to
     * every other marker the scan touches.
     */
    public void tryConnection() {
        System.out.println("[QuarryPlus] tryConnection on marker " + xCoord + "," + yCoord + "," + zCoord);
        xMin = xMax = xCoord;
        yMin = yMax = yCoord;
        zMin = zMax = zCoord;
        scan(xCoord, yCoord, zCoord);
        System.out.println("[QuarryPlus]   scan result: (" + xMin + "," + yMin + "," + zMin + ")-(" + xMax + "," + yMax + "," + zMax + ")");

        if (xMin == xMax && yMin == yMax && zMin == zMax) {
            // No partner found.
            System.out.println("[QuarryPlus]   no partner found");
            linked = false;
            broadcastUpdate();
            return;
        }
        System.out.println("[QuarryPlus]   linked OK, propagating");
        linked = true;
        broadcastUpdate();
        propagateBoxToOtherMarkers();
        requestChunkTicket();
    }

    private void requestChunkTicket() {
        if (chunkTicket != null || worldObj == null || worldObj.isRemote) return;
        chunkTicket = ForgeChunkManager.requestTicket(
                QuarryPlus.instance, worldObj, ForgeChunkManager.Type.NORMAL);
        if (chunkTicket == null) return;
        NBTTagCompound data = chunkTicket.getModData();
        data.setInteger("markerX", xCoord);
        data.setInteger("markerY", yCoord);
        data.setInteger("markerZ", zCoord);
        ForgeChunkManager.forceChunk(chunkTicket,
                new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4));
    }

    private void releaseChunkTicket() {
        if (chunkTicket == null) return;
        ForgeChunkManager.releaseTicket(chunkTicket);
        chunkTicket = null;
    }

    /**
     * Recursive axis scan. On each axis we look in both directions; the first marker we hit
     * within {@link Config#markerMaxRange} that isn't already part of another link wins. When
     * an axis pair lands, the perpendicular axes are re-scanned at the pair's coordinate so
     * an L-shape can fold into a full box.
     */
    private void scan(int sx, int sy, int sz) {
        int max = Config.markerMaxRange;
        int extX = 0, extY = 0, extZ = 0;

        if (xMax == xMin) {
            for (int t = 1; t <= max; t++) {
                if (isFreeMarker(sx + t, sy, sz)) { xMax = sx + t; extX = t;  break; }
                if (isFreeMarker(sx - t, sy, sz)) { xMin = sx - t; extX = -t; break; }
            }
        }
        if (yMax == yMin) {
            for (int t = 1; t <= max; t++) {
                if (isFreeMarker(sx, sy + t, sz)) { yMax = sy + t; extY = t;  break; }
                if (isFreeMarker(sx, sy - t, sz)) { yMin = sy - t; extY = -t; break; }
            }
        }
        if (zMax == zMin) {
            for (int t = 1; t <= max; t++) {
                if (isFreeMarker(sx, sy, sz + t)) { zMax = sz + t; extZ = t;  break; }
                if (isFreeMarker(sx, sy, sz - t)) { zMin = sz - t; extZ = -t; break; }
            }
        }

        // Cross-axis fold: if we extended along X, retry Y/Z at the new X tip; same for Y/Z.
        if (xMax != xMin && extY != 0) scan(sx, sy + extY, sz);
        if (xMax != xMin && extZ != 0) scan(sx, sy, sz + extZ);
        if (yMax != yMin && extX != 0) scan(sx + extX, sy, sz);
        if (yMax != yMin && extZ != 0) scan(sx, sy, sz + extZ);
        if (zMax != zMin && extX != 0) scan(sx + extX, sy, sz);
        if (zMax != zMin && extY != 0) scan(sx, sy + extY, sz);
    }

    private boolean isFreeMarker(int x, int y, int z) {
        if (worldObj.getBlockId(x, y, z) != QuarryPlusI.blockMarker.blockID) return false;
        if (worldObj.getBlockTileEntity(x, y, z) instanceof TileMarker) {
            TileMarker other = (TileMarker) worldObj.getBlockTileEntity(x, y, z);
            return !other.linked;
        }
        return false;
    }

    private void propagateBoxToOtherMarkers() {
        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    if (x == xCoord && y == yCoord && z == zCoord) continue;
                    if (worldObj.getBlockId(x, y, z) != QuarryPlusI.blockMarker.blockID) continue;
                    if (!(worldObj.getBlockTileEntity(x, y, z) instanceof TileMarker)) continue;
                    TileMarker other = (TileMarker) worldObj.getBlockTileEntity(x, y, z);
                    other.linked = true;
                    other.xMin = xMin; other.yMin = yMin; other.zMin = zMin;
                    other.xMax = xMax; other.yMax = yMax; other.zMax = zMax;
                    other.broadcastUpdate();
                }
            }
        }
    }

    /** Send the full box state to listening clients. */
    private void broadcastUpdate() {
        if (worldObj == null || worldObj.isRemote) return;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeBoolean(linked);
            out.writeInt(xMin); out.writeInt(yMin); out.writeInt(zMin);
            out.writeInt(xMax); out.writeInt(yMax); out.writeInt(zMax);
            out.writeBoolean(poweredLaser);
            sendToAround(PacketHandler.StC_UPDATE_MARKER, bos.toByteArray());
        } catch (IOException ignored) { /* writer to byte buffer can't fail */ }
        // Also force a vanilla block update — that re-sends getDescriptionPacket (NBT-based)
        // so the client refreshes even if the custom-payload packet is dropped or the
        // receiving client hasn't subscribed yet. This is what makes the box appear in
        // real-time on right-click without needing a relog.
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /** Called from {@link BlockMarker#onNeighborBlockChange}. */
    public void onRedstoneChanged() {
        boolean wasPowered = poweredLaser;
        poweredLaser = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord)
                    || worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord + 1, zCoord);
        if (wasPowered != poweredLaser) broadcastUpdate();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        releaseChunkTicket();
        if (!linked || worldObj == null || worldObj.isRemote) return;
        // Tell the other markers in the box that the link is broken; they'll fall back to
        // unlinked rendering until a player right-clicks one of them again.
        linked = false;
        broadcastUpdate();
    }

    // ===== Packet handling =====

    @Override
    public void S_receivePacket(byte type, DataInputStream in, EntityPlayer sender) throws IOException {
        if (type == PacketHandler.CtS_LINK_REQ) {
            // A fresh client just told us "send me your link state". Reply only to that
            // sender so we don't spam the whole area.
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bos);
                out.writeBoolean(linked);
                out.writeInt(xMin); out.writeInt(yMin); out.writeInt(zMin);
                out.writeInt(xMax); out.writeInt(yMax); out.writeInt(zMax);
                out.writeBoolean(poweredLaser);
                sendToPlayer(PacketHandler.StC_LINK_RES, bos.toByteArray(), sender);
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void C_receivePacket(byte type, DataInputStream in) throws IOException {
        if (type == PacketHandler.StC_UPDATE_MARKER || type == PacketHandler.StC_LINK_RES) {
            linked = in.readBoolean();
            xMin = in.readInt(); yMin = in.readInt(); zMin = in.readInt();
            xMax = in.readInt(); yMax = in.readInt(); zMax = in.readInt();
            poweredLaser = in.readBoolean();
        }
    }

    // ===== NBT =====

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("linked", linked);
        tag.setInteger("xMin", xMin); tag.setInteger("yMin", yMin); tag.setInteger("zMin", zMin);
        tag.setInteger("xMax", xMax); tag.setInteger("yMax", yMax); tag.setInteger("zMax", zMax);
        tag.setBoolean("powered", poweredLaser);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        linked = tag.getBoolean("linked");
        xMin = tag.getInteger("xMin"); yMin = tag.getInteger("yMin"); zMin = tag.getInteger("zMin");
        xMax = tag.getInteger("xMax"); yMax = tag.getInteger("yMax"); zMax = tag.getInteger("zMax");
        poweredLaser = tag.getBoolean("powered");
    }
}

package com.quarryrange;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;

/**
 * Reflective access to BuildCraft's quarry internals. We avoid a compile-time dependency on
 * BuildCraft (which is compiled against obfuscated Minecraft) by going through reflection.
 *
 * <p>Everything is best-effort: if BuildCraft is missing or its internals differ, calls no-op
 * rather than throwing into Minecraft's tick loop.
 */
public final class ReflectQuarry {

    private ReflectQuarry() {}

    private static boolean inited, ok;

    private static Class<?> tileQuarryCls;
    private static Field   fBox;            // buildcraft.core.Box box
    private static Field   fBuilderDone;    // boolean builderDone
    private static Field   fBlueprint;      // BptBuilderBase bluePrintBuilder
    private static Field   fIsAlive;        // boolean isAlive
    private static Field   fInProcess;      // boolean inProcess
    private static Field   fChunkTicket;    // ForgeChunkManager.Ticket chunkTicket
    private static Field   fPlacedBy;       // EntityPlayer placedBy
    private static Method  mDestroy;        // destroy()
    private static Method  mCreateUtils;    // createUtilsIfNeeded()
    private static Method  mForceChunks;    // forceChunkLoading(Ticket)
    private static Method  mSendUpdate;     // sendNetworkUpdate()  (inherited)

    private static Method  mBoxInit;        // Box.initialize(int x6)
    private static Method  mBoxDeleteLasers;// Box.deleteLasers()

    private static Field   fQuarryBlock;    // BuildCraftFactory.quarryBlock

    public static synchronized boolean init() {
        if (inited) return ok;
        inited = true;
        try {
            tileQuarryCls = Class.forName("buildcraft.factory.TileQuarry");
            Class<?> boxCls = Class.forName("buildcraft.core.Box");
            Class<?> bcfCls = Class.forName("buildcraft.BuildCraftFactory");
            Class<?> ticketCls = Class.forName("net.minecraftforge.common.ForgeChunkManager$Ticket");

            fBox         = tileQuarryCls.getDeclaredField("box");
            fBuilderDone = tileQuarryCls.getDeclaredField("builderDone");
            fBlueprint   = tileQuarryCls.getDeclaredField("bluePrintBuilder");
            fIsAlive     = tileQuarryCls.getDeclaredField("isAlive");
            fInProcess   = tileQuarryCls.getDeclaredField("inProcess");
            fChunkTicket = tileQuarryCls.getDeclaredField("chunkTicket");
            fPlacedBy    = tileQuarryCls.getDeclaredField("placedBy");
            for (Field f : new Field[]{ fBox, fBuilderDone, fBlueprint, fIsAlive, fInProcess, fChunkTicket, fPlacedBy }) {
                f.setAccessible(true);
            }

            mDestroy     = tileQuarryCls.getMethod("destroy");
            mCreateUtils = tileQuarryCls.getMethod("createUtilsIfNeeded");
            mForceChunks = tileQuarryCls.getMethod("forceChunkLoading", ticketCls);
            mSendUpdate  = findMethod(tileQuarryCls, "sendNetworkUpdate");

            mBoxInit = boxCls.getMethod("initialize", int.class, int.class, int.class,
                                                      int.class, int.class, int.class);
            mBoxDeleteLasers = boxCls.getMethod("deleteLasers");

            fQuarryBlock = bcfCls.getField("quarryBlock");

            ok = mSendUpdate != null;
        } catch (Throwable t) {
            System.out.println("[QuarryRange] BuildCraft quarry reflection unavailable: " + t);
            ok = false;
        }
        return ok;
    }

    private static Method findMethod(Class<?> c, String name, Class<?>... params) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    public static boolean isQuarry(TileEntity te) {
        if (te == null || !init()) return false;
        return tileQuarryCls.isInstance(te);
    }

    public static int getQuarryBlockId() {
        if (!init()) return -1;
        try {
            Object block = fQuarryBlock.get(null);
            if (block instanceof Block) return ((Block) block).blockID;
        } catch (Throwable ignored) {}
        return -1;
    }

    public static boolean hasPlacedBy(TileEntity te) {
        if (!isQuarry(te)) return false;
        try { return fPlacedBy.get(te) != null; } catch (Throwable t) { return false; }
    }

    /** The player who placed this quarry, or null (also null after a reload — not persisted). */
    public static EntityPlayer getPlacedBy(TileEntity te) {
        if (!isQuarry(te)) return null;
        try {
            Object p = fPlacedBy.get(te);
            if (p instanceof EntityPlayer) return (EntityPlayer) p;
        } catch (Throwable ignored) {}
        return null;
    }

    /** Clears placedBy so a quarry is opened in the editor exactly once per placement. */
    public static void clearPlacedBy(TileEntity te) {
        if (!isQuarry(te)) return;
        try { fPlacedBy.set(te, null); } catch (Throwable ignored) {}
    }

    /**
     * Holds or releases the quarry. While held ({@code alive=false}) BuildCraft's {@code g()} update
     * returns immediately on the server, so the quarry builds nothing and digs nothing even if an
     * engine is feeding it energy ({@code doWork()} is empty — all work lives behind that guard).
     */
    public static void setAlive(TileEntity te, boolean alive) {
        if (!isQuarry(te)) return;
        try { fIsAlive.setBoolean(te, alive); } catch (Throwable ignored) {}
    }

    /**
     * Forcing inProcess=true while held makes the CLIENT's update loop bail at
     * {@code if (inProcess || !isDigging) return;} before it re-creates the box lasers each tick —
     * the only way to keep the (client-only, untracked) quarry box from reappearing during editing.
     */
    public static void setInProcess(TileEntity te, boolean inProcess) {
        if (!isQuarry(te)) return;
        try { fInProcess.setBoolean(te, inProcess); } catch (Throwable ignored) {}
    }

    /** Pushes the tile's current state to watching clients (e.g. so a just-flipped isAlive lands). */
    public static void sync(TileEntity te) {
        if (!isQuarry(te) || mSendUpdate == null) return;
        try { mSendUpdate.invoke(te); } catch (Throwable ignored) {}
    }

    /**
     * Removes the quarry's own (server-spawned, synced) laser box so it doesn't linger behind the
     * editor's preview. While held ({@code isAlive=false}) BuildCraft won't respawn it.
     */
    public static void clearLasers(TileEntity te) {
        if (!isQuarry(te)) return;
        try {
            Object box = fBox.get(te);
            if (box != null) mBoxDeleteLasers.invoke(box);
        } catch (Throwable ignored) {}
    }

    /**
     * Rewrites the quarry's box and rebuilds its builder/lasers so the new area takes effect.
     *
     * @param reloadChunks when true also re-runs BuildCraft's chunk-loading for the new footprint
     *                     (and chats the placer once). Use false for live previews to avoid spam.
     */
    public static boolean setArea(TileEntity te, int[] box, boolean reloadChunks) {
        if (!isQuarry(te) || box == null || box.length < 6) return false;
        try {
            Object boxObj = fBox.get(te);
            if (boxObj == null) return false;
            mBoxInit.invoke(boxObj, box[0], box[1], box[2], box[3], box[4], box[5]);

            fBuilderDone.setBoolean(te, false);
            fBlueprint.set(te, null);

            mDestroy.invoke(te);          // kill old arm + lasers
            mCreateUtils.invoke(te);      // rebuild blueprint + preview lasers from the new box

            Object ticket = fChunkTicket.get(te);
            if (reloadChunks && ticket != null) {
                mForceChunks.invoke(te, ticket);  // also sends a network update
            } else if (mSendUpdate != null) {
                mSendUpdate.invoke(te);
            }
            return true;
        } catch (Throwable t) {
            System.out.println("[QuarryRange] setArea failed: " + t);
            return false;
        }
    }
}

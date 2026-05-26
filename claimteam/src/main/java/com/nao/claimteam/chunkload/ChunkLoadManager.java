package com.nao.claimteam.chunkload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nao.claimteam.ClaimTeamMod;
import com.nao.claimteam.data.Claim;
import com.nao.claimteam.data.ClaimRegistry;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

/**
 * Always-on chunk loading using Forge tickets.
 *
 * Strategy: one or more {@code Type.NORMAL} tickets per dimension. Each ticket can hold up
 * to {@code ForgeChunkManager.getMaxChunkDepthFor(modid)} chunks (default 25). When we run
 * out of slots in a ticket we request another. Tickets are kept for the server's lifetime;
 * chunks are individually attached/released via {@link #force} and {@link #release}.
 *
 * State stored only in memory — re-built on server start by walking the {@link ClaimRegistry}.
 */
public final class ChunkLoadManager {

    private ChunkLoadManager() {}

    /** dim -> list of tickets used for that dim. */
    private static final Map<Integer, List<Ticket>> ticketsByDim = new HashMap<Integer, List<Ticket>>();
    /** packedKey (dim,cx,cz) -> the ticket currently holding it. */
    private static final Map<Long, Ticket> ticketByChunk = new HashMap<Long, Ticket>();

    public static void reset() {
        ticketsByDim.clear();
        ticketByChunk.clear();
    }

    /**
     * Called at server start (after worlds are loaded and {@link ChunkLoaderCallback} has
     * replayed persisted tickets). For each chunkload claim without an active ticket
     * (e.g. world copied to a new server, no forgechunkloading.dat) we re-force it. Idempotent.
     */
    public static void rebuildFromRegistry() {
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv == null) return;
        for (WorldServer w : srv.worldServers) {
            ClaimRegistry reg = ClaimRegistry.get(w);
            for (Claim c : reg.snapshot()) {
                if (!c.chunkload) continue;
                if (c.dim != w.provider.dimensionId) continue;
                force(c.dim, c.chunkX, c.chunkZ);
            }
        }
    }

    /** Force-load a chunk. Idempotent. */
    public static void force(int dim, int cx, int cz) {
        long k = ClaimRegistry.key(dim, cx, cz);
        if (ticketByChunk.containsKey(k)) return;
        WorldServer w = worldOf(dim);
        if (w == null) return;
        Ticket t = pickTicket(dim, w);
        if (t == null) return;
        ForgeChunkManager.forceChunk(t, new ChunkCoordIntPair(cx, cz));
        ticketByChunk.put(k, t);
    }

    /** Release a chunk. Safe to call when not loaded. */
    public static void release(int dim, int cx, int cz) {
        long k = ClaimRegistry.key(dim, cx, cz);
        Ticket t = ticketByChunk.remove(k);
        if (t == null) return;
        try {
            ForgeChunkManager.unforceChunk(t, new ChunkCoordIntPair(cx, cz));
        } catch (Throwable ignored) {}
    }

    private static WorldServer worldOf(int dim) {
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv == null) return null;
        for (WorldServer w : srv.worldServers) if (w.provider.dimensionId == dim) return w;
        return null;
    }

    /** Find a ticket with room, or request a new one. */
    private static Ticket pickTicket(int dim, WorldServer w) {
        List<Ticket> list = ticketsByDim.get(dim);
        if (list == null) {
            list = new ArrayList<Ticket>();
            ticketsByDim.put(dim, list);
        }
        for (Ticket t : list) {
            if (t.getChunkList().size() < t.getMaxChunkListDepth()) return t;
        }
        Ticket t = ForgeChunkManager.requestTicket(ClaimTeamMod.instance, w, Type.NORMAL);
        if (t == null) return null;
        list.add(t);
        return t;
    }

    /** Called by the callback when Forge replays tickets at world load. */
    static void registerLoadedTicket(int dim, Ticket t) {
        List<Ticket> list = ticketsByDim.get(dim);
        if (list == null) {
            list = new ArrayList<Ticket>();
            ticketsByDim.put(dim, list);
        }
        if (!list.contains(t)) list.add(t);
        for (Object cc : t.getChunkList()) {
            ChunkCoordIntPair p = (ChunkCoordIntPair) cc;
            ticketByChunk.put(ClaimRegistry.key(dim, p.chunkXPos, p.chunkZPos), t);
        }
    }
}

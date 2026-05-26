package com.nao.claimteam.chunkload;

import java.util.List;
import java.util.Set;

import com.nao.claimteam.data.Claim;
import com.nao.claimteam.data.ClaimRegistry;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager.LoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

/**
 * Called by Forge when chunkload tickets are restored at world load.
 *
 * For each replayed ticket, walk its chunks and either keep them (still claimed as chunkload)
 * or drop them. Chunks the ticket is keeping that aren't backed by a claim anymore mean the
 * NBT got out of sync — release them.
 */
public class ChunkLoaderCallback implements LoadingCallback {

    @Override
    public void ticketsLoaded(List tickets, World world) {
        int dim = world.provider.dimensionId;
        ClaimRegistry reg = ClaimRegistry.get(world);
        for (Object o : tickets) {
            Ticket t = (Ticket) o;
            ChunkLoadManager.registerLoadedTicket(dim, t);
            // getChunkList returns an ImmutableSet — snapshot first to avoid CME on iteration.
            Set chunks = t.getChunkList();
            Object[] arr = chunks.toArray();
            for (int i = 0; i < arr.length; i++) {
                ChunkCoordIntPair p = (ChunkCoordIntPair) arr[i];
                Claim c = reg.find(dim, p.chunkXPos, p.chunkZPos);
                if (c == null || !c.chunkload) {
                    try { net.minecraftforge.common.ForgeChunkManager.unforceChunk(t, p); }
                    catch (Throwable ignored) {}
                }
            }
        }
    }
}

package com.nao.claimteam.core;

import com.nao.claimteam.data.Claim;
import com.nao.claimteam.data.ClaimRegistry;
import com.nao.claimteam.data.ClaimTeam;
import com.nao.claimteam.data.TeamRegistry;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Server-side implementation of {@link ClaimQuery.Provider}. Resolves chunk -> claim -> team
 * by hitting the persistent registries.
 *
 * Rules:
 *  - Explosion: block destruction in any claimed chunk. Sources are not checked — claims are
 *    treated as opaque blast walls.
 *  - Transition (piston/fluid): allow if both src and dst are in the same team's claim, or if
 *    neither is claimed. Anything else is blocked.
 *  - PVP: block the hit if the victim is in a claimed chunk and the attacker is not a member
 *    or ally of that team.
 */
public final class ClaimQueryProvider implements ClaimQuery.Provider {

    @Override
    public boolean shouldBlockExplosion(int dim, int x, int y, int z) {
        WorldServer w = worldOf(dim);
        if (w == null) return false;
        int cx = x >> 4;
        int cz = z >> 4;
        Claim c = ClaimRegistry.get(w).find(dim, cx, cz);
        return c != null;
    }

    @Override
    public boolean shouldBlockTransition(int dim, int srcCx, int srcCz, int dstCx, int dstCz) {
        WorldServer w = worldOf(dim);
        if (w == null) return false;
        ClaimRegistry reg = ClaimRegistry.get(w);
        Claim src = reg.find(dim, srcCx, srcCz);
        Claim dst = reg.find(dim, dstCx, dstCz);
        if (dst == null) return false;        // entering unclaimed territory — fine
        if (src == null) return true;          // entering claimed from unclaimed — block
        return !sameTeam(src, dst);            // both claimed: must be same team
    }

    @Override
    public boolean shouldBlockPvp(String attacker, int dim, int x, int y, int z) {
        WorldServer w = worldOf(dim);
        if (w == null) return false;
        int cx = x >> 4;
        int cz = z >> 4;
        Claim c = ClaimRegistry.get(w).find(dim, cx, cz);
        if (c == null) return false;
        ClaimTeam victimTeam = TeamRegistry.get(w).getByName(c.teamName);
        if (victimTeam == null) return false;
        if (attacker == null) return true;
        // Members or allies of the chunk's team can hit each other inside the claim
        // (kept simple — server admins can change behavior later).
        if (victimTeam.isMember(attacker)) return false;
        if (victimTeam.isAlly(attacker))   return false;
        return true;
    }

    private static boolean sameTeam(Claim a, Claim b) {
        if (a == null || b == null) return false;
        if (a.teamName == null || b.teamName == null) return false;
        return a.teamName.equalsIgnoreCase(b.teamName);
    }

    private static WorldServer worldOf(int dim) {
        MinecraftServer s = MinecraftServer.getServer();
        if (s == null) return null;
        for (WorldServer w : s.worldServers) if (w.provider.dimensionId == dim) return w;
        return null;
    }

    /**
     * Does {@code player} have build permission in this chunk? Members AND allies are allowed:
     * allies are treated as full team members (break, place, interact). Allies are matched
     * per individual player name only — allying a player does not extend to their teammates.
     */
    public static boolean canBuild(EntityPlayer player, World world, int cx, int cz) {
        return canAccess(player, world, cx, cz);
    }

    /** Same access as {@link #canBuild} — kept as a separate name for the interact call-site. */
    public static boolean canInteract(EntityPlayer player, World world, int cx, int cz) {
        return canAccess(player, world, cx, cz);
    }

    private static boolean canAccess(EntityPlayer player, World world, int cx, int cz) {
        if (player == null || world == null) return false;
        int dim = world.provider.dimensionId;
        Claim c = ClaimRegistry.get(world).find(dim, cx, cz);
        if (c == null) return true;
        ClaimTeam t = TeamRegistry.get(world).getByName(c.teamName);
        if (t == null) return true;
        return t.isMember(player.username) || t.isAlly(player.username);
    }
}

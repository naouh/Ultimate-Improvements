package com.nao.claimteam.action;

import com.nao.claimteam.Config;
import com.nao.claimteam.chunkload.ChunkLoadManager;
import com.nao.claimteam.data.Claim;
import com.nao.claimteam.data.ClaimRegistry;
import com.nao.claimteam.data.ClaimTeam;
import com.nao.claimteam.data.TeamRegistry;
import com.nao.claimteam.perm.PermissionResolver;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

/**
 * Shared claim/unclaim/chunkload-toggle logic used by both /claim commands and
 * the GUI packet handler. Returns a human-readable status message (English) suitable
 * for chat. Returns {@code null} on success with no message.
 */
public final class ClaimActions {

    private ClaimActions() {}

    public static String claim(EntityPlayerMP p, World w, int cx, int cz) {
        int dim = w.provider.dimensionId;
        ClaimTeam team = TeamRegistry.get(w).getByPlayer(p.username);
        if (team == null) return "You are not in a team. Use /team create <name> first.";

        ClaimRegistry reg = ClaimRegistry.get(w);
        Claim existing = reg.find(dim, cx, cz);
        if (existing != null) {
            if (existing.teamName != null && existing.teamName.equalsIgnoreCase(team.name)) {
                return "This chunk is already claimed by your team.";
            }
            return "This chunk is already claimed by " + existing.teamName + ".";
        }

        Config.GroupLimits limits = PermissionResolver.limitsFor(p);
        int used = reg.countByTeam(team.name);
        if (limits.maxClaims >= 0 && used >= limits.maxClaims) {
            return "Claim limit reached (" + used + "/" + limits.maxClaims + ").";
        }

        reg.put(new Claim(cx, cz, dim, team.name, false));
        return "Chunk (" + cx + "," + cz + ") claimed for team " + team.name + ".";
    }

    public static String unclaim(EntityPlayerMP p, World w, int cx, int cz) {
        int dim = w.provider.dimensionId;
        ClaimRegistry reg = ClaimRegistry.get(w);
        Claim c = reg.find(dim, cx, cz);
        if (c == null) return "This chunk is not claimed.";

        ClaimTeam team = TeamRegistry.get(w).getByPlayer(p.username);
        boolean op = PermissionResolver.isOp(p);
        boolean owner = team != null && team.name != null && team.name.equalsIgnoreCase(c.teamName)
                       && team.isOwner(p.username);
        if (!op && !owner) {
            return "Only the team owner can unclaim. (op may override.)";
        }
        if (c.chunkload) ChunkLoadManager.release(dim, cx, cz);
        reg.remove(dim, cx, cz);
        return "Chunk (" + cx + "," + cz + ") unclaimed.";
    }

    public static String toggleChunkload(EntityPlayerMP p, World w, int cx, int cz) {
        int dim = w.provider.dimensionId;
        ClaimRegistry reg = ClaimRegistry.get(w);
        Claim c = reg.find(dim, cx, cz);
        if (c == null) return "This chunk is not claimed.";
        ClaimTeam team = TeamRegistry.get(w).getByPlayer(p.username);
        boolean op = PermissionResolver.isOp(p);
        boolean memberOfClaimTeam = team != null && team.name != null
                                    && team.name.equalsIgnoreCase(c.teamName);
        if (!op && !memberOfClaimTeam) {
            return "Only team members can toggle chunk-load.";
        }
        if (c.chunkload) {
            c.chunkload = false;
            ChunkLoadManager.release(dim, cx, cz);
            reg.markDirty();
            return "Chunk-load disabled on (" + cx + "," + cz + ").";
        } else {
            Config.GroupLimits limits = PermissionResolver.limitsFor(p);
            int used = reg.countChunkloadByTeam(c.teamName);
            if (limits.maxChunkloads >= 0 && used >= limits.maxChunkloads) {
                return "Chunk-load limit reached (" + used + "/" + limits.maxChunkloads + ").";
            }
            c.chunkload = true;
            ChunkLoadManager.force(dim, cx, cz);
            reg.markDirty();
            return "Chunk-load enabled on (" + cx + "," + cz + ").";
        }
    }
}

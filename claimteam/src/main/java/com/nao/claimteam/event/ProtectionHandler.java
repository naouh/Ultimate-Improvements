package com.nao.claimteam.event;

import com.nao.claimteam.core.ClaimQueryProvider;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.event.Event;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;

/**
 * Direct interaction protection. Covers:
 *  - block break (LEFT_CLICK_BLOCK) — cancelled when player is not a build-member of the
 *    chunk's team. Note: 1.4.7 has no BlockEvent.BreakEvent; left-click is the only
 *    pre-break hook.
 *  - block place / container open (RIGHT_CLICK_BLOCK) — cancelled when player is not at
 *    least an ally.
 *
 * Bypass: server ops always pass.
 */
public class ProtectionHandler {

    @ForgeSubscribe
    public void onPlayerInteract(PlayerInteractEvent event) {
        EntityPlayer p = event.entityPlayer;
        if (p == null) return;
        World w = p.worldObj;
        if (w == null || w.isRemote) return;
        if (isOp(p)) return;

        int cx = event.x >> 4;
        int cz = event.z >> 4;

        if (event.action == Action.LEFT_CLICK_BLOCK) {
            if (!ClaimQueryProvider.canBuild(p, w, cx, cz)) {
                deny(event, p, "You cannot break blocks here.");
            }
            return;
        }
        if (event.action == Action.RIGHT_CLICK_BLOCK) {
            // Place uses build permission; pure interact (container/lever) uses interact permission.
            // We can't cheaply tell which without inspecting the held item, so use the looser
            // "interact" check and rely on the explosion/fluid/piston ASM patches plus the build
            // check above for the strict cases. This matches FTB Chunks' behavior where allies
            // can open containers.
            if (!ClaimQueryProvider.canInteract(p, w, cx, cz)) {
                deny(event, p, "You cannot interact here.");
            }
            return;
        }
    }

    private static void deny(PlayerInteractEvent e, EntityPlayer p, String msg) {
        e.useBlock = Event.Result.DENY;
        e.useItem  = Event.Result.DENY;
        e.setCanceled(true);
        p.sendChatToPlayer("§c[ClaimTeam] " + msg);
    }

    private static boolean isOp(EntityPlayer p) {
        return com.nao.claimteam.perm.PermissionResolver.isOp(p);
    }
}

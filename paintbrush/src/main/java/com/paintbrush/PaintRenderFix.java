package com.paintbrush;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import ic2.api.IPaintableBlock;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Fixes the cable-paint render glitch. This is an IC2 bug — the vanilla IC2 Painter triggers
 * it too — so the fix is keyed off the interaction, not off our own item.
 *
 * <p>When a cable's colour changes, {@code TileEntityCable.onNetworkUpdate} re-renders only
 * that one block ({@code World.markBlockForRenderUpdate} on itself). A neighbouring cable
 * whose connection geometry depends on it ({@code canInteractWithCable} — cables of different
 * non-zero colours don't connect) is left with stale geometry, so a segment appears to
 * vanish until the chunk re-renders for some unrelated reason.
 *
 * <p>This client-side handler watches for a right-click on any IC2 paintable block and, for a
 * short window afterwards, re-renders the 3x3x3 region around it — long enough for the colour
 * change to arrive from the server. Triggering on the interaction event means it also covers
 * the vanilla IC2 Painter, not just our brush.
 */
@SideOnly(Side.CLIENT)
public class PaintRenderFix implements ITickHandler {

    /** Client ticks to keep refreshing after a paint interaction (covers the server sync). */
    private static final int REFRESH_TICKS = 10;

    /** Pending refreshes — each entry is {x, y, z, ticksLeft}. */
    private final List<int[]> pending = new ArrayList<int[]>();

    @ForgeSubscribe
    public void onInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (event.entityPlayer == null) return;
        World world = event.entityPlayer.worldObj;
        if (world == null || !world.isRemote) return;
        int id = world.getBlockId(event.x, event.y, event.z);
        if (id <= 0 || id >= Block.blocksList.length) return;
        if (!(Block.blocksList[id] instanceof IPaintableBlock)) return;
        pending.add(new int[] { event.x, event.y, event.z, REFRESH_TICKS });
    }

    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData) {
        if (pending.isEmpty()) return;
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            pending.clear();
            return;
        }
        Iterator<int[]> it = pending.iterator();
        while (it.hasNext()) {
            int[] e = it.next();
            world.markBlockRangeForRenderUpdate(e[0] - 1, e[1] - 1, e[2] - 1,
                                                e[0] + 1, e[1] + 1, e[2] + 1);
            if (--e[3] <= 0) {
                it.remove();
            }
        }
    }

    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData) {
    }

    @Override
    public EnumSet<TickType> ticks() {
        return EnumSet.of(TickType.CLIENT);
    }

    @Override
    public String getLabel() {
        return "PaintBrush.RenderFix";
    }
}

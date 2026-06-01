package com.cagecontrol;

import java.util.EnumSet;
import java.util.List;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.world.World;

/**
 * Belt-and-braces poll for the menu key — see {@link CageKeyHandler#pollFallback()}.
 *
 * Also fixes the soul-cage mini-mob preview: TESoulCage extends the vanilla mob spawner, whose
 * renderer draws the cached {@code getMobEntity()} created from the mob name "Skeleton" alone — so
 * a wither cage shows a plain skeleton. SoulShards has no separate entity name for wither
 * skeletons (vanilla uses Skeleton + skeletonType=1), so we mark the cached render entity as the
 * wither variant ourselves. The entity is cached, so this sticks until the chunk reloads; we
 * re-apply periodically to cover that.
 */
public class CageKeyTickHandler implements ITickHandler {

    private int previewTick = 0;

    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData) {
        CageKeyHandler.pollFallback();
        if (++previewTick >= 20) {
            previewTick = 0;
            fixWitherPreviews();
        }
    }

    /** Sets skeletonType=1 on the render entity of every loaded special (wither) skeleton cage. */
    private void fixWitherPreviews() {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null) return;
        List tiles = world.loadedTileEntityList;
        for (int i = 0; i < tiles.size(); i++) {
            try {
                Object o = tiles.get(i);
                if (!(o instanceof TileEntityMobSpawner)) continue;
                TileEntity te = (TileEntity) o;
                if (!ReflectSS.isSoulCage(te) || !ReflectSS.getCageSpecial(te)) continue;
                Entity e = ((TileEntityMobSpawner) te).getMobEntity();
                if (e instanceof EntitySkeleton) {
                    EntitySkeleton sk = (EntitySkeleton) e;
                    if (sk.getSkeletonType() != 1) sk.setSkeletonType(1);
                }
            } catch (Throwable t) {
                // list may shrink mid-iteration on chunk unload; ignore and move on
            }
        }
    }

    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData) {}

    @Override
    public EnumSet<TickType> ticks() { return EnumSet.of(TickType.CLIENT); }

    @Override
    public String getLabel() { return "CageControlKeyPoll"; }
}

package com.nao.mpsnaoaddons;

import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;

/**
 * Logic for the OmniWrench power module. Mirrors the behavior of
 * codechicken/Eximius OmniTools' {@code ItemWrench}:
 *
 * <ul>
 *   <li>vanilla rotatable blocks → {@code block.rotateBlock(...)},</li>
 *   <li>IC2 {@code IWrenchable} tiles → set facing, or harvest the block (with
 *       its wrench-drop) when shift-clicking its current face,</li>
 *   <li>ThermalExpansion {@code IReconfigurableFacing} tiles → rotateBlock.</li>
 * </ul>
 *
 * <p>All cross-mod types are looked up reflectively so the coremod loads even
 * if BC/IC2/TE aren't present.
 *
 * <p>Energy cost: {@value #ENERGY_ROTATE} J per rotation, {@value #ENERGY_PICKUP}
 * J per IWrenchable pickup (more expensive because it preserves the TE).
 */
public final class OmniWrenchHelper {

    public static final String MODULE_NAME = "OmniWrench";
    public static final double ENERGY_ROTATE = 100.0;
    public static final double ENERGY_PICKUP = 500.0;
    /** Cost charged to BC/Railcraft/TE wrench-callback paths (post-rotation). */
    public static final double ENERGY_INTERFACE_USE = 100.0;

    private static Method mItemHasActiveModule;
    private static Method mItemHasModule;
    private static Method mDischarge;
    private static Method mGetJoules;
    private static Class<?> cIWrenchable;
    private static Class<?> cIReconfigurableFacing;
    private static Method mCanRotate;
    private static Method mRotateVanillaBlock;
    private static Method mRotateVanillaBlockAlt;
    private static boolean inited = false;

    private OmniWrenchHelper() {}

    private static synchronized void init() {
        if (inited) return;
        inited = true;
        try {
            Class<?> util = Class.forName("net.machinemuse.api.MuseItemUtils");
            mItemHasActiveModule = util.getMethod("itemHasActiveModule",
                    ItemStack.class, String.class);
            mItemHasModule = util.getMethod("itemHasModule",
                    ItemStack.class, String.class);
        } catch (Throwable t) {
            System.err.println("[OmniWrench] MPS MuseItemUtils not found");
        }
        try {
            Class<?> elec = Class.forName("net.machinemuse.api.ElectricItemUtils");
            mDischarge = elec.getMethod("discharge", double.class, ItemStack.class);
            mGetJoules = elec.getMethod("getJoules", ItemStack.class);
        } catch (Throwable t) {
            System.err.println("[OmniWrench] MPS ElectricItemUtils not found");
        }
        try { cIWrenchable = Class.forName("ic2.api.IWrenchable"); }
        catch (Throwable t) { /* IC2 not loaded */ }
        try { cIReconfigurableFacing = Class.forName("thermalexpansion.api.core.IReconfigurableFacing"); }
        catch (Throwable t) { /* TE not loaded */ }
        // CoFH BlockUtils provides the vanilla-rotation table OmniWrench uses
        // (logs, dispensers, pistons, hoppers, levers, repeaters, …). 1.4.7
        // Forge's Block has no rotateBlock() of its own.
        try {
            Class<?> blockUtils = Class.forName("cofh.core.BlockUtils");
            mCanRotate = blockUtils.getMethod("canRotate", int.class);
            mRotateVanillaBlock = blockUtils.getMethod("rotateVanillaBlock",
                    World.class, int.class, int.class, int.class, int.class, int.class);
            mRotateVanillaBlockAlt = blockUtils.getMethod("rotateVanillaBlockAlt",
                    World.class, int.class, int.class, int.class, int.class, int.class);
        } catch (Throwable t) {
            System.err.println("[OmniWrench] CoFH BlockUtils not found; vanilla-block rotation disabled");
        }
    }

    // ------------------------------------------------------------------
    // Entry points called from PowerToolInterfaceTransformer-injected
    // bytecode. These are NOT called from regular Java in this mod, but
    // are public because they're addressed by INVOKESTATIC instructions
    // generated at class-load time.
    // ------------------------------------------------------------------

    /** Used by injected canWrench/canWhack — same as {@link #isOmniWrenchModeActive}
     *  but takes coords (which we ignore — the wrench check is per-mode, not
     *  per-block). The coords stay in the signature so BC etc. can pass them
     *  through without us rewriting their calling convention. */
    public static boolean canWrenchAt(EntityPlayer player, int x, int y, int z) {
        return isOmniWrenchModeActive(player);
    }

    /** Called from injected wrenchUsed/onWhack/onLink/onBoost after the host
     *  mod has performed its rotation/whack. Drain energy + play swing. */
    public static void onWrenchUsed(EntityPlayer player) {
        if (player == null) return;
        ItemStack held = player.getHeldItem();
        if (held == null) return;
        drain(held, ENERGY_INTERFACE_USE);
        player.swingItem();
    }

    /** Public so the injected canLink/canBoost methods can call it directly. */
    public static boolean isOmniWrenchModeActive(EntityPlayer player) {
        return player != null && isActiveOnPowerTool(player.getHeldItem());
    }

    /**
     * @return true iff {@code stack} is an MPS power tool with the OmniWrench
     *         module installed and selected as the active mode.
     */
    public static boolean isActiveOnPowerTool(ItemStack stack) {
        return hasActiveModule(stack, MODULE_NAME);
    }

    /**
     * Generic active-module check, shared by all sibling modules (EU Reader,
     * TE Multimeter). Delegates to {@code MuseItemUtils.itemHasActiveModule}
     * via reflection — for an IRightClickModule that returns true only when
     * {@code moduleName} is the currently-selected mode.
     */
    public static boolean hasActiveModule(ItemStack stack, String moduleName) {
        if (stack == null) return false;
        init();
        if (mItemHasActiveModule == null) return false;
        try {
            Object result = mItemHasActiveModule.invoke(null, stack, moduleName);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Same as {@link #hasActiveModule} but checks whether the module is
     *  INSTALLED (regardless of whether it's the currently-selected mode).
     *  Used for things like ME Wireless linking, where the player shouldn't
     *  have to switch the power tool's active mode just to bind it. */
    public static boolean hasModule(ItemStack stack, String moduleName) {
        if (stack == null) return false;
        init();
        if (mItemHasModule == null) return false;
        try {
            Object result = mItemHasModule.invoke(null, stack, moduleName);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Drain {@code joules} from the power tool stack. Returns true if there
     * was enough energy for the operation to succeed.
     */
    public static boolean drain(ItemStack stack, double joules) {
        init();
        if (mGetJoules == null || mDischarge == null) return false;
        try {
            double available = (Double) mGetJoules.invoke(null, stack);
            if (available < joules) return false;
            mDischarge.invoke(null, joules, stack);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Attempt the wrench action at (x,y,z) on {@code side}. Caller is the
     * PlayerInteractEvent listener; on success this consumes energy from
     * {@code stack} and returns true (caller should cancel the event so
     * vanilla doesn't also try to interact).
     */
    public static boolean tryWrench(EntityPlayer player, World world, int x, int y, int z,
                                    int side, ItemStack stack) {
        init();
        int blockId = world.getBlockId(x, y, z);
        Block block = Block.blocksList[blockId];
        if (block == null) return false;
        boolean sneak = player.isSneaking();

        // 1) CoFH BlockUtils' vanilla-rotation table — same path OmniWrench
        //    uses. 1.4.7 Block has no rotateBlock(), so we go through CoFH.
        int meta = world.getBlockMetadata(x, y, z);
        if (canRotateVanilla(blockId)) {
            int newMeta = rotateVanillaMeta(world, blockId, meta, x, y, z, sneak);
            if (newMeta != meta) {
                if (!drain(stack, ENERGY_ROTATE)) return false;
                world.setBlockMetadataWithNotify(x, y, z, newMeta);
                return true;
            }
        }

        TileEntity tile = world.getBlockTileEntity(x, y, z);
        if (tile == null) return false;

        // 2) IC2 IWrenchable — set facing, or harvest if clicking its facing.
        if (cIWrenchable != null && cIWrenchable.isInstance(tile)) {
            return tryIWrenchable(tile, player, world, x, y, z, side, sneak, stack);
        }

        // 3) ThermalExpansion IReconfigurableFacing — has its own rotateBlock().
        if (cIReconfigurableFacing != null && cIReconfigurableFacing.isInstance(tile)) {
            try {
                Method rotate = cIReconfigurableFacing.getMethod("rotateBlock");
                Object res = rotate.invoke(tile);
                if (Boolean.TRUE.equals(res)) {
                    if (!drain(stack, ENERGY_ROTATE)) return false;
                    world.markBlockForUpdate(x, y, z);
                    return true;
                }
            } catch (Throwable t) {
                // Fall through.
            }
        }
        return false;
    }

    private static boolean canRotateVanilla(int blockId) {
        if (mCanRotate == null) return false;
        try { return Boolean.TRUE.equals(mCanRotate.invoke(null, blockId)); }
        catch (Throwable t) { return false; }
    }

    private static int rotateVanillaMeta(World world, int block, int meta, int x, int y, int z, boolean sneak) {
        Method m = sneak ? mRotateVanillaBlockAlt : mRotateVanillaBlock;
        if (m == null) return meta;
        try { return (Integer) m.invoke(null, world, block, meta, x, y, z); }
        catch (Throwable t) { return meta; }
    }

    private static int oppositeSide(int side) {
        // ForgeDirection ordinal pairs: 0/1, 2/3, 4/5 — flip the low bit.
        return side ^ 1;
    }

    private static boolean tryIWrenchable(TileEntity tile, EntityPlayer player, World world,
                                          int x, int y, int z, int side, boolean sneak,
                                          ItemStack stack) {
        try {
            Method mGetFacing = cIWrenchable.getMethod("getFacing");
            Method mSetFacing = cIWrenchable.getMethod("setFacing", short.class);
            Method mWrenchCanRemove = cIWrenchable.getMethod("wrenchCanRemove", EntityPlayer.class);
            Method mGetWrenchDrop = cIWrenchable.getMethod("getWrenchDrop", EntityPlayer.class);

            int effectiveSide = sneak ? oppositeSide(side) : side;
            short currentFacing = (Short) mGetFacing.invoke(tile);

            // Clicking the side that matches the current facing = pickup intent.
            if (effectiveSide == currentFacing
                    && Boolean.TRUE.equals(mWrenchCanRemove.invoke(tile, player))) {
                ItemStack drop = (ItemStack) mGetWrenchDrop.invoke(tile, player);
                if (drop == null) return false;
                if (!drain(stack, ENERGY_PICKUP)) return false;
                // 1.4.7 has no setBlockToAir — use setBlockWithNotify(.., 0).
                world.setBlockWithNotify(x, y, z, 0);
                if (!world.isRemote) {
                    double dx = x + 0.5 + world.rand.nextGaussian() * 0.2;
                    double dy = y + 0.5 + world.rand.nextGaussian() * 0.2;
                    double dz = z + 0.5 + world.rand.nextGaussian() * 0.2;
                    EntityItem ent = new EntityItem(world, dx, dy, dz, drop);
                    ent.delayBeforeCanPickup = 10;
                    world.spawnEntityInWorld(ent);
                }
                return true;
            }

            // Otherwise rotate to face the clicked side.
            if (!drain(stack, ENERGY_ROTATE)) return false;
            mSetFacing.invoke(tile, (short) effectiveSide);
            world.markBlockForUpdate(x, y, z);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}

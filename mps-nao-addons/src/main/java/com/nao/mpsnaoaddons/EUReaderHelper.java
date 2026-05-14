package com.nao.mpsnaoaddons;

import java.lang.reflect.Method;
import java.text.DecimalFormat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * EU Reader power module. Mirrors IC2's EC Meter behavior: on first click,
 * starts a measurement at the targeted IEnergyTile; on subsequent clicks at
 * the SAME tile, reports the average EU/t emitted and sunk over the elapsed
 * tick window.
 *
 * <p>All IC2 types are resolved reflectively so the mod loads even when IC2
 * isn't present (in which case the module simply won't do anything).
 */
public final class EUReaderHelper {

    public static final String MODULE_NAME = "EU Reader";
    public static final double ENERGY_PER_READING = 50.0;

    private static Class<?> cIEnergySource;
    private static Class<?> cIEnergyConductor;
    private static Class<?> cIEnergySink;
    private static Class<?> cEnergyNet;
    private static Method   mGetForWorld;
    private static Method   mGetTotalEmitted;
    private static Method   mGetTotalSunken;
    private static boolean  inited = false;

    private EUReaderHelper() {}

    private static synchronized void init() {
        if (inited) return;
        inited = true;
        try {
            cIEnergySource    = Class.forName("ic2.api.energy.tile.IEnergySource");
            cIEnergyConductor = Class.forName("ic2.api.energy.tile.IEnergyConductor");
            cIEnergySink      = Class.forName("ic2.api.energy.tile.IEnergySink");
            cEnergyNet        = Class.forName("ic2.api.energy.EnergyNet");
            mGetForWorld      = cEnergyNet.getMethod("getForWorld", World.class);
            mGetTotalEmitted  = cEnergyNet.getMethod("getTotalEnergyEmitted", TileEntity.class);
            mGetTotalSunken   = cEnergyNet.getMethod("getTotalEnergySunken",  TileEntity.class);
        } catch (Throwable t) {
            System.err.println("[EUReader] IC2 API not found; module will be inert");
        }
    }

    public static boolean isActiveOnPowerTool(ItemStack stack) {
        return OmniWrenchHelper.hasActiveModule(stack, MODULE_NAME);
    }

    /**
     * Take a reading at (x,y,z). Returns true if the tile was an IC2 energy
     * tile (so the caller cancels the event). False otherwise so the click
     * falls through to vanilla / other mods.
     */
    public static boolean handleClick(EntityPlayer player, World world, int x, int y, int z,
                                      ItemStack stack) {
        init();
        if (cEnergyNet == null) return false;
        TileEntity tile = world.getBlockTileEntity(x, y, z);
        if (tile == null) return false;
        if (!(cIEnergySource.isInstance(tile)
                || cIEnergyConductor.isInstance(tile)
                || cIEnergySink.isInstance(tile))) {
            return false;
        }
        if (!OmniWrenchHelper.drain(stack, ENERGY_PER_READING)) return false;

        try {
            Object net = mGetForWorld.invoke(null, world);
            long emitted = (Long) mGetTotalEmitted.invoke(net, tile);
            long sunken  = (Long) mGetTotalSunken.invoke(net, tile);
            long now     = world.getWorldTime();
            NBTTagCompound nbt = getOrCreateNbt(stack);

            if (nbt.getInteger("euLastX") != x
                    || nbt.getInteger("euLastY") != y
                    || nbt.getInteger("euLastZ") != z) {
                nbt.setInteger("euLastX", x);
                nbt.setInteger("euLastY", y);
                nbt.setInteger("euLastZ", z);
                player.sendChatToPlayer("[EU Reader] Starting new measurement at "
                        + x + "," + y + "," + z);
            } else {
                long period = now - nbt.getLong("euLastTime");
                if (period < 1L) period = 1L;
                double dEmitted = (emitted - nbt.getLong("euLastEmitted")) / (double) period;
                double dSunken  = (sunken  - nbt.getLong("euLastSunken"))  / (double) period;
                DecimalFormat fmt = new DecimalFormat("0.##");
                player.sendChatToPlayer("[EU Reader] in=" + fmt.format(dSunken)
                        + " EU/t  out=" + fmt.format(dEmitted)
                        + " EU/t  net=" + fmt.format(dSunken - dEmitted)
                        + " EU/t  (avg over " + period + " ticks)");
            }
            nbt.setLong("euLastEmitted", emitted);
            nbt.setLong("euLastSunken",  sunken);
            nbt.setLong("euLastTime",    now);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    /** Get-or-create the stack's NBT tag without clobbering existing data. */
    private static NBTTagCompound getOrCreateNbt(ItemStack stack) {
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }
        return nbt;
    }
}

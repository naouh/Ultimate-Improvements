package com.quarryplus.tile;

import buildcraft.api.power.IPowerProvider;
import buildcraft.api.power.IPowerReceptor;
import buildcraft.api.power.PowerFramework;
import buildcraft.api.power.PowerProvider;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Abstract parent for power-consuming tiles. Plugs into BuildCraft 3.x via
 * {@link IPowerReceptor} and stores energy through {@link PowerProvider}.
 *
 * <p>The original 1.7.10 APowerTile is multi-API (RF via CoFH, EU via IC2 — both gated by
 * {@code @Optional.Method}). 1.4.7 only ships BuildCraft MJ, so we drop the other two
 * paths and inherit straight from {@link APacketTile} (for the packet plumbing) plus the
 * IPowerReceptor contract.
 *
 * <p>Subclasses configure their per-tile budget by calling {@link #configure(int, int)}
 * during {@link #updateEntity}/{@code @PreInit}.
 */
public abstract class APowerTile extends APacketTile implements IPowerReceptor {

    protected IPowerProvider powerProvider;

    public APowerTile() {
        powerProvider = PowerFramework.currentFramework.createPowerProvider();
        powerProvider.configure(0, 0, 100, 0, 1000);
    }

    /**
     * Set the per-tick max-receive cap and the total energy buffer. Called by PowerManager
     * after every enchant-driven recalculation.
     */
    public final void configure(int maxEnergyReceived, int maxStoredEnergy) {
        powerProvider.configure(0, 0, maxEnergyReceived, 0, maxStoredEnergy);
    }

    /**
     * Spend up to {@code max} MJ if at least {@code min} are available. Returns the amount
     * actually charged. When {@code real == false} this is a dry-run (cost check before a
     * mining attempt).
     */
    public final float useEnergy(double min, double max, boolean real) {
        float stored = powerProvider.getEnergyStored();
        if (stored < min) return 0f;
        float charge = (float) Math.min(stored, max);
        if (real) powerProvider.useEnergy((float) min, charge, true);
        return charge;
    }

    public final float getStoredEnergy() {
        return powerProvider.getEnergyStored();
    }

    public final float getMaxStoredEnergy() {
        return powerProvider.getMaxEnergyStored();
    }

    // ----- IPowerReceptor -----
    @Override public void setPowerProvider(IPowerProvider provider) { this.powerProvider = provider; }
    @Override public IPowerProvider getPowerProvider()              { return powerProvider; }
    @Override public int powerRequest()                              {
        return (int) Math.min(powerProvider.getMaxEnergyReceived(),
                              powerProvider.getMaxEnergyStored() - powerProvider.getEnergyStored());
    }

    /** BC ticks this each pulse; subclasses override to consume their work budget. */
    @Override
    public void doWork() {
        // Default: nothing. Subclasses (TileQuarry) override to drive their mining loop.
    }

    // ----- NBT -----
    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (powerProvider != null) PowerFramework.currentFramework.savePowerProvider(this, tag);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        PowerFramework.currentFramework.loadPowerProvider(this, tag);
        if (powerProvider == null) {
            powerProvider = PowerFramework.currentFramework.createPowerProvider();
            powerProvider.configure(0, 0, 100, 0, 1000);
        }
    }
}

package codechicken.translocator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import codechicken.core.alg.MathHelper;
import codechicken.core.liquid.LiquidUtils;
import codechicken.core.liquid.TankAccess;
import codechicken.core.packet.PacketCustom;
import codechicken.core.vec.BlockCoord;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.liquids.ILiquidTank;
import net.minecraftforge.liquids.ITankContainer;
import net.minecraftforge.liquids.LiquidStack;
import net.minecraftforge.liquids.LiquidTank;

/**
 * Liquid translocator. Uses the 1.4.7 Forge liquids API
 * ({@link ITankContainer}/{@link LiquidStack}) which is what shipped at the
 * same era — the new {@code Fluid}/{@code IFluidHandler} world didn't exist
 * yet, so we stay on the version Forge 1.4.7-6.6.2.534 provides.
 *
 * <p>We implement {@link ITankContainer} so the block looks like a tank to
 * adjacent pipes, but every accessor returns zero — we don't actually accept
 * fluid externally, we only move it between *real* tanks attached to our
 * sides. {@link #getTanks(ForgeDirection)} therefore returns an empty
 * {@code LiquidTank} per attached side just so external callers see "we
 * exist on this side" without giving them anything useful to fill.
 */
public class TileLiquidTranslocator extends TileTranslocator implements ITankContainer {

    public LinkedList<MovingLiquid> movingLiquids = new LinkedList<MovingLiquid>();
    public LinkedList<MovingLiquid> exitingLiquids = new LinkedList<MovingLiquid>();

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (this.worldObj.isRemote) {
            Iterator<MovingLiquid> iterator = this.movingLiquids.iterator();
            while (iterator.hasNext()) {
                MovingLiquid m = iterator.next();
                if (m.update()) {
                    iterator.remove();
                    this.exitingLiquids.add(m);
                }
            }
            iterator = this.exitingLiquids.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().update()) {
                    iterator.remove();
                }
            }
            return;
        }

        BlockCoord pos = new BlockCoord(this);
        TankAccess[] attached = new TankAccess[6];
        int[] outputs = null;
        int[] r_outputs = null;

        for (int i = 0; i < 6; i++) {
            Attachment a = this.attachments[i];
            if (a != null) {
                BlockCoord invpos = pos.copy().offset(i);
                TileEntity tile = this.worldObj.getBlockTileEntity(invpos.x, invpos.y, invpos.z);
                if (!(tile instanceof ITankContainer)) {
                    this.harvestPart(i, true);
                } else {
                    attached[i] = new TankAccess((ITankContainer) tile, i ^ 1);
                }
            }
        }

        ArrayList<LiquidTransfer> transfers = new ArrayList<LiquidTransfer>();
        for (int i = 0; i < 6; i++) {
            Attachment a = this.attachments[i];
            if (a != null && a.a_eject) {
                TankAccess t = attached[i];
                LiquidStack drain = t.drain(a.fast ? 1000 : 100, false);
                if (drain != null && drain.amount != 0) {
                    if (outputs == null) {
                        outputs = sortOutputs(false);
                        r_outputs = sortOutputs(true);
                    }
                    LiquidStack move = drain.copy();
                    spreadOutput(move, i, outputs, attached, transfers);
                    spreadOutput(move, i, r_outputs, attached, transfers);
                    t.drain(drain.amount - move.amount, true);
                }
            }
        }
        if (!transfers.isEmpty()) {
            sendTransferPacket(transfers);
        }
    }

    private void spreadOutput(LiquidStack move, int src, int[] outputs, TankAccess[] attached, ArrayList<LiquidTransfer> transfers) {
        for (int k = 0; k < outputs.length && move.amount > 0; k++) {
            int dst = outputs[k];
            TankAccess outaccess = attached[dst];
            int fit = outaccess.fill(move, false);
            int spread = outputs.length - k;
            fit = Math.min(fit, move.amount / spread + this.worldObj.rand.nextInt(move.amount % spread + 1));
            if (fit != 0) {
                LiquidStack add = LiquidUtils.copy(move, fit);
                outaccess.fill(add, true);
                move.amount -= fit;
                transfers.add(new LiquidTransfer(src, dst, add));
            }
        }
    }

    public int[] sortOutputs(boolean b) {
        int[] str = new int[6];
        int k = 0;
        for (int i = 0; i < 6; i++) {
            Attachment a = this.attachments[i];
            if (a != null && !a.a_eject && a.redstone == b) {
                str[k++] = i;
            }
        }
        int[] ret = new int[k];
        System.arraycopy(str, 0, ret, 0, k);
        return ret;
    }

    private void sendTransferPacket(ArrayList<LiquidTransfer> transfers) {
        PacketCustom packet = new PacketCustom(TranslocatorSPH.channel, 2);
        packet.writeCoord(this.xCoord, this.yCoord, this.zCoord);
        packet.writeByte(transfers.size());
        for (LiquidTransfer t : transfers) {
            packet.writeByte(t.key);
            packet.writeLiquidStack(t.liquid);
        }
        packet.sendToChunk(this.worldObj, this.xCoord >> 4, this.zCoord >> 4);
    }

    @Override
    public void handleDescriptionPacket(PacketCustom packet) {
        if (packet.getType() == 2) {
            ArrayList<LiquidTransfer> transfers = new ArrayList<LiquidTransfer>();
            HashSet<Integer> maintainingKeys = new HashSet<Integer>();
            int k = packet.readUnsignedByte();
            for (int i = 0; i < k; i++) {
                LiquidTransfer t = new LiquidTransfer(packet.readUnsignedByte(), packet.readLiquidStack());
                transfers.add(t);
                maintainingKeys.add(t.key);
            }
            for (LiquidTransfer t : transfers) {
                int src = t.key >> 4;
                int dst = t.key & 0xF;
                boolean found = false;
                Iterator<MovingLiquid> iterator = this.movingLiquids.iterator();
                while (iterator.hasNext()) {
                    MovingLiquid m = iterator.next();
                    if (m.liquid.isLiquidEqual(t.liquid) && m.src == src && m.dst == dst) {
                        m.addLiquid(t.liquid.amount);
                        found = true;
                        continue;
                    }
                    if (m.dst != dst && m.src != src || maintainingKeys.contains(m.src << 4 | m.dst)) {
                        continue;
                    }
                    iterator.remove();
                    m.finish();
                    this.exitingLiquids.add(m);
                }
                if (!found) {
                    this.movingLiquids.add(new MovingLiquid(src, dst, t.liquid));
                }
            }
        } else {
            super.handleDescriptionPacket(packet);
        }
    }

    public Iterable<MovingLiquid> movingLiquids() {
        ArrayList<MovingLiquid> comp = new ArrayList<MovingLiquid>();
        comp.addAll(this.movingLiquids);
        comp.addAll(this.exitingLiquids);
        return comp;
    }

    @Override
    public int fill(ForgeDirection from, LiquidStack resource, boolean doFill) {
        return 0;
    }

    @Override
    public int fill(int tankIndex, LiquidStack resource, boolean doFill) {
        return 0;
    }

    @Override
    public LiquidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public LiquidStack drain(int tankIndex, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public ILiquidTank[] getTanks(ForgeDirection direction) {
        if (this.attachments[direction.ordinal()] != null) {
            return new ILiquidTank[] { new LiquidTank(0) };
        }
        return new ILiquidTank[0];
    }

    @Override
    public ILiquidTank getTank(ForgeDirection direction, LiquidStack type) {
        return this.attachments[direction.ordinal()] != null ? new LiquidTank(0) : null;
    }

    private static class LiquidTransfer {
        int key;
        LiquidStack liquid;

        public LiquidTransfer(int src, int dst, LiquidStack liquid) {
            this.key = (src << 4) | dst;
            this.liquid = liquid;
        }

        public LiquidTransfer(int key, LiquidStack liquid) {
            this.key = key;
            this.liquid = liquid;
        }
    }

    public class MovingLiquid {
        public int src;
        public int dst;
        public LiquidStack liquid;
        public double a_start;
        public double b_start;
        public double a_end;
        public double b_end;
        public boolean fast;

        public MovingLiquid(int src, int dst, LiquidStack add) {
            this.src = src;
            this.dst = dst;
            this.liquid = add;
            this.fast = TileLiquidTranslocator.this.attachments[src].fast;
            this.capLiquid();
        }

        private void capLiquid() {
            this.liquid.amount = Math.min(this.liquid.amount, this.fast ? 1000 : 100);
        }

        public boolean update() {
            if (this.a_end == 1.0) {
                return true;
            }
            this.b_start = this.a_start;
            this.a_start = MathHelper.approachLinear(this.a_start, 1.0, 0.2);
            this.b_end = this.a_end;
            if (this.liquid.amount > 0) {
                this.liquid.amount = Math.max(this.liquid.amount - (this.fast ? 200 : 20), 0);
                return this.liquid.amount == 0;
            }
            this.a_end = MathHelper.approachLinear(this.a_end, 1.0, 0.2);
            return false;
        }

        public void addLiquid(int moving) {
            if (this.liquid.amount == 0) {
                throw new IllegalArgumentException("Something went wrong!");
            }
            this.liquid.amount += moving;
            if (TileLiquidTranslocator.this.attachments[this.src] != null) {
                this.fast = TileLiquidTranslocator.this.attachments[this.src].fast;
            }
            this.capLiquid();
        }

        public void finish() {
            this.liquid.amount = 0;
        }
    }
}

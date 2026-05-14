package codechicken.translocator;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import codechicken.core.alg.MathHelper;
import codechicken.core.inventory.InventoryUtils;
import codechicken.core.packet.ICustomPacketTile;
import codechicken.core.packet.PacketCustom;
import codechicken.core.raytracer.RayTracer.IndexedCuboid6;
import codechicken.core.raytracer.SelectionBox;
import codechicken.core.vec.Cuboid6;
import codechicken.core.vec.Rotation;
import codechicken.core.vec.Vector3;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

/**
 * Base class for the Item and Liquid translocators. Holds up to six
 * {@link Attachment} mouths (one per cube face). Subclasses implement
 * {@link #updateEntity()} to actually move things, and override
 * {@link #createAttachment(int)} so they instantiate their own attachment
 * subclass with item/liquid-specific extras.
 *
 * <p>Each attachment has three configurable bits — redstone (responds to
 * redstone signal), invert_redstone (toggles whether high or low means
 * "eject"), and fast (4x speed, glowstone-dust upgrade). Plus an
 * {@code a_insertpos} animation field that drives the visible bobbing of the
 * insert peg, interpolated client-side.
 */
public abstract class TileTranslocator extends TileEntity implements ICustomPacketTile {

    public Attachment[] attachments = new Attachment[6];

    @Override
    public void updateEntity() {
        for (Attachment a : attachments) {
            if (a != null) {
                a.update(this.worldObj.isRemote);
            }
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        PacketCustom packet = new PacketCustom(TranslocatorSPH.channel, 1);
        packet.setChunkDataPacket();
        packet.writeCoord(this.xCoord, this.yCoord, this.zCoord);
        int attachmask = 0;
        for (int i = 0; i < 6; i++) {
            if (this.attachments[i] != null) {
                attachmask |= 1 << i;
            }
        }
        packet.writeByte(attachmask);
        for (Attachment a : attachments) {
            if (a != null) {
                a.write(packet);
            }
        }
        return packet.toPacket();
    }

    @Override
    public void handleDescriptionPacket(PacketCustom packet) {
        if (packet.getType() == 1) {
            int attachmask = packet.readUnsignedByte();
            for (int i = 0; i < 6; i++) {
                if ((attachmask & (1 << i)) != 0) {
                    boolean described = this.attachments[i] != null;
                    if (!described) {
                        this.createAttachment(i);
                    }
                    this.attachments[i].read(packet, described);
                } else {
                    this.attachments[i] = null;
                }
            }
            // markBlockForRenderUpdate equivalent
            this.worldObj.markBlockForRenderUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    public void createAttachment(int side) {
        this.attachments[side] = new Attachment(side);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        for (int i = 0; i < 6; i++) {
            if (this.attachments[i] != null) {
                tag.setCompoundTag("atmt" + i, this.attachments[i].write(new NBTTagCompound()));
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        for (int i = 0; i < 6; i++) {
            if (tag.hasKey("atmt" + i)) {
                this.createAttachment(i);
                this.attachments[i].read(tag.getCompoundTag("atmt" + i));
            }
        }
    }

    public void addTraceableCuboids(List<IndexedCuboid6> cuboids) {
        Vector3 pos = Vector3.fromTileEntityCenter(this);
        SelectionBox base = new SelectionBox(
                new Cuboid6(0.1875, 0.0, 0.1875, 0.8125, 0.125, 0.8125)
                    .add(new Vector3(-0.5, -0.5, -0.5)));
        for (int i = 0; i < 6; i++) {
            Attachment a = this.attachments[i];
            if (a != null) {
                cuboids.add(new IndexedCuboid6(i, transformPart(base, pos, i)));
                cuboids.add(new IndexedCuboid6(i + 6,
                        transformPart(
                            new SelectionBox(new Cuboid6(0.375, 0.0, 0.375,
                                    0.625, a.a_insertpos * 2.0 / 16.0 + 0.0625, 0.625)
                                .add(new Vector3(-0.5, -0.5, -0.5))),
                            pos, i)));
            }
        }
    }

    private Cuboid6 transformPart(SelectionBox box, Vector3 pos, int i) {
        // CCC 0.8.1.6's SelectionBox only exposes rotate(Quat); the
        // ITransformation-aware transform(...) signature didn't land until
        // 0.8.7. sideRotations[i].toQuat() and sideQuats[i] are equivalent
        // here (sideQuats is just the cached toQuat() of sideRotations).
        return box.copy().rotate(Rotation.sideQuats[i]).bound().add(pos);
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        // 1.4.7 uses AxisAlignedBB.getBoundingBox(...) as the static factory;
        // the pool path also exists as AABBPool.addOrModifyAABBInPool, but
        // the static factory is simpler for a once-per-frame bbox.
        return AxisAlignedBB.getBoundingBox(
                (double) this.xCoord, (double) this.yCoord, (double) this.zCoord,
                (double) (this.xCoord + 1), (double) (this.yCoord + 1), (double) (this.zCoord + 1));
    }

    /**
     * Pulls off attachment {@code i}, optionally dropping its components, and
     * if it was the last attachment also removes the block itself.
     *
     * @return true iff the host block was removed.
     */
    public boolean harvestPart(int i, boolean drop) {
        Attachment a = this.attachments[i];
        if (!this.worldObj.isRemote && drop) {
            for (Object stack : a.getDrops()) {
                this.dropItem((ItemStack) stack);
            }
        }
        this.attachments[i] = null;
        this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        for (Attachment a1 : attachments) {
            if (a1 != null) {
                return false;
            }
        }
        this.worldObj.setBlockWithNotify(this.xCoord, this.yCoord, this.zCoord, 0);
        return true;
    }

    public void dropItem(ItemStack stack) {
        InventoryUtils.dropItem(stack, this.worldObj, Vector3.fromTileEntityCenter(this));
    }

    public boolean gettingPowered() {
        return this.worldObj.isBlockIndirectlyGettingPowered(this.xCoord, this.yCoord, this.zCoord);
    }

    public boolean connectRedstone() {
        for (Attachment a : attachments) {
            if (a != null && a.redstone) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the mouth opposite {@code side} is signaling. 1.4.7 has no
     * per-level strong-power output from blocks, so this is just on/off.
     */
    public boolean isProvidingStrongPower(int side) {
        return false;
    }

    /**
     * One side mouth. The redstone/fast bits are persisted to NBT and sync'd
     * over the wire. The {@code a_insertpos}/{@code b_insertpos} pair is
     * tween state — only present on the client, smoothed each tick.
     */
    public class Attachment {
        public final int side;
        public boolean a_eject;
        public boolean b_eject;
        public boolean redstone;
        public boolean invert_redstone;
        public boolean fast;
        public double a_insertpos;
        public double b_insertpos;

        public Attachment(int side) {
            this.side = side;
            this.invert_redstone = true;
            this.b_eject = true;
            this.a_eject = true;
            this.b_insertpos = 1.0;
            this.a_insertpos = 1.0;
        }

        public void read(NBTTagCompound tag) {
            this.invert_redstone = tag.getBoolean("invert_redstone");
            this.redstone = tag.getBoolean("redstone");
            this.fast = tag.getBoolean("fast");
        }

        public void update(boolean client) {
            this.b_insertpos = this.a_insertpos;
            this.a_insertpos = MathHelper.approachExp(this.a_insertpos, approachInsertPos(), 0.5, 0.1);
            if (!client) {
                this.b_eject = this.a_eject;
                this.a_eject = (this.redstone && TileTranslocator.this.gettingPowered()) ^ this.invert_redstone;
                if (this.a_eject != this.b_eject) {
                    this.markUpdate();
                }
            }
        }

        public double approachInsertPos() {
            return this.a_eject ? 1 : 0;
        }

        public void write(PacketCustom packet) {
            packet.writeBoolean(this.a_eject);
            packet.writeBoolean(this.redstone);
            packet.writeBoolean(this.fast);
        }

        public void read(PacketCustom packet, boolean described) {
            this.a_eject = packet.readBoolean();
            this.redstone = packet.readBoolean();
            this.fast = packet.readBoolean();
            if (!described) {
                this.a_insertpos = this.b_insertpos = approachInsertPos();
            }
        }

        public NBTTagCompound write(NBTTagCompound tag) {
            tag.setBoolean("invert_redstone", this.invert_redstone);
            tag.setBoolean("redstone", this.redstone);
            tag.setBoolean("fast", this.fast);
            return tag;
        }

        public boolean activate(EntityPlayer player, int subPart) {
            ItemStack held = player.inventory.getCurrentItem();
            if (held == null && player.isSneaking()) {
                this.stripModifiers();
                this.markUpdate();
            } else if (held == null) {
                if (subPart == 1) {
                    this.invert_redstone = !this.invert_redstone;
                } else {
                    this.openGui(player);
                }
            } else if (held.getItem() == Item.redstone && !this.redstone) {
                this.redstone = true;
                if (!player.capabilities.isCreativeMode) {
                    held.stackSize--;
                }
                if ((TileTranslocator.this.gettingPowered() ^ this.invert_redstone) != this.a_eject) {
                    this.invert_redstone = !this.invert_redstone;
                }
                this.markUpdate();
            } else if (held.getItem() == Item.lightStoneDust && !this.fast) {
                this.fast = true;
                if (!player.capabilities.isCreativeMode) {
                    held.stackSize--;
                }
                this.markUpdate();
            } else {
                this.openGui(player);
            }
            return true;
        }

        public void stripModifiers() {
            if (this.redstone) {
                this.redstone = false;
                TileTranslocator.this.dropItem(new ItemStack(Item.redstone));
                if (this.invert_redstone != this.a_eject) {
                    this.invert_redstone = !this.invert_redstone;
                }
            }
            if (this.fast) {
                this.fast = false;
                TileTranslocator.this.dropItem(new ItemStack(Item.lightStoneDust));
            }
        }

        public void openGui(EntityPlayer player) {
        }

        public void markUpdate() {
            TileTranslocator.this.worldObj.markBlockForUpdate(
                    TileTranslocator.this.xCoord,
                    TileTranslocator.this.yCoord,
                    TileTranslocator.this.zCoord);
            TileTranslocator.this.onInventoryChanged();
        }

        public Collection<ItemStack> getDrops() {
            LinkedList<ItemStack> items = new LinkedList<ItemStack>();
            items.add(new ItemStack(TileTranslocator.this.getBlockType(), 1,
                    TileTranslocator.this.getBlockMetadata()));
            if (this.redstone) {
                items.add(new ItemStack(Item.redstone));
            }
            if (this.fast) {
                items.add(new ItemStack(Item.lightStoneDust));
            }
            return items;
        }

        public int getIconIndex() {
            int i = 0;
            if (this.redstone) {
                i |= TileTranslocator.this.gettingPowered() ? 2 : 1;
            }
            if (this.fast) {
                i |= 4;
            }
            return i;
        }
    }
}

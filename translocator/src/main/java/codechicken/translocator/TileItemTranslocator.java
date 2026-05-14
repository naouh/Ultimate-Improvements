package codechicken.translocator;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import codechicken.core.IGuiPacketSender;
import codechicken.core.ServerUtils;
import codechicken.core.alg.MathHelper;
import codechicken.core.inventory.InventoryRange;
import codechicken.core.inventory.InventorySimple;
import codechicken.core.inventory.InventoryUtils;
import codechicken.core.packet.PacketCustom;
import codechicken.core.vec.BlockCoord;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Item translocator: moves up to 1 stack/tick (or 1 item/tick if no glowstone
 * upgrade) per ejecting side, spread across available receiving sides.
 *
 * <p>{@link ItemAttachment} adds two modifiers on top of the base attachment:
 * <ul>
 *   <li>diamond-nugget = "regulate" — input sides STOP pulling and output
 *       sides STOP filling when the filter's per-stack quantity is satisfied;
 *   <li>iron-ingot = "signal" — emits a comparator/redstone signal when
 *       its filter is satisfied (used as a low-level state machine).
 * </ul>
 */
public class TileItemTranslocator extends TileTranslocator {

    public LinkedList<MovingItem> movingItems = new LinkedList<MovingItem>();

    private static boolean isOreDictMatch(ItemStack stack, String oreName) {
        // 1.4.7 OreDictionary tracks one ID per stack, not a list — and -1 means
        // unregistered (no need to look up that name).
        if (stack == null) return false;
        int id = OreDictionary.getOreID(stack);
        return id != -1 && oreName.equals(OreDictionary.getOreName(id));
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("items")) {
            for (Attachment a : this.attachments) {
                if (a != null) {
                    InventoryUtils.readItemStacksFromTag(((ItemAttachment) a).filters, tag.getTagList("items"));
                }
            }
        }
    }

    @Override
    public void createAttachment(int side) {
        this.attachments[side] = new ItemAttachment(side);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (this.worldObj.isRemote) {
            Iterator<MovingItem> iterator = this.movingItems.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().update()) {
                    iterator.remove();
                }
            }
            return;
        }

        BlockCoord pos = new BlockCoord(this);
        InventoryRange[] attached = new InventoryRange[6];
        for (int i = 0; i < 6; i++) {
            Attachment a = this.attachments[i];
            if (a != null) {
                BlockCoord invpos = pos.copy().offset(i);
                IInventory inv = InventoryUtils.getInventory(this.worldObj, invpos.x, invpos.y, invpos.z);
                if (inv == null) {
                    this.harvestPart(i, true);
                } else {
                    // CCC 0.8.1.6 InventoryRange takes ForgeDirection, not raw int.
                    attached[i] = new InventoryRange(inv, ForgeDirection.getOrientation(i ^ 1));
                }
            }
        }

        for (int i = 0; i < 6; i++) {
            ItemAttachment ia = (ItemAttachment) this.attachments[i];
            if (ia != null && ia.a_eject) {
                int largestQuantity = 0;
                int largestSlot = 0;
                InventoryRange access = attached[i];
                // CCC 0.8.1.6's InventoryRange exposes the slot range as
                // [fslot, lslot) rather than an int[] slots array.
                for (int slot = access.fslot; slot < access.lslot; slot++) {
                    ItemStack stack = access.inv.getStackInSlot(slot);
                    if (stack != null) {
                        int quantity = ia.fast ? stack.stackSize : 1;
                        if (quantity > largestQuantity
                                && (quantity = Math.min(quantity, extractAmount(stack, ia, access))) > largestQuantity
                                && (quantity = Math.min(quantity, insertAmount(stack, attached))) > largestQuantity) {
                            largestSlot = slot;
                            largestQuantity = quantity;
                        }
                    }
                }
                if (largestQuantity > 0) {
                    ItemStack move = InventoryUtils.copyStack(access.inv.getStackInSlot(largestSlot), largestQuantity);
                    this.spreadOutput(move, i, false, attached);
                    this.spreadOutput(move, i, true, attached);
                    InventoryUtils.decrStackSize(access.inv, largestSlot, largestQuantity - move.stackSize);
                }
            }
        }

        // Update comparator/signal outputs in two passes — first read all
        // receiving-side filters, then propagate that as the "satisfied"
        // state to ejecting sides flagged with the signal upgrade.
        boolean allSatisfied = true;
        for (int i = 0; i < 6; i++) {
            ItemAttachment ia = (ItemAttachment) this.attachments[i];
            if (ia != null && !ia.a_eject) {
                boolean b = isSatsified(ia, attached[i]);
                ia.setPowering(b);
                if (!b) {
                    allSatisfied = false;
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            ItemAttachment ia = (ItemAttachment) this.attachments[i];
            if (ia != null && ia.signal && ia.a_eject) {
                ia.setPowering(allSatisfied || !canTransferFilter(ia, attached[i], attached));
            }
        }
    }

    private boolean canTransferFilter(ItemAttachment ia, InventoryRange access, InventoryRange[] attached) {
        boolean filterSet = false;
        for (ItemStack filter : ia.filters) {
            if (filter != null) {
                filterSet = true;
                if (!(ia.regulate && countMatchingStacks(access, filter, false) <= filterCount(ia, filter)
                        || insertAmount(filter, attached) <= 0)) {
                    return true;
                }
            }
        }
        return !filterSet;
    }

    private boolean isSatsified(ItemAttachment ia, InventoryRange access) {
        boolean filterSet = false;
        for (ItemStack filter : ia.filters) {
            if (filter != null) {
                filterSet = true;
                boolean unsatisfied = ia.regulate
                        ? countMatchingStacks(access, filter, !ia.a_eject) < filterCount(ia, filter)
                        : InventoryUtils.getInsertableQuantity(access.inv, access.fslot, access.lslot, filter) > 0;
                if (unsatisfied) {
                    return false;
                }
            }
        }
        return filterSet || !hasEmptySpace(access);
    }

    private boolean hasEmptySpace(InventoryRange inv) {
        // 1.4.7 has no per-slot ISidedInventory permissions, so any slot in
        // our [fslot, lslot) range is fair game for insertion checks.
        for (int slot = inv.fslot; slot < inv.lslot; slot++) {
            ItemStack stack = inv.inv.getStackInSlot(slot);
            if (stack == null
                    || (stack.isStackable()
                        && stack.stackSize < Math.min(stack.getMaxStackSize(), inv.inv.getInventoryStackLimit()))) {
                return true;
            }
        }
        return false;
    }

    private int filterCount(ItemAttachment ia, ItemStack stack) {
        boolean filterSet = false;
        int match = 0;
        for (ItemStack filter : ia.filters) {
            if (filter != null) {
                filterSet = true;
                if (InventoryUtils.canStack(stack, filter)) {
                    match += filter.stackSize;
                }
            }
        }
        return filterSet ? match : -1;
    }

    private void spreadOutput(ItemStack move, int src, boolean rspass, InventoryRange[] attached) {
        if (move.stackSize == 0) {
            return;
        }
        int outputCount = 0;
        int[] outputQuantities = new int[6];
        for (int i = 0; i < 6; i++) {
            ItemAttachment ia = (ItemAttachment) this.attachments[i];
            if (ia != null && !ia.a_eject && ia.redstone == rspass) {
                outputQuantities[i] = insertAmount(move, ia, attached[i]);
                if (outputQuantities[i] > 0) {
                    outputCount++;
                }
            }
        }
        for (int dst = 0; dst < 6 && move.stackSize > 0; dst++) {
            int qty = outputQuantities[dst];
            if (qty > 0) {
                qty = Math.min(qty, move.stackSize / outputCount + this.worldObj.rand.nextInt(move.stackSize % outputCount + 1));
                outputCount--;
                if (qty != 0) {
                    InventoryRange range = attached[dst];
                    ItemStack add = InventoryUtils.copyStack(move, qty);
                    InventoryUtils.mergeItemStack(range.inv, range.fslot, range.lslot, add, true);
                    move.stackSize -= qty;
                    this.sendTransferPacket(src, dst, add);
                }
            }
        }
    }

    private int countMatchingStacks(InventoryRange inv, ItemStack filter, boolean insertable) {
        int c = 0;
        // 1.4.7 has no per-slot ISidedInventory permissions, so the insertable
        // / extractable flag is moot — whatever's in range counts.
        for (int slot = inv.fslot; slot < inv.lslot; slot++) {
            ItemStack stack = inv.inv.getStackInSlot(slot);
            if (stack != null && InventoryUtils.canStack(filter, stack)) {
                c += stack.stackSize;
            }
        }
        return c;
    }

    private void sendTransferPacket(int i, int j, ItemStack add) {
        PacketCustom packet = new PacketCustom(TranslocatorSPH.channel, 2);
        packet.writeCoord(this.xCoord, this.yCoord, this.zCoord);
        packet.writeByte((i << 4) | j);
        packet.writeItemStack(add);
        packet.sendToChunk(this.worldObj, this.xCoord >> 4, this.zCoord >> 4);
    }

    private int insertAmount(ItemStack stack, InventoryRange[] attached) {
        int insertAmount = 0;
        for (int i = 0; i < 6; i++) {
            ItemAttachment ia = (ItemAttachment) this.attachments[i];
            if (ia != null && !ia.a_eject) {
                insertAmount += insertAmount(stack, ia, attached[i]);
            }
        }
        return insertAmount;
    }

    private int insertAmount(ItemStack stack, ItemAttachment ia, InventoryRange range) {
        int filter = filterCount(ia, stack);
        if (filter == 0) {
            return 0;
        }
        int fit = InventoryUtils.getInsertableQuantity(range.inv, range.fslot, range.lslot, stack);
        if (fit == 0) {
            return 0;
        }
        if (ia.regulate && filter > 0) {
            fit = Math.min(fit, filter - countMatchingStacks(range, stack, true));
        }
        return fit > 0 ? fit : 0;
    }

    private int extractAmount(ItemStack stack, ItemAttachment ia, InventoryRange range) {
        int filter = filterCount(ia, stack);
        if (filter == 0) {
            return ia.regulate ? stack.getMaxStackSize() : 0;
        }
        int qty = filter < 0 ? stack.getMaxStackSize() : filter;
        if (ia.regulate && filter > 0) {
            qty = Math.min(qty, countMatchingStacks(range, stack, false) - filter);
        }
        return qty > 0 ? qty : 0;
    }

    @Override
    public void handleDescriptionPacket(PacketCustom packet) {
        if (packet.getType() == 2) {
            this.movingItems.add(new MovingItem(packet));
        } else {
            super.handleDescriptionPacket(packet);
        }
    }

    @Override
    public boolean isProvidingStrongPower(int side) {
        ItemAttachment ia = (ItemAttachment) this.attachments[side ^ 1];
        return ia != null && ia.a_powering;
    }

    public class ItemAttachment extends Attachment {
        boolean regulate;
        boolean a_powering;
        boolean signal;
        ItemStack[] filters;

        public ItemAttachment(int side) {
            super(side);
            this.regulate = false;
            this.a_powering = false;
            this.signal = false;
            this.filters = new ItemStack[9];
        }

        public void setPowering(boolean b) {
            if (this.signal && b != this.a_powering) {
                this.a_powering = b;
                BlockCoord pos = new BlockCoord(TileItemTranslocator.this);
                TileItemTranslocator.this.worldObj.notifyBlocksOfNeighborChange(pos.x, pos.y, pos.z,
                        Translocator.blockTranslocator.blockID);
                pos.offset(this.side);
                TileItemTranslocator.this.worldObj.notifyBlocksOfNeighborChange(pos.x, pos.y, pos.z,
                        Translocator.blockTranslocator.blockID);
                this.markUpdate();
            }
        }

        @Override
        public boolean activate(EntityPlayer player, int subPart) {
            ItemStack held = player.inventory.getCurrentItem();
            if (held == null) {
                return super.activate(player, subPart);
            }
            // Accept any OreDictionary-registered "diamondNugget" (ours, plus
                // ThaumicBees/EE3/etc. forms) — strict == would only match our own.
            if (isOreDictMatch(held, "diamondNugget") && !this.regulate) {
                this.regulate = true;
                if (!player.capabilities.isCreativeMode) {
                    held.stackSize--;
                }
                this.markUpdate();
                return true;
            }
            if (held.getItem() == Item.ingotIron && !this.signal) {
                this.a_powering = true;
                this.signal = true;
                if (!player.capabilities.isCreativeMode) {
                    held.stackSize--;
                }
                this.markUpdate();
                return true;
            }
            return super.activate(player, subPart);
        }

        @Override
        public void stripModifiers() {
            super.stripModifiers();
            if (this.regulate) {
                this.regulate = false;
                TileItemTranslocator.this.dropItem(new ItemStack(Translocator.itemDiamondNugget));
            }
            if (this.signal) {
                this.setPowering(false);
                this.signal = false;
                TileItemTranslocator.this.dropItem(new ItemStack(Item.ingotIron));
            }
        }

        @Override
        public Collection<ItemStack> getDrops() {
            Collection<ItemStack> stuff = super.getDrops();
            if (this.regulate) {
                stuff.add(new ItemStack(Translocator.itemDiamondNugget));
            }
            if (this.signal) {
                stuff.add(new ItemStack(Item.ingotIron));
            }
            return stuff;
        }

        @Override
        public int getIconIndex() {
            int i = super.getIconIndex();
            if (this.regulate) {
                i |= 8;
            }
            if (this.signal) {
                i |= this.a_powering ? 32 : 16;
            }
            return i;
        }

        @Override
        public void openGui(EntityPlayer player) {
            openItemGui(player, this.filters, this.regulate ? "Regulate" : "Filter");
        }

        private void openItemGui(EntityPlayer player, final ItemStack[] filters, final String string) {
            ServerUtils.openSMPContainer(
                (EntityPlayerMP) player,
                new ContainerItemTranslocator(new InventorySimple(filters, filterStackLimit()) {
                    @Override
                    public void onInventoryChanged() {
                        ItemAttachment.this.markUpdate();
                    }
                }, player.inventory),
                new IGuiPacketSender() {
                    @Override
                    public void sendPacket(EntityPlayerMP player, int windowId) {
                        PacketCustom packet = new PacketCustom(TranslocatorSPH.channel, 4);
                        packet.writeByte(windowId);
                        packet.writeShort(filterStackLimit());
                        packet.writeString(string);
                        packet.sendToPlayer(player);
                    }
                });
        }

        private int filterStackLimit() {
            if (this.regulate) return 65535;
            if (this.fast) return 64;
            return 1;
        }

        @Override
        public void read(NBTTagCompound tag) {
            super.read(tag);
            this.regulate = tag.getBoolean("regulate");
            this.signal = tag.getBoolean("signal");
            this.a_powering = tag.getBoolean("powering");
            InventoryUtils.readItemStacksFromTag(this.filters, tag.getTagList("filters"));
        }

        @Override
        public NBTTagCompound write(NBTTagCompound tag) {
            tag.setBoolean("regulate", this.regulate);
            tag.setBoolean("signal", this.signal);
            tag.setBoolean("powering", this.a_powering);
            NBTTagList filterList = InventoryUtils.writeItemStacksToTag(this.filters, 65536);
            tag.setTag("filters", filterList);
            return super.write(tag);
        }

        @Override
        public void read(PacketCustom packet, boolean described) {
            super.read(packet, described);
            this.regulate = packet.readBoolean();
            this.signal = packet.readBoolean();
            this.a_powering = packet.readBoolean();
        }

        @Override
        public void write(PacketCustom packet) {
            super.write(packet);
            packet.writeBoolean(this.regulate);
            packet.writeBoolean(this.signal);
            packet.writeBoolean(this.a_powering);
        }
    }

    /**
     * Static rather than inner-instance because (a) it doesn't reference any
     * outer state and (b) at least one coremod transformer in the modpack
     * was NPE-ing during class loading on the synthetic this$0 of the
     * non-static inner form, which surfaced as NoClassDefFoundError when the
     * first transfer packet tried to instantiate it.
     */
    public static class MovingItem {
        public int src;
        public int dst;
        public ItemStack stack;
        public double a_progress;
        public double b_progress;

        public MovingItem(PacketCustom packet) {
            int b = packet.readUnsignedByte();
            this.src = b >> 4;
            this.dst = b & 0xF;
            this.stack = packet.readItemStack();
        }

        public boolean update() {
            if (this.a_progress >= 1.0) {
                return true;
            }
            this.b_progress = this.a_progress;
            this.a_progress = MathHelper.approachLinear(this.a_progress, 1.0, 0.2);
            return false;
        }
    }
}

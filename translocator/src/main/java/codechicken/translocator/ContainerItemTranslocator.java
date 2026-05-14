package codechicken.translocator;

import java.util.List;

import codechicken.core.inventory.ContainerExtended;
import codechicken.core.inventory.InventorySimple;
import codechicken.core.inventory.SlotDummy;
import codechicken.core.packet.PacketCustom;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * Server-bound container for the filter GUI. Backed by 9 dummy slots that
 * accept ghost items (no real transfer), plus the player's hotbar/inventory
 * underneath. Stack sizes can exceed 64 when the "regulate" mode is on, hence
 * {@link #sendLargeStack(ItemStack, int, List)} sending raw shorts instead of
 * vanilla single-byte stack-size packets.
 */
public class ContainerItemTranslocator extends ContainerExtended {

    IInventory inv;

    public ContainerItemTranslocator(InventorySimple inv, InventoryPlayer playerInv) {
        this.inv = inv;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                this.addSlotToContainer(new SlotDummy(inv, y + x * 3, 62 + y * 18, 17 + x * 18, inv.limit));
            }
        }
        this.bindPlayerInventory(playerInv);
    }

    public String getInvName() {
        return this.inv.getInvName();
    }

    @Override
    public void sendLargeStack(ItemStack stack, int slot, List players) {
        PacketCustom packet = new PacketCustom(TranslocatorSPH.channel, 5);
        packet.writeByte(slot);
        packet.writeItemStack(stack, true);
        for (Object p : players) {
            packet.sendToPlayer((EntityPlayer) p);
        }
    }
}

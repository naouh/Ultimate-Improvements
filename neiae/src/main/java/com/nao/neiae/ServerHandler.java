package com.nao.neiae;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;

/**
 * Server-side handler: receives the recipe packet from the client and pulls
 * each ingredient out of the ME network into the open Crafting Terminal's
 * 3x3 matrix.
 *
 * <p>Heavy use of reflection here because every AE rv9 type we touch
 * ({@code ContainerCraftingTerminal}, {@code NetworkedIMEI},
 * {@code SlotCraftingMatrix}, the {@code IMEInventory} interface) is in the
 * AE jar whose own bytecode references obfuscated MC types — Voldeloom's
 * compile classpath can't satisfy those references, so we look them up by
 * name at runtime where everything is already loaded.
 */
public class ServerHandler implements IPacketHandler {

    /** Cached reflective handles, resolved lazily on first packet. */
    private static volatile boolean ready = false;
    private static Class<?>  cCraftingTerminalCls;
    private static Class<?>  cSlotMatrixCls;
    private static Method    mGetNetworkIME;
    private static Method    mExtractItems;
    private static Method    mAddItems;

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player) {
        if (packet == null || packet.data == null) return;
        if (!NeiAeMod.CHANNEL.equals(packet.channel)) return;
        if (!(player instanceof EntityPlayerMP)) return;
        EntityPlayerMP epm = (EntityPlayerMP) player;

        try {
            if (!ensureReady()) return;

            NBTTagCompound payload = CompressedStreamTools.decompress(packet.data);
            NBTTagList ingredients = payload.getTagList("ingredients");
            if (ingredients == null || ingredients.tagCount() == 0) return;

            Container container = epm.openContainer;
            if (container == null || !cCraftingTerminalCls.isInstance(container)) {
                epm.sendChatToPlayer("[NeiAe] Open the ME Crafting Terminal first.");
                return;
            }

            Object imei = mGetNetworkIME.invoke(container);
            if (imei == null) {
                epm.sendChatToPlayer("[NeiAe] ME network not available.");
                return;
            }

            List<Slot> matrixSlots = collectMatrixSlots(container);
            if (matrixSlots.size() < 9) {
                epm.sendChatToPlayer("[NeiAe] Could not find 9 crafting-matrix slots.");
                return;
            }

            // Empty the whole matrix back into the ME first — slots the new recipe doesn't
            // fill would otherwise keep whatever was in them from a previous recipe. Whatever
            // the network refuses (full, or no addItems) stays in its slot; the recipe loop
            // below retries those slots one by one.
            for (int i = 0; i < matrixSlots.size(); i++) {
                Slot s = matrixSlots.get(i);
                ItemStack existing = s.getStack();
                if (existing == null || existing.stackSize <= 0) continue;
                s.putStack(returnToNetwork(imei, existing));
            }

            int filled = 0;
            int missing = 0;
            for (int i = 0; i < ingredients.tagCount(); i++) {
                NBTTagCompound tag = (NBTTagCompound) ingredients.tagAt(i);
                int slotIdx = tag.getInteger("slot");
                if (slotIdx < 0 || slotIdx >= matrixSlots.size()) continue;

                ItemStack requested = ItemStack.loadItemStackFromNBT(tag);
                if (requested == null) continue;
                requested.stackSize = 1;

                ItemStack extracted = (ItemStack) mExtractItems.invoke(imei, requested);
                if (extracted == null || extracted.stackSize <= 0) {
                    missing++;
                    continue;
                }

                Slot dst = matrixSlots.get(slotIdx);
                ItemStack existing = dst.getStack();

                // If the slot still holds something, try once more to push it back into
                // the ME. If the network refuses (all or part of it), keep what it refused
                // in the slot and hand back what we just extracted — better to leave both
                // intact than to silently destroy either.
                if (existing != null && existing.stackSize > 0) {
                    ItemStack leftover = returnToNetwork(imei, existing);
                    if (leftover != null) {
                        dst.putStack(leftover);
                        giveBack(epm, imei, extracted);
                        missing++;
                        continue;
                    }
                }

                dst.putStack(extracted);
                filled++;
            }

            // Push the new matrix to the client now rather than on the next player tick.
            // (Looking this up reflectively by its MCP name never worked — the runtime
            // method is obfuscated — so the old code silently skipped it.)
            container.detectAndSendChanges();

            String msg = "[NeiAe] " + filled + " ingredients placed";
            if (missing > 0) msg += ", " + missing + " missing from ME";
            epm.sendChatToPlayer(msg);
        } catch (Throwable t) {
            System.err.println("[NeiAe] Server packet handler failed:");
            t.printStackTrace();
        }
    }

    /**
     * Pushes {@code stack} into the ME network and returns what the network refused:
     * {@code null} when everything was accepted, otherwise the leftover to keep in the
     * slot. AE may hand back the same instance with a reduced count or a fresh stack,
     * so callers always store the returned value instead of trusting the original.
     * Without {@code addItems} (older AE) nothing is moved and the stack comes back
     * untouched.
     */
    private static ItemStack returnToNetwork(Object imei, ItemStack stack) {
        if (mAddItems == null) return stack;
        try {
            Object leftover = mAddItems.invoke(imei, stack);
            if (leftover instanceof ItemStack && ((ItemStack) leftover).stackSize > 0) {
                return (ItemStack) leftover;
            }
            return null;
        } catch (Throwable t) {
            System.err.println("[NeiAe] addItems failed:");
            t.printStackTrace();
            return stack;
        }
    }

    /**
     * Returns an item we extracted but could not place: ME network first, then the
     * player's inventory, then dropped at their feet — never discarded.
     */
    private static void giveBack(EntityPlayerMP epm, Object imei, ItemStack stack) {
        ItemStack left = returnToNetwork(imei, stack);
        if (left == null) return;
        if (!epm.inventory.addItemStackToInventory(left)) {
            epm.dropPlayerItem(left);
        }
    }

    private static synchronized boolean ensureReady() {
        if (ready) return true;
        try {
            cCraftingTerminalCls  = Class.forName("appeng.me.container.ContainerCraftingTerminal");
            cSlotMatrixCls        = Class.forName("appeng.slot.SlotCraftingMatrix");
            Class<?> cMEInventory = Class.forName("appeng.api.me.util.IMEInventory");
            // Use the GetNetworkIME() getter rather than the public imeiinv field:
            // ContainerTerminal also declares `public GuiTerminal myGui`, and any
            // call to getField/getDeclaredField forces the JVM to resolve every
            // declared field's type — GuiTerminal is client-only, so on a
            // dedicated server that throws NoClassDefFoundError. getMethod only
            // resolves method signatures, and none of the methods reference it.
            mGetNetworkIME        = Class.forName("appeng.me.container.ContainerTerminal").getMethod("GetNetworkIME");
            mExtractItems         = cMEInventory.getMethod("extractItems", ItemStack.class);
            // addItems is optional — if AE drops it, the slot-freeing path
            // refuses to overwrite occupied slots, which is the safe default.
            try {
                mAddItems = cMEInventory.getMethod("addItems", ItemStack.class);
            } catch (NoSuchMethodException ignore) {
                mAddItems = null;
            }
            ready = true;
            return true;
        } catch (Throwable t) {
            System.err.println("[NeiAe] Reflection setup failed:");
            t.printStackTrace();
            return false;
        }
    }

    /** Returns matrix slots in stable order by their inventory slot index (0..8). */
    @SuppressWarnings("unchecked")
    private static List<Slot> collectMatrixSlots(Container container) {
        List<Slot> matrix = new ArrayList<Slot>();
        for (Object o : container.inventorySlots) {
            if (cSlotMatrixCls.isInstance(o)) matrix.add((Slot) o);
        }
        Collections.sort(matrix, new Comparator<Slot>() {
            @Override
            public int compare(Slot a, Slot b) {
                return a.getSlotIndex() - b.getSlotIndex();
            }
        });
        return matrix;
    }
}

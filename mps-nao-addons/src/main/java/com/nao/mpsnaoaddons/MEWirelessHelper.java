package com.nao.mpsnaoaddons;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * ME Wireless Terminal power module. Channels Applied Energistics' wireless
 * terminal through the power tool:
 *
 * <ul>
 *   <li>When the module is the active mode, right-click anywhere to open
 *       the wireless ME GUI (works exactly like AE's own wireless terminal
 *       item — same NBT keys, same controller lookup).</li>
 *   <li>The power tool can be dropped into the ME Controller's wireless slot
 *       to be linked, regardless of which mode is currently active — only
 *       requires the module to be INSTALLED. The two AE classes that
 *       short-circuit non-terminal stacks (slot acceptance + encode-step)
 *       are patched in via {@link com.nao.mpsnaoaddons.transform.MEWirelessAccessTransformer}.</li>
 * </ul>
 *
 * <p>If AE isn't present, everything in here no-ops — the module just
 * registers and stays inert, same as our other cross-mod helpers.
 */
public final class MEWirelessHelper {

    public static final String MODULE_NAME = "ME Wireless Terminal";
    public static final double ENERGY_PER_USE = 100.0;

    private static Class<?>  cWirelessTerminal;
    private static Class<?>  cTileController;
    private static Method    mOnItemRightClick;
    private static Field     fEncryptionKey;
    private static ItemStack wirelessTerminalStack;
    /** The actual {@code ItemWirelessTerminal} sub-item instance — {@code
     *  wirelessTerminalStack.getItem()} returns the {@code AppEngMultiItem}
     *  wrapper, which dispatches by damage to this sub-item. We need the
     *  sub-item to reflection-invoke its {@code onItemRightClick}. */
    private static Object    wirelessSubItem;
    private static boolean   inited = false;

    private MEWirelessHelper() {}

    private static synchronized void init() {
        if (inited) return;
        inited = true;
        try {
            cWirelessTerminal = Class.forName("appeng.me.item.ItemWirelessTerminal");
            cTileController   = Class.forName("appeng.me.tile.TileController");
            // FML's deobf remapper may rewrite the SRG/MCP method name
            // ("onItemRightClick") down to the obf name ("a") at load time, or
            // it may not — depends on the mod's signature in srg-to-obf table.
            // Pick the method by full signature so we don't depend on whichever
            // form survives.
            mOnItemRightClick = findMethodBySignature(cWirelessTerminal,
                    ItemStack.class, new Class<?>[]{ItemStack.class, World.class, EntityPlayer.class});
            if (mOnItemRightClick != null) mOnItemRightClick.setAccessible(true);
            fEncryptionKey    = cTileController.getField("EncryptionKey");
            Class<?> cAEItems = Class.forName("appeng.api.Items");
            wirelessTerminalStack = (ItemStack) cAEItems.getField("itemWirelessTerminal").get(null);
            // The Item on the stack is AE's AppEngMultiItem (a multi-sub-item
            // wrapper that dispatches by damage value). The actual
            // ItemWirelessTerminal lives in its SubItems list. Resolve it now.
            if (wirelessTerminalStack != null) {
                Class<?> cMulti = Class.forName("appeng.common.AppEngMultiItem");
                Object multi = wirelessTerminalStack.getItem();
                if (cMulti.isInstance(multi)) {
                    wirelessSubItem = cMulti.getMethod("getSubItem", ItemStack.class)
                            .invoke(multi, wirelessTerminalStack);
                }
            }
            System.out.println("[MEWireless] Init: methodLookup="
                    + (mOnItemRightClick == null ? "FAIL" : mOnItemRightClick.getName())
                    + " terminalStack=" + (wirelessTerminalStack == null ? "null" : wirelessTerminalStack)
                    + " subItem=" + (wirelessSubItem == null ? "null" : wirelessSubItem.getClass().getName()));
        } catch (Throwable t) {
            System.err.println("[MEWireless] Applied Energistics not found; module will be inert: " + t);
        }
    }

    /** Find an instance method on {@code c} whose return type and parameter
     *  types match exactly, regardless of method name. Walks declared methods
     *  plus inherited (in case the override lives upstream). */
    private static java.lang.reflect.Method findMethodBySignature(Class<?> c,
            Class<?> returnType, Class<?>[] params) {
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            if (!returnType.equals(m.getReturnType())) continue;
            Class<?>[] mp = m.getParameterTypes();
            if (mp.length != params.length) continue;
            boolean ok = true;
            for (int i = 0; i < mp.length; i++) {
                if (!mp[i].equals(params[i])) { ok = false; break; }
            }
            if (ok) return m;
        }
        return null;
    }

    /** Exposed for the registration code so we charge the AE wireless
     *  terminal as one of the install costs. */
    public static ItemStack getWirelessTerminalStack() {
        init();
        return wirelessTerminalStack;
    }

    public static boolean isActiveOnPowerTool(ItemStack stack) {
        return OmniWrenchHelper.hasActiveModule(stack, MODULE_NAME);
    }

    /** Module INSTALLED (regardless of active mode). Slot acceptance and the
     *  encode-step use this so the user doesn't have to switch the tool's
     *  active mode just to drop it in the controller's slot. */
    public static boolean hasModuleInstalled(ItemStack stack) {
        return OmniWrenchHelper.hasModule(stack, MODULE_NAME);
    }

    /** Called from ASM-injected code in {@code SlotWirelessTerminal.a(ItemStack)}.
     *  Public because INVOKESTATIC addresses it by name; not called from
     *  regular Java in this mod. */
    public static boolean acceptInSlot(ItemStack stack) {
        return hasModuleInstalled(stack);
    }

    /**
     * Called from ASM-injected code prepended to {@code TileController
     * .encodeWireless(ItemStack)}. If {@code stack} is a power tool with
     * the ME module installed, write the same five NBT keys AE writes onto
     * its own wireless terminal (encKey, d, x, y, z) and return {@code true}
     * to make the patched method skip its own check.
     */
    public static boolean tryEncodePowerTool(Object tileController, ItemStack stack) {
        if (stack == null) return false;
        if (!hasModuleInstalled(stack)) return false;
        init();
        if (fEncryptionKey == null) return false;
        try {
            TileEntity te = (TileEntity) tileController;
            String encKey = (String) fEncryptionKey.get(tileController);
            if (encKey == null || encKey.length() == 0) return false;

            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }
            tag.setString("encKey", encKey);
            tag.setInteger("d", te.worldObj.provider.dimensionId);
            tag.setInteger("x", te.xCoord);
            tag.setInteger("y", te.yCoord);
            tag.setInteger("z", te.zCoord);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    /**
     * Called from ASM-injected code at the top of
     * {@code MuseItemUtils.removeModule(ItemStack, String)}. If our ME
     * Wireless module is being salvaged, wipe the AE link NBT keys
     * (encKey, d, x, y, z) so the salvaged tool stops appearing as a
     * linked terminal to the network and a fresh re-install starts clean.
     *
     * <p>AE writes its wireless keys at the root of the stack's NBT; MPS
     * stores its module data under a sub-tag — distinct namespaces, so
     * targeting the AE keys directly here is safe.
     */
    public static void onModuleRemoved(ItemStack stack, String moduleName) {
        if (stack == null || moduleName == null) return;
        if (!MODULE_NAME.equals(moduleName)) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return;
        tag.removeTag("encKey");
        tag.removeTag("d");
        tag.removeTag("x");
        tag.removeTag("y");
        tag.removeTag("z");
        // MPS doesn't clear the active "Mode" string when a module is
        // salvaged, so if ME Wireless was the selected mode, the next
        // right-click would still route through our onItemRightClick
        // (active=true) and AE would print "Wireless Access Terminal — No
        // Signal". Clear the stale Mode tag so the tool drops back to
        // whatever active mode MPS picks next.
        if (tag.hasKey("mmmpsmod")) {
            NBTTagCompound mpsTag = tag.getCompoundTag("mmmpsmod");
            if (MODULE_NAME.equals(mpsTag.getString("Mode"))) {
                mpsTag.removeTag("Mode");
            }
        }
    }

    /**
     * Right-click handler, reached from {@link OmniWrenchEventHandler} on the
     * server-side {@code PlayerInteractEvent.RIGHT_CLICK_AIR} (fired by
     * {@code NetServerHandler.handlePlace} for an empty-air use). Drains energy
     * then forwards to AE's {@code ItemWirelessTerminal.onItemRightClick(stack,
     * world, player)}, which reads the NBT off our power tool and (if linked +
     * in range) opens the wireless ME GUI for that controller.
     */
    public static boolean handleClick(EntityPlayer player, World world, ItemStack stack) {
        init();
        if (mOnItemRightClick == null || wirelessSubItem == null) {
            return false; // AE absent — init() already logged it once
        }
        try {
            if (!OmniWrenchHelper.drain(stack, ENERGY_PER_USE)) {
                return false;
            }
            // Invoke on the AE sub-item instance, not the multi-item wrapper.
            // The sub-item's onItemRightClick reads NBT off the passed stack
            // (our power tool) and opens the wireless GUI.
            mOnItemRightClick.invoke(wirelessSubItem, stack, world, player);
            return true;
        } catch (Throwable t) {
            System.err.println("[MEWireless] handleClick: AE onItemRightClick threw:");
            t.printStackTrace();
            return false;
        }
    }
}

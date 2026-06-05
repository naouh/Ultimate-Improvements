package com.nao.mpsnaoaddons;

import net.machinemuse.api.IModularItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Fixes a ModularPowersuits stacking bug: {@code MuseItemUtils.getMuseItemTag(stack)} creates and
 * <b>persists</b> an empty {@code "mmmpsmod"} NBT compound on ANY ItemStack it's asked about — not
 * just real powersuit gear. Since MPS (and other code) calls it to check modules on inventory items,
 * ordinary items (ingots, cooked food, ...) end up carrying an empty {@code mmmpsmod} tag. A tagged
 * item no longer stacks with an untagged identical one, so e.g. fresh furnace output refuses to merge
 * with the stamped stack already in your inventory.
 *
 * <p>{@link com.nao.mpsnaoaddons.transform.MuseItemUtilsTransformer} injects a guard at the top of
 * {@code getMuseItemTag} that calls {@link #museTagFor(ItemStack)}: if the stack is a genuine modular
 * item we return {@code null} (let MPS run its normal create-and-persist path, so module data still
 * works); otherwise we return a fresh detached empty tag that is NEVER written back onto the stack,
 * so non-modular items are never stamped.
 */
public final class MuseTagGuard {
    private MuseTagGuard() {}

    /**
     * @return {@code null} to let MPS tag the stack normally (it is an {@link IModularItem}), or a
     *         fresh detached {@link NBTTagCompound} to be returned instead (non-modular stack — do
     *         not stamp it).
     */
    public static NBTTagCompound museTagFor(ItemStack stack) {
        if (stack == null) {
            return new NBTTagCompound();
        }
        if (stack.getItem() instanceof IModularItem) {
            return null; // real modular item -> let MPS create/persist its tag normally
        }
        cleanIfStamped(stack);
        return new NBTTagCompound(); // detached empty tag -> never written back, no new stamp
    }

    /**
     * Backward-compat cleanup: strip a leftover {@code "mmmpsmod"} stamp off a non-modular item so an
     * already-stamped stack becomes mergeable again, and drop the whole tag if it ends up empty (an
     * empty {@code "{}"} compound still blocks stacking against a null-tag item, since
     * areItemStackTagsEqual treats null vs {} as different). No-op on real modular gear and on clean
     * items. Safe to call on anything - used both from the getMuseItemTag guard and the active
     * inventory sweep ({@link com.nao.mpsnaoaddons.MmmpsmodCleanupHandler}).
     */
    public static void cleanIfStamped(ItemStack stack) {
        if (stack == null || stack.getItem() instanceof IModularItem || !stack.hasTagCompound()) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag.hasKey("mmmpsmod")) {
            tag.removeTag("mmmpsmod");
            if (tag.hasNoTags()) {
                stack.setTagCompound(null);
            }
        }
    }
}

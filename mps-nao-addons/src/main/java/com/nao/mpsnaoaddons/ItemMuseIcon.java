package com.nao.mpsnaoaddons;

import net.machinemuse.general.gui.MuseIcon;
import net.minecraft.item.ItemStack;

/**
 * A {@link MuseIcon} that carries an {@link ItemStack} reference for our
 * custom item-texture rendering. We can't change MPS' rendering API (it
 * takes a {@code MuseIcon} parameter everywhere), so we extend the type and
 * stash the stack on the subclass. {@link com.nao.mpsnaoaddons.transform
 * .MuseRendererTransformer} patches the two draw entry points
 * ({@code drawIconAt}, {@code drawIconPartial}) to {@code instanceof}-check
 * this subclass and dispatch to {@link CustomIconRenderer} when matched.
 *
 * <p>The super-class fields ({@code texturefile}, {@code index}) are
 * irrelevant when we dispatch via item rendering, but they have to be
 * sane values in case some code path bypasses our patch — pointing them
 * at MPS' default sebkicons sheet means a worst-case fallback shows a
 * blue orb, not a missing texture.
 */
public class ItemMuseIcon extends MuseIcon {

    private final ItemStack stack;

    public ItemMuseIcon(ItemStack stack) {
        super("/resources/machinemuse/sebkicons.png", 2 /* ORB_1_BLUE */);
        this.stack = stack;
    }

    public ItemStack getStack() {
        return stack;
    }
}

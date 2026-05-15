package com.quarryplus.tile;

/**
 * Marker interface for tiles whose behaviour is modulated by the four supported
 * enchantments: Efficiency, Unbreaking, Fortune and Silk Touch. The values stored here are
 * the levels — not the enchantment IDs — and they're written into the item's NBT when the
 * machine is broken so the next placement preserves them.
 *
 * <p>The PowerManager keys all of its budget recalculation off these getters, so the only
 * machine state that needs persistence beyond the tile NBT is whatever the renderer needs.
 */
public interface IEnchantableTile {

    byte getEfficiencyLevel();
    byte getUnbreakingLevel();
    byte getFortuneLevel();
    boolean getSilkTouch();

    void setEfficiencyLevel(byte level);
    void setUnbreakingLevel(byte level);
    void setFortuneLevel(byte level);
    void setSilkTouch(boolean on);
}

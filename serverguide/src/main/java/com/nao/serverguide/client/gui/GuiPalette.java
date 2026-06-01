package com.nao.serverguide.client.gui;

/** Shared ARGB constants for the guide GUI - matches the QuestCycle / ClaimTeam palette style. */
public final class GuiPalette {
    private GuiPalette() {}

    public static final int BG          = 0xE6101418; // 90% dark
    public static final int PANEL       = 0xFF1A2028;
    public static final int BORDER      = 0xFF3A4250;
    public static final int TEXT        = 0xFFEAEAEA;
    public static final int TEXT_DIM    = 0xFFA0A8B0;
    public static final int TEXT_MUTED  = 0xFF707880;

    public static final int TAB_ACTIVE  = 0xFF2E3540;
    public static final int TAB_HOVER   = 0xFF252A33;
    public static final int TAB_IDLE    = 0xFF1A2028;

    public static final int HEADING     = 0xFFFFB000; // gold
    public static final int SUBHEADING  = 0xFFE8D24A; // yellow
    public static final int BULLET      = 0xFF7FC8FF; // light blue dot
    public static final int ACCENT      = 0xFF44CC88;

    public static final int SCROLL_TRACK = 0xFF0E1217;
    public static final int SCROLL_THUMB = 0xFF4A5564;
}

package com.nao.questcycle.client.gui;

/** Shared ARGB constants for the book GUI - mirrors ClaimMapGui's palette style. */
public final class GuiPalette {
	private GuiPalette() {}

	public static final int BG          = 0xE6101418; // 90% dark
	public static final int PANEL       = 0xFF1A2028;
	public static final int PANEL_SOFT  = 0xFF22272F;
	public static final int BORDER      = 0xFF3A4250;
	public static final int BORDER_HI   = 0xFF5C6878;
	public static final int TEXT        = 0xFFEAEAEA;
	public static final int TEXT_DIM    = 0xFFA0A8B0;
	public static final int TEXT_MUTED  = 0xFF707880;

	public static final int TAB_ACTIVE  = 0xFF2E3540;
	public static final int TAB_HOVER   = 0xFF252A33;
	public static final int TAB_IDLE    = 0xFF1A2028;

	public static final int PROGRESS_BG = 0xFF0E1217;
	public static final int PROGRESS_FG = 0xFF22BB55;
	public static final int PROGRESS_DONE = 0xFF40DD60;
	public static final int PROGRESS_FAR= 0xFFBB4422;

	public static final int ACCENT_PRESTIGE = 0xFFFFAA00;
	public static final int ACCENT_ACHIEV   = 0xFF60AAFF;
	public static final int ACCENT_PROFILE  = 0xFFCC88FF;
	public static final int ACCENT_LEADER   = 0xFF44CC88;
}

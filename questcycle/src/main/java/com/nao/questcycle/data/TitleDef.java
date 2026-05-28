package com.nao.questcycle.data;

/**
 * A title that gets granted when an Achievement quest is completed.
 * displayName supports vanilla §-color codes; the active title is displayed
 * verbatim as a chat prefix and (via Packet201PlayerInfo) on the tab list.
 */
public final class TitleDef {
	public final String id;
	public final String displayName;
	/** Short tag for the tab list (16-char total budget shared with username). */
	public final String shortTag;
	public final String grantedByQuestId;

	public TitleDef(String id, String displayName, String shortTag, String grantedByQuestId) {
		this.id = id;
		this.displayName = displayName;
		this.shortTag = shortTag != null && shortTag.length() > 0 ? shortTag : deriveShortTag(displayName, id);
		this.grantedByQuestId = grantedByQuestId;
	}

	/** Falls back to first uppercase letter of the displayName (color-code stripped), in brackets. */
	private static String deriveShortTag(String displayName, String id) {
		String stripped = stripColors(displayName);
		// remove brackets and pick a letter
		for (int i = 0; i < stripped.length(); i++) {
			char c = stripped.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				return "[" + Character.toUpperCase(c) + "]";
			}
		}
		String src = id == null ? "?" : id;
		return "[" + Character.toUpperCase(src.charAt(0)) + "]";
	}

	private static String stripColors(String s) {
		if (s == null) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) { i++; continue; }
			sb.append(c);
		}
		return sb.toString();
	}
}

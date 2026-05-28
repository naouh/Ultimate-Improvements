package com.nao.questcycle.event;

import com.nao.questcycle.data.PlayerStateCache;
import com.nao.questcycle.data.QuestRegistry;
import com.nao.questcycle.data.TitleDef;
import net.minecraftforge.event.EventPriority;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.ServerChatEvent;

/**
 * Rewrites the chat line to inject the active title as a prefix.
 * Runs at LOWEST priority so other chat-formatting mods (rank prefixes, etc.)
 * are applied first and we prepend on top of their final output.
 */
public final class ChatPrefixHandler {
	@ForgeSubscribe(priority = EventPriority.LOWEST)
	public void onChat(ServerChatEvent event) {
		if (event.player == null) return;
		PlayerStateCache.Entry e = PlayerStateCache.getIfLoaded(event.player.username);
		if (e == null || e.persist.activeTitleId == null) return;
		TitleDef t = QuestRegistry.CURRENT.title(e.persist.activeTitleId);
		if (t == null) return;
		// Vanilla / other-mods `line` like "<player> msg" or "[rank|player] msg".
		// Prepend the title verbatim so it sits in front of whatever other mods wrote.
		event.line = t.displayName + " " + event.line;
	}
}

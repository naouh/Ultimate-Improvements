package com.nao.questcycle.client.hud;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/** Static FIFO of pending toast notifications. Coalesces dupes within a short window. */
public final class ToastQueue {
	public static final long DURATION_MS = 3500L;
	public static final long DEDUP_MS = 1500L;

	private static final Deque<Toast> queue = new ArrayDeque<Toast>();

	private ToastQueue() {}

	public static synchronized void push(String text, int accentArgb) {
		long now = System.currentTimeMillis();
		Iterator<Toast> it = queue.iterator();
		while (it.hasNext()) {
			Toast t = it.next();
			if (t.text.equals(text) && (now - t.bornMs) < DEDUP_MS) {
				return; // duplicate suppressed
			}
		}
		queue.addLast(new Toast(text, accentArgb, now));
		while (queue.size() > 4) queue.pollFirst();
	}

	public static synchronized Toast[] snapshot() {
		long now = System.currentTimeMillis();
		Iterator<Toast> it = queue.iterator();
		while (it.hasNext()) {
			if (now - it.next().bornMs > DURATION_MS) it.remove();
		}
		return queue.toArray(new Toast[queue.size()]);
	}

	public static final class Toast {
		public final String text;
		public final int accentArgb;
		public final long bornMs;
		Toast(String text, int accentArgb, long bornMs) {
			this.text = text;
			this.accentArgb = accentArgb;
			this.bornMs = bornMs;
		}
	}
}

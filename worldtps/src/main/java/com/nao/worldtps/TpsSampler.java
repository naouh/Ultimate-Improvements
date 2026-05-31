package com.nao.worldtps;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Measures global TPS by recording a timestamp every server tick into a ring buffer, then
 * deriving the average over arbitrary trailing windows on demand. Also exposes the mean
 * inter-tick interval, used as a fallback MSPT estimate when Forge timing is unavailable.
 *
 * Scheduled with period 1 tick from {@link WorldTpsPlugin#onEnable()}.
 */
final class TpsSampler extends BukkitRunnable {

	private static final int IDEAL_TPS = 20;
	private static final int MAX_SECONDS = 15 * 60;                  // keep 15 min of history
	private static final int CAPACITY = MAX_SECONDS * IDEAL_TPS + 8; // ring buffer size

	private final long[] tickMillis = new long[CAPACITY];
	private int head = -1;  // index of the most recent sample
	private int count = 0;  // number of valid samples so far

	@Override
	public void run() {
		head = (head + 1) % CAPACITY;
		tickMillis[head] = System.currentTimeMillis();
		if (count < CAPACITY) count++;
	}

	/** Average TPS over the trailing {@code seconds}, capped at 20. Returns -1 with too little data. */
	double tps(int seconds) {
		if (count < 2) return -1;
		long now = System.currentTimeMillis();
		long newest = tickMillis[head];
		long cutoff = now - seconds * 1000L;
		long oldest = newest;
		int ticks = 0;
		for (int i = 0; i < count; i++) {
			long t = tickMillis[(head - i + CAPACITY) % CAPACITY];
			if (t < cutoff) break;
			oldest = t;
			ticks++;
		}
		long span = newest - oldest;
		if (span <= 0 || ticks < 2) return -1;
		double tps = (ticks - 1) * 1000.0 / span;
		return Math.min(tps, IDEAL_TPS);
	}

	/** Mean milliseconds between the last {@code n} ticks (fallback MSPT). Returns -1 with too little data. */
	double meanInterTickMs(int n) {
		if (count < 2) return -1;
		int usable = Math.min(n, count - 1);
		long newest = tickMillis[head];
		long oldest = tickMillis[(head - usable + CAPACITY) % CAPACITY];
		return (newest - oldest) / (double) usable;
	}
}

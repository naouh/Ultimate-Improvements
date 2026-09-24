package com.nao.clearlag;

import org.bukkit.plugin.Plugin;

/**
 * Lightweight tick-rate (TPS) meter.
 *
 * <p>A sync task is scheduled to run every {@link #PERIOD_TICKS} ticks. If the server is healthy it
 * actually fires once per second; if it's lagging, more wall-clock time passes between firings, so
 * comparing the real elapsed time to the expected time yields the true TPS. We keep a small ring
 * buffer of recent samples for a 1-minute average. Cost: one tiny task per second.
 */
final class TpsMeter implements Runnable {

	private static final long PERIOD_TICKS = 20L;   // nominal 1 second
	private static final double EXPECTED_MS = 1000.0;
	private static final int WINDOW = 60;           // ~1 minute of 1s samples

	private final double[] samples = new double[WINDOW];
	private int count;
	private int idx;
	private long last;
	private volatile double current = 20.0;
	private int taskId = -1;

	void start(Plugin plugin) {
		last = System.currentTimeMillis();
		taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this, PERIOD_TICKS, PERIOD_TICKS);
	}

	void stop(Plugin plugin) {
		if (taskId != -1) {
			plugin.getServer().getScheduler().cancelTask(taskId);
			taskId = -1;
		}
	}

	@Override
	public void run() {
		long now = System.currentTimeMillis();
		long dt = now - last;
		last = now;
		double tps = dt > 0 ? Math.min(20.0, 20.0 * EXPECTED_MS / dt) : 20.0;
		current = tps;
		samples[idx] = tps;
		idx = (idx + 1) % WINDOW;
		if (count < WINDOW) count++;
	}

	/** Most recent ~1-second TPS. */
	double current() {
		return current;
	}

	/** Average TPS over roughly the last minute. */
	double averageOneMinute() {
		if (count == 0) return current;
		double sum = 0;
		for (int i = 0; i < count; i++) sum += samples[i];
		return sum / count;
	}
}

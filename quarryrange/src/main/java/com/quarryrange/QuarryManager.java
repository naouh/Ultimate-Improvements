package com.quarryrange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-side, in-memory tracking.
 *
 * <p>"pending" = quarries currently being configured: held idle ({@code isAlive=false}) until the
 * player confirms/cancels the editor.
 *
 * <p>"handled" = quarries whose editor has already been opened this session, so a freshly-placed
 * quarry pops the editor exactly once (not again after Apply). A position is forgotten when its
 * block disappears (see {@link ServerTick}), so breaking and replacing a quarry on the same spot
 * opens the editor again.
 */
public final class QuarryManager {

    private QuarryManager() {}

    private static final Set<String> pending = new HashSet<String>();
    private static final Set<String> handled = new HashSet<String>();

    public static String key(int dim, int x, int y, int z) {
        return dim + ":" + x + ":" + y + ":" + z;
    }

    public static int[] parseKey(String k) {
        String[] p = k.split(":");
        return new int[] { Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                           Integer.parseInt(p[2]), Integer.parseInt(p[3]) };
    }

    // ---- pending (held) quarries ----

    public static synchronized void markPending(int dim, int x, int y, int z) {
        pending.add(key(dim, x, y, z));
    }

    public static synchronized void clearPending(int dim, int x, int y, int z) {
        pending.remove(key(dim, x, y, z));
    }

    public static synchronized boolean isPending(int dim, int x, int y, int z) {
        return pending.contains(key(dim, x, y, z));
    }

    public static synchronized List<String> snapshotPending() {
        return new ArrayList<String>(pending);
    }

    // ---- handled (editor already opened) ----

    /** @return true if this position had NOT been handled yet (i.e. open the editor now). */
    public static synchronized boolean markHandledIfNew(int dim, int x, int y, int z) {
        return handled.add(key(dim, x, y, z));
    }

    public static synchronized List<String> snapshotHandled() {
        return new ArrayList<String>(handled);
    }

    /** Forget a position entirely (block gone) so a future placement is treated as fresh. */
    public static synchronized void forget(int dim, int x, int y, int z) {
        String k = key(dim, x, y, z);
        handled.remove(k);
        pending.remove(k);
    }
}

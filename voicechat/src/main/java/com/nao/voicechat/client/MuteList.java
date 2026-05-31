package com.nao.voicechat.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Client-side per-player mute list. Stored in {@code config/voicechat-mutes.txt}, one username
 * per line. Case-insensitive lookup; canonical case (as supplied) is preserved for display.
 */
public final class MuteList {

    private MuteList() {}

    private static final Set<String> muted = new HashSet<String>();   // all lowercase
    private static final Set<String> display = new TreeSet<String>(); // case-preserving
    private static File file;

    public static synchronized void load(File configDir) {
        file = new File(configDir, "voicechat-mutes.txt");
        muted.clear();
        display.clear();
        if (!file.exists()) return;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(file));
            String line;
            while ((line = r.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                muted.add(s.toLowerCase());
                display.add(s);
            }
        } catch (IOException ignored) {
        } finally {
            close(r);
        }
    }

    public static synchronized boolean isMuted(String username) {
        if (username == null) return false;
        return muted.contains(username.toLowerCase());
    }

    public static synchronized boolean toggle(String username) {
        if (username == null) return false;
        String k = username.toLowerCase();
        boolean nowMuted;
        if (muted.contains(k)) {
            muted.remove(k);
            // clear any case variant from the display set
            for (java.util.Iterator<String> it = display.iterator(); it.hasNext(); ) {
                if (it.next().equalsIgnoreCase(username)) it.remove();
            }
            nowMuted = false;
        } else {
            muted.add(k);
            display.add(username);
            nowMuted = true;
        }
        save();
        return nowMuted;
    }

    public static synchronized Set<String> snapshot() {
        return Collections.unmodifiableSet(new TreeSet<String>(display));
    }

    private static void save() {
        if (file == null) return;
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new FileWriter(file));
            w.write("# voicechat client-side mutes — one username per line\n");
            for (String s : display) {
                w.write(s);
                w.write('\n');
            }
        } catch (IOException ignored) {
        } finally {
            close(w);
        }
    }

    private static void close(java.io.Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (IOException ignored) {}
    }
}

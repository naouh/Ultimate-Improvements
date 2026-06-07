package com.nao.tfcfixes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Runtime helper for {@link com.nao.tfcfixes.asm.WindowClickSyncTransformer}.
 *
 * Called at the end of {@code NetServerHandler.handleWindowClick}, it re-sends the player's open
 * container in full ({@code EntityPlayerMP.sendContainerToPlayer}). 1.4.7 only sends slot diffs
 * after a click when the client's predicted result didn't match the server's; when it <em>did</em>
 * match, no correcting packet is sent and the client trusts its prediction — but under packet
 * jitter (worse with TickThreading) that prediction can render stale, so grabbed/crafted items
 * don't appear until the next click forces a re-sync. Re-sending the whole container as the last
 * packet of every click makes the client's view authoritative immediately. No items are ever lost
 * either way; this just fixes the display.
 *
 * The class is reached by reflection (never on the compile classpath here) and resolves its members
 * across runtime mapping flavours: this pack's server is MCPC+, where classes carry MCP names but
 * methods/fields keep SRG names ({@code func_*}/{@code field_*}); a plain obfuscated server/dev
 * uses single-letter names; a fully-deobf dev uses MCP names. We try SRG, then MCP, then obf. Every
 * access is guarded so a resolution miss or call failure can only no-op (desync stays) — it can
 * never break window-click handling.
 */
public final class WindowClickSyncHook {

    private WindowClickSyncHook() {}

    // NetServerHandler.playerEntity : EntityPlayerMP
    private static final String[] PLAYER_FIELD    = { "field_72574_e", "playerEntity", "d" };
    // EntityPlayer.openContainer : Container (declared on the EntityPlayer superclass)
    private static final String[] CONTAINER_FIELD = { "field_71070_bA", "openContainer", "bL" };
    // EntityPlayerMP.sendContainerToPlayer(Container) : void
    private static final String[] SEND_METHOD     = { "func_71120_a", "sendContainerToPlayer", "a" };

    private static Field  fPlayer;
    private static Field  fContainer;
    private static Method mSend;
    private static boolean resolved;
    private static boolean disabled;

    /**
     * @param netServerHandler the {@code this} of handleWindowClick, passed as Object so the
     *                         injected bytecode references no Minecraft type.
     */
    public static void afterWindowClick(Object netServerHandler) {
        if (disabled || netServerHandler == null) return;
        try {
            if (!resolved) resolve(netServerHandler);
            if (disabled) return;

            Object player = fPlayer.get(netServerHandler);
            if (player == null) return;
            Object container = fContainer.get(player);
            if (container == null) return;
            mSend.invoke(player, container);
        } catch (Throwable t) {
            // A resync must never break the click it follows: stop trying on any error.
            disabled = true;
            System.err.println("[TFCFixes] WindowClickSync disabled after error: " + t);
        }
    }

    private static void resolve(Object nsh) {
        resolved = true;
        try {
            fPlayer = findField(nsh.getClass(), PLAYER_FIELD);
            Object player = (fPlayer != null) ? fPlayer.get(nsh) : null;
            if (player == null) { disable("NetServerHandler.playerEntity"); return; }

            fContainer = findField(player.getClass(), CONTAINER_FIELD);
            if (fContainer == null) { disable("EntityPlayer.openContainer"); return; }
            Object container = fContainer.get(player);

            mSend = findOneArgMethod(player.getClass(), SEND_METHOD,
                    (container != null) ? container.getClass() : null);
            if (mSend == null) { disable("EntityPlayerMP.sendContainerToPlayer"); return; }

            System.out.println("[TFCFixes] WindowClickSync armed ("
                    + fPlayer.getName() + " / " + fContainer.getName() + " / " + mSend.getName() + ")");
        } catch (Throwable t) {
            disable(String.valueOf(t));
        }
    }

    private static void disable(String what) {
        disabled = true;
        System.err.println("[TFCFixes] WindowClickSync could not resolve " + what + "; disabled.");
    }

    private static Field findField(Class<?> start, String[] names) {
        for (Class<?> k = start; k != null && k != Object.class; k = k.getSuperclass()) {
            for (int i = 0; i < names.length; i++) {
                try {
                    Field f = k.getDeclaredField(names[i]);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {
                    // try the next candidate name / superclass
                }
            }
        }
        return null;
    }

    /** Finds a one-argument method by candidate name whose parameter accepts {@code argType}. */
    private static Method findOneArgMethod(Class<?> start, String[] names, Class<?> argType) {
        for (Class<?> k = start; k != null && k != Object.class; k = k.getSuperclass()) {
            for (int i = 0; i < names.length; i++) {
                Method[] declared = k.getDeclaredMethods();
                for (int j = 0; j < declared.length; j++) {
                    Method m = declared[j];
                    if (!m.getName().equals(names[i])) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length != 1) continue;
                    if (argType != null && !p[0].isAssignableFrom(argType)) continue;
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }
}

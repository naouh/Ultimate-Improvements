package com.nao.mousetweaksng;

/**
 * Server (and shared) proxy. The mod does nothing server-side — all behaviour is client GUI
 * handling — so this stays empty. Keeping the client logic in {@code client.ClientProxy} means a
 * dedicated server never classloads the client-only classes.
 */
public class CommonProxy {
    public void init() {}
}

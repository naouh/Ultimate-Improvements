package com.quarryplus;

/**
 * Server-side proxy. Renderer registration is a no-op here; the client overrides
 * {@link #registerRenderers()} in {@link ProxyClient}.
 */
public class ProxyCommon {

    public void registerRenderers() {
        // server-side: nothing to bind
    }
}

package com.nao.discordrpc;

/** Server-side proxy: no-op. The client proxy overrides {@link #init()} to start the RPC loop. */
public class CommonProxy {
	public void init() {}
}

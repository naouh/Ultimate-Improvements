package com.nao.voicechat.server;

import java.net.InetSocketAddress;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Per-player voicechat session. Created on login (handshake), destroyed on logout.
 * Mutable fields (udpAddress, lastSeenMs) are written by the UDP listener thread and read by
 * the broadcast path; we synchronise on the {@link #lock} for those.
 */
public final class VoiceSession {

    public final long   tokenHi;
    public final long   tokenLo;
    public final int    entityId;
    public final String username;

    public final Object lock = new Object();

    /** Set the first time we receive a UDP packet from this client. {@code null} until then. */
    public volatile InetSocketAddress udpAddress;

    public volatile long lastUdpRecvMs;

    /** Server-side resend sequence counter (independent from the client's). */
    public volatile int outSeq;

    /** Pinned at login. The entity reference can stale-bind after dimension changes; look up
     *  the live entity by id at dispatch time. */
    public VoiceSession(long tokenHi, long tokenLo, EntityPlayerMP epm) {
        this.tokenHi  = tokenHi;
        this.tokenLo  = tokenLo;
        this.entityId = epm.entityId;
        this.username = epm.username;
    }
}

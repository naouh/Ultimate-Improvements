package com.nao.tfcfixes;

/**
 * Single process-wide monitor used to serialize Applied Energistics (rv9) network operations
 * that were written for single-threaded ticking but are run in parallel by TickThreading.
 *
 * <p>AE's {@code TileController} guards its tick and item-injection paths with {@code synchronized}
 * on {@code this} (a per-controller monitor). When one logical AE network — or two subnets bridged
 * by a storage bus / interface — spans tiles that fall into different TickThreading regions, two
 * worker threads tick two controllers concurrently. Because a controller's tick traverses the whole
 * network and reaches into the <em>other</em> controller's synchronized method, the two per-instance
 * monitors get acquired in opposite orders, producing a textbook AB-BA deadlock (and, short of a full
 * deadlock, corrupted network state: stalled autocrafting, rejected items, "Internal server error"
 * kicks).
 *
 * <p>{@link com.nao.tfcfixes.asm.AppEngLockTransformer} rewrites those controller methods (and the
 * level-emitter tick) so they lock <em>this</em> shared object instead of their own instance. With a
 * single lock for all controllers, opposite-order acquisition is impossible by construction, and the
 * cross-controller traversal simply re-enters the (reentrant) monitor on the same thread. The cost is
 * that AE controller ticks run one-at-a-time server-wide; AE ticks are short and controllers are few,
 * so the throughput hit is negligible compared to the freezes it removes.
 */
public final class AppEngLock {

    /** The shared monitor. Never reassigned, so re-loading it for {@code monitorexit} is always safe. */
    public static final Object LOCK = new Object();

    private AppEngLock() {}
}

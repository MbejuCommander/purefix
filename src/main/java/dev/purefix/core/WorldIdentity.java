package dev.purefix.core;

/** Client-thread state. A confirmed ID is invalidated on every login/respawn, even with the same seed. */
public final class WorldIdentity {
    public static final int DISCOVERY_TICKS = 60;
    private static final int SETTLE_TICKS = 4;
    private String seed = "";
    private Integer activeId, stagedId;
    private long stagedAt = -100, settleAt, fallbackAt;
    private boolean receivedThisConnection, sawTraffic, expected;

    public void reset() {
        seed = ""; activeId = stagedId = null; stagedAt = -100; settleAt = fallbackAt = 0;
        receivedThisConnection = sawTraffic = expected = false;
    }
    public void changeWorld(long obfuscatedSeed, long tick, boolean networkKnown) {
        seed = obfuscatedSeed == 0 ? "unknown" : "seed:" + Long.toUnsignedString(obfuscatedSeed, 16);
        activeId = null;
        // MapModCompanion's respawn listener can send its announcement immediately BEFORE respawn.
        // Only an uncommitted announcement from that short transition window can cross the boundary.
        if (stagedId == null || tick - stagedAt > SETTLE_TICKS) stagedId = null;
        settleAt = tick + SETTLE_TICKS;
        fallbackAt = tick + DISCOVERY_TICKS;
        expected = networkKnown || receivedThisConnection;
    }
    public void receive(Integer id, long tick) {
        sawTraffic = true;
        if (id == null) return;
        receivedThisConnection = expected = true;
        if (activeId != null && activeId.equals(id) && stagedId == null) {
            // Keep a short-lived duplicate candidate for plugins that announce the same ID
            // immediately before a death/respawn packet, without interrupting a stable world.
            stagedId = id; stagedAt = tick; settleAt = tick + SETTLE_TICKS; return;
        }
        if (stagedId != null && stagedId.equals(id)) return; // duplicate delivery must not postpone readiness
        activeId = null; stagedId = id; stagedAt = tick; settleAt = tick + SETTLE_TICKS;
    }
    public void settle(long tick) {
        if (stagedId != null && tick >= settleAt) { activeId = stagedId; stagedId = null; }
    }
    public String fingerprint(long tick) {
        settle(tick);
        if (activeId != null) return XaeroProtocol.identity(activeId);
        if (expected || stagedId != null || tick < fallbackAt) return "";
        return seed;
    }
    public String fallback() { return seed; }
    public boolean receivedThisConnection() { return receivedThisConnection; }
    public boolean sawTraffic() { return sawTraffic; }
    public Integer currentId(long tick) { settle(tick); return activeId; }
}

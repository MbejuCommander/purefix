package dev.purefix.core;

/** Tracks explicit server mode changes only; rendering, F1, and initial login are irrelevant. */
public final class SpectateSession {
    private String pendingId = "";
    private boolean spectatorSeen;
    public String pendingId() { return pendingId; }
    public boolean armed() { return !pendingId.isEmpty(); }
    public void arm(String id, boolean spectator) { pendingId = id; spectatorSeen = spectator; }
    public void reset() { pendingId = ""; spectatorSeen = false; }
    public boolean modeChanged(boolean spectator) {
        if (!armed()) return false;
        if (spectator) { spectatorSeen = true; return false; }
        return spectatorSeen;
    }
}

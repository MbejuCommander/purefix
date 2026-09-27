package dev.purefix.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldIdentityTest {
    @Test void newNetworkWaitsBeforeUsingSeedFallback() {
        WorldIdentity state = new WorldIdentity(); state.changeWorld(13, 0, false);
        assertEquals("", state.fingerprint(59)); assertEquals("seed:d", state.fingerprint(60));
        state.receive(1337, 65);
        assertEquals("", state.fingerprint(68)); assertEquals("xaero-worldmap:1337", state.fingerprint(69));
    }
    @Test void sameSeedTransitionCannotReusePreviousXaeroId() {
        WorldIdentity state = new WorldIdentity(); state.changeWorld(13, 0, false); state.receive(1, 1);
        assertEquals("xaero-worldmap:1", state.fingerprint(5));
        state.changeWorld(13, 100, true);
        assertEquals("", state.fingerprint(500));
        state.receive(2, 501); assertEquals("xaero-worldmap:2", state.fingerprint(505));
    }
    @Test void announcementImmediatelyBeforeRespawnIsPreserved() {
        WorldIdentity state = new WorldIdentity(); state.changeWorld(13, 0, false);
        state.receive(7, 1); state.changeWorld(13, 2, true);
        assertEquals("", state.fingerprint(5)); assertEquals("xaero-worldmap:7", state.fingerprint(6));
    }
    @Test void duplicateBeforeSameWorldRespawnIsPreservedWithoutExtendingStableReadiness() {
        WorldIdentity state = new WorldIdentity(); state.changeWorld(13, 0, false); state.receive(7, 1);
        assertEquals("xaero-worldmap:7", state.fingerprint(5));
        state.receive(7, 50); assertEquals("xaero-worldmap:7", state.fingerprint(50));
        state.changeWorld(13, 51, true); assertEquals("xaero-worldmap:7", state.fingerprint(55));
    }
    @Test void oldUncommittedAnnouncementCannotCrossLateTransition() {
        WorldIdentity state = new WorldIdentity(); state.receive(7, 1); state.changeWorld(13, 50, true);
        assertEquals("", state.fingerprint(100));
    }
    @Test void duplicateTrafficDoesNotDelayAndBadTrafficDoesNotOverwriteId() {
        WorldIdentity state = new WorldIdentity(); state.changeWorld(13, 0, false); state.receive(-9, 1); state.receive(-9, 4);
        assertEquals("xaero-worldmap:-9", state.fingerprint(5));
        state.receive(null, 6); assertEquals("xaero-worldmap:-9", state.fingerprint(6));
    }
    @Test void knownNetworkCannotSilentlyDowngradeAfterRestart() {
        WorldIdentity state = new WorldIdentity(); state.changeWorld(13, 0, true);
        assertEquals("", state.fingerprint(5000)); assertFalse(state.receivedThisConnection());
        state.receive(null, 5001); assertTrue(state.sawTraffic()); assertFalse(state.receivedThisConnection());
    }
    @Test void disconnectRemovesAllPreviousNetworkEvidence() {
        WorldIdentity state = new WorldIdentity(); state.receive(7, 1); state.fingerprint(5); state.reset();
        state.changeWorld(13, 10, false); assertEquals("seed:d", state.fingerprint(70));
        assertFalse(state.receivedThisConnection()); assertFalse(state.sawTraffic());
    }
}

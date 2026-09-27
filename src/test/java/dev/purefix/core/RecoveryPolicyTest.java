package dev.purefix.core;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryPolicyTest {
    private final Data.Server server = new Data.Server();
    private final Data.Pending pending = new Data.Pending();
    RecoveryPolicyTest() { pending.serverId = server.id; pending.account = "staff"; pending.dimension = "minecraft:the_nether"; pending.x = 0; pending.y = 90; pending.z = 0; }
    @Test void onlyEligibleReadyMatchingSessionCanRun() {
        assertTrue(can("staff", "seed:abc", true, 10, Set.of(pending.id), Set.of()));
        assertFalse(can("other", "seed:abc", true, 10, Set.of(pending.id), Set.of()));
        assertFalse(can("staff", "seed:abc", false, 10, Set.of(pending.id), Set.of()));
        assertFalse(can("staff", "seed:abc", true, 9, Set.of(pending.id), Set.of()));
        assertFalse(can("staff", "seed:abc", true, 10, Set.of(), Set.of()));
        assertFalse(can("staff", "seed:abc", true, 10, Set.of(pending.id), Set.of(pending.id)));
        assertFalse(can("staff", "unknown", true, 10, Set.of(pending.id), Set.of()));
        server.automaticRecovery = false;
        assertFalse(can("staff", "seed:abc", true, 10, Set.of(pending.id), Set.of()));
    }
    @Test void anotherSubserverCannotConsumeReturn() {
        assertFalse(RecoveryPolicy.canAttempt(pending, new Data.Server(), "staff", "seed:abc", false, true, 10, 10, Set.of(pending.id), Set.of()));
        assertFalse(RecoveryPolicy.canAttempt(pending, null, "staff", "seed:abc", false, true, 10, 10, Set.of(pending.id), Set.of()));
    }
    @Test void xaeroSavedReturnRequiresExactLiveWorldIdEvenWithManualProfileSelection() {
        pending.worldFingerprint = "xaero-worldmap:100";
        assertTrue(can("staff", "xaero-worldmap:100", true, 10, Set.of(pending.id), Set.of()));
        assertFalse(can("staff", "xaero-worldmap:101", true, 10, Set.of(pending.id), Set.of()));
        assertFalse(RecoveryPolicy.canAttempt(pending, server, "staff", "seed:abc", true, true, 10, 10, Set.of(pending.id), Set.of()));
    }
    @Test void unknownFingerprintRequiresExplicitSelection() {
        assertTrue(RecoveryPolicy.canAttempt(pending, server, "staff", "unknown", true, true, 10, 10, Set.of(pending.id), Set.of()));
    }
    @Test void arrivalNeedsNewPacketCorrectWorldAccountServerAndPosition() {
        assertTrue(arrived("staff", server.id, pending.dimension, true, 0, 90, 0));
        assertFalse(arrived("staff", server.id, pending.dimension, false, 0, 90, 0));
        assertFalse(arrived("other", server.id, pending.dimension, true, 0, 90, 0));
        assertFalse(arrived("staff", "other-server", pending.dimension, true, 0, 90, 0));
        assertFalse(arrived("staff", server.id, "minecraft:overworld", true, 0, 90, 0));
        assertFalse(arrived("staff", server.id, pending.dimension, true, 100, 90, 0));
    }
    private boolean can(String account, String fp, boolean ready, int ticks, Set<String> eligible, Set<String> attempted) {
        return RecoveryPolicy.canAttempt(pending, server, account, fp, false, ready, ticks, 10, eligible, attempted);
    }
    private boolean arrived(String account, String serverId, String dimension, boolean packet, double x, double y, double z) {
        return RecoveryPolicy.arrived(pending, account, serverId, dimension, packet, x, y, z);
    }
}

package dev.purefix.core;

import java.util.Set;

/** Fail closed when the account, server identity, readiness, or retry state does not match. */
public final class RecoveryPolicy {
    private RecoveryPolicy() {}
    public static boolean canAttempt(Data.Pending p, Data.Server server, String account, String fingerprint,
                                     boolean manuallySelected, boolean positionReady, int readyTicks, int delay,
                                     Set<String> eligible, Set<String> attempted) {
        return canAttempt(p, server, account, fingerprint, manuallySelected, positionReady, readyTicks, delay, eligible, attempted, true);
    }
    public static boolean canAttempt(Data.Pending p, Data.Server server, String account, String fingerprint,
                                     boolean manuallySelected, boolean positionReady, int readyTicks, int delay,
                                     Set<String> eligible, Set<String> attempted, boolean useMapIdentity) {
        return server != null && server.automaticRecovery && !fingerprint.isBlank()
            && (!fingerprint.equals("unknown") || manuallySelected) && positionReady && readyTicks >= delay
            && identityMatches(p, fingerprint, useMapIdentity)
            && p.account.equals(account) && p.serverId.equals(server.id) && eligible.contains(p.id) && !attempted.contains(p.id);
    }
    public static boolean identityMatches(Data.Pending p, String fingerprint) {
        return identityMatches(p, fingerprint, true);
    }
    public static boolean identityMatches(Data.Pending p, String fingerprint, boolean useMapIdentity) {
        return !useMapIdentity || !XaeroProtocol.isIdentity(p.worldFingerprint) || p.worldFingerprint.equals(fingerprint);
    }
    public static boolean arrived(Data.Pending p, String account, String serverId, String dimension,
                                  boolean newServerPosition, double x, double y, double z) {
        return newServerPosition && p.account.equals(account) && p.serverId.equals(serverId) && p.dimension.equals(dimension)
            && Math.pow(p.x - x, 2) + Math.pow(p.y - y, 2) + Math.pow(p.z - z, 2) < 4;
    }
}

package dev.purefix.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpectateSessionTest {
    @Test void survivalDoesNotClearAnEntryBeforeSpectatorWasObserved() {
        SpectateSession session = new SpectateSession(); session.arm("origin", false);
        assertFalse(session.modeChanged(false));
        assertEquals("origin", session.pendingId());
    }
    @Test void spectatorToSurvivalEndsTheSameOriginWithoutASecondCommand() {
        SpectateSession session = new SpectateSession(); session.arm("origin", false);
        assertFalse(session.modeChanged(true)); assertFalse(session.modeChanged(true));
        assertTrue(session.modeChanged(false)); assertEquals("origin", session.pendingId());
    }
    @Test void disconnectResetCannotTreatNewLoginSurvivalAsAnExit() {
        SpectateSession session = new SpectateSession(); session.arm("origin", true); session.reset();
        assertFalse(session.modeChanged(false)); assertFalse(session.armed());
    }
    @Test void untrackedModeChangesNeverClearAnything() {
        SpectateSession session = new SpectateSession();
        assertFalse(session.modeChanged(true)); assertFalse(session.modeChanged(false));
    }
}

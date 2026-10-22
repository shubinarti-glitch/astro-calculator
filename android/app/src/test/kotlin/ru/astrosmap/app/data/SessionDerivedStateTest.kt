package ru.astrosmap.app.data

import org.junit.Assert.*
import org.junit.Test

class SessionDerivedStateTest {
    @Test fun premiumNeedsSameVerifiedOwnerAndPositiveAccess() {
        assertTrue(SessionDerivedState.premiumAllowed("user:1", "user:1", true))
        assertFalse(SessionDerivedState.premiumAllowed("user:2", "user:1", true))
        assertFalse(SessionDerivedState.premiumAllowed("user:1", "user:1", false))
    }

    @Test fun guestUnresolvedAndLegacyAccessCannotPersonalize() {
        listOf(null, "guest", "", "user:0", "user:-1").forEach { owner ->
            assertFalse(SessionDerivedState.premiumAllowed(owner, owner, true))
        }
        assertFalse(SessionDerivedState.premiumAllowed("user:1", null, true))
    }

    @Test fun transitionClearsNoticesWithoutResettingUnchangedAccount() {
        assertTrue(SessionDerivedState.clearNotices("user:1", "user:2"))
        assertTrue(SessionDerivedState.clearNotices("user:1", "guest"))
        assertTrue(SessionDerivedState.clearNotices("user:1", null))
        assertTrue(SessionDerivedState.clearNotices(null, null))
        assertFalse(SessionDerivedState.clearNotices("user:1", "user:1"))
    }
}

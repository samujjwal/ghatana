package com.ghatana.phr.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies lockout time calculations on PHRUser
 * @doc.layer product
 * @doc.pattern Test
 */
class PHRUserTest {

    @Test
    void reportsLockedWhenLockoutIsInFuture() {
        PHRUser user = new PHRUser();
        user.setLockoutUntil(Instant.now().plusSeconds(60));

        assertTrue(user.isLockedAt(Instant.now()));
    }

    @Test
    void reportsUnlockedWhenLockoutMissingOrExpired() {
        PHRUser user = new PHRUser();
        assertFalse(user.isLockedAt(Instant.now()));

        user.setLockoutUntil(Instant.now().minusSeconds(60));
        assertFalse(user.isLockedAt(Instant.now()));
    }
}

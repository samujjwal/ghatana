/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BruteForceGuard}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for brute-force detection and account lockout (K01-005)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BruteForceGuard — Unit Tests")
class BruteForceGuardTest extends EventloopTestBase {

    @Mock
    private JedisPool jedisPool;

    @Mock
    private Jedis jedis;

    private BruteForceGuard guard;

    @BeforeEach
    void setUp() {
        lenient().when(jedisPool.getResource()).thenReturn(jedis);
        guard = new BruteForceGuard(jedisPool, Executors.newSingleThreadExecutor());
    }

    @Test
    @DisplayName("first failure — no threshold reached → NONE decision")
    void firstFailure_belowThreshold_returnsNone() {
        when(jedis.incr("rl:fail:ip:192.168.1.1")).thenReturn(1L);
        when(jedis.expire("rl:fail:ip:192.168.1.1", 60L)).thenReturn(1L);
        when(jedis.incr("rl:fail:acct:user-1")).thenReturn(1L);
        when(jedis.expire("rl:fail:acct:user-1", 900L)).thenReturn(1L);

        BruteForceGuard.BruteForceDecision decision =
                runPromise(() -> guard.recordFailure("192.168.1.1", "user-1"));

        assertThat(decision.reason()).isEqualTo(BruteForceGuard.BruteForceDecision.Reason.NONE);
        assertThat(decision.shouldBlock()).isFalse();
    }

    @Test
    @DisplayName("IP reaches limit of 10 — returns IP_RATE_LIMITED decision")
    void ipReachesLimit_returnsIpRateLimited() {
        when(jedis.incr("rl:fail:ip:10.0.0.1")).thenReturn(10L); // exactly at limit
        when(jedis.incr("rl:fail:acct:user-2")).thenReturn(2L);

        BruteForceGuard.BruteForceDecision decision =
                runPromise(() -> guard.recordFailure("10.0.0.1", "user-2"));

        assertThat(decision.reason()).isEqualTo(BruteForceGuard.BruteForceDecision.Reason.IP_RATE_LIMITED);
        assertThat(decision.shouldBlock()).isTrue();
    }

    @Test
    @DisplayName("account reaches 5 failures — returns ACCOUNT_LOCKED and sets lock sentinel")
    void accountReachesLimit_returnsAccountLocked() {
        when(jedis.incr("rl:fail:ip:10.0.0.2")).thenReturn(2L);
        when(jedis.incr("rl:fail:acct:user-3")).thenReturn(5L); // exactly at account limit
        when(jedis.setex(eq("rl:lock:user-3"), eq(900L), eq("locked"))).thenReturn("OK");

        BruteForceGuard.BruteForceDecision decision =
                runPromise(() -> guard.recordFailure("10.0.0.2", "user-3"));

        assertThat(decision.reason()).isEqualTo(BruteForceGuard.BruteForceDecision.Reason.ACCOUNT_LOCKED);
        assertThat(decision.shouldBlock()).isTrue();
        verify(jedis).setex("rl:lock:user-3", 900L, "locked");
    }

    @Test
    @DisplayName("recordFailure with null userId — only IP checked, no account operations")
    void nullUserId_onlyIpChecked() {
        when(jedis.incr("rl:fail:ip:10.0.0.3")).thenReturn(3L);

        BruteForceGuard.BruteForceDecision decision =
                runPromise(() -> guard.recordFailure("10.0.0.3", null));

        assertThat(decision.reason()).isEqualTo(BruteForceGuard.BruteForceDecision.Reason.NONE);
        verify(jedis, never()).incr(startsWith("rl:fail:acct:"));
    }

    @Test
    @DisplayName("isLocked — returns true when lock sentinel exists in Redis")
    void isLocked_lockedUser_returnsTrue() {
        when(jedis.exists("rl:lock:user-4")).thenReturn(true);

        boolean locked = runPromise(() -> guard.isLocked("user-4"));

        assertThat(locked).isTrue();
    }

    @Test
    @DisplayName("isLocked — returns false when no lock sentinel")
    void isLocked_unlockedUser_returnsFalse() {
        when(jedis.exists("rl:lock:user-5")).thenReturn(false);

        boolean locked = runPromise(() -> guard.isLocked("user-5"));

        assertThat(locked).isFalse();
    }

    @Test
    @DisplayName("BruteForceDecision.shouldBlock — NONE returns false, others return true")
    void bruteForceDecision_shouldBlock_enumMapping() {
        assertThat(new BruteForceGuard.BruteForceDecision(BruteForceGuard.BruteForceDecision.Reason.NONE).shouldBlock()).isFalse();
        assertThat(new BruteForceGuard.BruteForceDecision(BruteForceGuard.BruteForceDecision.Reason.IP_RATE_LIMITED).shouldBlock()).isTrue();
        assertThat(new BruteForceGuard.BruteForceDecision(BruteForceGuard.BruteForceDecision.Reason.ACCOUNT_LOCKED).shouldBlock()).isTrue();
    }

    @Test
    @DisplayName("account locked takes priority over IP rate limit in decision")
    void accountLockedPriority_overIpRateLimit() {
        // Both conditions met: IP count >= 10, account count >= 5
        when(jedis.incr("rl:fail:ip:10.0.0.9")).thenReturn(11L);
        when(jedis.incr("rl:fail:acct:user-9")).thenReturn(6L);
        when(jedis.setex(anyString(), anyLong(), anyString())).thenReturn("OK");

        BruteForceGuard.BruteForceDecision decision =
                runPromise(() -> guard.recordFailure("10.0.0.9", "user-9"));

        // ACCOUNT_LOCKED takes precedence per implementation
        assertThat(decision.reason()).isEqualTo(BruteForceGuard.BruteForceDecision.Reason.ACCOUNT_LOCKED);
    }
}

/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.security;

import com.ghatana.appplatform.iam.audit.IamAuditEmitter;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BreakGlassService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for emergency super-admin elevation (K01-012)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BreakGlassService — Unit Tests")
class BreakGlassServiceTest extends EventloopTestBase {

    @Mock private JedisPool jedisPool;
    @Mock private Jedis jedis;
    @Mock private IamAuditEmitter audit;

    private BreakGlassService service;

    @BeforeEach
    void setUp() {
        when(jedisPool.getResource()).thenReturn(jedis);
        when(audit.onBreakGlassActivated(any(), any(), any(), any(), any()))
                .thenReturn(Promise.of(null));
        when(audit.onBreakGlassRevoked(any(), any(), any()))
                .thenReturn(Promise.of(null));
        service = new BreakGlassService(jedisPool, Executors.newSingleThreadExecutor(), audit);
    }

    // ── activate ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("activate — MFA verified, non-blank reason → returns session ID")
    void activate_validRequest_returnsSessionId() {
        when(jedis.setex(eq("breakglass:admin-1"), eq(BreakGlassService.MAX_ELEVATION_SECONDS), anyString()))
                .thenReturn("OK");

        String sessionId = runPromise(() ->
                service.activate("admin-1", "tenant-X", "Critical incident INC-1234",
                        "super-admin-2", true));

        assertThat(sessionId).isNotBlank();
        verify(jedis).setex(eq("breakglass:admin-1"), eq(BreakGlassService.MAX_ELEVATION_SECONDS), anyString());
    }

    @Test
    @DisplayName("activate — MFA not verified → rejects with IllegalArgumentException")
    void activate_mfaNotVerified_rejects() {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() ->
                runPromise(() -> service.activate("admin-1", "tenant-X", "Incident",
                        "super-admin-2", false)));

        assertThat(thrown).hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(thrown.getCause().getMessage()).contains("MFA");
    }

    @Test
    @DisplayName("activate — blank reason → rejects with IllegalArgumentException")
    void activate_blankReason_rejects() {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() ->
                runPromise(() -> service.activate("admin-1", "tenant-X", "   ",
                        "super-admin-2", true)));

        assertThat(thrown).hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(thrown.getCause().getMessage()).contains("reason");
    }

    @Test
    @DisplayName("activate — null reason → rejects")
    void activate_nullReason_rejects() {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() ->
                runPromise(() -> service.activate("admin-1", "tenant-X", null,
                        "super-admin-2", true)));

        assertThat(thrown).hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("activate — audit event emitted on success")
    void activate_emitsAuditEvent() {
        when(jedis.setex(anyString(), anyLong(), anyString())).thenReturn("OK");

        runPromise(() -> service.activate("admin-1", "tenant-X", "Urgent fix",
                "super-admin-2", true));

        verify(audit).onBreakGlassActivated(eq("super-admin-2"), eq("admin-1"),
                eq("tenant-X"), eq("Urgent fix"), anyString());
    }

    // ── status ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("status — active elevation returns ElevationStatus with active=true")
    void status_activeElevation_returnsActive() {
        String value = "sess-uuid-1|1700000000|Critical incident|super-admin-2";
        when(jedis.get("breakglass:admin-1")).thenReturn(value);
        when(jedis.ttl("breakglass:admin-1")).thenReturn(12000L);

        BreakGlassService.ElevationStatus status =
                runPromise(() -> service.status("admin-1"));

        assertThat(status.active()).isTrue();
        assertThat(status.sessionId()).isEqualTo("sess-uuid-1");
        assertThat(status.reason()).isEqualTo("Critical incident");
        assertThat(status.elevatedBy()).isEqualTo("super-admin-2");
        assertThat(status.remainingTtl()).isEqualTo(Duration.ofSeconds(12000));
    }

    @Test
    @DisplayName("status — no active elevation returns ElevationStatus with active=false")
    void status_noElevation_returnsInactive() {
        when(jedis.get("breakglass:admin-2")).thenReturn(null);

        BreakGlassService.ElevationStatus status =
                runPromise(() -> service.status("admin-2"));

        assertThat(status.active()).isFalse();
        assertThat(status.sessionId()).isNull();
        assertThat(status.remainingTtl()).isEqualTo(Duration.ZERO);
    }

    // ── revoke ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("revoke — deletes the break-glass key from Redis")
    void revoke_deletesRedisKey() {
        when(jedis.del("breakglass:admin-1")).thenReturn(1L);

        runPromise(() -> service.revoke("admin-1", "tenant-X", "security-team"));

        verify(jedis).del("breakglass:admin-1");
    }

    @Test
    @DisplayName("revoke — emits audit event after deletion")
    void revoke_emitsAuditEvent() {
        when(jedis.del(anyString())).thenReturn(1L);

        runPromise(() -> service.revoke("admin-1", "tenant-X", "security-team"));

        verify(audit).onBreakGlassRevoked("security-team", "admin-1", "tenant-X");
    }

    // ── constants ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MAX_ELEVATION_SECONDS — equals 4 hours")
    void maxElevationSeconds_isFourHours() {
        assertThat(BreakGlassService.MAX_ELEVATION_SECONDS).isEqualTo(4L * 3600L);
    }
}

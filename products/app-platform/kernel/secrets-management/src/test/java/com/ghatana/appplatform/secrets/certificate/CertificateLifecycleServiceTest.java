/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.certificate;

import com.ghatana.platform.audit.AuditBusPort;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CertificateLifecycleService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for TLS certificate lifecycle management (K14-007)
 * @doc.layer kernel
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CertificateLifecycleService — Unit Tests")
class CertificateLifecycleServiceTest extends EventloopTestBase {

    @Mock private AuditBusPort audit;
    @Mock private CertificateLifecycleService.CertificateRenewalPort renewalPort;
    @Mock private X509Certificate certAttemptToExpireIn90Days;
    @Mock private X509Certificate certExpiresIn2Days;
    @Mock private X509Certificate renewedCert;

    private CertificateLifecycleService service;

    @BeforeEach
    void setUp() {
        doNothing().when(audit).emit(any());
        service = new CertificateLifecycleService(
                CertificateLifecycleService.DEFAULT_RENEWAL_THRESHOLD,
                Executors.newSingleThreadExecutor(),
                audit,
                renewalPort
        );
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register — stores certificate, daysUntilExpiry returns correct value")
    void register_storesCertificate() {
        when(certAttemptToExpireIn90Days.getNotAfter())
                .thenReturn(Date.from(Instant.now().plus(Duration.ofDays(90))));

        service.register("api-gateway-tls", certAttemptToExpireIn90Days);

        long days = service.daysUntilExpiry("api-gateway-tls");
        assertThat(days).isGreaterThan(85L);
        assertThat(days).isLessThanOrEqualTo(90L);
    }

    @Test
    @DisplayName("daysUntilExpiry — returns -1 for unknown certificate name")
    void daysUntilExpiry_unknownName_returnsMinusOne() {
        assertThat(service.daysUntilExpiry("nonexistent-cert")).isEqualTo(-1L);
    }

    @Test
    @DisplayName("daysUntilExpiry — nearly expired certificate shows low count")
    void daysUntilExpiry_nearlyExpired() {
        when(certExpiresIn2Days.getNotAfter())
                .thenReturn(Date.from(Instant.now().plus(Duration.ofDays(2))));

        service.register("expiring-cert", certExpiresIn2Days);

        assertThat(service.daysUntilExpiry("expiring-cert")).isLessThanOrEqualTo(2L);
    }

    @Test
    @DisplayName("renew — calls renewal port with existing cert and updates stored cert")
    void renew_callsPortAndUpdates() throws Exception {
        when(certAttemptToExpireIn90Days.getNotAfter())
                .thenReturn(Date.from(Instant.now().plus(Duration.ofDays(5))));
        when(renewedCert.getNotAfter())
                .thenReturn(Date.from(Instant.now().plus(Duration.ofDays(365))));

        service.register("api-gw-cert", certAttemptToExpireIn90Days);
        when(renewalPort.issue(eq("api-gw-cert"), eq(certAttemptToExpireIn90Days)))
                .thenReturn(renewedCert);

        runPromise(() -> service.renew("api-gw-cert"));

        assertThat(service.daysUntilExpiry("api-gw-cert")).isGreaterThan(360L);
        verify(renewalPort).issue("api-gw-cert", certAttemptToExpireIn90Days);
        verify(audit).emit(argThat(evt -> "CERTIFICATE_RENEWED".equals(evt.getEventType())));
    }

    @Test
    @DisplayName("renew — unknown name throws IllegalArgumentException")
    void renew_unknownName_throws() {
        assertThatThrownBy(() -> runPromise(() -> service.renew("ghost-cert")))
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghost-cert");
    }

    @Test
    @DisplayName("register null name — throws NullPointerException")
    void register_nullName_throws() {
        assertThatThrownBy(() -> service.register(null, certAttemptToExpireIn90Days))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("register null certificate — throws NullPointerException")
    void register_nullCert_throws() {
        assertThatThrownBy(() -> service.register("api-cert", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("DEFAULT_RENEWAL_THRESHOLD — is 30 days")
    void defaultRenewalThreshold_is30Days() {
        assertThat(CertificateLifecycleService.DEFAULT_RENEWAL_THRESHOLD)
                .isEqualTo(Duration.ofDays(30));
    }
}

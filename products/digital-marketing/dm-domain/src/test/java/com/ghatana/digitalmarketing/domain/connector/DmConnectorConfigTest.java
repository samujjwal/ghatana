package com.ghatana.digitalmarketing.domain.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link DmConnectorConfig}.
 *
 * @doc.type class
 * @doc.purpose Verifies connector entity lifecycle and builder validation (DMOS-F2-006)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmConnectorConfig Tests")
class DmConnectorConfigTest {

    private DmConnectorConfig sample(DmConnectorStatus status) {
        return DmConnectorConfig.builder()
            .id("conn-1")
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .name("Test Connector")
            .connectorType(DmConnectorType.GOOGLE_ADS)
            .status(status)
            .settings(Map.of("clientId", "abc"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName("activate transitions PENDING to ACTIVE")
    void activatePending() {
        DmConnectorConfig c = sample(DmConnectorStatus.PENDING).activate();
        assertThat(c.getStatus()).isEqualTo(DmConnectorStatus.ACTIVE);
        assertThat(c.isOperational()).isTrue();
    }

    @Test
    @DisplayName("activate rejects DISABLED connector")
    void activateDisabled() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> sample(DmConnectorStatus.DISABLED).activate());
    }

    @Test
    @DisplayName("suspend transitions ACTIVE to SUSPENDED")
    void suspend() {
        DmConnectorConfig c = sample(DmConnectorStatus.ACTIVE).suspend();
        assertThat(c.getStatus()).isEqualTo(DmConnectorStatus.SUSPENDED);
        assertThat(c.isOperational()).isFalse();
    }

    @Test
    @DisplayName("suspend rejects non-ACTIVE connector")
    void suspendNonActive() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> sample(DmConnectorStatus.PENDING).suspend());
    }

    @Test
    @DisplayName("reactivate transitions SUSPENDED to ACTIVE")
    void reactivate() {
        DmConnectorConfig c = sample(DmConnectorStatus.SUSPENDED).reactivate();
        assertThat(c.getStatus()).isEqualTo(DmConnectorStatus.ACTIVE);
    }

    @Test
    @DisplayName("reactivate rejects non-SUSPENDED connector")
    void reactivateNonSuspended() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> sample(DmConnectorStatus.ACTIVE).reactivate());
    }

    @Test
    @DisplayName("markAuthFailed sets AUTH_FAILED with reason")
    void markAuthFailed() {
        DmConnectorConfig c = sample(DmConnectorStatus.ACTIVE).markAuthFailed("token expired");
        assertThat(c.getStatus()).isEqualTo(DmConnectorStatus.AUTH_FAILED);
        assertThat(c.getFailureReason()).isEqualTo("token expired");
    }

    @Test
    @DisplayName("markAuthFailed rejects null reason")
    void markAuthFailedNullReason() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> sample(DmConnectorStatus.ACTIVE).markAuthFailed(null));
    }

    @Test
    @DisplayName("markPending records readiness reason and health-check time")
    void markPending() {
        DmConnectorConfig c = sample(DmConnectorStatus.ACTIVE).markPending("rate limited");
        assertThat(c.getStatus()).isEqualTo(DmConnectorStatus.PENDING);
        assertThat(c.getFailureReason()).isEqualTo("rate limited");
        assertThat(c.getLastHealthCheckAt()).isNotNull();
    }

    @Test
    @DisplayName("disable transitions any status to DISABLED")
    void disable() {
        DmConnectorConfig c = sample(DmConnectorStatus.ACTIVE).disable();
        assertThat(c.getStatus()).isEqualTo(DmConnectorStatus.DISABLED);
    }

    @Test
    @DisplayName("isOperational returns true only for ACTIVE")
    void isOperational() {
        assertThat(sample(DmConnectorStatus.ACTIVE).isOperational()).isTrue();
        assertThat(sample(DmConnectorStatus.PENDING).isOperational()).isFalse();
        assertThat(sample(DmConnectorStatus.SUSPENDED).isOperational()).isFalse();
        assertThat(sample(DmConnectorStatus.DISABLED).isOperational()).isFalse();
        assertThat(sample(DmConnectorStatus.AUTH_FAILED).isOperational()).isFalse();
    }

    @Test
    @DisplayName("equals and hashCode by id")
    void equalsHashCode() {
        DmConnectorConfig a = sample(DmConnectorStatus.PENDING);
        DmConnectorConfig b = sample(DmConnectorStatus.ACTIVE);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("toString includes id, type, status")
    void toStringContents() {
        String s = sample(DmConnectorStatus.ACTIVE).toString();
        assertThat(s).contains("conn-1").contains("GOOGLE_ADS").contains("ACTIVE");
    }

    // ── Builder validation ────────────────────────────────────────────────────

    @Test
    @DisplayName("builder rejects blank id")
    void builderBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> DmConnectorConfig.builder()
                .id("").tenantId("t").name("n").connectorType(DmConnectorType.GOOGLE_ADS)
                .status(DmConnectorStatus.PENDING).settings(Map.of()).createdAt(Instant.now())
                .updatedAt(Instant.now()).build());
    }

    @Test
    @DisplayName("builder rejects blank tenantId")
    void builderBlankTenantId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> DmConnectorConfig.builder()
                .id("id").tenantId("").name("n").connectorType(DmConnectorType.GOOGLE_ADS)
                .status(DmConnectorStatus.PENDING).settings(Map.of()).createdAt(Instant.now())
                .updatedAt(Instant.now()).build());
    }

    @Test
    @DisplayName("builder rejects blank name")
    void builderBlankName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> DmConnectorConfig.builder()
                .id("id").tenantId("t").name("").connectorType(DmConnectorType.GOOGLE_ADS)
                .status(DmConnectorStatus.PENDING).settings(Map.of()).createdAt(Instant.now())
                .updatedAt(Instant.now()).build());
    }

    @Test
    @DisplayName("builder rejects null connectorType")
    void builderNullType() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> DmConnectorConfig.builder()
                .id("id").tenantId("t").name("n").connectorType(null)
                .status(DmConnectorStatus.PENDING).settings(Map.of()).createdAt(Instant.now())
                .updatedAt(Instant.now()).build());
    }

    @Test
    @DisplayName("builder rejects null status")
    void builderNullStatus() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> DmConnectorConfig.builder()
                .id("id").tenantId("t").name("n").connectorType(DmConnectorType.GOOGLE_ADS)
                .status(null).settings(Map.of()).createdAt(Instant.now())
                .updatedAt(Instant.now()).build());
    }

    @Test
    @DisplayName("builder rejects null createdAt")
    void builderNullCreatedAt() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> DmConnectorConfig.builder()
                .id("id").tenantId("t").name("n").connectorType(DmConnectorType.GOOGLE_ADS)
                .status(DmConnectorStatus.PENDING).settings(Map.of()).createdAt(null)
                .build());
    }

    @Test
    @DisplayName("settings are stored as immutable copy")
    void settingsImmutable() {
        DmConnectorConfig c = sample(DmConnectorStatus.PENDING);
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> c.getSettings().put("key", "val"));
    }
}

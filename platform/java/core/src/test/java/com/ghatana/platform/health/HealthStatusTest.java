package com.ghatana.platform.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HealthStatus} — factory methods, builder, and status semantics.
 *
 * @doc.type class
 * @doc.purpose Unit tests for HealthStatus value object
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("HealthStatus — factory methods, builder, and status semantics")
class HealthStatusTest {

    // ─── healthy() factory ─────────────────────────────────────────────────────

    @Test
    @DisplayName("healthy() returns HEALTHY status with non-null timestamp")
    void healthy_noArgs_returnsHealthyWithTimestamp() {
        HealthStatus status = HealthStatus.healthy();

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY);
        assertThat(status.isHealthy()).isTrue();
        assertThat(status.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("healthy(message) preserves the message")
    void healthy_withMessage_preservesMessage() {
        HealthStatus status = HealthStatus.healthy("All systems go");

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY);
        assertThat(status.getMessage()).isEqualTo("All systems go");
    }

    @Test
    @DisplayName("healthy(message, details) preserves both message and details")
    void healthy_withMessageAndDetails_preservesBoth() {
        Map<String, Object> details = Map.of("latencyMs", 12L);
        HealthStatus status = HealthStatus.healthy("Connected", details);

        assertThat(status.isHealthy()).isTrue();
        assertThat(status.getMessage()).isEqualTo("Connected");
        assertThat(status.getDetails()).containsEntry("latencyMs", 12L);
    }

    // ─── unhealthy() factory ────────────────────────────────────────────────────

    @Test
    @DisplayName("unhealthy(message) returns UNHEALTHY status")
    void unhealthy_withMessage_returnsUnhealthy() {
        HealthStatus status = HealthStatus.unhealthy("Connection refused");

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.UNHEALTHY);
        assertThat(status.isUnhealthy()).isTrue();
        assertThat(status.getMessage()).isEqualTo("Connection refused");
    }

    @Test
    @DisplayName("unhealthy(message, cause) captures the exception")
    void unhealthy_withCause_capturesException() {
        RuntimeException cause = new RuntimeException("DB down");
        HealthStatus status = HealthStatus.unhealthy("Database unreachable", cause);

        assertThat(status.isUnhealthy()).isTrue();
        assertThat(status.getException()).isSameAs(cause);
    }

    // ─── degraded() factory ────────────────────────────────────────────────────

    @Test
    @DisplayName("degraded(message) returns DEGRADED status")
    void degraded_withMessage_returnsDegraded() {
        HealthStatus status = HealthStatus.degraded("Slow response");

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.DEGRADED);
        assertThat(status.isDegraded()).isTrue();
    }

    // ─── ok() / error() plugin aliases ─────────────────────────────────────────

    @Test
    @DisplayName("ok() is an alias for healthy() — returns HEALTHY status")
    void ok_isAliasForHealthy() {
        HealthStatus status = HealthStatus.ok();

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY);
        assertThat(status.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("error(message) returns UNHEALTHY status")
    void error_withMessage_returnsUnhealthy() {
        HealthStatus status = HealthStatus.error("S3 unreachable");

        assertThat(status.isUnhealthy()).isTrue();
        assertThat(status.getMessage()).isEqualTo("S3 unreachable");
    }

    @Test
    @DisplayName("error(message, Throwable) captures exception and adds details")
    void error_withThrowable_capturesExceptionAndDetails() {
        IOException cause = new IOException("timeout");
        HealthStatus status = HealthStatus.error("Storage failure", cause);

        assertThat(status.isUnhealthy()).isTrue();
        assertThat(status.getException()).isSameAs(cause);
        assertThat(status.getDetails()).containsKey("error");
    }

    // ─── unknown() factory ─────────────────────────────────────────────────────

    @Test
    @DisplayName("unknown(message) returns UNKNOWN status")
    void unknown_withMessage_returnsUnknown() {
        HealthStatus status = HealthStatus.unknown("Not yet initialized");

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.UNKNOWN);
        assertThat(status.getMessage()).isEqualTo("Not yet initialized");
    }

    // ─── builder ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder with withStatus and withCheck adds checks map")
    void builder_withCheck_addsToChecksMap() {
        HealthStatus status = HealthStatus.builder()
                .withStatus(HealthStatus.Status.HEALTHY)
                .withCheck("db", HealthStatus.Status.HEALTHY, "Connected", 12L)
                .build();

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY);
        assertThat(status.getChecks()).containsKey("db");

        HealthStatus.HealthCheck dbCheck = status.getCheck("db");
        assertThat(dbCheck).isNotNull();
        assertThat(dbCheck.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY);
        assertThat(dbCheck.getMessage()).isEqualTo("Connected");
        assertThat(dbCheck.getResponseTimeMs()).isEqualTo(12L);
        assertThat(dbCheck.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("builder with multiple checks adds all to the checks map")
    void builder_multipleChecks_allPresent() {
        HealthStatus status = HealthStatus.builder()
                .withStatus(HealthStatus.Status.DEGRADED)
                .withCheck("db", HealthStatus.Status.HEALTHY, "OK", 5L)
                .withCheck("cache", HealthStatus.Status.UNHEALTHY, "Redis down", 0L)
                .build();

        assertThat(status.getChecks()).hasSize(2).containsKeys("db", "cache");
    }

    @Test
    @DisplayName("builder toString includes status and check count")
    void healthStatus_toString_containsStatusInfo() {
        HealthStatus status = HealthStatus.healthy("OK");

        assertThat(status.toString())
                .contains("HEALTHY")
                .contains("OK");
    }

    // ─── checks map immutability ────────────────────────────────────────────────

    @Test
    @DisplayName("getChecks returns an unmodifiable map")
    void getChecks_returnsUnmodifiableMap() {
        HealthStatus status = HealthStatus.builder()
                .withStatus(HealthStatus.Status.HEALTHY)
                .withCheck("db", HealthStatus.Status.HEALTHY, "OK", 1L)
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> status.getChecks().put("extra", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

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

    // ─── healthy() factory ───────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("healthy() returns HEALTHY status with non-null timestamp")
    void healthy_noArgs_returnsHealthyWithTimestamp() { // GH-90000
        HealthStatus status = HealthStatus.healthy(); // GH-90000

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); // GH-90000
        assertThat(status.isHealthy()).isTrue(); // GH-90000
        assertThat(status.getTimestamp()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("healthy(message) preserves the message")
    void healthy_withMessage_preservesMessage() { // GH-90000
        HealthStatus status = HealthStatus.healthy("All systems go");

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); // GH-90000
        assertThat(status.getMessage()).isEqualTo("All systems go");
    }

    @Test
    @DisplayName("healthy(message, details) preserves both message and details")
    void healthy_withMessageAndDetails_preservesBoth() { // GH-90000
        Map<String, Object> details = Map.of("latencyMs", 12L); // GH-90000
        HealthStatus status = HealthStatus.healthy("Connected", details); // GH-90000

        assertThat(status.isHealthy()).isTrue(); // GH-90000
        assertThat(status.getMessage()).isEqualTo("Connected");
        assertThat(status.getDetails()).containsEntry("latencyMs", 12L); // GH-90000
    }

    // ─── unhealthy() factory ──────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("unhealthy(message) returns UNHEALTHY status")
    void unhealthy_withMessage_returnsUnhealthy() { // GH-90000
        HealthStatus status = HealthStatus.unhealthy("Connection refused");

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.UNHEALTHY); // GH-90000
        assertThat(status.isUnhealthy()).isTrue(); // GH-90000
        assertThat(status.getMessage()).isEqualTo("Connection refused");
    }

    @Test
    @DisplayName("unhealthy(message, cause) captures the exception")
    void unhealthy_withCause_capturesException() { // GH-90000
        RuntimeException cause = new RuntimeException("DB down");
        HealthStatus status = HealthStatus.unhealthy("Database unreachable", cause); // GH-90000

        assertThat(status.isUnhealthy()).isTrue(); // GH-90000
        assertThat(status.getException()).isSameAs(cause); // GH-90000
    }

    // ─── degraded() factory ──────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("degraded(message) returns DEGRADED status")
    void degraded_withMessage_returnsDegraded() { // GH-90000
        HealthStatus status = HealthStatus.degraded("Slow response");

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.DEGRADED); // GH-90000
        assertThat(status.isDegraded()).isTrue(); // GH-90000
    }

    // ─── ok() / error() plugin aliases ───────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("ok() is an alias for healthy() — returns HEALTHY status")
    void ok_isAliasForHealthy() { // GH-90000
        HealthStatus status = HealthStatus.ok(); // GH-90000

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); // GH-90000
        assertThat(status.isHealthy()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("error(message) returns UNHEALTHY status")
    void error_withMessage_returnsUnhealthy() { // GH-90000
        HealthStatus status = HealthStatus.error("S3 unreachable");

        assertThat(status.isUnhealthy()).isTrue(); // GH-90000
        assertThat(status.getMessage()).isEqualTo("S3 unreachable");
    }

    @Test
    @DisplayName("error(message, Throwable) captures exception and adds details")
    void error_withThrowable_capturesExceptionAndDetails() { // GH-90000
        IOException cause = new IOException("timeout");
        HealthStatus status = HealthStatus.error("Storage failure", cause); // GH-90000

        assertThat(status.isUnhealthy()).isTrue(); // GH-90000
        assertThat(status.getException()).isSameAs(cause); // GH-90000
        assertThat(status.getDetails()).containsKey("error");
    }

    // ─── unknown() factory ───────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("unknown(message) returns UNKNOWN status")
    void unknown_withMessage_returnsUnknown() { // GH-90000
        HealthStatus status = HealthStatus.unknown("Not yet initialized");

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.UNKNOWN); // GH-90000
        assertThat(status.getMessage()).isEqualTo("Not yet initialized");
    }

    // ─── builder ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder with withStatus and withCheck adds checks map")
    void builder_withCheck_addsToChecksMap() { // GH-90000
        HealthStatus status = HealthStatus.builder() // GH-90000
                .withStatus(HealthStatus.Status.HEALTHY) // GH-90000
                .withCheck("db", HealthStatus.Status.HEALTHY, "Connected", 12L) // GH-90000
                .build(); // GH-90000

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); // GH-90000
        assertThat(status.getChecks()).containsKey("db");

        HealthStatus.HealthCheck dbCheck = status.getCheck("db");
        assertThat(dbCheck).isNotNull(); // GH-90000
        assertThat(dbCheck.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); // GH-90000
        assertThat(dbCheck.getMessage()).isEqualTo("Connected");
        assertThat(dbCheck.getResponseTimeMs()).isEqualTo(12L); // GH-90000
        assertThat(dbCheck.isHealthy()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("builder with multiple checks adds all to the checks map")
    void builder_multipleChecks_allPresent() { // GH-90000
        HealthStatus status = HealthStatus.builder() // GH-90000
                .withStatus(HealthStatus.Status.DEGRADED) // GH-90000
                .withCheck("db", HealthStatus.Status.HEALTHY, "OK", 5L) // GH-90000
                .withCheck("cache", HealthStatus.Status.UNHEALTHY, "Redis down", 0L) // GH-90000
                .build(); // GH-90000

        assertThat(status.getChecks()).hasSize(2).containsKeys("db", "cache"); // GH-90000
    }

    @Test
    @DisplayName("builder toString includes status and check count")
    void healthStatus_toString_containsStatusInfo() { // GH-90000
        HealthStatus status = HealthStatus.healthy("OK");

        assertThat(status.toString()) // GH-90000
                .contains("HEALTHY")
                .contains("OK");
    }

    // ─── checks map immutability ────────────────────────────────────────────────

    @Test
    @DisplayName("getChecks returns an unmodifiable map")
    void getChecks_returnsUnmodifiableMap() { // GH-90000
        HealthStatus status = HealthStatus.builder() // GH-90000
                .withStatus(HealthStatus.Status.HEALTHY) // GH-90000
                .withCheck("db", HealthStatus.Status.HEALTHY, "OK", 1L) // GH-90000
                .build(); // GH-90000

        org.assertj.core.api.Assertions.assertThatThrownBy( // GH-90000
                () -> status.getChecks().put("extra", null)) // GH-90000
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }
}

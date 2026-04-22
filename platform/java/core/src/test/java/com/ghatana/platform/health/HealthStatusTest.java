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
@DisplayName("HealthStatus — factory methods, builder, and status semantics [GH-90000]")
class HealthStatusTest {

    // ─── healthy() factory ───────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("healthy() returns HEALTHY status with non-null timestamp [GH-90000]")
    void healthy_noArgs_returnsHealthyWithTimestamp() { // GH-90000
        HealthStatus status = HealthStatus.healthy(); // GH-90000

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); // GH-90000
        assertThat(status.isHealthy()).isTrue(); // GH-90000
        assertThat(status.getTimestamp()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("healthy(message) preserves the message [GH-90000]")
    void healthy_withMessage_preservesMessage() { // GH-90000
        HealthStatus status = HealthStatus.healthy("All systems go [GH-90000]");

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); // GH-90000
        assertThat(status.getMessage()).isEqualTo("All systems go [GH-90000]");
    }

    @Test
    @DisplayName("healthy(message, details) preserves both message and details [GH-90000]")
    void healthy_withMessageAndDetails_preservesBoth() { // GH-90000
        Map<String, Object> details = Map.of("latencyMs", 12L); // GH-90000
        HealthStatus status = HealthStatus.healthy("Connected", details); // GH-90000

        assertThat(status.isHealthy()).isTrue(); // GH-90000
        assertThat(status.getMessage()).isEqualTo("Connected [GH-90000]");
        assertThat(status.getDetails()).containsEntry("latencyMs", 12L); // GH-90000
    }

    // ─── unhealthy() factory ──────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("unhealthy(message) returns UNHEALTHY status [GH-90000]")
    void unhealthy_withMessage_returnsUnhealthy() { // GH-90000
        HealthStatus status = HealthStatus.unhealthy("Connection refused [GH-90000]");

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.UNHEALTHY); // GH-90000
        assertThat(status.isUnhealthy()).isTrue(); // GH-90000
        assertThat(status.getMessage()).isEqualTo("Connection refused [GH-90000]");
    }

    @Test
    @DisplayName("unhealthy(message, cause) captures the exception [GH-90000]")
    void unhealthy_withCause_capturesException() { // GH-90000
        RuntimeException cause = new RuntimeException("DB down [GH-90000]");
        HealthStatus status = HealthStatus.unhealthy("Database unreachable", cause); // GH-90000

        assertThat(status.isUnhealthy()).isTrue(); // GH-90000
        assertThat(status.getException()).isSameAs(cause); // GH-90000
    }

    // ─── degraded() factory ──────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("degraded(message) returns DEGRADED status [GH-90000]")
    void degraded_withMessage_returnsDegraded() { // GH-90000
        HealthStatus status = HealthStatus.degraded("Slow response [GH-90000]");

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.DEGRADED); // GH-90000
        assertThat(status.isDegraded()).isTrue(); // GH-90000
    }

    // ─── ok() / error() plugin aliases ───────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("ok() is an alias for healthy() — returns HEALTHY status [GH-90000]")
    void ok_isAliasForHealthy() { // GH-90000
        HealthStatus status = HealthStatus.ok(); // GH-90000

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); // GH-90000
        assertThat(status.isHealthy()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("error(message) returns UNHEALTHY status [GH-90000]")
    void error_withMessage_returnsUnhealthy() { // GH-90000
        HealthStatus status = HealthStatus.error("S3 unreachable [GH-90000]");

        assertThat(status.isUnhealthy()).isTrue(); // GH-90000
        assertThat(status.getMessage()).isEqualTo("S3 unreachable [GH-90000]");
    }

    @Test
    @DisplayName("error(message, Throwable) captures exception and adds details [GH-90000]")
    void error_withThrowable_capturesExceptionAndDetails() { // GH-90000
        IOException cause = new IOException("timeout [GH-90000]");
        HealthStatus status = HealthStatus.error("Storage failure", cause); // GH-90000

        assertThat(status.isUnhealthy()).isTrue(); // GH-90000
        assertThat(status.getException()).isSameAs(cause); // GH-90000
        assertThat(status.getDetails()).containsKey("error [GH-90000]");
    }

    // ─── unknown() factory ───────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("unknown(message) returns UNKNOWN status [GH-90000]")
    void unknown_withMessage_returnsUnknown() { // GH-90000
        HealthStatus status = HealthStatus.unknown("Not yet initialized [GH-90000]");

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.UNKNOWN); // GH-90000
        assertThat(status.getMessage()).isEqualTo("Not yet initialized [GH-90000]");
    }

    // ─── builder ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder with withStatus and withCheck adds checks map [GH-90000]")
    void builder_withCheck_addsToChecksMap() { // GH-90000
        HealthStatus status = HealthStatus.builder() // GH-90000
                .withStatus(HealthStatus.Status.HEALTHY) // GH-90000
                .withCheck("db", HealthStatus.Status.HEALTHY, "Connected", 12L) // GH-90000
                .build(); // GH-90000

        assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); // GH-90000
        assertThat(status.getChecks()).containsKey("db [GH-90000]");

        HealthStatus.HealthCheck dbCheck = status.getCheck("db [GH-90000]");
        assertThat(dbCheck).isNotNull(); // GH-90000
        assertThat(dbCheck.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); // GH-90000
        assertThat(dbCheck.getMessage()).isEqualTo("Connected [GH-90000]");
        assertThat(dbCheck.getResponseTimeMs()).isEqualTo(12L); // GH-90000
        assertThat(dbCheck.isHealthy()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("builder with multiple checks adds all to the checks map [GH-90000]")
    void builder_multipleChecks_allPresent() { // GH-90000
        HealthStatus status = HealthStatus.builder() // GH-90000
                .withStatus(HealthStatus.Status.DEGRADED) // GH-90000
                .withCheck("db", HealthStatus.Status.HEALTHY, "OK", 5L) // GH-90000
                .withCheck("cache", HealthStatus.Status.UNHEALTHY, "Redis down", 0L) // GH-90000
                .build(); // GH-90000

        assertThat(status.getChecks()).hasSize(2).containsKeys("db", "cache"); // GH-90000
    }

    @Test
    @DisplayName("builder toString includes status and check count [GH-90000]")
    void healthStatus_toString_containsStatusInfo() { // GH-90000
        HealthStatus status = HealthStatus.healthy("OK [GH-90000]");

        assertThat(status.toString()) // GH-90000
                .contains("HEALTHY [GH-90000]")
                .contains("OK [GH-90000]");
    }

    // ─── checks map immutability ────────────────────────────────────────────────

    @Test
    @DisplayName("getChecks returns an unmodifiable map [GH-90000]")
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

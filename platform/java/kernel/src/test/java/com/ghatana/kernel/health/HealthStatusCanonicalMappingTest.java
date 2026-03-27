package com.ghatana.kernel.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Kernel HealthStatus canonical mapping")
class HealthStatusCanonicalMappingTest {

    @Test
    @DisplayName("maps kernel status to canonical platform health")
    void shouldMapKernelStatusToPlatformHealth() {
        Instant timestamp = Instant.parse("2026-03-27T10:15:30Z");
        HealthStatus kernelStatus = HealthStatus.builder()
            .withStatus(HealthStatus.Status.DEGRADED)
            .withMessage("Kernel partially available")
            .withTimestamp(timestamp)
            .withCheck("registry", HealthStatus.Status.HEALTHY, "Registry reachable", 12)
            .withCheck("plugins", HealthStatus.Status.DEGRADED, "One plugin restarting", 48)
            .build();

        com.ghatana.platform.health.HealthStatus platformStatus = kernelStatus.toPlatformHealthStatus();

        assertThat(platformStatus.getStatus()).isEqualTo(com.ghatana.platform.health.HealthStatus.Status.DEGRADED);
        assertThat(platformStatus.getMessage()).isEqualTo("Kernel partially available");
        assertThat(platformStatus.getTimestamp()).isEqualTo(timestamp);
        assertThat(platformStatus.getChecks()).containsKeys("registry", "plugins");
        assertThat(platformStatus.getCheck("plugins").getStatus())
            .isEqualTo(com.ghatana.platform.health.HealthStatus.Status.DEGRADED);
    }

    @Test
    @DisplayName("rebuilds kernel status from canonical platform health")
    void shouldMapPlatformHealthToKernelStatus() {
        Instant timestamp = Instant.parse("2026-03-27T10:20:30Z");
        com.ghatana.platform.health.HealthStatus platformStatus =
            com.ghatana.platform.health.HealthStatus.builder()
                .withStatus(com.ghatana.platform.health.HealthStatus.Status.UNHEALTHY)
                .withMessage("Kernel registry unavailable")
                .withTimestamp(timestamp)
                .withCheck("registry", com.ghatana.platform.health.HealthStatus.Status.UNHEALTHY, "Timed out", 250)
                .build();

        HealthStatus kernelStatus = HealthStatus.fromPlatformHealthStatus(platformStatus);

        assertThat(kernelStatus.getStatus()).isEqualTo(HealthStatus.Status.UNHEALTHY);
        assertThat(kernelStatus.getMessage()).isEqualTo("Kernel registry unavailable");
        assertThat(kernelStatus.getTimestamp()).isEqualTo(timestamp);
        assertThat(kernelStatus.getCheck("registry").getStatus()).isEqualTo(HealthStatus.Status.UNHEALTHY);
        assertThat(kernelStatus.getCheck("registry").getResponseTimeMs()).isEqualTo(250);
    }
}
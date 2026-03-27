package com.ghatana.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Agent HealthStatus canonical mapping")
class HealthStatusCanonicalMappingTest {

    @Test
    @DisplayName("maps starting and stopping states onto canonical degraded health")
    void shouldMapLifecycleStatesToCanonicalHealth() {
        assertThat(HealthStatus.STARTING.toPlatformHealthStatus().getStatus())
            .isEqualTo(com.ghatana.platform.health.HealthStatus.Status.DEGRADED);
        assertThat(HealthStatus.STOPPING.toPlatformHealthStatus().getStatus())
            .isEqualTo(com.ghatana.platform.health.HealthStatus.Status.DEGRADED);
    }

    @Test
    @DisplayName("maps canonical health back to lifecycle states")
    void shouldMapCanonicalHealthToLifecycleStates() {
        assertThat(HealthStatus.fromPlatformHealthStatus(com.ghatana.platform.health.HealthStatus.healthy()))
            .isEqualTo(HealthStatus.HEALTHY);
        assertThat(HealthStatus.fromPlatformHealthStatus(com.ghatana.platform.health.HealthStatus.degraded("slow")))
            .isEqualTo(HealthStatus.DEGRADED);
        assertThat(HealthStatus.fromPlatformHealthStatus(com.ghatana.platform.health.HealthStatus.unhealthy("down")))
            .isEqualTo(HealthStatus.UNHEALTHY);
        assertThat(HealthStatus.fromPlatformHealthStatus(com.ghatana.platform.health.HealthStatus.unknown("n/a")))
            .isEqualTo(HealthStatus.UNKNOWN);
    }
}
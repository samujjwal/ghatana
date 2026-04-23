package com.ghatana.yappc.services.performance;

import com.ghatana.yappc.services.metrics.BusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("YAPPC Services Performance Tier")
class YappcServicesPerformanceTierTest {

    @Test
    @DisplayName("records lifecycle business metrics within baseline")
    void shouldRecordLifecycleMetricsWithinBaseline() {
        int iterations = 8_000;

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BusinessMetrics metrics = new BusinessMetrics(registry);

        Instant started = Instant.now();
        for (int i = 0; i < iterations; i++) {
            metrics.setActiveProjects(i % 100);
            metrics.setPendingApprovals(i % 50);
            metrics.setActiveTenants(i % 25);
            metrics.recordPhaseTransition("tenant-a", "build", "test");
            metrics.recordProjectCreated("tenant-a");
            metrics.recordScaffoldGeneration("tenant-a", "spring-boot", true);
        }

        long elapsedMillis = Duration.between(started, Instant.now()).toMillis();

        assertThat(metrics.getTotalPhaseTransitions()).isEqualTo(iterations);
        assertThat(elapsedMillis).isLessThan(2_500);
    }
}



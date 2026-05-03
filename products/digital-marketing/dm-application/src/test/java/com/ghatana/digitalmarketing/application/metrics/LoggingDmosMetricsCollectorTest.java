package com.ghatana.digitalmarketing.application.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LoggingDmosMetricsCollector}.
 */
@DisplayName("LoggingDmosMetricsCollector")
class LoggingDmosMetricsCollectorTest {

    @Test
    @DisplayName("increment logs metric with labels")
    void increment_logsMetricWithLabels() {
        LoggingDmosMetricsCollector collector = new LoggingDmosMetricsCollector();

        // Should not throw - logs the metric
        collector.increment(DmosMetricsCollector.CAMPAIGN_CREATED, Map.of("tenantId", "acme", "workspaceId", "ws-1"));
    }

    @Test
    @DisplayName("increment throws for null counterName")
    void increment_throwsForNullCounterName() {
        LoggingDmosMetricsCollector collector = new LoggingDmosMetricsCollector();

        assertThatThrownBy(() -> collector.increment(null, Map.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("counterName");
    }

    @Test
    @DisplayName("increment throws for null labels")
    void increment_throwsForNullLabels() {
        LoggingDmosMetricsCollector collector = new LoggingDmosMetricsCollector();

        assertThatThrownBy(() -> collector.increment(DmosMetricsCollector.CAMPAIGN_CREATED, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("labels");
    }

    @Test
    @DisplayName("observe uses default implementation")
    void observe_usesDefaultImplementation() {
        LoggingDmosMetricsCollector collector = new LoggingDmosMetricsCollector();

        // Should not throw - uses default implementation which adds durationMs label
        collector.observe(DmosMetricsCollector.API_REQUEST_DURATION, 150, Map.of("tenantId", "acme"));
    }
}

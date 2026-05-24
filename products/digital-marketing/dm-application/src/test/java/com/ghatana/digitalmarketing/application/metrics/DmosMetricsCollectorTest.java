package com.ghatana.digitalmarketing.application.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DmosMetricsCollector}.
 */
@DisplayName("DmosMetricsCollector")
class DmosMetricsCollectorTest {

    @Test
    @DisplayName("noop returns no-op implementation")
    void noop_returnsNoOpImplementation() {
        DmosMetricsCollector collector = DmosMetricsCollector.disabled();

        // Should not throw any exception
        collector.increment(DmosMetricsCollector.CAMPAIGN_CREATED, Map.of("tenantId", "test"));

        DmosMetricsCollector noop = DmosMetricsCollector.disabled();
        assertThat(noop).isNotNull();
    }

    @Test
    @DisplayName("default observe adds durationMs label")
    void defaultObserve_addsDurationMsLabel() {
        RecordingMetricsCollector collector = new RecordingMetricsCollector();
        Map<String, String> labels = Map.of("tenantId", "acme", "workspaceId", "ws-1");

        collector.observe(DmosMetricsCollector.API_REQUEST_DURATION, 150, labels);

        assertThat(collector.lastCounterName()).isEqualTo(DmosMetricsCollector.API_REQUEST_DURATION);
        assertThat(collector.lastLabels()).containsEntry("durationMs", "150");
        assertThat(collector.lastLabels()).containsEntry("tenantId", "acme");
        assertThat(collector.lastLabels()).containsEntry("workspaceId", "ws-1");
    }

    @Test
    @DisplayName("metric constants are defined")
    void metricConstants_areDefined() {
        assertThat(DmosMetricsCollector.CAMPAIGN_CREATED).isEqualTo("dmos.campaign.created");
        assertThat(DmosMetricsCollector.CAMPAIGN_LAUNCHED).isEqualTo("dmos.campaign.launched");
        assertThat(DmosMetricsCollector.CAMPAIGN_PAUSED).isEqualTo("dmos.campaign.paused");
        assertThat(DmosMetricsCollector.APPROVAL_REQUESTED).isEqualTo("dmos.approval.requested");
        assertThat(DmosMetricsCollector.PERFORMANCE_FETCHED).isEqualTo("dmos.performance.fetched");
        assertThat(DmosMetricsCollector.APPROVAL_PENDING_GAUGE).isEqualTo("dmos.approval.pending");
        assertThat(DmosMetricsCollector.COMPLIANCE_VIOLATION).isEqualTo("dmos.compliance.violation");
        assertThat(DmosMetricsCollector.API_REQUEST_DURATION).isEqualTo("dmos.api.request_duration_ms");
    }

    private static final class RecordingMetricsCollector implements DmosMetricsCollector {
        private String lastCounterName;
        private Map<String, String> lastLabels;

        String lastCounterName() {
            return lastCounterName;
        }

        Map<String, String> lastLabels() {
            return lastLabels;
        }

        @Override
        public void increment(String counterName, Map<String, String> labels) {
            this.lastCounterName = counterName;
            this.lastLabels = labels;
        }
    }
}

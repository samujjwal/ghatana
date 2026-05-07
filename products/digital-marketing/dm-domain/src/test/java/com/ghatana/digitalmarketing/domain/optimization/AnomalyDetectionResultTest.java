package com.ghatana.digitalmarketing.domain.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AnomalyDetectionResult}.
 *
 * @doc.type test
 * @doc.purpose Validates AnomalyDetectionResult domain model behavior (P3-004)
 * @doc.layer product
 */
@DisplayName("AnomalyDetectionResult Tests")
class AnomalyDetectionResultTest {

    @Test
    @DisplayName("Should build valid anomaly detection result")
    void shouldBuildValidAnomalyDetectionResult() {
        Instant now = Instant.now();
        AnomalyDetectionResult anomaly = AnomalyDetectionResult.builder()
            .id("anomaly-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .severity(AnomalySeverity.HIGH)
            .metricName("conversion_rate")
            .anomalyType("SUDDEN_DROP")
            .expectedValue(0.05)
            .actualValue(0.02)
            .deviationPercentage(60.0)
            .description("Conversion rate dropped by 60%")
            .context(Map.of("timeWindow", "24h", "baseline", "7d avg"))
            .rationale("Statistically significant deviation from baseline")
            .status(AnomalyStatus.DETECTED)
            .detectedAt(now)
            .build();

        assertThat(anomaly.getId()).isEqualTo("anomaly-1");
        assertThat(anomaly.getTenantId()).isEqualTo("tenant-1");
        assertThat(anomaly.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(anomaly.getCampaignId()).isEqualTo("campaign-1");
        assertThat(anomaly.getSeverity()).isEqualTo(AnomalySeverity.HIGH);
        assertThat(anomaly.getMetricName()).isEqualTo("conversion_rate");
        assertThat(anomaly.getDeviationPercentage()).isEqualTo(60.0);
        assertThat(anomaly.getStatus()).isEqualTo(AnomalyStatus.DETECTED);
        assertThat(anomaly.isClosed()).isFalse();
    }

    @Test
    @DisplayName("Should acknowledge anomaly")
    void shouldAcknowledgeAnomaly() {
        AnomalyDetectionResult anomaly = AnomalyDetectionResult.builder()
            .id("anomaly-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .severity(AnomalySeverity.HIGH)
            .metricName("conversion_rate")
            .anomalyType("SUDDEN_DROP")
            .expectedValue(0.05)
            .actualValue(0.02)
            .deviationPercentage(60.0)
            .description("Conversion rate dropped")
            .context(Map.of())
            .rationale("Rationale")
            .status(AnomalyStatus.DETECTED)
            .detectedAt(Instant.now())
            .build();

        AnomalyDetectionResult acknowledged = anomaly.acknowledge("user-123");

        assertThat(acknowledged.getStatus()).isEqualTo(AnomalyStatus.ACKNOWLEDGED);
        assertThat(acknowledged.getAcknowledgedBy()).isEqualTo("user-123");
        assertThat(acknowledged.getAcknowledgedAt()).isNotNull();
        assertThat(acknowledged.isClosed()).isFalse();
    }

    @Test
    @DisplayName("Should resolve anomaly")
    void shouldResolveAnomaly() {
        AnomalyDetectionResult anomaly = AnomalyDetectionResult.builder()
            .id("anomaly-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .severity(AnomalySeverity.HIGH)
            .metricName("conversion_rate")
            .anomalyType("SUDDEN_DROP")
            .expectedValue(0.05)
            .actualValue(0.02)
            .deviationPercentage(60.0)
            .description("Conversion rate dropped")
            .context(Map.of())
            .rationale("Rationale")
            .status(AnomalyStatus.ACKNOWLEDGED)
            .acknowledgedBy("user-123")
            .acknowledgedAt(Instant.now().minusSeconds(3600))
            .detectedAt(Instant.now().minusSeconds(7200))
            .build();

        AnomalyDetectionResult resolved = anomaly.resolve("Adjusted targeting parameters");

        assertThat(resolved.getStatus()).isEqualTo(AnomalyStatus.RESOLVED);
        assertThat(resolved.getMitigationAction()).isEqualTo("Adjusted targeting parameters");
        assertThat(resolved.getResolvedAt()).isNotNull();
        assertThat(resolved.isClosed()).isTrue();
    }

    @Test
    @DisplayName("Should dismiss anomaly")
    void shouldDismissAnomaly() {
        AnomalyDetectionResult anomaly = AnomalyDetectionResult.builder()
            .id("anomaly-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .severity(AnomalySeverity.HIGH)
            .metricName("conversion_rate")
            .anomalyType("SUDDEN_DROP")
            .expectedValue(0.05)
            .actualValue(0.02)
            .deviationPercentage(60.0)
            .description("Conversion rate dropped")
            .context(Map.of())
            .rationale("Rationale")
            .status(AnomalyStatus.DETECTED)
            .detectedAt(Instant.now())
            .build();

        AnomalyDetectionResult dismissed = anomaly.dismiss("False positive - seasonal variation");

        assertThat(dismissed.getStatus()).isEqualTo(AnomalyStatus.DISMISSED);
        assertThat(dismissed.getMitigationAction()).isEqualTo("False positive - seasonal variation");
        assertThat(dismissed.getResolvedAt()).isNotNull();
        assertThat(dismissed.isClosed()).isTrue();
    }

    @Test
    @DisplayName("Should throw when acknowledging non-detected anomaly")
    void shouldThrowWhenAcknowledgingNonDetectedAnomaly() {
        AnomalyDetectionResult anomaly = AnomalyDetectionResult.builder()
            .id("anomaly-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .severity(AnomalySeverity.HIGH)
            .metricName("conversion_rate")
            .anomalyType("SUDDEN_DROP")
            .expectedValue(0.05)
            .actualValue(0.02)
            .deviationPercentage(60.0)
            .description("Conversion rate dropped")
            .context(Map.of())
            .rationale("Rationale")
            .status(AnomalyStatus.RESOLVED)
            .detectedAt(Instant.now())
            .build();

        assertThatThrownBy(() -> anomaly.acknowledge("user-123"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Only DETECTED anomalies can be acknowledged");
    }

    @Test
    @DisplayName("Should throw when resolving non-acknowledged anomaly")
    void shouldThrowWhenResolvingNonAcknowledgedAnomaly() {
        AnomalyDetectionResult anomaly = AnomalyDetectionResult.builder()
            .id("anomaly-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .severity(AnomalySeverity.HIGH)
            .metricName("conversion_rate")
            .anomalyType("SUDDEN_DROP")
            .expectedValue(0.05)
            .actualValue(0.02)
            .deviationPercentage(60.0)
            .description("Conversion rate dropped")
            .context(Map.of())
            .rationale("Rationale")
            .status(AnomalyStatus.DETECTED)
            .detectedAt(Instant.now())
            .build();

        assertThatThrownBy(() -> anomaly.resolve("Action"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Only ACKNOWLEDGED anomalies can be resolved");
    }

    @Test
    @DisplayName("Should support toBuilder pattern")
    void shouldSupportToBuilderPattern() {
        Instant now = Instant.now();
        AnomalyDetectionResult original = AnomalyDetectionResult.builder()
            .id("anomaly-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .severity(AnomalySeverity.HIGH)
            .metricName("conversion_rate")
            .anomalyType("SUDDEN_DROP")
            .expectedValue(0.05)
            .actualValue(0.02)
            .deviationPercentage(60.0)
            .description("Conversion rate dropped")
            .context(Map.of())
            .rationale("Rationale")
            .status(AnomalyStatus.DETECTED)
            .detectedAt(now)
            .build();

        AnomalyDetectionResult modified = original.toBuilder()
            .severity(AnomalySeverity.CRITICAL)
            .deviationPercentage(80.0)
            .build();

        assertThat(modified.getId()).isEqualTo(original.getId());
        assertThat(modified.getSeverity()).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(modified.getDeviationPercentage()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("Should implement equals and hashCode based on id")
    void shouldImplementEqualsAndHashCodeBasedOnId() {
        Instant now = Instant.now();
        AnomalyDetectionResult anomaly1 = AnomalyDetectionResult.builder()
            .id("anomaly-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .severity(AnomalySeverity.HIGH)
            .metricName("conversion_rate")
            .anomalyType("SUDDEN_DROP")
            .expectedValue(0.05)
            .actualValue(0.02)
            .deviationPercentage(60.0)
            .description("Conversion rate dropped")
            .context(Map.of())
            .rationale("Rationale")
            .status(AnomalyStatus.DETECTED)
            .detectedAt(now)
            .build();

        AnomalyDetectionResult anomaly2 = AnomalyDetectionResult.builder()
            .id("anomaly-1")
            .tenantId("tenant-2")
            .workspaceId("workspace-2")
            .campaignId("campaign-2")
            .severity(AnomalySeverity.LOW)
            .metricName("ctr")
            .anomalyType("SPIKE")
            .expectedValue(0.02)
            .actualValue(0.03)
            .deviationPercentage(50.0)
            .description("CTR spike")
            .context(Map.of())
            .rationale("Different rationale")
            .status(AnomalyStatus.RESOLVED)
            .detectedAt(now)
            .build();

        assertThat(anomaly1).isEqualTo(anomaly2);
        assertThat(anomaly1.hashCode()).isEqualTo(anomaly2.hashCode());
    }
}

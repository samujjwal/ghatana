package com.ghatana.datacloud.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused unit tests for MetricsService.
 *
 * <p>These tests cover the core functionality without integrating with
 * external systems or running release-readiness flows.</p>
 *
 * @doc.type class
 * @doc.purpose Focused unit tests for MetricsService
 * @doc.layer test
 * @doc.pattern UnitTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsService Tests")
class MetricsServiceTest {

    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = MetricsService.builder()
            .serviceName("test-service")
            .enableAggregation(true)
            .build();
    }

    @Test
    @DisplayName("Should increment counter metric")
    void shouldIncrementCounterMetric() {
        String metricName = "test_counter";
        Map<String, String> tags = Map.of("tag1", "value1");

        metricsService.incrementCounter(metricName, tags);

        var snapshot = metricsService.getMetricSnapshot(metricName);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getValues()).hasSize(1);
        assertThat(snapshot.get().getValues().values()).contains(1.0);
    }

    @Test
    @DisplayName("Should increment counter with custom value")
    void shouldIncrementCounterWithCustomValue() {
        String metricName = "test_counter";
        Map<String, String> tags = Map.of("tag1", "value1");

        metricsService.incrementCounter(metricName, 5.0, tags);

        var snapshot = metricsService.getMetricSnapshot(metricName);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getValues().values()).contains(5.0);
    }

    @Test
    @DisplayName("Should accumulate counter increments")
    void shouldAccumulateCounterIncrements() {
        String metricName = "test_counter";
        Map<String, String> tags = Map.of("tag1", "value1");

        metricsService.incrementCounter(metricName, 3.0, tags);
        metricsService.incrementCounter(metricName, 2.0, tags);

        var snapshot = metricsService.getMetricSnapshot(metricName);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getValues().values()).contains(5.0);
    }

    @Test
    @DisplayName("Should set gauge metric")
    void shouldSetGaugeMetric() {
        String metricName = "test_gauge";
        Map<String, String> tags = Map.of("tag1", "value1");
        double value = 42.5;

        metricsService.setGauge(metricName, value, tags);

        var snapshot = metricsService.getMetricSnapshot(metricName);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getValues().values()).contains(value);
    }

    @Test
    @DisplayName("Should update gauge metric")
    void shouldUpdateGaugeMetric() {
        String metricName = "test_gauge";
        Map<String, String> tags = Map.of("tag1", "value1");

        metricsService.setGauge(metricName, 10.0, tags);
        metricsService.setGauge(metricName, 20.0, tags);

        var snapshot = metricsService.getMetricSnapshot(metricName);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getValues().values()).contains(20.0);
    }

    @Test
    @DisplayName("Should record timer metric")
    void shouldRecordTimerMetric() {
        String metricName = "test_timer";
        Map<String, String> tags = Map.of("tag1", "value1");
        long durationMs = 150;

        metricsService.recordTimer(metricName, durationMs, tags);

        var snapshot = metricsService.getMetricSnapshot(metricName);
        assertThat(snapshot).isPresent();
        
        Map<String, Double> values = snapshot.get().getValues();
        assertThat(values).containsKey("tag1=value1_count");
        assertThat(values).containsKey("tag1=value1_sum");
        assertThat(values).containsKey("tag1=value1_avg");
        assertThat(values.get("tag1=value1_count")).isEqualTo(1.0);
        assertThat(values.get("tag1=value1_sum")).isEqualTo(150.0);
        assertThat(values.get("tag1=value1_avg")).isEqualTo(150.0);
    }

    @Test
    @DisplayName("Should record multiple timer values and calculate average")
    void shouldRecordMultipleTimerValuesAndCalculateAverage() {
        String metricName = "test_timer";
        Map<String, String> tags = Map.of("tag1", "value1");

        metricsService.recordTimer(metricName, 100, tags);
        metricsService.recordTimer(metricName, 200, tags);
        metricsService.recordTimer(metricName, 300, tags);

        var snapshot = metricsService.getMetricSnapshot(metricName);
        assertThat(snapshot).isPresent();
        
        Map<String, Double> values = snapshot.get().getValues();
        assertThat(values.get("tag1=value1_count")).isEqualTo(3.0);
        assertThat(values.get("tag1=value1_sum")).isEqualTo(600.0);
        assertThat(values.get("tag1=value1_avg")).isEqualTo(200.0);
    }

    @Test
    @DisplayName("Should record histogram metric")
    void shouldRecordHistogramMetric() {
        String metricName = "test_histogram";
        Map<String, String> tags = Map.of("tag1", "value1");
        double value = 42.5;

        metricsService.recordHistogram(metricName, value, tags);

        var snapshot = metricsService.getMetricSnapshot(metricName);
        assertThat(snapshot).isPresent();
        
        Map<String, Double> values = snapshot.get().getValues();
        assertThat(values).containsKey("tag1=value1_count");
        assertThat(values).containsKey("tag1=value1_sum");
        assertThat(values).containsKey("tag1=value1_avg");
        assertThat(values).containsKey("tag1=value1_min");
        assertThat(values).containsKey("tag1=value1_max");
        assertThat(values.get("tag1=value1_count")).isEqualTo(1.0);
        assertThat(values.get("tag1=value1_sum")).isEqualTo(42.5);
        assertThat(values.get("tag1=value1_avg")).isEqualTo(42.5);
        assertThat(values.get("tag1=value1_min")).isEqualTo(42.5);
        assertThat(values.get("tag1=value1_max")).isEqualTo(42.5);
    }

    @Test
    @DisplayName("Should calculate histogram statistics correctly")
    void shouldCalculateHistogramStatisticsCorrectly() {
        String metricName = "test_histogram";
        Map<String, String> tags = Map.of("tag1", "value1");

        metricsService.recordHistogram(metricName, 10.0, tags);
        metricsService.recordHistogram(metricName, 20.0, tags);
        metricsService.recordHistogram(metricName, 30.0, tags);

        var snapshot = metricsService.getMetricSnapshot(metricName);
        assertThat(snapshot).isPresent();
        
        Map<String, Double> values = snapshot.get().getValues();
        assertThat(values.get("tag1=value1_count")).isEqualTo(3.0);
        assertThat(values.get("tag1=value1_sum")).isEqualTo(60.0);
        assertThat(values.get("tag1=value1_avg")).isEqualTo(20.0);
        assertThat(values.get("tag1=value1_min")).isEqualTo(10.0);
        assertThat(values.get("tag1=value1_max")).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Should get all metric snapshots")
    void shouldGetAllMetricSnapshots() {
        metricsService.incrementCounter("counter1", Map.of("tag", "value"));
        metricsService.setGauge("gauge1", 42.0, Map.of("tag", "value"));

        Map<String, MetricsService.MetricSnapshot> snapshots = metricsService.getAllMetricSnapshots();
        
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots).containsKeys("counter1", "gauge1");
    }

    @Test
    @DisplayName("Should filter metrics by tags")
    void shouldFilterMetricsByTags() {
        metricsService.incrementCounter("counter1", Map.of("env", "prod"));
        metricsService.incrementCounter("counter2", Map.of("env", "dev"));
        metricsService.setGauge("gauge1", 42.0, Map.of("env", "prod"));

        Map<String, MetricsService.MetricSnapshot> prodMetrics = 
            metricsService.getMetricsByTags(Map.of("env", "prod"));
        
        assertThat(prodMetrics).hasSize(2);
        assertThat(prodMetrics).containsKeys("counter1", "gauge1");
    }

    @Test
    @DisplayName("Should get service metrics summary")
    void shouldGetServiceMetricsSummary() {
        metricsService.incrementCounter("counter1", Map.of("tag", "value"));
        metricsService.setGauge("gauge1", 42.0, Map.of("tag", "value"));

        MetricsService.ServiceMetricsSummary summary = metricsService.getServiceMetricsSummary();
        
        assertThat(summary.getServiceName()).isEqualTo("test-service");
        assertThat(summary.getTotalMetrics()).isEqualTo(2);
        assertThat(summary.getMetricCounts()).containsEntry(MetricsService.MetricType.COUNTER, 1L);
        assertThat(summary.getMetricCounts()).containsEntry(MetricsService.MetricType.GAUGE, 1L);
    }

    @Test
    @DisplayName("Should reset all metrics")
    void shouldResetAllMetrics() {
        metricsService.incrementCounter("counter1", Map.of("tag", "value"));
        metricsService.setGauge("gauge1", 42.0, Map.of("tag", "value"));

        metricsService.resetAllMetrics();

        Map<String, MetricsService.MetricSnapshot> snapshots = metricsService.getAllMetricSnapshots();
        assertThat(snapshots).isEmpty();
    }

    @Test
    @DisplayName("Should reset specific metric")
    void shouldResetSpecificMetric() {
        metricsService.incrementCounter("counter1", Map.of("tag", "value"));
        metricsService.setGauge("gauge1", 42.0, Map.of("tag", "value"));

        metricsService.resetMetric("counter1");

        Map<String, MetricsService.MetricSnapshot> snapshots = metricsService.getAllMetricSnapshots();
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots).containsKey("gauge1");
    }

    @Test
    @DisplayName("Should handle empty tags gracefully")
    void shouldHandleEmptyTagsGracefully() {
        metricsService.incrementCounter("counter1", Map.of());

        var snapshot = metricsService.getMetricSnapshot("counter1");
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getValues()).hasSize(1);
    }

    @Test
    @DisplayName("Should handle null tags gracefully")
    void shouldHandleNullTagsGracefully() {
        metricsService.incrementCounter("counter1", null);

        var snapshot = metricsService.getMetricSnapshot("counter1");
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getValues()).hasSize(1);
    }

    @Test
    @DisplayName("Should create snapshots when aggregation is enabled")
    void shouldCreateSnapshotsWhenAggregationIsEnabled() {
        metricsService.incrementCounter("counter1", Map.of("tag", "value"));
        
        int initialSnapshotCount = metricsService.getServiceMetricsSummary().getSnapshotCount();
        
        metricsService.createSnapshot();
        
        assertThat(metricsService.getServiceMetricsSummary().getSnapshotCount())
            .isGreaterThan(initialSnapshotCount);
    }

    @Test
    @DisplayName("Should build service with custom configuration")
    void shouldBuildServiceWithCustomConfiguration() {
        MetricsService customService = MetricsService.builder()
            .serviceName("custom-service")
            .enableAggregation(false)
            .retentionHours(12)
            .build();

        MetricsService.ServiceMetricsSummary summary = customService.getServiceMetricsSummary();
        assertThat(summary.getServiceName()).isEqualTo("custom-service");
    }

    @Test
    @DisplayName("Should return empty snapshot for non-existent metric")
    void shouldReturnEmptySnapshotForNonExistentMetric() {
        var snapshot = metricsService.getMetricSnapshot("non_existent");
        assertThat(snapshot).isEmpty();
    }
}

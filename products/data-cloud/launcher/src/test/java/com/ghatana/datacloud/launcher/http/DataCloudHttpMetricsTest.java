package com.ghatana.datacloud.launcher.http;

import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DataCloudHttpMetricsTest {

    @Test
    void recordRequestShouldEmitStandardizedRequestMetricWithTenantAndStatusTags() { // GH-90000
        MetricsCollector collector = mock(MetricsCollector.class); // GH-90000
        DataCloudHttpMetrics metrics = new DataCloudHttpMetrics(collector); // GH-90000

        metrics.recordRequest("AnalyticsHandler", "handleAnalyticsQuery", "tenant-a", 200); // GH-90000

        verify(collector).incrementCounter( // GH-90000
                DataCloudHttpMetrics.METRIC_REQUESTS,
                DataCloudHttpMetrics.TAG_HANDLER, "AnalyticsHandler",
                DataCloudHttpMetrics.TAG_OPERATION, "handleAnalyticsQuery",
                DataCloudHttpMetrics.TAG_TENANT, "tenant-a",
                DataCloudHttpMetrics.TAG_STATUS, "200"
        );
        verifyNoMoreInteractions(collector); // GH-90000
    }

    @Test
    void recordLatencyShouldEmitStandardizedLatencyMetric() { // GH-90000
        MetricsCollector collector = mock(MetricsCollector.class); // GH-90000
        DataCloudHttpMetrics metrics = new DataCloudHttpMetrics(collector); // GH-90000

        metrics.recordLatency("AiModelHandler", "handleListAiModels", 42L); // GH-90000

        verify(collector).recordTimer( // GH-90000
                DataCloudHttpMetrics.METRIC_LATENCY,
                42L,
                DataCloudHttpMetrics.TAG_HANDLER, "AiModelHandler",
                DataCloudHttpMetrics.TAG_OPERATION, "handleListAiModels"
        );
        verifyNoMoreInteractions(collector); // GH-90000
    }

    @Test
    void recordErrorShouldEmitStandardizedErrorMetric() { // GH-90000
        MetricsCollector collector = mock(MetricsCollector.class); // GH-90000
        DataCloudHttpMetrics metrics = new DataCloudHttpMetrics(collector); // GH-90000

        metrics.recordError("AnalyticsHandler", "handleAnalyticsQuery", "IllegalStateException"); // GH-90000

        verify(collector).incrementCounter( // GH-90000
                DataCloudHttpMetrics.METRIC_ERRORS,
                DataCloudHttpMetrics.TAG_HANDLER, "AnalyticsHandler",
                DataCloudHttpMetrics.TAG_OPERATION, "handleAnalyticsQuery",
                DataCloudHttpMetrics.TAG_ERROR_TYPE, "IllegalStateException"
        );
        verifyNoMoreInteractions(collector); // GH-90000
    }

    @Test
    void metricsRecordingShouldNeverThrowIfCollectorFails() { // GH-90000
        MetricsCollector collector = new MetricsCollector() { // GH-90000
            @Override
            public void increment(String metricName, double amount, java.util.Map<String, String> tags) { // GH-90000
                throw new RuntimeException("boom");
            }

            @Override
            public void recordError(String metricName, Exception e, java.util.Map<String, String> tags) { // GH-90000
                throw new RuntimeException("boom");
            }

            @Override
            public void incrementCounter(String metricName, String... keyValues) { // GH-90000
                throw new RuntimeException("boom");
            }

            @Override
            public io.micrometer.core.instrument.MeterRegistry getMeterRegistry() { // GH-90000
                return new SimpleMeterRegistry(); // GH-90000
            }

            @Override
            public void recordTimer(String name, long durationMs, String... keyValues) { // GH-90000
                throw new RuntimeException("boom");
            }
        };
        DataCloudHttpMetrics metrics = new DataCloudHttpMetrics(collector); // GH-90000

        metrics.recordRequest("H", "op", "tenant", 500); // GH-90000
        metrics.recordLatency("H", "op", 123L); // GH-90000
        metrics.recordError("H", "op", "RuntimeException"); // GH-90000
    }
}

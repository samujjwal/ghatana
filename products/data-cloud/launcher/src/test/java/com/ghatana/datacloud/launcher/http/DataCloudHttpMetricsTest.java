package com.ghatana.datacloud.launcher.http;

import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DataCloudHttpMetricsTest {

    @Test
    void recordRequestShouldEmitStandardizedRequestMetricWithTenantAndStatusTags() { 
        MetricsCollector collector = mock(MetricsCollector.class); 
        DataCloudHttpMetrics metrics = new DataCloudHttpMetrics(collector); 

        metrics.recordRequest("AnalyticsHandler", "handleAnalyticsQuery", "tenant-a", 200); 

        verify(collector).incrementCounter( 
                DataCloudHttpMetrics.METRIC_REQUESTS,
                DataCloudHttpMetrics.TAG_HANDLER, "AnalyticsHandler",
                DataCloudHttpMetrics.TAG_OPERATION, "handleAnalyticsQuery",
                DataCloudHttpMetrics.TAG_TENANT, "tenant-a",
                DataCloudHttpMetrics.TAG_STATUS, "200"
        );
        verifyNoMoreInteractions(collector); 
    }

    @Test
    void recordLatencyShouldEmitStandardizedLatencyMetric() { 
        MetricsCollector collector = mock(MetricsCollector.class); 
        DataCloudHttpMetrics metrics = new DataCloudHttpMetrics(collector); 

        metrics.recordLatency("AiModelHandler", "handleListAiModels", 42L); 

        verify(collector).recordTimer( 
                DataCloudHttpMetrics.METRIC_LATENCY,
                42L,
                DataCloudHttpMetrics.TAG_HANDLER, "AiModelHandler",
                DataCloudHttpMetrics.TAG_OPERATION, "handleListAiModels"
        );
        verifyNoMoreInteractions(collector); 
    }

    @Test
    void recordErrorShouldEmitStandardizedErrorMetric() { 
        MetricsCollector collector = mock(MetricsCollector.class); 
        DataCloudHttpMetrics metrics = new DataCloudHttpMetrics(collector); 

        metrics.recordError("AnalyticsHandler", "handleAnalyticsQuery", "IllegalStateException"); 

        verify(collector).incrementCounter( 
                DataCloudHttpMetrics.METRIC_ERRORS,
                DataCloudHttpMetrics.TAG_HANDLER, "AnalyticsHandler",
                DataCloudHttpMetrics.TAG_OPERATION, "handleAnalyticsQuery",
                DataCloudHttpMetrics.TAG_ERROR_TYPE, "IllegalStateException"
        );
        verifyNoMoreInteractions(collector); 
    }

    @Test
    void metricsRecordingShouldNeverThrowIfCollectorFails() { 
        MetricsCollector collector = new MetricsCollector() { 
            @Override
            public void increment(String metricName, double amount, java.util.Map<String, String> tags) { 
                throw new RuntimeException("boom");
            }

            @Override
            public void recordError(String metricName, Exception e, java.util.Map<String, String> tags) { 
                throw new RuntimeException("boom");
            }

            @Override
            public void incrementCounter(String metricName, String... keyValues) { 
                throw new RuntimeException("boom");
            }

            @Override
            public io.micrometer.core.instrument.MeterRegistry getMeterRegistry() { 
                return new SimpleMeterRegistry(); 
            }

            @Override
            public void recordTimer(String name, long durationMs, String... keyValues) { 
                throw new RuntimeException("boom");
            }
        };
        DataCloudHttpMetrics metrics = new DataCloudHttpMetrics(collector); 

        metrics.recordRequest("H", "op", "tenant", 500); 
        metrics.recordLatency("H", "op", 123L); 
        metrics.recordError("H", "op", "RuntimeException"); 
    }

    @Test
    void noopReturnsSingletonInstance() {
        DataCloudHttpMetrics noop1 = DataCloudHttpMetrics.noop();
        DataCloudHttpMetrics noop2 = DataCloudHttpMetrics.noop();
        assertThat(noop1).isSameAs(noop2);
    }

    @Test
    void noopDoesNotThrowOnRecordOperations() {
        DataCloudHttpMetrics noop = DataCloudHttpMetrics.noop();
        
        // Should not throw even though metrics collector is null
        noop.recordRequest("Handler", "operation", "tenant", 200);
        noop.recordLatency("Handler", "operation", 100L);
        noop.recordError("Handler", "operation", "error");
        noop.recordError("Handler", "operation", new RuntimeException("test"));
    }

    @Test
    void recordErrorWithThrowableExtractsSimpleName() {
        MetricsCollector collector = mock(MetricsCollector.class);
        DataCloudHttpMetrics metrics = new DataCloudHttpMetrics(collector);

        metrics.recordError("Handler", "operation", new IllegalStateException("test"));

        verify(collector).incrementCounter(
                DataCloudHttpMetrics.METRIC_ERRORS,
                DataCloudHttpMetrics.TAG_HANDLER, "Handler",
                DataCloudHttpMetrics.TAG_OPERATION, "operation",
                DataCloudHttpMetrics.TAG_ERROR_TYPE, "IllegalStateException"
        );
    }

    @Test
    void recordErrorWithNullThrowableUsesUnknown() {
        MetricsCollector collector = mock(MetricsCollector.class);
        DataCloudHttpMetrics metrics = new DataCloudHttpMetrics(collector);

        metrics.recordError("Handler", "operation", (Throwable) null);

        verify(collector).incrementCounter(
                DataCloudHttpMetrics.METRIC_ERRORS,
                DataCloudHttpMetrics.TAG_HANDLER, "Handler",
                DataCloudHttpMetrics.TAG_OPERATION, "operation",
                DataCloudHttpMetrics.TAG_ERROR_TYPE, "Unknown"
        );
    }

    @Test
    void recordRequestWithNullValuesUsesUnknown() {
        MetricsCollector collector = mock(MetricsCollector.class);
        DataCloudHttpMetrics metrics = new DataCloudHttpMetrics(collector);

        metrics.recordRequest(null, null, null, 200);

        verify(collector).incrementCounter(
                DataCloudHttpMetrics.METRIC_REQUESTS,
                DataCloudHttpMetrics.TAG_HANDLER, "unknown",
                DataCloudHttpMetrics.TAG_OPERATION, "unknown",
                DataCloudHttpMetrics.TAG_TENANT, "unknown",
                DataCloudHttpMetrics.TAG_STATUS, "200"
        );
    }

    @Test
    void recordRequestWithBlankValuesUsesUnknown() {
        MetricsCollector collector = mock(MetricsCollector.class);
        DataCloudHttpMetrics metrics = new DataCloudHttpMetrics(collector);

        metrics.recordRequest("  ", "  ", "  ", 200);

        verify(collector).incrementCounter(
                DataCloudHttpMetrics.METRIC_REQUESTS,
                DataCloudHttpMetrics.TAG_HANDLER, "unknown",
                DataCloudHttpMetrics.TAG_OPERATION, "unknown",
                DataCloudHttpMetrics.TAG_TENANT, "unknown",
                DataCloudHttpMetrics.TAG_STATUS, "200"
        );
    }
}

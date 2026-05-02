package com.ghatana.datacloud.launcher.http;

import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

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
}

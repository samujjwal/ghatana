package com.ghatana.core.pipeline;

import com.ghatana.platform.observability.MetricsCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @doc.type class
 * @doc.purpose Verifies AEP pipeline metrics facade emits the correct metric names and tags
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AepPipelineMetrics")
class AepPipelineMetricsTest {

    @Mock
    private MetricsCollector collector;

    @Test
    @DisplayName("recordStarted emits pipeline started counter with correct tags")
    void recordStartedShouldEmitStartedCounterWithPipelineAndTenantTags() { // GH-90000
        AepPipelineMetrics metrics = AepPipelineMetrics.of(collector); // GH-90000

        metrics.recordStarted("pipe-42", "tenant-a"); // GH-90000

        verify(collector).incrementCounter( // GH-90000
                AepPipelineMetrics.METRIC_STARTED,
                AepPipelineMetrics.TAG_PIPELINE_ID, "pipe-42",
                AepPipelineMetrics.TAG_TENANT_ID,   "tenant-a"
        );
        verifyNoMoreInteractions(collector); // GH-90000
    }

    @Test
    @DisplayName("recordSucceeded emits succeeded counter, latency timer, and per-stage counters")
    void recordSucceededShouldEmitSucceededCounterLatencyAndStageCounters() { // GH-90000
        AepPipelineMetrics metrics = AepPipelineMetrics.of(collector); // GH-90000

        metrics.recordSucceeded("pipe-42", "tenant-a", 150L, 2); // GH-90000

        verify(collector).incrementCounter( // GH-90000
                AepPipelineMetrics.METRIC_SUCCEEDED,
                AepPipelineMetrics.TAG_PIPELINE_ID, "pipe-42",
                AepPipelineMetrics.TAG_TENANT_ID,   "tenant-a"
        );
        verify(collector).recordTimer( // GH-90000
                AepPipelineMetrics.METRIC_LATENCY, 150L,
                AepPipelineMetrics.TAG_PIPELINE_ID, "pipe-42",
                AepPipelineMetrics.TAG_TENANT_ID,   "tenant-a"
        );
        verify(collector, times(2)).incrementCounter( // GH-90000
                AepPipelineMetrics.METRIC_STAGES,
                AepPipelineMetrics.TAG_PIPELINE_ID, "pipe-42",
                AepPipelineMetrics.TAG_TENANT_ID,   "tenant-a"
        );
    }

    @Test
    @DisplayName("recordFailed emits failed counter and latency timer with correct tags")
    void recordFailedShouldEmitFailedCounterAndLatencyTimer() { // GH-90000
        AepPipelineMetrics metrics = AepPipelineMetrics.of(collector); // GH-90000

        metrics.recordFailed("pipe-99", "tenant-b", 75L); // GH-90000

        verify(collector).incrementCounter( // GH-90000
                AepPipelineMetrics.METRIC_FAILED,
                AepPipelineMetrics.TAG_PIPELINE_ID, "pipe-99",
                AepPipelineMetrics.TAG_TENANT_ID,   "tenant-b"
        );
        verify(collector).recordTimer( // GH-90000
                AepPipelineMetrics.METRIC_LATENCY, 75L,
                AepPipelineMetrics.TAG_PIPELINE_ID, "pipe-99",
                AepPipelineMetrics.TAG_TENANT_ID,   "tenant-b"
        );
        verifyNoMoreInteractions(collector); // GH-90000
    }

    @Test
    @DisplayName("noop does not emit any metrics")
    void noopShouldNeverCallCollector() { // GH-90000
        AepPipelineMetrics noop = AepPipelineMetrics.noop(); // GH-90000

        noop.recordStarted("pipe-1", "tenant-x"); // GH-90000
        noop.recordSucceeded("pipe-1", "tenant-x", 100L, 3); // GH-90000
        noop.recordFailed("pipe-1", "tenant-x", 50L); // GH-90000

        verifyNoInteractions(collector); // GH-90000
    }

    @Test
    @DisplayName("recordStarted with null values uses 'unknown' tag fallback and does not throw")
    void recordStartedShouldUseUnknownFallbackForNullValues() { // GH-90000
        AepPipelineMetrics metrics = AepPipelineMetrics.of(collector); // GH-90000

        assertThatCode(() -> metrics.recordStarted(null, null)).doesNotThrowAnyException(); // GH-90000

        verify(collector).incrementCounter( // GH-90000
                AepPipelineMetrics.METRIC_STARTED,
                AepPipelineMetrics.TAG_PIPELINE_ID, "unknown",
                AepPipelineMetrics.TAG_TENANT_ID,   "unknown"
        );
    }

    @Test
    @DisplayName("never throws even when the collector itself throws")
    void shouldNeverPropagateExceptionsFromCollector() { // GH-90000
        doThrow(new RuntimeException("collector failure"))
                .when(collector) // GH-90000
                .incrementCounter(anyString(), any(String[].class)); // GH-90000

        AepPipelineMetrics metrics = AepPipelineMetrics.of(collector); // GH-90000

        assertThatCode(() -> metrics.recordStarted("pipe-1", "tenant-a")).doesNotThrowAnyException(); // GH-90000
        assertThatCode(() -> metrics.recordFailed("pipe-1", "tenant-a", 10L)).doesNotThrowAnyException(); // GH-90000
    }
}

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
@ExtendWith(MockitoExtension.class)
@DisplayName("AepPipelineMetrics")
class AepPipelineMetricsTest {

    @Mock
    private MetricsCollector collector;

    @Test
    @DisplayName("recordStarted emits pipeline started counter with correct tags")
    void recordStartedShouldEmitStartedCounterWithPipelineAndTenantTags() {
        AepPipelineMetrics metrics = AepPipelineMetrics.of(collector);

        metrics.recordStarted("pipe-42", "tenant-a");

        verify(collector).incrementCounter(
                AepPipelineMetrics.METRIC_STARTED,
                AepPipelineMetrics.TAG_PIPELINE_ID, "pipe-42",
                AepPipelineMetrics.TAG_TENANT_ID,   "tenant-a"
        );
        verifyNoMoreInteractions(collector);
    }

    @Test
    @DisplayName("recordSucceeded emits succeeded counter, latency timer, and per-stage counters")
    void recordSucceededShouldEmitSucceededCounterLatencyAndStageCounters() {
        AepPipelineMetrics metrics = AepPipelineMetrics.of(collector);

        metrics.recordSucceeded("pipe-42", "tenant-a", 150L, 2);

        verify(collector).incrementCounter(
                AepPipelineMetrics.METRIC_SUCCEEDED,
                AepPipelineMetrics.TAG_PIPELINE_ID, "pipe-42",
                AepPipelineMetrics.TAG_TENANT_ID,   "tenant-a"
        );
        verify(collector).recordTimer(
                AepPipelineMetrics.METRIC_LATENCY, 150L,
                AepPipelineMetrics.TAG_PIPELINE_ID, "pipe-42",
                AepPipelineMetrics.TAG_TENANT_ID,   "tenant-a"
        );
        verify(collector, times(2)).incrementCounter(
                AepPipelineMetrics.METRIC_STAGES,
                AepPipelineMetrics.TAG_PIPELINE_ID, "pipe-42",
                AepPipelineMetrics.TAG_TENANT_ID,   "tenant-a"
        );
    }

    @Test
    @DisplayName("recordFailed emits failed counter and latency timer with correct tags")
    void recordFailedShouldEmitFailedCounterAndLatencyTimer() {
        AepPipelineMetrics metrics = AepPipelineMetrics.of(collector);

        metrics.recordFailed("pipe-99", "tenant-b", 75L);

        verify(collector).incrementCounter(
                AepPipelineMetrics.METRIC_FAILED,
                AepPipelineMetrics.TAG_PIPELINE_ID, "pipe-99",
                AepPipelineMetrics.TAG_TENANT_ID,   "tenant-b"
        );
        verify(collector).recordTimer(
                AepPipelineMetrics.METRIC_LATENCY, 75L,
                AepPipelineMetrics.TAG_PIPELINE_ID, "pipe-99",
                AepPipelineMetrics.TAG_TENANT_ID,   "tenant-b"
        );
        verifyNoMoreInteractions(collector);
    }

    @Test
    @DisplayName("noop does not emit any metrics")
    void noopShouldNeverCallCollector() {
        AepPipelineMetrics noop = AepPipelineMetrics.noop();

        noop.recordStarted("pipe-1", "tenant-x");
        noop.recordSucceeded("pipe-1", "tenant-x", 100L, 3);
        noop.recordFailed("pipe-1", "tenant-x", 50L);

        verifyNoInteractions(collector);
    }

    @Test
    @DisplayName("recordStarted with null values uses 'unknown' tag fallback and does not throw")
    void recordStartedShouldUseUnknownFallbackForNullValues() {
        AepPipelineMetrics metrics = AepPipelineMetrics.of(collector);

        assertThatCode(() -> metrics.recordStarted(null, null)).doesNotThrowAnyException();

        verify(collector).incrementCounter(
                AepPipelineMetrics.METRIC_STARTED,
                AepPipelineMetrics.TAG_PIPELINE_ID, "unknown",
                AepPipelineMetrics.TAG_TENANT_ID,   "unknown"
        );
    }

    @Test
    @DisplayName("never throws even when the collector itself throws")
    void shouldNeverPropagateExceptionsFromCollector() {
        doThrow(new RuntimeException("collector failure"))
                .when(collector)
                .incrementCounter(anyString(), any(String[].class));

        AepPipelineMetrics metrics = AepPipelineMetrics.of(collector);

        assertThatCode(() -> metrics.recordStarted("pipe-1", "tenant-a")).doesNotThrowAnyException();
        assertThatCode(() -> metrics.recordFailed("pipe-1", "tenant-a", 10L)).doesNotThrowAnyException();
    }
}

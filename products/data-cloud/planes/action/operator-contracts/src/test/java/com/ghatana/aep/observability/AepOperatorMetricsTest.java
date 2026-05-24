package com.ghatana.aep.observability;

import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.platform.observability.MetricsCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AepOperatorMetricsTest {

    @Mock
    private MetricsCollector collector;

    @Test
    void emitsOperatorProcessedWithCanonicalTags() {
        AepOperatorMetrics metrics = AepOperatorMetrics.of(collector);

        metrics.recordOperatorProcessed("tenant-a", "op-1", OperatorKind.AGENT_PREDICATE, true);

        verify(collector).incrementCounter(
            AepOperatorMetrics.OPERATOR_PROCESSED_TOTAL,
            AepOperatorMetrics.TAG_TENANT_ID, "tenant-a",
            AepOperatorMetrics.TAG_OPERATOR_ID, "op-1",
            AepOperatorMetrics.TAG_OPERATOR_KIND, "AGENT_PREDICATE",
            AepOperatorMetrics.TAG_STATUS, "success");
    }

    @Test
    void emitsPatternAndAgentMetrics() {
        AepOperatorMetrics metrics = AepOperatorMetrics.of(collector);

        metrics.recordPatternMatch("tenant-a", "pattern-1", 0.82);
        metrics.recordAgentInvocation("tenant-a", "op-1", "agents/sre-risk@1.0.0");
        metrics.recordAgentReplayMode("tenant-a", "op-1", "RECORDED_OUTPUT");

        verify(collector).incrementCounter(
            AepOperatorMetrics.PATTERN_MATCHES_TOTAL,
            AepOperatorMetrics.TAG_TENANT_ID, "tenant-a",
            AepOperatorMetrics.TAG_PATTERN_ID, "pattern-1");
        verify(collector).recordTimer(
            AepOperatorMetrics.PATTERN_CONFIDENCE,
            820L,
            AepOperatorMetrics.TAG_TENANT_ID, "tenant-a",
            AepOperatorMetrics.TAG_PATTERN_ID, "pattern-1");
        verify(collector).incrementCounter(
            AepOperatorMetrics.AGENT_INVOCATIONS_TOTAL,
            AepOperatorMetrics.TAG_TENANT_ID, "tenant-a",
            AepOperatorMetrics.TAG_OPERATOR_ID, "op-1",
            AepOperatorMetrics.TAG_AGENT_REF, "agents/sre-risk@1.0.0");
        verify(collector).incrementCounter(
            AepOperatorMetrics.AGENT_REPLAY_MODE,
            AepOperatorMetrics.TAG_TENANT_ID, "tenant-a",
            AepOperatorMetrics.TAG_OPERATOR_ID, "op-1",
            AepOperatorMetrics.TAG_REPLAY_MODE, "RECORDED_OUTPUT");
    }

    @Test
    void emitsEventCloudMetrics() {
        AepOperatorMetrics metrics = AepOperatorMetrics.of(collector);

        metrics.recordEventCloudLag("tenant-a", "deploy.started", 42L);
        metrics.recordEventCloudDlq("tenant-a", "deploy.started");

        verify(collector).recordTimer(
            AepOperatorMetrics.EVENTCLOUD_LAG,
            42L,
            AepOperatorMetrics.TAG_TENANT_ID, "tenant-a",
            AepOperatorMetrics.TAG_EVENT_TYPE, "deploy.started");
        verify(collector).incrementCounter(
            AepOperatorMetrics.EVENTCLOUD_DLQ_TOTAL,
            AepOperatorMetrics.TAG_TENANT_ID, "tenant-a",
            AepOperatorMetrics.TAG_EVENT_TYPE, "deploy.started");
    }

    @Test
    void noopAndCollectorFailuresNeverThrow() {
        AepOperatorMetrics.noop().recordOperatorProcessed("tenant-a", "op-1", OperatorKind.SEQ, true);
        verifyNoInteractions(collector);

        doThrow(new RuntimeException("collector failed"))
            .when(collector)
            .incrementCounter(anyString(), any(String[].class));

        AepOperatorMetrics metrics = AepOperatorMetrics.of(collector);

        assertThatCode(() -> metrics.recordEventIngested("tenant-a", "deploy.started"))
            .doesNotThrowAnyException();
    }
}

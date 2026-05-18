/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.audit;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentLifecycleActionTraceRecorder")
class AgentLifecycleActionTraceRecorderTest extends EventloopTestBase {

    private HashChainedTraceAppender ledger;
    private AgentLifecycleActionTraceRecorder recorder;

    @Override
    protected Duration eventloopTimeout() {
        return Duration.ofSeconds(15);
    }

    @BeforeEach
    void setUp() {
        ledger = new HashChainedTraceAppender();
        recorder = new AgentLifecycleActionTraceRecorder(ledger);
    }

    @Test
    @DisplayName("records lifecycle action evidence with correlation ID")
    void recordsLifecycleActionEvidenceWithCorrelationId() {
        TraceEvent event = runPromise(() -> recorder.record(
                baseRecord(AgentLifecycleActionOutcome.ACCEPTED, null),
                ledger.getLastHash("tenant-1")));

        List<TraceEvent> events = runPromise(() -> ledger.getByTrace("corr-agent-1", "tenant-1"));

        assertThat(event.eventType()).isEqualTo(TraceEventType.ACTION_EXECUTED);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().traceId()).isEqualTo("corr-agent-1");
        assertThat(events.getFirst().payload())
                .containsEntry("agentVersion", "2026.05.0")
                .containsEntry("policyDecision", "allowed")
                .containsEntry("verificationProofRefs", "verification:health:1");
    }

    @Test
    @DisplayName("records policy denial as observable ACTION_DENIED event")
    void recordsPolicyDenialAsObservableActionDeniedEvent() {
        runPromise(() -> recorder.record(
                baseRecord(AgentLifecycleActionOutcome.POLICY_DENIED, "tool-not-allowed"),
                ledger.getLastHash("tenant-1")));

        List<TraceEvent> denied = runPromise(
                () -> ledger.getByType(TraceEventType.ACTION_DENIED, "tenant-1", null, null, 10));

        assertThat(denied).hasSize(1);
        assertThat(denied.getFirst().payload())
                .containsEntry("reasonCode", "tool-not-allowed")
                .containsEntry("outcome", "POLICY_DENIED");
    }

    @Test
    @DisplayName("records fallback mode after governed action evaluation")
    void recordsFallbackModeAfterGovernedActionEvaluation() {
        runPromise(() -> recorder.record(
                baseRecord(AgentLifecycleActionOutcome.ACCEPTED, null),
                ledger.getLastHash("tenant-1")));
        runPromise(() -> recorder.record(
                baseRecord(AgentLifecycleActionOutcome.FALLBACK_RECORDED, "rollback"),
                ledger.getLastHash("tenant-1")));

        List<TraceEvent> events = runPromise(() -> ledger.getByTrace("corr-agent-1", "tenant-1"));

        assertThat(events).extracting(TraceEvent::eventType)
                .containsExactly(TraceEventType.ACTION_EXECUTED, TraceEventType.VERIFICATION_CHECKED);
        assertThat(events.getLast().payload())
                .containsEntry("fallbackMode", "rollback")
                .containsEntry("reasonCode", "rollback");
        assertThat(ledger.verifyChain(events)).isTrue();
    }

    private static AgentLifecycleActionTraceRecord baseRecord(
            AgentLifecycleActionOutcome outcome,
            String reasonCode) {
        return new AgentLifecycleActionTraceRecord(
                "corr-agent-1",
                "agent-request-1",
                "digital-marketing",
                "tenant-1",
                "workspace-1",
                "project-1",
                "agent:release-reviewer",
                "2026.05.0",
                "execute-lifecycle-phase",
                "mastered",
                outcome == AgentLifecycleActionOutcome.POLICY_DENIED ? "denied" : "allowed",
                List.of("kernel.lifecycle.execute-phase"),
                false,
                "medium",
                List.of("input:product-unit-intent:1"),
                List.of("output:lifecycle-run:1"),
                List.of("verification:health:1"),
                List.of("evidence:policy:1"),
                "rollback-plan:run-1",
                "rollback",
                outcome,
                reasonCode);
    }
}

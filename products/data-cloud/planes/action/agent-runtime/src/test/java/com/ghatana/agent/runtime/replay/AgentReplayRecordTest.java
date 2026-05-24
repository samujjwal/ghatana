package com.ghatana.agent.runtime.replay;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentReplayRecordTest {

    @Test
    void recordedOutputReplayUsesCapturedAgentOutput() {
        AgentExecutionRecord record = executionRecord(AgentReplayPolicy.recordedOutput(), List.of());

        AgentReplayDecision decision = AgentReplayPlanner.decide(record);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.mode()).isEqualTo(AgentReplayMode.RECORDED_OUTPUT);
        assertThat(record.hasRecordedOutput()).isTrue();
    }

    @Test
    void liveModelReplayRequiresExplicitOptIn() {
        assertThatThrownBy(() ->
            new AgentReplayPolicy(AgentReplayMode.LIVE_MODEL_OPT_IN, false, "missing approval"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("explicit opt-in");
    }

    @Test
    void mutatingToolCallRequiresIdempotencyKey() {
        assertThatThrownBy(() -> new AgentToolCallRecord(
            "tool-call-1",
            "pagerduty.incident.create",
            "",
            true,
            "request-hash",
            "response-hash",
            "SUCCESS",
            Instant.parse("2026-05-23T00:00:00Z"),
            Instant.parse("2026-05-23T00:00:01Z"),
            Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("idempotencyKey");
    }

    @Test
    void executionRecordRequiresVersionedModelPromptAndRetrievalSnapshots() {
        assertThatThrownBy(() -> new AgentExecutionRecord(
            "execution-1",
            "agents/sre-risk-assessor@1.0.0",
            "tenant-a",
            "correlation-1",
            "operator-1",
            "",
            promptSnapshot(),
            retrievalSnapshot(),
            List.of(),
            outputRecord(),
            AgentReplayPolicy.recordedOutput(),
            Instant.parse("2026-05-23T00:00:00Z"),
            Instant.parse("2026-05-23T00:00:02Z"),
            Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("modelRef");
    }

    @Test
    void outputConfidenceMustBeCalibratedRange() {
        assertThatThrownBy(() -> new AgentOutputRecord(
            "RiskDecision",
            "output-hash",
            Map.of("riskScore", 0.91),
            1.2,
            0.8,
            List.of("event-1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("modelConfidence");
    }

    private static AgentExecutionRecord executionRecord(
            AgentReplayPolicy policy,
            List<AgentToolCallRecord> toolCalls) {
        return new AgentExecutionRecord(
            "execution-1",
            "agents/sre-risk-assessor@1.0.0",
            "tenant-a",
            "correlation-1",
            "operator-1",
            "openai/gpt-5.1@2026-05-01",
            promptSnapshot(),
            retrievalSnapshot(),
            toolCalls,
            outputRecord(),
            policy,
            Instant.parse("2026-05-23T00:00:00Z"),
            Instant.parse("2026-05-23T00:00:02Z"),
            Map.of("patternId", "patterns/risky-deploy@1.0.0"));
    }

    private static AgentPromptSnapshot promptSnapshot() {
        return new AgentPromptSnapshot(
            "prompt-hash",
            "system-prompt-hash",
            "sre-risk-template@1.0.0",
            "rendered-prompt-hash",
            Map.of("tenant", "tenant-a"));
    }

    private static AgentRetrievalSnapshot retrievalSnapshot() {
        return new AgentRetrievalSnapshot(
            "retrieval-hash",
            "hybrid-retriever@1.0.0",
            List.of("runbooks/deploy-risk"),
            List.of("event-1", "event-2"),
            Map.of("topK", "8"));
    }

    private static AgentOutputRecord outputRecord() {
        return new AgentOutputRecord(
            "RiskDecision",
            "output-hash",
            Map.of("decision", "HIGH_RISK", "riskScore", 0.91),
            0.86,
            0.81,
            List.of("event-1", "event-2"));
    }
}

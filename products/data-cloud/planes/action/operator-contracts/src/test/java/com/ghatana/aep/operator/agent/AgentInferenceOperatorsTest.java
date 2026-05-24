package com.ghatana.aep.operator.agent;

import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.model.EventTimeContext;
import com.ghatana.aep.model.ReplayContext;
import com.ghatana.aep.model.UncertaintyContext;
import com.ghatana.aep.operator.contract.EventOperator;
import com.ghatana.aep.operator.contract.EventOperatorResult;
import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.aep.operator.contract.OperatorRuntimeContext;
import com.ghatana.aep.operator.contract.OperatorSpec;
import com.ghatana.aep.operator.contract.ValidationResult;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInferenceOperatorsTest extends EventloopTestBase {

    @Test
    void enrichmentOperatorEmitsTypedDerivedOutputWithConfidence() {
        EventOperator<Map<String, Object>, Map<String, Object>> operator = new AgentEnrichmentOperator(
            operatorId("enrich"),
            "agents/enrich@1.0.0",
            "MatchContext",
            "DerivedEvent",
            request -> Promise.of(Map.of(
                "derivedEventType", "incident.semantic_context_added",
                "serviceTier", "critical",
                "modelConfidence", 0.84,
                "retrievalConfidence", 0.79)));

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> operator.process(
            eventContext(Map.of()),
            runtimeContext(Map.of("retrievalPolicy", Map.of("index", "runbooks")))));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).hasValueSatisfying(output ->
            assertThat(output).containsEntry("serviceTier", "critical"));
        assertThat(result.uncertainty().modelConfidence()).isEqualTo(0.84);
        assertThat(result.uncertainty().retrievalConfidence()).isEqualTo(0.79);
    }

    @Test
    void patternSynthesisMustEmitSuggestedPatternAndNeverAutoActivate() {
        AgentPatternSynthesisOperator operator = new AgentPatternSynthesisOperator(
            operatorId("synthesis"),
            "agents/synthesis@1.0.0",
            "EventGroups",
            "PatternSuggestion",
            request -> Promise.of(Map.of("eventType", "pattern.suggested")));

        ValidationResult missingSuggestedEvent = operator.validate(spec(
            OperatorKind.AGENT_PATTERN_SYNTHESIS,
            Map.of(),
            Map.of()), null);
        ValidationResult autoActivation = operator.validate(spec(
            OperatorKind.AGENT_PATTERN_SYNTHESIS,
            Map.of("emits", "pattern.suggested"),
            Map.of("autoActivate", true)), null);
        ValidationResult valid = operator.validate(spec(
            OperatorKind.AGENT_PATTERN_SYNTHESIS,
            Map.of("emits", "pattern.suggested"),
            Map.of()), null);

        assertThat(missingSuggestedEvent.errors()).contains("AGENT_PATTERN_SYNTHESIS must emit pattern.suggested");
        assertThat(autoActivation.errors()).contains("AGENT_PATTERN_SYNTHESIS must not auto-activate patterns");
        assertThat(valid.valid()).isTrue();
    }

    @Test
    void reviewOperatorCannotSelfApproveHighRiskProductionChanges() {
        AgentReviewOperator operator = new AgentReviewOperator(
            operatorId("review"),
            "agents/review@1.0.0",
            "ReviewPacket",
            "ReviewRecommendation",
            request -> Promise.of(Map.of("recommendation", "REQUEST_HUMAN_REVIEW")));

        ValidationResult validation = operator.validate(spec(
            OperatorKind.AGENT_REVIEW,
            Map.of(),
            Map.of("canSelfApproveHighRisk", true)), null);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.errors()).contains("AGENT_REVIEW must not self-approve high-risk production changes");
    }

    @Test
    void recordedReplayBypassesAgentInvocationForInferenceOperators() {
        AgentExplanationOperator operator = new AgentExplanationOperator(
            operatorId("explain"),
            "agents/explain@1.0.0",
            "PatternMatch",
            "PatternExplanation",
            request -> {
                throw new AssertionError("agent should not be invoked during recorded-output replay");
            });

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> operator.process(
            eventContext(Map.of(operator.id().toString(), Map.of(
                "eventType", "pattern.explanation_generated",
                "explanation", "Recorded explanation",
                "confidence", 0.72))),
            runtimeContext(Map.of())));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).hasValueSatisfying(output ->
            assertThat(output).containsEntry("explanation", "Recorded explanation"));
        assertThat(result.uncertainty().modelConfidence()).isEqualTo(0.72);
    }

    private static OperatorId operatorId(String name) {
        return OperatorId.of("tenant-a", "agent", name, "1.0.0");
    }

    private static OperatorSpec spec(
            OperatorKind kind,
            Map<String, Object> parameters,
            Map<String, Object> policies) {
        return new OperatorSpec(
            "tenant-a:agent:test:1.0.0",
            kind,
            "InputSchema",
            "OutputSchema",
            parameters,
            policies);
    }

    private static OperatorRuntimeContext runtimeContext(Map<String, Object> policies) {
        return new OperatorRuntimeContext(
            "tenant-a",
            Optional.of("trace-1"),
            Optional.of("correlation-1"),
            Map.of(),
            policies);
    }

    private static EventContext<Map<String, Object>> eventContext(Map<String, Object> recordedOutputs) {
        return new EventContext<>(
            "tenant-a",
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            new EventTimeContext(
                EventTimeContext.TimeMode.EVENT_TIME,
                Optional.empty(),
                Duration.ZERO,
                EventTimeContext.LateEventBehavior.INCORPORATE,
                Optional.empty()),
            UncertaintyContext.certain(),
            new ReplayContext(
                recordedOutputs.isEmpty() ? ReplayContext.ReplayMode.LIVE : ReplayContext.ReplayMode.RECORDED_AGENT_OUTPUT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                recordedOutputs),
            Optional.of(Map.of("eventType", "pattern.match")));
    }
}

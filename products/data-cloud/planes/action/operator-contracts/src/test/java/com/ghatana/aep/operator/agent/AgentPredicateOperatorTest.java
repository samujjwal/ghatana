package com.ghatana.aep.operator.agent;

import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.model.EventTimeContext;
import com.ghatana.aep.model.ReplayContext;
import com.ghatana.aep.model.UncertaintyContext;
import com.ghatana.aep.operator.contract.EventOperatorResult;
import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.aep.operator.contract.OperatorRuntimeContext;
import com.ghatana.aep.operator.contract.OperatorSpec;
import com.ghatana.aep.operator.contract.RuntimePlan;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentPredicateOperatorTest extends EventloopTestBase {

    @Test
    void validatesAndCompilesAgentPredicateSpec() {
        AgentPredicateOperator operator = operator(request ->
            Promise.of(Map.of("decision", "HIGH_RISK", "confidence", 0.86)));

        OperatorSpec spec = spec(Map.of());
        ValidationResult validation = operator.validate(spec, null);
        RuntimePlan plan = operator.compile(spec, null);

        assertThat(validation.valid()).isTrue();
        assertThat(plan.operatorIds()).contains(operator.id().toString());
        assertThat(plan.executionHints()).containsEntry("agentRef", "agents/sre-risk-assessor@1.0.0");
    }

    @Test
    void rejectsToolPolicyBecausePredicateIsPureInference() {
        AgentPredicateOperator operator = operator(request ->
            Promise.of(Map.of("decision", "HIGH_RISK", "confidence", 0.86)));

        ValidationResult validation = operator.validate(spec(Map.of("toolPolicy", Map.of())), null);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.errors()).anySatisfy(error -> assertThat(error).contains("toolPolicy"));
    }

    @Test
    void processesTypedAgentDecisionAndPropagatesConfidence() {
        AgentPredicateOperator operator = operator(request ->
            Promise.of(Map.of(
                "decision", "HIGH_RISK",
                "riskScore", 0.91,
                "confidence", 0.86,
                "retrievalConfidence", 0.8)));

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> operator.process(
            eventContext(Map.of()),
            runtimeContext()));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).hasValueSatisfying(output ->
            assertThat(output).containsEntry("decision", "HIGH_RISK"));
        assertThat(result.uncertainty().modelConfidence()).isEqualTo(0.86);
        assertThat(result.uncertainty().retrievalConfidence()).isEqualTo(0.8);
    }

    @Test
    void replayUsesRecordedOutputWithoutInvokingAgent() {
        AgentPredicateOperator operator = operator(request -> {
            throw new AssertionError("agent should not be invoked during recorded-output replay");
        });

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> operator.process(
            eventContext(Map.of(operator.id().toString(), Map.of("decision", "RECORDED", "confidence", 0.77))),
            runtimeContext()));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).hasValueSatisfying(output ->
            assertThat(output).containsEntry("decision", "RECORDED"));
        assertThat(result.uncertainty().patternConfidence()).isEqualTo(0.77);
    }

    @Test
    void constructorRejectsBlankAgentReference() {
        assertThatThrownBy(() -> new AgentPredicateOperator(
            OperatorId.of("tenant-a", "agent", "risk", "1.0.0"),
            "",
            "RiskContext",
            "RiskDecision",
            request -> Promise.of(Map.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("agentRef");
    }

    private static AgentPredicateOperator operator(AgentInvocationClient client) {
        return new AgentPredicateOperator(
            OperatorId.of("tenant-a", "agent", "sre-risk", "1.0.0"),
            "agents/sre-risk-assessor@1.0.0",
            "RiskContext",
            "RiskDecision",
            client);
    }

    private static OperatorSpec spec(Map<String, Object> policies) {
        return new OperatorSpec(
            "tenant-a:agent:sre-risk:1.0.0",
            OperatorKind.AGENT_PREDICATE,
            "RiskContext",
            "RiskDecision",
            Map.of(),
            policies);
    }

    private static OperatorRuntimeContext runtimeContext() {
        return new OperatorRuntimeContext(
            "tenant-a",
            Optional.of("trace-1"),
            Optional.of("correlation-1"),
            Map.of(),
            Map.of());
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
            Optional.of(Map.of("eventType", "deploy.started")));
    }
}

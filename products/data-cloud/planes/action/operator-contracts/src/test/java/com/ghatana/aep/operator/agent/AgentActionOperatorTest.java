package com.ghatana.aep.operator.agent;

import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.model.EventTimeContext;
import com.ghatana.aep.model.ReplayContext;
import com.ghatana.aep.model.UncertaintyContext;
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

class AgentActionOperatorTest extends EventloopTestBase {

    @Test
    void validatesRequiredGovernancePolicies() {
        AgentActionOperator operator = operator(request ->
            Promise.of(Map.of("status", "EXECUTED")));

        ValidationResult validation = operator.validate(spec(Map.of(
            "toolPolicy", Map.of("allowedTools", List.of("pagerduty.incident.create")),
            "approvalPolicy", Map.of("mode", "human_required"),
            "auditPolicy", Map.of("emitEvents", true))), null);

        assertThat(validation.valid()).isTrue();
    }

    @Test
    void rejectsMissingToolPolicy() {
        AgentActionOperator operator = operator(request ->
            Promise.of(Map.of("status", "EXECUTED")));

        ValidationResult validation = operator.validate(spec(Map.of(
            "approvalPolicy", Map.of("mode", "human_required"),
            "auditPolicy", Map.of("emitEvents", true))), null);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.errors()).contains("AGENT_ACTION requires toolPolicy");
    }

    @Test
    void runtimeApprovalIsRequiredBeforeInvocation() {
        AgentActionOperator operator = operator(request -> {
            throw new AssertionError("agent action should not be invoked without approval");
        });

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> operator.process(
            eventContext(),
            runtimeContext(Map.of("idempotencyKey", "action-1"))));

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).contains("AGENT_ACTION requires runtime approval");
    }

    @Test
    void idempotencyKeyIsRequiredForApprovedAction() {
        AgentActionOperator operator = operator(request -> {
            throw new AssertionError("agent action should not be invoked without idempotency key");
        });

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> operator.process(
            eventContext(),
            runtimeContext(Map.of("approved", true))));

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).contains("AGENT_ACTION requires idempotencyKey");
    }

    @Test
    void approvedActionInvokesAgentAndReturnsTypedResult() {
        AgentActionOperator operator = operator(request ->
            Promise.of(Map.of("status", "EXECUTED", "auditEventType", "action.executed")));

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> operator.process(
            eventContext(),
            runtimeContext(Map.of("approved", true, "idempotencyKey", "action-1"))));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).hasValueSatisfying(output ->
            assertThat(output).containsEntry("auditEventType", "action.executed"));
    }

    private static AgentActionOperator operator(AgentInvocationClient client) {
        return new AgentActionOperator(
            OperatorId.of("tenant-a", "agent", "incident-action", "1.0.0"),
            "agents/incident-action@1.0.0",
            "ActionRequest",
            "ActionResult",
            client);
    }

    private static OperatorSpec spec(Map<String, Object> policies) {
        return new OperatorSpec(
            "tenant-a:agent:incident-action:1.0.0",
            OperatorKind.AGENT_ACTION,
            "ActionRequest",
            "ActionResult",
            Map.of(),
            policies);
    }

    private static OperatorRuntimeContext runtimeContext(Map<String, Object> policies) {
        return new OperatorRuntimeContext(
            "tenant-a",
            Optional.of("trace-1"),
            Optional.of("correlation-1"),
            policies,
            Map.of());
    }

    private static EventContext<Map<String, Object>> eventContext() {
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
                ReplayContext.ReplayMode.LIVE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of()),
            Optional.of(Map.of("action", "create_incident")));
    }
}

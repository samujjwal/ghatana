package com.ghatana.aep.operator.agent;

import com.ghatana.aep.model.CanonicalEvent;
import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.operator.contract.CompileContext;
import com.ghatana.aep.operator.contract.EventOperator;
import com.ghatana.aep.operator.contract.EventOperatorResult;
import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.aep.operator.contract.OperatorRuntimeContext;
import com.ghatana.aep.operator.contract.OperatorSpec;
import com.ghatana.aep.operator.contract.OperatorVersion;
import com.ghatana.aep.operator.contract.RuntimePlan;
import com.ghatana.aep.operator.contract.ValidationContext;
import com.ghatana.aep.operator.contract.ValidationResult;
import com.ghatana.core.operator.OperatorId;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Side-effecting agent capability role for approved governed actions.
 *
 * @doc.type class
 * @doc.purpose Implements AGENT_ACTION with mandatory tool, approval, idempotency, audit, and replay controls
 * @doc.layer product
 * @doc.pattern Operator
 */
public final class AgentActionOperator implements EventOperator<Map<String, Object>, Map<String, Object>> {

    private final OperatorId operatorId;
    private final String agentRef;
    private final String inputSchema;
    private final String outputSchema;
    private final AgentInvocationClient invocationClient;

    public AgentActionOperator(
            OperatorId operatorId,
            String agentRef,
            String inputSchema,
            String outputSchema,
            AgentInvocationClient invocationClient) {
        this.operatorId = Objects.requireNonNull(operatorId, "operatorId");
        this.agentRef = requireText(agentRef, "agentRef");
        this.inputSchema = requireText(inputSchema, "inputSchema");
        this.outputSchema = requireText(outputSchema, "outputSchema");
        this.invocationClient = Objects.requireNonNull(invocationClient, "invocationClient");
    }

    @Override
    public OperatorId id() {
        return operatorId;
    }

    @Override
    public OperatorKind kind() {
        return OperatorKind.AGENT_ACTION;
    }

    @Override
    public OperatorVersion version() {
        return new OperatorVersion(operatorId.getVersion());
    }

    @Override
    public ValidationResult validate(OperatorSpec spec, ValidationContext ctx) {
        if (spec.kind() != OperatorKind.AGENT_ACTION) {
            return ValidationResult.invalid(List.of("AgentActionOperator requires AGENT_ACTION spec"));
        }
        if (!hasPolicy(spec.policies(), "toolPolicy")) {
            return ValidationResult.invalid(List.of("AGENT_ACTION requires toolPolicy"));
        }
        if (!hasPolicy(spec.policies(), "approvalPolicy")) {
            return ValidationResult.invalid(List.of("AGENT_ACTION requires approvalPolicy"));
        }
        if (!hasPolicy(spec.policies(), "auditPolicy")) {
            return ValidationResult.invalid(List.of("AGENT_ACTION requires auditPolicy"));
        }
        if (!hasPolicy(spec.policies(), "idempotencyPolicy")) {
            return ValidationResult.invalid(List.of("AGENT_ACTION requires idempotencyPolicy"));
        }
        return ValidationResult.ok();
    }

    @Override
    public RuntimePlan compile(OperatorSpec spec, CompileContext ctx) {
        ValidationResult validation = validate(spec, null);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }
        return new RuntimePlan(
            "agent-action-" + operatorId.getName(),
            List.of(operatorId.toString()),
            Map.of("agentRef", agentRef, "inputSchema", inputSchema, "outputSchema", outputSchema),
            Map.of("operatorKind", kind().name(), "sideEffecting", true));
    }

    @Override
    public Promise<EventOperatorResult<Map<String, Object>>> process(
            EventContext<Map<String, Object>> input,
            OperatorRuntimeContext ctx) {
        Objects.requireNonNull(input, "input");
        Map<String, Object> policies = ctx != null ? ctx.policies() : Map.of();
        if (!Boolean.TRUE.equals(policies.get("approved"))) {
            return Promise.of(failure(input, ctx, policies, "AGENT_ACTION requires runtime approval"));
        }
        if (isBlank(policies.get("idempotencyKey"))) {
            return Promise.of(failure(input, ctx, policies, "AGENT_ACTION requires idempotencyKey"));
        }

        AgentInvocationRequest request = new AgentInvocationRequest(
            operatorId.toString(),
            agentRef,
            outputSchema,
            input,
            policies);
        return invocationClient.invoke(request)
            .map(output -> {
                List<CanonicalEvent> emittedEvents = new ArrayList<>();
                emittedEvents.add(auditEvent("action.requested", input, ctx, policies, Map.of(
                    "operatorId", operatorId.toString(),
                    "agentRef", agentRef)));
                emittedEvents.add(auditEvent(actionEventType(output), input, ctx, policies, Map.of(
                    "operatorId", operatorId.toString(),
                    "agentRef", agentRef,
                    "output", output)));
                return new EventOperatorResult<>(
                    true,
                    Optional.of(output),
                    emittedEvents,
                    input.uncertainty(),
                    Map.of("agentRef", agentRef, "operatorKind", kind().name()),
                    List.of());
            });
    }

    private EventOperatorResult<Map<String, Object>> failure(
            EventContext<Map<String, Object>> input,
            OperatorRuntimeContext ctx,
            Map<String, Object> policies,
            String error) {
        return new EventOperatorResult<>(
            false,
            Optional.empty(),
            List.of(auditEvent("action.failed", input, ctx, policies, Map.of(
                "operatorId", operatorId.toString(),
                "agentRef", agentRef,
                "error", error))),
            input.uncertainty(),
            Map.of("agentRef", agentRef, "operatorKind", kind().name()),
            List.of(error));
    }

    private CanonicalEvent auditEvent(
            String eventType,
            EventContext<Map<String, Object>> input,
            OperatorRuntimeContext ctx,
            Map<String, Object> policies,
            Map<String, Object> payload) {
        String tenantId = ctx != null ? ctx.tenantId() : input.tenantId();
        String correlationId = ctx != null && ctx.correlationId().isPresent()
            ? ctx.correlationId().get()
            : UUID.randomUUID().toString();
        String idempotencyKey = String.valueOf(policies.getOrDefault("idempotencyKey", UUID.randomUUID().toString()));
        return new CanonicalEvent(
            UUID.randomUUID().toString(),
            tenantId,
            eventType,
            "v1",
            Instant.now(),
            Optional.of(Instant.now()),
            Optional.empty(),
            Optional.empty(),
            Map.of("system", "aep", "operatorKind", kind().name()),
            List.of(),
            correlationId,
            Optional.of(operatorId.toString()),
            payload,
            Map.of("modelConfidence", input.uncertainty().modelConfidence()),
            Map.of("agentRef", agentRef, "operatorId", operatorId.toString()),
            List.of("audit", "agent-action"),
            idempotencyKey);
    }

    private static String actionEventType(Map<String, Object> output) {
        Object auditEventType = output.get("auditEventType");
        if (auditEventType != null && !String.valueOf(auditEventType).isBlank()) {
            return String.valueOf(auditEventType);
        }
        Object status = output.get("status");
        if ("FAILED".equals(status)) {
            return "action.failed";
        }
        return "action.executed";
    }

    private static boolean hasPolicy(Map<String, Object> policies, String key) {
        Object value = policies.get(key);
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return !isBlank(value);
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}

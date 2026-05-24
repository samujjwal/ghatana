package com.ghatana.aep.operator.agent;

import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.model.UncertaintyContext;
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

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agent operator that returns a typed decision used as a pattern predicate.
 *
 * @doc.type class
 * @doc.purpose Implements AGENT_PREDICATE as an event-operator capability role
 * @doc.layer product
 * @doc.pattern Operator
 */
public final class AgentPredicateOperator implements EventOperator<Map<String, Object>, Map<String, Object>> {

    private final OperatorId operatorId;
    private final String agentRef;
    private final String inputSchema;
    private final String outputSchema;
    private final AgentInvocationClient invocationClient;

    public AgentPredicateOperator(
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
        return OperatorKind.AGENT_PREDICATE;
    }

    @Override
    public OperatorVersion version() {
        return new OperatorVersion(operatorId.getVersion());
    }

    @Override
    public ValidationResult validate(OperatorSpec spec, ValidationContext ctx) {
        if (spec.kind() != OperatorKind.AGENT_PREDICATE) {
            return ValidationResult.invalid(List.of("AgentPredicateOperator requires AGENT_PREDICATE spec"));
        }
        if (spec.outputSchema().isBlank()) {
            return ValidationResult.invalid(List.of("AGENT_PREDICATE requires outputSchema"));
        }
        if (spec.policies().containsKey("toolPolicy")) {
            return ValidationResult.invalid(List.of("AGENT_PREDICATE must not declare mutating toolPolicy"));
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
            "agent-predicate-" + operatorId.getName(),
            List.of(operatorId.toString()),
            Map.of("agentRef", agentRef, "inputSchema", inputSchema, "outputSchema", outputSchema),
            Map.of("operatorKind", kind().name()));
    }

    @Override
    public Promise<EventOperatorResult<Map<String, Object>>> process(
            EventContext<Map<String, Object>> input,
            OperatorRuntimeContext ctx) {
        Objects.requireNonNull(input, "input");
        Map<String, Object> recordedOutput = recordedOutput(input);
        if (!recordedOutput.isEmpty()) {
            return Promise.of(EventOperatorResult.success(recordedOutput, uncertainty(input, recordedOutput)));
        }

        AgentInvocationRequest request = new AgentInvocationRequest(
            operatorId.toString(),
            agentRef,
            outputSchema,
            input,
            ctx != null ? ctx.policies() : Map.of());
        return invocationClient.invoke(request)
            .map(output -> EventOperatorResult.success(output, uncertainty(input, output)));
    }

    private Map<String, Object> recordedOutput(EventContext<Map<String, Object>> input) {
        Object output = input.replay().recordedOutputs().get(operatorId.toString());
        if (output instanceof Map<?, ?> outputMap) {
            return outputMap.entrySet().stream()
                .filter(entry -> entry.getKey() instanceof String)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                    entry -> (String) entry.getKey(),
                    Map.Entry::getValue));
        }
        return Map.of();
    }

    private UncertaintyContext uncertainty(
            EventContext<Map<String, Object>> input,
            Map<String, Object> output) {
        double confidence = confidence(output, "confidence", input.uncertainty().modelConfidence());
        double retrievalConfidence = confidence(output, "retrievalConfidence", input.uncertainty().retrievalConfidence());
        return new UncertaintyContext(
            input.uncertainty().eventDetectionConfidence(),
            input.uncertainty().attributeConfidence(),
            input.uncertainty().temporalConfidence(),
            input.uncertainty().sourceReliability(),
            confidence,
            confidence,
            retrievalConfidence,
            input.uncertainty().inputCompleteness(),
            input.uncertainty().calibrationScore(),
            Map.of("agentRef", agentRef, "operatorKind", kind().name()));
    }

    private static double confidence(Map<String, Object> output, String key, double fallback) {
        Object value = output.get(key);
        if (value instanceof Number number) {
            return Math.max(0.0, Math.min(1.0, number.doubleValue()));
        }
        return fallback;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}

package com.ghatana.aep.operator.agent;

import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.model.UncertaintyContext;
import com.ghatana.aep.operator.contract.*;
import com.ghatana.core.operator.OperatorId;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

abstract class AbstractAgentInferenceOperator implements EventOperator<Map<String, Object>, Map<String, Object>> {

    private final OperatorId operatorId;
    private final OperatorKind operatorKind;
    private final String agentRef;
    private final String inputSchema;
    private final String outputSchema;
    private final AgentInvocationClient invocationClient;

    AbstractAgentInferenceOperator(
            OperatorId operatorId,
            OperatorKind operatorKind,
            String agentRef,
            String inputSchema,
            String outputSchema,
            AgentInvocationClient invocationClient) {
        this.operatorId = Objects.requireNonNull(operatorId, "operatorId");
        this.operatorKind = Objects.requireNonNull(operatorKind, "operatorKind");
        this.agentRef = requireText(agentRef, "agentRef");
        this.inputSchema = requireText(inputSchema, "inputSchema");
        this.outputSchema = requireText(outputSchema, "outputSchema");
        this.invocationClient = Objects.requireNonNull(invocationClient, "invocationClient");
    }

    @Override
    public final OperatorId id() {
        return operatorId;
    }

    @Override
    public final OperatorKind kind() {
        return operatorKind;
    }

    @Override
    public final OperatorVersion version() {
        return new OperatorVersion(operatorId.getVersion());
    }

    @Override
    public ValidationResult validate(OperatorSpec spec, ValidationContext ctx) {
        List<String> errors = new ArrayList<>();
        if (spec.kind() != operatorKind) {
            errors.add(simpleName() + " requires " + operatorKind + " spec");
        }
        if (spec.outputSchema().isBlank()) {
            errors.add(operatorKind + " requires outputSchema");
        }
        validatePolicies(spec, errors);
        if (errors.isEmpty()) {
            return ValidationResult.ok();
        }
        return ValidationResult.invalid(errors);
    }

    @Override
    public RuntimePlan compile(OperatorSpec spec, CompileContext ctx) {
        ValidationResult validation = validate(spec, null);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }
        return new RuntimePlan(
            operatorKind.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-') + "-" + operatorId.getName(),
            List.of(operatorId.toString()),
            Map.of("agentRef", agentRef, "inputSchema", inputSchema, "outputSchema", outputSchema),
            Map.of("operatorKind", operatorKind.name(), "sideEffecting", false));
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
        Map<String, Object> policies = ctx != null ? ctx.policies() : Map.of();
        AgentInvocationRequest request = new AgentInvocationRequest(
            operatorId.toString(),
            agentRef,
            outputSchema,
            input,
            policies);
        return invocationClient.invoke(request)
            .map(output -> EventOperatorResult.success(output, uncertainty(input, output)));
    }

    protected void validatePolicies(OperatorSpec spec, List<String> errors) {
    }

    protected static boolean hasPolicy(Map<String, Object> policies, String key) {
        Object value = policies.get(key);
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return value != null && !String.valueOf(value).isBlank();
    }

    protected static boolean isTrue(Map<String, Object> policies, String key) {
        return Boolean.TRUE.equals(policies.get(key));
    }

    private Map<String, Object> recordedOutput(EventContext<Map<String, Object>> input) {
        Object output = input.replay().recordedOutputs().get(operatorId.toString());
        if (output instanceof Map<?, ?> outputMap) {
            return outputMap.entrySet().stream()
                .filter(entry -> entry.getKey() instanceof String)
                .collect(Collectors.toUnmodifiableMap(
                    entry -> (String) entry.getKey(),
                    Map.Entry::getValue));
        }
        return Map.of();
    }

    private UncertaintyContext uncertainty(
            EventContext<Map<String, Object>> input,
            Map<String, Object> output) {
        double modelConfidence = confidence(output, "modelConfidence", confidence(output, "confidence", input.uncertainty().modelConfidence()));
        double retrievalConfidence = confidence(output, "retrievalConfidence", input.uncertainty().retrievalConfidence());
        double patternConfidence = confidence(output, "patternConfidence", input.uncertainty().patternConfidence());
        return new UncertaintyContext(
            input.uncertainty().eventDetectionConfidence(),
            input.uncertainty().attributeConfidence(),
            input.uncertainty().temporalConfidence(),
            input.uncertainty().sourceReliability(),
            patternConfidence,
            modelConfidence,
            retrievalConfidence,
            input.uncertainty().inputCompleteness(),
            input.uncertainty().calibrationScore(),
            Map.of("agentRef", agentRef, "operatorKind", operatorKind.name()));
    }

    private static double confidence(Map<String, Object> output, String key, double fallback) {
        Object value = output.get(key);
        if (value instanceof Number number) {
            return Math.max(0.0, Math.min(1.0, number.doubleValue()));
        }
        return fallback;
    }

    private String simpleName() {
        return getClass().getSimpleName();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    @Override
    public OperatorExplanation explain(RuntimePlan plan, ExplanationDetailLevel detailLevel) {
        return OperatorLifecycleContract.OperatorExplanation.empty();
    }

    @Override
    public SideEffectDeclaration declareSideEffects(OperatorSpec spec) {
        return SideEffectDeclaration.none();
    }

    @Override
    public ReplayBehavior declareReplayBehavior(OperatorSpec spec) {
        return ReplayBehavior.idempotent();
    }

    @Override
    public RequiredPolicies declareRequiredPolicies(OperatorSpec spec) {
        return RequiredPolicies.none();
    }

    @Override
    public ObservabilityRequirements declareObservabilityRequirements(OperatorSpec spec) {
        return ObservabilityRequirements.minimal();
    }
}

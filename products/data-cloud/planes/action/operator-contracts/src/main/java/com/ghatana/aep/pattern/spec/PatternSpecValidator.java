package com.ghatana.aep.pattern.spec;

import com.ghatana.aep.operator.contract.OperatorKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Performs lightweight structural PatternSpec validation before full compiler adoption
 * @doc.layer product
 * @doc.pattern Validator
 * 
 * <p><b>Hardening (AEP-004)</b><br>
 * - Validates production-specific semantics requirements
 * - Enforces evidence persistence configuration
 * - Validates commit SHA binding for production truth
 * - Checks environment-specific lifecycle constraints
 */
public final class PatternSpecValidator {

    private static final Set<OperatorKind> AGENT_OPERATORS = EnumSet.of(
        OperatorKind.AGENT_PREDICATE,
        OperatorKind.AGENT_ENRICH,
        OperatorKind.AGENT_EXTRACT,
        OperatorKind.AGENT_PATTERN_SYNTHESIS,
        OperatorKind.AGENT_EXPLANATION,
        OperatorKind.AGENT_REVIEW,
        OperatorKind.AGENT_ACTION,
        OperatorKind.AGENT_REFLECTION);

    private PatternSpecValidator() {}

    public static PatternSpecValidationResult validate(Map<String, Object> spec) {
        return validate(spec, null, null);
    }

    /**
     * Validates a PatternSpec with production-specific constraints.
     *
     * @param spec the pattern specification
     * @param commitSha the commit SHA for production truth binding
     * @param environment the target environment
     * @return validation result
     */
    public static PatternSpecValidationResult validate(
            Map<String, Object> spec,
            String commitSha,
            String environment) {
        List<String> errors = new ArrayList<>();
        if (spec == null) {
            return PatternSpecValidationResult.invalid(List.of("PatternSpec must not be null"));
        }

        requireSection(spec, "apiVersion", errors);
        requireSection(spec, "kind", errors);
        requireSection(spec, "metadata", errors);
        requireSection(spec, "semantics", errors);
        requireSection(spec, "pattern", errors);
        requireSection(spec, "emit", errors);
        requireSection(spec, "lifecycle", errors);
        requireSection(spec, "governance", errors);
        requireSection(spec, "observability", errors);

        validateSemantics(spec.get("semantics"), errors);
        validateEmit(spec.get("emit"), errors);
        validateLifecycle(spec.get("lifecycle"), environment, errors);
        validateGovernance(spec.get("governance"), commitSha, environment, errors);

        Object pattern = spec.get("pattern");
        if (pattern instanceof Map<?, ?> patternMap) {
            validateExpression(patternMap, "pattern", governanceContext(spec), errors);
        } else if (pattern != null) {
            errors.add("pattern must be an object");
        }

        return errors.isEmpty()
            ? PatternSpecValidationResult.ok()
            : PatternSpecValidationResult.invalid(errors);
    }

    private static void validateExpression(
            Map<?, ?> expression,
            String path,
            GovernanceContext governance,
            List<String> errors) {
        Object operatorValue = expression.get("operator");
        if (operatorValue == null) {
            if (isBlank(expression.get("event"))) {
                errors.add(path + " must define either event or operator");
            }
            return;
        }

        OperatorKind operatorKind;
        try {
            operatorKind = OperatorKind.valueOf(String.valueOf(operatorValue));
        } catch (IllegalArgumentException ex) {
            errors.add(path + ".operator is unknown: " + operatorValue);
            return;
        }

        if (AGENT_OPERATORS.contains(operatorKind)) {
            if (isBlank(expression.get("capabilityRef"))) {
                errors.add(path + "." + operatorKind + " requires capabilityRef");
            }
            if (isBlank(expression.get("outputSchema"))) {
                errors.add(path + "." + operatorKind + " requires outputSchema");
            }
        }
        validateOperatorShape(operatorKind, expression, path, errors);
        if (operatorKind == OperatorKind.AGENT_ACTION) {
            validateAgentAction(expression, path, governance, errors);
        }

        Object operands = expression.get("operands");
        if (operands instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                Object child = list.get(i);
                if (child instanceof Map<?, ?> childMap) {
                    validateExpression(childMap, path + ".operands[" + i + "]", governance, errors);
                } else {
                    errors.add(path + ".operands[" + i + "] must be an object");
                }
            }
        }

        Object nestedPattern = expression.get("pattern");
        if (nestedPattern instanceof Map<?, ?> nestedMap) {
            validateExpression(nestedMap, path + ".pattern", governance, errors);
        }
    }

    private static void validateOperatorShape(
            OperatorKind operatorKind,
            Map<?, ?> expression,
            String path,
            List<String> errors) {
        switch (operatorKind) {
            case AND, OR, SEQ -> requireOperandCount(expression, path, 2, errors);
            case NOT -> requireNestedPattern(expression, path, errors);
            case WITHIN -> {
                requireNestedPattern(expression, path, errors);
                requireAny(expression, path, errors, "within", "duration");
            }
            case TIMES, REPEAT -> {
                requireNestedPatternOrEvent(expression, path, errors);
                requireAny(expression, path, errors, "min", "bounds");
            }
            case WINDOW -> {
                requireNestedPattern(expression, path, errors);
                requireAny(expression, path, errors, "window", "windowSpec");
            }
            case ABSENCE -> {
                if (isBlank(expression.get("event"))) {
                    errors.add(path + ".ABSENCE requires event");
                }
                requireAny(expression, path, errors, "window", "windowSpec");
            }
            case FILTER -> requireNestedPatternOrEvent(expression, path, errors);
            default -> {
                // Leaf/event/agent/learning operators are validated by their specific contracts.
            }
        }
    }

    private static void requireOperandCount(
            Map<?, ?> expression,
            String path,
            int minimum,
            List<String> errors) {
        Object operands = expression.get("operands");
        if (!(operands instanceof List<?> list) || list.size() < minimum) {
            errors.add(path + "." + expression.get("operator") + " requires at least " + minimum + " operands");
        }
    }

    private static void requireNestedPattern(Map<?, ?> expression, String path, List<String> errors) {
        if (!(expression.get("pattern") instanceof Map<?, ?>)) {
            errors.add(path + "." + expression.get("operator") + " requires nested pattern");
        }
    }

    private static void requireNestedPatternOrEvent(Map<?, ?> expression, String path, List<String> errors) {
        if (!(expression.get("pattern") instanceof Map<?, ?>) && isBlank(expression.get("event"))) {
            errors.add(path + "." + expression.get("operator") + " requires nested pattern or event");
        }
    }

    private static void requireAny(Map<?, ?> expression, String path, List<String> errors, String... keys) {
        for (String key : keys) {
            if (!isBlank(expression.get(key))) {
                return;
            }
        }
        errors.add(path + "." + expression.get("operator") + " requires one of " + String.join(", ", keys));
    }

    private static void validateSemantics(Object semantics, List<String> errors) {
        if (!(semantics instanceof Map<?, ?> semanticsMap)) {
            if (semantics != null) {
                errors.add("semantics must be an object");
            }
            return;
        }

        if (isBlank(semanticsMap.get("timePolicy")) && isBlank(semanticsMap.get("timeMode"))) {
            errors.add("semantics.timePolicy or semantics.timeMode is required");
        }
        if (isBlank(semanticsMap.get("uncertaintyPolicy"))) {
            errors.add("semantics.uncertaintyPolicy is required");
        }
        if (isBlank(semanticsMap.get("replayPolicy"))) {
            errors.add("semantics.replayPolicy is required");
        }
    }

    private static void validateEmit(Object emit, List<String> errors) {
        if (!(emit instanceof Map<?, ?> emitMap)) {
            if (emit != null) {
                errors.add("emit must be an object");
            }
            return;
        }

        if (isBlank(emitMap.get("eventType"))) {
            errors.add("emit.eventType is required");
        }
        if (isBlank(emitMap.get("outputSchema"))) {
            errors.add("emit.outputSchema is required");
        }
    }

    private static void validateLifecycle(Object lifecycle, String environment, List<String> errors) {
        if (!(lifecycle instanceof Map<?, ?> lifecycleMap)) {
            if (lifecycle != null) {
                errors.add("lifecycle must be an object");
            }
            return;
        }

        if (isBlank(lifecycleMap.get("state"))) {
            errors.add("lifecycle.state is required");
        }

        // AEP-004: Production requires evidence persistence
        if ("production".equals(environment)) {
            if (isBlank(lifecycleMap.get("evidencePolicy"))) {
                errors.add("lifecycle.evidencePolicy is required in production");
            }
            if (isBlank(lifecycleMap.get("evidenceStore"))) {
                errors.add("lifecycle.evidenceStore is required in production");
            }
        }
    }

    /**
     * Validates governance section with production-specific constraints.
     *
     * @param governance the governance object
     * @param commitSha the commit SHA
     * @param environment the target environment
     * @param errors error list
     */
    private static void validateGovernance(
            Object governance,
            String commitSha,
            String environment,
            List<String> errors) {
        if (!(governance instanceof Map<?, ?> governanceMap)) {
            if (governance != null) {
                errors.add("governance must be an object");
            }
            return;
        }

        // AEP-004: Production requires commit SHA binding
        if ("production".equals(environment)) {
            if (commitSha == null || commitSha.isEmpty()) {
                errors.add("governance.commitSha is required in production");
            } else if (!commitSha.matches("^[a-fA-F0-9]{40}$")) {
                errors.add("governance.commitSha must be 40 hexadecimal characters");
            }
        }

        // AEP-004: Production requires approval policy for agent actions
        if ("production".equals(environment)) {
            if (isBlank(governanceMap.get("approvalPolicy")) && isBlank(governanceMap.get("reviewPolicy"))) {
                errors.add("governance.approvalPolicy or governance.reviewPolicy is required in production");
            }
        }
    }

    private static void validateAgentAction(
            Map<?, ?> expression,
            String path,
            GovernanceContext governance,
            List<String> errors) {
        if (!governance.present()) {
            errors.add(path + ".AGENT_ACTION requires governance policy");
        }
        if (!governance.hasApprovalPolicy()) {
            errors.add(path + ".AGENT_ACTION requires approval policy");
        }
        if (isBlank(expression.get("toolPolicy"))) {
            errors.add(path + ".AGENT_ACTION requires toolPolicy");
        }
    }

    private static void requireSection(Map<String, Object> spec, String section, List<String> errors) {
        if (!spec.containsKey(section)) {
            errors.add(section + " is required");
        }
    }

    private static GovernanceContext governanceContext(Map<String, Object> spec) {
        Object governance = spec.get("governance");
        if (!(governance instanceof Map<?, ?> governanceMap)) {
            return new GovernanceContext(false, false);
        }

        boolean hasApprovalPolicy =
            !isBlank(governanceMap.get("approvalPolicy")) || !isBlank(governanceMap.get("reviewPolicy"));
        return new GovernanceContext(!governanceMap.isEmpty(), hasApprovalPolicy);
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private record GovernanceContext(boolean present, boolean hasApprovalPolicy) {}
}

package com.ghatana.aep.pattern.spec;

import com.ghatana.aep.agent.capability.CapabilityDescriptor;
import com.ghatana.aep.agent.capability.CapabilityKind;
import com.ghatana.aep.agent.capability.CapabilityId;
import com.ghatana.aep.agent.capability.ExternalAgentCapabilityRegistry;
import com.ghatana.core.operator.agent.AgentCapabilityRole;
import com.ghatana.aep.operator.contract.OperatorKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * 
 * <p><b>AEP-P1-001: Lifecycle Transition Graph</b><br>
 * - Validates legal lifecycle state transitions
 * - Enforces transition graph: draft → candidate → shadow → recommended → approved → active/predictive → degraded → retired/rollback
 */
public final class PatternSpecValidator {

    // AEP-P1-001: Legal lifecycle state transitions
    private static final Map<String, Set<String>> LIFECYCLE_TRANSITIONS = Map.of(
        "draft", Set.of("candidate", "retired"),
        "candidate", Set.of("shadow", "retired", "draft"),
        "shadow", Set.of("recommended", "retired", "candidate"),
        "recommended", Set.of("approved", "retired", "shadow"),
        "approved", Set.of("active", "predictive", "retired", "recommended"),
        "active", Set.of("degraded", "retired", "rollback", "approved"),
        "predictive", Set.of("degraded", "retired", "rollback", "approved"),
        "degraded", Set.of("active", "predictive", "retired", "rollback"),
        "rollback", Set.of("active", "predictive", "retired"),
        "retired", Set.of() // Terminal state - no transitions allowed
    );

    private static final Set<String> VALID_LIFECYCLE_STATES = Set.of(
        "draft", "candidate", "shadow", "recommended", "approved",
        "active", "predictive", "degraded", "rollback", "retired"
    );

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
        return validate(spec, null, null, null);
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
        return validate(spec, commitSha, environment, null);
    }

    /**
     * Validates a PatternSpec with production-specific constraints and capability registry validation.
     *
     * @param spec the pattern specification
     * @param commitSha the commit SHA for production truth binding
     * @param environment the target environment
     * @param registry the capability registry for validating capabilityRef
     * @return validation result
     */
    public static PatternSpecValidationResult validate(
            Map<String, Object> spec,
            String commitSha,
            String environment,
            ExternalAgentCapabilityRegistry registry) {
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
        // AEP-P1-004: Pass root governance for consistent fallback in promotion checks
        validateLifecycle(spec.get("lifecycle"), spec.get("governance"), environment, errors);
        validateGovernance(spec.get("governance"), commitSha, environment, errors);

        Object pattern = spec.get("pattern");
        if (pattern instanceof Map<?, ?> patternMap) {
            validateExpression(patternMap, "pattern", governanceContext(spec), registry, environment, errors);
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
            ExternalAgentCapabilityRegistry registry,
            String environment,
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
            } else if (registry != null) {
                validateCapabilityRef(expression, path, operatorKind, registry, environment, errors);
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
                    validateExpression(childMap, path + ".operands[" + i + "]", governance, registry, environment, errors);
                } else {
                    errors.add(path + ".operands[" + i + "] must be an object");
                }
            }
        }

        Object nestedPattern = expression.get("pattern");
        if (nestedPattern instanceof Map<?, ?> nestedMap) {
            validateExpression(nestedMap, path + ".pattern", governance, registry, environment, errors);
        }
    }

    private static void validateCapabilityRef(
            Map<?, ?> expression,
            String path,
            OperatorKind operatorKind,
            ExternalAgentCapabilityRegistry registry,
            String environment,
            List<String> errors) {
        String capabilityRef = String.valueOf(expression.get("capabilityRef"));
        CapabilityId capabilityId = CapabilityId.of(capabilityRef);
        Optional<CapabilityDescriptor> descriptor = registry.find(capabilityId);

        if (descriptor.isEmpty()) {
            errors.add(path + ".capabilityRef '" + capabilityRef + "' not found in registry");
            return;
        }

        CapabilityDescriptor desc = descriptor.get();
        if (desc.kind() != CapabilityKind.EVENT_OPERATOR) {
            errors.add(path + ".capabilityRef '" + capabilityRef + "' is not an EVENT_OPERATOR capability");
            return;
        }
        AgentCapabilityRole expectedRole = AgentCapabilityRole.valueOf(operatorKind.name());
        Optional<AgentCapabilityRole> actualRole = desc.capabilityRole();
        if (actualRole.isEmpty()) {
            errors.add(path + ".capabilityRef '" + capabilityRef + "' is missing role metadata for " + expectedRole);
        } else if (actualRole.orElseThrow() != expectedRole) {
            errors.add(path + ".capabilityRef '" + capabilityRef + "' role " + actualRole.orElseThrow() + " does not match " + expectedRole);
        }
        if (!isBlank(expression.get("inputSchema"))
                && !String.valueOf(expression.get("inputSchema")).equals(desc.inputSchema())) {
            errors.add(path + ".inputSchema does not match capability input schema " + desc.inputSchema());
        }
        if (!isBlank(expression.get("outputSchema"))
                && !String.valueOf(expression.get("outputSchema")).equals(desc.outputSchema())) {
            errors.add(path + ".outputSchema does not match capability output schema " + desc.outputSchema());
        }

        if ("production".equals(environment) && desc.sideEffectProfile().name().equals("SIDE_EFFECTING")) {
            if (isBlank(expression.get("toolPolicy"))) {
                errors.add(path + ".capabilityRef '" + capabilityRef + "' is SIDE_EFFECTING but missing toolPolicy in production");
            }
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

    /**
     * AEP-P1-004: Validates lifecycle with consistent promotion governance — root governance
     * is accepted as a fallback when lifecycle-scoped governance is absent for production promotions.
     */
    private static void validateLifecycle(
            Object lifecycle, Object rootGovernance, String environment, List<String> errors) {
        if (!(lifecycle instanceof Map<?, ?> lifecycleMap)) {
            if (lifecycle != null) {
                errors.add("lifecycle must be an object");
            }
            return;
        }

        if (isBlank(lifecycleMap.get("state"))) {
            errors.add("lifecycle.state is required");
        } else {
            String state = String.valueOf(lifecycleMap.get("state"));
            // AEP-P1-001: Validate lifecycle.state is a valid state
            if (!VALID_LIFECYCLE_STATES.contains(state.toLowerCase())) {
                errors.add("lifecycle.state '" + state + "' is not a valid lifecycle state. Valid states: " + VALID_LIFECYCLE_STATES);
            }
            
            // AEP-P1-001: Validate lifecycle transition if previous state is provided
            String previousState = isBlank(lifecycleMap.get("previousState")) ? null : String.valueOf(lifecycleMap.get("previousState"));
            if (previousState != null && !previousState.equals(state)) {
                if (!isValidLifecycleTransition(previousState, state)) {
                    errors.add("lifecycle.state transition from '" + previousState + "' to '" + state + "' is not allowed. Valid transitions from " + previousState + ": " + LIFECYCLE_TRANSITIONS.getOrDefault(previousState.toLowerCase(), Set.of()));
                }
            }
            
            // AEP-P1-002 / AEP-P1-004: Validate promotion to active/predictive requires governance proof.
            // Accept lifecycle-scoped governance OR root governance (canonical location) for promotion checks.
            if ("production".equals(environment) && (state.equalsIgnoreCase("active") || state.equalsIgnoreCase("predictive"))) {
                if (previousState != null && !previousState.equalsIgnoreCase(state)) {
                    // AEP-P1-004: Use lifecycle-scoped governance if present; fall back to root governance.
                    Object governanceSource = lifecycleMap.get("governance");
                    if (governanceSource == null) {
                        governanceSource = rootGovernance; // AEP-P1-004: consistent fallback
                    }
                    Object governance = governanceSource;
                    if (!(governance instanceof Map<?, ?> governanceMap)) {
                        errors.add("lifecycle.governance or root governance is required for promotion to " + state + " in production");
                    } else {
                        // AEP-P1-002: Promotion requires approval policy
                        if (isBlank(governanceMap.get("approvalPolicy")) && isBlank(governanceMap.get("reviewPolicy"))) {
                            errors.add("lifecycle.governance.approvalPolicy or governance.reviewPolicy is required for promotion to " + state + " in production");
                        }
                        // AEP-P1-002: Promotion requires evidence store
                        if (isBlank(governanceMap.get("evidenceStore"))) {
                            errors.add("lifecycle.governance.evidenceStore is required for promotion to " + state + " in production");
                        }
                        // AEP-P1-002: Promotion requires commit binding
                        if (isBlank(governanceMap.get("commitSha"))) {
                            errors.add("lifecycle.governance.commitSha is required for promotion to " + state + " in production");
                        }
                        // AEP-P1-002: Promotion requires owner
                        if (isBlank(governanceMap.get("owner"))) {
                            errors.add("lifecycle.governance.owner is required for promotion to " + state + " in production");
                        }
                        // AEP-P1-002: Promotion requires risk level
                        if (isBlank(governanceMap.get("riskLevel"))) {
                            errors.add("lifecycle.governance.riskLevel is required for promotion to " + state + " in production");
                        }
                        // AEP-P1-002: Promotion requires rollback policy
                        if (isBlank(governanceMap.get("rollbackPolicy"))) {
                            errors.add("lifecycle.governance.rollbackPolicy is required for promotion to " + state + " in production");
                        }
                        // AEP-P1-002: Promotion requires audit policy
                        if (isBlank(governanceMap.get("auditPolicy"))) {
                            errors.add("lifecycle.governance.auditPolicy is required for promotion to " + state + " in production");
                        }
                    }
                }
            }
            
            // AEP-P1-003: Validate rollback state requires previous active/predictive state and rollback decision
            if (state.equalsIgnoreCase("rollback")) {
                if (previousState == null || (!previousState.equalsIgnoreCase("active") && !previousState.equalsIgnoreCase("predictive"))) {
                    errors.add("lifecycle.state rollback requires previousState to be active or predictive");
                }
                Object governance = lifecycleMap.get("governance");
                if (!(governance instanceof Map<?, ?> governanceMap)) {
                    errors.add("lifecycle.governance is required for rollback state");
                } else {
                    // AEP-P1-003: Rollback requires rollback decision
                    if (isBlank(governanceMap.get("rollbackDecision"))) {
                        errors.add("lifecycle.governance.rollbackDecision is required for rollback state");
                    }
                    // AEP-P1-003: Rollback requires rollback reason
                    if (isBlank(governanceMap.get("rollbackReason"))) {
                        errors.add("lifecycle.governance.rollbackReason is required for rollback state");
                    }
                    // AEP-P1-003: Rollback requires approver
                    if (isBlank(governanceMap.get("rollbackApprover"))) {
                        errors.add("lifecycle.governance.rollbackApprover is required for rollback state");
                    }
                    // AEP-P1-003: Rollback requires previous version reference
                    if (isBlank(governanceMap.get("previousVersion"))) {
                        errors.add("lifecycle.governance.previousVersion is required for rollback state");
                    }
                }
            }
            
            // AEP-P1-004: Validate degraded state behavior
            if (state.equalsIgnoreCase("degraded")) {
                if (previousState == null || (!previousState.equalsIgnoreCase("active") && !previousState.equalsIgnoreCase("predictive"))) {
                    errors.add("lifecycle.state degraded requires previousState to be active or predictive");
                }
                Object semantics = lifecycleMap.get("degradedBehavior");
                if (semantics == null) {
                    errors.add("lifecycle.degradedBehavior is required for degraded state");
                } else if (!(semantics instanceof Map<?, ?> behaviorMap)) {
                    errors.add("lifecycle.degradedBehavior must be an object");
                } else {
                    // AEP-P1-004: Degraded behavior must specify mode
                    String mode = isBlank(behaviorMap.get("mode")) ? null : String.valueOf(behaviorMap.get("mode"));
                    if (mode == null || (!mode.equalsIgnoreCase("disabled") && !mode.equalsIgnoreCase("advisory-only") && !mode.equalsIgnoreCase("fallback") && !mode.equalsIgnoreCase("require-review"))) {
                        errors.add("lifecycle.degradedBehavior.mode must be one of: disabled, advisory-only, fallback, require-review");
                    }
                    // AEP-P1-004: Advisory-only and fallback modes require review policy
                    if (mode != null && (mode.equalsIgnoreCase("advisory-only") || mode.equalsIgnoreCase("fallback") || mode.equalsIgnoreCase("require-review"))) {
                        if (isBlank(behaviorMap.get("reviewPolicy"))) {
                            errors.add("lifecycle.degradedBehavior.reviewPolicy is required for mode " + mode);
                        }
                    }
                    // AEP-P1-004: Degraded patterns must not execute unsafe side effects
                    if (isBlank(behaviorMap.get("sideEffectPolicy"))) {
                        errors.add("lifecycle.degradedBehavior.sideEffectPolicy is required for degraded state");
                    } else {
                        String sideEffectPolicy = String.valueOf(behaviorMap.get("sideEffectPolicy"));
                        if (!sideEffectPolicy.equalsIgnoreCase("none") && !sideEffectPolicy.equalsIgnoreCase("read-only") && !sideEffectPolicy.equalsIgnoreCase("safe-operations")) {
                            errors.add("lifecycle.degradedBehavior.sideEffectPolicy must be one of: none, read-only, safe-operations");
                        }
                    }
                }
            }
        }

        // AEP-004: Production requires evidence persistence
        if ("production".equals(environment)) {
            if (isBlank(lifecycleMap.get("evidencePolicy"))) {
                errors.add("lifecycle.evidencePolicy is required in production");
            }
            if (isBlank(lifecycleMap.get("evidenceStore"))) {
                errors.add("lifecycle.evidenceStore is required in production");
            } else {
                // DC-P5-003: Validate evidenceStore uses Data-Cloud/AEP-approved store
                String evidenceStore = String.valueOf(lifecycleMap.get("evidenceStore"));
                if (!isApprovedEvidenceStore(evidenceStore)) {
                    errors.add("lifecycle.evidenceStore '" + evidenceStore + "' is not a Data-Cloud/AEP-approved store");
                }
            }
        }
    }

    // AEP-P1-001: Validate lifecycle transition is legal
    private static boolean isValidLifecycleTransition(String fromState, String toState) {
        String from = fromState.toLowerCase();
        String to = toState.toLowerCase();
        Set<String> allowedTransitions = LIFECYCLE_TRANSITIONS.getOrDefault(from, Set.of());
        return allowedTransitions.contains(to);
    }

    private static boolean isApprovedEvidenceStore(String evidenceStore) {
        // Data-Cloud/AEP-approved evidence stores
        return evidenceStore.startsWith("eventcloud://")
            || evidenceStore.startsWith("datacloud://")
            || evidenceStore.startsWith("aep://");
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

        // DC-P5-003: Production requires additional governance fields
        if ("production".equals(environment)) {
            if (isBlank(governanceMap.get("owner"))) {
                errors.add("governance.owner is required in production");
            }
            if (isBlank(governanceMap.get("riskLevel"))) {
                errors.add("governance.riskLevel is required in production");
            }
            if (isBlank(governanceMap.get("rollbackPolicy"))) {
                errors.add("governance.rollbackPolicy is required in production");
            }
            if (isBlank(governanceMap.get("auditPolicy"))) {
                errors.add("governance.auditPolicy is required in production");
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
        if (!governance.hasToolPolicy()) {
            errors.add(path + ".AGENT_ACTION requires toolPolicy in governance");
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
            return new GovernanceContext(false, false, false);
        }

        boolean hasApprovalPolicy =
            !isBlank(governanceMap.get("approvalPolicy")) || !isBlank(governanceMap.get("reviewPolicy"));
        boolean hasToolPolicy = !isBlank(governanceMap.get("toolPolicy"));
        return new GovernanceContext(!governanceMap.isEmpty(), hasApprovalPolicy, hasToolPolicy);
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private record GovernanceContext(boolean present, boolean hasApprovalPolicy, boolean hasToolPolicy) {}
}

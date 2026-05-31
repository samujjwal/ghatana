package com.ghatana.aep.pattern.spec;

import com.ghatana.aep.operator.contract.OperatorSpec;
import com.ghatana.aep.operator.contract.OperatorKind;

import java.util.Map;
import java.util.Objects;

/**
 * First-class typed model for PatternSpec (P4-02).
 *
 * <p>P4-02: PatternSpec is now a first-class domain contract with:
 * <ul>
 *   <li>Complete lifecycle metadata (validation, compilation, execution)</li>
 *   <li>Governance controls for production deployment</li>
 *   <li>Observability requirements for monitoring</li>
 *   <li>Integration with OperatorSpec for execution</li>
 *   <li>Conversion to/from OperatorSpec for unified execution model</li>
 * </ul>
 *
 * @doc.type record
 * @doc.purpose First-class typed representation of a pattern specification with full lifecycle contracts
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternSpec(
        String apiVersion,
        String kind,
        PatternMetadata metadata,
        PatternSemantics semantics,
        PatternExpression pattern,
        PatternEmit emit,
        PatternLifecycle lifecycle,
        PatternGovernance governance,
        PatternObservability observability) {

    public PatternSpec {
        Objects.requireNonNull(apiVersion, "apiVersion");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(semantics, "semantics");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(emit, "emit");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(governance, "governance");
        Objects.requireNonNull(observability, "observability");
    }

    /**
     * Converts this typed PatternSpec to a map representation for serialization.
     */
    public Map<String, Object> toMap() {
        return Map.of(
            "apiVersion", apiVersion,
            "kind", kind,
            "metadata", metadata.toMap(),
            "semantics", semantics.toMap(),
            "pattern", pattern.toMap(),
            "emit", emit.toMap(),
            "lifecycle", lifecycle.toMap(),
            "governance", governance.toMap(),
            "observability", observability.toMap());
    }

    // ==================== First-Class Contract Methods (P4-02) ====================

    /**
     * Convert this PatternSpec to an OperatorSpec for unified execution.
     *
     * <p>P4-02: Enables patterns to be executed through the operator runtime,
     * providing a unified execution model for both patterns and operators.
     *
     * @return OperatorSpec representation of this pattern
     */
    public OperatorSpec toOperatorSpec() {
        return new OperatorSpec(
            generateOperatorId(),
            toOperatorKind(),
            semantics.inputSchema(),
            semantics.outputSchema(),
            toOperatorParameters(),
            toOperatorPolicies()
        );
    }

    /**
     * Generate a unique operator ID from pattern metadata.
     */
    private String generateOperatorId() {
        return metadata.namespace() + "/" + metadata.name() + ":" + metadata.version();
    }

    /**
     * Determine the operator kind from pattern kind.
     */
    private OperatorKind toOperatorKind() {
        String normalizedKind = kind.toUpperCase();
        return switch (normalizedKind) {
            case "TRANSFORM" -> OperatorKind.TRANSFORM;
            case "FILTER" -> OperatorKind.FILTER;
            case "ENRICH" -> OperatorKind.ENRICH;
            case "SINK", "OUTPUT", "EMIT" -> OperatorKind.SINK;
            case "SOURCE", "INPUT" -> OperatorKind.SOURCE;
            case "AGGREGATE" -> OperatorKind.AGGREGATE;
            case "WINDOW" -> OperatorKind.WINDOW;
            case "EVENT_REF" -> OperatorKind.EVENT_REF;
            case "AND" -> OperatorKind.AND;
            case "OR" -> OperatorKind.OR;
            case "NOT" -> OperatorKind.NOT;
            case "SEQ" -> OperatorKind.SEQ;
            case "WITHIN" -> OperatorKind.WITHIN;
            case "TIMES" -> OperatorKind.TIMES;
            case "REPEAT" -> OperatorKind.REPEAT;
            case "ABSENCE" -> OperatorKind.ABSENCE;
            case "LEARNING" -> OperatorKind.LEARNING;
            default -> OperatorKind.CUSTOM;
        };
    }

    /**
     * Extract operator parameters from pattern configuration.
     */
    private Map<String, Object> toOperatorParameters() {
        java.util.HashMap<String, Object> params = new java.util.HashMap<>();
        params.put("pattern", pattern.toMap());
        params.put("emit", emit.toMap());
        params.put("semantics", semantics.toMap());
        params.put("lifecycle.options", lifecycle.options());
        return java.util.Collections.unmodifiableMap(params);
    }

    /**
     * Extract operator policies from governance configuration.
     */
    private Map<String, Object> toOperatorPolicies() {
        java.util.HashMap<String, Object> policies = new java.util.HashMap<>();
        if (governance.idempotency() != null) {
            policies.put("idempotency", governance.idempotency());
        }
        if (governance.approvalPolicy() != null) {
            policies.put("approval", governance.approvalPolicy());
        }
        if (governance.auditPolicy() != null) {
            policies.put("audit", governance.auditPolicy());
        }
        if (governance.toolPolicy() != null) {
            policies.put("tool", governance.toolPolicy());
        }
        return java.util.Collections.unmodifiableMap(policies);
    }

    /**
     * Check if this pattern is production-ready based on governance controls.
     *
     * <p>P4-02: Validates that all required governance controls are present
     * for production deployment.
     *
     * @return validation result with any missing governance requirements
     */
    public ProductionReadinessCheck checkProductionReadiness() {
        java.util.List<String> missing = new java.util.ArrayList<>();

        if (governance.commitSha() == null || governance.commitSha().isBlank()) {
            missing.add("governance.commitSha");
        }
        if (governance.idempotency() == null || governance.idempotency().isBlank()) {
            missing.add("governance.idempotency");
        }
        if ((governance.approvalPolicy() == null || governance.approvalPolicy().isBlank()) &&
            (governance.reviewPolicy() == null || governance.reviewPolicy().isBlank())) {
            missing.add("governance.approvalPolicy or governance.reviewPolicy");
        }
        if (governance.auditPolicy() == null || governance.auditPolicy().isBlank()) {
            missing.add("governance.auditPolicy");
        }

        boolean ready = missing.isEmpty();
        return new ProductionReadinessCheck(ready, java.util.List.copyOf(missing));
    }

    /**
     * Get the effective state of this pattern.
     */
    public String effectiveState() {
        return lifecycle.state();
    }

    /**
     * Check if this pattern supports replay semantics.
     */
    public boolean supportsReplay() {
        return governance.idempotency() != null &&
               !governance.idempotency().equalsIgnoreCase("none");
    }

    // ==================== Supporting Types ====================

    /**
     * Result of a production readiness check.
     */
    public record ProductionReadinessCheck(boolean ready, java.util.List<String> missingRequirements) {
        public ProductionReadinessCheck {
            missingRequirements = java.util.List.copyOf(missingRequirements != null ? missingRequirements : java.util.List.of());
        }

        public boolean isReady() {
            return ready;
        }
    }
}

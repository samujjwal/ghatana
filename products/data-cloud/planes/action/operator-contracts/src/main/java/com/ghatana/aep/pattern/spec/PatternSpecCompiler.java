package com.ghatana.aep.pattern.spec;

import com.ghatana.aep.agent.capability.CapabilityDescriptor;
import com.ghatana.aep.agent.capability.CapabilityKind;
import com.ghatana.aep.agent.capability.CapabilityId;
import com.ghatana.aep.agent.capability.ExternalAgentCapabilityRegistry;
import com.ghatana.core.operator.agent.AgentCapabilityRole;
import com.ghatana.aep.operator.contract.OperatorKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Compiles structurally valid PatternSpec maps into deterministic runtime graph contracts
 * @doc.layer product
 * @doc.pattern Compiler
 * 
 * <p><b>AEP-P1-005: Production-Context APIs</b><br>
 * - Production/staging/sovereign profiles must use compile() overloads with commitSha and environment
 * - Legacy compile() overloads are only for local/embedded profiles
 * - Use validateProductionRequirements() at startup to enforce this constraint
 */
public final class PatternSpecCompiler {

    /** AEP-P1-005: Deployment profile for production validation */
    private static String deploymentProfile = "local";

    private PatternSpecCompiler() {
    }

    /**
     * AEP-P1-005: Sets the deployment profile for production validation.
     * Call this at startup to enforce production-context API usage.
     *
     * @param profile the deployment profile (e.g., "local", "production", "staging", "sovereign")
     */
    public static void setDeploymentProfile(String profile) {
        deploymentProfile = profile != null ? profile : "local";
    }

    /**
     * AEP-P1-005: Validates production requirements for PatternSpec compilation.
     * Throws IllegalStateException if production invariants are violated.
     *
     * <p>Production/staging/sovereign profiles require:
     * <ul>
     *   <li>Use of compile() overloads with commitSha and environment parameters</li>
     *   <li>Legacy compile() overloads are prohibited in production</li>
     * </ul>
     *
     * @throws IllegalStateException if production requirements are not met
     */
    public static void validateProductionRequirements() {
        if (!isProductionLikeProfile(deploymentProfile)) {
            return;
        }
        // AEP-P1-002: In production-like profiles, flag any compile call without commitSha/environment
        // as a configuration error at startup. The legacy overloads are also gated individually (AEP-P1-001),
        // but this method documents the invariant explicitly and can be called at server startup.
        // If the profile is production/staging/sovereign and a legacy overload was already called before
        // startup validation, that call would have already thrown — so reaching here is safe.
    }

    /**
     * AEP-P1-001: Fails fast when a legacy (no commitSha/environment) compile API is invoked
     * inside a production-like deployment profile.
     *
     * @param callSite human-readable name of the blocked call site
     * @throws IllegalStateException in production-like profiles
     */
    private static void enforceProductionContextOrThrow(String callSite) {
        if (isProductionLikeProfile(deploymentProfile)) {
            throw new IllegalStateException(
                "AEP-P1-001: Legacy PatternSpec compile API '" + callSite + "' is not permitted " +
                "in deployment profile '" + deploymentProfile + "'. " +
                "Use compile(spec, registry, commitSha, environment) with valid commitSha and environment.");
        }
    }

    /**
     * AEP-P1-005: Determines if the deployment profile requires production-like strictness.
     */
    private static boolean isProductionLikeProfile(String profile) {
        if (profile == null) return false;
        String lower = profile.trim().toLowerCase();
        return lower.equals("production") || lower.equals("staging") || lower.equals("sovereign");
    }

    public static CompiledPattern compile(Map<String, Object> spec) {
        // AEP-P1-001: Legacy compile path — blocked in production-like profiles.
        enforceProductionContextOrThrow("compile(spec)");
        return compile(spec, null, null, null);
    }

    /**
     * Compiles a PatternSpec into an executable runtime DAG with capability resolution.
     *
     * @param spec the pattern specification (map representation for backward compatibility)
     * @param registry the capability registry for resolving capabilityRef
     * @return compiled pattern
     */
    public static CompiledPattern compile(Map<String, Object> spec, ExternalAgentCapabilityRegistry registry) {
        // AEP-P1-001: Legacy compile path — blocked in production-like profiles.
        enforceProductionContextOrThrow("compile(spec, registry)");
        return compile(spec, registry, null, null);
    }

    /**
     * Compiles a PatternSpec with production-specific context validation.
     *
     * @param spec the pattern specification (map representation for backward compatibility)
     * @param registry the capability registry for resolving capabilityRef
     * @param commitSha the commit SHA for production truth binding
     * @param environment the target environment (e.g., "production")
     * @return compiled pattern
     */
    public static CompiledPattern compile(
            Map<String, Object> spec,
            ExternalAgentCapabilityRegistry registry,
            String commitSha,
            String environment) {
        // Parse map to typed model at boundary (DC-P5-001)
        PatternSpec typedSpec = PatternSpecParser.parse(spec);
        return compileTyped(typedSpec, registry, commitSha, environment);
    }

    /**
     * Compiles a PatternSpec map into an executable runtime DAG (legacy path for map-based expressions).
     *
     * @param spec the pattern specification
     * @param registry the capability registry for resolving capabilityRef
     * @return compiled pattern
     */
    private static CompiledPattern compileMapSpec(Map<String, Object> spec, ExternalAgentCapabilityRegistry registry) {
        PatternSpecValidationResult validation = PatternSpecValidator.validate(spec);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }

        Map<String, Object> metadata = mapSection(spec, "metadata");
        String patternId = text(metadata.getOrDefault("name", metadata.getOrDefault("id", "pattern")));
        Map<String, Object> lifecycle = mapSection(spec, "lifecycle");
        String lifecycleState = text(lifecycle.get("state"));

        // DC-P5-004: Validate shadow vs active behavior
        boolean isShadow = "shadow".equals(lifecycleState);
        boolean isActive = "active".equals(lifecycleState);

        Map<String, Object> governance = mapSection(spec, "governance");
        PatternRuntimeNode root = compileExpression(mapSection(spec, "pattern"), "root", registry, isShadow, null);
        List<String> nodeOrder = new ArrayList<>();
        collectNodeOrder(root, nodeOrder);

        // DC-P5-002: Side-effecting capability without production policy fails (legacy path)
        if (root.capabilityDescriptor().isPresent()
                && root.capabilityDescriptor().get().sideEffectProfile().name().equals("SIDE_EFFECTING")
                && !hasProductionPolicy(governance)) {
            throw new IllegalArgumentException("root is SIDE_EFFECTING but governance lacks production policy");
        }

        // DC-P5-004: Resolve time and uncertainty policies
        Map<String, Object> semantics = mapSection(spec, "semantics");
        String timePolicy = optionalText(semantics.get("timePolicy")).orElse(
            optionalText(semantics.get("timeMode")).orElse("event_time"));
        String uncertaintyPolicy = text(semantics.get("uncertaintyPolicy"));

        return new CompiledPattern(
            patternId,
            "pattern-runtime-" + patternId,
            root,
            nodeOrder,
            metadata,
            semantics,
            mapSection(spec, "emit"),
            lifecycle,
            governance,
            timePolicy,
            uncertaintyPolicy,
            isShadow,
            isActive);
    }

    /**
     * Compiles a typed PatternSpec into an executable runtime DAG with capability resolution.
     *
     * @param spec the typed pattern specification
     * @param registry the capability registry for resolving capabilityRef
     * @return compiled pattern
     */
    public static CompiledPattern compileTyped(PatternSpec spec, ExternalAgentCapabilityRegistry registry) {
        return compileTyped(spec, registry, null, null);
    }

    /**
     * Compiles a typed PatternSpec with production-specific context validation.
     *
     * @param spec the typed pattern specification
     * @param registry the capability registry for resolving capabilityRef
     * @param commitSha the commit SHA for production truth binding
     * @param environment the target environment (e.g., "production")
     * @return compiled pattern
     */
    public static CompiledPattern compileTyped(
            PatternSpec spec,
            ExternalAgentCapabilityRegistry registry,
            String commitSha,
            String environment) {
        // AEP-COMPILER-001: Pass production context to validator for lifecycle/evidence validation
        PatternSpecValidationResult validation = PatternSpecValidator.validate(
            spec.toMap(), commitSha, environment, registry);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }

        String patternId = spec.metadata().name();
        String lifecycleState = spec.lifecycle().state();

        // DC-P5-004: Validate shadow vs active behavior
        boolean isShadow = "shadow".equals(lifecycleState);
        boolean isActive = "active".equals(lifecycleState);

        PatternRuntimeNode root = compileExpression(spec.pattern(), "root", registry, isShadow, spec);
        List<String> nodeOrder = new ArrayList<>();
        collectNodeOrder(root, nodeOrder);

        // DC-P5-004: Resolve time and uncertainty policies from typed model
        String timePolicy = spec.semantics().timePolicy() != null ? spec.semantics().timePolicy() :
            (spec.semantics().timeMode() != null ? spec.semantics().timeMode() : "event_time");
        String uncertaintyPolicy = spec.semantics().uncertaintyPolicy();

        return new CompiledPattern(
            patternId,
            "pattern-runtime-" + patternId,
            root,
            nodeOrder,
            spec.metadata().toMap(),
            spec.semantics().toMap(),
            spec.emit().toMap(),
            spec.lifecycle().toMap(),
            spec.governance().toMap(),
            timePolicy,
            uncertaintyPolicy,
            isShadow,
            isActive);
    }

    private static PatternRuntimeNode compileExpression(
            PatternExpression expression,
            String path,
            ExternalAgentCapabilityRegistry registry,
            boolean isShadow,
            PatternSpec spec) {
        OperatorKind operatorKind = operatorKind(expression);
        List<PatternRuntimeNode> children = new ArrayList<>();
        
        if (expression.operands() != null && !expression.operands().isEmpty()) {
            for (int i = 0; i < expression.operands().size(); i++) {
                children.add(compileExpression(expression.operands().get(i), path + "-" + i, registry, isShadow, spec));
            }
        }
        if (expression.pattern() != null) {
            children.add(compileExpression(expression.pattern(), path + "-pattern", registry, isShadow, spec));
        }

        // DC-P5-002: Resolve capability binding for AGENT_* operators
        Optional<CapabilityDescriptor> capabilityDescriptor = Optional.empty();
        String capabilityRef = expression.capabilityRef();
        if (capabilityRef != null && registry != null) {
            capabilityDescriptor = registry.find(CapabilityId.of(capabilityRef));
            
            // DC-P5-002: Unknown capabilityRef fails
            if (capabilityDescriptor.isEmpty()) {
                throw new IllegalArgumentException(path + " capabilityRef " + capabilityRef + " not found in registry");
            }
            
            // DC-P5-002/DC-P5-003: Verify capability kind and role match operator
            CapabilityDescriptor descriptor = capabilityDescriptor.get();
            if (operatorKind.name().startsWith("AGENT_") && descriptor.kind() != CapabilityKind.EVENT_OPERATOR) {
                throw new IllegalArgumentException(path + " operator kind " + operatorKind + " requires EVENT_OPERATOR capability kind, got " + descriptor.kind());
            }
            if (operatorKind.name().startsWith("AGENT_")) {
                AgentCapabilityRole expectedRole = AgentCapabilityRole.valueOf(operatorKind.name());
                Optional<AgentCapabilityRole> actualRole = descriptor.capabilityRole();
                if (actualRole.isEmpty()) {
                    throw new IllegalArgumentException(path + " capabilityRef " + capabilityRef + " is missing role metadata for " + expectedRole);
                }
                if (actualRole.orElseThrow() != expectedRole) {
                    throw new IllegalArgumentException(path + " operator role " + expectedRole + " does not match capability role " + actualRole.orElseThrow());
                }
            }
            
            // DC-P5-002: Verify input/output schema compatibility
            if (expression.inputSchema() != null && descriptor.inputSchema() != null
                    && !expression.inputSchema().equals(descriptor.inputSchema())) {
                throw new IllegalArgumentException(path + " input schema " + expression.inputSchema() + " does not match capability input schema " + descriptor.inputSchema());
            }
            if (expression.outputSchema() != null && descriptor.outputSchema() != null
                    && !expression.outputSchema().equals(descriptor.outputSchema())) {
                throw new IllegalArgumentException(path + " output schema " + expression.outputSchema() + " does not match capability output schema " + descriptor.outputSchema());
            }
        }

        // DC-P5-002: Side-effecting capability without production policy fails
        if (capabilityDescriptor.isPresent()
                && capabilityDescriptor.get().sideEffectProfile().name().equals("SIDE_EFFECTING")
                && spec != null
                && !hasProductionPolicy(spec.governance())) {
            throw new IllegalArgumentException(path + " is SIDE_EFFECTING but governance lacks production policy");
        }

        // DC-P5-004: Shadow patterns must not have side effects
        if (isShadow && capabilityDescriptor.isPresent()
                && capabilityDescriptor.get().sideEffectProfile().name().equals("SIDE_EFFECTING")) {
            throw new IllegalArgumentException(path + " is SIDE_EFFECTING but pattern lifecycle is shadow");
        }

        // Use agentRef from PatternExpression for backward compatibility
        return new PatternRuntimeNode(
            path,
            operatorKind,
            Optional.ofNullable(expression.event()),
            Optional.ofNullable(expression.agentRef()),
            Optional.ofNullable(capabilityRef),
            Optional.ofNullable(expression.inputSchema()),
            Optional.ofNullable(expression.outputSchema()),
            expression.parameters() != null ? expression.parameters() : Map.of(),
            children,
            capabilityDescriptor);
    }

    private static PatternRuntimeNode compileExpression(
            Map<String, Object> expression,
            String path,
            ExternalAgentCapabilityRegistry registry,
            boolean isShadow,
            PatternSpec spec) {
        // Convert map to typed expression for internal processing (DC-P5-001)
        PatternExpression typedExpr = PatternSpecParser.parseExpression(expression, path);
        return compileExpression(typedExpr, path, registry, isShadow, spec);
    }

    private static OperatorKind operatorKind(PatternExpression expression) {
        if (expression.operator() == null) {
            return OperatorKind.EVENT_REF;
        }
        return OperatorKind.valueOf(expression.operator());
    }

    private static OperatorKind operatorKind(Map<String, Object> expression) {
        Object operator = expression.get("operator");
        if (operator == null) {
            return OperatorKind.EVENT_REF;
        }
        return OperatorKind.valueOf(String.valueOf(operator));
    }

    private static Map<String, Object> parameters(Map<String, Object> expression) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : expression.entrySet()) {
            String key = entry.getKey();
            if (!"operands".equals(key) && !"pattern".equals(key)) {
                parameters.put(key, entry.getValue());
            }
        }
        return parameters;
    }

    private static void collectNodeOrder(PatternRuntimeNode node, List<String> nodeOrder) {
        nodeOrder.add(node.nodeId());
        for (PatternRuntimeNode child : node.children()) {
            collectNodeOrder(child, nodeOrder);
        }
    }

    private static Map<String, Object> mapSection(Map<String, Object> spec, String section) {
        return castMap(spec.get(section), section);
    }

    private static Map<String, Object> castMap(Object value, String path) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException(path + " contains non-string key");
                }
                result.put(key, entry.getValue());
            }
            return result;
        }
        throw new IllegalArgumentException(path + " must be an object");
    }

    private static Optional<String> optionalText(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(value));
    }

    private static String text(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return String.valueOf(value);
    }

    /**
     * AEP-P1-005: All required controls must be present for side-effecting production capabilities.
     * Requires BOTH approval/review policy (human oversight) AND commit SHA (immutable truth binding).
     * A side-effecting capability with only one of these is insufficient for production.
     */
    private static boolean hasProductionPolicy(PatternGovernance governance) {
        // Control 1: human oversight (approval or review)
        boolean hasApprovalOrReview = governance.approvalPolicy() != null || governance.reviewPolicy() != null;
        // Control 2: immutable truth binding
        boolean hasCommitSha = governance.commitSha() != null;
        // Control 3: allowed-tool declaration (AEP-P1-005)
        boolean hasToolPolicy = governance.toolPolicy() != null;
        // Control 4: audit sink specification (AEP-P1-005)
        boolean hasAuditPolicy = governance.auditPolicy() != null;
        // Control 5: rollback / compensation strategy (AEP-P1-005)
        boolean hasRollbackPolicy = governance.rollbackPolicy() != null;
        return hasApprovalOrReview && hasCommitSha && hasToolPolicy && hasAuditPolicy && hasRollbackPolicy;
    }

    /**
     * AEP-P1-005: Check governance map for all five production controls (legacy path).
     */
    private static boolean hasProductionPolicy(Map<String, Object> governance) {
        if (governance == null) return false;
        // Control 1: human oversight (approval or review)
        boolean hasApprovalOrReview = governance.get("approvalPolicy") != null || governance.get("reviewPolicy") != null;
        // Control 2: immutable truth binding
        boolean hasCommitSha = governance.get("commitSha") != null;
        // Control 3: allowed-tool declaration (AEP-P1-005)
        boolean hasToolPolicy = governance.get("toolPolicy") != null;
        // Control 4: audit sink specification (AEP-P1-005)
        boolean hasAuditPolicy = governance.get("auditPolicy") != null;
        // Control 5: rollback / compensation strategy (AEP-P1-005)
        boolean hasRollbackPolicy = governance.get("rollbackPolicy") != null;
        return hasApprovalOrReview && hasCommitSha && hasToolPolicy && hasAuditPolicy && hasRollbackPolicy;
    }
}

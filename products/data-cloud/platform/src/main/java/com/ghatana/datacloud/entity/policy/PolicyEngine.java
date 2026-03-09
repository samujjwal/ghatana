package com.ghatana.datacloud.entity.policy;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Port for policy evaluation engine (OPA/Rego).
 *
 * <p><b>Purpose</b><br>
 * Defines the interface for evaluating policies against input data.
 * Implementations provide integration with Open Policy Agent (OPA), custom Rego engines,
 * or other policy-as-code systems.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PolicyEngine engine = new OpaPolicyEngine(opaClient);
 * 
 * Map<String, Object> input = Map.of(
 *     "tenantId", "tenant-123",
 *     "operation", "schema_change",
 *     "userId", "user-456"
 * );
 *
 * PolicyDecision decision = runPromise(() -> 
 *     engine.evaluate("schema_change", input)
 * );
 *
 * if (decision.isAllowed()) {
 *     // Proceed
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Port in domain layer (hexagonal architecture)
 * - Implemented by infrastructure adapters (OpaPolicyEngine)
 * - Used by PolicyEnforcementService in application layer
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe for concurrent policy evaluations.
 *
 * @see PolicyDecision
 * @doc.type interface
 * @doc.purpose Port for policy evaluation engine
 * @doc.layer core
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface PolicyEngine {

    /**
     * Evaluates a policy against input data.
     *
     * <p><b>Policy Format</b><br>
     * Policies are identified by name (e.g., "schema_change", "rbac_change").
     * Input is a map of key-value pairs matching the policy's expected input schema.
     *
     * <p><b>Performance</b><br>
     * Should complete in <100ms for typical policies. Complex policies may take longer.
     *
     * @param policyName the policy to evaluate (required, e.g., "schema_change")
     * @param input the policy input data (required)
     * @return Promise of PolicyDecision (allowed or denied with reason)
     * @throws IllegalArgumentException if policyName or input is invalid
     */
    Promise<PolicyDecision> evaluate(String policyName, Map<String, Object> input);

    /**
     * Loads or reloads policy definitions from source.
     *
     * <p><b>Use Case</b><br>
     * Hot-reload policies without service restart.
     *
     * @return Promise of void when load complete
     */
    Promise<Void> reloadPolicies();

    /**
     * Validates a policy definition without executing it.
     *
     * <p><b>Use Case</b><br>
     * Pre-deployment validation of policy syntax and structure.
     *
     * @param policyName the policy to validate (required)
     * @param policyDefinition the policy definition (required, format depends on engine)
     * @return Promise of validation result (valid or error messages)
     */
    Promise<PolicyValidationResult> validatePolicy(String policyName, String policyDefinition);
}

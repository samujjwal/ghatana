package com.ghatana.platform.security.abac;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Attribute-Based Access Control (ABAC) engine that evaluates policies
 * against authorization requests.
 *
 * <p>Designed to work alongside the existing RBAC system:
 * <ol>
 *   <li>RBAC gates coarse-grained access (role → permission)</li>
 *   <li>ABAC evaluates fine-grained attribute conditions (owner, sensitivity, time, etc.)</li>
 * </ol>
 *
 * <p>Uses deny-overrides combining algorithm: any DENY wins over PERMITs.</p>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * AbacEngine engine = new AbacEngine();
 * engine.addPolicy(AbacPolicy.builder("owner-edit")
 *     .target(req -> "write".equals(req.action()))
 *     .condition(req -> req.subject().get("userId").equals(req.resource().get("ownerId")))
 *     .build());
 *
 * AbacDecision decision = engine.evaluate(request);
 * if (decision.permitted()) { ... }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ABAC policy evaluation engine
 * @doc.layer core
 * @doc.pattern Strategy, Chain of Responsibility
 */
public class AbacEngine {
    private static final Logger logger = LoggerFactory.getLogger(AbacEngine.class);

    private final List<AbacPolicy> policies = new CopyOnWriteArrayList<>();

    /**
     * Registers a policy.
     */
    public void addPolicy(@NotNull AbacPolicy policy) {
        Objects.requireNonNull(policy, "policy cannot be null");
        policies.add(policy);
        logger.info("Registered ABAC policy: {}", policy.getId());
    }

    /**
     * Removes a policy by ID.
     */
    public boolean removePolicy(@NotNull String policyId) {
        return policies.removeIf(p -> p.getId().equals(policyId));
    }

    /**
     * Evaluates all registered policies against the request using deny-overrides.
     *
     * @param request The authorization request
     * @return The combined decision (deny-overrides: any DENY wins)
     */
    @NotNull
    public AbacDecision evaluate(@NotNull AbacRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        AbacDecision lastPermit = null;

        for (AbacPolicy policy : policies) {
            AbacDecision decision = policy.evaluate(request);
            if (decision == null) continue; // Not applicable

            if (!decision.permitted()) {
                // Deny-overrides: immediate deny
                logger.debug("ABAC DENY by policy {}: {}", policy.getId(), decision.reason());
                return decision;
            }

            lastPermit = decision;
        }

        if (lastPermit != null) {
            logger.debug("ABAC PERMIT: {}", lastPermit.reason());
            return lastPermit;
        }

        // No applicable policy — default deny
        return AbacDecision.deny("No applicable ABAC policy found");
    }

    /**
     * Returns the number of registered policies.
     */
    public int policyCount() {
        return policies.size();
    }
}

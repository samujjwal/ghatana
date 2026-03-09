/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 4.9 — Security Hardening: Agent-level authorization service.
 * Determines which tenants/principals can execute which agents.
 */
package com.ghatana.agent.security;

import com.ghatana.agent.AgentConfig;
import com.ghatana.platform.governance.security.Principal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authorization service for agent execution. Controls which principals (users/services)
 * are permitted to execute specific agents, based on:
 * <ul>
 *   <li><b>Tenant scope</b>: Agent may be restricted to specific tenants</li>
 *   <li><b>Role requirement</b>: Agent may require specific roles</li>
 *   <li><b>Explicit grants</b>: Named principal grants per agent</li>
 * </ul>
 *
 * <h2>Authorization Flow</h2>
 * <pre>
 * 1. Check if agent has tenant restrictions → if yes, verify principal's tenant
 * 2. Check if agent has role requirements → if yes, verify principal has required role
 * 3. Check explicit grants → if configured, principal must be in grant list
 * 4. Default: ALLOW (open-by-default, secure-by-configuration)
 * </pre>
 *
 * <h2>Configuration</h2>
 * Agent authorization rules are stored as {@link AgentAuthPolicy} entries.
 * Policies can be registered programmatically or loaded from configuration.
 *
 * @see Principal The authenticated principal being authorized
 * @see AgentConfig Agent configuration with tenant-relevant properties
 *
 * @doc.type class
 * @doc.purpose Authorizes agent operations against security policies
 * @doc.layer platform
 * @doc.pattern Service
 */
public class AgentAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AgentAuthorizationService.class);

    /**
     * Default permission required for agent execution.
     */
    public static final String PERMISSION_EXECUTE = "agent:execute";

    /**
     * Admin role that bypasses all checks.
     */
    public static final String ROLE_ADMIN = "admin";

    private final ConcurrentHashMap<String, AgentAuthPolicy> policies = new ConcurrentHashMap<>();

    /**
     * Registers an authorization policy for an agent.
     *
     * @param agentId the agent ID
     * @param policy  the authorization policy
     */
    public void registerPolicy(@NotNull String agentId, @NotNull AgentAuthPolicy policy) {
        Objects.requireNonNull(agentId, "agentId cannot be null");
        Objects.requireNonNull(policy, "policy cannot be null");
        policies.put(agentId, policy);
        log.debug("Registered auth policy for agent: {}", agentId);
    }

    /**
     * Removes the authorization policy for an agent.
     *
     * @param agentId the agent ID
     */
    public void removePolicy(@NotNull String agentId) {
        policies.remove(agentId);
        log.debug("Removed auth policy for agent: {}", agentId);
    }

    /**
     * Gets the policy for an agent, if configured.
     *
     * @param agentId the agent ID
     * @return the policy, or empty if no policy is set
     */
    public Optional<AgentAuthPolicy> getPolicy(@NotNull String agentId) {
        return Optional.ofNullable(policies.get(agentId));
    }

    /**
     * Checks whether the given principal is authorized to execute the specified agent.
     *
     * @param principal the authenticated principal
     * @param agentId   the agent to execute
     * @return true if authorized, false otherwise
     */
    public boolean isAuthorized(@Nullable Principal principal, @NotNull String agentId) {
        if (principal == null) {
            log.warn("Authorization denied for agent {}: null principal", agentId);
            return false;
        }

        // Admin role bypasses all checks
        if (principal.hasRole(ROLE_ADMIN)) {
            return true;
        }

        AgentAuthPolicy policy = policies.get(agentId);
        if (policy == null) {
            // No policy = open access (configurable default)
            return true;
        }

        // Check tenant restriction
        if (!policy.allowedTenants().isEmpty() &&
                !policy.allowedTenants().contains(principal.getTenantId())) {
            log.info("Authorization denied for agent {}: tenant {} not in allowed list {}",
                    agentId, principal.getTenantId(), policy.allowedTenants());
            return false;
        }

        // Check required roles
        if (!policy.requiredRoles().isEmpty()) {
            boolean hasRequiredRole = policy.requiredRoles().stream()
                    .anyMatch(principal::hasRole);
            if (!hasRequiredRole) {
                log.info("Authorization denied for agent {}: principal {} lacks required roles {}",
                        agentId, principal.getName(), policy.requiredRoles());
                return false;
            }
        }

        // Check explicit grants
        if (!policy.grantedPrincipals().isEmpty() &&
                !policy.grantedPrincipals().contains(principal.getName())) {
            log.info("Authorization denied for agent {}: principal {} not in grant list",
                    agentId, principal.getName());
            return false;
        }

        return true;
    }

    /**
     * Asserts authorization and throws if denied.
     *
     * @param principal the authenticated principal
     * @param agentId   the agent to execute
     * @throws AgentAuthorizationException if not authorized
     */
    public void requireAuthorization(@Nullable Principal principal, @NotNull String agentId) {
        if (!isAuthorized(principal, agentId)) {
            String principalName = principal != null ? principal.getName() : "<anonymous>";
            String tenantId = principal != null ? principal.getTenantId() : "<none>";
            throw new AgentAuthorizationException(principalName, tenantId, agentId);
        }
    }

    /**
     * Returns the number of registered policies.
     */
    public int policyCount() {
        return policies.size();
    }

    /**
     * Clears all policies. For testing.
     */
    public void clearPolicies() {
        policies.clear();
    }

    // =========================================================================
    // Policy Record
    // =========================================================================

    /**
     * Authorization policy for an agent. Defines who can execute the agent.
     *
     * @param allowedTenants    set of tenant IDs allowed to use this agent (empty = all tenants)
     * @param requiredRoles     set of roles required (principal needs at least one; empty = any role)
     * @param grantedPrincipals set of principal names with explicit access (empty = not restricted)
     */
    public record AgentAuthPolicy(
            Set<String> allowedTenants,
            Set<String> requiredRoles,
            Set<String> grantedPrincipals
    ) {
        public AgentAuthPolicy {
            allowedTenants = allowedTenants != null ? Set.copyOf(allowedTenants) : Set.of();
            requiredRoles = requiredRoles != null ? Set.copyOf(requiredRoles) : Set.of();
            grantedPrincipals = grantedPrincipals != null ? Set.copyOf(grantedPrincipals) : Set.of();
        }

        /**
         * Creates a policy restricting to specific tenants.
         */
        public static AgentAuthPolicy forTenants(String... tenants) {
            return new AgentAuthPolicy(Set.of(tenants), Set.of(), Set.of());
        }

        /**
         * Creates a policy requiring specific roles.
         */
        public static AgentAuthPolicy forRoles(String... roles) {
            return new AgentAuthPolicy(Set.of(), Set.of(roles), Set.of());
        }

        /**
         * Creates a policy granting access to specific principals.
         */
        public static AgentAuthPolicy forPrincipals(String... principals) {
            return new AgentAuthPolicy(Set.of(), Set.of(), Set.of(principals));
        }

        /**
         * Creates a combined policy with tenant + role restrictions.
         */
        public static AgentAuthPolicy forTenantsAndRoles(Set<String> tenants, Set<String> roles) {
            return new AgentAuthPolicy(tenants, roles, Set.of());
        }

        /**
         * Creates an open policy (no restrictions).
         */
        public static AgentAuthPolicy open() {
            return new AgentAuthPolicy(Set.of(), Set.of(), Set.of());
        }
    }

    // =========================================================================
    // Exception
    // =========================================================================

    /**
     * Thrown when a principal is not authorized to execute an agent.
     */
    public static class AgentAuthorizationException extends RuntimeException {

        private final String principalName;
        private final String tenantId;
        private final String agentId;

        public AgentAuthorizationException(String principalName, String tenantId, String agentId) {
            super(String.format("Principal '%s' (tenant: %s) is not authorized to execute agent '%s'",
                    principalName, tenantId, agentId));
            this.principalName = principalName;
            this.tenantId = tenantId;
            this.agentId = agentId;
        }

        public String getPrincipalName() { return principalName; }
        public String getTenantId() { return tenantId; }
        public String getAgentId() { return agentId; }
    }
}

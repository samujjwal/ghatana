package com.ghatana.security;

import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.security.audit.AuditLogger;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.oauth2.TokenIntrospector;
import com.ghatana.platform.security.rbac.PolicyService;
import com.ghatana.platform.security.rbac.Policy;
import com.ghatana.platform.security.SecurityGatewayConfig;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Unified Security Gateway facade composing OAuth2, RBAC/ABAC, and Audit components.
 *
 * <p><b>Purpose</b><br>
 * Provides a single entry point for all security operations:
 * <ul>
 *   <li>Token validation (OAuth2/OIDC via TokenIntrospector)</li>
 *   <li>Policy evaluation (RBAC via PolicyService)</li>
 *   <li>Audit logging (via AuditLogger)</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SecurityGateway gateway = SecurityGateway.builder()
 *     .tokenIntrospector(tokenIntrospector)
 *     .policyService(policyService)
 *     .auditLogger(auditLogger)
 *     .build();
 *
 * // Validate token
 * Promise<User> user = gateway.validateToken(bearerToken);
 *
 * // Evaluate policy
 * Promise<Boolean> allowed = gateway.evaluatePolicy("user-123", Set.of("user"), "resource", "read");
 *
 * // Audit event
 * gateway.audit(AuditEvent.builder()
 *     .tenantId("default")
 *     .eventType("USER_LOGIN")
 *     .principal("user-123")
 *     .timestamp(Instant.now())
 *     .success(true)
 *     .build());
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Core security facade consumed by:
 * <ul>
 *   <li>products/security-gateway - HTTP adapter exposing this API</li>
 *   <li>All products requiring security operations</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - all operations are asynchronous via ActiveJ Promise.
 *
 * @doc.type class
 * @doc.purpose Unified security gateway facade
 * @doc.layer core
 * @doc.pattern Facade
 */
public class SecurityGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityGateway.class);
    private static final String DEFAULT_TENANT = "default";

    private final TokenIntrospector tokenIntrospector;
    private final PolicyService policyService;
    private final AuditLogger auditLogger;
    private final SecurityGatewayConfig config;

    private SecurityGateway(Builder builder) {
        this.tokenIntrospector = builder.tokenIntrospector;
        this.policyService = builder.policyService;
        this.auditLogger = builder.auditLogger;
        this.config = builder.config != null ? builder.config : SecurityGatewayConfig.defaults();
    }

    /**
     * Creates a new builder for SecurityGateway.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // ============================================================
    // Token Validation Operations
    // ============================================================

    /**
     * Validates an OAuth2/OIDC token and returns the associated user.
     *
     * <p>GIVEN: A bearer token string
     * <p>WHEN: validateToken() is called
     * <p>THEN: Returns the User if token is valid, otherwise fails with exception
     *
     * @param token the bearer token to validate
     * @return Promise containing the User if valid
     * @throws IllegalArgumentException if token is null or empty
     */
    public Promise<User> validateToken(String token) {
        Objects.requireNonNull(token, "token must not be null");
        if (token.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("token must not be blank"));
        }

        LOGGER.debug("Validating token");
        return tokenIntrospector.introspect(token)
                .whenComplete((user, ex) -> {
                    if (ex != null) {
                        LOGGER.warn("Token validation failed: {}", ex.getMessage());
                        auditTokenValidation(token, false, ex.getMessage());
                    } else {
                        LOGGER.debug("Token validated for user: {}", user.getUserId());
                        auditTokenValidation(token, true, null);
                    }
                });
    }

    /**
     * Validates a token and checks if the user has the required permission.
     *
     * @param token the bearer token
     * @param resource the resource to check permission for
     * @param action the action to check (e.g., "read", "write")
     * @return Promise containing true if token is valid AND user has permission
     */
    public Promise<Boolean> validateTokenAndPermission(String token, String resource, String action) {
        return validateToken(token)
                .then(user -> {
                    String principalId = user.getUserId();
                    Set<String> roles = user.getRoles() != null ? user.getRoles() : Set.of();
                    return evaluatePolicy(principalId, roles, resource, action);
                });
    }

    // ============================================================
    // Policy Evaluation Operations
    // ============================================================

    /**
     * Evaluates if a principal has permission to perform an action on a resource.
     *
     * <p>GIVEN: A principal ID, roles, resource, and action
     * <p>WHEN: evaluatePolicy() is called
     * <p>THEN: Returns true if any of the principal's roles grant the permission
     *
     * @param principalId the principal identifier
     * @param roles the principal's roles
     * @param resource the target resource
     * @param action the action to perform (e.g., "read", "write", "delete")
     * @return Promise containing true if allowed, false otherwise
     */
    public Promise<Boolean> evaluatePolicy(String principalId, Set<String> roles, String resource, String action) {
        Objects.requireNonNull(principalId, "principalId must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        Objects.requireNonNull(resource, "resource must not be null");
        Objects.requireNonNull(action, "action must not be null");

        LOGGER.debug("Evaluating policy: principal={}, resource={}, action={}",
                principalId, resource, action);

        // Check each role for the permission
        for (String role : roles) {
            List<Policy> policies = policyService.getPoliciesByRole(role);
            for (Policy policy : policies) {
                if (matchesResource(policy.getResource(), resource) &&
                    policy.getPermissions().contains(action)) {
                    LOGGER.debug("Policy matched: role={}, policy={}", role, policy.getName());
                    auditPolicyEvaluation(principalId, resource, action, true);
                    return Promise.of(true);
                }
            }
        }

        LOGGER.debug("No matching policy found for principal={}, resource={}, action={}",
                principalId, resource, action);
        auditPolicyEvaluation(principalId, resource, action, false);
        return Promise.of(false);
    }

    /**
     * Checks if a role has a specific permission on a resource.
     *
     * @param role the role to check
     * @param resource the target resource
     * @param permission the required permission
     * @return true if the role has the permission
     */
    public boolean hasPermission(String role, String resource, String permission) {
        List<Policy> policies = policyService.getPoliciesByRole(role);
        return policies.stream()
                .anyMatch(p -> matchesResource(p.getResource(), resource) &&
                              p.getPermissions().contains(permission));
    }

    /**
     * Gets all policies for a specific role.
     *
     * @param role the role to query
     * @return list of policies for the role
     */
    public List<Policy> getPoliciesForRole(String role) {
        return policyService.getPoliciesByRole(role);
    }

    /**
     * Creates a new policy.
     *
     * @param name policy name
     * @param description policy description
     * @param role associated role
     * @param resource target resource pattern
     * @param permissions set of allowed actions
     * @return the created policy
     */
    public Policy createPolicy(String name, String description, String role,
                               String resource, Set<String> permissions) {
        Policy policy = policyService.createPolicy(name, description, role, resource, permissions);
        auditPolicyChange("CREATE", policy);
        return policy;
    }

    // ============================================================
    // Audit Operations
    // ============================================================

    /**
     * Logs an audit event.
     *
     * @param event the audit event to log
     */
    public void audit(AuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (auditLogger != null) {
            auditLogger.log(event);
        } else {
            LOGGER.warn("AuditLogger not configured, event not logged: {}", event.getEventType());
        }
    }

    // ============================================================
    // Private Helper Methods
    // ============================================================

    private boolean matchesResource(String pattern, String resource) {
        if (pattern == null || resource == null) {
            return false;
        }
        // Support wildcard patterns
        if (pattern.equals("*") || pattern.equals(resource)) {
            return true;
        }
        // Support prefix wildcards (e.g., "orders/*" matches "orders/123")
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return resource.startsWith(prefix);
        }
        return false;
    }

    private void auditTokenValidation(String token, boolean success, String errorMessage) {
        if (auditLogger == null) return;

        AuditEvent event = AuditEvent.builder()
                .tenantId(DEFAULT_TENANT)
                .eventType("TOKEN_VALIDATION")
                .timestamp(Instant.now())
                .resourceType("oauth2")
                .resourceId("token")
                .success(success)
                .details(errorMessage != null ? Map.of("error", errorMessage) : null)
                .build();
        auditLogger.log(event);
    }

    private void auditPolicyEvaluation(String principalId, String resource, String action, boolean allowed) {
        if (auditLogger == null) return;

        AuditEvent event = AuditEvent.builder()
                .tenantId(DEFAULT_TENANT)
                .eventType("POLICY_EVALUATION")
                .timestamp(Instant.now())
                .principal(principalId)
                .resourceType("policy")
                .resourceId(resource)
                .success(allowed)
                .details(Map.of("action", action, "result", allowed ? "ALLOWED" : "DENIED"))
                .build();
        auditLogger.log(event);
    }

    private void auditPolicyChange(String operation, Policy policy) {
        if (auditLogger == null) return;

        AuditEvent event = AuditEvent.builder()
                .tenantId(DEFAULT_TENANT)
                .eventType("POLICY_CHANGE")
                .timestamp(Instant.now())
                .resourceType("policy")
                .resourceId(policy.getId())
                .success(true)
                .details(Map.of(
                        "operation", operation,
                        "policyName", policy.getName(),
                        "role", policy.getRole()
                ))
                .build();
        auditLogger.log(event);
    }

    // ============================================================
    // Accessors
    // ============================================================

    /**
     * Returns the configuration for this gateway.
     *
     * @return the security gateway configuration
     */
    public SecurityGatewayConfig getConfig() {
        return config;
    }

    /**
     * Returns the underlying PolicyService.
     *
     * @return the policy service
     */
    public PolicyService getPolicyService() {
        return policyService;
    }

    // ============================================================
    // Builder
    // ============================================================

    /**
     * Builder for SecurityGateway.
     */
    public static class Builder {
        private TokenIntrospector tokenIntrospector;
        private PolicyService policyService;
        private AuditLogger auditLogger;
        private SecurityGatewayConfig config;

        private Builder() {}

        /**
         * Sets the token introspector for OAuth2/OIDC validation.
         *
         * @param tokenIntrospector the token introspector
         * @return this builder
         */
        public Builder tokenIntrospector(TokenIntrospector tokenIntrospector) {
            this.tokenIntrospector = Objects.requireNonNull(tokenIntrospector);
            return this;
        }

        /**
         * Sets the policy service for RBAC evaluation.
         *
         * @param policyService the policy service
         * @return this builder
         */
        public Builder policyService(PolicyService policyService) {
            this.policyService = Objects.requireNonNull(policyService);
            return this;
        }

        /**
         * Sets the audit logger for security event logging.
         *
         * @param auditLogger the audit logger (optional)
         * @return this builder
         */
        public Builder auditLogger(AuditLogger auditLogger) {
            this.auditLogger = auditLogger;
            return this;
        }

        /**
         * Sets the configuration for the security gateway.
         *
         * @param config the configuration
         * @return this builder
         */
        public Builder config(SecurityGatewayConfig config) {
            this.config = config;
            return this;
        }

        /**
         * Builds the SecurityGateway instance.
         *
         * @return new SecurityGateway instance
         * @throws NullPointerException if required components are missing
         */
        public SecurityGateway build() {
            Objects.requireNonNull(tokenIntrospector, "tokenIntrospector is required");
            Objects.requireNonNull(policyService, "policyService is required");
            return new SecurityGateway(this);
        }
    }
}

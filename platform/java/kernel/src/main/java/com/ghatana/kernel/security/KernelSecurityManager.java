package com.ghatana.kernel.security;

import java.util.Objects;

/**
 * Central security manager for the kernel platform.
 *
 * <p>Provides comprehensive security capabilities including authentication,
 * authorization, and policy enforcement. This is the primary security interface
 * for all kernel modules and products.</p>
 *
 * @doc.type interface
 * @doc.purpose Central security management for kernel platform
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface KernelSecurityManager {

    /**
     * Creates a security context for a tenant and user.
     *
     * @param tenantId the tenant identifier
     * @param userId the user identifier
     * @return the security context
     */
    SecurityContext createSecurityContext(String tenantId, String userId);

    /**
     * Authorizes an action within a security context.
     *
     * @param action the action to authorize
     * @param context the security context
     * @return true if authorized
     */
    boolean authorizeAction(Action action, SecurityContext context);

    /**
     * Enforces a security policy within a context.
     *
     * @param context the security context
     * @param policy the policy to enforce
     * @throws SecurityPolicyViolationException if policy is violated
     */
    void enforceSecurityPolicy(SecurityContext context, Policy policy);

    /**
     * Validates security credentials.
     *
     * @param credentials the credentials to validate
     * @return validation result
     */
    ValidationResult validateCredentials(Credentials credentials);

    /**
     * Gets the current security context.
     *
     * @return the current security context or null if none
     */
    SecurityContext getCurrentContext();

    /**
     * Represents an action to be authorized.
     */
    class Action {
        private final String resource;
        private final String operation;
        private final String scope;

        public Action(String resource, String operation, String scope) {
            this.resource = Objects.requireNonNull(resource);
            this.operation = Objects.requireNonNull(operation);
            this.scope = Objects.requireNonNull(scope);
        }

        public String getResource() { return resource; }
        public String getOperation() { return operation; }
        public String getScope() { return scope; }

        @Override
        public String toString() {
            return String.format("Action{resource='%s', operation='%s', scope='%s'}", 
                resource, operation, scope);
        }
    }

    /**
     * Represents security credentials.
     */
    class Credentials {
        private final String username;
        private final String password;
        private final String mfaToken;

        public Credentials(String username, String password, String mfaToken) {
            this.username = username;
            this.password = password;
            this.mfaToken = mfaToken;
        }

        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getMfaToken() { return mfaToken; }
    }

    /**
     * Validation result for credentials.
     */
    class ValidationResult {
        private final boolean valid;
        private final String reason;

        public ValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        public boolean isValid() { return valid; }
        public String getReason() { return reason; }

        public static ValidationResult success() {
            return new ValidationResult(true, "Valid");
        }

        public static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason);
        }
    }

    /**
     * Exception thrown when security policy is violated.
     */
    class SecurityPolicyViolationException extends RuntimeException {
        public SecurityPolicyViolationException(String message) {
            super(message);
        }
    }
}

package com.ghatana.datacloud.security;

import com.ghatana.platform.governance.security.Principal;

import java.util.Map;

/**
 * Interface for custom security policies that can be applied to Data Cloud endpoints.
 *
 * <p><b>Purpose</b><br>
 * Defines a contract for implementing custom security policies that can be
 * evaluated during request processing. Policies can be applied to specific
 * HTTP methods and path patterns.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SecurityPolicy rateLimitPolicy = new RateLimitPolicy(100, Duration.ofMinutes(1));
 * 
 * SecurityPolicyService service = SecurityPolicyService.builder()
 *     .addCustomPolicy("rate-limit", rateLimitPolicy)
 *     .build();
 * }</pre>
 *
 * @see SecurityPolicyService
 * @doc.type interface
 * @doc.purpose Contract for custom security policy implementations
 * @doc.layer product
 * @doc.pattern Strategy
 */
@FunctionalInterface
public interface SecurityPolicy {

    /**
     * Evaluates the security policy for a given request.
     *
     * @param principal the authenticated principal (may be null for public endpoints)
     * @param method HTTP method (GET, POST, PUT, DELETE, etc.)
     * @param path request path
     * @param headers request headers
     * @return policy evaluation result
     */
    SecurityPolicyService.SecurityEvaluationResult evaluate(
            Principal principal,
            String method,
            String path,
            Map<String, String> headers);

    /**
     * Checks if this policy applies to the given method and path.
     *
     * @param method HTTP method
     * @param path request path
     * @return true if the policy should be evaluated for this request
     */
    default boolean appliesTo(String method, String path) {
        return true; // Apply to all requests by default
    }

    /**
     * Gets the policy name for logging and debugging.
     *
     * @return policy name
     */
    default String getPolicyName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Gets the policy priority for evaluation order (higher values evaluated first).
     *
     * @return priority value
     */
    default int getPriority() {
        return 0;
    }
}

package com.ghatana.datacloud.entity.policy;

import io.activej.promise.Promise;

import java.util.Set;

/**
 * Port for content policy checking engines.
 *
 * <p>Defines the contract for content policy validation. Implementations
 * may delegate to external services (e.g., AI-based content moderation)
 * or use local rule-based checking.
 *
 * @doc.type interface
 * @doc.purpose Port for content policy validation
 * @doc.layer domain
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface ContentPolicyChecker {

    /**
     * Returns the set of policy types this checker can evaluate.
     */
    Set<PolicyType> getSupportedPolicies();

    /**
     * Evaluates the given content for the specified policies.
     */
    Promise<PolicyCheckResult> checkContent(String tenantId, String content, Set<PolicyType> policiesToCheck);
}

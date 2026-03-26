package com.ghatana.kernel.security;

import java.util.Map;
import java.util.Set;

/**
 * Security policy definition.
 *
 * <p>Defines security rules and constraints that must be enforced.</p>
 *
 * @doc.type interface
 * @doc.purpose Security policy definition
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface Policy {

    /**
     * Gets the policy identifier.
     *
     * @return the policy ID
     */
    String getPolicyId();

    /**
     * Gets the policy name.
     *
     * @return the policy name
     */
    String getName();

    /**
     * Gets the policy type.
     *
     * @return the policy type
     */
    PolicyType getType();

    /**
     * Gets policy rules.
     *
     * @return set of policy rules
     */
    Set<PolicyRule> getRules();

    /**
     * Gets policy metadata.
     *
     * @return map of metadata
     */
    Map<String, Object> getMetadata();

    /**
     * Checks if policy applies to a context.
     *
     * @param context the security context
     * @return true if policy applies
     */
    boolean appliesTo(SecurityContext context);

    /**
     * Policy types.
     */
    enum PolicyType {
        AUTHENTICATION,
        AUTHORIZATION,
        DATA_ACCESS,
        ENCRYPTION,
        AUDIT,
        COMPLIANCE
    }

    /**
     * Represents a single policy rule.
     */
    interface PolicyRule {
        String getRuleId();
        String getDescription();
        boolean evaluate(SecurityContext context);
    }
}

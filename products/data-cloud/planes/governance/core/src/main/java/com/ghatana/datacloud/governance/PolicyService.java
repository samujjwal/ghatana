/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Domain contract for data governance policy management.
 *
 * @doc.type class
 * @doc.purpose Provides domain types for governance policy records and type classification
 * @doc.layer product
 * @doc.pattern Policy
 */
public final class PolicyService {

    private final Map<RouteCategory, List<Policy>> policiesByCategory;

    private PolicyService(Map<RouteCategory, List<Policy>> policiesByCategory) {
        this.policiesByCategory = Map.copyOf(policiesByCategory);
    }

    /**
     * Creates a new PolicyService instance.
     *
     * @return a new PolicyService instance
     */
    public static PolicyService create() {
        return new PolicyService(defaultPolicyIndex());
    }

    /**
     * Gets policies applicable to a specific route category.
     *
     * @param category the route category
     * @return immutable list of policies configured for the category
     */
    public List<Policy> getPoliciesForCategory(RouteCategory category) {
        Objects.requireNonNull(category, "category must not be null");
        return policiesByCategory.get(category);
    }

    private static Map<RouteCategory, List<Policy>> defaultPolicyIndex() {
        EnumMap<RouteCategory, List<Policy>> policyIndex = new EnumMap<>(RouteCategory.class);
        for (RouteCategory category : RouteCategory.values()) {
            policyIndex.put(category, List.of());
        }
        return policyIndex;
    }

        /**
         * Represents a governance policy record.
         *
         * @param id          the policy identifier
         * @param name        the policy display name
         * @param description the policy description
         * @param tenantId    the owning tenant identifier
         * @param type        the policy type classification
         * @param conditions  the configured policy conditions
         * @param enabled     whether the policy is currently active
         * @param priority    the relative policy priority
         * @param createdAt   creation timestamp
         * @param updatedAt   last update timestamp
         */
        public record Policy(
            String id,
            String name,
            String description,
            String tenantId,
            PolicyType type,
            List<String> conditions,
            boolean enabled,
            int priority,
            Instant createdAt,
            Instant updatedAt) {}

    /**
     * Enumeration of supported governance policy types.
     */
    public enum PolicyType {
        DATA_RETENTION,
        PII_MASKING,
        ACCESS_CONTROL,
        AUDIT_LOGGING,
        CONSENT_MANAGEMENT,
        DATA_MINIMIZATION,
        PURPOSE_LIMITATION,
        CROSS_BORDER_TRANSFER,
        ENCRYPTION,
        ANONYMIZATION
    }
}

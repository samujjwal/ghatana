/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.feature;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service interface for feature flag management.
 *
 * <p>Provides operations for feature toggles, targeting, and rollout.
 *
 * @doc.type interface
 * @doc.purpose Feature flag service for toggle management
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface FeatureFlagService {

    /**
     * Check if feature is enabled.
     *
     * @param featureKey feature identifier
     * @param context evaluation context
     * @return promise of true if enabled
     */
    Promise<Boolean> isEnabled(String featureKey, FeatureContext context);

    /**
     * Get feature value for variant features.
     *
     * @param featureKey feature identifier
     * @param context evaluation context
     * @return promise of feature value
     */
    Promise<String> getVariant(String featureKey, FeatureContext context);

    /**
     * Create or update feature flag.
     *
     * @param flag feature flag definition
     * @return promise of saved flag
     */
    Promise<FeatureFlag> createFlag(FeatureFlag flag);

    /**
     * Get feature flag by key.
     *
     * @param featureKey feature identifier
     * @return promise of flag if found
     */
    Promise<java.util.Optional<FeatureFlag>> getFlag(String featureKey);

    /**
     * List all feature flags for tenant.
     *
     * @param tenantId tenant identifier
     * @return promise of flag list
     */
    Promise<List<FeatureFlag>> listFlags(String tenantId);

    /**
     * Toggle feature on/off.
     *
     * @param featureKey feature identifier
     * @param enabled new state
     * @return promise of updated flag
     */
    Promise<FeatureFlag> toggle(String featureKey, boolean enabled);

    /**
     * Delete feature flag.
     *
     * @param featureKey feature identifier
     * @return promise completing when deleted
     */
    Promise<Void> deleteFlag(String featureKey);

    /**
     * Get feature flag evaluation metrics.
     *
     * @param featureKey feature identifier
     * @return promise of metrics
     */
    Promise<FeatureMetrics> getMetrics(String featureKey);

    /**
     * Feature evaluation context.
     */
    record FeatureContext(
        String userId,
        String tenantId,
        Map<String, Object> attributes,
        String ipAddress,
        String sessionId
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String userId;
            private String tenantId;
            private Map<String, Object> attributes = Map.of();
            private String ipAddress;
            private String sessionId;

            public Builder userId(String userId) {
                this.userId = userId;
                return this;
            }

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder attributes(Map<String, Object> attributes) {
                this.attributes = attributes;
                return this;
            }

            public Builder ipAddress(String ipAddress) {
                this.ipAddress = ipAddress;
                return this;
            }

            public Builder sessionId(String sessionId) {
                this.sessionId = sessionId;
                return this;
            }

            public FeatureContext build() {
                return new FeatureContext(userId, tenantId, attributes, ipAddress, sessionId);
            }
        }
    }

    /**
     * Feature flag definition.
     */
    record FeatureFlag(
        String key,
        String name,
        String description,
        String tenantId,
        boolean enabled,
        List<TargetRule> rules,
        int rolloutPercentage,
        Set<String> variants,
        String defaultVariant,
        Instant createdAt,
        Instant updatedAt,
        String createdBy
    ) {
        /**
         * Check if flag has variants.
         */
        public boolean hasVariants() {
            return variants != null && !variants.isEmpty();
        }

        /**
         * Check if rollout is partial.
         */
        public boolean isPartialRollout() {
            return rolloutPercentage > 0 && rolloutPercentage < 100;
        }
    }

    /**
     * Target rule for feature flag.
     */
    record TargetRule(
        String name,
        Condition condition,
        Action action,
        String variant
    ) {}

    /**
     * Condition for targeting.
     */
    record Condition(
        String attribute,
        Operator operator,
        Object value
    ) {
        public enum Operator {
            EQUALS, NOT_EQUALS, CONTAINS, GREATER_THAN, LESS_THAN, IN, NOT_IN
        }
    }

    /**
     * Action to take when condition matches.
     */
    enum Action {
        ENABLE, DISABLE, SERVE_VARIANT
    }

    /**
     * Feature metrics.
     */
    record FeatureMetrics(
        String featureKey,
        long totalEvaluations,
        long enabledCount,
        long disabledCount,
        Map<String, Long> variantCounts,
        double conversionRate,
        Instant lastEvaluatedAt
    ) {}
}

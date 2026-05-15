/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.config.materializer;

import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.mastery.FreshnessPolicy;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;

/**
 * Materializes a {@link FreshnessPolicy} from an {@link AgentDefinition}.
 *
 * <p>This class is responsible for converting the freshness policy configuration
 * from the agent definition's mastery bindings into a typed FreshnessPolicy.
 *
 * @doc.type class
 * @doc.purpose Materializes FreshnessPolicy from AgentDefinition
 * @doc.layer platform
 * @doc.pattern Materializer
 */
public final class FreshnessPolicyMaterializer {

    private FreshnessPolicyMaterializer() {
        // Utility class - prevent instantiation
    }

    /**
     * Materializes a typed FreshnessPolicy from an agent definition's mastery bindings.
     *
     * <p>If no freshness policy configuration is present in mastery bindings,
     * returns the default policy.
     *
     * @param definition the agent definition
     * @return typed FreshnessPolicy
     */
    @NotNull
    public static FreshnessPolicy materialize(@NotNull AgentDefinition definition) {
        Map<String, Object> masteryBindings = definition.getMasteryBindings();
        if (masteryBindings.isEmpty()) {
            return FreshnessPolicy.defaultPolicy();
        }

        String policyId = (String) masteryBindings.get("freshnessPolicyRef");
        if (policyId == null || policyId.isBlank()) {
            return FreshnessPolicy.defaultPolicy();
        }

        // Extract policy configuration from metadata if present
        Object policyConfigObj = definition.getMetadata().get("freshnessPolicy");
        if (policyConfigObj instanceof Map<?, ?> policyConfig) {
            try {
                String defaultStaleAfterStr = (String) policyConfig.get("defaultStaleAfter");
                String maxStaleAfterStr = (String) policyConfig.get("maxStaleAfter");
                Double minEvidenceStrength = (Double) policyConfig.get("minEvidenceStrength");
                Boolean requireRecentVerification = (Boolean) policyConfig.get("requireRecentVerification");

                Duration defaultStaleAfter = defaultStaleAfterStr != null
                        ? Duration.parse(defaultStaleAfterStr)
                        : Duration.ofDays(30);
                Duration maxStaleAfter = maxStaleAfterStr != null
                        ? Duration.parse(maxStaleAfterStr)
                        : Duration.ofDays(90);
                double minEvidenceStrengthVal = minEvidenceStrength != null ? minEvidenceStrength : 0.7;
                boolean requireRecentVerificationVal = requireRecentVerification != null ? requireRecentVerification : true;

                return new FreshnessPolicy(
                        policyId,
                        defaultStaleAfter,
                        maxStaleAfter,
                        minEvidenceStrengthVal,
                        requireRecentVerificationVal
                );
            } catch (Exception e) {
                // Fall back to default policy on any parsing error
                return FreshnessPolicy.defaultPolicy();
            }
        }

        return FreshnessPolicy.defaultPolicy();
    }
}

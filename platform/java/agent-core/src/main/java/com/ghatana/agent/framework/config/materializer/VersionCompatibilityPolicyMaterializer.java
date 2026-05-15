/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.config.materializer;

import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.mastery.VersionCompatibilityPolicy;
import com.ghatana.agent.mastery.VersionConstraint;
import com.ghatana.agent.mastery.VersionScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Materializes a {@link VersionCompatibilityPolicy} from an {@link AgentDefinition}.
 *
 * <p>This class is responsible for converting the version compatibility policy
 * configuration from the agent definition's mastery bindings into a typed
 * VersionCompatibilityPolicy.
 *
 * @doc.type class
 * @doc.purpose Materializes VersionCompatibilityPolicy from AgentDefinition
 * @doc.layer platform
 * @doc.pattern Materializer
 */
public final class VersionCompatibilityPolicyMaterializer {

    private VersionCompatibilityPolicyMaterializer() {
        // Utility class - prevent instantiation
    }

    /**
     * Materializes a typed VersionCompatibilityPolicy from an agent definition's mastery bindings.
     *
     * <p>If no version compatibility policy configuration is present in mastery bindings,
     * returns a default policy with an empty version scope.
     *
     * @param definition the agent definition
     * @return typed VersionCompatibilityPolicy
     */
    @NotNull
    public static VersionCompatibilityPolicy materialize(@NotNull AgentDefinition definition) {
        Map<String, Object> masteryBindings = definition.getMasteryBindings();
        if (masteryBindings.isEmpty()) {
            return VersionCompatibilityPolicy.defaultPolicy("default");
        }

        String policyId = (String) masteryBindings.get("versionCompatibilityPolicyRef");
        if (policyId == null || policyId.isBlank()) {
            return VersionCompatibilityPolicy.defaultPolicy("default");
        }

        // Extract policy configuration from metadata if present
        Object policyConfigObj = definition.getMetadata().get("versionCompatibilityPolicy");
        if (policyConfigObj instanceof Map<?, ?> policyConfig) {
            try {
                Boolean strictMode = (Boolean) policyConfig.get("strictMode");
                Boolean allowMinorVersionDrift = (Boolean) policyConfig.get("allowMinorVersionDrift");
                Boolean allowPatchVersionDrift = (Boolean) policyConfig.get("allowPatchVersionDrift");

                boolean strictModeVal = strictMode != null ? strictMode : false;
                boolean allowMinorVersionDriftVal = allowMinorVersionDrift != null ? allowMinorVersionDrift : true;
                boolean allowPatchVersionDriftVal = allowPatchVersionDrift != null ? allowPatchVersionDrift : true;

                // Extract version scope constraints from policy configuration
                VersionScope versionScope = extractVersionScope(policyConfig);

                return new VersionCompatibilityPolicy(
                        policyId,
                        versionScope,
                        strictModeVal,
                        allowMinorVersionDriftVal,
                        allowPatchVersionDriftVal
                );
            } catch (Exception e) {
                // Fall back to default policy on any parsing error
                return VersionCompatibilityPolicy.defaultPolicy(policyId);
            }
        }

        return VersionCompatibilityPolicy.defaultPolicy(policyId);
    }

    /**
     * Extracts a {@link VersionScope} from a policy-config map.
     * Reads optional {@code active}, {@code maintenance}, and {@code obsolete} constraint lists.
     */
    @NotNull
    private static VersionScope extractVersionScope(@NotNull Map<?, ?> policyConfig) {
        return new VersionScope(
                extractConstraintList(policyConfig.get("active")),
                extractConstraintList(policyConfig.get("maintenance")),
                extractConstraintList(policyConfig.get("obsolete"))
        );
    }

    /**
     * Converts a raw list of constraint maps to typed {@link VersionConstraint} objects.
     */
    @NotNull
    private static List<VersionConstraint> extractConstraintList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<VersionConstraint> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Object kind = m.get("kind");
                Object name = m.get("name");
                Object range = m.get("range");
                Object ecosystem = m.get("ecosystem");
                if (kind instanceof String k && name instanceof String n
                        && range instanceof String r && ecosystem instanceof String e
                        && !k.isBlank() && !n.isBlank() && !r.isBlank() && !e.isBlank()) {
                    result.add(new VersionConstraint(k, n, r, e));
                }
            }
        }
        return List.copyOf(result);
    }
}

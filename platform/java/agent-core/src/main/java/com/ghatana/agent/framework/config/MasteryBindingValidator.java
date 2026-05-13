/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.agent.framework.config;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates mastery binding configuration including namespace, registry ref,
 * and policy references.
 *
 * @doc.type class
 * @doc.purpose Mastery binding validation
 * @doc.layer agent-core
 * @doc.pattern Specification
 */
public final class MasteryBindingValidator {

    private MasteryBindingValidator() {
        // Utility class
    }

    /**
     * Validates mastery binding configuration for an agent definition.
     *
     * @param masteryBindings the mastery bindings map
     * @param skillRefs the skill refs list
     * @param masteryPolicyRefs the mastery policy refs list
     * @param evaluationRefs the evaluation refs list
     * @return list of validation error messages (empty if valid)
     */
    @NotNull
    public static List<String> validate(
            @NotNull Map<String, Object> masteryBindings,
            @NotNull List<String> skillRefs,
            @NotNull List<String> masteryPolicyRefs,
            @NotNull List<String> evaluationRefs
    ) {
        List<String> errors = new ArrayList<>();

        // If masteryBindings exists, it must include required fields
        if (!masteryBindings.isEmpty()) {
            if (!masteryBindings.containsKey("namespace")) {
                errors.add("[mastery] masteryBindings must include 'namespace'");
            } else {
                String namespace = (String) masteryBindings.get("namespace");
                if (namespace == null || namespace.isBlank()) {
                    errors.add("[mastery] masteryBindings 'namespace' must not be blank");
                }
            }

            if (!masteryBindings.containsKey("registryRef")) {
                errors.add("[mastery] masteryBindings must include 'registryRef'");
            } else {
                String registryRef = (String) masteryBindings.get("registryRef");
                if (registryRef == null || registryRef.isBlank()) {
                    errors.add("[mastery] masteryBindings 'registryRef' must not be blank");
                }
            }

            // Required policy refs when namespace and registryRef are present
            if (!masteryBindings.containsKey("freshnessPolicyRef")) {
                errors.add("[mastery] masteryBindings must include 'freshnessPolicyRef'");
            } else {
                String freshnessPolicyRef = (String) masteryBindings.get("freshnessPolicyRef");
                if (freshnessPolicyRef != null && freshnessPolicyRef.isBlank()) {
                    errors.add("[mastery] masteryBindings 'freshnessPolicyRef' must not be blank");
                }
            }

            if (!masteryBindings.containsKey("versionCompatibilityPolicyRef")) {
                errors.add("[mastery] masteryBindings must include 'versionCompatibilityPolicyRef'");
            } else {
                String versionCompatibilityPolicyRef = (String) masteryBindings.get("versionCompatibilityPolicyRef");
                if (versionCompatibilityPolicyRef != null && versionCompatibilityPolicyRef.isBlank()) {
                    errors.add("[mastery] masteryBindings 'versionCompatibilityPolicyRef' must not be blank");
                }
            }

            if (masteryBindings.containsKey("obsolescencePolicyRef")) {
                String obsolescencePolicyRef = (String) masteryBindings.get("obsolescencePolicyRef");
                if (obsolescencePolicyRef != null && obsolescencePolicyRef.isBlank()) {
                    errors.add("[mastery] masteryBindings 'obsolescencePolicyRef' must not be blank if provided");
                }
            }
        }

        // Any agent with masteryBindings must declare skillRefs
        if (!masteryBindings.isEmpty() && skillRefs.isEmpty()) {
            errors.add("[mastery] agents with masteryBindings must declare skillRefs");
        }

        // Validate skillRefs are non-blank
        for (String skillRef : skillRefs) {
            if (skillRef == null || skillRef.isBlank()) {
                errors.add("[mastery] skillRefs must not contain blank values");
            }
        }

        // Validate masteryPolicyRefs are non-blank
        for (String policyRef : masteryPolicyRefs) {
            if (policyRef == null || policyRef.isBlank()) {
                errors.add("[mastery] masteryPolicyRefs must not contain blank values");
            }
        }

        // Validate evaluationRefs are non-blank
        for (String evalRef : evaluationRefs) {
            if (evalRef == null || evalRef.isBlank()) {
                errors.add("[mastery] evaluationRefs must not contain blank values");
            }
        }

        return errors;
    }
}

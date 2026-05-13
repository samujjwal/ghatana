/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery.transition;

import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Default implementation of MasteryTransitionPolicy.
 *
 * <p>Transition rules:
 * <ul>
 *   <li>UNKNOWN → OBSERVED: requires at least one trace or verified source</li>
 *   <li>OBSERVED → PRACTICED: requires repeated episodes or sandbox experiments</li>
 *   <li>PRACTICED → COMPETENT: requires procedure exists and basic evaluation passes</li>
 *   <li>COMPETENT → MASTERED: requires regression, safety, recovery, and compatibility tests pass</li>
 *   <li>MASTERED → MAINTENANCE_ONLY: new active version exists; old version still used</li>
 *   <li>Any → OBSOLETE: docs/API/security/runtime contradiction or repeated failures</li>
 *   <li>Any → QUARANTINED: unsafe behavior or failed safety evaluation</li>
 *   <li>OBSOLETE → RETIRED: no active retrieval/use case remains</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default implementation of MasteryTransitionPolicy
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public final class DefaultMasteryTransitionPolicy implements MasteryTransitionPolicy {

    @Override
    @NotNull
    public TransitionValidation canTransition(
            @NotNull MasteryState fromState,
            @NotNull MasteryState toState,
            @NotNull Map<String, String> evidence
    ) {
        // Direct UNKNOWN to MASTERED is not allowed
        if (fromState == MasteryState.UNKNOWN && toState == MasteryState.MASTERED) {
            return TransitionValidation.denied("Cannot transition directly from UNKNOWN to MASTERED");
        }

        // Direct UNKNOWN to COMPETENT is not allowed
        if (fromState == MasteryState.UNKNOWN && toState == MasteryState.COMPETENT) {
            return TransitionValidation.denied("Cannot transition directly from UNKNOWN to COMPETENT");
        }

        // Direct UNKNOWN to PRACTICED is not allowed
        if (fromState == MasteryState.UNKNOWN && toState == MasteryState.PRACTICED) {
            return TransitionValidation.denied("Cannot transition directly from UNKNOWN to PRACTICED");
        }

        // UNKNOWN to OBSERVED requires minimal evidence (trace or verified source)
        if (fromState == MasteryState.UNKNOWN && toState == MasteryState.OBSERVED) {
            if (!hasRequiredEvidence(evidence, "trace") && !hasRequiredEvidence(evidence, "trace_id")
                    && !hasRequiredEvidence(evidence, "verified_source_id")) {
                return TransitionValidation.denied("UNKNOWN → OBSERVED requires at least one trace, trace_id, or verified_source_id");
            }
            return TransitionValidation.success();
        }

        // OBSERVED to PRACTICED requires episode evidence (repeated episodes or sandbox experiments)
        if (fromState == MasteryState.OBSERVED && toState == MasteryState.PRACTICED) {
            if (!hasRequiredEvidence(evidence, "episodes") && !hasRequiredEvidence(evidence, "sandbox_experiments")) {
                return TransitionValidation.denied("OBSERVED → PRACTICED requires episodes or sandbox_experiments evidence");
            }
            return TransitionValidation.success();
        }

        // PRACTICED to COMPETENT requires procedure and evaluation evidence
        if (fromState == MasteryState.PRACTICED && toState == MasteryState.COMPETENT) {
            if (!hasRequiredEvidence(evidence, "procedure_id")) {
                return TransitionValidation.denied("PRACTICED → COMPETENT requires procedure_id evidence");
            }
            if (!hasBooleanEvidence(evidence, "basic_eval_passed", true)) {
                return TransitionValidation.denied("PRACTICED → COMPETENT requires basic_eval_passed=true");
            }
            return TransitionValidation.success();
        }

        // COMPETENT to MASTERED requires comprehensive evaluation evidence
        if (fromState == MasteryState.COMPETENT && toState == MasteryState.MASTERED) {
            if (!hasBooleanEvidence(evidence, "regression_passed", true)) {
                return TransitionValidation.denied("COMPETENT → MASTERED requires regression_passed=true");
            }
            if (!hasBooleanEvidence(evidence, "safety_passed", true)) {
                return TransitionValidation.denied("COMPETENT → MASTERED requires safety_passed=true");
            }
            if (!hasBooleanEvidence(evidence, "recovery_passed", true)) {
                return TransitionValidation.denied("COMPETENT → MASTERED requires recovery_passed=true");
            }
            if (!hasBooleanEvidence(evidence, "compatibility_passed", true)) {
                return TransitionValidation.denied("COMPETENT → MASTERED requires compatibility_passed=true");
            }
            return TransitionValidation.success();
        }

        // MASTERED to MAINTENANCE_ONLY requires new active version evidence
        if (fromState == MasteryState.MASTERED && toState == MasteryState.MAINTENANCE_ONLY) {
            if (!hasRequiredEvidence(evidence, "new_active_version_id")) {
                return TransitionValidation.denied("MASTERED → MAINTENANCE_ONLY requires new_active_version_id evidence");
            }
            return TransitionValidation.success();
        }

        // MASTERED cannot go directly to OBSOLETE; must go through MAINTENANCE_ONLY first
        if (fromState == MasteryState.MASTERED && toState == MasteryState.OBSOLETE) {
            return TransitionValidation.denied("MASTERED → OBSOLETE is not allowed; must transition through MAINTENANCE_ONLY");
        }

        // MAINTENANCE_ONLY to OBSOLETE requires version end-of-life or replacement evidence
        if (fromState == MasteryState.MAINTENANCE_ONLY && toState == MasteryState.OBSOLETE) {
            if (!hasRequiredEvidence(evidence, "end_of_life") && !hasRequiredEvidence(evidence, "replaced_by_newer")) {
                return TransitionValidation.denied("MAINTENANCE_ONLY → OBSOLETE requires end_of_life or replaced_by_newer evidence");
            }
            return TransitionValidation.success();
        }

        // QUARANTINED can be reached from any state (for safety violations)
        if (toState == MasteryState.QUARANTINED) {
            if (!hasRequiredEvidence(evidence, "safety_violation") && !hasRequiredEvidence(evidence, "unsafe_behavior")) {
                return TransitionValidation.denied("QUARANTINED requires safety_violation or unsafe_behavior evidence");
            }
            return TransitionValidation.success();
        }

        // QUARANTINED can only exit with explicit human/security review approval
        if (fromState == MasteryState.QUARANTINED) {
            if (!hasBooleanEvidence(evidence, "human_review_approved", true) && !hasBooleanEvidence(evidence, "security_review_approved", true)) {
                return TransitionValidation.denied("QUARANTINED exit requires human_review_approved=true or security_review_approved=true");
            }
            // Additional check: ensure the target state is appropriate for quarantine exit
            if (toState != MasteryState.OBSERVED && toState != MasteryState.PRACTICED && toState != MasteryState.COMPETENT) {
                return TransitionValidation.denied("QUARANTINED can only exit to OBSERVED, PRACTICED, or COMPETENT states after review");
            }
            return TransitionValidation.success();
        }

        // OBSOLETE can be reached from any active state or MAINTENANCE_ONLY with appropriate evidence
        if (toState == MasteryState.OBSOLETE) {
            if (!fromState.isActiveForRetrieval() && fromState != MasteryState.MAINTENANCE_ONLY) {
                return TransitionValidation.denied("OBSOLETE can only be reached from active states or MAINTENANCE_ONLY");
            }
            if (!hasRequiredEvidence(evidence, "contradiction") && !hasRequiredEvidence(evidence, "repeated_failures") 
                    && !hasRequiredEvidence(evidence, "security_advisory") && !hasRequiredEvidence(evidence, "api_break")) {
                return TransitionValidation.denied("OBSOLETE requires contradiction, repeated_failures, security_advisory, or api_break evidence");
            }
            return TransitionValidation.success();
        }

        // RETIRED can only be reached from OBSOLETE with confirmation
        if (toState == MasteryState.RETIRED) {
            if (fromState != MasteryState.OBSOLETE) {
                return TransitionValidation.denied("RETIRED can only be reached from OBSOLETE");
            }
            if (!hasRequiredEvidence(evidence, "no_active_use_case")) {
                return TransitionValidation.denied("RETIRED requires no_active_use_case evidence");
            }
            return TransitionValidation.success();
        }

        // PRACTICED to OBSERVED is a demotion requiring explicit evidence
        if (fromState == MasteryState.PRACTICED && toState == MasteryState.OBSERVED) {
            if (!hasRequiredEvidence(evidence, "demotion_reason")) {
                return TransitionValidation.denied("PRACTICED → OBSERVED demotion requires demotion_reason evidence");
            }
            return TransitionValidation.success();
        }

        // COMPETENT to PRACTICED or OBSERVED is a demotion requiring explicit evidence
        if (fromState == MasteryState.COMPETENT && (toState == MasteryState.PRACTICED || toState == MasteryState.OBSERVED)) {
            if (!hasRequiredEvidence(evidence, "demotion_reason")) {
                return TransitionValidation.denied("COMPETENT → " + toState + " demotion requires demotion_reason evidence");
            }
            return TransitionValidation.success();
        }

        // MASTERED to COMPETENT, PRACTICED, or OBSERVED is a demotion requiring explicit evidence
        if (fromState == MasteryState.MASTERED && (toState == MasteryState.COMPETENT || toState == MasteryState.PRACTICED || toState == MasteryState.OBSERVED)) {
            if (!hasRequiredEvidence(evidence, "demotion_reason")) {
                return TransitionValidation.denied("MASTERED → " + toState + " demotion requires demotion_reason evidence");
            }
            return TransitionValidation.success();
        }

        // MAINTENANCE_ONLY back to MASTERED is allowed with replacement evidence
        if (fromState == MasteryState.MAINTENANCE_ONLY && toState == MasteryState.MASTERED) {
            if (!hasRequiredEvidence(evidence, "replacement_version_id")) {
                return TransitionValidation.denied("MAINTENANCE_ONLY → MASTERED requires replacement_version_id evidence");
            }
            return TransitionValidation.success();
        }

        return TransitionValidation.denied("Invalid transition from " + fromState + " to " + toState);
    }

    /**
     * Checks if the evidence map contains a required non-null, non-empty key.
     *
     * @param evidence evidence map
     * @param key required key
     * @return true if the key exists with a non-empty value
     */
    private static boolean hasRequiredEvidence(@NotNull Map<String, String> evidence, @NotNull String key) {
        String value = evidence.get(key);
        return value != null && !value.isBlank();
    }

    /**
     * Checks if the evidence map contains a boolean key with the expected value.
     *
     * @param evidence evidence map
     * @param key boolean key
     * @param expected expected boolean value
     * @return true if the key exists and matches the expected value
     */
    private static boolean hasBooleanEvidence(@NotNull Map<String, String> evidence, @NotNull String key, boolean expected) {
        String value = evidence.get(key);
        if (value == null || value.isBlank()) {
            return false;
        }
        return Boolean.parseBoolean(value) == expected;
    }
}

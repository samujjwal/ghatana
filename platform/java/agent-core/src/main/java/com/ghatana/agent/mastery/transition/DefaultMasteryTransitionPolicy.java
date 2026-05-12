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

        // UNKNOWN to OBSERVED requires minimal evidence
        if (fromState == MasteryState.UNKNOWN && toState == MasteryState.OBSERVED) {
            if (evidence.isEmpty()) {
                return TransitionValidation.denied("UNKNOWN → OBSERVED requires at least one trace or verified source");
            }
            return TransitionValidation.success();
        }

        // OBSERVED to PRACTICED requires episode evidence
        if (fromState == MasteryState.OBSERVED && toState == MasteryState.PRACTICED) {
            if (!evidence.containsKey("episodes") && !evidence.containsKey("sandbox_experiments")) {
                return TransitionValidation.denied("OBSERVED → PRACTICED requires repeated episodes or sandbox experiments");
            }
            return TransitionValidation.success();
        }

        // PRACTICED to COMPETENT requires procedure and evaluation
        if (fromState == MasteryState.PRACTICED && toState == MasteryState.COMPETENT) {
            if (!evidence.containsKey("procedure_id")) {
                return TransitionValidation.denied("PRACTICED → COMPETENT requires procedure to exist");
            }
            if (!evidence.containsKey("basic_eval_passed") || !Boolean.parseBoolean(evidence.get("basic_eval_passed"))) {
                return TransitionValidation.denied("PRACTICED → COMPETENT requires basic evaluation to pass");
            }
            return TransitionValidation.success();
        }

        // COMPETENT to MASTERED requires comprehensive evaluation
        if (fromState == MasteryState.COMPETENT && toState == MasteryState.MASTERED) {
            if (!evidence.containsKey("regression_passed") || !Boolean.parseBoolean(evidence.get("regression_passed"))) {
                return TransitionValidation.denied("COMPETENT → MASTERED requires regression tests to pass");
            }
            if (!evidence.containsKey("safety_passed") || !Boolean.parseBoolean(evidence.get("safety_passed"))) {
                return TransitionValidation.denied("COMPETENT → MASTERED requires safety tests to pass");
            }
            if (!evidence.containsKey("recovery_passed") || !Boolean.parseBoolean(evidence.get("recovery_passed"))) {
                return TransitionValidation.denied("COMPETENT → MASTERED requires recovery tests to pass");
            }
            if (!evidence.containsKey("compatibility_passed") || !Boolean.parseBoolean(evidence.get("compatibility_passed"))) {
                return TransitionValidation.denied("COMPETENT → MASTERED requires compatibility tests to pass");
            }
            return TransitionValidation.success();
        }

        // MASTERED to MAINTENANCE_ONLY requires new active version
        if (fromState == MasteryState.MASTERED && toState == MasteryState.MAINTENANCE_ONLY) {
            if (!evidence.containsKey("new_active_version_id")) {
                return TransitionValidation.denied("MASTERED → MAINTENANCE_ONLY requires new active version to exist");
            }
            return TransitionValidation.success();
        }

        // QUARANTINED can be reached from any state (for safety)
        if (toState == MasteryState.QUARANTINED) {
            if (!evidence.containsKey("safety_violation") && !evidence.containsKey("unsafe_behavior")) {
                return TransitionValidation.denied("QUARANTINED requires safety violation or unsafe behavior");
            }
            return TransitionValidation.success();
        }

        // OBSOLETE can be reached from any active state
        if (toState == MasteryState.OBSOLETE && fromState.isActiveForRetrieval()) {
            if (!evidence.containsKey("contradiction") && !evidence.containsKey("repeated_failures")) {
                return TransitionValidation.denied("OBSOLETE requires contradiction or repeated failures");
            }
            return TransitionValidation.success();
        }

        // RETIRED can only be reached from OBSOLETE
        if (toState == MasteryState.RETIRED) {
            if (fromState != MasteryState.OBSOLETE) {
                return TransitionValidation.denied("RETIRED can only be reached from OBSOLETE");
            }
            if (!evidence.containsKey("no_active_use_case")) {
                return TransitionValidation.denied("RETIRED requires confirmation of no active use case");
            }
            return TransitionValidation.success();
        }

        // Forward progression through lifecycle
        if (fromState == MasteryState.OBSERVED && toState == MasteryState.PRACTICED) {
            return TransitionValidation.success();
        }

        if (fromState == MasteryState.PRACTICED && toState == MasteryState.COMPETENT) {
            return TransitionValidation.success();
        }

        if (fromState == MasteryState.COMPETENT && toState == MasteryState.MASTERED) {
            return !evidence.isEmpty() ? TransitionValidation.success() : TransitionValidation.denied("COMPETENT → MASTERED requires evaluation evidence");
        }

        return TransitionValidation.denied("Invalid transition from " + fromState + " to " + toState);
    }
}

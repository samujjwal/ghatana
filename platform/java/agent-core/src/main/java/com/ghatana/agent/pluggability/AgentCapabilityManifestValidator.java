/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.pluggability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates an {@link AgentCapabilityManifest} for consistency requirements that
 * cannot be expressed via the record compact constructor alone.
 *
 * <p>Cross-field validation rules:
 * <ul>
 *   <li>If {@code interactionModes} contains {@code ORCHESTRATOR}, the
 *       {@code handoffCapability} must be {@code INITIATOR_ONLY} or {@code BIDIRECTIONAL}.</li>
 *   <li>If {@code interactionModes} contains {@code COLLABORATIVE} and
 *       {@code handoffCapability} is {@code NONE}, handoff is unavailable — warn.</li>
 *   <li>If {@code supervisionRole} is {@code SUPERVISOR}, at least one interaction mode
 *       must be {@code ORCHESTRATOR} or {@code COLLABORATIVE}.</li>
 *   <li>If {@code supervisionRole} is {@code STANDALONE}, {@code interactionModes} must
 *       not contain {@code SPECIALIST}.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Cross-field validation for AgentCapabilityManifest
 * @doc.layer platform
 * @doc.pattern Validator
 */
public final class AgentCapabilityManifestValidator {

    private AgentCapabilityManifestValidator() {}

    /**
     * Result of a validation run.
     *
     * @param valid    whether the manifest is valid
     * @param errors   list of error messages (empty if valid)
     * @param warnings list of non-fatal warning messages
     */
    public record ValidationResult(boolean valid, List<String> errors, List<String> warnings) {

        /** Returns a successful result with no errors or warnings. */
        static ValidationResult ok() {
            return new ValidationResult(true, List.of(), List.of());
        }
    }

    /**
     * Validates {@code manifest} against all cross-field rules.
     *
     * @param manifest the manifest to validate; must not be null
     * @return a {@link ValidationResult} describing failures and warnings
     */
    public static ValidationResult validate(AgentCapabilityManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        List<String> errors   = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Rule 1: ORCHESTRATOR mode ⟹ must be able to initiate handoffs
        if (manifest.supports(InteractionMode.ORCHESTRATOR)
                && !manifest.canInitiateHandoff()) {
            errors.add("ORCHESTRATOR agents must declare INITIATOR_ONLY or BIDIRECTIONAL handoffCapability");
        }

        // Rule 2: COLLABORATIVE with NONE handoff — warn
        if (manifest.supports(InteractionMode.COLLABORATIVE)
                && manifest.handoffCapability() == HandoffCapability.NONE) {
            warnings.add("COLLABORATIVE agents typically need handoff capability; current handoffCapability=NONE");
        }

        // Rule 3: SUPERVISOR role ⟹ must be ORCHESTRATOR or COLLABORATIVE
        if (manifest.supervisionRole() == SupervisionRole.SUPERVISOR
                && !manifest.supports(InteractionMode.ORCHESTRATOR)
                && !manifest.supports(InteractionMode.COLLABORATIVE)) {
            errors.add("SUPERVISOR role requires ORCHESTRATOR or COLLABORATIVE interaction mode");
        }

        // Rule 4: STANDALONE role ⟹ must not be SPECIALIST
        if (manifest.supervisionRole() == SupervisionRole.STANDALONE
                && manifest.supports(InteractionMode.SPECIALIST)) {
            errors.add("STANDALONE agents cannot declare SPECIALIST interaction mode");
        }

        // Rule 5: Hierarchical interaction modes require explicit supervision role.
        if (manifest.supervisionRole() == null
                && (manifest.supports(InteractionMode.ORCHESTRATOR)
                || manifest.supports(InteractionMode.SPECIALIST))) {
            errors.add("ORCHESTRATOR/SPECIALIST interaction modes require explicit supervisionRole");
        }

        // Rule 6: SUPERVISOR role must be able to initiate handoff.
        if (manifest.supervisionRole() == SupervisionRole.SUPERVISOR
                && !manifest.canInitiateHandoff()) {
            errors.add("SUPERVISOR role requires INITIATOR_ONLY or BIDIRECTIONAL handoffCapability");
        }

        // Rule 7: SUBORDINATE role must be able to receive handoff.
        if (manifest.supervisionRole() == SupervisionRole.SUBORDINATE
                && !manifest.canReceiveHandoff()) {
            errors.add("SUBORDINATE role requires RECEIVER_ONLY or BIDIRECTIONAL handoffCapability");
        }

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, List.copyOf(errors), List.copyOf(warnings));
    }
}

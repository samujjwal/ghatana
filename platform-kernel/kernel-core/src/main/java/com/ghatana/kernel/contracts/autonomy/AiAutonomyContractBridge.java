/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts.autonomy;

import com.ghatana.kernel.contracts.AutonomyContract;

import java.util.List;

/**
 * Bridge interface for AI governance subsystems to connect to the canonical
 * {@link AutonomyContract} model.
 *
 * <p>Implementors include:</p>
 * <ul>
 *   <li><b>AI Governance Policy Engine</b> — maps policy checks to governance rules</li>
 *   <li><b>Model Registry</b> — maps model lifecycle to agent capability declarations</li>
 *   <li><b>HITL Review Workflow</b> — enforces human-in-the-loop for autonomous agents</li>
 * </ul>
 *
 * <p>The bridge unifies model governance, explainability, and certification
 * hooks into the canonical contract surface, enabling Aura-class autonomous
 * agents to declare and validate their governance posture.</p>
 *
 * @doc.type interface
 * @doc.purpose Bridge between AI governance subsystems and canonical autonomy contract
 * @doc.layer core
 * @doc.pattern Adapter, Bridge
 * @doc.gaa.lifecycle perceive
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public interface AiAutonomyContractBridge {

    /**
     * Result of a model promotion validation against contract policies.
     */
    record PromotionValidationResult(boolean approved, List<String> blockers,
                                     List<String> warnings) {
        public static final PromotionValidationResult APPROVED =
            new PromotionValidationResult(true, List.of(), List.of());

        public static PromotionValidationResult blocked(List<String> blockers) {
            return new PromotionValidationResult(false, List.copyOf(blockers), List.of());
        }
    }

    /**
     * Exports the current AI governance state for a tenant as an {@link AutonomyContract}.
     *
     * @param tenantId the tenant to export governance state for
     * @return the autonomy contract reflecting current governance posture
     */
    AutonomyContract exportCurrentState(String tenantId);

    /**
     * Validates a model promotion against contract-level governance policies.
     *
     * @param modelId the model being promoted
     * @param targetStatus the target lifecycle status (e.g., "VALIDATED", "DEPLOYED")
     * @return validation result with blockers if any
     */
    PromotionValidationResult validatePromotion(String modelId, String targetStatus);

    /**
     * Returns the governance rules that apply to a given agent tier.
     *
     * @param tier the agent tier to query rules for
     * @return applicable governance rules
     */
    List<AutonomyContract.ModelGovernanceRule> getRulesForTier(AutonomyContract.AgentTier tier);
}

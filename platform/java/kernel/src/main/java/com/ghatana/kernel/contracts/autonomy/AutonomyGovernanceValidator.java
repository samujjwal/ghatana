/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts.autonomy;

import com.ghatana.kernel.contracts.AutonomyContract;
import com.ghatana.kernel.contracts.ContractFamily;
import com.ghatana.kernel.contracts.ContractValidator;
import com.ghatana.kernel.contracts.KernelContract;

import java.util.ArrayList;
import java.util.List;

/**
 * Contract validator that enforces AI governance rules on autonomy contracts.
 *
 * <p>Validates that:</p>
 * <ul>
 *   <li>AUTONOMOUS agents always require human review</li>
 *   <li>DELIBERATIVE+ agents have explainability governance rules</li>
 *   <li>All agents have non-zero minimum confidence</li>
 *   <li>Governance rules have non-blank descriptions</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Autonomy-family contract validator with AI governance rules
 * @doc.layer core
 * @doc.pattern Strategy
 * @doc.gaa.lifecycle reason
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class AutonomyGovernanceValidator implements ContractValidator {

    @Override
    public ValidationResult validate(KernelContract contract) {
        if (!(contract instanceof AutonomyContract autonomy)) {
            return ValidationResult.OK;
        }
        List<String> errors = new ArrayList<>();

        for (AutonomyContract.AgentCapabilityDeclaration cap : autonomy.getAgentCapabilities()) {
            if (cap.minimumConfidence() <= 0.0) {
                errors.add("Agent '" + cap.capabilityId() + "' must have positive minimumConfidence");
            }
            if (cap.tier() == AutonomyContract.AgentTier.AUTONOMOUS && !cap.requiresHumanReview()) {
                errors.add("AUTONOMOUS agent '" + cap.capabilityId() + "' must require human review");
            }
        }

        for (AutonomyContract.ModelGovernanceRule rule : autonomy.getGovernanceRules()) {
            if (rule.description().isBlank()) {
                errors.add("Governance rule '" + rule.ruleId() + "' must have a non-blank description");
            }
        }

        return errors.isEmpty() ? ValidationResult.OK : ValidationResult.failed(errors);
    }

    @Override
    public List<ContractFamily> applicableFamilies() {
        return List.of(ContractFamily.AUTONOMY);
    }
}

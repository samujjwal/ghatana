package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Evolve phase model.
 *
 * @doc.type record
 * @doc.purpose Model Evolve phase data
 * @doc.layer product
 * @doc.pattern PhaseModel
 */
public record EvolvePhaseModel(
        String proposal,
        String impactSummary,
        String diffSummary,
        List<String> validationRequirements,
        String approvalState,
        String rollbackPath,
        String rerunTarget
) {
}

package com.ghatana.yappc.services.phase;

import java.util.List;

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

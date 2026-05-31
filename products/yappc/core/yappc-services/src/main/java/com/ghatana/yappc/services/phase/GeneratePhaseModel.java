package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Generate phase model.
 *
 * @doc.type record
 * @doc.purpose Model Generate phase data
 * @doc.layer product
 * @doc.pattern PhaseModel
 */
public record GeneratePhaseModel(
        List<String> generatedArtifacts,
        String assuranceStatus,
        String reviewState,
        String diffSummary,
        String buildStatus,
        String handoffReadiness
) {
}

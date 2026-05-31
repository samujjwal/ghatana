package com.ghatana.yappc.services.phase;

import java.util.List;

public record GeneratePhaseModel(
        List<String> generatedArtifacts,
        String assuranceStatus,
        String reviewState,
        String diffSummary,
        String buildStatus,
        String handoffReadiness
) {
}

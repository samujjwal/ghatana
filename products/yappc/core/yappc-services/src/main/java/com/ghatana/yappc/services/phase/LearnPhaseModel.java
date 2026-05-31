package com.ghatana.yappc.services.phase;

import java.util.List;

public record LearnPhaseModel(
        List<String> learnedSignals,
        String sourceEvent,
        double confidence,
        String approvalState,
        String rollbackPath,
        List<String> evidenceIds
) {
}

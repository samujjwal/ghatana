package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Learn phase model.
 *
 * @doc.type record
 * @doc.purpose Model Learn phase data
 * @doc.layer product
 * @doc.pattern PhaseModel
 */
public record LearnPhaseModel(
        List<String> learnedSignals,
        String sourceEvent,
        double confidence,
        String approvalState,
        String rollbackPath,
        List<String> evidenceIds
) {
}

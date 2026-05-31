package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Run phase model.
 *
 * @doc.type record
 * @doc.purpose Model Run phase data
 * @doc.layer product
 * @doc.pattern PhaseModel
 */
public record RunPhaseModel(
        String latestRunId,
        String status,
        String targetEnvironment,
        String targetVersion,
        boolean rollbackReady,
        boolean promoteReady,
        List<String> remediationHints,
        List<String> runHistory
) {
}

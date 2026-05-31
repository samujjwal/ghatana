package com.ghatana.yappc.services.phase;

import java.util.List;

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

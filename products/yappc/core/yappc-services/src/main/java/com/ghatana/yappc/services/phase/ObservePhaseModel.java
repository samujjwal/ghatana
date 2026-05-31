package com.ghatana.yappc.services.phase;

import java.util.List;

public record ObservePhaseModel(
        String previewStatus,
        String runtimeStatus,
        List<String> traceIds,
        List<String> incidents,
        List<String> dependencyHealth,
        String remediation
) {
}

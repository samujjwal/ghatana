package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Observe phase model.
 *
 * @doc.type record
 * @doc.purpose Model Observe phase data
 * @doc.layer product
 * @doc.pattern PhaseModel
 */
public record ObservePhaseModel(
        String previewStatus,
        String runtimeStatus,
        List<String> traceIds,
        List<String> incidents,
        List<String> dependencyHealth,
        String remediation
) {
}

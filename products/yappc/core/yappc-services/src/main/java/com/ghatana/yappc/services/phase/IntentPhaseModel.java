package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Intent-phase lifecycle model.
 */
public record IntentPhaseModel(
        List<String> goals,
        List<String> personas,
        List<String> constraints,
        List<String> successCriteria,
        List<String> unresolvedFields,
        String version,
        List<String> sourceArtifactIds
) {
}

package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Shape phase model.
 *
 * @doc.type record
 * @doc.purpose Model Shape phase data
 * @doc.layer product
 * @doc.pattern PhaseModel
 */
public record ShapePhaseModel(
        List<String> selectedSurfaces,
        List<String> runtimeChoices,
        List<String> architectureDecisions,
        String canvasDocumentId,
        String uiBuilderDocumentId,
        List<String> dependencies,
        List<String> unresolvedDesignGaps
) {
}

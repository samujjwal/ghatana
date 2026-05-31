package com.ghatana.yappc.services.phase;

import java.util.List;

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

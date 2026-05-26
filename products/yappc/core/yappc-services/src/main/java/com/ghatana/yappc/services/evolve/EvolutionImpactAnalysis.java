package com.ghatana.yappc.services.evolve;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Structured dependency, artifact, and runtime blast-radius analysis for Evolve proposals
 * @doc.layer service
 * @doc.pattern Value Object
 */
public record EvolutionImpactAnalysis(
        String status,
        String truthSource,
        List<String> affectedSurfaces,
        List<String> affectedModules,
        List<String> affectedTests,
        List<String> runtimeImpacts,
        List<String> dependencyNodeIds,
        List<String> notes
) {
    public static EvolutionImpactAnalysis unavailable(String note) {
        return new EvolutionImpactAnalysis(
                "UNAVAILABLE",
                "artifact-graph",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(note));
    }

    public static EvolutionImpactAnalysis empty(String truthSource, List<String> notes) {
        return new EvolutionImpactAnalysis(
                "NO_IMPACT_FOUND",
                truthSource,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.copyOf(notes));
    }

    public Map<String, Object> toMetadata() {
        return Map.of(
                "status", status,
                "truthSource", truthSource,
                "affectedSurfaces", affectedSurfaces,
                "affectedModules", affectedModules,
                "affectedTests", affectedTests,
                "runtimeImpacts", runtimeImpacts,
                "dependencyNodeIds", dependencyNodeIds,
                "notes", notes);
    }
}

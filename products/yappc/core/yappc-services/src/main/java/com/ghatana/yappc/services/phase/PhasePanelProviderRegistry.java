package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for backend phase panel providers.
 *
 * @doc.type class
 * @doc.purpose Maps lifecycle phase names to canonical panel providers
 * @doc.layer services
 * @doc.pattern Registry
 */
public final class PhasePanelProviderRegistry {

    private static final List<String> LIFECYCLE_ORDER = List.of(
            "intent", "shape", "validate", "generate", "run", "observe", "learn", "evolve");

    private final Map<String, PhasePanelProvider> providers;

    public PhasePanelProviderRegistry(LearningInsightService learningInsightService, EvolutionPlanService evolutionPlanService) {
        this(List.of(
                new IntentPhasePanelProvider(),
                new ShapePhasePanelProvider(),
                new ValidatePhasePanelProvider(),
                new GeneratePhasePanelProvider(),
                new RunPhasePanelProvider(),
                new ObservePhasePanelProvider(),
                new LearnPhasePanelProvider(learningInsightService),
                new EvolvePhasePanelProvider(evolutionPlanService)
        ));
    }

    public PhasePanelProviderRegistry(List<PhasePanelProvider> providers) {
        Map<String, PhasePanelProvider> resolved = new LinkedHashMap<>();
        for (PhasePanelProvider provider : providers) {
            resolved.put(provider.phase(), provider);
        }
        this.providers = Map.copyOf(resolved);
    }

    public List<PhasePacket.PhasePanelView> buildPanels(PhasePanelInput input) {
        return LIFECYCLE_ORDER.stream()
                .map(providers::get)
                .filter(java.util.Objects::nonNull)
                .map(provider -> provider.build(input))
                .toList();
    }
}

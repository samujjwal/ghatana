package com.ghatana.softwareorg.domain.devsecops;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * AgentMetadata holds DevSecOps-specific metadata that sits alongside the
 * structural AgentSpec configuration.
 */
@Value
@Builder
public class AgentMetadata {

    DevSecOpsStage devsecopsStage;

    @Singular
    List<ProductLifecyclePhase> lifecyclePhases;

    @Singular
    List<PersonaRef> personas;

    @Singular("capabilityArchetype")
    List<String> capabilityArchetypes;

    @Singular
    List<String> categories;

    @Singular
    List<String> domainTags;

    @Singular
    List<DomainOverlay> overlays;

    @Singular
    List<String> tags;

    @Singular
    Map<String, String> customAttributes;

    @Singular
    List<String> runtimeManifestIds;

    @Singular
    List<String> runtimeCapabilitiesLabels;
}

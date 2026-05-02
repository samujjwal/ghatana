/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.yappc.agent.integration;

import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.aep.registry.AgentRegistryContracts;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for YAPPC → AEP migration (Phase 5). 
 *
 * <p>Verifies that:
 * <ul>
 *   <li>YAPPC agents are discoverable through the central AEP catalog</li>
 *   <li>Phase-based derived views produce correct groupings</li>
 *   <li>Step-name resolution maps correctly to catalog IDs</li>
 *   <li>Capability-based search returns only YAPPC-owned agents</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Phase 5 YAPPC migration acceptance tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YAPPC → AEP Migration Acceptance Tests")
class YappcAepIntegrationTest extends EventloopTestBase {

    private YappcAepIntegration integration;
    private AgentRegistryContracts registryService;

    @BeforeEach
    void setUp() { 
        registryService = mock(AgentRegistryContracts.class); 

        // Stub agent entries: mix of YAPPC and non-YAPPC agents
        List<CatalogAgentEntry> allAgents = List.of( 
                buildEntry("agent.yappc.architecture.domain-modeler", "yappc", 
                        Set.of("architecture", "domain-modeling")), 
                buildEntry("agent.yappc.architecture.security-architect", "yappc", 
                        Set.of("architecture", "security")), 
                buildEntry("agent.yappc.implementation.code-reviewer", "yappc", 
                        Set.of("code-review", "implementation")), 
                buildEntry("agent.yappc.testing.test-generator", "yappc", 
                        Set.of("testing", "code-generation")), 
                buildEntry("agent.yappc.testing.performance-test", "yappc", 
                        Set.of("testing", "performance")), 
                // Non-YAPPC agents — should be filtered out
                buildEntry("agent.data-cloud.schema-validator", "data-cloud", 
                        Set.of("validation", "schema")), 
                buildEntry("agent.platform.health-checker", "platform", 
                        Set.of("monitoring", "health")) 
        );

        when(registryService.listAgents()).thenReturn(Promise.of(allAgents)); 
        when(registryService.getAgent("agent.yappc.architecture.domain-modeler"))
                .thenReturn(Promise.of(Optional.of(allAgents.get(0)))); 
        when(registryService.getAgent("agent.yappc.implementation.code-reviewer"))
                .thenReturn(Promise.of(Optional.of(allAgents.get(2)))); 
        when(registryService.getAgent("agent.yappc.nonexistent"))
                .thenReturn(Promise.of(Optional.empty())); 
        when(registryService.findByCapability("architecture"))
                .thenReturn(Promise.of(List.of(allAgents.get(0), allAgents.get(1)))); 
        when(registryService.findByCapability("testing"))
                .thenReturn(Promise.of(List.of(allAgents.get(3), allAgents.get(4)))); 
        when(registryService.findByCapability("validation"))
                .thenReturn(Promise.of(List.of(allAgents.get(5)))); 

        integration = new YappcAepIntegration(registryService); 
    }

    @Test
    @DisplayName("listYappcAgents filters to only YAPPC-owned agents")
    void listYappcAgentsFiltersCorrectly() { 
        List<CatalogAgentEntry> result = runPromise(() -> integration.listYappcAgents()); 

        assertThat(result).hasSize(5); 
        assertThat(result).allSatisfy(e -> 
                assertThat(e.getId()).startsWith("agent.yappc."));
    }

    @Test
    @DisplayName("getAgentsByPhase groups YAPPC agents by SDLC phase")
    void getAgentsByPhaseGroupsCorrectly() { 
        Map<String, List<CatalogAgentEntry>> byPhase =
                runPromise(() -> integration.getAgentsByPhase()); 

        assertThat(byPhase).containsOnlyKeys("architecture", "implementation", "testing"); 
        assertThat(byPhase.get("architecture")).hasSize(2);
        assertThat(byPhase.get("implementation")).hasSize(1);
        assertThat(byPhase.get("testing")).hasSize(2);
    }

    @Test
    @DisplayName("getAgentsForPhase returns agents for a specific SDLC phase")
    void getAgentsForPhaseReturnsCorrectSubset() { 
        List<CatalogAgentEntry> testing =
                runPromise(() -> integration.getAgentsForPhase("testing"));

        assertThat(testing).hasSize(2); 
        assertThat(testing).extracting(CatalogAgentEntry::getId) 
                .containsExactlyInAnyOrder( 
                        "agent.yappc.testing.test-generator",
                        "agent.yappc.testing.performance-test");
    }

    @Test
    @DisplayName("resolveByStepName maps step names to catalog IDs")
    void resolveByStepNameMapsCorrectly() { 
        Optional<CatalogAgentEntry> result =
                runPromise(() -> integration.resolveByStepName("architecture.domain-modeler"));

        assertThat(result).isPresent(); 
        assertThat(result.get().getId()) 
                .isEqualTo("agent.yappc.architecture.domain-modeler");
    }

    @Test
    @DisplayName("resolveByStepName returns empty for unknown steps")
    void resolveByStepNameReturnsEmptyForUnknown() { 
        Optional<CatalogAgentEntry> result =
                runPromise(() -> integration.resolveByStepName("nonexistent"));

        assertThat(result).isEmpty(); 
    }

    @Test
    @DisplayName("findByCapability returns only YAPPC agents for a given capability")
    void findByCapabilityFiltersToYappcOnly() { 
        List<CatalogAgentEntry> result =
                runPromise(() -> integration.findByCapability("architecture"));

        assertThat(result).hasSize(2); 
        assertThat(result).allSatisfy(e -> 
                assertThat(e.getId()).startsWith("agent.yappc."));
    }

    @Test
    @DisplayName("findByCapability excludes non-YAPPC agents")
    void findByCapabilityExcludesNonYappc() { 
        List<CatalogAgentEntry> result =
                runPromise(() -> integration.findByCapability("validation"));

        assertThat(result).isEmpty(); 
    }

    @Test
    @DisplayName("getAllPhases returns distinct SDLC phases from YAPPC agents")
    void getAllPhasesReturnsDistinctPhases() { 
        Set<String> phases = runPromise(() -> integration.getAllPhases()); 

        assertThat(phases).containsExactlyInAnyOrder( 
                "architecture", "implementation", "testing");
    }

    @Test
    @DisplayName("yappcAgentCount returns correct count")
    void yappcAgentCountIsCorrect() { 
        Integer count = runPromise(() -> integration.yappcAgentCount()); 

        assertThat(count).isEqualTo(5); 
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private static CatalogAgentEntry buildEntry( 
            String id, String catalogId, Set<String> capabilities) {
        return CatalogAgentEntry.builder() 
                .id(id) 
                .catalogId(catalogId) 
                .name(id.replace(".", " ")) 
                .capabilities(capabilities) 
                .build(); 
    }
}

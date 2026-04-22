/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Acceptance tests for YAPPC → AEP migration (Phase 5). // GH-90000
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
@DisplayName("YAPPC → AEP Migration Acceptance Tests [GH-90000]")
class YappcAepIntegrationTest extends EventloopTestBase {

    private YappcAepIntegration integration;
    private AgentRegistryContracts registryService;

    @BeforeEach
    void setUp() { // GH-90000
        registryService = mock(AgentRegistryContracts.class); // GH-90000

        // Stub agent entries: mix of YAPPC and non-YAPPC agents
        List<CatalogAgentEntry> allAgents = List.of( // GH-90000
                buildEntry("agent.yappc.architecture.domain-modeler", "yappc", // GH-90000
                        Set.of("architecture", "domain-modeling")), // GH-90000
                buildEntry("agent.yappc.architecture.security-architect", "yappc", // GH-90000
                        Set.of("architecture", "security")), // GH-90000
                buildEntry("agent.yappc.implementation.code-reviewer", "yappc", // GH-90000
                        Set.of("code-review", "implementation")), // GH-90000
                buildEntry("agent.yappc.testing.test-generator", "yappc", // GH-90000
                        Set.of("testing", "code-generation")), // GH-90000
                buildEntry("agent.yappc.testing.performance-test", "yappc", // GH-90000
                        Set.of("testing", "performance")), // GH-90000
                // Non-YAPPC agents — should be filtered out
                buildEntry("agent.data-cloud.schema-validator", "data-cloud", // GH-90000
                        Set.of("validation", "schema")), // GH-90000
                buildEntry("agent.platform.health-checker", "platform", // GH-90000
                        Set.of("monitoring", "health")) // GH-90000
        );

        when(registryService.listAgents()).thenReturn(Promise.of(allAgents)); // GH-90000
        when(registryService.getAgent("agent.yappc.architecture.domain-modeler [GH-90000]"))
                .thenReturn(Promise.of(Optional.of(allAgents.get(0)))); // GH-90000
        when(registryService.getAgent("agent.yappc.implementation.code-reviewer [GH-90000]"))
                .thenReturn(Promise.of(Optional.of(allAgents.get(2)))); // GH-90000
        when(registryService.getAgent("agent.yappc.nonexistent [GH-90000]"))
                .thenReturn(Promise.of(Optional.empty())); // GH-90000
        when(registryService.findByCapability("architecture [GH-90000]"))
                .thenReturn(Promise.of(List.of(allAgents.get(0), allAgents.get(1)))); // GH-90000
        when(registryService.findByCapability("testing [GH-90000]"))
                .thenReturn(Promise.of(List.of(allAgents.get(3), allAgents.get(4)))); // GH-90000
        when(registryService.findByCapability("validation [GH-90000]"))
                .thenReturn(Promise.of(List.of(allAgents.get(5)))); // GH-90000

        integration = new YappcAepIntegration(registryService); // GH-90000
    }

    @Test
    @DisplayName("listYappcAgents filters to only YAPPC-owned agents [GH-90000]")
    void listYappcAgentsFiltersCorrectly() { // GH-90000
        List<CatalogAgentEntry> result = runPromise(() -> integration.listYappcAgents()); // GH-90000

        assertThat(result).hasSize(5); // GH-90000
        assertThat(result).allSatisfy(e -> // GH-90000
                assertThat(e.getId()).startsWith("agent.yappc. [GH-90000]"));
    }

    @Test
    @DisplayName("getAgentsByPhase groups YAPPC agents by SDLC phase [GH-90000]")
    void getAgentsByPhaseGroupsCorrectly() { // GH-90000
        Map<String, List<CatalogAgentEntry>> byPhase =
                runPromise(() -> integration.getAgentsByPhase()); // GH-90000

        assertThat(byPhase).containsOnlyKeys("architecture", "implementation", "testing"); // GH-90000
        assertThat(byPhase.get("architecture [GH-90000]")).hasSize(2);
        assertThat(byPhase.get("implementation [GH-90000]")).hasSize(1);
        assertThat(byPhase.get("testing [GH-90000]")).hasSize(2);
    }

    @Test
    @DisplayName("getAgentsForPhase returns agents for a specific SDLC phase [GH-90000]")
    void getAgentsForPhaseReturnsCorrectSubset() { // GH-90000
        List<CatalogAgentEntry> testing =
                runPromise(() -> integration.getAgentsForPhase("testing [GH-90000]"));

        assertThat(testing).hasSize(2); // GH-90000
        assertThat(testing).extracting(CatalogAgentEntry::getId) // GH-90000
                .containsExactlyInAnyOrder( // GH-90000
                        "agent.yappc.testing.test-generator",
                        "agent.yappc.testing.performance-test");
    }

    @Test
    @DisplayName("resolveByStepName maps step names to catalog IDs [GH-90000]")
    void resolveByStepNameMapsCorrectly() { // GH-90000
        Optional<CatalogAgentEntry> result =
                runPromise(() -> integration.resolveByStepName("architecture.domain-modeler [GH-90000]"));

        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getId()) // GH-90000
                .isEqualTo("agent.yappc.architecture.domain-modeler [GH-90000]");
    }

    @Test
    @DisplayName("resolveByStepName returns empty for unknown steps [GH-90000]")
    void resolveByStepNameReturnsEmptyForUnknown() { // GH-90000
        Optional<CatalogAgentEntry> result =
                runPromise(() -> integration.resolveByStepName("nonexistent [GH-90000]"));

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("findByCapability returns only YAPPC agents for a given capability [GH-90000]")
    void findByCapabilityFiltersToYappcOnly() { // GH-90000
        List<CatalogAgentEntry> result =
                runPromise(() -> integration.findByCapability("architecture [GH-90000]"));

        assertThat(result).hasSize(2); // GH-90000
        assertThat(result).allSatisfy(e -> // GH-90000
                assertThat(e.getId()).startsWith("agent.yappc. [GH-90000]"));
    }

    @Test
    @DisplayName("findByCapability excludes non-YAPPC agents [GH-90000]")
    void findByCapabilityExcludesNonYappc() { // GH-90000
        List<CatalogAgentEntry> result =
                runPromise(() -> integration.findByCapability("validation [GH-90000]"));

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("getAllPhases returns distinct SDLC phases from YAPPC agents [GH-90000]")
    void getAllPhasesReturnsDistinctPhases() { // GH-90000
        Set<String> phases = runPromise(() -> integration.getAllPhases()); // GH-90000

        assertThat(phases).containsExactlyInAnyOrder( // GH-90000
                "architecture", "implementation", "testing");
    }

    @Test
    @DisplayName("yappcAgentCount returns correct count [GH-90000]")
    void yappcAgentCountIsCorrect() { // GH-90000
        Integer count = runPromise(() -> integration.yappcAgentCount()); // GH-90000

        assertThat(count).isEqualTo(5); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private static CatalogAgentEntry buildEntry( // GH-90000
            String id, String catalogId, Set<String> capabilities) {
        return CatalogAgentEntry.builder() // GH-90000
                .id(id) // GH-90000
                .catalogId(catalogId) // GH-90000
                .name(id.replace(".", " ")) // GH-90000
                .capabilities(capabilities) // GH-90000
                .build(); // GH-90000
    }
}

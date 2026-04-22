/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.engine.registry;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.aep.integration.registry.CatalogRegistryContractAdapter;
import com.ghatana.aep.registry.AgentRegistryContracts;
import com.ghatana.agent.catalog.AgentCatalog;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.catalog.CatalogRegistry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Parity test: proves that the orchestrator-level {@link AepCentralRegistryService}
 * (backed by {@link CatalogRegistryContractAdapter}) returns the same discovery results // GH-90000
 * as the {@link AgentRegistryContracts} backend it delegates to.
 *
 * <p>This verifies the transparent delegation contract: the service should not filter,
 * transform, or drop entries coming from the backend catalog. Local in-process registrations
 * are separate and take priority, but catalog-only entries must be faithfully propagated.
 *
 * <p>Guarantees locked here:
 * <ol>
 *   <li>listAll() returns at least all backend catalog entries</li> // GH-90000
 *   <li>resolveAgent(id) finds backend agents that are not locally registered</li> // GH-90000
 *   <li>findByCapability (via backend) is consistent with direct adapter result</li> // GH-90000
 * </ol>
 */
@DisplayName("AepCentralRegistryService — Catalog Backend Parity [GH-90000]")
class AepCentralRegistryServiceParityTest extends EventloopTestBase {

    private CatalogRegistry sharedCatalogRegistry;
    private CatalogRegistryContractAdapter backendAdapter;
    private AepCentralRegistryService orchestratorService;

    @BeforeEach
    void setUp() { // GH-90000
        sharedCatalogRegistry = CatalogRegistry.empty(); // GH-90000
        sharedCatalogRegistry.register(new StubCatalog( // GH-90000
                "catalog-aep",
                List.of( // GH-90000
                        entry("aep-planner", "AEP Planner", "catalog-aep", Set.of("planning", "reasoning")), // GH-90000
                        entry("aep-executor", "AEP Executor", "catalog-aep", Set.of("execution [GH-90000]")))));
        sharedCatalogRegistry.register(new StubCatalog( // GH-90000
                "catalog-yappc",
                List.of(entry( // GH-90000
                        "yappc-code-gen", "YAPPC Code Gen", "catalog-yappc", Set.of("code-generation", "reasoning"))))); // GH-90000

        backendAdapter = new CatalogRegistryContractAdapter(sharedCatalogRegistry); // GH-90000
        orchestratorService = new AepCentralRegistryService(backendAdapter); // GH-90000
    }

    @Test
    @DisplayName("listAll on orchestrator returns same count as backend listAgents [GH-90000]")
    void listAllParityWithBackend() { // GH-90000
        List<CatalogAgentEntry> backendEntries = runPromise(backendAdapter::listAgents); // GH-90000
        List<AgentInfo> orchestratorAll = runPromise(orchestratorService::listAll); // GH-90000

        // Orchestrator must expose at least all backend entries (plus any local) // GH-90000
        assertThat(orchestratorAll).hasSizeGreaterThanOrEqualTo(backendEntries.size()); // GH-90000

        // All backend agent IDs must appear in orchestrator results
        Set<String> orchestratorIds =
                orchestratorAll.stream().map(AgentInfo::id).collect(java.util.stream.Collectors.toSet()); // GH-90000
        for (CatalogAgentEntry backendEntry : backendEntries) { // GH-90000
            assertThat(orchestratorIds) // GH-90000
                    .as("Orchestrator must expose backend agent '%s'", backendEntry.getId()) // GH-90000
                    .contains(backendEntry.getId()); // GH-90000
        }
    }

    @Test
    @DisplayName("resolveAgent on orchestrator finds catalog-only agents (no local registration) [GH-90000]")
    void resolveAgentFindsBackendAgentsWithoutLocalRegistration() { // GH-90000
        Optional<AgentInfo> planner = runPromise(() -> orchestratorService.resolveAgent("aep-planner [GH-90000]"));
        Optional<AgentInfo> executor = runPromise(() -> orchestratorService.resolveAgent("aep-executor [GH-90000]"));
        Optional<AgentInfo> codeGen = runPromise(() -> orchestratorService.resolveAgent("yappc-code-gen [GH-90000]"));
        Optional<AgentInfo> missing = runPromise(() -> orchestratorService.resolveAgent("does-not-exist [GH-90000]"));

        assertThat(planner).isPresent().satisfies(a -> assertThat(a.get().id()).isEqualTo("aep-planner [GH-90000]"));
        assertThat(executor).isPresent().satisfies(a -> assertThat(a.get().id()).isEqualTo("aep-executor [GH-90000]"));
        assertThat(codeGen).isPresent().satisfies(a -> assertThat(a.get().id()).isEqualTo("yappc-code-gen [GH-90000]"));
        assertThat(missing).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("local registration takes priority over backend catalog entry [GH-90000]")
    void localRegistrationTakesPriorityOverBackend() { // GH-90000
        AgentInfo localOverride = new AgentInfo("aep-planner", "Local Override Planner", "HYBRID"); // GH-90000
        localOverride.product = "yappc";
        runPromise(() -> orchestratorService.registerAgent(localOverride)); // GH-90000

        Optional<AgentInfo> resolved = runPromise(() -> orchestratorService.resolveAgent("aep-planner [GH-90000]"));

        // Local registration wins
        assertThat(resolved).isPresent(); // GH-90000
        assertThat(resolved.get().name()).isEqualTo("Local Override Planner [GH-90000]");
    }

    @Test
    @DisplayName("backend capability filtering matches direct adapter result [GH-90000]")
    void capabilityFilteringConsistentWithBackend() { // GH-90000
        List<CatalogAgentEntry> backendReasoning = runPromise(() -> backendAdapter.findByCapability("reasoning [GH-90000]"));

        // Agents with reasoning: aep-planner + yappc-code-gen
        assertThat(backendReasoning).hasSize(2); // GH-90000
        assertThat(backendReasoning) // GH-90000
                .extracting(CatalogAgentEntry::getId) // GH-90000
                .containsExactlyInAnyOrder("aep-planner", "yappc-code-gen"); // GH-90000

        // Orchestrator listAll should include all agents with reasoning
        List<AgentInfo> all = runPromise(orchestratorService::listAll); // GH-90000
        long plannerOrCodeGen = all.stream() // GH-90000
                .filter(a -> a.id().equals("aep-planner [GH-90000]") || a.id().equals("yappc-code-gen [GH-90000]"))
                .count(); // GH-90000
        assertThat(plannerOrCodeGen).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("catalog entries are exposed as AgentInfo with correct metadata [GH-90000]")
    void catalogEntriesExposedWithCorrectMetadata() { // GH-90000
        Optional<AgentInfo> planner = runPromise(() -> orchestratorService.resolveAgent("aep-planner [GH-90000]"));

        assertThat(planner).isPresent(); // GH-90000
        AgentInfo info = planner.get(); // GH-90000
        assertThat(info.id()).isEqualTo("aep-planner [GH-90000]");
        assertThat(info.name()).isEqualTo("AEP Planner [GH-90000]");
        assertThat(info.product()).isEqualTo("catalog-aep [GH-90000]");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private CatalogAgentEntry entry(String id, String name, String catalogId, Set<String> capabilities) { // GH-90000
        return CatalogAgentEntry.builder() // GH-90000
                .id(id) // GH-90000
                .name(name) // GH-90000
                .catalogId(catalogId) // GH-90000
                .capabilities(capabilities) // GH-90000
                .build(); // GH-90000
    }

    /** Minimal AgentCatalog for testing — implements all interface methods. */
    private static final class StubCatalog implements AgentCatalog {
        private final String id;
        private final List<CatalogAgentEntry> defs;

        StubCatalog(String id, List<CatalogAgentEntry> defs) { // GH-90000
            this.id = id;
            this.defs = defs;
        }

        @Override
        public String getCatalogId() { // GH-90000
            return id;
        }

        @Override
        public String getDisplayName() { // GH-90000
            return id;
        }

        @Override
        public int priority() { // GH-90000
            return 0;
        }

        @Override
        public List<CatalogAgentEntry> getDefinitions() { // GH-90000
            return defs;
        }

        @Override
        public Optional<CatalogAgentEntry> findById(String agentId) { // GH-90000
            return defs.stream().filter(e -> e.getId().equals(agentId)).findFirst(); // GH-90000
        }

        @Override
        public List<CatalogAgentEntry> findByCapability(String capability) { // GH-90000
            return defs.stream() // GH-90000
                    .filter(e -> e.getCapabilities().contains(capability)) // GH-90000
                    .toList(); // GH-90000
        }

        @Override
        public List<CatalogAgentEntry> findByLevel(String level) { // GH-90000
            return List.of(); // GH-90000
        }

        @Override
        public List<CatalogAgentEntry> findByDomain(String domain) { // GH-90000
            return List.of(); // GH-90000
        }

        @Override
        public Set<String> getAllCapabilities() { // GH-90000
            return defs.stream() // GH-90000
                    .flatMap(e -> e.getCapabilities().stream()) // GH-90000
                    .collect(java.util.stream.Collectors.toSet()); // GH-90000
        }
    }
}

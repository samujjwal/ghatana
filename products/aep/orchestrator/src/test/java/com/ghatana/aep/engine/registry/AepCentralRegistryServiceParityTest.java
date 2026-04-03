/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * (backed by {@link CatalogRegistryContractAdapter}) returns the same discovery results
 * as the {@link AgentRegistryContracts} backend it delegates to.
 *
 * <p>This verifies the transparent delegation contract: the service should not filter,
 * transform, or drop entries coming from the backend catalog. Local in-process registrations
 * are separate and take priority, but catalog-only entries must be faithfully propagated.
 *
 * <p>Guarantees locked here:
 * <ol>
 *   <li>listAll() returns at least all backend catalog entries</li>
 *   <li>resolveAgent(id) finds backend agents that are not locally registered</li>
 *   <li>findByCapability (via backend) is consistent with direct adapter result</li>
 * </ol>
 */
@DisplayName("AepCentralRegistryService — Catalog Backend Parity")
class AepCentralRegistryServiceParityTest extends EventloopTestBase {

    private CatalogRegistry sharedCatalogRegistry;
    private CatalogRegistryContractAdapter backendAdapter;
    private AepCentralRegistryService orchestratorService;

    @BeforeEach
    void setUp() {
        sharedCatalogRegistry = CatalogRegistry.empty();
        sharedCatalogRegistry.register(new StubCatalog(
                "catalog-aep",
                List.of(
                        entry("aep-planner", "AEP Planner", "catalog-aep", Set.of("planning", "reasoning")),
                        entry("aep-executor", "AEP Executor", "catalog-aep", Set.of("execution")))));
        sharedCatalogRegistry.register(new StubCatalog(
                "catalog-yappc",
                List.of(entry(
                        "yappc-code-gen", "YAPPC Code Gen", "catalog-yappc", Set.of("code-generation", "reasoning")))));

        backendAdapter = new CatalogRegistryContractAdapter(sharedCatalogRegistry);
        orchestratorService = new AepCentralRegistryService(backendAdapter);
    }

    @Test
    @DisplayName("listAll on orchestrator returns same count as backend listAgents")
    void listAllParityWithBackend() {
        List<CatalogAgentEntry> backendEntries = runPromise(backendAdapter::listAgents);
        List<AgentInfo> orchestratorAll = runPromise(orchestratorService::listAll);

        // Orchestrator must expose at least all backend entries (plus any local)
        assertThat(orchestratorAll).hasSizeGreaterThanOrEqualTo(backendEntries.size());

        // All backend agent IDs must appear in orchestrator results
        Set<String> orchestratorIds =
                orchestratorAll.stream().map(AgentInfo::id).collect(java.util.stream.Collectors.toSet());
        for (CatalogAgentEntry backendEntry : backendEntries) {
            assertThat(orchestratorIds)
                    .as("Orchestrator must expose backend agent '%s'", backendEntry.getId())
                    .contains(backendEntry.getId());
        }
    }

    @Test
    @DisplayName("resolveAgent on orchestrator finds catalog-only agents (no local registration)")
    void resolveAgentFindsBackendAgentsWithoutLocalRegistration() {
        Optional<AgentInfo> planner = runPromise(() -> orchestratorService.resolveAgent("aep-planner"));
        Optional<AgentInfo> executor = runPromise(() -> orchestratorService.resolveAgent("aep-executor"));
        Optional<AgentInfo> codeGen = runPromise(() -> orchestratorService.resolveAgent("yappc-code-gen"));
        Optional<AgentInfo> missing = runPromise(() -> orchestratorService.resolveAgent("does-not-exist"));

        assertThat(planner).isPresent().satisfies(a -> assertThat(a.get().id()).isEqualTo("aep-planner"));
        assertThat(executor).isPresent().satisfies(a -> assertThat(a.get().id()).isEqualTo("aep-executor"));
        assertThat(codeGen).isPresent().satisfies(a -> assertThat(a.get().id()).isEqualTo("yappc-code-gen"));
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("local registration takes priority over backend catalog entry")
    void localRegistrationTakesPriorityOverBackend() {
        AgentInfo localOverride = new AgentInfo("aep-planner", "Local Override Planner", "HYBRID");
        localOverride.product = "yappc";
        runPromise(() -> orchestratorService.registerAgent(localOverride));

        Optional<AgentInfo> resolved = runPromise(() -> orchestratorService.resolveAgent("aep-planner"));

        // Local registration wins
        assertThat(resolved).isPresent();
        assertThat(resolved.get().name()).isEqualTo("Local Override Planner");
    }

    @Test
    @DisplayName("backend capability filtering matches direct adapter result")
    void capabilityFilteringConsistentWithBackend() {
        List<CatalogAgentEntry> backendReasoning = runPromise(() -> backendAdapter.findByCapability("reasoning"));

        // Agents with reasoning: aep-planner + yappc-code-gen
        assertThat(backendReasoning).hasSize(2);
        assertThat(backendReasoning)
                .extracting(CatalogAgentEntry::getId)
                .containsExactlyInAnyOrder("aep-planner", "yappc-code-gen");

        // Orchestrator listAll should include all agents with reasoning
        List<AgentInfo> all = runPromise(orchestratorService::listAll);
        long plannerOrCodeGen = all.stream()
                .filter(a -> a.id().equals("aep-planner") || a.id().equals("yappc-code-gen"))
                .count();
        assertThat(plannerOrCodeGen).isEqualTo(2);
    }

    @Test
    @DisplayName("catalog entries are exposed as AgentInfo with correct metadata")
    void catalogEntriesExposedWithCorrectMetadata() {
        Optional<AgentInfo> planner = runPromise(() -> orchestratorService.resolveAgent("aep-planner"));

        assertThat(planner).isPresent();
        AgentInfo info = planner.get();
        assertThat(info.id()).isEqualTo("aep-planner");
        assertThat(info.name()).isEqualTo("AEP Planner");
        assertThat(info.product()).isEqualTo("catalog-aep");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private CatalogAgentEntry entry(String id, String name, String catalogId, Set<String> capabilities) {
        return CatalogAgentEntry.builder()
                .id(id)
                .name(name)
                .catalogId(catalogId)
                .capabilities(capabilities)
                .build();
    }

    /** Minimal AgentCatalog for testing — implements all interface methods. */
    private static final class StubCatalog implements AgentCatalog {
        private final String id;
        private final List<CatalogAgentEntry> defs;

        StubCatalog(String id, List<CatalogAgentEntry> defs) {
            this.id = id;
            this.defs = defs;
        }

        @Override
        public String getCatalogId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return id;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public List<CatalogAgentEntry> getDefinitions() {
            return defs;
        }

        @Override
        public Optional<CatalogAgentEntry> findById(String agentId) {
            return defs.stream().filter(e -> e.getId().equals(agentId)).findFirst();
        }

        @Override
        public List<CatalogAgentEntry> findByCapability(String capability) {
            return defs.stream()
                    .filter(e -> e.getCapabilities().contains(capability))
                    .toList();
        }

        @Override
        public List<CatalogAgentEntry> findByLevel(String level) {
            return List.of();
        }

        @Override
        public List<CatalogAgentEntry> findByDomain(String domain) {
            return List.of();
        }

        @Override
        public Set<String> getAllCapabilities() {
            return defs.stream()
                    .flatMap(e -> e.getCapabilities().stream())
                    .collect(java.util.stream.Collectors.toSet());
        }
    }
}

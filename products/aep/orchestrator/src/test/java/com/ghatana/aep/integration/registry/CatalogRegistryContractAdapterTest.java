/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.integration.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.agent.catalog.AgentCatalog;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.catalog.CatalogRegistry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests CatalogRegistryContractAdapter — verifies that AgentRegistryContracts
 * methods delegate correctly to the underlying CatalogRegistry.
 */
@DisplayName("CatalogRegistryContractAdapter")
class CatalogRegistryContractAdapterTest extends EventloopTestBase {

    private CatalogRegistry catalogRegistry;
    private CatalogRegistryContractAdapter adapter;

    @BeforeEach
    void setUp() {
        catalogRegistry = CatalogRegistry.empty();
        adapter = new CatalogRegistryContractAdapter(catalogRegistry);
    }

    @Test
    @DisplayName("listAgents returns empty list when catalog is empty")
    void listAgentsEmptyCatalog() {
        List<CatalogAgentEntry> result = runPromise(adapter::listAgents);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listAgents returns all definitions from registered catalog")
    void listAgentsWithCatalog() {
        catalogRegistry.register(buildCatalog("cat1", "agent-alpha", "agent-beta"));

        List<CatalogAgentEntry> result = runPromise(adapter::listAgents);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CatalogAgentEntry::getId).containsExactlyInAnyOrder("agent-alpha", "agent-beta");
    }

    @Test
    @DisplayName("getAgent returns present optional when agent exists")
    void getAgentFound() {
        catalogRegistry.register(buildCatalog("cat1", "agent-alpha"));

        Optional<CatalogAgentEntry> result = runPromise(() -> adapter.getAgent("agent-alpha"));

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("agent-alpha");
    }

    @Test
    @DisplayName("getAgent returns empty optional when agent is not found")
    void getAgentNotFound() {
        Optional<CatalogAgentEntry> result = runPromise(() -> adapter.getAgent("no-such-agent"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByCapability returns matching agents")
    void findByCapabilityMatches() {
        catalogRegistry.register(buildCatalogWithCapability("cat1", "agent-cap", "text-generation"));
        catalogRegistry.register(buildCatalog("cat1b", "agent-other")); // no capability

        List<CatalogAgentEntry> result = runPromise(() -> adapter.findByCapability("text-generation"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("agent-cap");
    }

    @Test
    @DisplayName("findByCapability returns empty list when no agents match")
    void findByCapabilityNoMatch() {
        catalogRegistry.register(buildCatalog("cat1", "agent-alpha"));

        List<CatalogAgentEntry> result = runPromise(() -> adapter.findByCapability("nonexistent-capability"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("rejects null catalogRegistry in constructor")
    void rejectsNullRegistry() {
        assertThatThrownBy(() -> new CatalogRegistryContractAdapter(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("catalogRegistry");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private AgentCatalog buildCatalog(String catalogId, String... agentIds) {
        List<CatalogAgentEntry> entries = java.util.Arrays.stream(agentIds)
                .map(id -> CatalogAgentEntry.builder()
                        .id(id)
                        .name(id)
                        .catalogId(catalogId)
                        .capabilities(java.util.Set.of())
                        .build())
                .toList();
        return new StubCatalog(catalogId, entries);
    }

    private AgentCatalog buildCatalogWithCapability(String catalogId, String agentId, String capability) {
        CatalogAgentEntry entry = CatalogAgentEntry.builder()
                .id(agentId)
                .name(agentId)
                .catalogId(catalogId)
                .capabilities(java.util.Set.of(capability))
                .build();
        return new StubCatalog(catalogId, List.of(entry));
    }

    /** Minimal AgentCatalog implementation for test purposes only. */
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
        public java.util.Set<String> getAllCapabilities() {
            return defs.stream()
                    .flatMap(e -> e.getCapabilities().stream())
                    .collect(java.util.stream.Collectors.toSet());
        }
    }
}

/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        catalogRegistry = CatalogRegistry.empty(); // GH-90000
        adapter = new CatalogRegistryContractAdapter(catalogRegistry); // GH-90000
    }

    @Test
    @DisplayName("listAgents returns empty list when catalog is empty")
    void listAgentsEmptyCatalog() { // GH-90000
        List<CatalogAgentEntry> result = runPromise(adapter::listAgents); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("listAgents returns all definitions from registered catalog")
    void listAgentsWithCatalog() { // GH-90000
        catalogRegistry.register(buildCatalog("cat1", "agent-alpha", "agent-beta")); // GH-90000

        List<CatalogAgentEntry> result = runPromise(adapter::listAgents); // GH-90000

        assertThat(result).hasSize(2); // GH-90000
        assertThat(result).extracting(CatalogAgentEntry::getId).containsExactlyInAnyOrder("agent-alpha", "agent-beta"); // GH-90000
    }

    @Test
    @DisplayName("getAgent returns present optional when agent exists")
    void getAgentFound() { // GH-90000
        catalogRegistry.register(buildCatalog("cat1", "agent-alpha")); // GH-90000

        Optional<CatalogAgentEntry> result = runPromise(() -> adapter.getAgent("agent-alpha"));

        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getId()).isEqualTo("agent-alpha");
    }

    @Test
    @DisplayName("getAgent returns empty optional when agent is not found")
    void getAgentNotFound() { // GH-90000
        Optional<CatalogAgentEntry> result = runPromise(() -> adapter.getAgent("no-such-agent"));

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("findByCapability returns matching agents")
    void findByCapabilityMatches() { // GH-90000
        catalogRegistry.register(buildCatalogWithCapability("cat1", "agent-cap", "text-generation")); // GH-90000
        catalogRegistry.register(buildCatalog("cat1b", "agent-other")); // no capability // GH-90000

        List<CatalogAgentEntry> result = runPromise(() -> adapter.findByCapability("text-generation"));

        assertThat(result).hasSize(1); // GH-90000
        assertThat(result.get(0).getId()).isEqualTo("agent-cap");
    }

    @Test
    @DisplayName("findByCapability returns empty list when no agents match")
    void findByCapabilityNoMatch() { // GH-90000
        catalogRegistry.register(buildCatalog("cat1", "agent-alpha")); // GH-90000

        List<CatalogAgentEntry> result = runPromise(() -> adapter.findByCapability("nonexistent-capability"));

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("rejects null catalogRegistry in constructor")
    void rejectsNullRegistry() { // GH-90000
        assertThatThrownBy(() -> new CatalogRegistryContractAdapter(null)) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("catalogRegistry");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private AgentCatalog buildCatalog(String catalogId, String... agentIds) { // GH-90000
        List<CatalogAgentEntry> entries = java.util.Arrays.stream(agentIds) // GH-90000
                .map(id -> CatalogAgentEntry.builder() // GH-90000
                        .id(id) // GH-90000
                        .name(id) // GH-90000
                        .catalogId(catalogId) // GH-90000
                        .capabilities(java.util.Set.of()) // GH-90000
                        .build()) // GH-90000
                .toList(); // GH-90000
        return new StubCatalog(catalogId, entries); // GH-90000
    }

    private AgentCatalog buildCatalogWithCapability(String catalogId, String agentId, String capability) { // GH-90000
        CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                .id(agentId) // GH-90000
                .name(agentId) // GH-90000
                .catalogId(catalogId) // GH-90000
                .capabilities(java.util.Set.of(capability)) // GH-90000
                .build(); // GH-90000
        return new StubCatalog(catalogId, List.of(entry)); // GH-90000
    }

    /** Minimal AgentCatalog implementation for test purposes only. */
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
        public java.util.Set<String> getAllCapabilities() { // GH-90000
            return defs.stream() // GH-90000
                    .flatMap(e -> e.getCapabilities().stream()) // GH-90000
                    .collect(java.util.stream.Collectors.toSet()); // GH-90000
        }
    }
}

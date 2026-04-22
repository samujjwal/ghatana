/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.agent.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CatalogRegistry}.
 */
@DisplayName("CatalogRegistry [GH-90000]")
class CatalogRegistryTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Factory methods
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("empty() [GH-90000]")
    class EmptyTests {

        @Test
        @DisplayName("should start with zero catalogs [GH-90000]")
        void shouldBeEmpty() { // GH-90000
            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            assertThat(registry.getCatalogIds()).isEmpty(); // GH-90000
            assertThat(registry.size()).isZero(); // GH-90000
            assertThat(registry.allDefinitions()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("findById on empty registry returns empty [GH-90000]")
        void findByIdOnEmptyRegistryReturnsEmpty() { // GH-90000
            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            assertThat(registry.findById("any-id [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("findByCapability on empty registry returns empty list [GH-90000]")
        void findByCapabilityOnEmptyRegistryReturnsEmpty() { // GH-90000
            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            assertThat(registry.findByCapability("code-generation [GH-90000]")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Registration
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("register() [GH-90000]")
    class RegisterTests {

        @Test
        @DisplayName("should register a catalog and index its definitions [GH-90000]")
        void shouldRegisterCatalog() { // GH-90000
            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            registry.register(buildCatalog("platform", 100, // GH-90000
                    entry("sentinel", "monitoring"), // GH-90000
                    entry("data-ingestor", "data-processing"))); // GH-90000

            assertThat(registry.getCatalogIds()).containsExactly("platform [GH-90000]");
            assertThat(registry.size()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("should allow multiple catalogs [GH-90000]")
        void shouldAllowMultipleCatalogs() { // GH-90000
            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            registry.register(buildCatalog("platform", 100, entry("agent-a", "domain-a"))); // GH-90000
            registry.register(buildCatalog("yappc", 200, entry("agent-b", "domain-b"))); // GH-90000

            assertThat(registry.getCatalogIds()).containsExactlyInAnyOrder("platform", "yappc"); // GH-90000
            assertThat(registry.size()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("null catalog should throw NullPointerException [GH-90000]")
        void shouldThrowOnNullCatalog() { // GH-90000
            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            assertThatThrownBy(() -> registry.register(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("replacing catalog with same id rebuilds index [GH-90000]")
        void replacingCatalogRetainsOnlyNewDefinitions() { // GH-90000
            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            registry.register(buildCatalog("platform", 100, entry("v1-agent", "domain"))); // GH-90000
            registry.register(buildCatalog("platform", 100, entry("v2-agent", "domain"))); // GH-90000

            // Index rebuilt from re-registered catalog; v1-agent gone, v2-agent present
            assertThat(registry.findById("v2-agent [GH-90000]")).isPresent();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Priority resolution
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Priority conflict resolution [GH-90000]")
    class PriorityTests {

        @Test
        @DisplayName("lower numeric priority wins when two catalogs define same agent id [GH-90000]")
        void lowerPriorityWins() { // GH-90000
            CatalogAgentEntry platformVersion = CatalogAgentEntry.builder() // GH-90000
                    .id("shared-agent [GH-90000]")
                    .name("Platform Version [GH-90000]")
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000

            CatalogAgentEntry yappcVersion = CatalogAgentEntry.builder() // GH-90000
                    .id("shared-agent [GH-90000]")
                    .name("YAPPC Override [GH-90000]")
                    .catalogId("yappc [GH-90000]")
                    .build(); // GH-90000

            AgentCatalog platformCatalog = stubCatalog("platform", 100, List.of(platformVersion)); // GH-90000
            AgentCatalog yappcCatalog = stubCatalog("yappc", 200, List.of(yappcVersion)); // GH-90000

            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            registry.register(yappcCatalog); // GH-90000
            registry.register(platformCatalog); // GH-90000

            // platform has priority 100 (lower) — wins // GH-90000
            Optional<CatalogAgentEntry> resolved = registry.findById("shared-agent [GH-90000]");
            assertThat(resolved).isPresent(); // GH-90000
            assertThat(resolved.get().getName()).isEqualTo("Platform Version [GH-90000]");
        }

        @Test
        @DisplayName("higher numeric priority catalog is shadowed by lower numeric priority [GH-90000]")
        void higherNumericPriorityIsShadowed() { // GH-90000
            CatalogAgentEntry base = CatalogAgentEntry.builder() // GH-90000
                    .id("conflict-agent [GH-90000]")
                    .name("Base [GH-90000]")
                    .catalogId("base [GH-90000]")
                    .build(); // GH-90000

            CatalogAgentEntry override = CatalogAgentEntry.builder() // GH-90000
                    .id("conflict-agent [GH-90000]")
                    .name("Override [GH-90000]")
                    .catalogId("product [GH-90000]")
                    .build(); // GH-90000

            // base priority=50 wins over product priority=999
            AgentCatalog baseCatalog = stubCatalog("base", 50, List.of(base)); // GH-90000
            AgentCatalog productCatalog = stubCatalog("product", 999, List.of(override)); // GH-90000

            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            registry.register(productCatalog); // GH-90000
            registry.register(baseCatalog); // GH-90000

            assertThat(registry.findById("conflict-agent [GH-90000]").get().getName()).isEqualTo("Base [GH-90000]");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findById
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findById() [GH-90000]")
    class FindByIdTests {

        @Test
        @DisplayName("should find agent by id [GH-90000]")
        void shouldFindById() { // GH-90000
            CatalogRegistry registry = populatedRegistry(); // GH-90000
            assertThat(registry.findById("sentinel [GH-90000]")).isPresent();
            assertThat(registry.findById("sentinel [GH-90000]").get().getName()).isEqualTo("Sentinel [GH-90000]");
        }

        @Test
        @DisplayName("should return empty for unknown id [GH-90000]")
        void shouldReturnEmptyForUnknownId() { // GH-90000
            CatalogRegistry registry = populatedRegistry(); // GH-90000
            assertThat(registry.findById("does-not-exist [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("unknown id returns empty [GH-90000]")
        void shouldReturnEmptyForMissingId() { // GH-90000
            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            assertThat(registry.findById("unknown-id-xyz [GH-90000]")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findByCapability
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByCapability() [GH-90000]")
    class FindByCapabilityTests {

        @Test
        @DisplayName("should return agents with requested capability [GH-90000]")
        void shouldFindByCapability() { // GH-90000
            CatalogAgentEntry a1 = CatalogAgentEntry.builder() // GH-90000
                    .id("agent-a [GH-90000]")
                    .capabilities(Set.of("monitoring", "alerting")) // GH-90000
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000
            CatalogAgentEntry a2 = CatalogAgentEntry.builder() // GH-90000
                    .id("agent-b [GH-90000]")
                    .capabilities(Set.of("data-transformation [GH-90000]"))
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000

            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            registry.register(stubCatalog("platform", 100, List.of(a1, a2))); // GH-90000

            List<CatalogAgentEntry> monitoringAgents = registry.findByCapability("monitoring [GH-90000]");
            assertThat(monitoringAgents).hasSize(1); // GH-90000
            assertThat(monitoringAgents.get(0).getId()).isEqualTo("agent-a [GH-90000]");
        }

        @Test
        @DisplayName("should return empty list when no agent has requested capability [GH-90000]")
        void shouldReturnEmptyForUnknownCapability() { // GH-90000
            CatalogRegistry registry = populatedRegistry(); // GH-90000
            assertThat(registry.findByCapability("unknown-capability [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("should return multiple agents sharing a capability [GH-90000]")
        void shouldReturnMultipleAgentsWithSharedCapability() { // GH-90000
            CatalogAgentEntry a1 = CatalogAgentEntry.builder() // GH-90000
                    .id("agent-1 [GH-90000]")
                    .capabilities(Set.of("shared-cap [GH-90000]"))
                    .catalogId("c1 [GH-90000]")
                    .build(); // GH-90000
            CatalogAgentEntry a2 = CatalogAgentEntry.builder() // GH-90000
                    .id("agent-2 [GH-90000]")
                    .capabilities(Set.of("shared-cap", "extra-cap")) // GH-90000
                    .catalogId("c1 [GH-90000]")
                    .build(); // GH-90000

            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            registry.register(stubCatalog("c1", 100, List.of(a1, a2))); // GH-90000

            assertThat(registry.findByCapability("shared-cap [GH-90000]")).hasSize(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findByLevel + findByDomain
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByLevel() and findByDomain() [GH-90000]")
    class FindByLevelDomainTests {

        @Test
        @DisplayName("should find agents by level (case-insensitive) [GH-90000]")
        void shouldFindByLevel() { // GH-90000
            CatalogAgentEntry strategic = CatalogAgentEntry.builder() // GH-90000
                    .id("strategic-agent [GH-90000]")
                    .metadata(java.util.Map.of("level", "strategic")) // GH-90000
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000
            CatalogAgentEntry worker = CatalogAgentEntry.builder() // GH-90000
                    .id("worker-agent [GH-90000]")
                    .metadata(java.util.Map.of("level", "worker")) // GH-90000
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000

            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            registry.register(stubCatalog("platform", 100, List.of(strategic, worker))); // GH-90000

            assertThat(registry.findByLevel("strategic [GH-90000]")).hasSize(1);
            assertThat(registry.findByLevel("STRATEGIC [GH-90000]")).hasSize(1); // case-insensitive
            assertThat(registry.findByLevel("worker [GH-90000]")).hasSize(1);
        }

        @Test
        @DisplayName("should find agents by domain (case-insensitive) [GH-90000]")
        void shouldFindByDomain() { // GH-90000
            CatalogAgentEntry dataAgent = CatalogAgentEntry.builder() // GH-90000
                    .id("data-agent [GH-90000]")
                    .metadata(java.util.Map.of("domain", "data-processing")) // GH-90000
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000
            CatalogAgentEntry secAgent = CatalogAgentEntry.builder() // GH-90000
                    .id("security-agent [GH-90000]")
                    .metadata(java.util.Map.of("domain", "security")) // GH-90000
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000

            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            registry.register(stubCatalog("platform", 100, List.of(dataAgent, secAgent))); // GH-90000

            assertThat(registry.findByDomain("data-processing [GH-90000]")).hasSize(1);
            assertThat(registry.findByDomain("DATA-PROCESSING [GH-90000]")).hasSize(1); // case-insensitive
            assertThat(registry.findByDomain("security [GH-90000]")).hasSize(1);
            assertThat(registry.findByDomain("unknown [GH-90000]")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Unregister
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("unregister() [GH-90000]")
    class UnregisterTests {

        @Test
        @DisplayName("should unregister catalog and remove its definitions from index [GH-90000]")
        void shouldUnregisterAndRebuildIndex() { // GH-90000
            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            registry.register(buildCatalog("platform", 100, entry("platform-agent", "domain"))); // GH-90000
            registry.register(buildCatalog("yappc", 200, entry("yappc-agent", "domain"))); // GH-90000

            assertThat(registry.size()).isEqualTo(2); // GH-90000

            registry.unregister("platform [GH-90000]");

            assertThat(registry.getCatalogIds()).containsExactly("yappc [GH-90000]");
            assertThat(registry.findById("platform-agent [GH-90000]")).isEmpty();
            assertThat(registry.findById("yappc-agent [GH-90000]")).isPresent();
        }

        @Test
        @DisplayName("unregistering unknown catalog id is a no-op [GH-90000]")
        void shouldIgnoreUnknownId() { // GH-90000
            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            registry.register(buildCatalog("platform", 100, entry("agent", "domain"))); // GH-90000

            registry.unregister("non-existent [GH-90000]");

            assertThat(registry.size()).isEqualTo(1); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getCatalog
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getCatalog() [GH-90000]")
    class GetCatalogTests {

        @Test
        @DisplayName("should return catalog by id [GH-90000]")
        void shouldReturnCatalogById() { // GH-90000
            AgentCatalog catalog = buildCatalog("platform", 100, // GH-90000
                    entry("agent-a", "domain")); // GH-90000
            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            registry.register(catalog); // GH-90000

            assertThat(registry.getCatalog("platform [GH-90000]")).isPresent();
            assertThat(registry.getCatalog("platform [GH-90000]").get().getCatalogId()).isEqualTo("platform [GH-90000]");
        }

        @Test
        @DisplayName("should return empty for unknown catalog id [GH-90000]")
        void shouldReturnEmptyForUnknownCatalog() { // GH-90000
            CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
            assertThat(registry.getCatalog("does-not-exist [GH-90000]")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private CatalogRegistry populatedRegistry() { // GH-90000
        CatalogRegistry registry = CatalogRegistry.empty(); // GH-90000
        registry.register(buildCatalog("platform", 100, // GH-90000
                CatalogAgentEntry.builder() // GH-90000
                        .id("sentinel [GH-90000]")
                        .name("Sentinel [GH-90000]")
                        .capabilities(Set.of("monitoring", "alerting")) // GH-90000
                        .metadata(java.util.Map.of("domain", "monitoring")) // GH-90000
                        .catalogId("platform [GH-90000]")
                        .build(), // GH-90000
                CatalogAgentEntry.builder() // GH-90000
                        .id("data-transformer [GH-90000]")
                        .name("Data Transformer [GH-90000]")
                        .capabilities(Set.of("data-transformation [GH-90000]"))
                        .metadata(java.util.Map.of("domain", "data-processing")) // GH-90000
                        .catalogId("platform [GH-90000]")
                        .build())); // GH-90000
        return registry;
    }

    private AgentCatalog buildCatalog(String catalogId, int priority, CatalogAgentEntry... entries) { // GH-90000
        return stubCatalog(catalogId, priority, List.of(entries)); // GH-90000
    }

    private AgentCatalog stubCatalog(String catalogId, int prio, List<CatalogAgentEntry> entries) { // GH-90000
        return new AgentCatalog() { // GH-90000
            @Override
            public String getCatalogId() { return catalogId; } // GH-90000

            @Override
            public String getDisplayName() { return catalogId + " catalog"; } // GH-90000

            @Override
            public List<CatalogAgentEntry> getDefinitions() { return entries; } // GH-90000

            @Override
            public Optional<CatalogAgentEntry> findById(String agentId) { // GH-90000
                return entries.stream().filter(e -> e.getId().equals(agentId)).findFirst(); // GH-90000
            }

            @Override
            public List<CatalogAgentEntry> findByCapability(String capability) { // GH-90000
                return entries.stream() // GH-90000
                        .filter(e -> e.getCapabilities().contains(capability)) // GH-90000
                        .toList(); // GH-90000
            }

            @Override
            public List<CatalogAgentEntry> findByLevel(String level) { // GH-90000
                return entries.stream() // GH-90000
                        .filter(e -> level.equalsIgnoreCase(e.getLevel())) // GH-90000
                        .toList(); // GH-90000
            }

            @Override
            public List<CatalogAgentEntry> findByDomain(String domain) { // GH-90000
                return entries.stream() // GH-90000
                        .filter(e -> domain.equalsIgnoreCase(e.getDomain())) // GH-90000
                        .toList(); // GH-90000
            }

            @Override
            public Set<String> getAllCapabilities() { // GH-90000
                java.util.HashSet<String> caps = new java.util.HashSet<>(); // GH-90000
                entries.forEach(e -> caps.addAll(e.getCapabilities())); // GH-90000
                return caps;
            }

            @Override
            public int priority() { return prio; } // GH-90000
        };
    }

    private CatalogAgentEntry entry(String id, String domain) { // GH-90000
        return CatalogAgentEntry.builder() // GH-90000
                .id(id) // GH-90000
                .name(id) // GH-90000
                .metadata(java.util.Map.of("domain", domain)) // GH-90000
                .catalogId("platform [GH-90000]")
                .build(); // GH-90000
    }
}

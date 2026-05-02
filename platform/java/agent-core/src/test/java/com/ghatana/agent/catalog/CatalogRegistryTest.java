/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. 
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
@DisplayName("CatalogRegistry")
class CatalogRegistryTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Factory methods
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("empty()")
    class EmptyTests {

        @Test
        @DisplayName("should start with zero catalogs")
        void shouldBeEmpty() { 
            CatalogRegistry registry = CatalogRegistry.empty(); 
            assertThat(registry.getCatalogIds()).isEmpty(); 
            assertThat(registry.size()).isZero(); 
            assertThat(registry.allDefinitions()).isEmpty(); 
        }

        @Test
        @DisplayName("findById on empty registry returns empty")
        void findByIdOnEmptyRegistryReturnsEmpty() { 
            CatalogRegistry registry = CatalogRegistry.empty(); 
            assertThat(registry.findById("any-id")).isEmpty();
        }

        @Test
        @DisplayName("findByCapability on empty registry returns empty list")
        void findByCapabilityOnEmptyRegistryReturnsEmpty() { 
            CatalogRegistry registry = CatalogRegistry.empty(); 
            assertThat(registry.findByCapability("code-generation")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Registration
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("should register a catalog and index its definitions")
        void shouldRegisterCatalog() { 
            CatalogRegistry registry = CatalogRegistry.empty(); 
            registry.register(buildCatalog("platform", 100, 
                    entry("sentinel", "monitoring"), 
                    entry("data-ingestor", "data-processing"))); 

            assertThat(registry.getCatalogIds()).containsExactly("platform");
            assertThat(registry.size()).isEqualTo(2); 
        }

        @Test
        @DisplayName("should allow multiple catalogs")
        void shouldAllowMultipleCatalogs() { 
            CatalogRegistry registry = CatalogRegistry.empty(); 
            registry.register(buildCatalog("platform", 100, entry("agent-a", "domain-a"))); 
            registry.register(buildCatalog("yappc", 200, entry("agent-b", "domain-b"))); 

            assertThat(registry.getCatalogIds()).containsExactlyInAnyOrder("platform", "yappc"); 
            assertThat(registry.size()).isEqualTo(2); 
        }

        @Test
        @DisplayName("null catalog should throw NullPointerException")
        void shouldThrowOnNullCatalog() { 
            CatalogRegistry registry = CatalogRegistry.empty(); 
            assertThatThrownBy(() -> registry.register(null)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("replacing catalog with same id rebuilds index")
        void replacingCatalogRetainsOnlyNewDefinitions() { 
            CatalogRegistry registry = CatalogRegistry.empty(); 
            registry.register(buildCatalog("platform", 100, entry("v1-agent", "domain"))); 
            registry.register(buildCatalog("platform", 100, entry("v2-agent", "domain"))); 

            // Index rebuilt from re-registered catalog; v1-agent gone, v2-agent present
            assertThat(registry.findById("v2-agent")).isPresent();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Priority resolution
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Priority conflict resolution")
    class PriorityTests {

        @Test
        @DisplayName("lower numeric priority wins when two catalogs define same agent id")
        void lowerPriorityWins() { 
            CatalogAgentEntry platformVersion = CatalogAgentEntry.builder() 
                    .id("shared-agent")
                    .name("Platform Version")
                    .catalogId("platform")
                    .build(); 

            CatalogAgentEntry yappcVersion = CatalogAgentEntry.builder() 
                    .id("shared-agent")
                    .name("YAPPC Override")
                    .catalogId("yappc")
                    .build(); 

            AgentCatalog platformCatalog = stubCatalog("platform", 100, List.of(platformVersion)); 
            AgentCatalog yappcCatalog = stubCatalog("yappc", 200, List.of(yappcVersion)); 

            CatalogRegistry registry = CatalogRegistry.empty(); 
            registry.register(yappcCatalog); 
            registry.register(platformCatalog); 

            // platform has priority 100 (lower) — wins 
            Optional<CatalogAgentEntry> resolved = registry.findById("shared-agent");
            assertThat(resolved).isPresent(); 
            assertThat(resolved.get().getName()).isEqualTo("Platform Version");
        }

        @Test
        @DisplayName("higher numeric priority catalog is shadowed by lower numeric priority")
        void higherNumericPriorityIsShadowed() { 
            CatalogAgentEntry base = CatalogAgentEntry.builder() 
                    .id("conflict-agent")
                    .name("Base")
                    .catalogId("base")
                    .build(); 

            CatalogAgentEntry override = CatalogAgentEntry.builder() 
                    .id("conflict-agent")
                    .name("Override")
                    .catalogId("product")
                    .build(); 

            // base priority=50 wins over product priority=999
            AgentCatalog baseCatalog = stubCatalog("base", 50, List.of(base)); 
            AgentCatalog productCatalog = stubCatalog("product", 999, List.of(override)); 

            CatalogRegistry registry = CatalogRegistry.empty(); 
            registry.register(productCatalog); 
            registry.register(baseCatalog); 

            assertThat(registry.findById("conflict-agent").get().getName()).isEqualTo("Base");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findById
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("should find agent by id")
        void shouldFindById() { 
            CatalogRegistry registry = populatedRegistry(); 
            assertThat(registry.findById("sentinel")).isPresent();
            assertThat(registry.findById("sentinel").get().getName()).isEqualTo("Sentinel");
        }

        @Test
        @DisplayName("should return empty for unknown id")
        void shouldReturnEmptyForUnknownId() { 
            CatalogRegistry registry = populatedRegistry(); 
            assertThat(registry.findById("does-not-exist")).isEmpty();
        }

        @Test
        @DisplayName("unknown id returns empty")
        void shouldReturnEmptyForMissingId() { 
            CatalogRegistry registry = CatalogRegistry.empty(); 
            assertThat(registry.findById("unknown-id-xyz")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findByCapability
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByCapability()")
    class FindByCapabilityTests {

        @Test
        @DisplayName("should return agents with requested capability")
        void shouldFindByCapability() { 
            CatalogAgentEntry a1 = CatalogAgentEntry.builder() 
                    .id("agent-a")
                    .capabilities(Set.of("monitoring", "alerting")) 
                    .catalogId("platform")
                    .build(); 
            CatalogAgentEntry a2 = CatalogAgentEntry.builder() 
                    .id("agent-b")
                    .capabilities(Set.of("data-transformation"))
                    .catalogId("platform")
                    .build(); 

            CatalogRegistry registry = CatalogRegistry.empty(); 
            registry.register(stubCatalog("platform", 100, List.of(a1, a2))); 

            List<CatalogAgentEntry> monitoringAgents = registry.findByCapability("monitoring");
            assertThat(monitoringAgents).hasSize(1); 
            assertThat(monitoringAgents.get(0).getId()).isEqualTo("agent-a");
        }

        @Test
        @DisplayName("should return empty list when no agent has requested capability")
        void shouldReturnEmptyForUnknownCapability() { 
            CatalogRegistry registry = populatedRegistry(); 
            assertThat(registry.findByCapability("unknown-capability")).isEmpty();
        }

        @Test
        @DisplayName("should return multiple agents sharing a capability")
        void shouldReturnMultipleAgentsWithSharedCapability() { 
            CatalogAgentEntry a1 = CatalogAgentEntry.builder() 
                    .id("agent-1")
                    .capabilities(Set.of("shared-cap"))
                    .catalogId("c1")
                    .build(); 
            CatalogAgentEntry a2 = CatalogAgentEntry.builder() 
                    .id("agent-2")
                    .capabilities(Set.of("shared-cap", "extra-cap")) 
                    .catalogId("c1")
                    .build(); 

            CatalogRegistry registry = CatalogRegistry.empty(); 
            registry.register(stubCatalog("c1", 100, List.of(a1, a2))); 

            assertThat(registry.findByCapability("shared-cap")).hasSize(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findByLevel + findByDomain
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByLevel() and findByDomain()")
    class FindByLevelDomainTests {

        @Test
        @DisplayName("should find agents by level (case-insensitive)")
        void shouldFindByLevel() { 
            CatalogAgentEntry strategic = CatalogAgentEntry.builder() 
                    .id("strategic-agent")
                    .metadata(java.util.Map.of("level", "strategic")) 
                    .catalogId("platform")
                    .build(); 
            CatalogAgentEntry worker = CatalogAgentEntry.builder() 
                    .id("worker-agent")
                    .metadata(java.util.Map.of("level", "worker")) 
                    .catalogId("platform")
                    .build(); 

            CatalogRegistry registry = CatalogRegistry.empty(); 
            registry.register(stubCatalog("platform", 100, List.of(strategic, worker))); 

            assertThat(registry.findByLevel("strategic")).hasSize(1);
            assertThat(registry.findByLevel("STRATEGIC")).hasSize(1); // case-insensitive
            assertThat(registry.findByLevel("worker")).hasSize(1);
        }

        @Test
        @DisplayName("should find agents by domain (case-insensitive)")
        void shouldFindByDomain() { 
            CatalogAgentEntry dataAgent = CatalogAgentEntry.builder() 
                    .id("data-agent")
                    .metadata(java.util.Map.of("domain", "data-processing")) 
                    .catalogId("platform")
                    .build(); 
            CatalogAgentEntry secAgent = CatalogAgentEntry.builder() 
                    .id("security-agent")
                    .metadata(java.util.Map.of("domain", "security")) 
                    .catalogId("platform")
                    .build(); 

            CatalogRegistry registry = CatalogRegistry.empty(); 
            registry.register(stubCatalog("platform", 100, List.of(dataAgent, secAgent))); 

            assertThat(registry.findByDomain("data-processing")).hasSize(1);
            assertThat(registry.findByDomain("DATA-PROCESSING")).hasSize(1); // case-insensitive
            assertThat(registry.findByDomain("security")).hasSize(1);
            assertThat(registry.findByDomain("unknown")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Unregister
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("unregister()")
    class UnregisterTests {

        @Test
        @DisplayName("should unregister catalog and remove its definitions from index")
        void shouldUnregisterAndRebuildIndex() { 
            CatalogRegistry registry = CatalogRegistry.empty(); 
            registry.register(buildCatalog("platform", 100, entry("platform-agent", "domain"))); 
            registry.register(buildCatalog("yappc", 200, entry("yappc-agent", "domain"))); 

            assertThat(registry.size()).isEqualTo(2); 

            registry.unregister("platform");

            assertThat(registry.getCatalogIds()).containsExactly("yappc");
            assertThat(registry.findById("platform-agent")).isEmpty();
            assertThat(registry.findById("yappc-agent")).isPresent();
        }

        @Test
        @DisplayName("unregistering unknown catalog id is a no-op")
        void shouldIgnoreUnknownId() { 
            CatalogRegistry registry = CatalogRegistry.empty(); 
            registry.register(buildCatalog("platform", 100, entry("agent", "domain"))); 

            registry.unregister("non-existent");

            assertThat(registry.size()).isEqualTo(1); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getCatalog
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getCatalog()")
    class GetCatalogTests {

        @Test
        @DisplayName("should return catalog by id")
        void shouldReturnCatalogById() { 
            AgentCatalog catalog = buildCatalog("platform", 100, 
                    entry("agent-a", "domain")); 
            CatalogRegistry registry = CatalogRegistry.empty(); 
            registry.register(catalog); 

            assertThat(registry.getCatalog("platform")).isPresent();
            assertThat(registry.getCatalog("platform").get().getCatalogId()).isEqualTo("platform");
        }

        @Test
        @DisplayName("should return empty for unknown catalog id")
        void shouldReturnEmptyForUnknownCatalog() { 
            CatalogRegistry registry = CatalogRegistry.empty(); 
            assertThat(registry.getCatalog("does-not-exist")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private CatalogRegistry populatedRegistry() { 
        CatalogRegistry registry = CatalogRegistry.empty(); 
        registry.register(buildCatalog("platform", 100, 
                CatalogAgentEntry.builder() 
                        .id("sentinel")
                        .name("Sentinel")
                        .capabilities(Set.of("monitoring", "alerting")) 
                        .metadata(java.util.Map.of("domain", "monitoring")) 
                        .catalogId("platform")
                        .build(), 
                CatalogAgentEntry.builder() 
                        .id("data-transformer")
                        .name("Data Transformer")
                        .capabilities(Set.of("data-transformation"))
                        .metadata(java.util.Map.of("domain", "data-processing")) 
                        .catalogId("platform")
                        .build())); 
        return registry;
    }

    private AgentCatalog buildCatalog(String catalogId, int priority, CatalogAgentEntry... entries) { 
        return stubCatalog(catalogId, priority, List.of(entries)); 
    }

    private AgentCatalog stubCatalog(String catalogId, int prio, List<CatalogAgentEntry> entries) { 
        return new AgentCatalog() { 
            @Override
            public String getCatalogId() { return catalogId; } 

            @Override
            public String getDisplayName() { return catalogId + " catalog"; } 

            @Override
            public List<CatalogAgentEntry> getDefinitions() { return entries; } 

            @Override
            public Optional<CatalogAgentEntry> findById(String agentId) { 
                return entries.stream().filter(e -> e.getId().equals(agentId)).findFirst(); 
            }

            @Override
            public List<CatalogAgentEntry> findByCapability(String capability) { 
                return entries.stream() 
                        .filter(e -> e.getCapabilities().contains(capability)) 
                        .toList(); 
            }

            @Override
            public List<CatalogAgentEntry> findByLevel(String level) { 
                return entries.stream() 
                        .filter(e -> level.equalsIgnoreCase(e.getLevel())) 
                        .toList(); 
            }

            @Override
            public List<CatalogAgentEntry> findByDomain(String domain) { 
                return entries.stream() 
                        .filter(e -> domain.equalsIgnoreCase(e.getDomain())) 
                        .toList(); 
            }

            @Override
            public Set<String> getAllCapabilities() { 
                java.util.HashSet<String> caps = new java.util.HashSet<>(); 
                entries.forEach(e -> caps.addAll(e.getCapabilities())); 
                return caps;
            }

            @Override
            public int priority() { return prio; } 
        };
    }

    private CatalogAgentEntry entry(String id, String domain) { 
        return CatalogAgentEntry.builder() 
                .id(id) 
                .name(id) 
                .metadata(java.util.Map.of("domain", domain)) 
                .catalogId("platform")
                .build(); 
    }
}

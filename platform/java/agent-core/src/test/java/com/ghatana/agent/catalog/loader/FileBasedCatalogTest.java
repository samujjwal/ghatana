/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. 
 */

package com.ghatana.agent.catalog.loader;

import com.ghatana.agent.catalog.CatalogAgentEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FileBasedCatalog}.
 */
@DisplayName("FileBasedCatalog")
class FileBasedCatalogTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Basic construction
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should build minimal catalog")
        void shouldBuildMinimal() { 
            FileBasedCatalog catalog = FileBasedCatalog.builder() 
                    .catalogId("platform")
                    .displayName("Platform Catalog")
                    .build(); 

            assertThat(catalog.getCatalogId()).isEqualTo("platform");
            assertThat(catalog.getDisplayName()).isEqualTo("Platform Catalog");
            assertThat(catalog.getDefinitions()).isEmpty(); 
        }

        @Test
        @DisplayName("should build with definitions")
        void shouldBuildWithDefinitions() { 
            List<CatalogAgentEntry> entries = List.of( 
                    makeEntry("agent-a", "cap-a"), 
                    makeEntry("agent-b", "cap-b") 
            );

            FileBasedCatalog catalog = FileBasedCatalog.builder() 
                    .catalogId("platform")
                    .displayName("Platform")
                    .definitions(entries) 
                    .build(); 

            assertThat(catalog.getDefinitions()).hasSize(2); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findById
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("should find existing agent by id")
        void shouldFindById() { 
            FileBasedCatalog catalog = catalogWithEntries( 
                    makeEntry("sentinel", "monitoring"), 
                    makeEntry("data-ingestor", "ingestion") 
            );

            Optional<CatalogAgentEntry> result = catalog.findById("sentinel");
            assertThat(result).isPresent(); 
            assertThat(result.get().getId()).isEqualTo("sentinel");
        }

        @Test
        @DisplayName("should return empty for unknown id")
        void shouldReturnEmptyForUnknownId() { 
            FileBasedCatalog catalog = catalogWithEntries(makeEntry("sentinel", "monitoring")); 
            assertThat(catalog.findById("unknown-agent")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findByCapability
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByCapability()")
    class FindByCapabilityTests {

        @Test
        @DisplayName("should find agents with matching capability")
        void shouldFindByCapability() { 
            CatalogAgentEntry monitoringAgent = CatalogAgentEntry.builder() 
                    .id("monitor")
                    .capabilities(Set.of("monitoring", "alerting")) 
                    .catalogId("platform")
                    .build(); 
            CatalogAgentEntry dataAgent = CatalogAgentEntry.builder() 
                    .id("data-agent")
                    .capabilities(Set.of("data-transformation"))
                    .catalogId("platform")
                    .build(); 

            FileBasedCatalog catalog = FileBasedCatalog.builder() 
                    .catalogId("platform")
                    .displayName("Platform")
                    .definitions(List.of(monitoringAgent, dataAgent)) 
                    .build(); 

            List<CatalogAgentEntry> results = catalog.findByCapability("monitoring");
            assertThat(results).hasSize(1); 
            assertThat(results.get(0).getId()).isEqualTo("monitor");
        }

        @Test
        @DisplayName("should return empty list when no agents have capability")
        void shouldReturnEmptyListWhenNoMatches() { 
            FileBasedCatalog catalog = catalogWithEntries(makeEntry("agent", "cap-a")); 
            assertThat(catalog.findByCapability("cap-unknown")).isEmpty();
        }

        @Test
        @DisplayName("getDefinitions returns unmodifiable view")
        void getDefinitionsShouldBeUnmodifiable() { 
            FileBasedCatalog catalog = catalogWithEntries(makeEntry("agent", "cap")); 
            assertThat(catalog.getDefinitions()).isUnmodifiable(); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findByLevel
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByLevel()")
    class FindByLevelTests {

        @Test
        @DisplayName("should find agents at matching level")
        void shouldFindByLevel() { 
            CatalogAgentEntry strategic = CatalogAgentEntry.builder() 
                    .id("strategic-agent")
                    .metadata(Map.of("level", "strategic")) 
                    .catalogId("platform")
                    .build(); 
            CatalogAgentEntry worker = CatalogAgentEntry.builder() 
                    .id("worker-agent")
                    .metadata(Map.of("level", "worker")) 
                    .catalogId("platform")
                    .build(); 

            FileBasedCatalog catalog = FileBasedCatalog.builder() 
                    .catalogId("platform")
                    .displayName("Platform")
                    .definitions(List.of(strategic, worker)) 
                    .build(); 

            assertThat(catalog.findByLevel("strategic")).hasSize(1);
            assertThat(catalog.findByLevel("strategic").get(0).getId()).isEqualTo("strategic-agent");
            assertThat(catalog.findByLevel("worker")).hasSize(1);
        }

        @Test
        @DisplayName("should return empty when no agents at given level")
        void shouldReturnEmptyWhenNoLevel() { 
            FileBasedCatalog catalog = catalogWithEntries(makeEntry("agent", "cap")); 
            assertThat(catalog.findByLevel("expert")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findByDomain
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByDomain()")
    class FindByDomainTests {

        @Test
        @DisplayName("should find agents in matching domain")
        void shouldFindByDomain() { 
            CatalogAgentEntry dataAgent = CatalogAgentEntry.builder() 
                    .id("data-agent")
                    .metadata(Map.of("domain", "data-processing")) 
                    .catalogId("platform")
                    .build(); 
            CatalogAgentEntry secAgent = CatalogAgentEntry.builder() 
                    .id("security-agent")
                    .metadata(Map.of("domain", "security")) 
                    .catalogId("platform")
                    .build(); 

            FileBasedCatalog catalog = FileBasedCatalog.builder() 
                    .catalogId("platform")
                    .displayName("Platform")
                    .definitions(List.of(dataAgent, secAgent)) 
                    .build(); 

            assertThat(catalog.findByDomain("data-processing")).hasSize(1);
            assertThat(catalog.findByDomain("security")).hasSize(1);
            assertThat(catalog.findByDomain("unknown")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getAllCapabilities
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAllCapabilities()")
    class GetAllCapabilitiesTests {

        @Test
        @DisplayName("should aggregate capabilities across all definitions")
        void shouldAggregateCapabilities() { 
            CatalogAgentEntry a = CatalogAgentEntry.builder() 
                    .id("agent-a")
                    .capabilities(Set.of("cap-1", "cap-2")) 
                    .catalogId("platform")
                    .build(); 
            CatalogAgentEntry b = CatalogAgentEntry.builder() 
                    .id("agent-b")
                    .capabilities(Set.of("cap-2", "cap-3")) 
                    .catalogId("platform")
                    .build(); 

            FileBasedCatalog catalog = FileBasedCatalog.builder() 
                    .catalogId("platform")
                    .displayName("Platform")
                    .definitions(List.of(a, b)) 
                    .build(); 

            Set<String> allCaps = catalog.getAllCapabilities(); 
            assertThat(allCaps).containsExactlyInAnyOrder("cap-1", "cap-2", "cap-3"); 
        }

        @Test
        @DisplayName("should return empty set for empty catalog")
        void shouldReturnEmptySetForEmptyCatalog() { 
            FileBasedCatalog catalog = FileBasedCatalog.builder() 
                    .catalogId("empty")
                    .displayName("Empty")
                    .build(); 

            assertThat(catalog.getAllCapabilities()).isEmpty(); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // priority() 
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("priority()")
    class PriorityTests {

        @Test
        @DisplayName("should use priority from metadata")
        void shouldUsePriorityFromMetadata() { 
            FileBasedCatalog catalog = FileBasedCatalog.builder() 
                    .catalogId("platform")
                    .displayName("Platform")
                    .metadata(Map.of("priority", 100)) 
                    .build(); 

            assertThat(catalog.priority()).isEqualTo(100); 
        }

        @Test
        @DisplayName("should default to 1000 when metadata has no priority")
        void shouldDefaultPriorityTo1000() { 
            FileBasedCatalog catalog = FileBasedCatalog.builder() 
                    .catalogId("platform")
                    .displayName("Platform")
                    .build(); 

            assertThat(catalog.priority()).isEqualTo(1000); 
        }

        @Test
        @DisplayName("should handle integer metadata priority")
        void shouldHandleIntegerPriority() { 
            FileBasedCatalog catalog = FileBasedCatalog.builder() 
                    .catalogId("platform")
                    .displayName("Platform")
                    .metadata(Map.of("priority", 50)) 
                    .build(); 

            assertThat(catalog.priority()).isEqualTo(50); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // extendsCatalogs
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("extendsCatalogs")
    class ExtendsTests {

        @Test
        @DisplayName("should default to empty extends list")
        void shouldDefaultToEmpty() { 
            FileBasedCatalog catalog = FileBasedCatalog.builder() 
                    .catalogId("platform")
                    .displayName("Platform")
                    .build(); 

            assertThat(catalog.getExtendsCatalogs()).isEmpty(); 
        }

        @Test
        @DisplayName("should store extends list")
        void shouldStoreExtendsList() { 
            FileBasedCatalog catalog = FileBasedCatalog.builder() 
                    .catalogId("yappc")
                    .displayName("YAPPC")
                    .extendsCatalogs(List.of("platform", "domain-base")) 
                    .build(); 

            assertThat(catalog.getExtendsCatalogs()) 
                    .containsExactly("platform", "domain-base"); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private CatalogAgentEntry makeEntry(String id, String capability) { 
        return CatalogAgentEntry.builder() 
                .id(id) 
                .name(id) 
                .capabilities(Set.of(capability)) 
                .catalogId("platform")
                .build(); 
    }

    private FileBasedCatalog catalogWithEntries(CatalogAgentEntry... entries) { 
        return FileBasedCatalog.builder() 
                .catalogId("platform")
                .displayName("Platform Catalog")
                .definitions(List.of(entries)) 
                .build(); 
    }
}

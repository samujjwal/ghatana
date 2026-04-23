/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
        void shouldBuildMinimal() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform")
                    .displayName("Platform Catalog")
                    .build(); // GH-90000

            assertThat(catalog.getCatalogId()).isEqualTo("platform");
            assertThat(catalog.getDisplayName()).isEqualTo("Platform Catalog");
            assertThat(catalog.getDefinitions()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should build with definitions")
        void shouldBuildWithDefinitions() { // GH-90000
            List<CatalogAgentEntry> entries = List.of( // GH-90000
                    makeEntry("agent-a", "cap-a"), // GH-90000
                    makeEntry("agent-b", "cap-b") // GH-90000
            );

            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform")
                    .displayName("Platform")
                    .definitions(entries) // GH-90000
                    .build(); // GH-90000

            assertThat(catalog.getDefinitions()).hasSize(2); // GH-90000
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
        void shouldFindById() { // GH-90000
            FileBasedCatalog catalog = catalogWithEntries( // GH-90000
                    makeEntry("sentinel", "monitoring"), // GH-90000
                    makeEntry("data-ingestor", "ingestion") // GH-90000
            );

            Optional<CatalogAgentEntry> result = catalog.findById("sentinel");
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getId()).isEqualTo("sentinel");
        }

        @Test
        @DisplayName("should return empty for unknown id")
        void shouldReturnEmptyForUnknownId() { // GH-90000
            FileBasedCatalog catalog = catalogWithEntries(makeEntry("sentinel", "monitoring")); // GH-90000
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
        void shouldFindByCapability() { // GH-90000
            CatalogAgentEntry monitoringAgent = CatalogAgentEntry.builder() // GH-90000
                    .id("monitor")
                    .capabilities(Set.of("monitoring", "alerting")) // GH-90000
                    .catalogId("platform")
                    .build(); // GH-90000
            CatalogAgentEntry dataAgent = CatalogAgentEntry.builder() // GH-90000
                    .id("data-agent")
                    .capabilities(Set.of("data-transformation"))
                    .catalogId("platform")
                    .build(); // GH-90000

            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform")
                    .displayName("Platform")
                    .definitions(List.of(monitoringAgent, dataAgent)) // GH-90000
                    .build(); // GH-90000

            List<CatalogAgentEntry> results = catalog.findByCapability("monitoring");
            assertThat(results).hasSize(1); // GH-90000
            assertThat(results.get(0).getId()).isEqualTo("monitor");
        }

        @Test
        @DisplayName("should return empty list when no agents have capability")
        void shouldReturnEmptyListWhenNoMatches() { // GH-90000
            FileBasedCatalog catalog = catalogWithEntries(makeEntry("agent", "cap-a")); // GH-90000
            assertThat(catalog.findByCapability("cap-unknown")).isEmpty();
        }

        @Test
        @DisplayName("getDefinitions returns unmodifiable view")
        void getDefinitionsShouldBeUnmodifiable() { // GH-90000
            FileBasedCatalog catalog = catalogWithEntries(makeEntry("agent", "cap")); // GH-90000
            assertThat(catalog.getDefinitions()).isUnmodifiable(); // GH-90000
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
        void shouldFindByLevel() { // GH-90000
            CatalogAgentEntry strategic = CatalogAgentEntry.builder() // GH-90000
                    .id("strategic-agent")
                    .metadata(Map.of("level", "strategic")) // GH-90000
                    .catalogId("platform")
                    .build(); // GH-90000
            CatalogAgentEntry worker = CatalogAgentEntry.builder() // GH-90000
                    .id("worker-agent")
                    .metadata(Map.of("level", "worker")) // GH-90000
                    .catalogId("platform")
                    .build(); // GH-90000

            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform")
                    .displayName("Platform")
                    .definitions(List.of(strategic, worker)) // GH-90000
                    .build(); // GH-90000

            assertThat(catalog.findByLevel("strategic")).hasSize(1);
            assertThat(catalog.findByLevel("strategic").get(0).getId()).isEqualTo("strategic-agent");
            assertThat(catalog.findByLevel("worker")).hasSize(1);
        }

        @Test
        @DisplayName("should return empty when no agents at given level")
        void shouldReturnEmptyWhenNoLevel() { // GH-90000
            FileBasedCatalog catalog = catalogWithEntries(makeEntry("agent", "cap")); // GH-90000
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
        void shouldFindByDomain() { // GH-90000
            CatalogAgentEntry dataAgent = CatalogAgentEntry.builder() // GH-90000
                    .id("data-agent")
                    .metadata(Map.of("domain", "data-processing")) // GH-90000
                    .catalogId("platform")
                    .build(); // GH-90000
            CatalogAgentEntry secAgent = CatalogAgentEntry.builder() // GH-90000
                    .id("security-agent")
                    .metadata(Map.of("domain", "security")) // GH-90000
                    .catalogId("platform")
                    .build(); // GH-90000

            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform")
                    .displayName("Platform")
                    .definitions(List.of(dataAgent, secAgent)) // GH-90000
                    .build(); // GH-90000

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
        void shouldAggregateCapabilities() { // GH-90000
            CatalogAgentEntry a = CatalogAgentEntry.builder() // GH-90000
                    .id("agent-a")
                    .capabilities(Set.of("cap-1", "cap-2")) // GH-90000
                    .catalogId("platform")
                    .build(); // GH-90000
            CatalogAgentEntry b = CatalogAgentEntry.builder() // GH-90000
                    .id("agent-b")
                    .capabilities(Set.of("cap-2", "cap-3")) // GH-90000
                    .catalogId("platform")
                    .build(); // GH-90000

            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform")
                    .displayName("Platform")
                    .definitions(List.of(a, b)) // GH-90000
                    .build(); // GH-90000

            Set<String> allCaps = catalog.getAllCapabilities(); // GH-90000
            assertThat(allCaps).containsExactlyInAnyOrder("cap-1", "cap-2", "cap-3"); // GH-90000
        }

        @Test
        @DisplayName("should return empty set for empty catalog")
        void shouldReturnEmptySetForEmptyCatalog() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("empty")
                    .displayName("Empty")
                    .build(); // GH-90000

            assertThat(catalog.getAllCapabilities()).isEmpty(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // priority() // GH-90000
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("priority()")
    class PriorityTests {

        @Test
        @DisplayName("should use priority from metadata")
        void shouldUsePriorityFromMetadata() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform")
                    .displayName("Platform")
                    .metadata(Map.of("priority", 100)) // GH-90000
                    .build(); // GH-90000

            assertThat(catalog.priority()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("should default to 1000 when metadata has no priority")
        void shouldDefaultPriorityTo1000() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform")
                    .displayName("Platform")
                    .build(); // GH-90000

            assertThat(catalog.priority()).isEqualTo(1000); // GH-90000
        }

        @Test
        @DisplayName("should handle integer metadata priority")
        void shouldHandleIntegerPriority() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform")
                    .displayName("Platform")
                    .metadata(Map.of("priority", 50)) // GH-90000
                    .build(); // GH-90000

            assertThat(catalog.priority()).isEqualTo(50); // GH-90000
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
        void shouldDefaultToEmpty() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform")
                    .displayName("Platform")
                    .build(); // GH-90000

            assertThat(catalog.getExtendsCatalogs()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should store extends list")
        void shouldStoreExtendsList() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("yappc")
                    .displayName("YAPPC")
                    .extendsCatalogs(List.of("platform", "domain-base")) // GH-90000
                    .build(); // GH-90000

            assertThat(catalog.getExtendsCatalogs()) // GH-90000
                    .containsExactly("platform", "domain-base"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private CatalogAgentEntry makeEntry(String id, String capability) { // GH-90000
        return CatalogAgentEntry.builder() // GH-90000
                .id(id) // GH-90000
                .name(id) // GH-90000
                .capabilities(Set.of(capability)) // GH-90000
                .catalogId("platform")
                .build(); // GH-90000
    }

    private FileBasedCatalog catalogWithEntries(CatalogAgentEntry... entries) { // GH-90000
        return FileBasedCatalog.builder() // GH-90000
                .catalogId("platform")
                .displayName("Platform Catalog")
                .definitions(List.of(entries)) // GH-90000
                .build(); // GH-90000
    }
}

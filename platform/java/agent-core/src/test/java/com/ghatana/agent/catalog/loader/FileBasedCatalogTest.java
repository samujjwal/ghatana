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
@DisplayName("FileBasedCatalog [GH-90000]")
class FileBasedCatalogTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Basic construction
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Builder [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("should build minimal catalog [GH-90000]")
        void shouldBuildMinimal() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform [GH-90000]")
                    .displayName("Platform Catalog [GH-90000]")
                    .build(); // GH-90000

            assertThat(catalog.getCatalogId()).isEqualTo("platform [GH-90000]");
            assertThat(catalog.getDisplayName()).isEqualTo("Platform Catalog [GH-90000]");
            assertThat(catalog.getDefinitions()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should build with definitions [GH-90000]")
        void shouldBuildWithDefinitions() { // GH-90000
            List<CatalogAgentEntry> entries = List.of( // GH-90000
                    makeEntry("agent-a", "cap-a"), // GH-90000
                    makeEntry("agent-b", "cap-b") // GH-90000
            );

            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform [GH-90000]")
                    .displayName("Platform [GH-90000]")
                    .definitions(entries) // GH-90000
                    .build(); // GH-90000

            assertThat(catalog.getDefinitions()).hasSize(2); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findById
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findById() [GH-90000]")
    class FindByIdTests {

        @Test
        @DisplayName("should find existing agent by id [GH-90000]")
        void shouldFindById() { // GH-90000
            FileBasedCatalog catalog = catalogWithEntries( // GH-90000
                    makeEntry("sentinel", "monitoring"), // GH-90000
                    makeEntry("data-ingestor", "ingestion") // GH-90000
            );

            Optional<CatalogAgentEntry> result = catalog.findById("sentinel [GH-90000]");
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getId()).isEqualTo("sentinel [GH-90000]");
        }

        @Test
        @DisplayName("should return empty for unknown id [GH-90000]")
        void shouldReturnEmptyForUnknownId() { // GH-90000
            FileBasedCatalog catalog = catalogWithEntries(makeEntry("sentinel", "monitoring")); // GH-90000
            assertThat(catalog.findById("unknown-agent [GH-90000]")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findByCapability
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByCapability() [GH-90000]")
    class FindByCapabilityTests {

        @Test
        @DisplayName("should find agents with matching capability [GH-90000]")
        void shouldFindByCapability() { // GH-90000
            CatalogAgentEntry monitoringAgent = CatalogAgentEntry.builder() // GH-90000
                    .id("monitor [GH-90000]")
                    .capabilities(Set.of("monitoring", "alerting")) // GH-90000
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000
            CatalogAgentEntry dataAgent = CatalogAgentEntry.builder() // GH-90000
                    .id("data-agent [GH-90000]")
                    .capabilities(Set.of("data-transformation [GH-90000]"))
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000

            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform [GH-90000]")
                    .displayName("Platform [GH-90000]")
                    .definitions(List.of(monitoringAgent, dataAgent)) // GH-90000
                    .build(); // GH-90000

            List<CatalogAgentEntry> results = catalog.findByCapability("monitoring [GH-90000]");
            assertThat(results).hasSize(1); // GH-90000
            assertThat(results.get(0).getId()).isEqualTo("monitor [GH-90000]");
        }

        @Test
        @DisplayName("should return empty list when no agents have capability [GH-90000]")
        void shouldReturnEmptyListWhenNoMatches() { // GH-90000
            FileBasedCatalog catalog = catalogWithEntries(makeEntry("agent", "cap-a")); // GH-90000
            assertThat(catalog.findByCapability("cap-unknown [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("getDefinitions returns unmodifiable view [GH-90000]")
        void getDefinitionsShouldBeUnmodifiable() { // GH-90000
            FileBasedCatalog catalog = catalogWithEntries(makeEntry("agent", "cap")); // GH-90000
            assertThat(catalog.getDefinitions()).isUnmodifiable(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findByLevel
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByLevel() [GH-90000]")
    class FindByLevelTests {

        @Test
        @DisplayName("should find agents at matching level [GH-90000]")
        void shouldFindByLevel() { // GH-90000
            CatalogAgentEntry strategic = CatalogAgentEntry.builder() // GH-90000
                    .id("strategic-agent [GH-90000]")
                    .metadata(Map.of("level", "strategic")) // GH-90000
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000
            CatalogAgentEntry worker = CatalogAgentEntry.builder() // GH-90000
                    .id("worker-agent [GH-90000]")
                    .metadata(Map.of("level", "worker")) // GH-90000
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000

            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform [GH-90000]")
                    .displayName("Platform [GH-90000]")
                    .definitions(List.of(strategic, worker)) // GH-90000
                    .build(); // GH-90000

            assertThat(catalog.findByLevel("strategic [GH-90000]")).hasSize(1);
            assertThat(catalog.findByLevel("strategic [GH-90000]").get(0).getId()).isEqualTo("strategic-agent [GH-90000]");
            assertThat(catalog.findByLevel("worker [GH-90000]")).hasSize(1);
        }

        @Test
        @DisplayName("should return empty when no agents at given level [GH-90000]")
        void shouldReturnEmptyWhenNoLevel() { // GH-90000
            FileBasedCatalog catalog = catalogWithEntries(makeEntry("agent", "cap")); // GH-90000
            assertThat(catalog.findByLevel("expert [GH-90000]")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // findByDomain
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByDomain() [GH-90000]")
    class FindByDomainTests {

        @Test
        @DisplayName("should find agents in matching domain [GH-90000]")
        void shouldFindByDomain() { // GH-90000
            CatalogAgentEntry dataAgent = CatalogAgentEntry.builder() // GH-90000
                    .id("data-agent [GH-90000]")
                    .metadata(Map.of("domain", "data-processing")) // GH-90000
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000
            CatalogAgentEntry secAgent = CatalogAgentEntry.builder() // GH-90000
                    .id("security-agent [GH-90000]")
                    .metadata(Map.of("domain", "security")) // GH-90000
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000

            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform [GH-90000]")
                    .displayName("Platform [GH-90000]")
                    .definitions(List.of(dataAgent, secAgent)) // GH-90000
                    .build(); // GH-90000

            assertThat(catalog.findByDomain("data-processing [GH-90000]")).hasSize(1);
            assertThat(catalog.findByDomain("security [GH-90000]")).hasSize(1);
            assertThat(catalog.findByDomain("unknown [GH-90000]")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getAllCapabilities
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAllCapabilities() [GH-90000]")
    class GetAllCapabilitiesTests {

        @Test
        @DisplayName("should aggregate capabilities across all definitions [GH-90000]")
        void shouldAggregateCapabilities() { // GH-90000
            CatalogAgentEntry a = CatalogAgentEntry.builder() // GH-90000
                    .id("agent-a [GH-90000]")
                    .capabilities(Set.of("cap-1", "cap-2")) // GH-90000
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000
            CatalogAgentEntry b = CatalogAgentEntry.builder() // GH-90000
                    .id("agent-b [GH-90000]")
                    .capabilities(Set.of("cap-2", "cap-3")) // GH-90000
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000

            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform [GH-90000]")
                    .displayName("Platform [GH-90000]")
                    .definitions(List.of(a, b)) // GH-90000
                    .build(); // GH-90000

            Set<String> allCaps = catalog.getAllCapabilities(); // GH-90000
            assertThat(allCaps).containsExactlyInAnyOrder("cap-1", "cap-2", "cap-3"); // GH-90000
        }

        @Test
        @DisplayName("should return empty set for empty catalog [GH-90000]")
        void shouldReturnEmptySetForEmptyCatalog() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("empty [GH-90000]")
                    .displayName("Empty [GH-90000]")
                    .build(); // GH-90000

            assertThat(catalog.getAllCapabilities()).isEmpty(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // priority() // GH-90000
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("priority() [GH-90000]")
    class PriorityTests {

        @Test
        @DisplayName("should use priority from metadata [GH-90000]")
        void shouldUsePriorityFromMetadata() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform [GH-90000]")
                    .displayName("Platform [GH-90000]")
                    .metadata(Map.of("priority", 100)) // GH-90000
                    .build(); // GH-90000

            assertThat(catalog.priority()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("should default to 1000 when metadata has no priority [GH-90000]")
        void shouldDefaultPriorityTo1000() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform [GH-90000]")
                    .displayName("Platform [GH-90000]")
                    .build(); // GH-90000

            assertThat(catalog.priority()).isEqualTo(1000); // GH-90000
        }

        @Test
        @DisplayName("should handle integer metadata priority [GH-90000]")
        void shouldHandleIntegerPriority() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform [GH-90000]")
                    .displayName("Platform [GH-90000]")
                    .metadata(Map.of("priority", 50)) // GH-90000
                    .build(); // GH-90000

            assertThat(catalog.priority()).isEqualTo(50); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // extendsCatalogs
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("extendsCatalogs [GH-90000]")
    class ExtendsTests {

        @Test
        @DisplayName("should default to empty extends list [GH-90000]")
        void shouldDefaultToEmpty() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("platform [GH-90000]")
                    .displayName("Platform [GH-90000]")
                    .build(); // GH-90000

            assertThat(catalog.getExtendsCatalogs()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should store extends list [GH-90000]")
        void shouldStoreExtendsList() { // GH-90000
            FileBasedCatalog catalog = FileBasedCatalog.builder() // GH-90000
                    .catalogId("yappc [GH-90000]")
                    .displayName("YAPPC [GH-90000]")
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
                .catalogId("platform [GH-90000]")
                .build(); // GH-90000
    }

    private FileBasedCatalog catalogWithEntries(CatalogAgentEntry... entries) { // GH-90000
        return FileBasedCatalog.builder() // GH-90000
                .catalogId("platform [GH-90000]")
                .displayName("Platform Catalog [GH-90000]")
                .definitions(List.of(entries)) // GH-90000
                .build(); // GH-90000
    }
}

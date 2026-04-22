/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.agent.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CatalogAgentEntry}.
 */
@DisplayName("CatalogAgentEntry [GH-90000]")
class CatalogAgentEntryTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Builder defaults
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Builder [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("should require at minimum an id and catalogId [GH-90000]")
        void shouldBuildWithMinimalFields() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("my-agent [GH-90000]")
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000

            assertThat(entry.getId()).isEqualTo("my-agent [GH-90000]");
            assertThat(entry.getCatalogId()).isEqualTo("platform [GH-90000]");
        }

        @Test
        @DisplayName("should default version to 1.0.0 [GH-90000]")
        void shouldDefaultVersion() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent [GH-90000]")
                    .catalogId("catalog [GH-90000]")
                    .build(); // GH-90000

            assertThat(entry.getVersion()).isEqualTo("1.0.0 [GH-90000]");
        }

        @Test
        @DisplayName("should default metadata to empty map [GH-90000]")
        void shouldDefaultMetadataToEmptyMap() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent [GH-90000]")
                    .catalogId("catalog [GH-90000]")
                    .build(); // GH-90000

            assertThat(entry.getMetadata()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should default capabilities to empty set [GH-90000]")
        void shouldDefaultCapabilitiesToEmptySet() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent [GH-90000]")
                    .catalogId("catalog [GH-90000]")
                    .build(); // GH-90000

            assertThat(entry.getCapabilities()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should default tools to empty list [GH-90000]")
        void shouldDefaultToolsToEmptyList() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent [GH-90000]")
                    .catalogId("catalog [GH-90000]")
                    .build(); // GH-90000

            assertThat(entry.getTools()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should accept all fields [GH-90000]")
        void shouldAcceptAllFields() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("data-transformation-agent [GH-90000]")
                    .name("Data Transformation Agent [GH-90000]")
                    .version("2.0.0 [GH-90000]")
                    .description("Transforms structured data between formats. [GH-90000]")
                    .metadata(Map.of("level", "3", "domain", "data-processing")) // GH-90000
                    .capabilities(Set.of("data-transformation", "json-schema-validation")) // GH-90000
                    .tools(List.of("transformationEngine", "mappingResolver")) // GH-90000
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000

            assertThat(entry.getId()).isEqualTo("data-transformation-agent [GH-90000]");
            assertThat(entry.getName()).isEqualTo("Data Transformation Agent [GH-90000]");
            assertThat(entry.getVersion()).isEqualTo("2.0.0 [GH-90000]");
            assertThat(entry.getDescription()).contains("Transforms structured data [GH-90000]");
            assertThat(entry.getCapabilities()).containsExactlyInAnyOrder( // GH-90000
                    "data-transformation", "json-schema-validation");
            assertThat(entry.getTools()).containsExactly("transformationEngine", "mappingResolver"); // GH-90000
            assertThat(entry.getCatalogId()).isEqualTo("platform [GH-90000]");
        }

        @Test
        @DisplayName("toBuilder should produce equal copy [GH-90000]")
        void toBuilderShouldProduceCopy() { // GH-90000
            CatalogAgentEntry original = CatalogAgentEntry.builder() // GH-90000
                    .id("original [GH-90000]")
                    .name("Original Agent [GH-90000]")
                    .version("1.0.0 [GH-90000]")
                    .catalogId("catalog [GH-90000]")
                    .build(); // GH-90000

            CatalogAgentEntry copy = original.toBuilder().name("Updated Agent [GH-90000]").build();

            assertThat(copy.getId()).isEqualTo("original [GH-90000]");
            assertThat(copy.getName()).isEqualTo("Updated Agent [GH-90000]");
            assertThat(copy.getVersion()).isEqualTo("1.0.0 [GH-90000]");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Convenience accessors
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getLevel() [GH-90000]")
    class GetLevelTests {

        @Test
        @DisplayName("should return level from metadata as string [GH-90000]")
        void shouldReturnLevelFromMetadata() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent [GH-90000]")
                    .catalogId("catalog [GH-90000]")
                    .metadata(Map.of("level", 3)) // GH-90000
                    .build(); // GH-90000

            assertThat(entry.getLevel()).isEqualTo("3 [GH-90000]");
        }

        @Test
        @DisplayName("should return 'worker' when metadata has no level [GH-90000]")
        void shouldDefaultToWorker() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent [GH-90000]")
                    .catalogId("catalog [GH-90000]")
                    .build(); // GH-90000

            assertThat(entry.getLevel()).isEqualTo("worker [GH-90000]");
        }

        @Test
        @DisplayName("should handle string level in metadata [GH-90000]")
        void shouldHandleStringLevel() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent [GH-90000]")
                    .catalogId("catalog [GH-90000]")
                    .metadata(Map.of("level", "strategic")) // GH-90000
                    .build(); // GH-90000

            assertThat(entry.getLevel()).isEqualTo("strategic [GH-90000]");
        }
    }

    @Nested
    @DisplayName("getDomain() [GH-90000]")
    class GetDomainTests {

        @Test
        @DisplayName("should return domain from metadata [GH-90000]")
        void shouldReturnDomainFromMetadata() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent [GH-90000]")
                    .catalogId("catalog [GH-90000]")
                    .metadata(Map.of("domain", "data-processing")) // GH-90000
                    .build(); // GH-90000

            assertThat(entry.getDomain()).isEqualTo("data-processing [GH-90000]");
        }

        @Test
        @DisplayName("should default to 'general' when no domain in metadata [GH-90000]")
        void shouldDefaultToGeneral() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent [GH-90000]")
                    .catalogId("catalog [GH-90000]")
                    .build(); // GH-90000

            assertThat(entry.getDomain()).isEqualTo("general [GH-90000]");
        }

        @Test
        @DisplayName("should handle metadata with neither level nor domain [GH-90000]")
        void shouldHandleEmptyMetadata() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent [GH-90000]")
                    .catalogId("catalog [GH-90000]")
                    .metadata(Map.of("tags", List.of("monitoring [GH-90000]")))
                    .build(); // GH-90000

            assertThat(entry.getLevel()).isEqualTo("worker [GH-90000]");
            assertThat(entry.getDomain()).isEqualTo("general [GH-90000]");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Value equality
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Value equality (@Value) [GH-90000]")
    class EqualityTests {

        @Test
        @DisplayName("equal entries with same fields should be equal [GH-90000]")
        void shouldBeEqual() { // GH-90000
            CatalogAgentEntry a = CatalogAgentEntry.builder() // GH-90000
                    .id("my-agent [GH-90000]")
                    .name("My Agent [GH-90000]")
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000

            CatalogAgentEntry b = CatalogAgentEntry.builder() // GH-90000
                    .id("my-agent [GH-90000]")
                    .name("My Agent [GH-90000]")
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000

            assertThat(a).isEqualTo(b); // GH-90000
            assertThat(a.hashCode()).isEqualTo(b.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("entries with different ids should not be equal [GH-90000]")
        void shouldNotBeEqualWithDifferentId() { // GH-90000
            CatalogAgentEntry a = CatalogAgentEntry.builder().id("agent-a [GH-90000]").catalogId("c [GH-90000]").build();
            CatalogAgentEntry b = CatalogAgentEntry.builder().id("agent-b [GH-90000]").catalogId("c [GH-90000]").build();

            assertThat(a).isNotEqualTo(b); // GH-90000
        }

        @Test
        @DisplayName("toString should contain id and catalogId [GH-90000]")
        void toStringShouldContainKeyFields() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("event-transformer [GH-90000]")
                    .catalogId("platform [GH-90000]")
                    .build(); // GH-90000

            assertThat(entry.toString()).contains("event-transformer [GH-90000]");
        }
    }
}

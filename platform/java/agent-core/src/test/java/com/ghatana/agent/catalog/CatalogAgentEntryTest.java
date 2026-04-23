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
@DisplayName("CatalogAgentEntry")
class CatalogAgentEntryTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Builder defaults
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should require at minimum an id and catalogId")
        void shouldBuildWithMinimalFields() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("my-agent")
                    .catalogId("platform")
                    .build(); // GH-90000

            assertThat(entry.getId()).isEqualTo("my-agent");
            assertThat(entry.getCatalogId()).isEqualTo("platform");
        }

        @Test
        @DisplayName("should default version to 1.0.0")
        void shouldDefaultVersion() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent")
                    .catalogId("catalog")
                    .build(); // GH-90000

            assertThat(entry.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("should default metadata to empty map")
        void shouldDefaultMetadataToEmptyMap() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent")
                    .catalogId("catalog")
                    .build(); // GH-90000

            assertThat(entry.getMetadata()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should default capabilities to empty set")
        void shouldDefaultCapabilitiesToEmptySet() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent")
                    .catalogId("catalog")
                    .build(); // GH-90000

            assertThat(entry.getCapabilities()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should default tools to empty list")
        void shouldDefaultToolsToEmptyList() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent")
                    .catalogId("catalog")
                    .build(); // GH-90000

            assertThat(entry.getTools()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should accept all fields")
        void shouldAcceptAllFields() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("data-transformation-agent")
                    .name("Data Transformation Agent")
                    .version("2.0.0")
                    .description("Transforms structured data between formats.")
                    .metadata(Map.of("level", "3", "domain", "data-processing")) // GH-90000
                    .capabilities(Set.of("data-transformation", "json-schema-validation")) // GH-90000
                    .tools(List.of("transformationEngine", "mappingResolver")) // GH-90000
                    .catalogId("platform")
                    .build(); // GH-90000

            assertThat(entry.getId()).isEqualTo("data-transformation-agent");
            assertThat(entry.getName()).isEqualTo("Data Transformation Agent");
            assertThat(entry.getVersion()).isEqualTo("2.0.0");
            assertThat(entry.getDescription()).contains("Transforms structured data");
            assertThat(entry.getCapabilities()).containsExactlyInAnyOrder( // GH-90000
                    "data-transformation", "json-schema-validation");
            assertThat(entry.getTools()).containsExactly("transformationEngine", "mappingResolver"); // GH-90000
            assertThat(entry.getCatalogId()).isEqualTo("platform");
        }

        @Test
        @DisplayName("toBuilder should produce equal copy")
        void toBuilderShouldProduceCopy() { // GH-90000
            CatalogAgentEntry original = CatalogAgentEntry.builder() // GH-90000
                    .id("original")
                    .name("Original Agent")
                    .version("1.0.0")
                    .catalogId("catalog")
                    .build(); // GH-90000

            CatalogAgentEntry copy = original.toBuilder().name("Updated Agent").build();

            assertThat(copy.getId()).isEqualTo("original");
            assertThat(copy.getName()).isEqualTo("Updated Agent");
            assertThat(copy.getVersion()).isEqualTo("1.0.0");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Convenience accessors
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getLevel()")
    class GetLevelTests {

        @Test
        @DisplayName("should return level from metadata as string")
        void shouldReturnLevelFromMetadata() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent")
                    .catalogId("catalog")
                    .metadata(Map.of("level", 3)) // GH-90000
                    .build(); // GH-90000

            assertThat(entry.getLevel()).isEqualTo("3");
        }

        @Test
        @DisplayName("should return 'worker' when metadata has no level")
        void shouldDefaultToWorker() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent")
                    .catalogId("catalog")
                    .build(); // GH-90000

            assertThat(entry.getLevel()).isEqualTo("worker");
        }

        @Test
        @DisplayName("should handle string level in metadata")
        void shouldHandleStringLevel() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent")
                    .catalogId("catalog")
                    .metadata(Map.of("level", "strategic")) // GH-90000
                    .build(); // GH-90000

            assertThat(entry.getLevel()).isEqualTo("strategic");
        }
    }

    @Nested
    @DisplayName("getDomain()")
    class GetDomainTests {

        @Test
        @DisplayName("should return domain from metadata")
        void shouldReturnDomainFromMetadata() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent")
                    .catalogId("catalog")
                    .metadata(Map.of("domain", "data-processing")) // GH-90000
                    .build(); // GH-90000

            assertThat(entry.getDomain()).isEqualTo("data-processing");
        }

        @Test
        @DisplayName("should default to 'general' when no domain in metadata")
        void shouldDefaultToGeneral() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent")
                    .catalogId("catalog")
                    .build(); // GH-90000

            assertThat(entry.getDomain()).isEqualTo("general");
        }

        @Test
        @DisplayName("should handle metadata with neither level nor domain")
        void shouldHandleEmptyMetadata() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("agent")
                    .catalogId("catalog")
                    .metadata(Map.of("tags", List.of("monitoring")))
                    .build(); // GH-90000

            assertThat(entry.getLevel()).isEqualTo("worker");
            assertThat(entry.getDomain()).isEqualTo("general");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Value equality
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Value equality (@Value)")
    class EqualityTests {

        @Test
        @DisplayName("equal entries with same fields should be equal")
        void shouldBeEqual() { // GH-90000
            CatalogAgentEntry a = CatalogAgentEntry.builder() // GH-90000
                    .id("my-agent")
                    .name("My Agent")
                    .catalogId("platform")
                    .build(); // GH-90000

            CatalogAgentEntry b = CatalogAgentEntry.builder() // GH-90000
                    .id("my-agent")
                    .name("My Agent")
                    .catalogId("platform")
                    .build(); // GH-90000

            assertThat(a).isEqualTo(b); // GH-90000
            assertThat(a.hashCode()).isEqualTo(b.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("entries with different ids should not be equal")
        void shouldNotBeEqualWithDifferentId() { // GH-90000
            CatalogAgentEntry a = CatalogAgentEntry.builder().id("agent-a").catalogId("c").build();
            CatalogAgentEntry b = CatalogAgentEntry.builder().id("agent-b").catalogId("c").build();

            assertThat(a).isNotEqualTo(b); // GH-90000
        }

        @Test
        @DisplayName("toString should contain id and catalogId")
        void toStringShouldContainKeyFields() { // GH-90000
            CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                    .id("event-transformer")
                    .catalogId("platform")
                    .build(); // GH-90000

            assertThat(entry.toString()).contains("event-transformer");
        }
    }
}

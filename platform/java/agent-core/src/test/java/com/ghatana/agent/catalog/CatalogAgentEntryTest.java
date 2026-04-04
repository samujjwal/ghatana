/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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
        void shouldBuildWithMinimalFields() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("my-agent")
                    .catalogId("platform")
                    .build();

            assertThat(entry.getId()).isEqualTo("my-agent");
            assertThat(entry.getCatalogId()).isEqualTo("platform");
        }

        @Test
        @DisplayName("should default version to 1.0.0")
        void shouldDefaultVersion() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent")
                    .catalogId("catalog")
                    .build();

            assertThat(entry.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("should default metadata to empty map")
        void shouldDefaultMetadataToEmptyMap() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent")
                    .catalogId("catalog")
                    .build();

            assertThat(entry.getMetadata()).isEmpty();
        }

        @Test
        @DisplayName("should default capabilities to empty set")
        void shouldDefaultCapabilitiesToEmptySet() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent")
                    .catalogId("catalog")
                    .build();

            assertThat(entry.getCapabilities()).isEmpty();
        }

        @Test
        @DisplayName("should default tools to empty list")
        void shouldDefaultToolsToEmptyList() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent")
                    .catalogId("catalog")
                    .build();

            assertThat(entry.getTools()).isEmpty();
        }

        @Test
        @DisplayName("should accept all fields")
        void shouldAcceptAllFields() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("data-transformation-agent")
                    .name("Data Transformation Agent")
                    .version("2.0.0")
                    .description("Transforms structured data between formats.")
                    .metadata(Map.of("level", "3", "domain", "data-processing"))
                    .capabilities(Set.of("data-transformation", "json-schema-validation"))
                    .tools(List.of("transformationEngine", "mappingResolver"))
                    .catalogId("platform")
                    .build();

            assertThat(entry.getId()).isEqualTo("data-transformation-agent");
            assertThat(entry.getName()).isEqualTo("Data Transformation Agent");
            assertThat(entry.getVersion()).isEqualTo("2.0.0");
            assertThat(entry.getDescription()).contains("Transforms structured data");
            assertThat(entry.getCapabilities()).containsExactlyInAnyOrder(
                    "data-transformation", "json-schema-validation");
            assertThat(entry.getTools()).containsExactly("transformationEngine", "mappingResolver");
            assertThat(entry.getCatalogId()).isEqualTo("platform");
        }

        @Test
        @DisplayName("toBuilder should produce equal copy")
        void toBuilderShouldProduceCopy() {
            CatalogAgentEntry original = CatalogAgentEntry.builder()
                    .id("original")
                    .name("Original Agent")
                    .version("1.0.0")
                    .catalogId("catalog")
                    .build();

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
        void shouldReturnLevelFromMetadata() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent")
                    .catalogId("catalog")
                    .metadata(Map.of("level", 3))
                    .build();

            assertThat(entry.getLevel()).isEqualTo("3");
        }

        @Test
        @DisplayName("should return 'worker' when metadata has no level")
        void shouldDefaultToWorker() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent")
                    .catalogId("catalog")
                    .build();

            assertThat(entry.getLevel()).isEqualTo("worker");
        }

        @Test
        @DisplayName("should handle string level in metadata")
        void shouldHandleStringLevel() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent")
                    .catalogId("catalog")
                    .metadata(Map.of("level", "strategic"))
                    .build();

            assertThat(entry.getLevel()).isEqualTo("strategic");
        }
    }

    @Nested
    @DisplayName("getDomain()")
    class GetDomainTests {

        @Test
        @DisplayName("should return domain from metadata")
        void shouldReturnDomainFromMetadata() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent")
                    .catalogId("catalog")
                    .metadata(Map.of("domain", "data-processing"))
                    .build();

            assertThat(entry.getDomain()).isEqualTo("data-processing");
        }

        @Test
        @DisplayName("should default to 'general' when no domain in metadata")
        void shouldDefaultToGeneral() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent")
                    .catalogId("catalog")
                    .build();

            assertThat(entry.getDomain()).isEqualTo("general");
        }

        @Test
        @DisplayName("should handle metadata with neither level nor domain")
        void shouldHandleEmptyMetadata() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent")
                    .catalogId("catalog")
                    .metadata(Map.of("tags", List.of("monitoring")))
                    .build();

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
        void shouldBeEqual() {
            CatalogAgentEntry a = CatalogAgentEntry.builder()
                    .id("my-agent")
                    .name("My Agent")
                    .catalogId("platform")
                    .build();

            CatalogAgentEntry b = CatalogAgentEntry.builder()
                    .id("my-agent")
                    .name("My Agent")
                    .catalogId("platform")
                    .build();

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("entries with different ids should not be equal")
        void shouldNotBeEqualWithDifferentId() {
            CatalogAgentEntry a = CatalogAgentEntry.builder().id("agent-a").catalogId("c").build();
            CatalogAgentEntry b = CatalogAgentEntry.builder().id("agent-b").catalogId("c").build();

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("toString should contain id and catalogId")
        void toStringShouldContainKeyFields() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("event-transformer")
                    .catalogId("platform")
                    .build();

            assertThat(entry.toString()).contains("event-transformer");
        }
    }
}

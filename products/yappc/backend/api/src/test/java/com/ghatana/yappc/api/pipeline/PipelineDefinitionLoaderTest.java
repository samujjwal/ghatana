/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module — Pipeline Package Tests
 */
package com.ghatana.yappc.api.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PipelineDefinitionLoader}.
 *
 * <p>Covers null-safety, graceful empty-directory handling, and YAML parsing via
 * the package-private constructor that accepts an explicit {@link Path} directory.
 *
 * @doc.type class
 * @doc.purpose Unit tests for PipelineDefinitionLoader — registry, parsing, graceful degradation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PipelineDefinitionLoader")
class PipelineDefinitionLoaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // =========================================================================
    // Null safety
    // =========================================================================

    @Nested
    @DisplayName("null safety")
    class NullSafety {

        @Test
        @DisplayName("null ObjectMapper throws NullPointerException")
        void nullMapperThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new PipelineDefinitionLoader(null))
                    .withMessageContaining("jsonMapper");
        }
    }

    // =========================================================================
    // Graceful empty state
    // =========================================================================

    @Nested
    @DisplayName("graceful empty state")
    class GracefulEmpty {

        @Test
        @DisplayName("get() returns null for unknown pipeline name")
        void getUnknownReturnsNull() {
            PipelineDefinitionLoader loader = new PipelineDefinitionLoader(MAPPER);
            assertThat(loader.get("does-not-exist")).isNull();
        }

        @Test
        @DisplayName("all() returns unmodifiable map")
        void allReturnsUnmodifiableMap() {
            PipelineDefinitionLoader loader = new PipelineDefinitionLoader(MAPPER);
            assertThatThrownBy(() -> loader.all().put("x", null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("size() returns a non-negative count")
        void sizeIsNonNegative() {
            PipelineDefinitionLoader loader = new PipelineDefinitionLoader(MAPPER);
            assertThat(loader.size()).isGreaterThanOrEqualTo(0);
        }
    }

    // =========================================================================
    // YAML parsing via temp directory
    // =========================================================================

    @Nested
    @DisplayName("YAML parsing")
    class YamlParsing {

        @Test
        @DisplayName("parses single well-formed YAML pipeline correctly")
        void parsesSingleYaml(@TempDir Path dir) throws IOException {
            String yaml = """
                    apiVersion: ghatana.aep/v1
                    kind: Pipeline
                    metadata:
                      name: "test-pipeline"
                      version: "1.2.3"
                      description: "Integration test pipeline"
                      tags: ["test", "integration"]
                    spec:
                      inputs:
                        - name: "in-topic"
                          topic: "events.input"
                      outputs:
                        - name: "out-topic"
                          topic: "events.output"
                      operators:
                        - name: "filter-op"
                          type: "filter"
                    """;
            Files.writeString(dir.resolve("test-pipeline.yaml"), yaml);

            PipelineDefinitionLoader loader = new PipelineDefinitionLoader(MAPPER, dir);

            assertThat(loader.size()).isEqualTo(1);
            PipelineDefinition def = loader.get("test-pipeline");
            assertThat(def).isNotNull();
            assertThat(def.name()).isEqualTo("test-pipeline");
            assertThat(def.version()).isEqualTo("1.2.3");
            assertThat(def.description()).isEqualTo("Integration test pipeline");
            assertThat(def.tags()).containsExactlyInAnyOrder("test", "integration");
            assertThat(def.inputs()).hasSize(1);
            assertThat(def.outputs()).hasSize(1);
            assertThat(def.operators()).hasSize(1);
        }

        @Test
        @DisplayName("skips YAML missing metadata.name without aborting")
        void skipsMalformedYaml(@TempDir Path dir) throws IOException {
            String badYaml = """
                    apiVersion: ghatana.aep/v1
                    kind: Pipeline
                    metadata:
                      description: "Pipeline without a name field"
                    spec:
                      operators: []
                    """;
            Files.writeString(dir.resolve("bad-pipeline.yaml"), badYaml);

            PipelineDefinitionLoader loader = new PipelineDefinitionLoader(MAPPER, dir);
            // Bad YAML is skipped — size is 0 and no exception thrown
            assertThat(loader.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("loads two pipelines from same directory")
        void loadsTwoPipelines(@TempDir Path dir) throws IOException {
            String yaml1 = minimalYaml("pipeline-alpha");
            String yaml2 = minimalYaml("pipeline-beta");
            Files.writeString(dir.resolve("alpha.yaml"), yaml1);
            Files.writeString(dir.resolve("beta.yaml"), yaml2);

            PipelineDefinitionLoader loader = new PipelineDefinitionLoader(MAPPER, dir);

            assertThat(loader.size()).isEqualTo(2);
            assertThat(loader.get("pipeline-alpha")).isNotNull();
            assertThat(loader.get("pipeline-beta")).isNotNull();
        }

        @Test
        @DisplayName("get returns null for name not in loaded set")
        void getMissingReturnsNull(@TempDir Path dir) throws IOException {
            Files.writeString(dir.resolve("known.yaml"), minimalYaml("known-pipeline"));
            PipelineDefinitionLoader loader = new PipelineDefinitionLoader(MAPPER, dir);

            assertThat(loader.get("unknown-pipeline")).isNull();
            assertThat(loader.get("known-pipeline")).isNotNull();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String minimalYaml(String name) {
        return String.format("""
                apiVersion: ghatana.aep/v1
                kind: Pipeline
                metadata:
                  name: "%s"
                  version: "1.0.0"
                spec:
                  operators: []
                """, name);
    }

}

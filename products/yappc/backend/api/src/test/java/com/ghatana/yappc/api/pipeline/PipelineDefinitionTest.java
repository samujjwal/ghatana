/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module — Pipeline Package Tests
 */
package com.ghatana.yappc.api.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PipelineDefinition}.
 *
 * <p>Covers construction, field defaults, defensive immutability, and basic getters.
 *
 * @doc.type class
 * @doc.purpose Unit tests for PipelineDefinition value object
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PipelineDefinition")
class PipelineDefinitionTest {

    // =========================================================================
    // Construction — required field
    // =========================================================================

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("null name throws NullPointerException")
        void nullNameThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new PipelineDefinition(
                            null, "1.0.0", "desc", List.of(), List.of(), List.of(), List.of()))
                    .withMessageContaining("name");
        }

        @Test
        @DisplayName("valid name is stored as-is")
        void validNameStored() {
            PipelineDefinition def = new PipelineDefinition(
                    "my-pipeline", null, null, null, null, null, null);
            assertThat(def.name()).isEqualTo("my-pipeline");
        }
    }

    // =========================================================================
    // Defaults for optional fields
    // =========================================================================

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("null version defaults to 1.0.0")
        void nullVersionDefaultsTo100() {
            PipelineDefinition def = new PipelineDefinition(
                    "p", null, null, null, null, null, null);
            assertThat(def.version()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("null description defaults to empty string")
        void nullDescriptionDefaultsToEmpty() {
            PipelineDefinition def = new PipelineDefinition(
                    "p", "1.0.0", null, null, null, null, null);
            assertThat(def.description()).isEqualTo("");
        }

        @Test
        @DisplayName("null tags default to empty list")
        void nullTagsDefaultToEmpty() {
            PipelineDefinition def = new PipelineDefinition(
                    "p", "1.0.0", "desc", null, null, null, null);
            assertThat(def.tags()).isEmpty();
        }

        @Test
        @DisplayName("null inputs default to empty list")
        void nullInputsDefaultToEmpty() {
            PipelineDefinition def = new PipelineDefinition(
                    "p", "1.0.0", "desc", List.of(), null, null, null);
            assertThat(def.inputs()).isEmpty();
        }

        @Test
        @DisplayName("null outputs default to empty list")
        void nullOutputsDefaultToEmpty() {
            PipelineDefinition def = new PipelineDefinition(
                    "p", "1.0.0", "desc", List.of(), List.of(), null, null);
            assertThat(def.outputs()).isEmpty();
        }

        @Test
        @DisplayName("null operators default to empty list")
        void nullOperatorsDefaultToEmpty() {
            PipelineDefinition def = new PipelineDefinition(
                    "p", "1.0.0", "desc", List.of(), List.of(), List.of(), null);
            assertThat(def.operators()).isEmpty();
        }
    }

    // =========================================================================
    // Getters — populated values
    // =========================================================================

    @Nested
    @DisplayName("getters")
    class Getters {

        @Test
        @DisplayName("all fields are stored and returned correctly")
        void allFieldsStoredAndReturned() {
            List<String> tags = List.of("agents", "orchestration");
            List<Map<String, Object>> inputs = List.of(Map.of("name", "input-topic"));
            List<Map<String, Object>> outputs = List.of(Map.of("name", "output-topic"));
            List<Map<String, Object>> operators = List.of(Map.of("type", "filter"));

            PipelineDefinition def = new PipelineDefinition(
                    "agent-pipeline", "2.1.0", "Agent orchestration pipeline",
                    tags, inputs, outputs, operators);

            assertThat(def.name()).isEqualTo("agent-pipeline");
            assertThat(def.version()).isEqualTo("2.1.0");
            assertThat(def.description()).isEqualTo("Agent orchestration pipeline");
            assertThat(def.tags()).containsExactlyInAnyOrder("agents", "orchestration");
            assertThat(def.inputs()).hasSize(1);
            assertThat(def.outputs()).hasSize(1);
            assertThat(def.operators()).hasSize(1);
        }
    }

    // =========================================================================
    // Immutability
    // =========================================================================

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("tags list is unmodifiable")
        void tagsListIsUnmodifiable() {
            PipelineDefinition def = new PipelineDefinition(
                    "p", "1.0.0", "", List.of("tag1"), null, null, null);
            assertThatThrownBy(() -> def.tags().add("extra"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("operators list is unmodifiable")
        void operatorsListIsUnmodifiable() {
            PipelineDefinition def = new PipelineDefinition(
                    "p", "1.0.0", "", null, null, null,
                    List.of(Map.of("type", "transform")));
            assertThatThrownBy(() -> def.operators().add(Map.of("type", "extra")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}

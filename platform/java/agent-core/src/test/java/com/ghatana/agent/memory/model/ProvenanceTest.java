/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Provenance} — origin tracking for memory items.
 *
 * @doc.type class
 * @doc.purpose Unit tests for Provenance value object
 * @doc.layer agent-memory
 * @doc.pattern Test
 */
@DisplayName("Provenance - origin and chain-of-custody tracking")
class ProvenanceTest {

    @Nested
    @DisplayName("Builder defaults")
    class BuilderDefaultsTests {

        @Test
        @DisplayName("Default source is 'unknown'")
        void defaultSource_isUnknown() { // GH-90000
            assertThat(Provenance.builder().build().getSource()).isEqualTo("unknown");
        }

        @Test
        @DisplayName("Default confidenceSource is LLM_INFERENCE")
        void defaultConfidenceSource_isLlmInference() { // GH-90000
            assertThat(Provenance.builder().build().getConfidenceSource()) // GH-90000
                    .isEqualTo(Provenance.ConfidenceSource.LLM_INFERENCE); // GH-90000
        }

        @Test
        @DisplayName("Default agentId is empty string")
        void defaultAgentId_isEmptyString() { // GH-90000
            assertThat(Provenance.builder().build().getAgentId()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Default traceId is null")
        void defaultTraceId_isNull() { // GH-90000
            assertThat(Provenance.builder().build().getTraceId()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("Default sessionId is null")
        void defaultSessionId_isNull() { // GH-90000
            assertThat(Provenance.builder().build().getSessionId()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("Default parentItemId is null")
        void defaultParentItemId_isNull() { // GH-90000
            assertThat(Provenance.builder().build().getParentItemId()).isNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Custom builder values")
    class CustomBuilderTests {

        @Test
        @DisplayName("Custom source is stored correctly")
        void customSource_storedCorrectly() { // GH-90000
            Provenance p = Provenance.builder().source("tool:grep").build();
            assertThat(p.getSource()).isEqualTo("tool:grep");
        }

        @Test
        @DisplayName("HUMAN confidence source can be set")
        void humanConfidenceSource_settable() { // GH-90000
            Provenance p = Provenance.builder() // GH-90000
                    .confidenceSource(Provenance.ConfidenceSource.HUMAN).build(); // GH-90000
            assertThat(p.getConfidenceSource()).isEqualTo(Provenance.ConfidenceSource.HUMAN); // GH-90000
        }

        @Test
        @DisplayName("TraceId is stored when non-null")
        void traceId_storedWhenSet() { // GH-90000
            Provenance p = Provenance.builder().traceId("trace-abc-123").build();
            assertThat(p.getTraceId()).isEqualTo("trace-abc-123");
        }

        @Test
        @DisplayName("ParentItemId is stored for derived items")
        void parentItemId_storedForDerivedItems() { // GH-90000
            Provenance p = Provenance.builder().parentItemId("parent-001").build();
            assertThat(p.getParentItemId()).isEqualTo("parent-001");
        }
    }

    @Nested
    @DisplayName("ConfidenceSource enum values")
    class ConfidenceSourceEnumTests {

        @Test
        @DisplayName("All five ConfidenceSource values are present")
        void allConfidenceSourceValues_present() { // GH-90000
            assertThat(Provenance.ConfidenceSource.values()).containsExactlyInAnyOrder( // GH-90000
                    Provenance.ConfidenceSource.HUMAN,
                    Provenance.ConfidenceSource.LLM_INFERENCE,
                    Provenance.ConfidenceSource.TOOL_OUTPUT,
                    Provenance.ConfidenceSource.CONSOLIDATION,
                    Provenance.ConfidenceSource.STATISTICAL);
        }
    }
}

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
@DisplayName("Provenance - origin and chain-of-custody tracking [GH-90000]")
class ProvenanceTest {

    @Nested
    @DisplayName("Builder defaults [GH-90000]")
    class BuilderDefaultsTests {

        @Test
        @DisplayName("Default source is 'unknown' [GH-90000]")
        void defaultSource_isUnknown() { // GH-90000
            assertThat(Provenance.builder().build().getSource()).isEqualTo("unknown [GH-90000]");
        }

        @Test
        @DisplayName("Default confidenceSource is LLM_INFERENCE [GH-90000]")
        void defaultConfidenceSource_isLlmInference() { // GH-90000
            assertThat(Provenance.builder().build().getConfidenceSource()) // GH-90000
                    .isEqualTo(Provenance.ConfidenceSource.LLM_INFERENCE); // GH-90000
        }

        @Test
        @DisplayName("Default agentId is empty string [GH-90000]")
        void defaultAgentId_isEmptyString() { // GH-90000
            assertThat(Provenance.builder().build().getAgentId()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Default traceId is null [GH-90000]")
        void defaultTraceId_isNull() { // GH-90000
            assertThat(Provenance.builder().build().getTraceId()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("Default sessionId is null [GH-90000]")
        void defaultSessionId_isNull() { // GH-90000
            assertThat(Provenance.builder().build().getSessionId()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("Default parentItemId is null [GH-90000]")
        void defaultParentItemId_isNull() { // GH-90000
            assertThat(Provenance.builder().build().getParentItemId()).isNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Custom builder values [GH-90000]")
    class CustomBuilderTests {

        @Test
        @DisplayName("Custom source is stored correctly [GH-90000]")
        void customSource_storedCorrectly() { // GH-90000
            Provenance p = Provenance.builder().source("tool:grep [GH-90000]").build();
            assertThat(p.getSource()).isEqualTo("tool:grep [GH-90000]");
        }

        @Test
        @DisplayName("HUMAN confidence source can be set [GH-90000]")
        void humanConfidenceSource_settable() { // GH-90000
            Provenance p = Provenance.builder() // GH-90000
                    .confidenceSource(Provenance.ConfidenceSource.HUMAN).build(); // GH-90000
            assertThat(p.getConfidenceSource()).isEqualTo(Provenance.ConfidenceSource.HUMAN); // GH-90000
        }

        @Test
        @DisplayName("TraceId is stored when non-null [GH-90000]")
        void traceId_storedWhenSet() { // GH-90000
            Provenance p = Provenance.builder().traceId("trace-abc-123 [GH-90000]").build();
            assertThat(p.getTraceId()).isEqualTo("trace-abc-123 [GH-90000]");
        }

        @Test
        @DisplayName("ParentItemId is stored for derived items [GH-90000]")
        void parentItemId_storedForDerivedItems() { // GH-90000
            Provenance p = Provenance.builder().parentItemId("parent-001 [GH-90000]").build();
            assertThat(p.getParentItemId()).isEqualTo("parent-001 [GH-90000]");
        }
    }

    @Nested
    @DisplayName("ConfidenceSource enum values [GH-90000]")
    class ConfidenceSourceEnumTests {

        @Test
        @DisplayName("All five ConfidenceSource values are present [GH-90000]")
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

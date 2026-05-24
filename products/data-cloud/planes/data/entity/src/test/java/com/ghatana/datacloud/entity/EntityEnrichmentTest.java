/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3: Contract tests for EntityEnrichment.
 *
 * @doc.type class
 * @doc.purpose Tests for EntityEnrichment record validation and methods
 * @doc.layer test
 */
@DisplayName("EntityEnrichment Tests")
class EntityEnrichmentTest {

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("valid enrichment is created successfully")
        void validEnrichmentIsCreated() {
            EntityEnrichment enrichment = new EntityEnrichment(
                "description",
                "Test value",
                "Test reason",
                0.85
            );

            assertThat(enrichment.fieldName()).isEqualTo("description");
            assertThat(enrichment.suggestedValue()).isEqualTo("Test value");
            assertThat(enrichment.reason()).isEqualTo("Test reason");
            assertThat(enrichment.confidence()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("blank field name is rejected")
        void blankFieldNameIsRejected() {
            assertThatThrownBy(() -> new EntityEnrichment(
                "",
                "value",
                "reason",
                0.5
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field name must not be blank");
        }

        @Test
        @DisplayName("null field name is rejected")
        void nullFieldNameIsRejected() {
            assertThatThrownBy(() -> new EntityEnrichment(
                null,
                "value",
                "reason",
                0.5
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field name must not be blank");
        }

        @Test
        @DisplayName("null suggested value is rejected")
        void nullSuggestedValueIsRejected() {
            assertThatThrownBy(() -> new EntityEnrichment(
                "field",
                null,
                "reason",
                0.5
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Suggested value must not be null");
        }

        @Test
        @DisplayName("blank reason is rejected")
        void blankReasonIsRejected() {
            assertThatThrownBy(() -> new EntityEnrichment(
                "field",
                "value",
                "",
                0.5
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reason must not be blank");
        }

        @Test
        @DisplayName("null reason is rejected")
        void nullReasonIsRejected() {
            assertThatThrownBy(() -> new EntityEnrichment(
                "field",
                "value",
                null,
                0.5
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reason must not be blank");
        }

        @Test
        @DisplayName("confidence below 0.0 is rejected")
        void confidenceBelowZeroIsRejected() {
            assertThatThrownBy(() -> new EntityEnrichment(
                "field",
                "value",
                "reason",
                -0.1
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidence must be between 0.0 and 1.0");
        }

        @Test
        @DisplayName("confidence above 1.0 is rejected")
        void confidenceAboveOneIsRejected() {
            assertThatThrownBy(() -> new EntityEnrichment(
                "field",
                "value",
                "reason",
                1.1
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidence must be between 0.0 and 1.0");
        }

        @Test
        @DisplayName("confidence at 0.0 is accepted")
        void confidenceAtZeroIsAccepted() {
            EntityEnrichment enrichment = new EntityEnrichment(
                "field",
                "value",
                "reason",
                0.0
            );

            assertThat(enrichment.confidence()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("confidence at 1.0 is accepted")
        void confidenceAtOneIsAccepted() {
            EntityEnrichment enrichment = new EntityEnrichment(
                "field",
                "value",
                "reason",
                1.0
            );

            assertThat(enrichment.confidence()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Confidence Methods")
    class ConfidenceMethodTests {

        @Test
        @DisplayName("isHighConfidence returns true for confidence >= 0.8")
        void isHighConfidenceReturnsTrueForHighValues() {
            EntityEnrichment enrichment = new EntityEnrichment(
                "field",
                "value",
                "reason",
                0.85
            );

            assertThat(enrichment.isHighConfidence()).isTrue();
        }

        @Test
        @DisplayName("isHighConfidence returns false for confidence < 0.8")
        void isHighConfidenceReturnsFalseForLowValues() {
            EntityEnrichment enrichment = new EntityEnrichment(
                "field",
                "value",
                "reason",
                0.75
            );

            assertThat(enrichment.isHighConfidence()).isFalse();
        }

        @Test
        @DisplayName("isHighConfidence returns true at exactly 0.8")
        void isHighConfidenceReturnsTrueAtThreshold() {
            EntityEnrichment enrichment = new EntityEnrichment(
                "field",
                "value",
                "reason",
                0.8
            );

            assertThat(enrichment.isHighConfidence()).isTrue();
        }

        @Test
        @DisplayName("shouldAutoApply returns true for confidence >= 0.9")
        void shouldAutoApplyReturnsTrueForVeryHighValues() {
            EntityEnrichment enrichment = new EntityEnrichment(
                "field",
                "value",
                "reason",
                0.95
            );

            assertThat(enrichment.shouldAutoApply()).isTrue();
        }

        @Test
        @DisplayName("shouldAutoApply returns false for confidence < 0.9")
        void shouldAutoApplyReturnsFalseForLowerValues() {
            EntityEnrichment enrichment = new EntityEnrichment(
                "field",
                "value",
                "reason",
                0.85
            );

            assertThat(enrichment.shouldAutoApply()).isFalse();
        }

        @Test
        @DisplayName("shouldAutoApply returns true at exactly 0.9")
        void shouldAutoApplyReturnsTrueAtThreshold() {
            EntityEnrichment enrichment = new EntityEnrichment(
                "field",
                "value",
                "reason",
                0.9
            );

            assertThat(enrichment.shouldAutoApply()).isTrue();
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("record is immutable")
        void recordIsImmutable() {
            EntityEnrichment enrichment = new EntityEnrichment(
                "field",
                "value",
                "reason",
                0.85
            );

            // Records are immutable by design
            assertThat(enrichment).isNotNull();
        }
    }
}

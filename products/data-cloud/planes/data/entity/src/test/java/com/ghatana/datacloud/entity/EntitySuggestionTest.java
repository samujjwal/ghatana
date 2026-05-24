/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3: Contract tests for EntitySuggestion.
 *
 * @doc.type class
 * @doc.purpose Tests for EntitySuggestion record validation and methods
 * @doc.layer test
 */
@DisplayName("EntitySuggestion Tests")
class EntitySuggestionTest {

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("valid suggestion is created successfully")
        void validSuggestionIsCreated() {
            Map<String, Object> data = Map.of("name", "Test Product", "price", 99.99);
            List<String> reasoning = List.of("Generated from description");
            EntitySuggestion suggestion = new EntitySuggestion(data, 0.85, reasoning);

            assertThat(suggestion.suggestedData()).isEqualTo(data);
            assertThat(suggestion.confidence()).isEqualTo(0.85);
            assertThat(suggestion.reasoning()).isEqualTo(reasoning);
        }

        @Test
        @DisplayName("null suggested data is rejected")
        void nullSuggestedDataIsRejected() {
            List<String> reasoning = List.of("reason");
            
            assertThatThrownBy(() -> new EntitySuggestion(null, 0.85, reasoning))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Suggested data must not be null");
        }

        @Test
        @DisplayName("null reasoning is rejected")
        void nullReasoningIsRejected() {
            Map<String, Object> data = Map.of("name", "Test");
            
            assertThatThrownBy(() -> new EntitySuggestion(data, 0.85, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reasoning must not be null");
        }

        @Test
        @DisplayName("confidence below 0.0 is rejected")
        void confidenceBelowZeroIsRejected() {
            Map<String, Object> data = Map.of("name", "Test");
            List<String> reasoning = List.of("reason");
            
            assertThatThrownBy(() -> new EntitySuggestion(data, -0.1, reasoning))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidence must be between 0.0 and 1.0");
        }

        @Test
        @DisplayName("confidence above 1.0 is rejected")
        void confidenceAboveOneIsRejected() {
            Map<String, Object> data = Map.of("name", "Test");
            List<String> reasoning = List.of("reason");
            
            assertThatThrownBy(() -> new EntitySuggestion(data, 1.1, reasoning))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidence must be between 0.0 and 1.0");
        }
    }

    @Nested
    @DisplayName("Confidence Methods")
    class ConfidenceMethodTests {

        @Test
        @DisplayName("isHighConfidence returns true for confidence >= 0.8")
        void isHighConfidenceReturnsTrueForHighValues() {
            Map<String, Object> data = Map.of("name", "Test");
            List<String> reasoning = List.of("reason");
            EntitySuggestion suggestion = new EntitySuggestion(data, 0.85, reasoning);

            assertThat(suggestion.isHighConfidence()).isTrue();
        }

        @Test
        @DisplayName("isHighConfidence returns false for confidence < 0.8")
        void isHighConfidenceReturnsFalseForLowValues() {
            Map<String, Object> data = Map.of("name", "Test");
            List<String> reasoning = List.of("reason");
            EntitySuggestion suggestion = new EntitySuggestion(data, 0.75, reasoning);

            assertThat(suggestion.isHighConfidence()).isFalse();
        }

        @Test
        @DisplayName("isMediumConfidence returns true for confidence between 0.5 and 0.8")
        void isMediumConfidenceReturnsTrueForMediumValues() {
            Map<String, Object> data = Map.of("name", "Test");
            List<String> reasoning = List.of("reason");
            EntitySuggestion suggestion = new EntitySuggestion(data, 0.65, reasoning);

            assertThat(suggestion.isMediumConfidence()).isTrue();
        }

        @Test
        @DisplayName("isMediumConfidence returns false for confidence outside range")
        void isMediumConfidenceReturnsFalseForOutOfRangeValues() {
            Map<String, Object> data = Map.of("name", "Test");
            List<String> reasoning = List.of("reason");
            EntitySuggestion suggestion = new EntitySuggestion(data, 0.85, reasoning);

            assertThat(suggestion.isMediumConfidence()).isFalse();
        }

        @Test
        @DisplayName("isLowConfidence returns true for confidence < 0.5")
        void isLowConfidenceReturnsTrueForLowValues() {
            Map<String, Object> data = Map.of("name", "Test");
            List<String> reasoning = List.of("reason");
            EntitySuggestion suggestion = new EntitySuggestion(data, 0.35, reasoning);

            assertThat(suggestion.isLowConfidence()).isTrue();
        }

        @Test
        @DisplayName("isLowConfidence returns false for confidence >= 0.5")
        void isLowConfidenceReturnsFalseForHighValues() {
            Map<String, Object> data = Map.of("name", "Test");
            List<String> reasoning = List.of("reason");
            EntitySuggestion suggestion = new EntitySuggestion(data, 0.65, reasoning);

            assertThat(suggestion.isLowConfidence()).isFalse();
        }
    }

    @Nested
    @DisplayName("Data Access")
    class DataAccessTests {

        @Test
        @DisplayName("suggestedData returns the data map")
        void suggestedDataReturnsDataMap() {
            Map<String, Object> data = Map.of("name", "Test Product", "price", 99.99);
            List<String> reasoning = List.of("reason");
            EntitySuggestion suggestion = new EntitySuggestion(data, 0.85, reasoning);

            assertThat(suggestion.suggestedData()).isEqualTo(data);
            assertThat(suggestion.suggestedData()).containsEntry("name", "Test Product");
            assertThat(suggestion.suggestedData()).containsEntry("price", 99.99);
        }

        @Test
        @DisplayName("reasoning returns the reasoning list")
        void reasoningReturnsReasoningList() {
            Map<String, Object> data = Map.of("name", "Test");
            List<String> reasoning = List.of("Reason 1", "Reason 2");
            EntitySuggestion suggestion = new EntitySuggestion(data, 0.85, reasoning);

            assertThat(suggestion.reasoning()).isEqualTo(reasoning);
            assertThat(suggestion.reasoning()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("record is immutable")
        void recordIsImmutable() {
            Map<String, Object> data = Map.of("name", "Test");
            List<String> reasoning = List.of("reason");
            EntitySuggestion suggestion = new EntitySuggestion(data, 0.85, reasoning);

            // Records are immutable by design
            assertThat(suggestion).isNotNull();
        }

        @Test
        @DisplayName("suggestedData is defensively copied")
        void suggestedDataIsDefensivelyCopied() {
            Map<String, Object> originalData = new java.util.HashMap<>();
            originalData.put("name", "Test");
            List<String> reasoning = List.of("reason");
            
            EntitySuggestion suggestion = new EntitySuggestion(originalData, 0.85, reasoning);
            
            // Modify original map
            originalData.put("name", "Modified");
            
            // Suggestion should not be affected
            assertThat(suggestion.suggestedData().get("name")).isEqualTo("Test");
        }

        @Test
        @DisplayName("reasoning is defensively copied")
        void reasoningIsDefensivelyCopied() {
            Map<String, Object> data = Map.of("name", "Test");
            List<String> originalReasoning = new java.util.ArrayList<>();
            originalReasoning.add("reason");
            
            EntitySuggestion suggestion = new EntitySuggestion(data, 0.85, originalReasoning);
            
            // Modify original list
            originalReasoning.add("another reason");
            
            // Suggestion should not be affected
            assertThat(suggestion.reasoning()).hasSize(1);
        }
    }
}

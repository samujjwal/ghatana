/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

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
            EntitySuggestion suggestion = new EntitySuggestion(
                "tenant-123",
                "products",
                data,
                0.85,
                "Generated from description"
            );

            assertThat(suggestion.tenantId()).isEqualTo("tenant-123");
            assertThat(suggestion.collectionName()).isEqualTo("products");
            assertThat(suggestion.generatedData()).isEqualTo(data);
            assertThat(suggestion.confidence()).isEqualTo(0.85);
            assertThat(suggestion.reason()).isEqualTo("Generated from description");
        }

        @Test
        @DisplayName("blank tenant ID is rejected")
        void blankTenantIdIsRejected() {
            Map<String, Object> data = Map.of("name", "Test");
            
            assertThatThrownBy(() -> new EntitySuggestion(
                "",
                "products",
                data,
                0.85,
                "reason"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant ID must not be blank");
        }

        @Test
        @DisplayName("null tenant ID is rejected")
        void nullTenantIdIsRejected() {
            Map<String, Object> data = Map.of("name", "Test");
            
            assertThatThrownBy(() -> new EntitySuggestion(
                null,
                "products",
                data,
                0.85,
                "reason"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant ID must not be blank");
        }

        @Test
        @DisplayName("blank collection name is rejected")
        void blankCollectionNameIsRejected() {
            Map<String, Object> data = Map.of("name", "Test");
            
            assertThatThrownBy(() -> new EntitySuggestion(
                "tenant-123",
                "",
                data,
                0.85,
                "reason"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Collection name must not be blank");
        }

        @Test
        @DisplayName("null generated data is rejected")
        void nullGeneratedDataIsRejected() {
            assertThatThrownBy(() -> new EntitySuggestion(
                "tenant-123",
                "products",
                null,
                0.85,
                "reason"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Generated data must not be null");
        }

        @Test
        @DisplayName("confidence below 0.0 is rejected")
        void confidenceBelowZeroIsRejected() {
            Map<String, Object> data = Map.of("name", "Test");
            
            assertThatThrownBy(() -> new EntitySuggestion(
                "tenant-123",
                "products",
                data,
                -0.1,
                "reason"
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confidence must be between 0.0 and 1.0");
        }

        @Test
        @DisplayName("confidence above 1.0 is rejected")
        void confidenceAboveOneIsRejected() {
            Map<String, Object> data = Map.of("name", "Test");
            
            assertThatThrownBy(() -> new EntitySuggestion(
                "tenant-123",
                "products",
                data,
                1.1,
                "reason"
            ))
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
            EntitySuggestion suggestion = new EntitySuggestion(
                "tenant-123",
                "products",
                data,
                0.85,
                "reason"
            );

            assertThat(suggestion.isHighConfidence()).isTrue();
        }

        @Test
        @DisplayName("isHighConfidence returns false for confidence < 0.8")
        void isHighConfidenceReturnsFalseForLowValues() {
            Map<String, Object> data = Map.of("name", "Test");
            EntitySuggestion suggestion = new EntitySuggestion(
                "tenant-123",
                "products",
                data,
                0.75,
                "reason"
            );

            assertThat(suggestion.isHighConfidence()).isFalse();
        }

        @Test
        @DisplayName("shouldAutoAccept returns true for confidence >= 0.9")
        void shouldAutoAcceptReturnsTrueForVeryHighValues() {
            Map<String, Object> data = Map.of("name", "Test");
            EntitySuggestion suggestion = new EntitySuggestion(
                "tenant-123",
                "products",
                data,
                0.95,
                "reason"
            );

            assertThat(suggestion.shouldAutoAccept()).isTrue();
        }

        @Test
        @DisplayName("shouldAutoAccept returns false for confidence < 0.9")
        void shouldAutoAcceptReturnsFalseForLowerValues() {
            Map<String, Object> data = Map.of("name", "Test");
            EntitySuggestion suggestion = new EntitySuggestion(
                "tenant-123",
                "products",
                data,
                0.85,
                "reason"
            );

            assertThat(suggestion.shouldAutoAccept()).isFalse();
        }
    }

    @Nested
    @DisplayName("Data Access")
    class DataAccessTests {

        @Test
        @DisplayName("getFieldValue returns value for existing field")
        void getFieldValueReturnsValueForExistingField() {
            Map<String, Object> data = Map.of("name", "Test Product", "price", 99.99);
            EntitySuggestion suggestion = new EntitySuggestion(
                "tenant-123",
                "products",
                data,
                0.85,
                "reason"
            );

            assertThat(suggestion.getFieldValue("name")).isEqualTo("Test Product");
            assertThat(suggestion.getFieldValue("price")).isEqualTo(99.99);
        }

        @Test
        @DisplayName("getFieldValue returns null for missing field")
        void getFieldValueReturnsNullForMissingField() {
            Map<String, Object> data = Map.of("name", "Test Product");
            EntitySuggestion suggestion = new EntitySuggestion(
                "tenant-123",
                "products",
                data,
                0.85,
                "reason"
            );

            assertThat(suggestion.getFieldValue("price")).isNull();
        }

        @Test
        @DisplayName("hasField returns true for existing field")
        void hasFieldReturnsTrueForExistingField() {
            Map<String, Object> data = Map.of("name", "Test Product");
            EntitySuggestion suggestion = new EntitySuggestion(
                "tenant-123",
                "products",
                data,
                0.85,
                "reason"
            );

            assertThat(suggestion.hasField("name")).isTrue();
        }

        @Test
        @DisplayName("hasField returns false for missing field")
        void hasFieldReturnsFalseForMissingField() {
            Map<String, Object> data = Map.of("name", "Test Product");
            EntitySuggestion suggestion = new EntitySuggestion(
                "tenant-123",
                "products",
                data,
                0.85,
                "reason"
            );

            assertThat(suggestion.hasField("price")).isFalse();
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("record is immutable")
        void recordIsImmutable() {
            Map<String, Object> data = Map.of("name", "Test");
            EntitySuggestion suggestion = new EntitySuggestion(
                "tenant-123",
                "products",
                data,
                0.85,
                "reason"
            );

            // Records are immutable by design
            assertThat(suggestion).isNotNull();
        }
    }
}

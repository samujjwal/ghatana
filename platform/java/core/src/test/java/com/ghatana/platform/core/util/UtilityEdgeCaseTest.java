/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.platform.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive edge case validation tests for core utilities.
 * Tests null/empty/malformed inputs, boundary conditions, and performance edge cases.
 *
 * @doc.type class
 * @doc.purpose Validates edge cases and boundary conditions across core utility classes
 * @doc.layer core
 * @doc.pattern EdgeCaseTest
 */
@DisplayName("Utility Edge Case Validation")
class UtilityEdgeCaseTest {

    // =========================================================================
    // StringUtils Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("StringUtils Edge Cases")
    class StringUtilsEdgeCases {

        @Test
        @DisplayName("should handle extremely long strings")
        void shouldHandleExtremelyLongStrings() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100000; i++) {
                sb.append("a");
            }
            String veryLong = sb.toString();

            assertThat(StringUtils.isBlank(veryLong)).isFalse();
            assertThat(StringUtils.truncate(veryLong, 100)).hasSize(103); // 100 + "..."
        }

        @Test
        @DisplayName("should handle unicode and special characters")
        void shouldHandleUnicodeAndSpecialCharacters() {
            String unicode = "Hello 世界 🌍 Ñoño";
            assertThat(StringUtils.isBlank(unicode)).isFalse();
            assertThat(StringUtils.toSnakeCase(unicode)).contains("hello");
        }

        @Test
        @DisplayName("should handle mixed whitespace types")
        void shouldHandleMixedWhitespaceTypes() {
            String mixed = " \t\n\r\f"; // Various whitespace types (excluding Unicode non-breaking space)
            assertThat(StringUtils.isBlank(mixed)).isTrue();
        }

        @Test
        @DisplayName("should handle zero-width characters")
        void shouldHandleZeroWidthCharacters() {
            String zeroWidth = "test\u200B\u200C\u200D"; // Zero-width joiners
            assertThat(StringUtils.isBlank(zeroWidth)).isFalse();
            assertThat(zeroWidth.length()).isEqualTo(7);
        }

        @Test
        @DisplayName("should handle surrogate pairs")
        void shouldHandleSurrogatePairs() {
            String emoji = "😀😁😂"; // Emoji using surrogate pairs
            assertThat(StringUtils.isBlank(emoji)).isFalse();
            assertThat(emoji.length()).isEqualTo(6); // 3 emoji * 2 code units each
        }

        @Test
        @DisplayName("should handle empty arrays in join")
        void shouldHandleEmptyArraysInJoin() {
            assertThat(StringUtils.join(Collections.emptyList(), ",")).isEmpty();
            assertThat(StringUtils.join(Arrays.asList(), ",")).isEmpty();
        }

        @Test
        @DisplayName("should handle single element arrays")
        void shouldHandleSingleElementArrays() {
            assertThat(StringUtils.join(Arrays.asList("single"), ",")).isEqualTo("single");
        }

        @Test
        @DisplayName("should handle all null elements in join")
        void shouldHandleAllNullElementsInJoin() {
            List<String> allNull = Arrays.asList(null, null, null);
            assertThat(StringUtils.join(allNull, ",")).isEmpty();
        }
    }

    // =========================================================================
    // CollectionUtils Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("CollectionUtils Edge Cases")
    class CollectionUtilsEdgeCases {

        @Test
        @DisplayName("should handle empty collections")
        void shouldHandleEmptyCollections() {
            assertThat(CollectionUtils.isEmpty(Collections.emptyList())).isTrue();
        }

        @Test
        @DisplayName("should handle null collections")
        void shouldHandleNullCollections() {
            assertThat(CollectionUtils.isEmpty(null)).isTrue();
            assertThat(CollectionUtils.isEmpty((List<?>) null)).isTrue();
        }

        @Test
        @DisplayName("should handle single element collections")
        void shouldHandleSingleElementCollections() {
            assertThat(CollectionUtils.isEmpty(Arrays.asList("one"))).isFalse();
        }

        @Test
        @DisplayName("should handle nested collections")
        void shouldHandleNestedCollections() {
            List<List<String>> nested = Arrays.asList(
                Arrays.asList("a", "b"),
                Arrays.asList("c", "d")
            );
            assertThat(CollectionUtils.isEmpty(nested)).isFalse();
        }

        @Test
        @DisplayName("should handle collections with null elements")
        void shouldHandleCollectionsWithNullElements() {
            List<String> withNulls = Arrays.asList("a", null, "c");
            assertThat(CollectionUtils.isEmpty(withNulls)).isFalse();
        }
    }

    // =========================================================================
    // JsonUtils Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("JsonUtils Edge Cases")
    class JsonUtilsEdgeCases {

        @Test
        @DisplayName("should handle null JSON strings")
        void shouldHandleNullJsonStrings() {
            assertThat(JsonUtils.fromJsonSafe(null, Object.class)).isNull();
        }

        @Test
        @DisplayName("should handle empty JSON strings")
        void shouldHandleEmptyJsonStrings() {
            assertThat(JsonUtils.fromJsonSafe("", Object.class)).isNull();
            assertThat(JsonUtils.fromJsonSafe("{}", Object.class)).isNotNull();
        }

        @Test
        @DisplayName("should handle malformed JSON")
        void shouldHandleMalformedJson() {
            assertThat(JsonUtils.fromJsonSafe("{invalid}", Object.class)).isNull();
        }

        @Test
        @DisplayName("should handle deeply nested JSON")
        void shouldHandleDeeplyNestedJson() {
            String deepJson = "{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":\"value\"}}}}}";
            Object parsed = JsonUtils.fromJsonSafe(deepJson, Object.class);
            assertThat(parsed).isNotNull();
        }

        @Test
        @DisplayName("should handle JSON arrays")
        void shouldHandleJsonArrays() {
            String arrayJson = "[1,2,3,4,5]";
            Object parsed = JsonUtils.fromJsonSafe(arrayJson, Object.class);
            assertThat(parsed).isNotNull();
        }

        @Test
        @DisplayName("should handle JSON with special characters")
        void shouldHandleJsonWithSpecialCharacters() {
            String specialJson = "{\"text\":\"Hello \\\"World\\\" \\n Newline\"}";
            Object parsed = JsonUtils.fromJsonSafe(specialJson, Object.class);
            assertThat(parsed).isNotNull();
        }
    }

    // =========================================================================
    // PaginationUtils Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("PaginationUtils Edge Cases")
    class PaginationUtilsEdgeCases {

        @Test
        @DisplayName("should handle zero page size")
        void shouldHandleZeroPageSize() {
            // calculateOffset is a pure math function, validation is done in validateRequest
            assertThat(PaginationUtils.calculateOffset(0, 0)).isEqualTo(0L);
        }

        @Test
        @DisplayName("should handle negative page numbers")
        void shouldHandleNegativePageNumbers() {
            // calculateOffset is a pure math function, validation is done in validateRequest
            assertThat(PaginationUtils.calculateOffset(-1, 10)).isEqualTo(-10L);
        }

        @Test
        @DisplayName("should handle very large page numbers")
        void shouldHandleVeryLargePageNumbers() {
            long offset = PaginationUtils.calculateOffset(Integer.MAX_VALUE, 10);
            assertThat(offset).isEqualTo(Integer.MAX_VALUE * 10L);
        }

        @Test
        @DisplayName("should handle page size 1")
        void shouldHandlePageSizeOne() {
            long offset = PaginationUtils.calculateOffset(5, 1);
            assertThat(offset).isEqualTo(5);
        }

        @Test
        @DisplayName("should handle page number 0")
        void shouldHandlePageNumberZero() {
            long offset = PaginationUtils.calculateOffset(0, 10);
            assertThat(offset).isEqualTo(0);
        }
    }

    // =========================================================================
    // Preconditions Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Preconditions Edge Cases")
    class PreconditionsEdgeCases {

        @Test
        @DisplayName("should handle null check with null")
        void shouldHandleNullCheckWithNull() {
            assertThatThrownBy(() -> Preconditions.requireNonNull(null, "value"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should handle null check with valid value")
        void shouldHandleNullCheckWithValidValue() {
            String result = Preconditions.requireNonNull("valid", "value");
            assertThat(result).isEqualTo("valid");
        }

        @Test
        @DisplayName("should handle check argument with false")
        void shouldHandleCheckArgumentWithFalse() {
            assertThatThrownBy(() -> Preconditions.require(false, "error message"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("error message");
        }

        @Test
        @DisplayName("should handle check state with false")
        void shouldHandleCheckStateWithFalse() {
            assertThatThrownBy(() -> Preconditions.require(false, "state error"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("state error");
        }

        @Test
        @DisplayName("should handle check element index")
        void shouldHandleCheckElementIndex() {
            assertThatThrownBy(() -> Preconditions.requireInRange(10, 0, 5, "index"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should handle valid element index")
        void shouldHandleValidElementIndex() {
            int index = Preconditions.requireInRange(2, 0, 5, "index");
            assertThat(index).isEqualTo(2);
        }
    }

    // =========================================================================
    // Performance Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Performance Edge Cases")
    class PerformanceEdgeCases {

        @Test
        @DisplayName("should handle large collection operations efficiently")
        void shouldHandleLargeCollectionOperationsEfficiently() {
            List<Integer> largeList = Arrays.asList(new Integer[10000]);
            long start = System.currentTimeMillis();
            CollectionUtils.isEmpty(largeList);
            long duration = System.currentTimeMillis() - start;
            assertThat(duration).isLessThan(100); // Should complete in < 100ms
        }

        @Test
        @DisplayName("should handle large string operations efficiently")
        void shouldHandleLargeStringOperationsEfficiently() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("test-");
            }
            String largeString = sb.toString();

            long start = System.currentTimeMillis();
            StringUtils.isBlank(largeString);
            long duration = System.currentTimeMillis() - start;
            assertThat(duration).isLessThan(100);
        }

        @Test
        @DisplayName("should handle repeated operations efficiently")
        void shouldHandleRepeatedOperationsEfficiently() {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 1000; i++) {
                StringUtils.isBlank("test");
                CollectionUtils.isEmpty(Arrays.asList(1, 2, 3));
            }
            long duration = System.currentTimeMillis() - start;
            assertThat(duration).isLessThan(500);
        }
    }

    // =========================================================================
    // Boundary Value Tests
    // =========================================================================

    @Nested
    @DisplayName("Boundary Value Tests")
    class BoundaryValueTests {

        @Test
        @DisplayName("should handle integer max value in string operations")
        void shouldHandleIntegerMaxValueInStringOperations() {
            String maxInt = String.valueOf(Integer.MAX_VALUE);
            assertThat(StringUtils.isBlank(maxInt)).isFalse();
            assertThat(StringUtils.toSnakeCase(maxInt)).isNotNull();
        }

        @Test
        @DisplayName("should handle integer min value in string operations")
        void shouldHandleIntegerMinValueInStringOperations() {
            String minInt = String.valueOf(Integer.MIN_VALUE);
            assertThat(StringUtils.isBlank(minInt)).isFalse();
        }

        @Test
        @DisplayName("should handle long max value in string operations")
        void shouldHandleLongMaxValueInStringOperations() {
            String maxLong = String.valueOf(Long.MAX_VALUE);
            assertThat(StringUtils.isBlank(maxLong)).isFalse();
        }

        @Test
        @DisplayName("should handle collection at capacity boundaries")
        void shouldHandleCollectionAtCapacityBoundaries() {
            List<Integer> atBoundary = Arrays.asList(new Integer[100]);
            assertThat(CollectionUtils.isEmpty(atBoundary)).isFalse();
        }
    }
}

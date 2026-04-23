/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
        void shouldHandleExtremelyLongStrings() { // GH-90000
            StringBuilder sb = new StringBuilder(); // GH-90000
            for (int i = 0; i < 100000; i++) { // GH-90000
                sb.append("a");
            }
            String veryLong = sb.toString(); // GH-90000

            assertThat(StringUtils.isBlank(veryLong)).isFalse(); // GH-90000
            assertThat(StringUtils.truncate(veryLong, 100)).hasSize(103); // 100 + "..." // GH-90000
        }

        @Test
        @DisplayName("should handle unicode and special characters")
        void shouldHandleUnicodeAndSpecialCharacters() { // GH-90000
            String unicode = "Hello 世界 🌍 Ñoño";
            assertThat(StringUtils.isBlank(unicode)).isFalse(); // GH-90000
            assertThat(StringUtils.toSnakeCase(unicode)).contains("hello");
        }

        @Test
        @DisplayName("should handle mixed whitespace types")
        void shouldHandleMixedWhitespaceTypes() { // GH-90000
            String mixed = " \t\n\r\f"; // Various whitespace types (excluding Unicode non-breaking space) // GH-90000
            assertThat(StringUtils.isBlank(mixed)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should handle zero-width characters")
        void shouldHandleZeroWidthCharacters() { // GH-90000
            String zeroWidth = "test\u200B\u200C\u200D"; // Zero-width joiners
            assertThat(StringUtils.isBlank(zeroWidth)).isFalse(); // GH-90000
            assertThat(zeroWidth.length()).isEqualTo(7); // GH-90000
        }

        @Test
        @DisplayName("should handle surrogate pairs")
        void shouldHandleSurrogatePairs() { // GH-90000
            String emoji = "😀😁😂"; // Emoji using surrogate pairs
            assertThat(StringUtils.isBlank(emoji)).isFalse(); // GH-90000
            assertThat(emoji.length()).isEqualTo(6); // 3 emoji * 2 code units each // GH-90000
        }

        @Test
        @DisplayName("should handle empty arrays in join")
        void shouldHandleEmptyArraysInJoin() { // GH-90000
            assertThat(StringUtils.join(Collections.emptyList(), ",")).isEmpty(); // GH-90000
            assertThat(StringUtils.join(Arrays.asList(), ",")).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should handle single element arrays")
        void shouldHandleSingleElementArrays() { // GH-90000
            assertThat(StringUtils.join(Arrays.asList("single"), ",")).isEqualTo("single");
        }

        @Test
        @DisplayName("should handle all null elements in join")
        void shouldHandleAllNullElementsInJoin() { // GH-90000
            List<String> allNull = Arrays.asList(null, null, null); // GH-90000
            assertThat(StringUtils.join(allNull, ",")).isEmpty(); // GH-90000
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
        void shouldHandleEmptyCollections() { // GH-90000
            assertThat(CollectionUtils.isEmpty(Collections.emptyList())).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should handle null collections")
        void shouldHandleNullCollections() { // GH-90000
            assertThat(CollectionUtils.isEmpty(null)).isTrue(); // GH-90000
            assertThat(CollectionUtils.isEmpty((List<?>) null)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should handle single element collections")
        void shouldHandleSingleElementCollections() { // GH-90000
            assertThat(CollectionUtils.isEmpty(Arrays.asList("one"))).isFalse();
        }

        @Test
        @DisplayName("should handle nested collections")
        void shouldHandleNestedCollections() { // GH-90000
            List<List<String>> nested = Arrays.asList( // GH-90000
                Arrays.asList("a", "b"), // GH-90000
                Arrays.asList("c", "d") // GH-90000
            );
            assertThat(CollectionUtils.isEmpty(nested)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should handle collections with null elements")
        void shouldHandleCollectionsWithNullElements() { // GH-90000
            List<String> withNulls = Arrays.asList("a", null, "c"); // GH-90000
            assertThat(CollectionUtils.isEmpty(withNulls)).isFalse(); // GH-90000
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
        void shouldHandleNullJsonStrings() { // GH-90000
            assertThat(JsonUtils.fromJsonSafe(null, Object.class)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should handle empty JSON strings")
        void shouldHandleEmptyJsonStrings() { // GH-90000
            assertThat(JsonUtils.fromJsonSafe("", Object.class)).isNull(); // GH-90000
            assertThat(JsonUtils.fromJsonSafe("{}", Object.class)).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should handle malformed JSON")
        void shouldHandleMalformedJson() { // GH-90000
            assertThat(JsonUtils.fromJsonSafe("{invalid}", Object.class)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should handle deeply nested JSON")
        void shouldHandleDeeplyNestedJson() { // GH-90000
            String deepJson = "{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":\"value\"}}}}}";
            Object parsed = JsonUtils.fromJsonSafe(deepJson, Object.class); // GH-90000
            assertThat(parsed).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should handle JSON arrays")
        void shouldHandleJsonArrays() { // GH-90000
            String arrayJson = "[1,2,3,4,5]";
            Object parsed = JsonUtils.fromJsonSafe(arrayJson, Object.class); // GH-90000
            assertThat(parsed).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should handle JSON with special characters")
        void shouldHandleJsonWithSpecialCharacters() { // GH-90000
            String specialJson = "{\"text\":\"Hello \\\"World\\\" \\n Newline\"}";
            Object parsed = JsonUtils.fromJsonSafe(specialJson, Object.class); // GH-90000
            assertThat(parsed).isNotNull(); // GH-90000
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
        void shouldHandleZeroPageSize() { // GH-90000
            // calculateOffset is a pure math function, validation is done in validateRequest
            assertThat(PaginationUtils.calculateOffset(0, 0)).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("should handle negative page numbers")
        void shouldHandleNegativePageNumbers() { // GH-90000
            // calculateOffset is a pure math function, validation is done in validateRequest
            assertThat(PaginationUtils.calculateOffset(-1, 10)).isEqualTo(-10L); // GH-90000
        }

        @Test
        @DisplayName("should handle very large page numbers")
        void shouldHandleVeryLargePageNumbers() { // GH-90000
            long offset = PaginationUtils.calculateOffset(Integer.MAX_VALUE, 10); // GH-90000
            assertThat(offset).isEqualTo(Integer.MAX_VALUE * 10L); // GH-90000
        }

        @Test
        @DisplayName("should handle page size 1")
        void shouldHandlePageSizeOne() { // GH-90000
            long offset = PaginationUtils.calculateOffset(5, 1); // GH-90000
            assertThat(offset).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("should handle page number 0")
        void shouldHandlePageNumberZero() { // GH-90000
            long offset = PaginationUtils.calculateOffset(0, 10); // GH-90000
            assertThat(offset).isEqualTo(0); // GH-90000
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
        void shouldHandleNullCheckWithNull() { // GH-90000
            assertThatThrownBy(() -> Preconditions.requireNonNull(null, "value")) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should handle null check with valid value")
        void shouldHandleNullCheckWithValidValue() { // GH-90000
            String result = Preconditions.requireNonNull("valid", "value"); // GH-90000
            assertThat(result).isEqualTo("valid");
        }

        @Test
        @DisplayName("should handle check argument with false")
        void shouldHandleCheckArgumentWithFalse() { // GH-90000
            assertThatThrownBy(() -> Preconditions.require(false, "error message")) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("error message");
        }

        @Test
        @DisplayName("should handle check state with false")
        void shouldHandleCheckStateWithFalse() { // GH-90000
            assertThatThrownBy(() -> Preconditions.require(false, "state error")) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("state error");
        }

        @Test
        @DisplayName("should handle check element index")
        void shouldHandleCheckElementIndex() { // GH-90000
            assertThatThrownBy(() -> Preconditions.requireInRange(10, 0, 5, "index")) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("should handle valid element index")
        void shouldHandleValidElementIndex() { // GH-90000
            int index = Preconditions.requireInRange(2, 0, 5, "index"); // GH-90000
            assertThat(index).isEqualTo(2); // GH-90000
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
        void shouldHandleLargeCollectionOperationsEfficiently() { // GH-90000
            List<Integer> largeList = Arrays.asList(new Integer[10000]); // GH-90000
            long start = System.currentTimeMillis(); // GH-90000
            CollectionUtils.isEmpty(largeList); // GH-90000
            long duration = System.currentTimeMillis() - start; // GH-90000
            assertThat(duration).isLessThan(100); // Should complete in < 100ms // GH-90000
        }

        @Test
        @DisplayName("should handle large string operations efficiently")
        void shouldHandleLargeStringOperationsEfficiently() { // GH-90000
            StringBuilder sb = new StringBuilder(); // GH-90000
            for (int i = 0; i < 10000; i++) { // GH-90000
                sb.append("test-");
            }
            String largeString = sb.toString(); // GH-90000

            long start = System.currentTimeMillis(); // GH-90000
            StringUtils.isBlank(largeString); // GH-90000
            long duration = System.currentTimeMillis() - start; // GH-90000
            assertThat(duration).isLessThan(100); // GH-90000
        }

        @Test
        @DisplayName("should handle repeated operations efficiently")
        void shouldHandleRepeatedOperationsEfficiently() { // GH-90000
            long start = System.currentTimeMillis(); // GH-90000
            for (int i = 0; i < 1000; i++) { // GH-90000
                StringUtils.isBlank("test");
                CollectionUtils.isEmpty(Arrays.asList(1, 2, 3)); // GH-90000
            }
            long duration = System.currentTimeMillis() - start; // GH-90000
            assertThat(duration).isLessThan(500); // GH-90000
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
        void shouldHandleIntegerMaxValueInStringOperations() { // GH-90000
            String maxInt = String.valueOf(Integer.MAX_VALUE); // GH-90000
            assertThat(StringUtils.isBlank(maxInt)).isFalse(); // GH-90000
            assertThat(StringUtils.toSnakeCase(maxInt)).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should handle integer min value in string operations")
        void shouldHandleIntegerMinValueInStringOperations() { // GH-90000
            String minInt = String.valueOf(Integer.MIN_VALUE); // GH-90000
            assertThat(StringUtils.isBlank(minInt)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should handle long max value in string operations")
        void shouldHandleLongMaxValueInStringOperations() { // GH-90000
            String maxLong = String.valueOf(Long.MAX_VALUE); // GH-90000
            assertThat(StringUtils.isBlank(maxLong)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should handle collection at capacity boundaries")
        void shouldHandleCollectionAtCapacityBoundaries() { // GH-90000
            List<Integer> atBoundary = Arrays.asList(new Integer[100]); // GH-90000
            assertThat(CollectionUtils.isEmpty(atBoundary)).isFalse(); // GH-90000
        }
    }
}

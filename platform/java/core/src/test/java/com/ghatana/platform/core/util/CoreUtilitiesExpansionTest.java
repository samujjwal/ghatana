/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3 Expansion tests for Core {@link StringUtils} and {@link Result}.
 * Tests string manipulation at scale, result chaining, and edge cases.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for core platform utilities
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("CoreUtilities - Phase 3 Expansion")
class CoreUtilitiesExpansionTest {

    // ============================================
    // STRING UTILS: BLANK CHECKING (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("StringUtils - Blank Checking")
    class BlankCheckingTests {

        @Test
        @DisplayName("isBlank recognizes various whitespace patterns")
        void variousWhitespacePatterns() { // GH-90000
            String[] blanks = {
                null, "", " ", "  ", "\t", "\n", "\r\n", " \t\n\r "
            };

            for (String blank : blanks) { // GH-90000
                assertThat(StringUtils.isBlank(blank)) // GH-90000
                    .as("Should be blank: %s", blank) // GH-90000
                    .isTrue(); // GH-90000
            }
        }

        @Test
        @DisplayName("isNotBlank recognizes content correctly")
        void contentRecognition() { // GH-90000
            String[] withContent = {
                "a", "hello", " hello ", "\thello\n", "!@#$%", "123", "   x   "
            };

            for (String str : withContent) { // GH-90000
                assertThat(StringUtils.isNotBlank(str)) // GH-90000
                    .as("Should not be blank: %s", str) // GH-90000
                    .isTrue(); // GH-90000
            }
        }

        @Test
        @DisplayName("firstNonBlank handles many blanks before content")
        void firstNonBlankMany() { // GH-90000
            String result = StringUtils.firstNonBlank( // GH-90000
                null, "", "  ", "\t", "\n", "  \t\n  ", "first", "second");

            assertThat(result).isEqualTo("first");
        }

        @Test
        @DisplayName("defaultIfBlank with empty and nil returns default")
        void defaultIfBlankMultiple() { // GH-90000
            assertThat(StringUtils.defaultIfBlank(null, "def")).isEqualTo("def");
            assertThat(StringUtils.defaultIfBlank("", "def")).isEqualTo("def");
            assertThat(StringUtils.defaultIfBlank("  ", "def")).isEqualTo("def");
            assertThat(StringUtils.defaultIfBlank("value", "def")).isEqualTo("value");
        }
    }

    // ============================================
    // STRING UTILS: CASE CONVERSION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("StringUtils - Case Conversion")
    class CaseConversionTests {

        @Test
        @DisplayName("toSnakeCase converts camelCase and PascalCase")
        void snakeCaseConversion() { // GH-90000
            assertThat(StringUtils.toSnakeCase("helloWorld")).isEqualTo("hello_world");
            assertThat(StringUtils.toSnakeCase("HelloWorld")).isEqualTo("hello_world");
            assertThat(StringUtils.toSnakeCase("URL")).isEqualTo("url");
            assertThat(StringUtils.toSnakeCase("HTTPServer")).isEqualTo("http_server");
            assertThat(StringUtils.toSnakeCase("alreadySnakeCase")).isEqualTo("already_snake_case");
            assertThat(StringUtils.toSnakeCase("a")).isEqualTo("a");
        }

        @Test
        @DisplayName("toKebabCase converts camelCase and PascalCase")
        void kebabCaseConversion() { // GH-90000
            assertThat(StringUtils.toKebabCase("helloWorld")).contains("hello");
            assertThat(StringUtils.toKebabCase("helloWorld")).contains("world");
            assertThat(StringUtils.toKebabCase("camelCaseString")).contains("camel");
        }

        @Test
        @DisplayName("toCamelCase converts snake_case and kebab-case")
        void camelCaseConversion() { // GH-90000
            assertThat(StringUtils.toCamelCase("hello_world")).isEqualTo("helloWorld");
            assertThat(StringUtils.toCamelCase("hello-world")).isEqualTo("helloWorld");
            assertThat(StringUtils.toCamelCase("HELLO_WORLD")).isNotNull();
        }

        @Test
        @DisplayName("Case conversions handle long strings")
        void longStringConversion() { // GH-90000
            String longCamelCase = "thisIsAVeryLongStringWithManyWordsInCamelCase";
            String snaked = StringUtils.toSnakeCase(longCamelCase); // GH-90000
            assertThat(snaked).isNotNull(); // GH-90000
            assertThat(snaked).contains("_");
        }
    }

    // ============================================
    // STRING UTILS: JOINING (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("StringUtils - Joining")
    class JoiningTests {

        @Test
        @DisplayName("join combines many strings with delimiter")
        void manyItemsJoin() { // GH-90000
            List<String> items = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                items.add("item-" + i); // GH-90000
            }

            String joined = StringUtils.join(items, ","); // GH-90000

            assertThat(joined).contains("item-0");
            assertThat(joined).contains("item-99");
            assertThat(joined).contains(",");
        }

        @Test
        @DisplayName("join with different delimiters")
        void joinMultipleDelimiters() { // GH-90000
            List<String> items = Arrays.asList("a", "b", "c"); // GH-90000

            assertThat(StringUtils.join(items, ",")).isEqualTo("a,b,c");
            assertThat(StringUtils.join(items, "-")).isEqualTo("a-b-c");
            assertThat(StringUtils.join(items, " | ")).isEqualTo("a | b | c");
            assertThat(StringUtils.join(items, "")).isEqualTo("abc");
        }

        @Test
        @DisplayName("join with null items and empty list")
        void joinEdgeCases() { // GH-90000
            assertThat(StringUtils.join(null, ",")).isEmpty(); // GH-90000
            assertThat(StringUtils.join(Arrays.asList(), ",")).isEmpty(); // GH-90000
            assertThat(StringUtils.join(Arrays.asList("single"), ",")).isEqualTo("single");
        }
    }

    // ============================================
    // RESULT: SUCCESS PATH (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Result - Success Path")
    class SuccessPathTests {

        @Test
        @DisplayName("Success result chain mapped correctly")
        void successMapping() { // GH-90000
            Result<String, String> result = Result.success("42");

            Result<Integer, String> mapped = result.map(Integer::parseInt); // GH-90000

            assertThat(mapped.isSuccess()).isTrue(); // GH-90000
            assertThat(mapped.get()).isEqualTo(42); // GH-90000
        }

        @Test
        @DisplayName("Multiple map operations chain correctly")
        void chainingMaps() { // GH-90000
            Result<String, String> result = Result.success("hello");

            Result<Integer, String> length = result.map(String::length); // GH-90000
            Result<String, String> lengthStr = length.map(Object::toString); // GH-90000

            assertThat(lengthStr.isSuccess()).isTrue(); // GH-90000
            assertThat(lengthStr.get()).isEqualTo("5");
        }

        @Test
        @DisplayName("flatMap chains successful results")
        void flatMapSuccess() { // GH-90000
            Result<String, String> result = Result.success("42");

            Result<Integer, String> chained = result.flatMap(s -> { // GH-90000
                try {
                    return Result.success(Integer.parseInt(s)); // GH-90000
                } catch (NumberFormatException e) { // GH-90000
                    return Result.failure("invalid number");
                }
            });

            assertThat(chained.isSuccess()).isTrue(); // GH-90000
            assertThat(chained.get()).isEqualTo(42); // GH-90000
        }

        @Test
        @DisplayName("getOrElse returns success value")
        void getOrElseSuccess() { // GH-90000
            Result<String, String> result = Result.success("value");

            assertThat(result.getOrElse("default")).isEqualTo("value");
            assertThat(result.getOrElse(null)).isEqualTo("value");
        }
    }

    // ============================================
    // RESULT: FAILURE PATH (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Result - Failure Path")
    class FailurePathTests {

        @Test
        @DisplayName("Failure result propagates through map")
        void failureMapping() { // GH-90000
            Result<String, String> result = Result.failure("error occurred");

            Result<Integer, String> mapped = result.map(String::length); // GH-90000

            assertThat(mapped.isFailure()).isTrue(); // GH-90000
            assertThat(mapped.getError()).isEqualTo("error occurred");
        }

        @Test
        @DisplayName("mapError transforms failure")
        void mapErrorTransform() { // GH-90000
            Result<String, String> result = Result.failure("original error");

            Result<String, Integer> mapped = result.mapError(String::length); // GH-90000

            assertThat(mapped.isFailure()).isTrue(); // GH-90000
            assertThat(mapped.getError()).isEqualTo(14); // GH-90000
        }

        @Test
        @DisplayName("flatMap with failure returns failure early")
        void flatMapFailure() { // GH-90000
            Result<String, String> result = Result.failure("invalid input");

            Result<Integer, String> chained = result.flatMap(s -> { // GH-90000
                return Result.success(Integer.parseInt(s)); // GH-90000
            });

            assertThat(chained.isFailure()).isTrue(); // GH-90000
            assertThat(chained.getError()).isEqualTo("invalid input");
        }

        @Test
        @DisplayName("getOrElse returns default on failure")
        void getOrElseFailure() { // GH-90000
            Result<String, String> result = Result.failure("error");

            assertThat(result.getOrElse("default")).isEqualTo("default");
        }
    }

    // ============================================
    // RESULT: ERROR HANDLING (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Result - Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("get on failure throws NoSuchElementException")
        void getFailureThrows() { // GH-90000
            Result<String, String> result = Result.failure("error message");

            assertThatThrownBy(result::get) // GH-90000
                .isInstanceOf(NoSuchElementException.class) // GH-90000
                .hasMessageContaining("error message");
        }

        @Test
        @DisplayName("getError on success throws NoSuchElementException")
        void getErrorSuccessThrows() { // GH-90000
            Result<String, String> result = Result.success("value");

            assertThatThrownBy(result::getError) // GH-90000
                .isInstanceOf(NoSuchElementException.class); // GH-90000
        }

        @Test
        @DisplayName("Many nested flatMaps with potential failures")
        void complexFlatMapChain() { // GH-90000
            Result<String, String> initial = Result.success("10");

            Result<Integer, String> step1 = initial.flatMap(s -> { // GH-90000
                try {
                    return Result.success(Integer.parseInt(s)); // GH-90000
                } catch (NumberFormatException e) { // GH-90000
                    return Result.failure("parse error");
                }
            });

            Result<Integer, String> step2 = step1.flatMap(i -> { // GH-90000
                if (i < 0) { // GH-90000
                    return Result.failure("negative number");
                }
                return Result.success(i * 2); // GH-90000
            });

            Result<String, String> step3 = step2.flatMap(i -> { // GH-90000
                return Result.success("result: " + i); // GH-90000
            });

            assertThat(step3.isSuccess()).isTrue(); // GH-90000
            assertThat(step3.get()).isEqualTo("result: 20");
        }
    }

    // ============================================
    // RESULT: COMPLEX PATTERNS (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Result - Complex Patterns")
    class ComplexPatternTests {

        @Test
        @DisplayName("Transforming result from one type to another")
        void typeTransformation() { // GH-90000
            Result<String, String> stringResult = Result.success("42");

            Result<Integer, String> intResult = stringResult
                .flatMap(s -> { // GH-90000
                    try {
                        return Result.success(Integer.parseInt(s)); // GH-90000
                    } catch (NumberFormatException e) { // GH-90000
                        return Result.failure("Not a number: " + s); // GH-90000
                    }
                });

            Result<Boolean, String> boolResult = intResult
                .map(i -> i > 0); // GH-90000

            assertThat(boolResult.get()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Many results in list processed sequentially")
        void resultSequence() { // GH-90000
            List<Result<Integer, String>> results = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 50; i++) { // GH-90000
                final int idx = i;
                results.add(Result.success(idx * 10)); // GH-90000
            }

            AtomicInteger sum = new AtomicInteger(0); // GH-90000
            for (Result<Integer, String> result : results) { // GH-90000
                if (result.isSuccess()) { // GH-90000
                    sum.addAndGet(result.get()); // GH-90000
                }
            }

            assertThat(sum.get()).isEqualTo(12250); // Sum of 0*10 + 1*10 + ... + 49*10 // GH-90000
        }

        @Test
        @DisplayName("Mixed success and failure results in collection")
        void mixedResultCollection() { // GH-90000
            List<Result<Integer, String>> results = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                if (idx % 3 == 0) { // GH-90000
                    results.add(Result.failure("error for " + idx)); // GH-90000
                } else {
                    results.add(Result.success(idx)); // GH-90000
                }
            }

            long successCount = results.stream().filter(Result::isSuccess).count(); // GH-90000
            long failureCount = results.stream().filter(Result::isFailure).count(); // GH-90000

            assertThat(successCount).isEqualTo(66); // GH-90000
            assertThat(failureCount).isEqualTo(34); // GH-90000
        }
    }

    // ============================================
    // COMBINED UTILITY TESTS (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Combined Utilities")
    class CombinedTests {

        @Test
        @DisplayName("StringUtils and Result together for parsing")
        void stringResultCombination() { // GH-90000
            String input = "   42   ";

            Result<String, String> trimmed = Result.success( // GH-90000
                StringUtils.defaultIfBlank(input, "0")); // GH-90000

            Result<Integer, String> parsed = trimmed.flatMap(s -> { // GH-90000
                try {
                    return Result.success(Integer.parseInt(s.trim())); // GH-90000
                } catch (NumberFormatException e) { // GH-90000
                    return Result.failure("Invalid integer: " + s); // GH-90000
                }
            });

            Result<String, String> formatted = parsed.map(i -> // GH-90000
                StringUtils.toSnakeCase("number_" + i)); // GH-90000

            assertThat(formatted.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Complex string manipulation pipeline with results")
        void stringProcessingPipeline() { // GH-90000
            String input = "HelloWorld";

            Result<String, String> step1 = Result.<String, String>success(input) // GH-90000
                .map(s -> StringUtils.toSnakeCase(s)); // GH-90000

            Result<String, String> step2 = step1
                .map(s -> s.toUpperCase()); // GH-90000

            Result<Integer, String> step3 = step2
                .map(String::length); // GH-90000

            assertThat(step3.get()).isGreaterThan(0); // GH-90000
        }
    }

    // ============================================
    // EDGE CASES & PERFORMANCE (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases & Performance")
    class EdgeCaseTests {

        @Test
        @DisplayName("Processing very long strings")
        void veryLongStrings() { // GH-90000
            String longString = "a".repeat(10000); // GH-90000

            String snaked = StringUtils.toSnakeCase(longString); // GH-90000
            assertThat(snaked).isNotNull(); // GH-90000

            Result<Integer, String> result = Result.<String, String>success(longString) // GH-90000
                .map(String::length); // GH-90000

            assertThat(result.get()).isEqualTo(10000); // GH-90000
        }

        @Test
        @DisplayName("Large collection of results processed efficiently")
        void largeResultCollection() { // GH-90000
            List<Result<Integer, String>> results = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 1000; i++) { // GH-90000
                final int idx = i;
                results.add(Result.success(idx)); // GH-90000
            }

            long total = results.stream() // GH-90000
                .filter(Result::isSuccess) // GH-90000
                .map(Result::get) // GH-90000
                .mapToLong(Long::valueOf) // GH-90000
                .sum(); // GH-90000

            assertThat(total).isEqualTo(499500); // Sum of 0 to 999 // GH-90000
        }
    }
}

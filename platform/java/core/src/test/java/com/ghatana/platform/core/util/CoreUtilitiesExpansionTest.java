/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    // STRING UTILS: BLANK CHECKING (4 tests) 
    // ============================================

    @Nested
    @DisplayName("StringUtils - Blank Checking")
    class BlankCheckingTests {

        @Test
        @DisplayName("isBlank recognizes various whitespace patterns")
        void variousWhitespacePatterns() { 
            String[] blanks = {
                null, "", " ", "  ", "\t", "\n", "\r\n", " \t\n\r "
            };

            for (String blank : blanks) { 
                assertThat(StringUtils.isBlank(blank)) 
                    .as("Should be blank: %s", blank) 
                    .isTrue(); 
            }
        }

        @Test
        @DisplayName("isNotBlank recognizes content correctly")
        void contentRecognition() { 
            String[] withContent = {
                "a", "hello", " hello ", "\thello\n", "!@#$%", "123", "   x   "
            };

            for (String str : withContent) { 
                assertThat(StringUtils.isNotBlank(str)) 
                    .as("Should not be blank: %s", str) 
                    .isTrue(); 
            }
        }

        @Test
        @DisplayName("firstNonBlank handles many blanks before content")
        void firstNonBlankMany() { 
            String result = StringUtils.firstNonBlank( 
                null, "", "  ", "\t", "\n", "  \t\n  ", "first", "second");

            assertThat(result).isEqualTo("first");
        }

        @Test
        @DisplayName("defaultIfBlank with empty and nil returns default")
        void defaultIfBlankMultiple() { 
            assertThat(StringUtils.defaultIfBlank(null, "def")).isEqualTo("def");
            assertThat(StringUtils.defaultIfBlank("", "def")).isEqualTo("def");
            assertThat(StringUtils.defaultIfBlank("  ", "def")).isEqualTo("def");
            assertThat(StringUtils.defaultIfBlank("value", "def")).isEqualTo("value");
        }
    }

    // ============================================
    // STRING UTILS: CASE CONVERSION (4 tests) 
    // ============================================

    @Nested
    @DisplayName("StringUtils - Case Conversion")
    class CaseConversionTests {

        @Test
        @DisplayName("toSnakeCase converts camelCase and PascalCase")
        void snakeCaseConversion() { 
            assertThat(StringUtils.toSnakeCase("helloWorld")).isEqualTo("hello_world");
            assertThat(StringUtils.toSnakeCase("HelloWorld")).isEqualTo("hello_world");
            assertThat(StringUtils.toSnakeCase("URL")).isEqualTo("url");
            assertThat(StringUtils.toSnakeCase("HTTPServer")).isEqualTo("http_server");
            assertThat(StringUtils.toSnakeCase("alreadySnakeCase")).isEqualTo("already_snake_case");
            assertThat(StringUtils.toSnakeCase("a")).isEqualTo("a");
        }

        @Test
        @DisplayName("toKebabCase converts camelCase and PascalCase")
        void kebabCaseConversion() { 
            assertThat(StringUtils.toKebabCase("helloWorld")).contains("hello");
            assertThat(StringUtils.toKebabCase("helloWorld")).contains("world");
            assertThat(StringUtils.toKebabCase("camelCaseString")).contains("camel");
        }

        @Test
        @DisplayName("toCamelCase converts snake_case and kebab-case")
        void camelCaseConversion() { 
            assertThat(StringUtils.toCamelCase("hello_world")).isEqualTo("helloWorld");
            assertThat(StringUtils.toCamelCase("hello-world")).isEqualTo("helloWorld");
            assertThat(StringUtils.toCamelCase("HELLO_WORLD")).isNotNull();
        }

        @Test
        @DisplayName("Case conversions handle long strings")
        void longStringConversion() { 
            String longCamelCase = "thisIsAVeryLongStringWithManyWordsInCamelCase";
            String snaked = StringUtils.toSnakeCase(longCamelCase); 
            assertThat(snaked).isNotNull(); 
            assertThat(snaked).contains("_");
        }
    }

    // ============================================
    // STRING UTILS: JOINING (3 tests) 
    // ============================================

    @Nested
    @DisplayName("StringUtils - Joining")
    class JoiningTests {

        @Test
        @DisplayName("join combines many strings with delimiter")
        void manyItemsJoin() { 
            List<String> items = new ArrayList<>(); 
            for (int i = 0; i < 100; i++) { 
                items.add("item-" + i); 
            }

            String joined = StringUtils.join(items, ","); 

            assertThat(joined).contains("item-0");
            assertThat(joined).contains("item-99");
            assertThat(joined).contains(",");
        }

        @Test
        @DisplayName("join with different delimiters")
        void joinMultipleDelimiters() { 
            List<String> items = Arrays.asList("a", "b", "c"); 

            assertThat(StringUtils.join(items, ",")).isEqualTo("a,b,c");
            assertThat(StringUtils.join(items, "-")).isEqualTo("a-b-c");
            assertThat(StringUtils.join(items, " | ")).isEqualTo("a | b | c");
            assertThat(StringUtils.join(items, "")).isEqualTo("abc");
        }

        @Test
        @DisplayName("join with null items and empty list")
        void joinEdgeCases() { 
            assertThat(StringUtils.join(null, ",")).isEmpty(); 
            assertThat(StringUtils.join(Arrays.asList(), ",")).isEmpty(); 
            assertThat(StringUtils.join(Arrays.asList("single"), ",")).isEqualTo("single");
        }
    }

    // ============================================
    // RESULT: SUCCESS PATH (4 tests) 
    // ============================================

    @Nested
    @DisplayName("Result - Success Path")
    class SuccessPathTests {

        @Test
        @DisplayName("Success result chain mapped correctly")
        void successMapping() { 
            Result<String, String> result = Result.success("42");

            Result<Integer, String> mapped = result.map(Integer::parseInt); 

            assertThat(mapped.isSuccess()).isTrue(); 
            assertThat(mapped.get()).isEqualTo(42); 
        }

        @Test
        @DisplayName("Multiple map operations chain correctly")
        void chainingMaps() { 
            Result<String, String> result = Result.success("hello");

            Result<Integer, String> length = result.map(String::length); 
            Result<String, String> lengthStr = length.map(Object::toString); 

            assertThat(lengthStr.isSuccess()).isTrue(); 
            assertThat(lengthStr.get()).isEqualTo("5");
        }

        @Test
        @DisplayName("flatMap chains successful results")
        void flatMapSuccess() { 
            Result<String, String> result = Result.success("42");

            Result<Integer, String> chained = result.flatMap(s -> { 
                try {
                    return Result.success(Integer.parseInt(s)); 
                } catch (NumberFormatException e) { 
                    return Result.failure("invalid number");
                }
            });

            assertThat(chained.isSuccess()).isTrue(); 
            assertThat(chained.get()).isEqualTo(42); 
        }

        @Test
        @DisplayName("getOrElse returns success value")
        void getOrElseSuccess() { 
            Result<String, String> result = Result.success("value");

            assertThat(result.getOrElse("default")).isEqualTo("value");
            assertThat(result.getOrElse(null)).isEqualTo("value");
        }
    }

    // ============================================
    // RESULT: FAILURE PATH (4 tests) 
    // ============================================

    @Nested
    @DisplayName("Result - Failure Path")
    class FailurePathTests {

        @Test
        @DisplayName("Failure result propagates through map")
        void failureMapping() { 
            Result<String, String> result = Result.failure("error occurred");

            Result<Integer, String> mapped = result.map(String::length); 

            assertThat(mapped.isFailure()).isTrue(); 
            assertThat(mapped.getError()).isEqualTo("error occurred");
        }

        @Test
        @DisplayName("mapError transforms failure")
        void mapErrorTransform() { 
            Result<String, String> result = Result.failure("original error");

            Result<String, Integer> mapped = result.mapError(String::length); 

            assertThat(mapped.isFailure()).isTrue(); 
            assertThat(mapped.getError()).isEqualTo(14); 
        }

        @Test
        @DisplayName("flatMap with failure returns failure early")
        void flatMapFailure() { 
            Result<String, String> result = Result.failure("invalid input");

            Result<Integer, String> chained = result.flatMap(s -> { 
                return Result.success(Integer.parseInt(s)); 
            });

            assertThat(chained.isFailure()).isTrue(); 
            assertThat(chained.getError()).isEqualTo("invalid input");
        }

        @Test
        @DisplayName("getOrElse returns default on failure")
        void getOrElseFailure() { 
            Result<String, String> result = Result.failure("error");

            assertThat(result.getOrElse("default")).isEqualTo("default");
        }
    }

    // ============================================
    // RESULT: ERROR HANDLING (3 tests) 
    // ============================================

    @Nested
    @DisplayName("Result - Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("get on failure throws NoSuchElementException")
        void getFailureThrows() { 
            Result<String, String> result = Result.failure("error message");

            assertThatThrownBy(result::get) 
                .isInstanceOf(NoSuchElementException.class) 
                .hasMessageContaining("error message");
        }

        @Test
        @DisplayName("getError on success throws NoSuchElementException")
        void getErrorSuccessThrows() { 
            Result<String, String> result = Result.success("value");

            assertThatThrownBy(result::getError) 
                .isInstanceOf(NoSuchElementException.class); 
        }

        @Test
        @DisplayName("Many nested flatMaps with potential failures")
        void complexFlatMapChain() { 
            Result<String, String> initial = Result.success("10");

            Result<Integer, String> step1 = initial.flatMap(s -> { 
                try {
                    return Result.success(Integer.parseInt(s)); 
                } catch (NumberFormatException e) { 
                    return Result.failure("parse error");
                }
            });

            Result<Integer, String> step2 = step1.flatMap(i -> { 
                if (i < 0) { 
                    return Result.failure("negative number");
                }
                return Result.success(i * 2); 
            });

            Result<String, String> step3 = step2.flatMap(i -> { 
                return Result.success("result: " + i); 
            });

            assertThat(step3.isSuccess()).isTrue(); 
            assertThat(step3.get()).isEqualTo("result: 20");
        }
    }

    // ============================================
    // RESULT: COMPLEX PATTERNS (3 tests) 
    // ============================================

    @Nested
    @DisplayName("Result - Complex Patterns")
    class ComplexPatternTests {

        @Test
        @DisplayName("Transforming result from one type to another")
        void typeTransformation() { 
            Result<String, String> stringResult = Result.success("42");

            Result<Integer, String> intResult = stringResult
                .flatMap(s -> { 
                    try {
                        return Result.success(Integer.parseInt(s)); 
                    } catch (NumberFormatException e) { 
                        return Result.failure("Not a number: " + s); 
                    }
                });

            Result<Boolean, String> boolResult = intResult
                .map(i -> i > 0); 

            assertThat(boolResult.get()).isTrue(); 
        }

        @Test
        @DisplayName("Many results in list processed sequentially")
        void resultSequence() { 
            List<Result<Integer, String>> results = new ArrayList<>(); 
            for (int i = 0; i < 50; i++) { 
                final int idx = i;
                results.add(Result.success(idx * 10)); 
            }

            AtomicInteger sum = new AtomicInteger(0); 
            for (Result<Integer, String> result : results) { 
                if (result.isSuccess()) { 
                    sum.addAndGet(result.get()); 
                }
            }

            assertThat(sum.get()).isEqualTo(12250); // Sum of 0*10 + 1*10 + ... + 49*10 
        }

        @Test
        @DisplayName("Mixed success and failure results in collection")
        void mixedResultCollection() { 
            List<Result<Integer, String>> results = new ArrayList<>(); 
            for (int i = 0; i < 100; i++) { 
                final int idx = i;
                if (idx % 3 == 0) { 
                    results.add(Result.failure("error for " + idx)); 
                } else {
                    results.add(Result.success(idx)); 
                }
            }

            long successCount = results.stream().filter(Result::isSuccess).count(); 
            long failureCount = results.stream().filter(Result::isFailure).count(); 

            assertThat(successCount).isEqualTo(66); 
            assertThat(failureCount).isEqualTo(34); 
        }
    }

    // ============================================
    // COMBINED UTILITY TESTS (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Combined Utilities")
    class CombinedTests {

        @Test
        @DisplayName("StringUtils and Result together for parsing")
        void stringResultCombination() { 
            String input = "   42   ";

            Result<String, String> trimmed = Result.success( 
                StringUtils.defaultIfBlank(input, "0")); 

            Result<Integer, String> parsed = trimmed.flatMap(s -> { 
                try {
                    return Result.success(Integer.parseInt(s.trim())); 
                } catch (NumberFormatException e) { 
                    return Result.failure("Invalid integer: " + s); 
                }
            });

            Result<String, String> formatted = parsed.map(i -> 
                StringUtils.toSnakeCase("number_" + i)); 

            assertThat(formatted.isSuccess()).isTrue(); 
        }

        @Test
        @DisplayName("Complex string manipulation pipeline with results")
        void stringProcessingPipeline() { 
            String input = "HelloWorld";

            Result<String, String> step1 = Result.<String, String>success(input) 
                .map(s -> StringUtils.toSnakeCase(s)); 

            Result<String, String> step2 = step1
                .map(s -> s.toUpperCase()); 

            Result<Integer, String> step3 = step2
                .map(String::length); 

            assertThat(step3.get()).isGreaterThan(0); 
        }
    }

    // ============================================
    // EDGE CASES & PERFORMANCE (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Edge Cases & Performance")
    class EdgeCaseTests {

        @Test
        @DisplayName("Processing very long strings")
        void veryLongStrings() { 
            String longString = "a".repeat(10000); 

            String snaked = StringUtils.toSnakeCase(longString); 
            assertThat(snaked).isNotNull(); 

            Result<Integer, String> result = Result.<String, String>success(longString) 
                .map(String::length); 

            assertThat(result.get()).isEqualTo(10000); 
        }

        @Test
        @DisplayName("Large collection of results processed efficiently")
        void largeResultCollection() { 
            List<Result<Integer, String>> results = new ArrayList<>(); 
            for (int i = 0; i < 1000; i++) { 
                final int idx = i;
                results.add(Result.success(idx)); 
            }

            long total = results.stream() 
                .filter(Result::isSuccess) 
                .map(Result::get) 
                .mapToLong(Long::valueOf) 
                .sum(); 

            assertThat(total).isEqualTo(499500); // Sum of 0 to 999 
        }
    }
}

package com.ghatana.platform.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Result [GH-90000]")
class ResultTest {

    @Test
    @DisplayName("success should create a successful result [GH-90000]")
    void successShouldCreateSuccessfulResult() { // GH-90000
        Result<String, String> result = Result.success("value [GH-90000]");

        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.isFailure()).isFalse(); // GH-90000
        assertThat(result.get()).isEqualTo("value [GH-90000]");
    }

    @Test
    @DisplayName("failure should create a failed result [GH-90000]")
    void failureShouldCreateFailedResult() { // GH-90000
        Result<String, String> result = Result.failure("error [GH-90000]");

        assertThat(result.isSuccess()).isFalse(); // GH-90000
        assertThat(result.isFailure()).isTrue(); // GH-90000
        assertThat(result.getError()).isEqualTo("error [GH-90000]");
    }

    @Test
    @DisplayName("get on failure should throw NoSuchElementException [GH-90000]")
    void getOnFailureShouldThrow() { // GH-90000
        Result<String, String> result = Result.failure("error [GH-90000]");

        assertThatThrownBy(result::get) // GH-90000
                .isInstanceOf(NoSuchElementException.class) // GH-90000
                .hasMessageContaining("error [GH-90000]");
    }

    @Test
    @DisplayName("getError on success should throw NoSuchElementException [GH-90000]")
    void getErrorOnSuccessShouldThrow() { // GH-90000
        Result<String, String> result = Result.success("value [GH-90000]");

        assertThatThrownBy(result::getError) // GH-90000
                .isInstanceOf(NoSuchElementException.class); // GH-90000
    }

    @Test
    @DisplayName("getOrElse should return value on success [GH-90000]")
    void getOrElseShouldReturnValueOnSuccess() { // GH-90000
        Result<String, String> result = Result.success("value [GH-90000]");

        assertThat(result.getOrElse("default [GH-90000]")).isEqualTo("value [GH-90000]");
    }

    @Test
    @DisplayName("getOrElse should return default on failure [GH-90000]")
    void getOrElseShouldReturnDefaultOnFailure() { // GH-90000
        Result<String, String> result = Result.failure("error [GH-90000]");

        assertThat(result.getOrElse("default [GH-90000]")).isEqualTo("default [GH-90000]");
    }

    @Test
    @DisplayName("map should transform success value [GH-90000]")
    void mapShouldTransformSuccessValue() { // GH-90000
        Result<String, String> result = Result.success("hello [GH-90000]");

        Result<Integer, String> mapped = result.map(String::length); // GH-90000

        assertThat(mapped.isSuccess()).isTrue(); // GH-90000
        assertThat(mapped.get()).isEqualTo(5); // GH-90000
    }

    @Test
    @DisplayName("map should not transform failure [GH-90000]")
    void mapShouldNotTransformFailure() { // GH-90000
        Result<String, String> result = Result.failure("error [GH-90000]");

        Result<Integer, String> mapped = result.map(String::length); // GH-90000

        assertThat(mapped.isFailure()).isTrue(); // GH-90000
        assertThat(mapped.getError()).isEqualTo("error [GH-90000]");
    }

    @Test
    @DisplayName("mapError should transform error [GH-90000]")
    void mapErrorShouldTransformError() { // GH-90000
        Result<String, String> result = Result.failure("error [GH-90000]");

        Result<String, Integer> mapped = result.mapError(String::length); // GH-90000

        assertThat(mapped.isFailure()).isTrue(); // GH-90000
        assertThat(mapped.getError()).isEqualTo(5); // GH-90000
    }

    @Test
    @DisplayName("flatMap should chain successful results [GH-90000]")
    void flatMapShouldChainSuccessfulResults() { // GH-90000
        Result<String, String> result = Result.success("42 [GH-90000]");

        Result<Integer, String> chained = result.flatMap(s -> { // GH-90000
            try {
                return Result.success(Integer.parseInt(s)); // GH-90000
            } catch (NumberFormatException e) { // GH-90000
                return Result.failure("Not a number [GH-90000]");
            }
        });

        assertThat(chained.isSuccess()).isTrue(); // GH-90000
        assertThat(chained.get()).isEqualTo(42); // GH-90000
    }

    @Test
    @DisplayName("flatMap should propagate failure [GH-90000]")
    void flatMapShouldPropagateFailure() { // GH-90000
        Result<String, String> result = Result.failure("original error [GH-90000]");

        Result<Integer, String> chained = result.flatMap(s -> Result.success(Integer.parseInt(s))); // GH-90000

        assertThat(chained.isFailure()).isTrue(); // GH-90000
        assertThat(chained.getError()).isEqualTo("original error [GH-90000]");
    }

    @Test
    @DisplayName("toOptional should return Optional with value on success [GH-90000]")
    void toOptionalShouldReturnOptionalWithValueOnSuccess() { // GH-90000
        Result<String, String> result = Result.success("value [GH-90000]");

        Optional<String> optional = result.toOptional(); // GH-90000

        assertThat(optional).isPresent().contains("value [GH-90000]");
    }

    @Test
    @DisplayName("toOptional should return empty Optional on failure [GH-90000]")
    void toOptionalShouldReturnEmptyOptionalOnFailure() { // GH-90000
        Result<String, String> result = Result.failure("error [GH-90000]");

        Optional<String> optional = result.toOptional(); // GH-90000

        assertThat(optional).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("ofNullable should create success for non-null value [GH-90000]")
    void ofNullableShouldCreateSuccessForNonNullValue() { // GH-90000
        Result<String, String> result = Result.ofNullable("value", "was null"); // GH-90000

        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.get()).isEqualTo("value [GH-90000]");
    }

    @Test
    @DisplayName("ofNullable should create failure for null value [GH-90000]")
    void ofNullableShouldCreateFailureForNullValue() { // GH-90000
        Result<String, String> result = Result.ofNullable(null, "was null"); // GH-90000

        assertThat(result.isFailure()).isTrue(); // GH-90000
        assertThat(result.getError()).isEqualTo("was null [GH-90000]");
    }

    @Test
    @DisplayName("of should catch exceptions and return failure [GH-90000]")
    void ofShouldCatchExceptionsAndReturnFailure() { // GH-90000
        Result<Integer, Exception> result = Result.of(() -> { // GH-90000
            throw new RuntimeException("test error [GH-90000]");
        });

        assertThat(result.isFailure()).isTrue(); // GH-90000
        assertThat(result.getError()).isInstanceOf(RuntimeException.class); // GH-90000
        assertThat(result.getError().getMessage()).isEqualTo("test error [GH-90000]");
    }

    @Test
    @DisplayName("of should return success for successful computation [GH-90000]")
    void ofShouldReturnSuccessForSuccessfulComputation() { // GH-90000
        Result<Integer, Exception> result = Result.of(() -> 42); // GH-90000

        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.get()).isEqualTo(42); // GH-90000
    }
}

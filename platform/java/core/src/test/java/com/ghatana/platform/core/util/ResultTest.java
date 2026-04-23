package com.ghatana.platform.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Result")
class ResultTest {

    @Test
    @DisplayName("success should create a successful result")
    void successShouldCreateSuccessfulResult() { // GH-90000
        Result<String, String> result = Result.success("value");

        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.isFailure()).isFalse(); // GH-90000
        assertThat(result.get()).isEqualTo("value");
    }

    @Test
    @DisplayName("failure should create a failed result")
    void failureShouldCreateFailedResult() { // GH-90000
        Result<String, String> result = Result.failure("error");

        assertThat(result.isSuccess()).isFalse(); // GH-90000
        assertThat(result.isFailure()).isTrue(); // GH-90000
        assertThat(result.getError()).isEqualTo("error");
    }

    @Test
    @DisplayName("get on failure should throw NoSuchElementException")
    void getOnFailureShouldThrow() { // GH-90000
        Result<String, String> result = Result.failure("error");

        assertThatThrownBy(result::get) // GH-90000
                .isInstanceOf(NoSuchElementException.class) // GH-90000
                .hasMessageContaining("error");
    }

    @Test
    @DisplayName("getError on success should throw NoSuchElementException")
    void getErrorOnSuccessShouldThrow() { // GH-90000
        Result<String, String> result = Result.success("value");

        assertThatThrownBy(result::getError) // GH-90000
                .isInstanceOf(NoSuchElementException.class); // GH-90000
    }

    @Test
    @DisplayName("getOrElse should return value on success")
    void getOrElseShouldReturnValueOnSuccess() { // GH-90000
        Result<String, String> result = Result.success("value");

        assertThat(result.getOrElse("default")).isEqualTo("value");
    }

    @Test
    @DisplayName("getOrElse should return default on failure")
    void getOrElseShouldReturnDefaultOnFailure() { // GH-90000
        Result<String, String> result = Result.failure("error");

        assertThat(result.getOrElse("default")).isEqualTo("default");
    }

    @Test
    @DisplayName("map should transform success value")
    void mapShouldTransformSuccessValue() { // GH-90000
        Result<String, String> result = Result.success("hello");

        Result<Integer, String> mapped = result.map(String::length); // GH-90000

        assertThat(mapped.isSuccess()).isTrue(); // GH-90000
        assertThat(mapped.get()).isEqualTo(5); // GH-90000
    }

    @Test
    @DisplayName("map should not transform failure")
    void mapShouldNotTransformFailure() { // GH-90000
        Result<String, String> result = Result.failure("error");

        Result<Integer, String> mapped = result.map(String::length); // GH-90000

        assertThat(mapped.isFailure()).isTrue(); // GH-90000
        assertThat(mapped.getError()).isEqualTo("error");
    }

    @Test
    @DisplayName("mapError should transform error")
    void mapErrorShouldTransformError() { // GH-90000
        Result<String, String> result = Result.failure("error");

        Result<String, Integer> mapped = result.mapError(String::length); // GH-90000

        assertThat(mapped.isFailure()).isTrue(); // GH-90000
        assertThat(mapped.getError()).isEqualTo(5); // GH-90000
    }

    @Test
    @DisplayName("flatMap should chain successful results")
    void flatMapShouldChainSuccessfulResults() { // GH-90000
        Result<String, String> result = Result.success("42");

        Result<Integer, String> chained = result.flatMap(s -> { // GH-90000
            try {
                return Result.success(Integer.parseInt(s)); // GH-90000
            } catch (NumberFormatException e) { // GH-90000
                return Result.failure("Not a number");
            }
        });

        assertThat(chained.isSuccess()).isTrue(); // GH-90000
        assertThat(chained.get()).isEqualTo(42); // GH-90000
    }

    @Test
    @DisplayName("flatMap should propagate failure")
    void flatMapShouldPropagateFailure() { // GH-90000
        Result<String, String> result = Result.failure("original error");

        Result<Integer, String> chained = result.flatMap(s -> Result.success(Integer.parseInt(s))); // GH-90000

        assertThat(chained.isFailure()).isTrue(); // GH-90000
        assertThat(chained.getError()).isEqualTo("original error");
    }

    @Test
    @DisplayName("toOptional should return Optional with value on success")
    void toOptionalShouldReturnOptionalWithValueOnSuccess() { // GH-90000
        Result<String, String> result = Result.success("value");

        Optional<String> optional = result.toOptional(); // GH-90000

        assertThat(optional).isPresent().contains("value");
    }

    @Test
    @DisplayName("toOptional should return empty Optional on failure")
    void toOptionalShouldReturnEmptyOptionalOnFailure() { // GH-90000
        Result<String, String> result = Result.failure("error");

        Optional<String> optional = result.toOptional(); // GH-90000

        assertThat(optional).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("ofNullable should create success for non-null value")
    void ofNullableShouldCreateSuccessForNonNullValue() { // GH-90000
        Result<String, String> result = Result.ofNullable("value", "was null"); // GH-90000

        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.get()).isEqualTo("value");
    }

    @Test
    @DisplayName("ofNullable should create failure for null value")
    void ofNullableShouldCreateFailureForNullValue() { // GH-90000
        Result<String, String> result = Result.ofNullable(null, "was null"); // GH-90000

        assertThat(result.isFailure()).isTrue(); // GH-90000
        assertThat(result.getError()).isEqualTo("was null");
    }

    @Test
    @DisplayName("of should catch exceptions and return failure")
    void ofShouldCatchExceptionsAndReturnFailure() { // GH-90000
        Result<Integer, Exception> result = Result.of(() -> { // GH-90000
            throw new RuntimeException("test error");
        });

        assertThat(result.isFailure()).isTrue(); // GH-90000
        assertThat(result.getError()).isInstanceOf(RuntimeException.class); // GH-90000
        assertThat(result.getError().getMessage()).isEqualTo("test error");
    }

    @Test
    @DisplayName("of should return success for successful computation")
    void ofShouldReturnSuccessForSuccessfulComputation() { // GH-90000
        Result<Integer, Exception> result = Result.of(() -> 42); // GH-90000

        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.get()).isEqualTo(42); // GH-90000
    }
}

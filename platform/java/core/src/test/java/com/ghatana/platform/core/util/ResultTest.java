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
    void successShouldCreateSuccessfulResult() {
        Result<String, String> result = Result.success("value");
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isFailure()).isFalse();
        assertThat(result.get()).isEqualTo("value");
    }
    
    @Test
    @DisplayName("failure should create a failed result")
    void failureShouldCreateFailedResult() {
        Result<String, String> result = Result.failure("error");
        
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).isEqualTo("error");
    }
    
    @Test
    @DisplayName("get on failure should throw NoSuchElementException")
    void getOnFailureShouldThrow() {
        Result<String, String> result = Result.failure("error");
        
        assertThatThrownBy(result::get)
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("error");
    }
    
    @Test
    @DisplayName("getError on success should throw NoSuchElementException")
    void getErrorOnSuccessShouldThrow() {
        Result<String, String> result = Result.success("value");
        
        assertThatThrownBy(result::getError)
                .isInstanceOf(NoSuchElementException.class);
    }
    
    @Test
    @DisplayName("getOrElse should return value on success")
    void getOrElseShouldReturnValueOnSuccess() {
        Result<String, String> result = Result.success("value");
        
        assertThat(result.getOrElse("default")).isEqualTo("value");
    }
    
    @Test
    @DisplayName("getOrElse should return default on failure")
    void getOrElseShouldReturnDefaultOnFailure() {
        Result<String, String> result = Result.failure("error");
        
        assertThat(result.getOrElse("default")).isEqualTo("default");
    }
    
    @Test
    @DisplayName("map should transform success value")
    void mapShouldTransformSuccessValue() {
        Result<String, String> result = Result.success("hello");
        
        Result<Integer, String> mapped = result.map(String::length);
        
        assertThat(mapped.isSuccess()).isTrue();
        assertThat(mapped.get()).isEqualTo(5);
    }
    
    @Test
    @DisplayName("map should not transform failure")
    void mapShouldNotTransformFailure() {
        Result<String, String> result = Result.failure("error");
        
        Result<Integer, String> mapped = result.map(String::length);
        
        assertThat(mapped.isFailure()).isTrue();
        assertThat(mapped.getError()).isEqualTo("error");
    }
    
    @Test
    @DisplayName("mapError should transform error")
    void mapErrorShouldTransformError() {
        Result<String, String> result = Result.failure("error");
        
        Result<String, Integer> mapped = result.mapError(String::length);
        
        assertThat(mapped.isFailure()).isTrue();
        assertThat(mapped.getError()).isEqualTo(5);
    }
    
    @Test
    @DisplayName("flatMap should chain successful results")
    void flatMapShouldChainSuccessfulResults() {
        Result<String, String> result = Result.success("42");
        
        Result<Integer, String> chained = result.flatMap(s -> {
            try {
                return Result.success(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                return Result.failure("Not a number");
            }
        });
        
        assertThat(chained.isSuccess()).isTrue();
        assertThat(chained.get()).isEqualTo(42);
    }
    
    @Test
    @DisplayName("flatMap should propagate failure")
    void flatMapShouldPropagateFailure() {
        Result<String, String> result = Result.failure("original error");
        
        Result<Integer, String> chained = result.flatMap(s -> Result.success(Integer.parseInt(s)));
        
        assertThat(chained.isFailure()).isTrue();
        assertThat(chained.getError()).isEqualTo("original error");
    }
    
    @Test
    @DisplayName("toOptional should return Optional with value on success")
    void toOptionalShouldReturnOptionalWithValueOnSuccess() {
        Result<String, String> result = Result.success("value");
        
        Optional<String> optional = result.toOptional();
        
        assertThat(optional).isPresent().contains("value");
    }
    
    @Test
    @DisplayName("toOptional should return empty Optional on failure")
    void toOptionalShouldReturnEmptyOptionalOnFailure() {
        Result<String, String> result = Result.failure("error");
        
        Optional<String> optional = result.toOptional();
        
        assertThat(optional).isEmpty();
    }
    
    @Test
    @DisplayName("ofNullable should create success for non-null value")
    void ofNullableShouldCreateSuccessForNonNullValue() {
        Result<String, String> result = Result.ofNullable("value", "was null");
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.get()).isEqualTo("value");
    }
    
    @Test
    @DisplayName("ofNullable should create failure for null value")
    void ofNullableShouldCreateFailureForNullValue() {
        Result<String, String> result = Result.ofNullable(null, "was null");
        
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).isEqualTo("was null");
    }
    
    @Test
    @DisplayName("of should catch exceptions and return failure")
    void ofShouldCatchExceptionsAndReturnFailure() {
        Result<Integer, Exception> result = Result.of(() -> {
            throw new RuntimeException("test error");
        });
        
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).isInstanceOf(RuntimeException.class);
        assertThat(result.getError().getMessage()).isEqualTo("test error");
    }
    
    @Test
    @DisplayName("of should return success for successful computation")
    void ofShouldReturnSuccessForSuccessfulComputation() {
        Result<Integer, Exception> result = Result.of(() -> 42);
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.get()).isEqualTo(42);
    }
}

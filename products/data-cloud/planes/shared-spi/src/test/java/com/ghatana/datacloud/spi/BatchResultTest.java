package com.ghatana.datacloud.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BatchResult Tests")
class BatchResultTest {

    @Test
    @DisplayName("isFullySuccessful returns true when failureCount is 0")
    void isFullySuccessful_returnsTrueWhenNoFailures() {
        BatchResult<String> result = new BatchResult<>(5, 5, 0, List.of());
        assertTrue(result.isFullySuccessful());
    }

    @Test
    @DisplayName("isFullySuccessful returns false when there are failures")
    void isFullySuccessful_returnsFalseWhenFailuresExist() {
        BatchResult<String> result = new BatchResult<>(5, 3, 2, List.of());
        assertFalse(result.isFullySuccessful());
    }

    @Test
    @DisplayName("isPartiallySuccessful returns true when both success and failure")
    void isPartiallySuccessful_returnsTrueWhenMixed() {
        BatchResult<String> result = new BatchResult<>(5, 3, 2, List.of());
        assertTrue(result.isPartiallySuccessful());
    }

    @Test
    @DisplayName("isPartiallySuccessful returns false when all succeed")
    void isPartiallySuccessful_returnsFalseWhenAllSucceed() {
        BatchResult<String> result = new BatchResult<>(5, 5, 0, List.of());
        assertFalse(result.isPartiallySuccessful());
    }

    @Test
    @DisplayName("isPartiallySuccessful returns false when all fail")
    void isPartiallySuccessful_returnsFalseWhenAllFail() {
        BatchResult<String> result = new BatchResult<>(5, 0, 5, List.of());
        assertFalse(result.isPartiallySuccessful());
    }

    @Test
    @DisplayName("isCompleteFailure returns true when successCount is 0")
    void isCompleteFailure_returnsTrueWhenNoSuccesses() {
        BatchResult<String> result = new BatchResult<>(5, 0, 5, List.of());
        assertTrue(result.isCompleteFailure());
    }

    @Test
    @DisplayName("isCompleteFailure returns false when there are successes")
    void isCompleteFailure_returnsFalseWhenSuccessesExist() {
        BatchResult<String> result = new BatchResult<>(5, 3, 2, List.of());
        assertFalse(result.isCompleteFailure());
    }

    @Test
    @DisplayName("success factory creates fully successful result")
    void successFactory_createsFullySuccessfulResult() {
        BatchResult<String> result = BatchResult.success(10);
        assertEquals(10, result.totalCount());
        assertEquals(10, result.successCount());
        assertEquals(0, result.failureCount());
        assertTrue(result.isFullySuccessful());
        assertFalse(result.isPartiallySuccessful());
    }

    @Test
    @DisplayName("failure factory creates complete failure result")
    void failureFactory_createsCompleteFailureResult() {
        List<BatchError<String>> errors = List.of(
            new BatchError<>(0, "id1", "ERROR", "error1"),
            new BatchError<>(1, "id2", "ERROR", "error2")
        );
        BatchResult<String> result = BatchResult.failure(2, errors);
        assertEquals(2, result.totalCount());
        assertEquals(0, result.successCount());
        assertEquals(2, result.failureCount());
        assertTrue(result.isCompleteFailure());
        assertFalse(result.isFullySuccessful());
    }

    @Test
    @DisplayName("partial factory creates partially successful result")
    void partialFactory_createsPartiallySuccessfulResult() {
        List<BatchError<String>> errors = List.of(new BatchError<>(2, "id3", "ERROR", "error3"));
        BatchResult<String> result = BatchResult.partial(5, 3, 2, errors);
        assertEquals(5, result.totalCount());
        assertEquals(3, result.successCount());
        assertEquals(2, result.failureCount());
        assertTrue(result.isPartiallySuccessful());
        assertFalse(result.isFullySuccessful());
        assertFalse(result.isCompleteFailure());
    }

    @Test
    @DisplayName("DC-19: constructor throws on negative totalCount")
    void constructor_throwsOnNegativeTotalCount() {
        assertThrows(IllegalArgumentException.class,
            () -> new BatchResult<>(-1, 0, 0, List.of()));
    }

    @Test
    @DisplayName("DC-19: constructor throws on negative successCount")
    void constructor_throwsOnNegativeSuccessCount() {
        assertThrows(IllegalArgumentException.class,
            () -> new BatchResult<>(5, -1, 0, List.of()));
    }

    @Test
    @DisplayName("DC-19: constructor throws on negative failureCount")
    void constructor_throwsOnNegativeFailureCount() {
        assertThrows(IllegalArgumentException.class,
            () -> new BatchResult<>(5, 0, -1, List.of()));
    }

    @Test
    @DisplayName("DC-19: constructor throws when success + failure != total")
    void constructor_throwsWhenCountsMismatch() {
        assertThrows(IllegalArgumentException.class,
            () -> new BatchResult<>(5, 3, 1, List.of()));
    }

    @Test
    @DisplayName("DC-19: constructor throws when success + failure exceeds total")
    void constructor_throwsWhenCountsExceedTotal() {
        assertThrows(IllegalArgumentException.class,
            () -> new BatchResult<>(5, 4, 2, List.of()));
    }

    @Test
    @DisplayName("DC-19: constructor accepts valid counts")
    void constructor_acceptsValidCounts() {
        assertDoesNotThrow(() -> new BatchResult<>(5, 3, 2, List.of()));
        assertDoesNotThrow(() -> new BatchResult<>(5, 5, 0, List.of()));
        assertDoesNotThrow(() -> new BatchResult<>(5, 0, 5, List.of()));
    }

    @Test
    @DisplayName("DC-19: partial factory validates counts")
    void partialFactory_validatesCounts() {
        List<BatchError<String>> errors = List.of(new BatchError<>(0, "id1", "ERROR", "error1"));
        
        assertThrows(IllegalArgumentException.class,
            () -> BatchResult.partial(5, 3, 1, errors));
        
        assertDoesNotThrow(() -> BatchResult.partial(5, 3, 2, errors));
    }
}

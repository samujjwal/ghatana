package com.ghatana.datacloud.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BatchResult [GH-90000]")
class BatchResultTest {

    @Test
    @DisplayName("success result is fully successful and immutable [GH-90000]")
    void successResultIsFullySuccessfulAndImmutable() { // GH-90000
        BatchResult<String> result = BatchResult.success(3); // GH-90000

        assertThat(result.totalCount()).isEqualTo(3); // GH-90000
        assertThat(result.successCount()).isEqualTo(3); // GH-90000
        assertThat(result.failureCount()).isZero(); // GH-90000
        assertThat(result.errors()).isEmpty(); // GH-90000
        assertThat(result.isFullySuccessful()).isTrue(); // GH-90000
        assertThat(result.isPartiallySuccessful()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("failure result preserves typed item identifiers [GH-90000]")
    void failureResultPreservesTypedItemIdentifiers() { // GH-90000
        UUID failedId = UUID.randomUUID(); // GH-90000
        BatchError<UUID> error = new BatchError<>(1, failedId, "INSERT_ERROR", "duplicate key"); // GH-90000

        BatchResult<UUID> result = BatchResult.failure(2, List.of(error)); // GH-90000

        assertThat(result.totalCount()).isEqualTo(2); // GH-90000
        assertThat(result.failureCount()).isEqualTo(2); // GH-90000
        assertThat(result.errors()).singleElement().extracting(BatchError::itemId).isEqualTo(failedId); // GH-90000
        assertThat(result.isFullySuccessful()).isFalse(); // GH-90000
        assertThat(result.isPartiallySuccessful()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("partial result reports partial success [GH-90000]")
    void partialResultReportsPartialSuccess() { // GH-90000
        BatchResult<String> result = new BatchResult<>(3, 2, 1, // GH-90000
            List.of(new BatchError<>(0, "entity-1", "UPDATE_ERROR", "validation failed"))); // GH-90000

        assertThat(result.isFullySuccessful()).isFalse(); // GH-90000
        assertThat(result.isPartiallySuccessful()).isTrue(); // GH-90000
        assertThat(result.errors()).hasSize(1); // GH-90000
    }
}

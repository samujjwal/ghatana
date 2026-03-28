package com.ghatana.datacloud.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BatchResult")
class BatchResultTest {

    @Test
    @DisplayName("success result is fully successful and immutable")
    void successResultIsFullySuccessfulAndImmutable() {
        BatchResult<String> result = BatchResult.success(3);

        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.failureCount()).isZero();
        assertThat(result.errors()).isEmpty();
        assertThat(result.isFullySuccessful()).isTrue();
        assertThat(result.isPartiallySuccessful()).isFalse();
    }

    @Test
    @DisplayName("failure result preserves typed item identifiers")
    void failureResultPreservesTypedItemIdentifiers() {
        UUID failedId = UUID.randomUUID();
        BatchError<UUID> error = new BatchError<>(1, failedId, "INSERT_ERROR", "duplicate key");

        BatchResult<UUID> result = BatchResult.failure(2, List.of(error));

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(2);
        assertThat(result.errors()).singleElement().extracting(BatchError::itemId).isEqualTo(failedId);
        assertThat(result.isFullySuccessful()).isFalse();
        assertThat(result.isPartiallySuccessful()).isFalse();
    }

    @Test
    @DisplayName("partial result reports partial success")
    void partialResultReportsPartialSuccess() {
        BatchResult<String> result = new BatchResult<>(3, 2, 1,
            List.of(new BatchError<>(0, "entity-1", "UPDATE_ERROR", "validation failed")));

        assertThat(result.isFullySuccessful()).isFalse();
        assertThat(result.isPartiallySuccessful()).isTrue();
        assertThat(result.errors()).hasSize(1);
    }
}
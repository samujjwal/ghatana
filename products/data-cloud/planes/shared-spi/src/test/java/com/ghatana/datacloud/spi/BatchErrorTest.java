package com.ghatana.datacloud.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BatchError}.
 */
@DisplayName("BatchError")
class BatchErrorTest {

    @Test
    @DisplayName("constructor creates batch error with all fields")
    void constructor_createsBatchError() {
        BatchError<String> error = new BatchError<>(
                0,
                "item-123",
                "VALIDATION_ERROR",
                "Invalid field value"
        );

        assertThat(error.index()).isEqualTo(0);
        assertThat(error.itemId()).isEqualTo("item-123");
        assertThat(error.errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.errorMessage()).isEqualTo("Invalid field value");
    }

    @Test
    @DisplayName("constructor with integer itemId")
    void constructor_withIntegerItemId() {
        BatchError<Integer> error = new BatchError<>(
                1,
                42,
                "NOT_FOUND",
                "Item not found"
        );

        assertThat(error.index()).isEqualTo(1);
        assertThat(error.itemId()).isEqualTo(42);
        assertThat(error.errorCode()).isEqualTo("NOT_FOUND");
        assertThat(error.errorMessage()).isEqualTo("Item not found");
    }

    @Test
    @DisplayName("constructor with UUID itemId")
    void constructor_withUUIDItemId() {
        java.util.UUID id = java.util.UUID.randomUUID();
        BatchError<java.util.UUID> error = new BatchError<>(
                2,
                id,
                "PERMISSION_DENIED",
                "Access denied"
        );

        assertThat(error.index()).isEqualTo(2);
        assertThat(error.itemId()).isEqualTo(id);
        assertThat(error.errorCode()).isEqualTo("PERMISSION_DENIED");
        assertThat(error.errorMessage()).isEqualTo("Access denied");
    }

    @Test
    @DisplayName("record components are accessible")
    void recordComponents_areAccessible() {
        BatchError<String> error = new BatchError<>(
                5,
                "test-id",
                "TEST_ERROR",
                "Test message"
        );

        assertThat(error.index()).isEqualTo(5);
        assertThat(error.itemId()).isEqualTo("test-id");
        assertThat(error.errorCode()).isEqualTo("TEST_ERROR");
        assertThat(error.errorMessage()).isEqualTo("Test message");
    }
}

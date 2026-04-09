package com.ghatana.datacloud.spi;

import java.util.List;

/**
 * Shared result for batch operations across Data-Cloud SPI contracts.
 *
 * @param <TId> identifier type reported for per-item failures
 * @doc.type record
 * @doc.purpose Shared batch operation result contract for SPI interfaces
 * @doc.layer spi
 * @doc.pattern Value Object
 */
public record BatchResult<TId>(
    int totalCount,
    int successCount,
    int failureCount,
    List<BatchError<TId>> errors
) {
    public BatchResult {
        errors = errors != null ? List.copyOf(errors) : List.of();
    }

    public boolean isFullySuccessful() {
        return failureCount == 0;
    }

    public boolean isPartiallySuccessful() {
        return successCount > 0 && failureCount > 0;
    }

    public static <TId> BatchResult<TId> success(int count) {
        return new BatchResult<>(count, count, 0, List.of());
    }

    public static <TId> BatchResult<TId> failure(int count, List<BatchError<TId>> errors) {
        return new BatchResult<>(count, 0, count, errors);
    }
}

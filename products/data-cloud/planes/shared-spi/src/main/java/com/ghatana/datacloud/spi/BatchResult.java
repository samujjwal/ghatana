package com.ghatana.datacloud.spi;

import java.util.List;

/**
 * Shared result for batch operations across Data-Cloud SPI contracts.
 *
 * <p><b>DC-19: Batch Semantics</b><br>
 * This record represents the result of a batch operation with explicit partial success
 * semantics. By default, batch operations allow partial success - some items may succeed
 * while others fail. The result provides:
 * <ul>
 *   <li>totalCount: total number of items in the batch</li>
 *   <li>successCount: number of items that succeeded</li>
 *   <li>failureCount: number of items that failed</li>
 *   <li>errors: per-item error details for failed items</li>
 * </ul>
 *
 * <p><b>Transactional vs Partial Semantics</b><br>
 * For transactional (all-or-nothing) semantics, implementations should throw an exception
 * if any item fails, ensuring atomicity. For partial semantics, implementations should
 * return this BatchResult with detailed error information.
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
        // DC-19: Validate consistency
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount cannot be negative");
        }
        if (successCount < 0) {
            throw new IllegalArgumentException("successCount cannot be negative");
        }
        if (failureCount < 0) {
            throw new IllegalArgumentException("failureCount cannot be negative");
        }
        if (successCount + failureCount != totalCount) {
            throw new IllegalArgumentException(
                "successCount + failureCount must equal totalCount: " +
                successCount + " + " + failureCount + " != " + totalCount
            );
        }
    }

    /**
     * Returns true if all items in the batch succeeded (failureCount == 0).
     * Indicates complete success with no partial failures.
     */
    public boolean isFullySuccessful() {
        return failureCount == 0;
    }

    /**
     * Returns true if the batch had both successes and failures.
     * Indicates partial success semantics were applied.
     */
    public boolean isPartiallySuccessful() {
        return successCount > 0 && failureCount > 0;
    }

    /**
     * Returns true if all items in the batch failed (successCount == 0).
     * Indicates complete failure.
     */
    public boolean isCompleteFailure() {
        return successCount == 0 && failureCount > 0;
    }

    /**
     * Creates a BatchResult representing complete success of all items.
     *
     * @param count total number of items that succeeded
     * @return BatchResult with all items marked as successful
     */
    public static <TId> BatchResult<TId> success(int count) {
        return new BatchResult<>(count, count, 0, List.of());
    }

    /**
     * Creates a BatchResult representing complete failure of all items.
     *
     * @param count total number of items that failed
     * @param errors per-item error details
     * @return BatchResult with all items marked as failed
     */
    public static <TId> BatchResult<TId> failure(int count, List<BatchError<TId>> errors) {
        return new BatchResult<>(count, 0, count, errors);
    }

    /**
     * DC-19: Creates a BatchResult representing partial success.
     * Use this when some items succeeded and others failed.
     *
     * @param totalCount total number of items
     * @param successCount number of items that succeeded
     * @param failureCount number of items that failed
     * @param errors per-item error details for failed items
     * @return BatchResult with partial success
     */
    public static <TId> BatchResult<TId> partial(
        int totalCount,
        int successCount,
        int failureCount,
        List<BatchError<TId>> errors
    ) {
        return new BatchResult<>(totalCount, successCount, failureCount, errors);
    }
}

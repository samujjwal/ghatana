package com.ghatana.datacloud.spi;

/**
 * Shared error detail for failed items in batch operations.
 *
 * @param <TId> identifier type reported for the failed item
 * @doc.type record
 * @doc.purpose Shared batch operation error detail for SPI interfaces
 * @doc.layer spi
 * @doc.pattern Value Object
 */
public record BatchError<TId>(
    int index,
    TId itemId,
    String errorCode,
    String errorMessage
) {
}
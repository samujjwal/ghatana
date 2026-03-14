package com.ghatana.appplatform.eventstore.consumer;

import java.time.Instant;

/**
 * Captures the details of a consumer processing error for DLQ enrichment and metrics.
 *
 * @doc.type record
 * @doc.purpose Consumer error details for DLQ routing and observability (K05-012)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ConsumerError(
    ConsumerErrorType errorType,
    String message,
    String errorClass,
    String topic,
    int partition,
    long offset,
    int retryCount,
    Instant failedAt
) {
    public static ConsumerError transient_(String message, String topic, int partition, long offset, int retryCount) {
        return new ConsumerError(
            ConsumerErrorType.TRANSIENT, message, RuntimeException.class.getName(),
            topic, partition, offset, retryCount, Instant.now());
    }

    public static ConsumerError permanent(String message, Class<?> errorClass, String topic, int partition, long offset) {
        return new ConsumerError(
            ConsumerErrorType.PERMANENT, message, errorClass.getName(),
            topic, partition, offset, 0, Instant.now());
    }
}

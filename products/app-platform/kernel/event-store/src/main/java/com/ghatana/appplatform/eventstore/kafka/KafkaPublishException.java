package com.ghatana.appplatform.eventstore.kafka;

/**
 * Thrown when a Kafka publish operation fails permanently.
 *
 * <p>The {@link KafkaEventOutboxRelay} catches this, increments the attempt counter,
 * and retries on the next poll cycle. After {@code max_attempts} failures the event
 * is routed to the DLQ topic.
 *
 * @doc.type class
 * @doc.purpose Checked runtime exception for Kafka publish failures (K05-009)
 * @doc.layer product
 * @doc.pattern Exception
 */
public final class KafkaPublishException extends RuntimeException {

    public KafkaPublishException(String message, Throwable cause) {
        super(message, cause);
    }

    public KafkaPublishException(String message) {
        super(message);
    }
}

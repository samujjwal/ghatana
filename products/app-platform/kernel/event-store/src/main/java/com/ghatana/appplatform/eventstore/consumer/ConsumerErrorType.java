package com.ghatana.appplatform.eventstore.consumer;

/**
 * Classifies a consumer processing error as transient or permanent.
 *
 * <p>Classification drives the retry / DLQ routing strategy:
 * <ul>
 *   <li>{@code TRANSIENT} — retry up to the configured limit; then DLQ.</li>
 *   <li>{@code PERMANENT} — skip retries; route immediately to DLQ.</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Error classification for consumer retry/DLQ routing (K05-012)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum ConsumerErrorType {

    /**
     * Transient errors are expected to resolve on retry:
     * network timeouts, temporary downstream unavailability, lock contention.
     */
    TRANSIENT,

    /**
     * Permanent errors cannot be fixed by retrying:
     * deserialization failures, schema violations, poison-pill messages.
     */
    PERMANENT
}

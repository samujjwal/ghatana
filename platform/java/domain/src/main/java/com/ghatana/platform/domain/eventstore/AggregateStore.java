package com.ghatana.platform.domain.eventstore;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Aggregate-specific event store contract built on top of EventLogStore.
 *
 * <p>Provides aggregate-centric operations for event sourcing, including
 * reading events by aggregate ID, version checking, and optimistic concurrency.</p>
 *
 * @doc.type interface
 * @doc.purpose Aggregate event sourcing contract (KERNEL-P1)
 * @doc.layer platform
 * @doc.pattern Service Provider Interface, Event Sourcing
 */
public interface AggregateStore {

    /**
     * Append an event to an aggregate stream.
     *
     * @param tenant tenant context
     * @param aggregateId aggregate identifier
     * @param eventType event type
     * @param eventVersion event version
     * @param payload event payload
     * @param expectedVersion expected aggregate version for optimistic concurrency
     * @return Promise containing the new aggregate version
     */
    Promise<Long> appendEvent(
        TenantContext tenant,
        String aggregateId,
        String eventType,
        String eventVersion,
        byte[] payload,
        long expectedVersion
    );

    /**
     * Read all events for an aggregate.
     *
     * @param tenant tenant context
     * @param aggregateId aggregate identifier
     * @return Promise containing list of aggregate events
     */
    Promise<List<AggregateEvent>> readAggregateEvents(
        TenantContext tenant,
        String aggregateId
    );

    /**
     * Read events for an aggregate from a specific version.
     *
     * @param tenant tenant context
     * @param aggregateId aggregate identifier
     * @param fromVersion starting version
     * @return Promise containing list of aggregate events
     */
    Promise<List<AggregateEvent>> readAggregateEventsFromVersion(
        TenantContext tenant,
        String aggregateId,
        long fromVersion
    );

    /**
     * Get the current version of an aggregate.
     *
     * @param tenant tenant context
     * @param aggregateId aggregate identifier
     * @return Promise containing current version or empty if aggregate not found
     */
    Promise<Optional<Long>> getAggregateVersion(
        TenantContext tenant,
        String aggregateId
    );

    /**
     * Check if an aggregate exists.
     *
     * @param tenant tenant context
     * @param aggregateId aggregate identifier
     * @return Promise containing true if aggregate exists
     */
    Promise<Boolean> aggregateExists(
        TenantContext tenant,
        String aggregateId
    );

    /**
     * Delete all events for an aggregate (for GDPR/DSAR compliance).
     *
     * @param tenant tenant context
     * @param aggregateId aggregate identifier
     * @return Promise that completes when deletion is finished
     */
    Promise<Void> deleteAggregate(
        TenantContext tenant,
        String aggregateId
    );

    /**
     * Aggregate event with version information.
     *
     * @doc.type record
     * @doc.purpose Aggregate event with version metadata
     * @doc.layer platform
     * @doc.pattern Value Object
     */
    record AggregateEvent(
        String aggregateId,
        long version,
        String eventType,
        String eventVersion,
        Instant timestamp,
        byte[] payload,
        String eventId
    ) {
        public AggregateEvent {
            if (aggregateId == null || aggregateId.isBlank()) {
                throw new IllegalArgumentException("aggregateId must not be blank");
            }
            if (version < 0) {
                throw new IllegalArgumentException("version must be non-negative");
            }
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("eventType must not be blank");
            }
            if (eventVersion == null || eventVersion.isBlank()) {
                throw new IllegalArgumentException("eventVersion must not be blank");
            }
            if (timestamp == null) {
                throw new IllegalArgumentException("timestamp must not be null");
            }
            if (payload == null) {
                throw new IllegalArgumentException("payload must not be null");
            }
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException("eventId must not be blank");
            }
        }

        /**
         * Creates a new AggregateEvent with the given payload.
         */
        public static AggregateEvent of(
            String aggregateId,
            long version,
            String eventType,
            String eventVersion,
            byte[] payload
        ) {
            return new AggregateEvent(
                aggregateId,
                version,
                eventType,
                eventVersion,
                Instant.now(),
                payload,
                java.util.UUID.randomUUID().toString()
            );
        }
    }
}

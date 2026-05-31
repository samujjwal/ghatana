package com.ghatana.datacloud.spi;

import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Event Log Store SPI - Append-only event log storage interface.
 *
 * <p>This interface is owned by Data-Cloud and provides the storage layer
 * that AEP's EventCloud can use for persistence. Implementations include:
 * <ul>
 *   <li>PostgreSQL (durable storage)</li>
 *   <li>Kafka (streaming)</li>
 *   <li>In-memory (testing)</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Append-only event log storage abstraction
 * @doc.layer spi
 * @doc.pattern Service Provider Interface, Event Store
 * @since 1.0.0
 */
public interface EventLogStore {

    // ==================== Append Operations ====================

    /**
     * Append an event entry to the log.
     *
     * @param tenant tenant context
     * @param entry event entry to append
     * @return promise of assigned offset
     */
    Promise<Offset> append(TenantContext tenant, EventEntry entry);

    /**
     * Append multiple event entries atomically.
     *
     * @param tenant tenant context
     * @param entries event entries to append
     * @return promise of assigned offsets
     */
    Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries);

    // ==================== Read Operations ====================

    /**
     * Read events from a specific offset.
     *
     * @param tenant tenant context
     * @param from starting offset (inclusive)
     * @param limit maximum number of events to read
     * @return promise of event entries
     */
    Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit);

    /**
     * Read events within a time range.
     *
     * @param tenant tenant context
     * @param startTime start time (inclusive)
     * @param endTime end time (exclusive)
     * @param limit maximum number of events
     * @return promise of event entries
     */
    Promise<List<EventEntry>> readByTimeRange(
        TenantContext tenant,
        Instant startTime,
        Instant endTime,
        int limit
    );

    /**
     * Read events by type.
     *
     * @param tenant tenant context
     * @param eventType event type name
     * @param from starting offset
     * @param limit maximum number of events
     * @return promise of event entries
     */
    Promise<List<EventEntry>> readByType(
        TenantContext tenant,
        String eventType,
        Offset from,
        int limit
    );

    // ==================== Offset Management ====================

    /**
     * Get the latest offset for a tenant.
     *
     * @param tenant tenant context
     * @return promise of latest offset
     */
    Promise<Offset> getLatestOffset(TenantContext tenant);

    /**
     * Get the earliest offset for a tenant.
     *
     * @param tenant tenant context
     * @return promise of earliest offset
     */
    Promise<Offset> getEarliestOffset(TenantContext tenant);

    // ==================== Streaming ====================

    /**
     * Tail events from a specific offset.
     *
     * @param tenant tenant context
     * @param from starting offset
     * @param handler event handler
     * @return promise of subscription for cancellation
     */
    Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler);

    // ==================== Checkpoint Management (P3-03) ====================

    /**
     * Store a consumer checkpoint for a named stream.
     *
     * <p>P3-03: Checkpoints enable exactly-once processing semantics by tracking
     * consumer progress through the event log. Each checkpoint is scoped to a
     * tenant and stream, allowing multiple consumers to track independent positions.
     *
     * <p>P3-03: This method is deprecated in favor of {@link #commitCheckpoint} which
     * provides idempotency guarantees and consumer group scoping.
     *
     * @param tenant tenant context
     * @param stream stream identifier (consumer group name)
     * @param offset offset to checkpoint
     * @return promise of true if checkpoint stored successfully
     * @deprecated Use {@link #commitCheckpoint} for production-grade checkpoint management
     */
    @Deprecated(since = "2026.05", forRemoval = true)
    default Promise<Boolean> storeCheckpoint(TenantContext tenant, String stream, Offset offset) {
        return Promise.ofException(new UnsupportedOperationException(
            "storeCheckpoint() is deprecated. Use commitCheckpoint() with consumer group and idempotency key for production-grade checkpoint management."));
    }

    /**
     * Retrieve the last stored checkpoint for a named stream.
     *
     * <p>P3-03: Returns the offset last committed by the consumer for this stream.
     * If no checkpoint exists, returns the earliest offset.
     *
     * <p>P3-03: This method is deprecated in favor of {@link #readCheckpoint} which
     * provides consumer group scoping and structured checkpoint metadata.
     *
     * @param tenant tenant context
     * @param stream stream identifier (consumer group name)
     * @return promise of offset (earliest if no checkpoint exists)
     * @deprecated Use {@link #readCheckpoint} for production-grade checkpoint reading
     */
    @Deprecated(since = "2026.05", forRemoval = true)
    default Promise<Offset> getCheckpoint(TenantContext tenant, String stream) {
        return Promise.ofException(new UnsupportedOperationException(
            "getCheckpoint() is deprecated. Use readCheckpoint() with consumer group for production-grade checkpoint management."));
    }

    /**
     * Delete a checkpoint for a named stream.
     *
     * <p>P3-03: Removes the stored checkpoint, causing the consumer to restart
     * from the earliest offset on next read.
     *
     * @param tenant tenant context
     * @param stream stream identifier (consumer group name)
     * @return promise of true if checkpoint was deleted
     */
    default Promise<Boolean> deleteCheckpoint(TenantContext tenant, String stream) {
        return Promise.ofException(new UnsupportedOperationException(
            "deleteCheckpoint() requires consumer group context. Use deleteCheckpoint(TenantContext, String, String) for production-grade checkpoint management."));
    }

    /**
     * Get all checkpoints for a tenant.
     *
     * <p>P3-03: Administrative API for monitoring consumer progress across all streams.
     *
     * @param tenant tenant context
     * @return promise of map from stream name to checkpoint offset
     * @deprecated Use {@link #getAllCheckpointsWithMetadata} for production-grade checkpoint monitoring
     */
    @Deprecated(since = "2026.05", forRemoval = true)
    default Promise<Map<String, Offset>> getAllCheckpoints(TenantContext tenant) {
        return Promise.ofException(new UnsupportedOperationException(
            "getAllCheckpoints() is deprecated. Use getAllCheckpointsWithMetadata() for production-grade checkpoint monitoring."));
    }

    /**
     * Read a checkpoint for a specific consumer group.
     *
     * <p>P3-03: Returns structured checkpoint metadata including offset,
     * timestamp, and consumer group information.
     *
     * @param tenant tenant context
     * @param stream stream identifier
     * @param consumerGroup consumer group name
     * @return promise of checkpoint (empty if no checkpoint exists)
     */
    default Promise<Optional<Checkpoint>> readCheckpoint(TenantContext tenant, String stream, String consumerGroup) {
        return Promise.ofException(new UnsupportedOperationException(
            "readCheckpoint() must be implemented by durable event store providers."));
    }

    /**
     * Commit a checkpoint with idempotency guarantees.
     *
     * <p>P3-03: Idempotent checkpoint commit that:
     * <ul>
     *   <li>Stores the offset for the tenant/stream/consumer-group tuple</li>
     *   <li>Uses idempotency key to prevent duplicate commits</li>
     *   <li>Returns the committed checkpoint metadata</li>
     * </ul>
     *
     * @param tenant tenant context
     * @param stream stream identifier
     * @param consumerGroup consumer group name
     * @param offset offset to commit
     * @param idempotencyKey idempotency key for safe retry
     * @return promise of committed checkpoint
     */
    default Promise<Checkpoint> commitCheckpoint(TenantContext tenant, String stream, String consumerGroup, Offset offset, String idempotencyKey) {
        return Promise.ofException(new UnsupportedOperationException(
            "commitCheckpoint() must be implemented by durable event store providers."));
    }

    /**
     * Delete a checkpoint for a specific consumer group.
     *
     * <p>P3-03: Removes the stored checkpoint for the tenant/stream/consumer-group tuple.
     *
     * @param tenant tenant context
     * @param stream stream identifier
     * @param consumerGroup consumer group name
     * @return promise of true if checkpoint was deleted
     */
    default Promise<Boolean> deleteCheckpoint(TenantContext tenant, String stream, String consumerGroup) {
        return Promise.ofException(new UnsupportedOperationException(
            "deleteCheckpoint() must be implemented by durable event store providers."));
    }

    /**
     * Get all checkpoints with metadata for a tenant.
     *
     * <p>P3-03: Administrative API for monitoring consumer progress across all streams
     * with structured checkpoint metadata.
     *
     * @param tenant tenant context
     * @return promise of map from stream/consumer-group to checkpoint metadata
     */
    default Promise<Map<String, Checkpoint>> getAllCheckpointsWithMetadata(TenantContext tenant) {
        return Promise.ofException(new UnsupportedOperationException(
            "getAllCheckpointsWithMetadata() must be implemented by durable event store providers."));
    }

    /**
     * Replay events with comprehensive replay semantics.
     *
     * <p>P3-03: Production-grade replay supporting:
     * <ul>
     *   <li>Bounded offset ranges with inclusive semantics</li>
     *   <li>Event type filtering</li>
     *   <li>Replay mode selection (AT_LEAST_ONCE, EXACTLY_ONCE, AT_MOST_ONCE)</li>
     *   <li>Idempotency keys for safe retry</li>
     *   <li>Consumer group scoping</li>
     * </ul>
     *
     * @param tenant tenant context
     * @param spec replay specification
     * @return promise of event entries in the specified range
     */
    default Promise<List<EventEntry>> replay(TenantContext tenant, ReplaySpec spec) {
        return Promise.ofException(new UnsupportedOperationException(
            "replay() must be implemented by durable event store providers."));
    }

    /**
     * Unsubscribe from a tail subscription.
     *
     * <p>P3-03: Properly unregisters the listener from the event store,
     * not just ignoring callbacks.
     *
     * @param tenant tenant context
     * @param subscriptionId subscription identifier
     * @return promise completing when unsubscribed
     */
    default Promise<Void> unsubscribe(TenantContext tenant, SubscriptionId subscriptionId) {
        return Promise.ofException(new UnsupportedOperationException(
            "unsubscribe() must be implemented by event store providers with proper listener management."));
    }

    // ==================== Supporting Types ====================

    /**
     * Event entry for storage.
     *
     * <p><b>DC-20:</b> Core event envelope fields promoted from opaque headers to first-class
     * queryable storage fields. The following fields are now first-class and queryable:
     * <ul>
     *   <li>correlationId: Correlates related events across the system</li>
     *   <li>causationId: Traces causal chain of events</li>
     *   <li>source: Origin system/component that generated the event</li>
     *   <li>userId: User who triggered the event (if applicable)</li>
     * </ul>
     * The headers map remains for custom application-specific metadata.
     */
    record EventEntry(
        UUID eventId,
        String eventType,
        String eventVersion,
        Instant timestamp,
        ByteBuffer payload,
        String contentType,
        Map<String, String> headers,
        Optional<String> idempotencyKey,
        // DC-20: Promoted core fields from opaque headers to first-class queryable fields
        Optional<String> correlationId,
        Optional<String> causationId,
        Optional<String> source,
        Optional<String> userId
    ) {
        public EventEntry {
            Objects.requireNonNull(eventId, "eventId required");
            Objects.requireNonNull(eventType, "eventType required");
            Objects.requireNonNull(timestamp, "timestamp required");
            Objects.requireNonNull(payload, "payload required");
            eventVersion = eventVersion != null ? eventVersion : "1.0.0";
            contentType = contentType != null ? contentType : "application/json";
            headers = headers != null ? Map.copyOf(headers) : Map.of();
            idempotencyKey = idempotencyKey != null ? idempotencyKey : Optional.empty();
            // DC-20: Initialize optional fields
            correlationId = correlationId != null ? correlationId : Optional.empty();
            causationId = causationId != null ? causationId : Optional.empty();
            source = source != null ? source : Optional.empty();
            userId = userId != null ? userId : Optional.empty();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private UUID eventId = UUID.randomUUID();
            private String eventType;
            private String eventVersion = "1.0.0";
            private Instant timestamp = Instant.now();
            private ByteBuffer payload = ByteBuffer.allocate(0);
            private String contentType = "application/json";
            private Map<String, String> headers = Map.of();
            private Optional<String> idempotencyKey = Optional.empty();
            // DC-20: Builder fields for promoted core fields
            private Optional<String> correlationId = Optional.empty();
            private Optional<String> causationId = Optional.empty();
            private Optional<String> source = Optional.empty();
            private Optional<String> userId = Optional.empty();

            public Builder eventId(UUID eventId) {
                this.eventId = eventId;
                return this;
            }

            public Builder eventType(String eventType) {
                this.eventType = eventType;
                return this;
            }

            public Builder eventVersion(String eventVersion) {
                this.eventVersion = eventVersion;
                return this;
            }

            public Builder timestamp(Instant timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public Builder payload(ByteBuffer payload) {
                this.payload = payload;
                return this;
            }

            public Builder payload(byte[] payload) {
                this.payload = ByteBuffer.wrap(payload);
                return this;
            }

            public Builder payload(String payload) {
                this.payload = ByteBuffer.wrap(payload.getBytes());
                return this;
            }

            public Builder contentType(String contentType) {
                this.contentType = contentType;
                return this;
            }

            public Builder headers(Map<String, String> headers) {
                this.headers = headers;
                return this;
            }

            public Builder idempotencyKey(String key) {
                this.idempotencyKey = Optional.ofNullable(key);
                return this;
            }

            // DC-20: Builder methods for promoted core fields
            public Builder correlationId(String correlationId) {
                this.correlationId = Optional.ofNullable(correlationId);
                return this;
            }

            public Builder causationId(String causationId) {
                this.causationId = Optional.ofNullable(causationId);
                return this;
            }

            public Builder source(String source) {
                this.source = Optional.ofNullable(source);
                return this;
            }

            public Builder userId(String userId) {
                this.userId = Optional.ofNullable(userId);
                return this;
            }

            public EventEntry build() {
                return new EventEntry(
                    eventId, eventType, eventVersion, timestamp,
                    payload, contentType, headers, idempotencyKey,
                    correlationId, causationId, source, userId
                );
            }
        }
    }

    /**
     * Subscription handle for cancellation.
     */
    interface Subscription {
        void cancel();
        boolean isCancelled();
        
        /**
         * Get the subscription ID for unsubscribe operations.
         *
         * @return subscription identifier
         */
        default SubscriptionId getId() {
            return new SubscriptionId(UUID.randomUUID().toString());
        }
    }

    /**
     * Subscription identifier for unsubscribe operations.
     *
     * <p>P3-03: Unique identifier for a tail subscription that can be used
     * to properly unregister the listener from the event store.
     */
    record SubscriptionId(String value) {
        public SubscriptionId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("subscription ID is required");
            }
        }

        public static SubscriptionId of(String value) {
            return new SubscriptionId(value);
        }
    }

    /**
     * Structured checkpoint with metadata.
     *
     * <p>P3-03: Contains checkpoint offset, timestamp, consumer group,
     * and idempotency key for production-grade checkpoint management.
     */
    record Checkpoint(
        String stream,
        String consumerGroup,
        Offset offset,
        java.time.Instant timestamp,
        String idempotencyKey
    ) {
        public Checkpoint {
            if (stream == null || stream.isBlank()) {
                throw new IllegalArgumentException("stream is required");
            }
            if (consumerGroup == null || consumerGroup.isBlank()) {
                throw new IllegalArgumentException("consumerGroup is required");
            }
            timestamp = timestamp != null ? timestamp : java.time.Instant.now();
        }
    }

    /**
     * Replay specification for comprehensive replay semantics.
     *
     * <p>P3-03: Enables production-grade event replay with:
     * <ul>
     *   <li>Bounded offset ranges (inclusive fromOffset, inclusive toOffset or -1 for latest)</li>
     *   <li>Event type filtering for selective replay</li>
     *   <li>Replay mode selection (AT_LEAST_ONCE, EXACTLY_ONCE, AT_MOST_ONCE)</li>
     *   <li>Idempotency keys for safe retry semantics</li>
     *   <li>Consumer group scoping for independent consumer tracking</li>
     * </ul>
     */
    record ReplaySpec(
        Offset fromOffset,
        Offset toOffset,
        List<String> eventTypes,
        ReplayMode replayMode,
        String idempotencyKey,
        String consumerGroup
    ) {
        public ReplaySpec {
            eventTypes = eventTypes != null ? List.copyOf(eventTypes) : List.of();
            replayMode = replayMode != null ? replayMode : ReplayMode.AT_LEAST_ONCE;
            consumerGroup = consumerGroup != null && !consumerGroup.isBlank() ? consumerGroup : "default";
        }

        public static ReplaySpec fromOffset(Offset fromOffset) {
            return new ReplaySpec(fromOffset, Offset.of(-1), List.of(), ReplayMode.AT_LEAST_ONCE, null, "default");
        }

        public static ReplaySpec bounded(Offset fromOffset, Offset toOffset) {
            return new ReplaySpec(fromOffset, toOffset, List.of(), ReplayMode.AT_LEAST_ONCE, null, "default");
        }

        public static ReplaySpec filtered(Offset fromOffset, Offset toOffset, List<String> eventTypes) {
            return new ReplaySpec(fromOffset, toOffset, eventTypes, ReplayMode.AT_LEAST_ONCE, null, "default");
        }

        public static ReplaySpec withIdempotency(Offset fromOffset, Offset toOffset, String idempotencyKey) {
            return new ReplaySpec(fromOffset, toOffset, List.of(), ReplayMode.EXACTLY_ONCE, idempotencyKey, "default");
        }

        public static ReplaySpec forConsumerGroup(Offset fromOffset, Offset toOffset, String consumerGroup) {
            return new ReplaySpec(fromOffset, toOffset, List.of(), ReplayMode.AT_LEAST_ONCE, null, consumerGroup);
        }
    }

    /**
     * Replay mode semantics.
     */
    enum ReplayMode {
        /**
         * At-least-once semantics: events may be delivered multiple times on failure/retry.
         * Consumers must be idempotent.
         */
        AT_LEAST_ONCE,
        /**
         * Exactly-once semantics: events are delivered exactly once using idempotency keys.
         * Requires idempotencyKey to be set.
         */
        EXACTLY_ONCE,
        /**
         * At-most-once semantics: events may be lost on failure but never duplicated.
         */
        AT_MOST_ONCE
    }
}

package com.ghatana.platform.domain.eventstore;

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
 * Platform event log storage contract.
 *
 * <h2>Offset semantics</h2>
 * <p>Offsets are <strong>per-tenant</strong>. An {@link Offset} returned from
 * {@link #append} or {@link #appendBatch} is meaningful only within the scope of
 * the same {@link TenantContext} that produced it.  Callers MUST NOT
 * use an offset obtained for tenant A when reading from tenant B.</p>
 *
 * <p>Implementations MUST enforce this contract by scoping every storage access
 * to the tenant identifier supplied in {@link TenantContext#tenantId()} (e.g.
 * via a {@code WHERE tenant_id = ?} predicate in SQL, or a per-tenant map key
 * in memory).  Global, cross-tenant offsets are explicitly not supported and
 * constitute a data-isolation violation.</p>
 *
 * @doc.type interface
 * @doc.purpose Platform-owned append-only event log abstraction with per-tenant offset scoping
 * @doc.layer platform
 * @doc.pattern Service Provider Interface, Event Store
 */
public interface EventLogStore {

    Promise<Offset> append(TenantContext tenant, EventEntry entry);

    Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries);

    Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit);

    Promise<List<EventEntry>> readByTimeRange(
        TenantContext tenant,
        Instant startTime,
        Instant endTime,
        int limit
    );

    Promise<List<EventEntry>> readByType(
        TenantContext tenant,
        String eventType,
        Offset from,
        int limit
    );

    Promise<Offset> getLatestOffset(TenantContext tenant);

    Promise<Offset> getEarliestOffset(TenantContext tenant);

    Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler);

    // ==================== Checkpoint Management ====================

    /**
     * Store a consumer checkpoint for a named stream.
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
     * @param tenant tenant context
     * @param subscriptionId subscription identifier
     * @return promise completing when unsubscribed
     */
    default Promise<Void> unsubscribe(TenantContext tenant, SubscriptionId subscriptionId) {
        return Promise.ofException(new UnsupportedOperationException(
            "unsubscribe() must be implemented by event store providers with proper listener management."));
    }

    // ==================== Supporting Types ====================

    record EventEntry(
        UUID eventId,
        String eventType,
        String eventVersion,
        Instant timestamp,
        ByteBuffer payload,
        String contentType,
        Map<String, String> headers,
        Optional<String> idempotencyKey,
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
     */
    record Checkpoint(
        String stream,
        String consumerGroup,
        Offset offset,
        Instant timestamp,
        String idempotencyKey
    ) {
        public Checkpoint {
            if (stream == null || stream.isBlank()) {
                throw new IllegalArgumentException("stream is required");
            }
            if (consumerGroup == null || consumerGroup.isBlank()) {
                throw new IllegalArgumentException("consumerGroup is required");
            }
            timestamp = timestamp != null ? timestamp : Instant.now();
        }
    }

    /**
     * Replay specification for comprehensive replay semantics.
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
        AT_LEAST_ONCE,
        EXACTLY_ONCE,
        AT_MOST_ONCE
    }
}

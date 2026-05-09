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
    }
}

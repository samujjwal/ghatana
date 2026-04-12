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
 * @doc.type interface
 * @doc.purpose Platform-owned append-only event log abstraction
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

    record EventEntry(
        UUID eventId,
        String eventType,
        String eventVersion,
        Instant timestamp,
        ByteBuffer payload,
        String contentType,
        Map<String, String> headers,
        Optional<String> idempotencyKey
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

            public EventEntry build() {
                return new EventEntry(
                    eventId, eventType, eventVersion, timestamp,
                    payload, contentType, headers, idempotencyKey
                );
            }
        }
    }

    interface Subscription {
        void cancel();

        boolean isCancelled();
    }
}

package com.ghatana.core.event.cloud;

import com.ghatana.platform.types.ContentType;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.CorrelationId;
import com.ghatana.platform.types.identity.EventId;
import com.ghatana.platform.types.identity.IdempotencyKey;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable canonical event record for EventCloud storage and wire transfer.
 *
 * <p><b>Purpose</b><br>
 * Format-agnostic, immutable event representation with multi-tenant isolation, correlation tracking, and
 * idempotency support. Uses raw ByteBuffer payload with ContentType indicator for schema flexibility.
 * @doc.type class
 * @doc.purpose Immutable canonical event record for EventCloud storage with multi-tenant isolation
 * @doc.layer core
 * @doc.pattern Value Object, Domain Model
 *
 * Follows QT-model with occurrenceTime (event-time) and detectionTime (processing-time) for temporal accuracy.
 *
 * <p><b>Architecture Role</b><br>
 * Core domain model for events in EventCloud platform. Used by IngestionService for event intake, EventCloud
 * for storage, PipelineBuilder for stream processing, and QueryService for retrieval. Serialized to PostgreSQL
 * (L1 storage), Kafka (L0 fan-out), and Parquet (L4 archive). Foundation for event-driven architecture and CQRS.
 *
 * <p><b>Usage Examples</b><br>
 *
 * <pre>{@code
 * // Example 1: Create transaction event with JSON payload
 * String json = """
 *     {"orderId": "order-123", "amount": 99.99, "currency": "USD"}
 *     """;
 * 
 * EventRecord event = EventRecord.builder()
 *     .tenantId(TenantId.of("tenant-acme"))
 *     .typeRef(EventTypeRef.of("transaction.created", Version.of("1.0.0")))
 *     .eventId(EventId.random())
 *     .occurrenceTime(Instant.now())  // When transaction occurred
 *     .detectionTime(Instant.now())   // When system received it
 *     .headers(Map.of("userId", "user-456", "source", "mobile-app"))
 *     .contentType(ContentType.JSON)
 *     .schemaUri("https://schema.ghatana.com/transaction.created/v1")
 *     .payload(ByteBuffer.wrap(json.getBytes(UTF_8)))
 *     .correlationId(Optional.of(CorrelationId.random()))
 *     .idempotencyKey(Optional.of(IdempotencyKey.of("tx-order-123")))
 *     .build();
 * 
 * // Immutable - thread-safe to share
 * }</pre>
 *
 * <pre>{@code
 * // Example 2: Create sensor event with Protobuf payload
 * SensorReading reading = SensorReading.newBuilder()
 *     .setTemperature(23.5)
 *     .setHumidity(65.2)
 *     .build();
 * 
 * EventRecord event = EventRecord.builder()
 *     .tenantId(TenantId.of("tenant-iot"))
 *     .typeRef(EventTypeRef.of("sensor.reading", Version.of("2.0.0")))
 *     .eventId(EventId.random())
 *     .occurrenceTime(Instant.now())
 *     .detectionTime(Instant.now())
 *     .headers(Map.of("deviceId", "sensor-789"))
 *     .contentType(ContentType.PROTOBUF)
 *     .schemaUri("proto://SensorReading")
 *     .payload(ByteBuffer.wrap(reading.toByteArray()))
 *     .build();
 * }</pre>
 *
 * <pre>{@code
 * // Example 3: Deserialize payload (JSON example)
 * EventRecord event = getEvent();
 * 
 * if (event.contentType().equals(ContentType.JSON)) {
 *     ByteBuffer payload = event.payload();  // Read-only view
 *     byte[] bytes = new byte[payload.remaining()];
 *     payload.get(bytes);
 *     
 *     String json = new String(bytes, UTF_8);
 *     TransactionCreated tx = objectMapper.readValue(json, TransactionCreated.class);
 *     
 *     log.info("Order {} amount {}", tx.getOrderId(), tx.getAmount());
 * }
 * }</pre>
 *
 * <pre>{@code
 * // Example 4: Correlation tracking across services
 * EventRecord incomingRequest = getIncomingEvent();
 * CorrelationId correlationId = incomingRequest.correlationId()
 *     .orElse(CorrelationId.random());
 * 
 * // Create response event with same correlationId
 * EventRecord responseEvent = EventRecord.builder()
 *     .tenantId(incomingRequest.tenantId())
 *     .typeRef(EventTypeRef.of("order.confirmed"))
 *     .eventId(EventId.random())
 *     .occurrenceTime(Instant.now())
 *     .detectionTime(Instant.now())
 *     .contentType(ContentType.JSON)
 *     .payload(responsePayload)
 *     .correlationId(Optional.of(correlationId))  // Same correlation
 *     .build();
 * 
 * // Traces request → response flow
 * }</pre>
 *
 * <p><b>Field Descriptions</b><br>
 * - **tenantId**: Tenant isolation (required, non-null)
 * - **typeRef**: Event type + version (e.g., "transaction.created" v1.0.0)
 * - **eventId**: Unique event identifier (UUID-based)
 * - **occurrenceTime**: When event actually occurred (event-time, QT-model)
 * - **detectionTime**: When system received event (processing-time, QT-model)
 * - **headers**: Key-value metadata (userId, source, etc., immutable Map)
 * - **contentType**: Payload format (JSON, Protobuf, Avro, etc.)
 * - **schemaUri**: Schema location for validation (URL or proto:// reference)
 * - **payload**: Raw event data (ByteBuffer, format-agnostic, read-only view)
 * - **correlationId**: Request correlation for distributed tracing (optional)
 * - **idempotencyKey**: Duplicate detection key (optional, enables exactly-once)
 *
 * <p><b>QT-Model Temporal Semantics</b><br>
 * - **occurrenceTime** (event-time): When event happened in real world
 * - **detectionTime** (processing-time): When system first saw event
 * - **Difference**: occurrenceTime <= detectionTime (detection after occurrence)
 * - **Use cases**: Out-of-order events, late arrivals, time-travel queries
 *
 * <p><b>Idempotency Semantics</b><br>
 * Events with same (tenantId, idempotencyKey) considered duplicates:
 * - First append: Stores event, returns partition/offset
 * - Duplicate append: Returns cached partition/offset, no storage
 * - Enables at-least-once delivery with exactly-once processing
 *
 * <p><b>Best Practices</b><br>
 * - Always set contentType and schemaUri for schema validation
 * - Use idempotencyKey for critical events (duplicate protection)
 * - Set occurrenceTime from event source (not detection time)
 * - Add correlationId for distributed tracing
 * - Use headers for filtering metadata (avoid parsing payload)
 * - Keep payload < 1MB (large payloads reduce throughput)
 *
 * <p><b>Performance Characteristics</b><br>
 * - **Construction**: O(1) field assignment + O(n) header/payload copy
 * - **Memory**: ~500 bytes overhead + payload size + headers size
 * - **Serialization**: ~1-5KB typical (varies by payload)
 * - **Immutability**: Thread-safe, zero-copy for read-only access
 *
 * <p><b>Thread Safety</b><br>
 * ✅ **Thread-safe** - Immutable record with defensive copies.
 * Payload returned as read-only ByteBuffer. Safe to share across threads.
 *
 * <p><b>Integration Points</b><br>
 * - IngestionService: Event intake validation
 * - EventCloud: Storage layer (PostgreSQL L1, Kafka L0)
 * - PipelineBuilder: Stream processing input
 * - QueryService: Historical retrieval
 * - Schema validation: Uses schemaUri for schema registry lookup
 *
 * @see EventCloud
 * @see EventTypeRef
 * @see AppendResult
 * @since 2.0.0
 * @doc.type record
 * @doc.purpose Immutable canonical event record for EventCloud
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record EventRecord(
    TenantId tenantId,
    EventTypeRef typeRef,
    EventId eventId,
    Instant occurrenceTime,
    Instant detectionTime,
    Map<String, String> headers,
    ContentType contentType,
    String schemaUri,
    ByteBuffer payload,
    Optional<CorrelationId> correlationId,
    Optional<IdempotencyKey> idempotencyKey
) {
    /**
     * Compact constructor for validation and defensive copies.
     */
    public EventRecord {
        tenantId = Objects.requireNonNull(tenantId, "tenantId required");
        typeRef = Objects.requireNonNull(typeRef, "typeRef required");
        eventId = Objects.requireNonNull(eventId, "eventId required");
        occurrenceTime = Objects.requireNonNull(occurrenceTime, "occurrenceTime required");
        detectionTime = Objects.requireNonNull(detectionTime, "detectionTime required");
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers required"));
        contentType = Objects.requireNonNull(contentType, "contentType required");
        schemaUri = Objects.requireNonNull(schemaUri, "schemaUri required");
        payload = Objects.requireNonNull(payload, "payload required").asReadOnlyBuffer();
        correlationId = Objects.requireNonNull(correlationId, "correlationId required");
        idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey required");
    }

    /**
     * Builder for creating EventRecord instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TenantId tenantId;
        private EventTypeRef typeRef;
        private EventId eventId;
        private Instant occurrenceTime;
        private Instant detectionTime;
        private Map<String, String> headers = Map.of();
        private ContentType contentType;
        private String schemaUri;
        private ByteBuffer payload;
        private Optional<CorrelationId> correlationId = Optional.empty();
        private Optional<IdempotencyKey> idempotencyKey = Optional.empty();

        public Builder tenantId(TenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder typeRef(EventTypeRef typeRef) {
            this.typeRef = typeRef;
            return this;
        }

        public Builder eventId(EventId eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder occurrenceTime(Instant occurrenceTime) {
            this.occurrenceTime = occurrenceTime;
            return this;
        }

        public Builder detectionTime(Instant detectionTime) {
            this.detectionTime = detectionTime;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder contentType(ContentType contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder schemaUri(String schemaUri) {
            this.schemaUri = schemaUri;
            return this;
        }

        public Builder payload(ByteBuffer payload) {
            this.payload = payload;
            return this;
        }

        public Builder correlationId(CorrelationId correlationId) {
            this.correlationId = Optional.ofNullable(correlationId);
            return this;
        }

        public Builder idempotencyKey(IdempotencyKey idempotencyKey) {
            this.idempotencyKey = Optional.ofNullable(idempotencyKey);
            return this;
        }

        public EventRecord build() {
            return new EventRecord(
                tenantId,
                typeRef,
                eventId,
                occurrenceTime,
                detectionTime,
                headers,
                contentType,
                schemaUri,
                payload,
                correlationId,
                idempotencyKey
            );
        }
    }
}

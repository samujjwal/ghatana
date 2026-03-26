package com.ghatana.datacloud.grpc;

import com.ghatana.contracts.event.v1.EventProto;
import com.ghatana.contracts.event.v1.EventRecordProto;
import com.ghatana.contracts.event.v1.UuidProto;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.platform.types.identity.Offset;
import com.google.protobuf.Timestamp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Bidirectional mapper between {@link EventEntry} domain objects and Protocol Buffer messages.
 *
 * <p>Handles the translation between the Data-Cloud SPI's {@link EventEntry} record type and
 * the canonically typed {@link EventProto} / {@link EventRecordProto} messages generated from
 * {@code platform/contracts/event/v1/event_log.proto}.</p>
 *
 * @doc.type class
 * @doc.purpose Proto ↔ EventEntry bidirectional mapper for the gRPC layer
 * @doc.layer product
 * @doc.pattern Mapper
 * @since 2.0.0
 */
public final class ProtobufMapper {

    /** Default log identifier used when building {@link EventRecordProto}. */
    private static final String DEFAULT_LOG_ID = "event-log";

    private ProtobufMapper() {}

    // ==================== Proto → Domain ====================

    /**
     * Convert an {@link EventProto} into a storable {@link EventEntry}.
     *
     * <p>The idempotency key comes from the proto's {@code idempotency_key} field if present.
     * The payload is encoded as UTF-8 from {@code payload_json}; if that field is empty, an
     * empty byte buffer is used.</p>
     *
     * @param event the canonical proto event (must not be null)
     * @return an {@link EventEntry} ready for {@code EventLogStore.append()}
     */
    public static EventEntry toEventEntry(EventProto event) {
        UUID eventId = parseUuid(event.hasId() ? event.getId().getValue() : null);
        String eventType = event.getType().isBlank() ? "unknown" : event.getType();
        String eventVersion = event.getTypeVersion().isBlank() ? "1.0.0" : event.getTypeVersion();
        Instant timestamp = event.hasDetectedAt()
                ? toInstant(event.getDetectedAt())
                : Instant.now();
        String payloadJson = event.getPayloadJson();
        ByteBuffer payload = payloadJson.isEmpty()
                ? ByteBuffer.allocate(0)
                : ByteBuffer.wrap(payloadJson.getBytes(StandardCharsets.UTF_8));
        Map<String, String> headers = Map.copyOf(event.getHeadersMap());
        String idempotencyKey = event.getIdempotencyKey().isBlank()
                ? null
                : event.getIdempotencyKey();

        return EventEntry.builder()
                .eventId(eventId)
                .eventType(eventType)
                .eventVersion(eventVersion)
                .timestamp(timestamp)
                .payload(payload)
                .contentType("application/json")
                .headers(headers)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    // ==================== Domain → Proto ====================

    /**
     * Build a full {@link EventRecordProto} from a stored {@link EventEntry} and its assigned
     * {@link Offset}.
     *
     * @param entry  the stored event entry
     * @param offset the offset assigned by the store after a successful append
     * @return a populated {@link EventRecordProto} suitable for an {@code AppendResponse}
     */
    public static EventRecordProto toEventRecord(EventEntry entry, Offset offset) {
        EventProto eventProto = toEventProto(entry);
        long offsetValue = parseLongOffset(offset);

        return EventRecordProto.newBuilder()
                .setEvent(eventProto)
                .setLogId(DEFAULT_LOG_ID)
                .setPartition(0)
                .setOffset(offsetValue)
                .build();
    }

    /**
     * Build an {@link EventProto} from a stored {@link EventEntry}.
     *
     * @param entry the stored event entry
     * @return a proto representation suitable for embedding in {@link EventRecordProto}
     */
    public static EventProto toEventProto(EventEntry entry) {
        EventProto.Builder builder = EventProto.newBuilder()
                .setId(UuidProto.newBuilder().setValue(entry.eventId().toString()).build())
                .setType(entry.eventType())
                .setTypeVersion(entry.eventVersion())
                .setDetectedAt(fromInstant(entry.timestamp()))
                .putAllHeaders(entry.headers());

        // Re-encode payload bytes as UTF-8 JSON string (we stored it as UTF-8)
        String payloadJson = StandardCharsets.UTF_8.decode(
                entry.payload().duplicate()).toString();
        if (!payloadJson.isEmpty()) {
            builder.setPayloadJson(payloadJson);
        }

        entry.idempotencyKey().ifPresent(builder::setIdempotencyKey);

        return builder.build();
    }

    // ==================== Helpers ====================

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return UUID.randomUUID();
        }
    }

    private static Instant toInstant(Timestamp ts) {
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }

    private static Timestamp fromInstant(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    static long parseLongOffset(Offset offset) {
        try {
            return Long.parseLong(offset.value());
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }
}

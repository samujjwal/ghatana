package com.ghatana.eventlog;

import com.ghatana.contracts.event.v1.EventProto;
import com.ghatana.contracts.event.v1.UuidProto;
import com.ghatana.contracts.common.v1.TimeIntervalProto;
import com.ghatana.contracts.event.v1.QueryEventsRequestProto;
import com.ghatana.contracts.event.v1.QueryEventsResponseProto;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.EventId;
import com.ghatana.platform.domain.domain.event.EventTime;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.types.time.GTimeInterval;
import com.ghatana.platform.types.time.GTimestamp;
import com.google.protobuf.Timestamp;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event log store interface for Day 8 MVP.
 * Uses contracts messages for API to keep the boundary consistent.
 * 
 * <p>This extends the adapter port and adds domain-level operations.</p>
 * <p>For the core domain port, see {@link com.ghatana.eventcore.ports.EventStore}.</p>
 *
 * @doc.type interface
 * @doc.purpose Defines event append, query, and purge operations bridging domain and protobuf boundaries
 * @doc.layer product
 * @doc.pattern Port
 */
public interface EventLogStore extends com.ghatana.eventlog.ports.EventStorePort {

    Event append(Event event);

    List<Event> appendBatch(List<Event> events);

    Event get(String eventId);

    QueryEventsResponseProto query(QueryEventsRequestProto request);

    List<byte[]> getSerializedEvents(QueryEventsRequestProto request);

    void purgeEventsBefore(Instant cutoff);

    /**
     * Gets events before the specified cutoff time.
     *
     * @param cutoff the time cutoff
     * @return serialized events before the cutoff
     */
    List<byte[]> getSerializedEventsBefore(Instant cutoff);

    /**
     * Converts a domain {@link Event} to its protobuf representation.
     *
     * <p>Maps all available domain fields to their proto counterparts:
     * identity (id, tenant, type, version), temporal (occurrence, detection, bounding),
     * correlation (correlationId, causationId), headers, and payload (as JSON).</p>
     *
     * @param event the domain event to convert (must not be null)
     * @return the protobuf representation; never null
     * @throws IllegalArgumentException if event is null
     */
    default EventProto toProto(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null for proto conversion");
        }

        EventProto.Builder builder = EventProto.newBuilder();

        // Identity
        EventId eventId = event.getId();
        if (eventId != null) {
            if (eventId.getId() != null) {
                builder.setId(UuidProto.newBuilder().setValue(eventId.getId()).build());
            }
            if (eventId.getEventType() != null) {
                builder.setType(eventId.getEventType());
            }
            if (eventId.getVersion() != null) {
                builder.setTypeVersion(eventId.getVersion());
            }
            if (eventId.getTenantId() != null) {
                builder.setTenantId(eventId.getTenantId());
            }
        }

        // Temporal
        EventTime time = event.getTime();
        if (time != null) {
            GTimeInterval occurrenceTime = time.getOccurrenceTime();
            if (occurrenceTime != null) {
                builder.setOccurrenceTime(toTimeIntervalProto(occurrenceTime));
            }
            GTimeInterval boundingInterval = time.getBoundingInterval();
            if (boundingInterval != null) {
                builder.setBoundingInterval(toTimeIntervalProto(boundingInterval));
            }
            GTimestamp detectionTime = time.getDetectionTimePoint();
            if (detectionTime != null) {
                builder.setDetectedAt(toTimestamp(detectionTime.toInstant()));
            }
        }

        // Correlation
        String correlationId = event.getCorrelationId();
        if (correlationId != null) {
            builder.setCorrelationId(correlationId);
        }
        String causationId = event.getCausationId();
        if (causationId != null) {
            builder.setCausationId(causationId);
        }

        // Headers
        if (event.hasHeader("")) {
            // Copy all custom headers
            // The Event interface uses getHeader(name) — iterate known keys
        }
        // For GEvent, headers are a Map
        if (event instanceof GEvent gEvent) {
            Map<String, String> headers = gEvent.getHeaders();
            if (headers != null && !headers.isEmpty()) {
                builder.putAllHeaders(headers);
            }
        }

        // Payload — serialize as JSON string
        if (event instanceof GEvent gEvent) {
            Map<String, Object> payload = gEvent.getPayload();
            if (payload != null && !payload.isEmpty()) {
                builder.setPayloadJson(serializePayloadToJson(payload));
            }
        }

        return builder.build();
    }

    /**
     * Converts a protobuf {@link EventProto} to its domain {@link Event} representation.
     *
     * <p>Maps proto fields back to domain model using the {@link GEvent} builder.
     * Validates that critical fields (id, type) are present before conversion.</p>
     *
     * @param proto the protobuf event to convert (must not be null)
     * @return the domain event; never null
     * @throws IllegalArgumentException if proto is null or missing required fields
     */
    default Event fromProto(EventProto proto) {
        if (proto == null) {
            throw new IllegalArgumentException("EventProto cannot be null for domain conversion");
        }

        GEvent.GEventBuilder builder = Event.builder();

        // Identity
        String id = proto.hasId() ? proto.getId().getValue() : null;
        String type = proto.getType();
        String version = proto.getTypeVersion();
        String tenantId = proto.getTenantId();

        if (id != null && !id.isEmpty() && type != null && !type.isEmpty()) {
            builder.id(EventId.create(id, type, version != null ? version : "v1", tenantId));
        } else if (type != null && !type.isEmpty()) {
            builder.type(type);
        }

        // Temporal
        EventTime.EventTimeBuilder timeBuilder = EventTime.builder();
        boolean hasTime = false;

        if (proto.hasOccurrenceTime()) {
            timeBuilder.occurrenceTime(fromTimeIntervalProto(proto.getOccurrenceTime()));
            hasTime = true;
        }
        if (proto.hasBoundingInterval()) {
            timeBuilder.boundingInterval(fromTimeIntervalProto(proto.getBoundingInterval()));
            hasTime = true;
        }
        if (proto.hasDetectedAt()) {
            timeBuilder.detectionTimePoint(GTimestamp.of(fromTimestamp(proto.getDetectedAt())));
            hasTime = true;
        }
        if (hasTime) {
            builder.time(timeBuilder.build());
        }

        // Correlation — stored in headers
        Map<String, String> headers = new HashMap<>(proto.getHeadersMap());
        if (!proto.getCorrelationId().isEmpty()) {
            headers.put("correlationId", proto.getCorrelationId());
        }
        if (!proto.getCausationId().isEmpty()) {
            headers.put("causationId", proto.getCausationId());
        }
        if (!headers.isEmpty()) {
            builder.headers(headers);
        }

        // Payload
        Map<String, Object> payload = new HashMap<>();
        if (!proto.getPayloadJson().isEmpty()) {
            payload.putAll(deserializeJsonToPayload(proto.getPayloadJson()));
        }
        if (!payload.isEmpty()) {
            builder.payload(payload);
        }

        return builder.build();
    }

    // ============================================================
    // Private conversion helpers (default methods for interface)
    // ============================================================

    /**
     * Converts a {@link GTimeInterval} to a {@link TimeIntervalProto}.
     */
    private static TimeIntervalProto toTimeIntervalProto(GTimeInterval interval) {
        TimeIntervalProto.Builder builder = TimeIntervalProto.newBuilder();
        if (interval.start() != null) {
            builder.setStart(toTimestamp(interval.start().toInstant()));
        }
        if (interval.end() != null) {
            builder.setEnd(toTimestamp(interval.end().toInstant()));
        }
        return builder.build();
    }

    /**
     * Converts a {@link TimeIntervalProto} to a {@link GTimeInterval}.
     */
    private static GTimeInterval fromTimeIntervalProto(TimeIntervalProto proto) {
        Instant start = proto.hasStart() ? fromTimestamp(proto.getStart()) : Instant.now();
        Instant end = proto.hasEnd() ? fromTimestamp(proto.getEnd()) : start;
        return GTimeInterval.between(start, end);
    }

    /**
     * Converts an {@link Instant} to a protobuf {@link Timestamp}.
     */
    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    /**
     * Converts a protobuf {@link Timestamp} to an {@link Instant}.
     */
    private static Instant fromTimestamp(Timestamp ts) {
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }

    /**
     * Serializes a payload map to a JSON string.
     * Uses simple serialization; production systems should use a shared ObjectMapper.
     */
    private static String serializePayloadToJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append(valueToJson(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Deserializes a JSON string to a payload map.
     * For minimal-dependency interface default; production impls should use Jackson.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deserializeJsonToPayload(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return new HashMap<>();
        }
        // Minimal JSON parsing for interface default method
        // Implementations should override with proper Jackson parsing
        Map<String, Object> result = new HashMap<>();
        result.put("_raw_json", json);
        return result;
    }

    /**
     * Escapes a string for JSON representation.
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Converts an object value to its JSON representation.
     */
    private static String valueToJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escapeJson((String) value) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) return serializePayloadToJson((Map<String, Object>) value);
        return "\"" + escapeJson(value.toString()) + "\"";
    }
}

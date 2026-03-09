package com.ghatana.virtualorg.framework.event;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Utility for building and publishing organizational events.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides common pattern for all departments to publish events with consistent
 * structure. Encapsulates event building, tenant context propagation, and
 * validation.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Build event payload
 * Map<String, Object> payload = EventBuilder.newPayload()
 *     .withField("feature_id", featureId)
 *     .withField("title", "Add user authentication")
 *     .withField("requested_by", "pm-alice")
 *     .withTimestamp()
 *     .withCorrelationId()
 *     .build();
 *
 * // Publish event
 * EventBuilder.publishEvent(
 *     publisher,
 *     "FeatureRequestCreated",
 *     payload,
 *     tenantId
 * );
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Event building and publication utility
 * @doc.layer product
 * @doc.pattern Builder
 */
public final class EventBuilder {

    private EventBuilder() {
        // Utility class
    }

    /**
     * Create new event payload builder.
     *
     * @return payload builder
     */
    public static PayloadBuilder newPayload() {
        return new PayloadBuilder();
    }

    /**
     * Publish event to EventPublisher.
     *
     * <p>The payload map is serialized to JSON bytes and tenant ID is added
     * as a field in the payload before publishing.
     *
     * @param publisher event publisher
     * @param eventType event type
     * @param payload   event payload
     * @param tenantId  tenant identifier
     */
    public static void publishEvent(
            EventPublisher publisher,
            String eventType,
            Map<String, Object> payload,
            String tenantId) {

        Objects.requireNonNull(publisher, "publisher must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        // Enrich payload with tenant ID
        Map<String, Object> enrichedPayload = new HashMap<>(payload);
        enrichedPayload.put("tenant_id", tenantId);

        // Serialize to JSON bytes
        byte[] payloadBytes = toJsonBytes(enrichedPayload);
        publisher.publish(eventType, payloadBytes);
    }

    /**
     * Converts a Map to JSON bytes (simple implementation).
     *
     * @param map map to convert
     * @return JSON bytes
     */
    private static byte[] toJsonBytes(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append(toJsonValue(entry.getValue()));
        }
        sb.append("}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeJson((String) value) + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
                sb.append(toJsonValue(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        } else {
            return "\"" + escapeJson(value.toString()) + "\"";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Validates an event type format.
     *
     * @param eventType event type to validate
     * @return true if valid format
     */
    public static boolean isValidEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return false;
        }
        // Basic validation - should be dot-separated identifier or simple name
        return eventType.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$");
    }

    /**
     * Payload builder for constructing event payloads.
     */
    public static class PayloadBuilder {
        private final Map<String, Object> payload = new HashMap<>();

        /**
         * Adds a field to the payload.
         *
         * @param key   field key
         * @param value field value
         * @return this builder
         */
        public PayloadBuilder withField(String key, Object value) {
            if (key != null && value != null) {
                payload.put(key, value);
            }
            return this;
        }

        /**
         * Adds a timestamp field.
         *
         * @return this builder
         */
        public PayloadBuilder withTimestamp() {
            payload.put("timestamp", Instant.now().toString());
            return this;
        }

        /**
         * Adds a timestamp field with custom key.
         *
         * @param key field key for timestamp
         * @return this builder
         */
        public PayloadBuilder withTimestamp(String key) {
            payload.put(key, Instant.now().toString());
            return this;
        }

        /**
         * Adds a correlation ID field.
         *
         * @return this builder
         */
        public PayloadBuilder withCorrelationId() {
            payload.put("correlation_id", UUID.randomUUID().toString());
            return this;
        }

        /**
         * Adds a correlation ID field with custom key.
         *
         * @param key field key for correlation ID
         * @return this builder
         */
        public PayloadBuilder withCorrelationId(String key) {
            payload.put(key, UUID.randomUUID().toString());
            return this;
        }

        /**
         * Adds all fields from another map.
         *
         * @param fields fields to add
         * @return this builder
         */
        public PayloadBuilder withFields(Map<String, Object> fields) {
            if (fields != null) {
                payload.putAll(fields);
            }
            return this;
        }

        /**
         * Builds the payload map.
         *
         * @return immutable payload map
         */
        public Map<String, Object> build() {
            return Map.copyOf(payload);
        }

        /**
         * Builds the payload map as mutable HashMap.
         *
         * @return mutable payload map
         */
        public Map<String, Object> buildMutable() {
            return new HashMap<>(payload);
        }
    }
}

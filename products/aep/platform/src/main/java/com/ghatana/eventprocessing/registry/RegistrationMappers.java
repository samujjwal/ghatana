package com.ghatana.eventprocessing.registry;

import com.ghatana.aep.domain.registry.EventTypeRegistration;
import com.ghatana.platform.domain.domain.event.Event;

import java.util.UUID;

/**
 * Factory for mapping domain registration models to/from event payloads.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides bidirectional mapping between
 * PatternRegistration/EventTypeRegistration domain objects and event payload
 * representations for EventCloud persistence and messaging. Supports backward
 * compatibility by handling optional fields gracefully.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Domain to event payload
 * PatternRegistration registration = PatternRegistration.builder()...build();
 * Map<String, Object> payload = RegistrationMappers.patternToEventPayload(registration);
 *
 * // Event payload to domain
 * PatternRegistration restored = RegistrationMappers.patternFromEventPayload(payload);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Bidirectional mapper for registration domain models and event
 * payloads
 * @doc.layer product
 * @doc.pattern Mapper
 */
public final class RegistrationMappers {

    private RegistrationMappers() {
        // Utility class - no instantiation
    }

    /**
     * Converts PatternRegistration domain object to event payload map.
     *
     * @param registration pattern registration to convert
     * @return payload map with all fields for EventCloud persistence
     */
    public static java.util.Map<String, Object> patternToEventPayload(
            PatternRegistration registration) {
        if (registration == null || !registration.isValid()) {
            throw new IllegalArgumentException("Invalid pattern registration");
        }

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("patternId", registration.getPatternId().toString());
        payload.put("tenantId", registration.getTenantId());
        payload.put("specification", registration.getSpecification());
        payload.put("schemaVersion", registration.getSchemaVersion());
        payload.put("createdBy", registration.getCreatedBy());
        payload.put("createdAt", registration.getCreatedAt().toString());
        payload.put("updatedAt", registration.getUpdatedAt().toString());
        payload.put("agentHint", registration.getAgentHint());
        payload.put("consumerHint", registration.getConsumerHint());
        payload.put("tags", registration.getTags());
        payload.put("active", registration.isActive());
        return payload;
    }

    /**
     * Converts event payload map to PatternRegistration domain object.
     *
     * <p>
     * Supports backward compatibility by treating missing optional fields as
     * null.
     *
     * @param payload event payload map from EventCloud
     * @return PatternRegistration instance
     */
    public static PatternRegistration patternFromEventPayload(
            java.util.Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Empty pattern registration payload");
        }

        return PatternRegistration.builder()
                .patternId(UUID.fromString((String) payload.getOrDefault("patternId", "")))
                .tenantId((String) payload.getOrDefault("tenantId", ""))
                .specification((String) payload.getOrDefault("specification", ""))
                .schemaVersion((String) payload.getOrDefault("schemaVersion", "1.0.0"))
                .createdBy((String) payload.getOrDefault("createdBy", ""))
                .createdAt(java.time.Instant.parse((String) payload.getOrDefault("createdAt", "")))
                .updatedAt(java.time.Instant.parse((String) payload.getOrDefault("updatedAt", "")))
                .agentHint((String) payload.get("agentHint"))
                .consumerHint((String) payload.get("consumerHint"))
                .tags((java.util.List<String>) payload.getOrDefault("tags", java.util.List.of()))
                .active((Boolean) payload.getOrDefault("active", true))
                .build();
    }

    /**
     * Converts EventTypeRegistration domain object to event payload map.
     *
     * @param registration event type registration to convert
     * @return payload map with all fields for EventCloud persistence
     */
    public static java.util.Map<String, Object> eventTypeToEventPayload(
            EventTypeRegistration registration) {
        if (registration == null || !registration.isValid()) {
            throw new IllegalArgumentException("Invalid event type registration");
        }

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("eventTypeId", registration.getEventTypeId().toString());
        payload.put("tenantId", registration.getTenantId());
        payload.put("eventTypeName", registration.getEventTypeName());
        payload.put("schemaJson", registration.getSchemaJson());
        payload.put("schemaVersion", registration.getSchemaVersion());
        payload.put("createdBy", registration.getCreatedBy());
        payload.put("createdAt", registration.getCreatedAt().toString());
        payload.put("updatedAt", registration.getUpdatedAt().toString());
        payload.put("sourceHint", registration.getSourceHint());
        payload.put("consumerHint", registration.getConsumerHint());
        payload.put("tags", registration.getTags());
        payload.put("active", registration.isActive());
        return payload;
    }

    /**
     * Converts event payload map to EventTypeRegistration domain object.
     *
     * <p>
     * Supports backward compatibility by treating missing optional fields as
     * null.
     *
     * @param payload event payload map from EventCloud
     * @return EventTypeRegistration instance
     */
    public static EventTypeRegistration eventTypeFromEventPayload(
            java.util.Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Empty event type registration payload");
        }

        return EventTypeRegistration.builder()
                .eventTypeId(UUID.fromString((String) payload.getOrDefault("eventTypeId", "")))
                .tenantId((String) payload.getOrDefault("tenantId", ""))
                .eventTypeName((String) payload.getOrDefault("eventTypeName", ""))
                .schemaJson((String) payload.getOrDefault("schemaJson", "{}"))
                .schemaVersion((String) payload.getOrDefault("schemaVersion", "1.0.0"))
                .createdBy((String) payload.getOrDefault("createdBy", ""))
                .createdAt(java.time.Instant.parse((String) payload.getOrDefault("createdAt", "")))
                .updatedAt(java.time.Instant.parse((String) payload.getOrDefault("updatedAt", "")))
                .sourceHint((String) payload.get("sourceHint"))
                .consumerHint((String) payload.get("consumerHint"))
                .tags((java.util.List<String>) payload.getOrDefault("tags", java.util.List.of()))
                .active((Boolean) payload.getOrDefault("active", true))
                .build();
    }
}

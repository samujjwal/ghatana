/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.event.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ghatana.datacloud.event.model.EventType;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates events against their registered EventType schemas.
 *
 * <h2>Capabilities</h2>
 * <ul>
 *   <li>JSON Schema validation for headers and payloads</li>
 *   <li>Schema compatibility checking (BACKWARD, FORWARD, FULL, NONE)</li>
 *   <li>Custom validation rules for event-specific constraints</li>
 *   <li>Schema caching for performance</li>
 *   <li>Lifecycle status enforcement (ACTIVE/DEPRECATED only)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EventValidator validator = EventValidator.create();
 * validator.registerEventType(eventType);
 *
 * EventValidationResult result = validator.validate(eventType, header, payload);
 * if (!result.valid()) {
 *     throw new ValidationException(result.violations());
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Event-level validation with JSON Schema and compatibility checking
 * @doc.layer product
 * @doc.pattern Service
 */
public final class EventValidator {

    private static final Logger log = LoggerFactory.getLogger(EventValidator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory SCHEMA_FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    /** Cache for compiled JSON schemas to avoid recompilation. */
    private static final int MAX_SCHEMA_CACHE_ENTRIES = 10_000;
    private final Cache<String, JsonSchema> headerSchemaCache;
    private final Cache<String, JsonSchema> payloadSchemaCache;

    /** Custom validators for event-specific validation logic. */
    private final Map<String, List<EventCustomValidator>> customValidators;

    private EventValidator() {
        this.headerSchemaCache = Caffeine.newBuilder().maximumSize(MAX_SCHEMA_CACHE_ENTRIES).build();
        this.payloadSchemaCache = Caffeine.newBuilder().maximumSize(MAX_SCHEMA_CACHE_ENTRIES).build();
        this.customValidators = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new event validator instance.
     *
     * @return new validator instance
     */
    public static EventValidator create() {
        return new EventValidator();
    }

    /**
     * Registers an event type for validation.
     *
     * <p>Compiles and caches the header and payload schemas.
     *
     * @param eventType the event type to register
     */
    public void registerEventType(EventType eventType) {
        String schemaKey = schemaKey(eventType);
        
        // Cache header schema
        if (!eventType.getHeaderSchema().isEmpty()) {
            headerSchemaCache.get(schemaKey + ":header", k -> {
                try {
                    JsonNode schemaNode = OBJECT_MAPPER.valueToTree(toJsonSchemaObject(eventType.getHeaderSchema()));
                    return SCHEMA_FACTORY.getSchema(schemaNode);
                } catch (Exception e) {
                    log.warn("Failed to compile header schema for event type '{}': {}", 
                        eventType.getFullyQualifiedName(), e.getMessage());
                    return null;
                }
            });
        }

        // Cache payload schema
        if (!eventType.getPayloadSchema().isEmpty()) {
            payloadSchemaCache.get(schemaKey + ":payload", k -> {
                try {
                    JsonNode schemaNode = OBJECT_MAPPER.valueToTree(toJsonSchemaObject(eventType.getPayloadSchema()));
                    return SCHEMA_FACTORY.getSchema(schemaNode);
                } catch (Exception e) {
                    log.warn("Failed to compile payload schema for event type '{}': {}", 
                        eventType.getFullyQualifiedName(), e.getMessage());
                    return null;
                }
            });
        }

        log.debug("Registered event type '{}' for validation", eventType.getFullyQualifiedName());
    }

    /**
     * Evicts cached schemas for an event type.
     *
     * @param eventType the event type to evict
     */
    public void evictEventType(EventType eventType) {
        String schemaKey = schemaKey(eventType);
        headerSchemaCache.invalidate(schemaKey + ":header");
        payloadSchemaCache.invalidate(schemaKey + ":payload");
        log.debug("Evicted event type '{}' from validation cache", eventType.getFullyQualifiedName());
    }

    /**
     * Registers a custom validator for an event type.
     *
     * @param eventType the event type
     * @param validator  the custom validator implementation
     */
    public void registerCustomValidator(EventType eventType, EventCustomValidator validator) {
        String key = schemaKey(eventType);
        customValidators.computeIfAbsent(key, k -> new ArrayList<>()).add(validator);
        log.debug("Registered custom validator for event type '{}'", eventType.getFullyQualifiedName());
    }

    /**
     * Validates an event against its event type schema.
     *
     * @param eventType the event type schema
     * @param header    the event header map
     * @param payload   the event payload map
     * @return validation result
     */
    public EventValidationResult validate(EventType eventType, Map<String, Object> header, Map<String, Object> payload) {
        List<String> violations = new ArrayList<>();
        String schemaKey = schemaKey(eventType);

        // 1. Check lifecycle status
        if (!eventType.acceptsEvents()) {
            violations.add("Event type '" + eventType.getFullyQualifiedName() + 
                "' is in " + eventType.getLifecycleStatus() + " status and cannot accept events");
        }

        // 2. Validate header schema
        JsonSchema headerSchema = headerSchemaCache.getIfPresent(schemaKey + ":header");
        if (headerSchema != null && header != null) {
            try {
                JsonNode headerNode = OBJECT_MAPPER.valueToTree(header);
                Set<ValidationMessage> messages = headerSchema.validate(headerNode);
                for (ValidationMessage message : messages) {
                    violations.add("Header validation error: " + message.getMessage());
                }
            } catch (Exception e) {
                log.warn("Failed to validate header for event type '{}': {}", 
                    eventType.getFullyQualifiedName(), e.getMessage());
            }
        }

        // 3. Validate payload schema
        JsonSchema payloadSchema = payloadSchemaCache.getIfPresent(schemaKey + ":payload");
        if (payloadSchema != null && payload != null) {
            try {
                JsonNode payloadNode = OBJECT_MAPPER.valueToTree(payload);
                Set<ValidationMessage> messages = payloadSchema.validate(payloadNode);
                for (ValidationMessage message : messages) {
                    violations.add("Payload validation error: " + message.getMessage());
                }
            } catch (Exception e) {
                log.warn("Failed to validate payload for event type '{}': {}", 
                    eventType.getFullyQualifiedName(), e.getMessage());
            }
        }

        // 4. Run custom validators (only if schema validation passed)
        if (violations.isEmpty()) {
            List<EventCustomValidator> validators = customValidators.get(schemaKey);
            if (validators != null) {
                for (EventCustomValidator validator : validators) {
                    validator.validate(eventType, header, payload).ifPresent(violations::add);
                }
            }
        }

        return violations.isEmpty()
            ? new EventValidationResult(true, List.of())
            : new EventValidationResult(false, violations);
    }

    /**
     * Checks schema compatibility between two event type versions.
     *
     * @param oldType the old event type
     * @param newType the new event type
     * @return compatibility check result
     */
    public CompatibilityResult checkCompatibility(EventType oldType, EventType newType) {
        EventType.CompatibilityPolicy policy = newType.getCompatibilityPolicy();

        switch (policy) {
            case BACKWARD:
                return checkBackwardCompatibility(oldType, newType);
            case FORWARD:
                return checkForwardCompatibility(oldType, newType);
            case FULL:
                CompatibilityResult backward = checkBackwardCompatibility(oldType, newType);
                if (!backward.compatible()) {
                    return backward;
                }
                return checkForwardCompatibility(oldType, newType);
            case NONE:
                return new CompatibilityResult(true, List.of());
            default:
                return new CompatibilityResult(true, List.of());
        }
    }

    @SuppressWarnings("unchecked")
    private CompatibilityResult checkBackwardCompatibility(EventType oldType, EventType newType) {
        List<String> violations = new ArrayList<>();

        // Check that no required fields were removed from payload
        Map<String, Object> oldPayload = oldType.getPayloadSchema();
        Map<String, Object> newPayload = newType.getPayloadSchema();

        for (Map.Entry<String, Object> entry : oldPayload.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, Object> fieldDef = (Map<String, Object>) entry.getValue();
            boolean wasRequired = !Boolean.FALSE.equals(fieldDef.get("required"));

            if (wasRequired && !newPayload.containsKey(fieldName)) {
                violations.add("required field '" + fieldName + "' was removed from payload schema");
            }
        }

        return violations.isEmpty()
            ? new CompatibilityResult(true, List.of())
            : new CompatibilityResult(false, violations);
    }

    @SuppressWarnings("unchecked")
    private CompatibilityResult checkForwardCompatibility(EventType oldType, EventType newType) {
        List<String> violations = new ArrayList<>();

        // Check that no required fields were added (old consumers won't have them)
        Map<String, Object> oldPayload = oldType.getPayloadSchema();
        Map<String, Object> newPayload = newType.getPayloadSchema();

        for (Map.Entry<String, Object> entry : newPayload.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, Object> fieldDef = (Map<String, Object>) entry.getValue();
            boolean isRequired = !Boolean.FALSE.equals(fieldDef.get("required"));

            if (isRequired && !oldPayload.containsKey(fieldName)) {
                violations.add("New required field '" + fieldName + "' added to payload schema");
            }
        }

        return violations.isEmpty()
            ? new CompatibilityResult(true, List.of())
            : new CompatibilityResult(false, violations);
    }

    private static String schemaKey(EventType eventType) {
        return eventType.getTenantId() + ":" + eventType.getFullyQualifiedName() + ":" + eventType.getSchemaVersion();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toJsonSchemaObject(Map<String, Object> fieldSchema) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Map.Entry<String, Object> entry : fieldSchema.entrySet()) {
            String fieldName = entry.getKey();
            Object raw = entry.getValue();

            if (!(raw instanceof Map<?, ?> fieldDefRaw)) {
                continue;
            }

            Map<String, Object> fieldDef = (Map<String, Object>) fieldDefRaw;
            String type = String.valueOf(fieldDef.getOrDefault("type", "string")).toLowerCase();

            Map<String, Object> propertySchema = new LinkedHashMap<>();
            propertySchema.put("type", type);
            properties.put(fieldName, propertySchema);

            if (Boolean.TRUE.equals(fieldDef.get("required"))) {
                required.add(fieldName);
            }
        }

        root.put("properties", properties);
        if (!required.isEmpty()) {
            root.put("required", required);
        }
        return root;
    }

    /**
     * Result of event validation.
     */
    public record EventValidationResult(
        boolean valid,
        List<String> violations
    ) {
    }

    /**
     * Result of compatibility check.
     */
    public record CompatibilityResult(
        boolean compatible,
        List<String> violations
    ) {
    }

    /**
     * Interface for custom event validation logic.
     */
    @FunctionalInterface
    public interface EventCustomValidator {
        /**
         * Validates an event with custom business logic.
         *
         * @param eventType the event type schema
         * @param header    the event header
         * @param payload   the event payload
         * @return Optional containing a violation message if validation fails
         */
        Optional<String> validate(EventType eventType, Map<String, Object> header, Map<String, Object> payload);
    }
}

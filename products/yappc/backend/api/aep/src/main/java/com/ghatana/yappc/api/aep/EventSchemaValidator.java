/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC AEP — Event Schema Validator
 */

package com.ghatana.yappc.api.aep;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
/**
 * Validates YAPPC AEP event payloads against JSON-Schema (Draft-07) definitions
 * loaded from the classpath ({@code config/agents/event-schemas/}).
 *
 * <p>Schemas are loaded once at construction time and cached for the lifetime
 * of the validator.  Unknown event types are <em>allowed</em> (pass-through)
 * so that the pipeline can evolve independently of the validator.
 *
 * @doc.type class
 * @doc.purpose Validates YAPPC AEP event payloads against classpath JSON schemas
 * @doc.layer product
 * @doc.pattern Validator
 */
public class EventSchemaValidator {

    private static final Logger LOG = LoggerFactory.getLogger(EventSchemaValidator.class);
    private static final String SCHEMA_BASE_PATH = "config/agents/event-schemas/";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaFactory schemaFactory =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private final Map<String, JsonSchema> compiledSchemas;

    /**
     * Creates an EventSchemaValidator and loads all known schemas from the classpath.
     */
    public EventSchemaValidator() {
        this.compiledSchemas = new HashMap<>();
        loadSchemas();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Validates {@code eventPayload} against the JSON schema registered for
     * {@code eventType}.
     *
     * <p>If no schema is registered for the given event type the payload is
     * considered valid (pass-through semantics).  If {@code eventPayload} is
     * {@code null} an error result is returned immediately.
     *
     * @param eventType    the event type string (e.g. {@code "phase.transition"})
     * @param eventPayload the payload map to validate (may be {@code null})
     * @return an {@link EventValidationResult} — never {@code null}
     */
    @NotNull
    public EventValidationResult validateEvent(
            @NotNull String eventType,
            @Nullable Map<String, Object> eventPayload) {

        if (eventPayload == null) {
            return EventValidationResult.failure(List.of("Validation error: payload must not be null"));
        }

        JsonSchema schema = compiledSchemas.get(eventType);
        if (schema == null) {
            LOG.debug("No schema registered for event type '{}' — allowing pass-through", eventType);
            return EventValidationResult.success();
        }

        try {
            JsonNode node = objectMapper.valueToTree(eventPayload);
            Set<ValidationMessage> violations = schema.validate(node);
            if (violations.isEmpty()) {
                LOG.debug("Validation passed for event type '{}'", eventType);
                return EventValidationResult.success();
            }
            List<String> errors = violations.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toList());
            LOG.warn("Validation failed for event type '{}': {}", eventType, errors);
            return EventValidationResult.failure(errors);
        } catch (Exception e) {
            LOG.error("Unexpected error validating event type '{}': {}", eventType, e.getMessage(), e);
            return EventValidationResult.failure(List.of("Validation error: " + e.getMessage()));
        }
    }

    /**
     * Returns {@code true} if a compiled schema exists for {@code eventType}.
     *
     * @param eventType the event type to check
     * @return {@code true} if a schema is registered
     */
    public boolean hasSchema(@NotNull String eventType) {
        return compiledSchemas.containsKey(eventType);
    }

    /**
     * Returns an unmodifiable view of all event types that have a registered schema.
     *
     * @return immutable set of supported event type strings
     */
    @NotNull
    public Set<String> getSupportedEventTypes() {
        return Collections.unmodifiableSet(compiledSchemas.keySet());
    }

    // -------------------------------------------------------------------------
    // Schema loading
    // -------------------------------------------------------------------------

    private void loadSchemas() {
        String[][] schemaEntries = {
            {"shape-created-v1.json",       "shape.created"},
            {"task-status-changed-v1.json", "task.status.changed"},
            {"phase-transition-v1.json",    "phase.transition"},
            {"agent-dispatch-v1.json",      "agent.dispatch"},
            {"agent-result-v1.json",        "agent.result"},
        };

        for (String[] entry : schemaEntries) {
            String file      = entry[0];
            String eventType = entry[1];
            String resource  = SCHEMA_BASE_PATH + file;

            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (is == null) {
                    LOG.warn("Schema resource not found on classpath: {}", resource);
                    continue;
                }
                JsonSchema schema = schemaFactory.getSchema(is);
                compiledSchemas.put(eventType, schema);
                LOG.debug("Loaded schema '{}' from '{}'", eventType, resource);
            } catch (Exception e) {
                LOG.error("Failed to load schema '{}' from '{}': {}", eventType, resource, e.getMessage(), e);
            }
        }

        LOG.info("EventSchemaValidator ready — {} schema(s) loaded", compiledSchemas.size());
    }
}

/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC AEP - Event Schema Validator
 * 
 * Extends existing SchemaValidationService to validate YAPPC event schemas
 * in the AEP pipeline. No duplication - reuses platform validation infrastructure.
 */

package com.ghatana.yappc.api.aep;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event validation result value object.
 */
public static class EventValidationResult {
    private final boolean valid;
    private final List<String> errors;
    private final String errorMessage;
    
    private EventValidationResult(boolean valid, List<String> errors, String errorMessage) {
        this.valid = valid;
        this.errors = errors != null ? Collections.unmodifiableList(new ArrayList<>(errors)) : Collections.emptyList();
        this.errorMessage = errorMessage;
    }
    
    public static EventValidationResult success() {
        return new EventValidationResult(true, null, null);
    }
    
    public static EventValidationResult failure(List<String> errors) {
        return new EventValidationResult(false, errors, null);
    }
    
    public static EventValidationResult error(String errorMessage) {
        return new EventValidationResult(false, null, errorMessage);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}

/**
 * Event schema validator for YAPPC AEP pipeline.
 * 
 * Validates events against JSON schemas defined in config/agents/event-schemas/.
 * Integrates with existing SchemaValidationService infrastructure.
 * 
 * @doc.type class
 * @doc.purpose Event schema validation for AEP pipeline
 * @doc.layer aep
 * @doc.pattern Validator
 */
public class EventSchemaValidator {
    
    private static final Logger LOG = LoggerFactory.getLogger(EventSchemaValidator.class);
    
    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;
    private final Map<String, JsonSchema> eventSchemas;
    
    public EventSchemaValidator() {
        this.objectMapper = new ObjectMapper();
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        this.eventSchemas = new HashMap<>();
        
        loadEventSchemas();
    }
    
    /**
     * Validate an event payload against its schema.
     * 
     * @param eventType the event type (e.g., "shape.created", "task.status.changed")
     * @param eventPayload the event data to validate
     * @return validation result
     */
    public EventValidationResult validateEvent(String eventType, Map<String, Object> eventPayload) {
        try {
            JsonSchema schema = eventSchemas.get(eventType);
            if (schema == null) {
                LOG.warn("No schema found for event type: {}", eventType);
                return EventValidationResult.success(); // Allow unknown events
            }
            
            JsonNode jsonNode = objectMapper.valueToTree(eventPayload);
            Set<ValidationMessage> messages = schema.validate(jsonNode);
            
            if (messages.isEmpty()) {
                LOG.debug("Event validation passed: {}", eventType);
                return EventValidationResult.success();
            } else {
                List<String> errors = messages.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toList());
                
                LOG.warn("Event validation failed: {} - {}", eventType, errors);
                return EventValidationResult.failure(errors);
            }
            
        } catch (Exception e) {
            LOG.error("Event validation error for {}: {}", eventType, e.getMessage());
            return EventValidationResult.error("Validation error: " + e.getMessage());
        }
    }
    
    /**
     * Check if a schema exists for the given event type.
     * 
     * @param eventType the event type
     * @return true if schema exists
     */
    public boolean hasSchema(String eventType) {
        return eventSchemas.containsKey(eventType);
    }
    
    /**
     * Get all supported event types.
     * 
     * @return set of supported event types
     */
    public Set<String> getSupportedEventTypes() {
        return Collections.unmodifiableSet(eventSchemas.keySet());
    }
    
    /**
     * Load event schemas from classpath resources.
     */
    private void loadEventSchemas() {
        String[] schemaFiles = {
            "shape-created-v1.json",
            "task-status-changed-v1.json", 
            "phase-transition-v1.json",
            "agent-dispatch-v1.json",
            "agent-result-v1.json",
        };
        
        for (String schemaFile : schemaFiles) {
            try {
                String eventType = extractEventTypeFromFilename(schemaFile);
                InputStream schemaStream = getClass().getClassLoader()
                    .getResourceAsStream("config/agents/event-schemas/" + schemaFile);
                
                if (schemaStream != null) {
                    JsonSchema schema = schemaFactory.getSchema(schemaStream);
                    eventSchemas.put(eventType, schema);
                    LOG.debug("Loaded event schema: {} -> {}", eventType, schemaFile);
                } else {
                    LOG.warn("Event schema not found: {}", schemaFile);
                }
                
            } catch (Exception e) {
                LOG.error("Failed to load event schema {}: {}", schemaFile, e.getMessage());
            }
        }
        
        LOG.info("Loaded {} event schemas", eventSchemas.size());
    }
    
    /**
     * Extract event type from schema filename.
     * 
     * @param filename the schema filename
     * @return event type
     */
    private String extractEventTypeFromFilename(String filename) {
        // Convert "shape-created-v1.json" -> "shape.created"
        String base = filename.replace(".json", "");
        String withoutVersion = base.substring(0, base.lastIndexOf("-v"));
        return withoutVersion.replace("-", ".");
    }
}

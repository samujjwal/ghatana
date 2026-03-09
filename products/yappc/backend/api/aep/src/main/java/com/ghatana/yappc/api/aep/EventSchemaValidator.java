/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC AEP - Event Schema Validator
 * 
 * Validates event payloads against JSON schemas to ensure event integrity
 * and compatibility across the platform.
 */

package com.ghatana.yappc.api.aep;

import com.ghatana.core.validation.ValidationResult;
import com.ghatana.core.validation.SchemaValidationService;
import io.activej.inject.annotation.Inject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event Schema Validator - Validates AEP events against JSON schemas.
 * 
 * <p><b>Purpose</b><br>
 * Ensures event payloads conform to expected schemas before processing.
 * Provides validation error details for debugging and error handling.
 * 
 * <p><b>Features</b><br>
 * - JSON schema validation using platform validation service
 * - Schema caching for performance
 * - Support for multiple event types
 * - Detailed validation error reporting
 
 * @doc.type class
 * @doc.purpose Handles event schema validator operations
 * @doc.layer product
 * @doc.pattern Validator
*/
public class EventSchemaValidator {
    
    private static final Logger LOG = LoggerFactory.getLogger(EventSchemaValidator.class);
    
    private final SchemaValidationService schemaValidationService;
    private final Map<String, String> eventSchemas = new ConcurrentHashMap<>();
    
    /**
     * Creates a new event schema validator.
     * 
     * @param schemaValidationService the platform schema validation service
     */
    @Inject
    public EventSchemaValidator(@NotNull SchemaValidationService schemaValidationService) {
        this.schemaValidationService = schemaValidationService;
        loadEventSchemas();
    }
    
    /**
     * Validates an event payload against its expected schema.
     * 
     * @param eventType the event type to validate
     * @param payload the event payload to validate
     * @return validation result with success status and error details
     */
    @NotNull
    public ValidationResult validateEvent(@NotNull String eventType, @NotNull Map<String, Object> payload) {
        try {
            String schema = eventSchemas.get(eventType);
            if (schema == null) {
                LOG.warn("No schema found for event type: {}", eventType);
                return ValidationResult.failure("No schema found for event type: " + eventType);
            }
            
            return schemaValidationService.validate(payload, schema);
            
        } catch (Exception e) {
            LOG.error("Error validating event type: {}", eventType, e);
            return ValidationResult.failure("Validation error: " + e.getMessage());
        }
    }
    
    /**
     * Checks if a schema exists for the given event type.
     * 
     * @param eventType the event type to check
     * @return true if schema exists, false otherwise
     */
    public boolean hasSchema(@NotNull String eventType) {
        return eventSchemas.containsKey(eventType);
    }
    
    /**
     * Loads event schemas from classpath resources.
     */
    private void loadEventSchemas() {
        // For now, provide basic schemas for common event types
        // In a full implementation, these would be loaded from config files
        
        eventSchemas.put("agent_dispatch", createAgentDispatchSchema());
        eventSchemas.put("agent_result", createAgentResultSchema());
        eventSchemas.put("phase_transition", createPhaseTransitionSchema());
        eventSchemas.put("task_status", createTaskStatusSchema());
        
        LOG.info("Loaded {} event schemas", eventSchemas.size());
    }
    
    /**
     * Creates a basic schema for agent dispatch events.
     */
    @NotNull
    private String createAgentDispatchSchema() {
        return """
            {
                "type": "object",
                "required": ["agentId", "eventType", "timestamp", "payload"],
                "properties": {
                    "agentId": {"type": "string"},
                    "eventType": {"type": "string"},
                    "timestamp": {"type": "number"},
                    "payload": {"type": "object"}
                }
            }
            """;
    }
    
    /**
     * Creates a basic schema for agent result events.
     */
    @NotNull
    private String createAgentResultSchema() {
        return """
            {
                "type": "object",
                "required": ["agentId", "result", "timestamp"],
                "properties": {
                    "agentId": {"type": "string"},
                    "result": {"type": "object"},
                    "success": {"type": "boolean"},
                    "timestamp": {"type": "number"},
                    "explanation": {"type": "string"}
                }
            }
            """;
    }
    
    /**
     * Creates a basic schema for phase transition events.
     */
    @NotNull
    private String createPhaseTransitionSchema() {
        return """
            {
                "type": "object",
                "required": ["projectId", "fromPhase", "toPhase", "timestamp"],
                "properties": {
                    "projectId": {"type": "string"},
                    "fromPhase": {"type": "string"},
                    "toPhase": {"type": "string"},
                    "timestamp": {"type": "number"},
                    "gateResult": {"type": "object"}
                }
            }
            """;
    }
    
    /**
     * Creates a basic schema for task status events.
     */
    @NotNull
    private String createTaskStatusSchema() {
        return """
            {
                "type": "object",
                "required": ["taskId", "status", "timestamp"],
                "properties": {
                    "taskId": {"type": "string"},
                    "status": {"type": "string"},
                    "timestamp": {"type": "number"},
                    "result": {"type": "object"},
                    "error": {"type": "string"}
                }
            }
            """;
    }
}

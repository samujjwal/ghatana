/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC AEP - Event Schema Validator Test
 * 
 * Comprehensive unit tests for EventSchemaValidator to ensure
 * event schema validation is working correctly for all event types.
 */

package com.ghatana.yappc.api.aep;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Unit tests for EventSchemaValidator.
 * 
 * Tests:
 * - Schema loading and validation
 * - Valid event validation
 * - Invalid event rejection
 * - Schema existence checks
 * - Error handling
 */
@DisplayName("EventSchemaValidator")
/**
 * @doc.type class
 * @doc.purpose Handles event schema validator test operations
 * @doc.layer product
 * @doc.pattern Test
 */
class EventSchemaValidatorTest {
    
    private EventSchemaValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new EventSchemaValidator();
    }
    
    @Nested
    @DisplayName("Schema Loading")
    class SchemaLoading {
        
        @Test
        @DisplayName("Should load all expected event schemas")
        void shouldLoadAllExpectedSchemas() {
            Set<String> supportedTypes = validator.getSupportedEventTypes();
            
            assertTrue(supportedTypes.contains("shape.created"), "Should support shape.created events");
            assertTrue(supportedTypes.contains("task.status.changed"), "Should support task.status.changed events");
            assertTrue(supportedTypes.contains("phase.transition"), "Should support phase.transition events");
            assertTrue(supportedTypes.contains("agent.dispatch"), "Should support agent.dispatch events");
            assertTrue(supportedTypes.contains("agent.result"), "Should support agent.result events");
            
            assertEquals(5, supportedTypes.size(), "Should load exactly 5 event schemas");
        }
        
        @Test
        @DisplayName("Should report schema existence correctly")
        void shouldReportSchemaExistence() {
            assertTrue(validator.hasSchema("shape.created"), "Should have schema for shape.created");
            assertTrue(validator.hasSchema("phase.transition"), "Should have schema for phase.transition");
            assertFalse(validator.hasSchema("unknown.event"), "Should not have schema for unknown events");
        }
    }
    
    @Nested
    @DisplayName("Valid Event Validation")
    class ValidEventValidation {
        
        @Test
        @DisplayName("Should validate valid phase transition event")
        void shouldValidateValidPhaseTransition() {
            Map<String, Object> event = createValidPhaseTransitionEvent();
            
            var result = validator.validateEvent("phase.transition", event);
            
            assertTrue(result.isValid(), "Valid phase transition should pass validation");
            assertTrue(result.getErrors().isEmpty(), "Should have no validation errors");
        }
        
        @Test
        @DisplayName("Should validate valid agent dispatch event")
        void shouldValidateValidAgentDispatch() {
            Map<String, Object> event = createValidAgentDispatchEvent();
            
            var result = validator.validateEvent("agent.dispatch", event);
            
            assertTrue(result.isValid(), "Valid agent dispatch should pass validation");
            assertTrue(result.getErrors().isEmpty(), "Should have no validation errors");
        }
        
        @Test
        @DisplayName("Should validate valid agent result event")
        void shouldValidateValidAgentResult() {
            Map<String, Object> event = createValidAgentResultEvent();
            
            var result = validator.validateEvent("agent.result", event);
            
            assertTrue(result.isValid(), "Valid agent result should pass validation");
            assertTrue(result.getErrors().isEmpty(), "Should have no validation errors");
        }
        
        @Test
        @DisplayName("Should validate valid task status changed event")
        void shouldValidateValidTaskStatusChanged() {
            Map<String, Object> event = createValidTaskStatusChangedEvent();
            
            var result = validator.validateEvent("task.status.changed", event);
            
            assertTrue(result.isValid(), "Valid task status changed should pass validation");
            assertTrue(result.getErrors().isEmpty(), "Should have no validation errors");
        }
        
        @Test
        @DisplayName("Should validate valid shape created event")
        void shouldValidateValidShapeCreated() {
            Map<String, Object> event = createValidShapeCreatedEvent();
            
            var result = validator.validateEvent("shape.created", event);
            
            assertTrue(result.isValid(), "Valid shape created should pass validation");
            assertTrue(result.getErrors().isEmpty(), "Should have no validation errors");
        }
    }
    
    @Nested
    @DisplayName("Invalid Event Validation")
    class InvalidEventValidation {
        
        @Test
        @DisplayName("Should reject phase transition missing required fields")
        void shouldRejectPhaseTransitionMissingRequiredFields() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "phase.transition");
            // Missing required fields: eventId, timestamp, projectId, fromStage, toStage
            
            var result = validator.validateEvent("phase.transition", event);
            
            assertFalse(result.isValid(), "Invalid phase transition should fail validation");
            assertFalse(result.getErrors().isEmpty(), "Should have validation errors");
        }
        
        @Test
        @DisplayName("Should reject agent dispatch with invalid stage")
        void shouldRejectAgentDispatchWithInvalidStage() {
            Map<String, Object> event = createValidAgentDispatchEvent();
            event.put("fromStage", "invalid-stage");
            
            var result = validator.validateEvent("agent.dispatch", event);
            
            assertFalse(result.isValid(), "Agent dispatch with invalid stage should fail validation");
            assertFalse(result.getErrors().isEmpty(), "Should have validation errors");
        }
        
        @Test
        @DisplayName("Should reject agent result with invalid status")
        void shouldRejectAgentResultWithInvalidStatus() {
            Map<String, Object> event = createValidAgentResultEvent();
            event.put("status", "invalid-status");
            
            var result = validator.validateEvent("agent.result", event);
            
            assertFalse(result.isValid(), "Agent result with invalid status should fail validation");
            assertFalse(result.getErrors().isEmpty(), "Should have validation errors");
        }
    }
    
    @Nested
    @DisplayName("Unknown Event Handling")
    class UnknownEventHandling {
        
        @Test
        @DisplayName("Should allow unknown event types gracefully")
        void shouldAllowUnknownEventTypes() {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "unknown.event");
            event.put("eventId", "test-123");
            event.put("timestamp", System.currentTimeMillis());
            
            var result = validator.validateEvent("unknown.event", event);
            
            assertTrue(result.isValid(), "Unknown events should be allowed");
            assertTrue(result.getErrors().isEmpty(), "Unknown events should have no errors");
        }
    }
    
    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {
        
        @Test
        @DisplayName("Should handle null event payload gracefully")
        void shouldHandleNullEventPayload() {
            var result = validator.validateEvent("phase.transition", null);
            
            assertFalse(result.isValid(), "Null payload should fail validation");
            assertTrue(result.getErrors().get(0).contains("Validation error"), "Should return validation error");
        }
        
        @Test
        @DisplayName("Should handle malformed event data")
        void shouldHandleMalformedEventData() {
            Map<String, Object> event = new HashMap<>();
            event.put("timestamp", "not-a-number"); // Invalid type
            
            var result = validator.validateEvent("phase.transition", event);
            
            assertFalse(result.isValid(), "Malformed data should fail validation");
            assertFalse(result.getErrors().isEmpty(), "Should have validation errors");
        }
    }
    
    // Helper methods to create valid test events
    private Map<String, Object> createValidPhaseTransitionEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "phase-123");
        event.put("eventType", "phase.transition");
        event.put("timestamp", System.currentTimeMillis());
        event.put("projectId", "proj-456");
        event.put("fromStage", "plan");
        event.put("toStage", "execute");
        event.put("triggerEvent", "task.completed");
        event.put("correlationId", "corr-789");
        return event;
    }
    
    private Map<String, Object> createValidAgentDispatchEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "dispatch-123");
        event.put("eventType", "agent.dispatch");
        event.put("timestamp", System.currentTimeMillis());
        event.put("agentId", "agent.test");
        event.put("fromStage", "execute");
        event.put("toStage", "verify");
        event.put("executionContext", Map.of(
            "projectId", "proj-456",
            "taskId", "task-789"
        ));
        event.put("inputData", Map.of("query", "test query"));
        event.put("priority", "normal");
        event.put("timeout", 30000);
        return event;
    }
    
    private Map<String, Object> createValidAgentResultEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "result-123");
        event.put("eventType", "agent.result");
        event.put("timestamp", System.currentTimeMillis());
        event.put("agentId", "agent.test");
        event.put("status", "success");
        event.put("executionTime", 5000);
        event.put("outputData", Map.of("result", "test result"));
        event.put("context", Map.of(
            "projectId", "proj-456",
            "taskId", "task-789"
        ));
        event.put("metrics", Map.of("tokensUsed", 100));
        return event;
    }
    
    private Map<String, Object> createValidTaskStatusChangedEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "task-status-123");
        event.put("eventType", "task.status.changed");
        event.put("timestamp", System.currentTimeMillis());
        event.put("taskId", "task-456");
        event.put("projectId", "proj-789");
        event.put("oldStatus", "pending");
        event.put("newStatus", "in_progress");
        event.put("assigneeId", "user-123");
        return event;
    }
    
    private Map<String, Object> createValidShapeCreatedEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "shape-123");
        event.put("eventType", "shape.created");
        event.put("timestamp", System.currentTimeMillis());
        event.put("shapeId", "shape-456");
        event.put("shapeType", "component");
        event.put("projectId", "proj-789");
        event.put("properties", Map.of("name", "test component"));
        return event;
    }
}

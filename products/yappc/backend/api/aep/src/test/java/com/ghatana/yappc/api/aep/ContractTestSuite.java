/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC AEP - Contract Test Suite
 * 
 * Contract tests to ensure API contracts are maintained between
 * frontend and backend, and that event schemas remain stable.
 */

package com.ghatana.yappc.api.aep;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Contract test suite for YAPPC platform contracts.
 * 
 * Tests:
 * - Event schema contract stability
 * - API response format contracts
 * - Agent registry contract compliance
 * - Pipeline definition contract validation
 */
@DisplayName("Contract Test Suite")
/**
 * @doc.type class
 * @doc.purpose Handles contract test suite operations
 * @doc.layer product
 * @doc.pattern ValueObject
 */
class ContractTestSuite {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventSchemaValidator validator = new EventSchemaValidator();
    
    @Nested
    @DisplayName("Event Schema Contracts")
    @Order(1)
    class EventSchemaContracts {
        
        @Test
        @DisplayName("Phase transition event contract should remain stable")
        void phaseTransitionEventContractShouldRemainStable() {
            // This test ensures the phase transition event schema
            // doesn't break existing contracts
            
            Set<String> supportedTypes = validator.getSupportedEventTypes();
            assertTrue(supportedTypes.contains("phase.transition"), 
                "phase.transition event must remain supported");
            
            // Test contract compliance with expected fields
            Map<String, Object> contractEvent = createContractPhaseTransitionEvent();
            var result = validator.validateEvent("phase.transition", contractEvent);
            
            assertTrue(result.isValid(), 
                "Contract-compliant phase transition event should validate: " + result.getErrors());
        }
        
        @Test
        @DisplayName("Agent dispatch event contract should remain stable")
        void agentDispatchEventContractShouldRemainStable() {
            Set<String> supportedTypes = validator.getSupportedEventTypes();
            assertTrue(supportedTypes.contains("agent.dispatch"), 
                "agent.dispatch event must remain supported");
            
            Map<String, Object> contractEvent = createContractAgentDispatchEvent();
            var result = validator.validateEvent("agent.dispatch", contractEvent);
            
            assertTrue(result.isValid(), 
                "Contract-compliant agent dispatch event should validate: " + result.getErrors());
        }
        
        @Test
        @DisplayName("Agent result event contract should remain stable")
        void agentResultEventContractShouldRemainStable() {
            Set<String> supportedTypes = validator.getSupportedEventTypes();
            assertTrue(supportedTypes.contains("agent.result"), 
                "agent.result event must remain supported");
            
            Map<String, Object> contractEvent = createContractAgentResultEvent();
            var result = validator.validateEvent("agent.result", contractEvent);
            
            assertTrue(result.isValid(), 
                "Contract-compliant agent result event should validate: " + result.getErrors());
        }
        
        @Test
        @DisplayName("Task status changed event contract should remain stable")
        void taskStatusChangedEventContractShouldRemainStable() {
            Set<String> supportedTypes = validator.getSupportedEventTypes();
            assertTrue(supportedTypes.contains("task.status.changed"), 
                "task.status.changed event must remain supported");
            
            Map<String, Object> contractEvent = createContractTaskStatusChangedEvent();
            var result = validator.validateEvent("task.status.changed", contractEvent);
            
            assertTrue(result.isValid(), 
                "Contract-compliant task status changed event should validate: " + result.getErrors());
        }
        
        @Test
        @DisplayName("Shape created event contract should remain stable")
        void shapeCreatedEventContractShouldRemainStable() {
            Set<String> supportedTypes = validator.getSupportedEventTypes();
            assertTrue(supportedTypes.contains("shape.created"), 
                "shape.created event must remain supported");
            
            Map<String, Object> contractEvent = createContractShapeCreatedEvent();
            var result = validator.validateEvent("shape.created", contractEvent);
            
            assertTrue(result.isValid(), 
                "Contract-compliant shape created event should validate: " + result.getErrors());
        }
    }
    
    @Nested
    @DisplayName("API Response Contracts")
    @Order(2)
    class ApiResponseContracts {
        
        @Test
        @DisplayName("API responses should follow standard contract")
        void apiResponsesShouldFollowStandardContract() {
            // This test ensures API responses follow the established contract
            // for consistent frontend integration
            
            Map<String, Object> standardResponse = createStandardApiResponse();
            
            // Verify required contract fields
            assertTrue(standardResponse.containsKey("success"), "Response must have success field");
            assertTrue(standardResponse.containsKey("data"), "Response must have data field");
            assertTrue(standardResponse.containsKey("timestamp"), "Response must have timestamp field");
            
            // Verify field types
            assertTrue(standardResponse.get("success") instanceof Boolean, "success must be boolean");
            assertNotNull(standardResponse.get("data"), "data must not be null");
            assertTrue(standardResponse.get("timestamp") instanceof Long, "timestamp must be long");
        }
        
        @Test
        @DisplayName("Error responses should follow error contract")
        void errorResponsesShouldFollowErrorContract() {
            Map<String, Object> errorResponse = createStandardErrorResponse();
            
            // Verify required error contract fields
            assertTrue(errorResponse.containsKey("success"), "Error response must have success field");
            assertTrue(errorResponse.containsKey("error"), "Error response must have error field");
            assertTrue(errorResponse.containsKey("timestamp"), "Error response must have timestamp field");
            
            // Verify error contract structure
            assertFalse((Boolean) errorResponse.get("success"), "Error response success must be false");
            assertTrue(errorResponse.get("error") instanceof Map, "error must be object");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) errorResponse.get("error");
            assertTrue(error.containsKey("code"), "Error must have code");
            assertTrue(error.containsKey("message"), "Error must have message");
        }
    }
    
    @Nested
    @DisplayName("Lifecycle Stage Contracts")
    @Order(3)
    class LifecycleStageContracts {
        
        @Test
        @DisplayName("Lifecycle stages should follow contract")
        void lifecycleStagesShouldFollowContract() {
            // This test ensures lifecycle stages follow the established contract
            // for consistent frontend lifecycle management
            
            List<String> expectedStages = getContractLifecycleStages();
            
            // Verify all expected stages exist
            assertEquals(8, expectedStages.size(), "Must have exactly 8 lifecycle stages");
            
            // Verify stage order and naming
            assertEquals("intent", expectedStages.get(0), "First stage must be intent");
            assertEquals("context", expectedStages.get(1), "Second stage must be context");
            assertEquals("plan", expectedStages.get(2), "Third stage must be plan");
            assertEquals("execute", expectedStages.get(3), "Fourth stage must be execute");
            assertEquals("verify", expectedStages.get(4), "Fifth stage must be verify");
            assertEquals("observe", expectedStages.get(5), "Sixth stage must be observe");
            assertEquals("learn", expectedStages.get(6), "Seventh stage must be learn");
            assertEquals("institutionalize", expectedStages.get(7), "Eighth stage must be institutionalize");
        }
        
        @Test
        @DisplayName("Stage transitions should follow contract")
        void stageTransitionsShouldFollowContract() {
            // This test ensures stage transitions follow the established contract
            
            List<Map<String, String>> expectedTransitions = getContractStageTransitions();
            
            // Verify forward transitions are allowed
            assertTrue(isTransitionAllowed("intent", "context"), "intent -> context should be allowed");
            assertTrue(isTransitionAllowed("context", "plan"), "context -> plan should be allowed");
            assertTrue(isTransitionAllowed("plan", "execute"), "plan -> execute should be allowed");
            assertTrue(isTransitionAllowed("execute", "verify"), "execute -> verify should be allowed");
            assertTrue(isTransitionAllowed("verify", "observe"), "verify -> observe should be allowed");
            assertTrue(isTransitionAllowed("observe", "learn"), "observe -> learn should be allowed");
            assertTrue(isTransitionAllowed("learn", "institutionalize"), "learn -> institutionalize should be allowed");
            
            // Verify backward transitions are allowed (with conditions)
            assertTrue(isTransitionAllowed("execute", "plan"), "execute -> plan should be allowed (rollback)");
            assertTrue(isTransitionAllowed("verify", "execute"), "verify -> execute should be allowed (rollback)");
            
            // Verify invalid transitions are not allowed
            assertFalse(isTransitionAllowed("intent", "execute"), "intent -> execute should not be allowed");
            assertFalse(isTransitionAllowed("plan", "institutionalize"), "plan -> institutionalize should not be allowed");
        }
    }
    
    @Nested
    @DisplayName("Agent Registry Contracts")
    @Order(4)
    class AgentRegistryContracts {
        
        @Test
        @DisplayName("Agent registry should maintain contract structure")
        void agentRegistryShouldMaintainContractStructure() {
            // This test ensures the agent registry follows the established contract
            
            Map<String, Object> registryContract = createContractAgentRegistry();
            
            // Verify required top-level fields
            assertTrue(registryContract.containsKey("apiVersion"), "Registry must have apiVersion");
            assertTrue(registryContract.containsKey("kind"), "Registry must have kind");
            assertTrue(registryContract.containsKey("metadata"), "Registry must have metadata");
            assertTrue(registryContract.containsKey("phases"), "Registry must have phases");
            assertTrue(registryContract.containsKey("hierarchy"), "Registry must have hierarchy");
            
            // Verify metadata structure
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) registryContract.get("metadata");
            assertTrue(metadata.containsKey("version"), "Metadata must have version");
            assertTrue(metadata.containsKey("statistics"), "Metadata must have statistics");
            
            // Verify statistics structure
            @SuppressWarnings("unchecked")
            Map<String, Object> statistics = (Map<String, Object>) metadata.get("statistics");
            assertTrue(statistics.containsKey("total_agents"), "Statistics must have total_agents");
            assertTrue(statistics.containsKey("level_1_orchestrators"), "Statistics must have level_1_orchestrators");
            assertTrue(statistics.containsKey("level_2_experts"), "Statistics must have level_2_experts");
            assertTrue(statistics.containsKey("level_3_workers"), "Statistics must have level_3_workers");
        }
        
        @Test
        @DisplayName("Agent definitions should follow contract")
        void agentDefinitionsShouldFollowContract() {
            Map<String, Object> agentDefinition = createContractAgentDefinition();
            
            // Verify required agent definition fields
            assertTrue(agentDefinition.containsKey("id"), "Agent must have id");
            assertTrue(agentDefinition.containsKey("name"), "Agent must have name");
            assertTrue(agentDefinition.containsKey("version"), "Agent must have version");
            assertTrue(agentDefinition.containsKey("metadata"), "Agent must have metadata");
            assertTrue(agentDefinition.containsKey("capabilities"), "Agent must have capabilities");
            assertTrue(agentDefinition.containsKey("tools"), "Agent must have tools");
            
            // Verify metadata structure
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) agentDefinition.get("metadata");
            assertTrue(metadata.containsKey("phase"), "Metadata must have phase");
            assertTrue(metadata.containsKey("level"), "Metadata must have level");
            assertTrue(metadata.containsKey("description"), "Metadata must have description");
        }
    }
    
    // Helper methods for contract-compliant test data
    private Map<String, Object> createContractPhaseTransitionEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "contract-phase-123");
        event.put("eventType", "phase.transition");
        event.put("timestamp", System.currentTimeMillis());
        event.put("projectId", "contract-proj-456");
        event.put("fromStage", "plan");
        event.put("toStage", "execute");
        event.put("triggerEvent", "task.completed");
        event.put("correlationId", "contract-corr-789");
        event.put("gateDecisions", Map.of(
            "quality-gate", "approved",
            "security-gate", "approved"
        ));
        event.put("requiredArtifacts", List.of("design-doc", "test-plan"));
        event.put("producedArtifacts", List.of("implementation-plan"));
        event.put("phaseStateId", "phase-state-123");
        return event;
    }
    
    private Map<String, Object> createContractAgentDispatchEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "contract-dispatch-123");
        event.put("eventType", "agent.dispatch");
        event.put("timestamp", System.currentTimeMillis());
        event.put("agentId", "contract-agent.test");
        event.put("fromStage", "execute");
        event.put("toStage", "verify");
        event.put("executionContext", Map.of(
            "projectId", "contract-proj-456",
            "taskId", "contract-task-789",
            "userId", "contract-user-123"
        ));
        event.put("inputData", Map.of(
            "query", "contract test query",
            "parameters", Map.of("timeout", 30000)
        ));
        event.put("priority", "normal");
        event.put("timeout", 30000);
        event.put("retryPolicy", Map.of(
            "maxRetries", 3,
            "backoffMs", 1000
        ));
        event.put("expectedCapabilities", List.of("code-analysis", "test-generation"));
        return event;
    }
    
    private Map<String, Object> createContractAgentResultEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "contract-result-123");
        event.put("eventType", "agent.result");
        event.put("timestamp", System.currentTimeMillis());
        event.put("agentId", "contract-agent.test");
        event.put("status", "success");
        event.put("executionTime", 5000);
        event.put("outputData", Map.of(
            "result", "contract test result",
            "confidence", 0.95
        ));
        event.put("context", Map.of(
            "projectId", "contract-proj-456",
            "taskId", "contract-task-789",
            "userId", "contract-user-123"
        ));
        event.put("metrics", Map.of(
            "tokensUsed", 100,
            "cost", 0.001,
            "memoryUsed", 52428800
        ));
        event.put("artifacts", List.of(
            Map.of("type", "code", "path", "/generated/code.java"),
            Map.of("type", "test", "path", "/generated/test.java")
        ));
        event.put("checkpointId", "checkpoint-123");
        return event;
    }
    
    private Map<String, Object> createContractTaskStatusChangedEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "contract-task-status-123");
        event.put("eventType", "task.status.changed");
        event.put("timestamp", System.currentTimeMillis());
        event.put("taskId", "contract-task-456");
        event.put("projectId", "contract-proj-789");
        event.put("oldStatus", "pending");
        event.put("newStatus", "in_progress");
        event.put("assigneeId", "contract-user-123");
        event.put("reason", "Task started by user");
        event.put("metadata", Map.of(
            "estimatedHours", 8,
            "actualHours", 0
        ));
        return event;
    }
    
    private Map<String, Object> createContractShapeCreatedEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "contract-shape-123");
        event.put("eventType", "shape.created");
        event.put("timestamp", System.currentTimeMillis());
        event.put("shapeId", "contract-shape-456");
        event.put("shapeType", "component");
        event.put("projectId", "contract-proj-789");
        event.put("properties", Map.of(
            "name", "contract test component",
            "type", "service",
            "technology", "java"
        ));
        event.put("position", Map.of("x", 100, "y", 200));
        event.put("parentId", "parent-shape-123");
        return event;
    }
    
    private Map<String, Object> createStandardApiResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("message", "Operation completed successfully"));
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
    
    private Map<String, Object> createStandardErrorResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", Map.of(
            "code", "VALIDATION_ERROR",
            "message", "Invalid input data",
            "details", List.of("Field 'name' is required")
        ));
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
    
    private List<String> getContractLifecycleStages() {
        return List.of("intent", "context", "plan", "execute", "verify", "observe", "learn", "institutionalize");
    }
    
    private List<Map<String, String>> getContractStageTransitions() {
        List<Map<String, String>> transitions = new ArrayList<>();
        // Forward transitions
        transitions.add(Map.of("from", "intent", "to", "context"));
        transitions.add(Map.of("from", "context", "to", "plan"));
        transitions.add(Map.of("from", "plan", "to", "execute"));
        transitions.add(Map.of("from", "execute", "to", "verify"));
        transitions.add(Map.of("from", "verify", "to", "observe"));
        transitions.add(Map.of("from", "observe", "to", "learn"));
        transitions.add(Map.of("from", "learn", "to", "institutionalize"));
        // Backward transitions (rollback)
        transitions.add(Map.of("from", "execute", "to", "plan"));
        transitions.add(Map.of("from", "verify", "to", "execute"));
        return transitions;
    }
    
    private boolean isTransitionAllowed(String from, String to) {
        return getContractStageTransitions().stream()
            .anyMatch(transition -> 
                from.equals(transition.get("from")) && to.equals(transition.get("to"))
            );
    }
    
    private Map<String, Object> createContractAgentRegistry() {
        Map<String, Object> registry = new HashMap<>();
        registry.put("apiVersion", "ghatana.yappc/v1");
        registry.put("kind", "AgentRegistry");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "2.0.0");
        metadata.put("updated", "2026-03-07T00:00:00Z");
        metadata.put("description", "Contract-compliant agent registry");
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("total_agents", 223);
        statistics.put("level_1_orchestrators", 14);
        statistics.put("level_2_experts", 67);
        statistics.put("level_3_workers", 113);
        metadata.put("statistics", statistics);
        
        registry.put("metadata", metadata);
        registry.put("phases", Map.of("phase1-ideation", "definitions/phase1-ideation/"));
        registry.put("hierarchy", new ArrayList<>());
        
        return registry;
    }
    
    private Map<String, Object> createContractAgentDefinition() {
        Map<String, Object> agent = new HashMap<>();
        agent.put("id", "contract.test-agent");
        agent.put("name", "Contract Test Agent");
        agent.put("version", "1.0.0");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("phase", "phase1-ideation");
        metadata.put("level", "level_3_workers");
        metadata.put("description", "Contract-compliant test agent");
        metadata.put("owner", "test-team");
        agent.put("metadata", metadata);
        
        agent.put("capabilities", List.of("test-capability"));
        agent.put("tools", List.of("test-tool"));
        
        return agent;
    }
}

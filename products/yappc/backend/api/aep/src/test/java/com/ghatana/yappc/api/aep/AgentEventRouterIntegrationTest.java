/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC AEP - Agent Event Router Integration Test
 * 
 * Integration tests for YappcAgentEventRouter to ensure proper
 * event routing, schema validation, and agent coordination.
 */

package com.ghatana.yappc.api.aep;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentConfig;

/**
 * Integration tests for YappcAgentEventRouter.
 * 
 * Tests:
 * - Event routing to correct agents
 * - Schema validation during routing
 * - Agent execution and result handling
 * - Error handling and recovery
 * - Backpressure management
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("YappcAgentEventRouter Integration")
/**
 * @doc.type class
 * @doc.purpose Handles agent event router integration test operations
 * @doc.layer product
 * @doc.pattern Test
 */
class AgentEventRouterIntegrationTest {
    
    @Mock
    private TypedAgent mockAgent;
    
    @Mock
    private AgentConfig mockAgentConfig;
    
    private YappcAgentEventRouter router;
    private EventSchemaValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new EventSchemaValidator();
        router = new YappcAgentEventRouter(validator);
        
        // Setup mock agent
        when(mockAgent.getId()).thenReturn("test-agent");
        when(mockAgent.getVersion()).thenReturn("1.0.0");
        when(mockAgent.getConfig()).thenReturn(mockAgentConfig);
        when(mockAgent.isHealthy()).thenReturn(true);
    }
    
    @Nested
    @DisplayName("Agent Registration")
    class AgentRegistration {
        
        @Test
        @DisplayName("Should register agent successfully")
        void shouldRegisterAgentSuccessfully() {
            router.registerAgent("test.topic", mockAgent);
            
            assertTrue(router.isAgentRegistered("test.topic"), "Agent should be registered");
        }
        
        @Test
        @DisplayName("Should handle duplicate agent registration")
        void shouldHandleDuplicateRegistration() {
            router.registerAgent("test.topic", mockAgent);
            
            assertDoesNotThrow(() -> {
                router.registerAgent("test.topic", mockAgent);
            }, "Duplicate registration should not throw exception");
        }
    }
    
    @Nested
    @DisplayName("Event Routing")
    class EventRouting {
        
        @Test
        @DisplayName("Should route valid event to registered agent")
        void shouldRouteValidEventToAgent() {
            // Setup
            router.registerAgent("agent.dispatch", mockAgent);
            
            Map<String, Object> event = createValidAgentDispatchEvent();
            AgentResult mockResult = createMockAgentResult(true, "Success");
            
            when(mockAgent.process(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
            
            // Execute
            var future = router.routeEvent("agent.dispatch", event);
            
            // Verify
            assertDoesNotThrow(() -> {
                AgentResult result = future.get(5, TimeUnit.SECONDS);
                assertTrue(result.isSuccess(), "Agent processing should succeed");
            });
            
            verify(mockAgent, times(1)).process(any());
        }
        
        @Test
        @DisplayName("Should validate event schema before routing")
        void shouldValidateEventSchemaBeforeRouting() {
            router.registerAgent("phase.transition", mockAgent);
            
            Map<String, Object> invalidEvent = new HashMap<>();
            invalidEvent.put("eventType", "phase.transition");
            // Missing required fields
            
            var future = router.routeEvent("phase.transition", invalidEvent);
            
            assertThrows(Exception.class, () -> {
                future.get(5, TimeUnit.SECONDS);
            }, "Invalid event should cause routing failure");
            
            verify(mockAgent, never()).process(any());
        }
        
        @Test
        @DisplayName("Should handle events for unregistered topics")
        void shouldHandleUnregisteredTopics() {
            Map<String, Object> event = createValidAgentDispatchEvent();
            
            var future = router.routeEvent("unknown.topic", event);
            
            assertDoesNotThrow(() -> {
                // Should complete without agent processing
                future.get(5, TimeUnit.SECONDS);
            });
            
            verify(mockAgent, never()).process(any());
        }
    }
    
    @Nested
    @DisplayName("Agent Execution")
    class AgentExecution {
        
        @Test
        @DisplayName("Should handle successful agent execution")
        void shouldHandleSuccessfulExecution() {
            router.registerAgent("agent.dispatch", mockAgent);
            
            Map<String, Object> event = createValidAgentDispatchEvent();
            AgentResult mockResult = createMockAgentResult(true, "Task completed successfully");
            
            when(mockAgent.process(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
            
            var future = router.routeEvent("agent.dispatch", event);
            
            assertDoesNotThrow(() -> {
                AgentResult result = future.get(5, TimeUnit.SECONDS);
                assertTrue(result.isSuccess(), "Result should be successful");
                assertEquals("Task completed successfully", result.getExplanation());
            });
        }
        
        @Test
        @DisplayName("Should handle agent execution failure")
        void shouldHandleExecutionFailure() {
            router.registerAgent("agent.dispatch", mockAgent);
            
            Map<String, Object> event = createValidAgentDispatchEvent();
            AgentResult mockResult = createMockAgentResult(false, "Agent processing failed");
            
            when(mockAgent.process(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
            
            var future = router.routeEvent("agent.dispatch", event);
            
            assertDoesNotThrow(() -> {
                AgentResult result = future.get(5, TimeUnit.SECONDS);
                assertFalse(result.isSuccess(), "Result should indicate failure");
                assertEquals("Agent processing failed", result.getExplanation());
            });
        }
        
        @Test
        @DisplayName("Should handle agent execution timeout")
        void shouldHandleExecutionTimeout() {
            router.registerAgent("agent.dispatch", mockAgent);
            
            Map<String, Object> event = createValidAgentDispatchEvent();
            
            // Simulate slow agent
            when(mockAgent.process(any())).thenReturn(
                CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(10000); // Longer than test timeout
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return createMockAgentResult(true, "Delayed success");
                })
            );
            
            var future = router.routeEvent("agent.dispatch", event);
            
            assertThrows(Exception.class, () -> {
                future.get(2, TimeUnit.SECONDS); // Shorter timeout
            }, "Should timeout on slow agent execution");
        }
    }
    
    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {
        
        @Test
        @DisplayName("Should handle unhealthy agent")
        void shouldHandleUnhealthyAgent() {
            when(mockAgent.isHealthy()).thenReturn(false);
            router.registerAgent("agent.dispatch", mockAgent);
            
            Map<String, Object> event = createValidAgentDispatchEvent();
            
            var future = router.routeEvent("agent.dispatch", event);
            
            assertDoesNotThrow(() -> {
                // Should complete without processing unhealthy agent
                future.get(5, TimeUnit.SECONDS);
            });
            
            verify(mockAgent, never()).process(any());
        }
        
        @Test
        @DisplayName("Should handle agent processing exception")
        void shouldHandleProcessingException() {
            router.registerAgent("agent.dispatch", mockAgent);
            
            Map<String, Object> event = createValidAgentDispatchEvent();
            
            when(mockAgent.process(any())).thenReturn(
                CompletableFuture.failedFuture(new RuntimeException("Agent error"))
            );
            
            var future = router.routeEvent("agent.dispatch", event);
            
            assertThrows(Exception.class, () -> {
                future.get(5, TimeUnit.SECONDS);
            }, "Should propagate agent processing exception");
        }
    }
    
    @Nested
    @DisplayName("Schema Validation Integration")
    class SchemaValidationIntegration {
        
        @Test
        @DisplayName("Should validate all supported event types")
        void shouldValidateAllSupportedEventTypes() {
            router.registerAgent("phase.transition", mockAgent);
            router.registerAgent("agent.dispatch", mockAgent);
            router.registerAgent("agent.result", mockAgent);
            router.registerAgent("task.status.changed", mockAgent);
            router.registerAgent("shape.created", mockAgent);
            
            AgentResult mockResult = createMockAgentResult(true, "Success");
            when(mockAgent.process(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
            
            // Test each event type
            assertDoesNotThrow(() -> {
                router.routeEvent("phase.transition", createValidPhaseTransitionEvent())
                    .get(5, TimeUnit.SECONDS);
            });
            
            assertDoesNotThrow(() -> {
                router.routeEvent("agent.dispatch", createValidAgentDispatchEvent())
                    .get(5, TimeUnit.SECONDS);
            });
            
            assertDoesNotThrow(() -> {
                router.routeEvent("agent.result", createValidAgentResultEvent())
                    .get(5, TimeUnit.SECONDS);
            });
            
            assertDoesNotThrow(() -> {
                router.routeEvent("task.status.changed", createValidTaskStatusChangedEvent())
                    .get(5, TimeUnit.SECONDS);
            });
            
            assertDoesNotThrow(() -> {
                router.routeEvent("shape.created", createValidShapeCreatedEvent())
                    .get(5, TimeUnit.SECONDS);
            });
        }
    }
    
    // Helper methods
    private AgentResult createMockAgentResult(boolean success, String explanation) {
        return new AgentResult() {
            @Override
            public boolean isSuccess() {
                return success;
            }
            
            @Override
            public String getExplanation() {
                return explanation;
            }
            
            @Override
            public Map<String, Object> getData() {
                return Map.of("result", explanation);
            }
        };
    }
    
    private Map<String, Object> createValidPhaseTransitionEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", "phase-123");
        event.put("eventType", "phase.transition");
        event.put("timestamp", System.currentTimeMillis());
        event.put("projectId", "proj-456");
        event.put("fromStage", "plan");
        event.put("toStage", "execute");
        event.put("triggerEvent", "task.completed");
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

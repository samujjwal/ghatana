package com.ghatana.virtualorg.adapter;

import com.ghatana.platform.domain.domain.event.*;
import com.ghatana.virtualorg.v1.TaskProto;
import com.ghatana.virtualorg.v1.TaskResponseProto;
import com.ghatana.virtualorg.v1.DecisionProto;
import com.ghatana.virtualorg.v1.ToolCallProto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Factory for creating EventCloud events from virtual organization lifecycle events.
 *
 * <p><b>Purpose</b><br>
 * Translates virtual organization domain events (task lifecycle, decisions, tool executions)
 * into canonical EventCloud Event format for pattern matching, auditing, and downstream
 * processing. Ensures consistent event structure, correlation tracking, and metadata enrichment.
 *
 * <p><b>Architecture Role</b><br>
 * Factory in hexagonal architecture:
 * <ul>
 *   <li>Factory for EventCloud Event creation from virtual-org domain events</li>
 *   <li>Used by: Agents (task events), Tools (execution events), Security (auth events)</li>
 *   <li>Produces: Canonical Event objects with standardized structure</li>
 *   <li>Integrates with: EventEmitter (publishing), EventCloud (storage), Pattern Engine (matching)</li>
 * </ul>
 *
 * <p><b>Event Types Produced</b><br>
 * Task lifecycle events:
 * <ul>
 *   <li>com.ghatana.virtualorg.task.created - Task created and queued</li>
 *   <li>com.ghatana.virtualorg.task.started - Agent began task processing</li>
 *   <li>com.ghatana.virtualorg.task.completed - Task finished successfully</li>
 *   <li>com.ghatana.virtualorg.task.failed - Task failed with error</li>
 * </ul>
 *
 * Decision events:
 * <ul>
 *   <li>com.ghatana.virtualorg.decision.made - Decision made by agent with confidence</li>
 *   <li>com.ghatana.virtualorg.decision.escalated - Decision escalated to higher authority</li>
 * </ul>
 *
 * Execution events:
 * <ul>
 *   <li>com.ghatana.virtualorg.tool.executed - Tool execution completed</li>
 *   <li>com.ghatana.virtualorg.authorization.checked - Authorization check performed</li>
 * </ul>
 *
 * <p><b>Event Structure</b><br>
 * All events include:
 * <ul>
 *   <li>type: Fully-qualified event type (namespace.entity.action)</li>
 *   <li>id: Unique event ID (UUID)</li>
 *   <li>correlationId: Correlation ID for distributed tracing</li>
 *   <li>timestamp: Event occurrence time (Instant)</li>
 *   <li>tenantId: Multi-tenancy isolation (default: "default")</li>
 *   <li>version: Event schema version (1.0.0)</li>
 *   <li>payload: Event-specific data (agentId, taskId, decision, etc.)</li>
 * </ul>
 *
 * <p><b>Correlation Tracking</b><br>
 * Correlation IDs enable distributed tracing:
 * <ul>
 *   <li>Task events share same correlationId for request lifecycle</li>
 *   <li>Decision events link to parent task via correlationId</li>
 *   <li>Tool events link to triggering agent via correlationId</li>
 *   <li>Authorization events link to protected operation via correlationId</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Task lifecycle events
 * String correlationId = UUID.randomUUID().toString();
 * 
 * Event taskStarted = VirtualOrgEventFactory.createTaskStartedEvent(
 *     "agent-senior-eng-001", task, correlationId);
 * eventEmitter.emit(taskStarted);
 * 
 * Event taskCompleted = VirtualOrgEventFactory.createTaskCompletedEvent(
 *     "agent-senior-eng-001", task, response, correlationId);
 * eventEmitter.emit(taskCompleted);
 * 
 * // Decision event
 * Event decisionMade = VirtualOrgEventFactory.createDecisionMadeEvent(
 *     "agent-cto-001", decision, correlationId);
 * eventEmitter.emit(decisionMade);
 * 
 * // Tool execution event
 * Event toolExecuted = VirtualOrgEventFactory.createToolExecutedEvent(
 *     "agent-devops-001", toolCall, correlationId);
 * eventEmitter.emit(toolExecuted);
 * 
 * // Authorization event
 * Event authChecked = VirtualOrgEventFactory.createAuthorizationCheckedEvent(
 *     "agent-pm-001", resourceType, action, allowed, correlationId);
 * eventEmitter.emit(authChecked);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Stateless factory with thread-safe static methods. Safe for concurrent use.
 *
 * @see EventEmitter
 * @see Event
 * @doc.type class
 * @doc.purpose Factory for EventCloud events from virtual-org domain events
 * @doc.layer product
 * @doc.pattern Factory
 */
public class VirtualOrgEventFactory {
    private static final Logger log = LoggerFactory.getLogger(VirtualOrgEventFactory.class);
    private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();
    
    private static final String NAMESPACE = "com.ghatana.virtualorg";
    private static final String VERSION = "1.0.0";
    private static final String TENANT_ID = "default";
    
    /**
     * Creates a "task.started" event when a task begins processing.
     */
    public static Event createTaskStartedEvent(String agentId, TaskProto task, String correlationId) {
        return buildEvent(
            "com.ghatana.virtualorg.task.started",
            correlationId != null ? correlationId : UUID.randomUUID().toString(),
            mapOf(
                "agentId", agentId,
                "taskId", task.getTaskId(),
                "taskType", task.getType().name(),
                "priority", String.valueOf(task.getPriority().getNumber()),
                "description", task.getDescription()
            )
        );
    }
    
    /**
     * Creates a "task.completed" event when a task finishes successfully.
     */
    public static Event createTaskCompletedEvent(
        String agentId, 
        TaskResponseProto response, 
        long durationMs,
        String correlationId) {
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("agentId", agentId);
        payload.put("taskId", response.getTaskId());
        payload.put("success", true);
        payload.put("result", response.getResult());
        payload.put("reasoning", response.getReasoning());
        payload.put("durationMs", durationMs);
        if (response.hasMetrics()) {
            payload.put("confidenceScore", response.getMetrics().getConfidenceScore());
        }
        
        return buildEvent(
            "com.ghatana.virtualorg.task.completed",
            correlationId != null ? correlationId : UUID.randomUUID().toString(),
            payload
        );
    }
    
    /**
     * Creates a "task.failed" event when a task fails.
     */
    public static Event createTaskFailedEvent(
        String agentId,
        String taskId,
        String errorMessage,
        long durationMs,
        String correlationId) {
        
        return buildEvent(
            "com.ghatana.virtualorg.task.failed",
            correlationId != null ? correlationId : UUID.randomUUID().toString(),
            mapOf(
                "agentId", agentId,
                "taskId", taskId,
                "error", errorMessage,
                "durationMs", String.valueOf(durationMs),
                "failed", "true"
            )
        );
    }
    
    /**
     * Creates a "decision.made" event when an agent makes a decision.
     */
    public static Event createDecisionMadeEvent(
        String agentId,
        DecisionProto decision,
        String correlationId) {
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("agentId", agentId);
        payload.put("decisionId", decision.getDecisionId());
        payload.put("confidence", String.valueOf(decision.getConfidence()));
        payload.put("reasoning", decision.getReasoning());
        
        return buildEvent(
            "com.ghatana.virtualorg.decision.made",
            correlationId != null ? correlationId : UUID.randomUUID().toString(),
            payload
        );
    }
    
    /**
     * Creates a "tool.executed" event when an agent executes a tool.
     */
    public static Event createToolExecutedEvent(
        String agentId,
        ToolCallProto toolCall,
        long durationMs,
        String correlationId) {
        
        return buildEvent(
            "com.ghatana.virtualorg.tool.executed",
            correlationId != null ? correlationId : UUID.randomUUID().toString(),
            mapOf(
                "agentId", agentId,
                "toolName", toolCall.getToolName(),
                "success", String.valueOf(toolCall.getSuccess()),
                "durationMs", String.valueOf(toolCall.getDurationMs()),
                "result", toolCall.getResult()
            )
        );
    }
    
    /**
     * Creates an "authorization.checked" event for audit trail.
     */
    public static Event createAuthorizationEvent(
        String agentId,
        String principal,
        String resource,
        String action,
        boolean allowed,
        String correlationId) {
        
        return buildEvent(
            "com.ghatana.virtualorg.authorization.checked",
            correlationId != null ? correlationId : UUID.randomUUID().toString(),
            mapOf(
                "agentId", agentId,
                "principal", principal,
                "resource", resource,
                "action", action,
                "allowed", String.valueOf(allowed)
            )
        );
    }
    
    /**
     * Creates an "agent.started" lifecycle event.
     */
    public static Event createAgentStartedEvent(String agentId, String role) {
        return buildEvent(
            "com.ghatana.virtualorg.agent.started",
            UUID.randomUUID().toString(),
            mapOf(
                "agentId", agentId,
                "role", role
            )
        );
    }
    
    /**
     * Creates an "agent.stopped" lifecycle event.
     */
    public static Event createAgentStoppedEvent(String agentId) {
        return buildEvent(
            "com.ghatana.virtualorg.agent.stopped",
            UUID.randomUUID().toString(),
            mapOf("agentId", agentId)
        );
    }
    
    /**
     * Creates an "agent.error" lifecycle event.
     */
    public static Event createAgentErrorEvent(String agentId, String errorMessage) {
        return buildEvent(
            "com.ghatana.virtualorg.agent.error",
            UUID.randomUUID().toString(),
            mapOf(
                "agentId", agentId,
                "error", errorMessage
            )
        );
    }
    
    /**
     * Creates a generic event with the given type, agent ID, and data.
     * Used for custom or ad-hoc events not covered by specific factory methods.
     *
     * @param eventType the event type (e.g., "com.ghatana.virtualorg.custom.event")
     * @param agentId the agent ID emitting the event
     * @param data additional event data
     * @return Event instance
     */
    public static Event createEvent(
            @org.jetbrains.annotations.NotNull String eventType,
            String agentId,
            @org.jetbrains.annotations.NotNull Map<String, String> data) {
        
        Map<String, Object> payload = new HashMap<>(data);
        if (agentId != null) {
            payload.put("agentId", agentId);
        }
        
        return buildEvent(
            eventType,
            UUID.randomUUID().toString(),
            payload
        );
    }
    
    // =============================
    // Helper Methods
    // =============================
    
    private static Event buildEvent(String eventType, String correlationId, Map<String, ?> payload) {
        try {
            // Create EventId
            EventId eventId = new SimpleEventId(
                UUID.randomUUID().toString(),
                eventType,
                VERSION,
                TENANT_ID
            );
            
            // Create EventTime using current instant
            Instant now = Instant.now();
            long nowMillis = now.toEpochMilli();
            EventTime eventTime = EventTime.builder()
                .detectionTimePoint(com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis))
                .occurrenceTime(com.ghatana.platform.types.time.GTimeInterval.between(
                    com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis),
                    com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis)
                ))
                .validDuration(new com.ghatana.platform.types.time.GTimeValue(-1, com.ghatana.platform.types.time.GTimeUnit.MILLISECONDS))
                .boundingInterval(com.ghatana.platform.types.time.GTimeInterval.between(
                    com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis),
                    com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis)
                ))
                .build();
            
            // Create EventStats
            EventStats stats = EventStats.builder()
                .withSizeInBytes(objectMapper.writeValueAsString(payload).length())
                .build();
            
            // Create EventRelations
            EventRelations relations = EventRelations.builder().build();
            
            // Create headers with correlation ID
            Map<String, String> headers = new HashMap<>();
            headers.put("correlationId", correlationId);
            headers.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            // Cast payload keys to String for consistency
            Map<String, Object> typedPayload = new HashMap<>(payload);
            
            return GEvent.builder()
                .id(eventId)
                .time(eventTime)
                .location(null)
                .stats(stats)
                .relations(relations)
                .headers(headers)
                .payload(typedPayload)
                .intervalBased(false)
                .provenance(java.util.List.of())
                .build();
            
        } catch (Exception e) {
            log.error("Failed to build event: {}", eventType, e);
            throw new RuntimeException("Event construction failed", e);
        }
    }
    
    /**
     * Simple EventId implementation.
     */
    private static class SimpleEventId implements EventId {
        private final String id;
        private final String eventType;
        private final String version;
        private final String tenantId;
        
        SimpleEventId(String id, String eventType, String version, String tenantId) {
            this.id = id;
            this.eventType = eventType;
            this.version = version;
            this.tenantId = tenantId;
        }
        
        @Override
        public String getId() {
            return id;
        }
        
        @Override
        public String getEventType() {
            return eventType;
        }
        
        @Override
        public String getVersion() {
            return version;
        }
        
        @Override
        public String getTenantId() {
            return tenantId;
        }
    }
    
    /**
     * Convenience method for creating a map of string key-value pairs.
     */
    @SafeVarargs
    private static <K, V> Map<K, V> mapOf(K k1, V v1, Object... rest) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        
        for (int i = 0; i < rest.length; i += 2) {
            if (i + 1 < rest.length) {
                map.put((K) rest[i], (V) rest[i + 1]);
            }
        }
        return map;
    }
}
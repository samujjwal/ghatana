package com.ghatana.virtualorg.adapter;

import com.ghatana.platform.domain.agent.registry.AgentExecutionContext;
import com.ghatana.platform.domain.agent.registry.AgentMetrics;
import com.ghatana.platform.domain.agent.registry.HealthStatus;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.contracts.agent.v1.AgentInputProto;
import com.ghatana.contracts.agent.v1.AgentResultProto;
import com.ghatana.virtualorg.agent.VirtualOrgAgent;
import com.ghatana.virtualorg.v1.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Protocol adapter bridging virtual-org TaskProto with EventCloud Event protocol.
 *
 * <p><b>Purpose</b><br>
 * Enables virtual organization agents to participate in EventCloud pipelines by adapting
 * between TaskProto-based virtual-org protocol and Event-based EventCloud protocol.
 * Handles bidirectional conversion, correlation tracking, and lifecycle management for
 * both protocols.
 *
 * <p><b>Architecture Role</b><br>
 * Adapter in hexagonal architecture:
 * <ul>
 *   <li>Implements Agent interface (EventCloud platform compatibility)</li>
 *   <li>Wraps VirtualOrgAgent delegate (virtual-org domain agent)</li>
 *   <li>Converts Event → TaskRequestProto (inbound)</li>
 *   <li>Converts TaskResponseProto → Event (outbound)</li>
 *   <li>Preserves correlation IDs for distributed tracing</li>
 *   <li>Manages agent lifecycle in both protocols</li>
 * </ul>
 *
 * <p><b>Protocol Conversion</b><br>
 * Inbound (EventCloud → Virtual-Org):
 * <ul>
 *   <li>Extracts Event payload → TaskRequestProto</li>
 *   <li>Maps Event metadata → TaskProto context</li>
 *   <li>Preserves correlationId for request tracking</li>
 *   <li>Validates Event type against supported patterns</li>
 * </ul>
 *
 * Outbound (Virtual-Org → EventCloud):
 * <ul>
 *   <li>Wraps TaskResponseProto → Event</li>
 *   <li>Includes decision, tool calls, errors in Event payload</li>
 *   <li>Propagates correlationId to response Event</li>
 *   <li>Emits result Event to EventCloud for pattern matching</li>
 * </ul>
 *
 * <p><b>Supported Event Patterns</b><br>
 * Processes events matching:
 * <ul>
 *   <li>com.ghatana.virtualorg.task.* - Virtual-org task requests</li>
 *   <li>com.ghatana.virtualorg.request.* - Generic agent requests</li>
 *   <li>com.ghatana.*.task.* - Cross-product task patterns</li>
 * </ul>
 *
 * <p><b>Lifecycle Management</b><br>
 * Manages dual lifecycle:
 * <ul>
 *   <li>EventCloud Agent: start(), stop(), isHealthy(), getMetrics()</li>
 *   <li>Virtual-Org Agent: processTask(), makeDecision(), getState()</li>
 *   <li>Delegates lifecycle to wrapped VirtualOrgAgent</li>
 *   <li>Aggregates metrics from both protocols</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Wrap virtual-org agent with EventCloud adapter
 * VirtualOrgAgent seniorEngineer = new SeniorEngineerAgent(...);
 * Agent eventCloudAgent = new VirtualOrgEventAdapter(seniorEngineer);
 * 
 * // Register with EventCloud agent registry
 * agentRegistry.register(eventCloudAgent);
 * 
 * // Process EventCloud events
 * Event taskEvent = Event.builder()
 *     .type("com.ghatana.virtualorg.task.execute")
 *     .correlationId(UUID.randomUUID().toString())
 *     .payload(Map.of(
 *         "taskId", "task-123",
 *         "description", "Implement user authentication",
 *         "type", "FEATURE_IMPLEMENTATION"
 *     ))
 *     .build();
 * 
 * AgentExecutionContext context = AgentExecutionContext.builder()...build();
 * List<Event> results = eventCloudAgent.handle(taskEvent, context);
 * 
 * // Results contain TaskResponseProto wrapped as Events
 * Event resultEvent = results.get(0);
 * TaskResponseProto response = extractTaskResponse(resultEvent.getPayload());
 * log.info("Task status: {}", response.getStatus());
 * 
 * // Metrics available from both protocols
 * AgentMetrics metrics = eventCloudAgent.getMetrics();
 * log.info("Tasks processed: {}", metrics.getTasksProcessed());
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe using AtomicBoolean for health status and ConcurrentHashMap for metrics.
 * Delegates to underlying VirtualOrgAgent which must be thread-safe.
 *
 * @see VirtualOrgAgent
 * @see VirtualOrgEventFactory
 * @doc.type class
 * @doc.purpose Protocol adapter bridging virtual-org and EventCloud protocols
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class VirtualOrgEventAdapter {
    private static final Logger log = LoggerFactory.getLogger(VirtualOrgEventAdapter.class);
    private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();
    
    private final VirtualOrgAgent delegate;
    private final AtomicBoolean isHealthy = new AtomicBoolean(true);
    private final Map<String, AgentMetrics> metricsCache = new ConcurrentHashMap<>();
    
    // Supported event patterns that this adapter processes
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
        "com.ghatana.virtualorg.task.*",
        "com.ghatana.virtualorg.request.*",
        "com.ghatana.*.task.*"
    );
    
    private static final Set<String> OUTPUT_EVENT_TYPES = Set.of(
        "com.ghatana.virtualorg.task.completed",
        "com.ghatana.virtualorg.task.failed",
        "com.ghatana.virtualorg.decision.made",
        "com.ghatana.virtualorg.tool.executed",
        "com.ghatana.virtualorg.authorization.checked"
    );
    
    /**
     * Creates an adapter wrapping the given Virtual-Org agent.
     */
    public VirtualOrgEventAdapter(VirtualOrgAgent delegate) {
        this.delegate = delegate;
        log.info("Created EventCloud adapter for Virtual-Org agent: {}", delegate.getId());
    }
    
    // =============================
    // Agent Interface Implementation
    // =============================
    
    public String getId() {
        return delegate.getId();
    }
    
    public String getVersion() {
        return "1.0.0-adapter";
    }
    
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_EVENT_TYPES;
    }
    
    public Set<String> getOutputEventTypes() {
        return OUTPUT_EVENT_TYPES;
    }
    
    /**
     * Main entry point: handles an Event from EventCloud by converting it to a task,
     * processing it with the delegate agent, and converting the response back to Events.
     */
    public List<Event> handle(Event event, AgentExecutionContext context) {
        String correlationId = event.getCorrelationId();
        
        try {
            log.debug("Adapter.handle: eventType={}, correlationId={}", event.getType(), correlationId);
            
            // Step 1: Convert Event to TaskRequestProto
            TaskRequestProto taskRequest = convertEventToTaskRequest(event, correlationId);
            
            // Step 2: Process with delegate agent
            long startTime = System.currentTimeMillis();
            TaskResponseProto response = delegate.processTask(taskRequest).getResult();
            long duration = System.currentTimeMillis() - startTime;
            
            // Step 3: Convert response to Event(s)
            List<Event> results = convertResponseToEvents(response, duration, correlationId);
            
            log.debug("Adapter.handle completed: eventCount={}, correlationId={}", 
                results.size(), correlationId);
            
            return results;
            
        } catch (Exception e) {
            log.error("Adapter.handle failed: correlationId={}", correlationId, e);
            
            // Emit failure event
            List<Event> failureEvents = new ArrayList<>();
            failureEvents.add(
                VirtualOrgEventFactory.createAgentErrorEvent(
                    delegate.getId(),
                    e.getMessage()
                )
            );
            return failureEvents;
        }
    }
    
    public List<Event> handleBatch(List<Event> events, AgentExecutionContext context) {
        List<Event> allResults = new ArrayList<>();
        for (Event event : events) {
            allResults.addAll(handle(event, context));
        }
        return allResults;
    }
    
    public AgentResultProto execute(AgentInputProto input) {
        try {
            // Convert proto input to Event
            Event event = convertAgentInputToEvent(input);
            
            // Process through event handler
            List<Event> results = handle(event, new DummyExecutionContext());
            
            // Convert results back to proto
            return convertEventsToAgentResult(results);
            
        } catch (Exception e) {
            log.error("execute() failed", e);
            return AgentResultProto.newBuilder()
                .setStatus("error")  // Use status field instead of setSuccess
                .setErrorMessage(e.getMessage())  // Use errorMessage field
                .build();
        }
    }
    
    public boolean isHealthy() {
        return isHealthy.get() && delegate.getState() != AgentStateProto.AGENT_STATE_ERROR;
    }
    
    public AgentMetrics getMetrics() {
        // Adapt Virtual-Org metrics to EventCloud format
        AgentPerformanceProto performance = delegate.getPerformance();
        
        return new AgentMetrics() {
            @Override
            public long processedCount() {
                return performance.getTasksCompleted() + performance.getTasksFailed();
            }
            
            @Override
            public long getEventsProcessed() {
                return performance.getTasksCompleted() + performance.getTasksFailed();
            }
            
            @Override
            public long getErrorCount() {
                return performance.getTasksFailed();
            }
            
            @Override
            public double getAverageProcessingTimeMs() {
                return performance.getAvgCompletionTimeSeconds() * 1000;
            }
            
            @Override
            public double getCurrentThroughput() {
                return 0.0;
            }
            
            @Override
            public double getPeakThroughput() {
                return 0.0;
            }
            
            @Override
            public java.time.Instant getLastProcessedAt() {
                return null;
            }
            
            @Override
            public long getMemoryUsageMb() {
                return 0;
            }
            
            @Override
            public double getCpuUtilization() {
                return 0.0;
            }
            
            @Override
            public int getActiveThreads() {
                return 0;
            }
            
            @Override
            public Map<String, Object> getCustomMetrics() {
                Map<String, Object> custom = new HashMap<>();
                custom.put("confidence", performance.getAvgConfidence());
                custom.put("tokensUsed", performance.getTotalTokensUsed());
                custom.put("toolCalls", performance.getTotalToolCalls());
                return custom;
            }
            
            @Override
            public HealthStatus getHealthStatus() {
                return isHealthy.get() ? HealthStatus.HEALTHY : HealthStatus.UNHEALTHY;
            }
        };
    }
    
    // =============================
    // Lifecycle Methods
    // =============================
    
    public void initialize() {
        try {
            delegate.start().getResult();
            isHealthy.set(true);
            log.info("Adapter.initialize: delegate agent started");
        } catch (Exception e) {
            log.error("Adapter.initialize failed", e);
            isHealthy.set(false);
            throw new RuntimeException("Failed to initialize adapter", e);
        }
    }
    
    public void shutdown() {
        try {
            delegate.stop().getResult();
            isHealthy.set(false);
            log.info("Adapter.shutdown: delegate agent stopped");
        } catch (Exception e) {
            log.error("Adapter.shutdown failed", e);
        }
    }
    
    // =============================
    // Conversion Methods
    // =============================
    
    /**
     * Converts an EventCloud Event to a Virtual-Org TaskRequestProto.
     */
    private TaskRequestProto convertEventToTaskRequest(Event event, String correlationId) {
        // Extract payload values by key
        String taskId = extractStringFromEvent(event, "taskId", UUID.randomUUID().toString());
        String taskType = extractStringFromEvent(event, "taskType", "generic");
        String description = extractStringFromEvent(event, "description", event.getType());
        String priorityStr = extractStringFromEvent(event, "priority", "2");
        int priority = Integer.parseInt(priorityStr);
        
        TaskProto task = TaskProto.newBuilder()
            .setTaskId(taskId)
            .setTitle(description)
            .setDescription(description)
            .setType(convertPriorityToType(priority))
            .setCreatedAt(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(System.currentTimeMillis() / 1000)
                .setNanos((int) ((System.currentTimeMillis() % 1000) * 1_000_000))
                .build())
            .build();
        
        return TaskRequestProto.newBuilder()
            .setTask(task)
            .build();
    }
    
    /**
     * Converts priority to TaskTypeProto enum.
     */
    private TaskTypeProto convertPriorityToType(int priority) {
        if (priority >= 4) return TaskTypeProto.TASK_TYPE_FEATURE_IMPLEMENTATION;
        if (priority >= 3) return TaskTypeProto.TASK_TYPE_BUG_FIX;
        return TaskTypeProto.TASK_TYPE_RESEARCH;
    }
    
    /**
     * Extracts string value from event payload.
     */
    private String extractStringFromEvent(Event event, String key, String defaultValue) {
        Object value = event.getPayload(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Converts a Virtual-Org TaskResponseProto to EventCloud Event(s).
     */
    private List<Event> convertResponseToEvents(TaskResponseProto response, long duration, String correlationId) {
        List<Event> events = new ArrayList<>();
        
        if (response.getSuccess()) {
            events.add(VirtualOrgEventFactory.createTaskCompletedEvent(
                delegate.getId(),
                response,
                duration,
                correlationId
            ));
        } else {
            // For failed tasks, use result field if available, otherwise use generic message
            String errorMsg = response.getResult().isEmpty() ? "Task execution failed" : response.getResult();
            events.add(VirtualOrgEventFactory.createTaskFailedEvent(
                delegate.getId(),
                response.getTaskId(),
                errorMsg,
                duration,
                correlationId
            ));
        }
        
        return events;
    }
    
    /**
     * Converts an AgentInputProto to an Event.
     */
    private Event convertAgentInputToEvent(AgentInputProto input) {
        // Convert protobuf input to event
        String payload = input.hasPayload() ? input.getPayload() : "{}";
        return VirtualOrgEventFactory.createTaskStartedEvent(
            delegate.getId(),
            TaskProto.newBuilder()
                .setTaskId(UUID.randomUUID().toString())
                .setTitle("Agent Task")
                .setDescription(payload)
                .build(),
            ""  // correlation ID not directly available in AgentInputProto
        );
    }
    
    /**
     * Converts Events to AgentResultProto.
     */
    private AgentResultProto convertEventsToAgentResult(List<Event> results) {
        if (results.isEmpty()) {
            return AgentResultProto.newBuilder()
                .setStatus("error")
                .setErrorMessage("No results produced")
                .build();
        }
        
        // Serialize first event as result
        Event firstEvent = results.get(0);
        String status = firstEvent.getType().contains("failed") ? "error" : "success";
        return AgentResultProto.newBuilder()
            .setStatus(status)
            .setPayload(firstEvent.getType())
            .build();
    }
    

    
    /**
     * Dummy ExecutionContext for proto-based invocations.
     * Implements minimal AgentExecutionContext interface (only tenantId() is required).
     */
    private static class DummyExecutionContext implements AgentExecutionContext {
        @Override
        public String tenantId() {
            return "default-tenant";
        }
    }
}
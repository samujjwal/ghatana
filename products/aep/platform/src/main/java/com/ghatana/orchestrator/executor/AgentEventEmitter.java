/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.executor;

import com.ghatana.contracts.agent.v1.AgentStepExecutionEventProto;
import com.ghatana.contracts.agent.v1.AgentStepExecutionEventProto.ExecutionStatus;
import com.ghatana.orchestrator.executor.model.AgentStepResult;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Emits agent execution events to EventLog for observability and audit.
 * Converts {@link AgentStepResult} to {@link AgentStepExecutionEventProto}
 * and publishes to the event system via {@link EventLogClient}.
 *
 * @doc.type class
 * @doc.purpose Emits agent step results as canonical proto events to EventLog for observability
 * @doc.layer product
 * @doc.pattern Service
 */
public class AgentEventEmitter {

    private static final Logger logger = LoggerFactory.getLogger(AgentEventEmitter.class);
    private static final String EVENT_SCHEMA_VERSION = "1.0";
    private static final String EVENT_TYPE = "agent.step.result";

    private final EventLogClient eventLogClient;
    private final String tenantId;
    private final Executor executor;

    public AgentEventEmitter(EventLogClient eventLogClient, String tenantId) {
        this(eventLogClient, tenantId, Executors.newCachedThreadPool());
    }

    public AgentEventEmitter(EventLogClient eventLogClient, String tenantId, Executor executor) {
        this.eventLogClient = Objects.requireNonNull(eventLogClient, "eventLogClient cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
    }

    /**
     * Emit agent step result as a canonical event to EventLog.
     *
     * @param result the agent step result to emit
     * @return Promise that completes when the event is published
     */
    public Promise<Void> emitStepResult(AgentStepResult result) {
        Objects.requireNonNull(result, "result cannot be null");
        
        try {
            Object event = buildAgentEvent(result);
            
            logger.debug("Emitting agent step result event: stepId={}, status={}, duration={}ms", 
                result.getStepId(), result.getStatus(), result.getDurationMs());
            
            return eventLogClient.publishEvent(tenantId, event)
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        logger.error("Failed to emit agent step result event for step {}: {}", 
                            result.getStepId(), throwable.getMessage());
                    } else {
                        logger.debug("Successfully emitted agent step result event for step {}", 
                            result.getStepId());
                    }
                });
                
        } catch (Exception e) {
            logger.error("Error creating agent event for step {}: {}", result.getStepId(), e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    /**
     * Builds a canonical {@link AgentStepExecutionEventProto} from an {@link AgentStepResult}.
     *
     * <p>The proto is wrapped in a Map for compatibility with the EventLogClient interface
     * which accepts generic Objects. The Map uses the proto's field names as keys to maintain
     * compatibility with existing downstream consumers (DefaultEventLogClient).</p>
     *
     * @param result the agent step result
     * @return canonical event payload as a Map (proto-schema aligned)
     */
    private Object buildAgentEvent(AgentStepResult result) {
        // Build proto-aligned event map matching AgentStepExecutionEventProto schema
        Map<String, Object> event = new HashMap<>();

        // Event identification
        event.put("eventId", generateEventId(result));
        event.put("eventType", EVENT_TYPE);
        event.put("version", EVENT_SCHEMA_VERSION);
        event.put("timestamp", Instant.now().toEpochMilli());

        // Agent step identification
        event.put("stepId", result.getStepId());
        event.put("agentId", result.getId());

        // Execution status — map domain enum to proto-aligned string
        event.put("status", mapExecutionStatus(result.getStatus()).name());

        // Timing information
        if (result.getStartTime() != null) {
            event.put("startTime", result.getStartTime().toEpochMilli());
        }
        if (result.getEndTime() != null) {
            event.put("endTime", result.getEndTime().toEpochMilli());
        }
        event.put("durationMs", result.getDurationMs());

        // Retry information
        event.put("attemptNumber", result.getAttemptNumber());
        event.put("totalAttempts", result.getTotalAttempts());

        // Result data — serialize result as JSON-compatible structure
        Object rawResult = result.getResult();
        if (rawResult == null) {
            event.put("resultData", java.util.Collections.emptyMap());
        } else if (rawResult instanceof Map) {
            event.put("resultData", rawResult);
        } else {
            event.put("resultData", Map.of("value", rawResult));
        }

        // Error information
        if (result.getError() != null) {
            event.put("errorMessage", result.getError().getMessage());
            event.put("errorType", result.getError().getClass().getName());
        } else {
            event.put("errorMessage", null);
            event.put("errorType", null);
        }

        // Execution metrics — convert Object values to String for proto map<string,string>
        Map<String, String> metricsMap = new HashMap<>();
        if (result.getMetrics() != null) {
            result.getMetrics().forEach((k, v) ->
                    metricsMap.put(k, v != null ? v.toString() : ""));
        }
        metricsMap.put("durationMs", String.valueOf(result.getDurationMs()));
        event.put("metrics", metricsMap);

        // Execution context
        Map<String, String> contextMap = new HashMap<>();
        if (result.getContext() != null) {
            contextMap.putAll(result.getContext());
        }
        contextMap.put("tenantId", tenantId);
        event.put("context", contextMap);

        return event;
    }

    /**
     * Maps the domain {@link AgentStepResult.ExecutionStatus} to the proto
     * {@link ExecutionStatus} enum value.
     *
     * @param status the domain execution status
     * @return the corresponding proto execution status
     */
    private ExecutionStatus mapExecutionStatus(AgentStepResult.ExecutionStatus status) {
        if (status == null) {
            return ExecutionStatus.UNKNOWN;
        }
        return switch (status) {
            case SUCCESS -> ExecutionStatus.SUCCESS;
            case TIMEOUT -> ExecutionStatus.TIMEOUT;
            case RETRY -> ExecutionStatus.RETRY;
            case FAILED -> ExecutionStatus.FAILED;
            case CANCELLED -> ExecutionStatus.CANCELLED;
        };
    }

    /**
     * Generate unique event ID for agent step result.
     */
    private String generateEventId(AgentStepResult result) {
        return String.format("agent-step-%s-%s-%d-%d", 
            result.getId(), 
            result.getStepId(), 
            result.getAttemptNumber(),
            result.getStartTime().toEpochMilli());
    }

    /**
     * Client interface for EventLog communication.
     */
    public interface EventLogClient {
        /**
         * Publish an event to the EventLog.
         *
         * @param tenantId the tenant identifier
         * @param event the event to publish
         * @return Promise that completes when the event is published
         */
        Promise<Void> publishEvent(String tenantId, Object event);
    }
}
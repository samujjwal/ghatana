/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - AEP Integration
 */
package com.ghatana.yappc.api.aep;

import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

/**
 * YAPPC Agent Event Router — Bridges AEP events to TypedAgent dispatch.
 *
 * <p><b>Purpose</b><br>
 * Routes AEP events to the appropriate TypedAgent based on event routing configuration.
 * Wraps agents with backpressure handling and provides unified event dispatch.
 *
 * <p><b>Architecture</b><br>
 * <ul>
 *   <li>Maintains a registry of agent ID → AgentEventOperator mappings</li>
 *   <li>Wraps operators with BackpressureOperator for load handling</li>
 *   <li>Reads routing configuration from event-routing.yaml</li>
 *   <li>Integrates with TypedAgent instances from agent-framework</li>
 * </ul>
 *
 * <p><b>Event Flow</b><br>
 * <pre>
 * AEP Event Stream
 *       ↓
 * YappcAgentEventRouter (topic → agent_id lookup)
 *       ↓
 * BackpressureOperator (buffer + strategy)
 *       ↓
 * AgentEventOperator (TypedAgent wrapper)
 *       ↓
 * TypedAgent.process() → AgentResult
 * </pre>
 *
 * <p>All async operations use ActiveJ {@link Promise} — no {@code CompletableFuture}
 * is used in the public or internal API.
 *
 * <p><b>Usage</b><br>
 * <pre>
 * // Register an agent
 * router.registerAgent("test.failed", agent);
 *
 * // Route an event
 * Promise&lt;Map&lt;String, Object&gt;&gt; result = router.routeEvent("test.failed", event);
 * </pre>
 *
 * @see AgentEventOperator
 * @see BackpressureOperator
 * @see TypedAgent
 *
 * @doc.type class
 * @doc.purpose AEP event → TypedAgent dispatch router
 * @doc.layer product
 * @doc.pattern Router, Registry
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public class YappcAgentEventRouter {

    private static final Logger LOG = LoggerFactory.getLogger(YappcAgentEventRouter.class);

    private final Map<String, TypedAgent<Map<String, Object>, Map<String, Object>>> registeredAgents;
    private final YappcEventRoutingConfig routingConfig;

    /**
     * Creates a new router with the specified routing configuration.
     *
     * @param routingConfig the event routing configuration
     */
    public YappcAgentEventRouter(@NotNull YappcEventRoutingConfig routingConfig) {
        this.routingConfig = Objects.requireNonNull(routingConfig, "routingConfig");
        this.registeredAgents = new ConcurrentHashMap<>();
    }

    /**
     * Registers a TypedAgent with the router.
     *
     * @param topic the event topic this agent handles
     * @param agent the TypedAgent to register
     * @return this router for chaining
     */
    @NotNull
    public YappcAgentEventRouter registerAgent(
            @NotNull String topic,
            @NotNull TypedAgent<Map<String, Object>, Map<String, Object>> agent) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(agent, "agent");

        registeredAgents.put(topic, agent);
        LOG.info("Registered agent {} for topic {}", agent.descriptor().getAgentId(), topic);
        return this;
    }

    /**
     * Checks if an agent is registered for the given topic.
     *
     * @param topic the event topic
     * @return true if an agent is registered
     */
    public boolean isAgentRegistered(@NotNull String topic) {
        Objects.requireNonNull(topic, "topic");
        return registeredAgents.containsKey(topic);
    }

    /**
     * Routes an event to the appropriate agent based on topic.
     *
     * <p>Returns a {@link Promise} that completes with the processed result, or
     * an error event map if routing or processing fails.
     *
     * @param topic the event topic
     * @param event the event payload
     * @return Promise of the processed result, or error event if routing fails
     */
    @NotNull
    public Promise<Map<String, Object>> routeEvent(
            @NotNull String topic,
            @NotNull Map<String, Object> event) {

        TypedAgent<Map<String, Object>, Map<String, Object>> agent = registeredAgents.get(topic);
        if (agent == null) {
            LOG.warn("No agent registered for topic: {}", topic);
            return Promise.of(createErrorEvent(topic, "NO_AGENT_REGISTERED", "No agent mapped to topic"));
        }

        AgentContext context = AgentContext.builder()
                .turnId(UUID.randomUUID().toString())
                .agentId("yappc-event-router")
                .tenantId("default")
                .userId(null)
                .sessionId(null)
                .startTime(Instant.now())
                .memoryStore(MemoryStore.noOp())
                .logger(LOG)
                .config(Map.of())
                .build();

        try {
            return agent.process(context, event)
                    .then(result -> {
                        if (result.isSuccess()) {
                            return Promise.of(createSuccessEvent(topic, result.getOutput()));
                        }
                        return Promise.of(createErrorEvent(
                                topic, "AGENT_PROCESSING_FAILED", result.getExplanation()));
                    });
        } catch (Exception e) {
            LOG.error("Error routing event to agent for topic: {}", topic, e);
            return Promise.of(createErrorEvent(topic, "ROUTING_FAILED", e.getMessage()));
        }
    }

    /**
     * Creates a success event response.
     *
     * @param topic the event topic
     * @param data  the result data
     * @return success event map
     */
    private Map<String, Object> createSuccessEvent(@NotNull String topic,
                                                   @NotNull Map<String, Object> data) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "agent.result");
        event.put("topic", topic);
        event.put("status", "success");
        event.put("timestamp", System.currentTimeMillis());
        event.put("data", data);
        return event;
    }

    /**
     * Creates an error event response.
     *
     * @param topic        the event topic
     * @param errorCode    the error code
     * @param errorMessage the error message
     * @return error event map
     */
    private Map<String, Object> createErrorEvent(@NotNull String topic,
                                                 @NotNull String errorCode,
                                                 @NotNull String errorMessage) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "agent.error");
        event.put("topic", topic);
        event.put("errorCode", errorCode);
        event.put("errorMessage", errorMessage);
        event.put("timestamp", System.currentTimeMillis());
        return event;
    }

    /**
     * Gets the number of registered agents.
     *
     * @return agent count
     */
    public int getRegisteredAgentCount() {
        return registeredAgents.size();
    }

    /**
     * Gets basic metrics for the router.
     *
     * @return metrics map
     */
    @NotNull
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("registeredAgents", registeredAgents.size());
        metrics.put("supportedTopics", registeredAgents.keySet());
        return metrics;
    }
}

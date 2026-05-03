/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.tracing;

import com.ghatana.platform.domain.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

/**
 * Tracing instrumentation for agent operations.
 *
 * Provides span creation and trace context propagation for agent processing.
 *
 * @doc.type class
 * @doc.purpose Distributed tracing instrumentation for agent operations
 * @doc.layer product
 * @doc.pattern Instrumentation
 */
public final class AgentTracingInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(AgentTracingInstrumentation.class);

    private AgentTracingInstrumentation() {
        // Utility class
    }

    /**
     * Creates a span for agent processing.
     */
    public static AgentSpan createAgentSpan(
        String agentId,
        String agentType,
        String eventType,
        String tenantId
    ) {
        AgentSpan span = new AgentSpan(
            "aep.agent.process",
            Map.of(
                "agent.id", agentId,
                "agent.type", agentType,
                "event.type", eventType,
                "tenant.id", tenantId
            )
        );
        
        span.event("agent.started", Map.of("timestamp", Instant.now().toString()));
        log.debug("Created agent processing span: agentId={}, type={}", agentId, agentType);
        
        return span;
    }

    /**
     * Records tool invocation in span.
     */
    public static void recordToolInvoked(
        AgentSpan span,
        String toolName,
        String toolType,
        String endpoint
    ) {
        span.event("tool.invoked", Map.of(
            "tool.name", toolName,
            "tool.type", toolType,
            "tool.endpoint", endpoint,
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Records memory operation in span.
     */
    public static void recordMemoryOperation(
        AgentSpan span,
        String operation,
        String memoryType,
        int itemCount
    ) {
        span.event("memory.operation", Map.of(
            "operation", operation,
            "memory.type", memoryType,
            "item.count", itemCount,
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Records agent completion in span.
     */
    public static void recordAgentCompleted(
        AgentSpan span,
        boolean success,
        long processingTimeMs,
        int outputCount
    ) {
        span.attribute("result.success", success);
        span.attribute("processing.time.ms", processingTimeMs);
        span.attribute("output.count", outputCount);
        
        span.event("agent.completed", Map.of(
            "processing.time.ms", processingTimeMs,
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Agent span implementation.
     */
    public static class AgentSpan implements Span {
        private final String name;
        private final Map<String, Object> attributes;
        private final Map<String, Object> events = new java.util.LinkedHashMap<>();
        private String status = "OK";
        private String statusDescription;
        private boolean ended = false;

        AgentSpan(String name, Map<String, Object> attributes) {
            this.name = name;
            this.attributes = new java.util.HashMap<>(attributes);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.copyOf(attributes);
        }

        @Override
        public void attribute(String key, Object value) {
            if (!ended) {
                attributes.put(key, value);
            }
        }

        @Override
        public void event(String eventName, Map<String, Object> attributes) {
            if (!ended) {
                events.put(eventName, Map.copyOf(attributes));
            }
        }

        @Override
        public void recordException(Exception error) {
            if (!ended) {
                attribute("error.type", error.getClass().getSimpleName());
                attribute("error.message", error.getMessage());
                attribute("error.stacktrace", java.util.Arrays.stream(error.getStackTrace())
                    .limit(10)
                    .map(StackTraceElement::toString)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse(""));
            }
        }

        @Override
        public void status(String status, String description) {
            if (!ended) {
                this.status = status;
                this.statusDescription = description;
            }
        }

        @Override
        public void end() {
            if (!ended) {
                ended = true;
                log.debug("Agent span ended: name={}, status={}", name, status);
                // In production, this would export to tracing backend
            }
        }

        @Override
        public boolean isEnded() {
            return ended;
        }

        public Map<String, Object> getEvents() {
            return Map.copyOf(events);
        }
    }

    /**
     * Span interface.
     */
    public interface Span {
        String getName();
        Map<String, Object> getAttributes();
        void attribute(String key, Object value);
        void event(String eventName, Map<String, Object> attributes);
        void recordException(Exception error);
        void status(String status, String description);
        void end();
        boolean isEnded();
    }
}

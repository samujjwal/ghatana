package com.ghatana.kernel.observability;

import java.util.Map;

/**
 * Central telemetry manager for kernel platform.
 *
 * <p>Provides comprehensive observability through metrics, events, and
 * explainability tracking. Integrates with monitoring backends like
 * Prometheus, Grafana, and Jaeger.</p>
 *
 * @doc.type interface
 * @doc.purpose Central telemetry and observability management
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface KernelTelemetryManager {

    /**
     * Records a metric value.
     *
     * @param name the metric name
     * @param value the metric value
     * @param tags optional tags for the metric
     */
    void recordMetric(String name, double value, String... tags);

    /**
     * Records an event.
     *
     * @param event the event to record
     */
    void recordEvent(Event event);

    /**
     * Creates an explainability context for an agent action.
     *
     * @param action the agent action
     * @return explainability context
     */
    ExplainabilityContext createExplainabilityContext(AgentAction action);

    /**
     * Starts a timer for measuring duration.
     *
     * @param name the timer name
     * @param tags optional tags
     * @return timer instance
     */
    Timer startTimer(String name, String... tags);

    /**
     * Records a counter increment.
     *
     * @param name the counter name
     * @param increment the increment value
     * @param tags optional tags
     */
    void incrementCounter(String name, long increment, String... tags);

    /**
     * Records a gauge value.
     *
     * @param name the gauge name
     * @param value the gauge value
     * @param tags optional tags
     */
    void recordGauge(String name, double value, String... tags);

    /**
     * Records a histogram value.
     *
     * @param name the histogram name
     * @param value the value to record
     * @param tags optional tags
     */
    void recordHistogram(String name, double value, String... tags);

    /**
     * Represents a telemetry event.
     */
    class Event {
        private final String eventType;
        private final String source;
        private final Map<String, Object> data;
        private final long timestamp;

        public Event(String eventType, String source, Map<String, Object> data) {
            this.eventType = eventType;
            this.source = source;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public String getEventType() { return eventType; }
        public String getSource() { return source; }
        public Map<String, Object> getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * Represents an agent action for explainability.
     */
    class AgentAction {
        private final String agentId;
        private final String actionType;
        private final Map<String, Object> parameters;

        public AgentAction(String agentId, String actionType, Map<String, Object> parameters) {
            this.agentId = agentId;
            this.actionType = actionType;
            this.parameters = parameters;
        }

        public String getAgentId() { return agentId; }
        public String getActionType() { return actionType; }
        public Map<String, Object> getParameters() { return parameters; }
    }

    /**
     * Timer for measuring duration.
     */
    interface Timer {
        /**
         * Stops the timer and records the duration.
         */
        void stop();

        /**
         * Gets the elapsed time in milliseconds.
         *
         * @return elapsed time
         */
        long getElapsedMillis();
    }
}

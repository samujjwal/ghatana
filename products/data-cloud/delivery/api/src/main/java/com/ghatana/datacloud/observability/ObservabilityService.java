package com.ghatana.datacloud.observability;

import com.ghatana.platform.governance.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced observability service for Data Cloud with unified correlation tracking.
 *
 * <p><b>Purpose</b><br>
 * Provides centralized observability management with unified correlation IDs,
 * distributed tracing, and comprehensive runtime context tracking. Ensures
 * consistent observability across all Data Cloud components.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ObservabilityService observability = ObservabilityService.builder()
 *     .serviceName("data-cloud-api")
 *     .build();
 * 
 * try (ObservabilityContext context = observability.createContext(
 *         correlationId, tenantId, "api", "runId", "jobId")) {
 *     // Business logic here
 *     context.addMetadata("artifactId", "artifact-123");
 *     context.recordMetric("processing_time_ms", 150);
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Enhanced observability with unified correlation tracking
 * @doc.layer product
 * @doc.pattern Service, Observer
 */
public class ObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityService.class);

    // Standard correlation context keys
    public static final String CORRELATION_ID = "correlationId";
    public static final String TENANT_ID = "tenantId";
    public static final String SURFACE = "surface";
    public static final String RUN_ID = "runId";
    public static final String JOB_ID = "jobId";
    public static final String AGENT_ID = "agentId";
    public static final String PIPELINE_ID = "pipelineId";
    public static final String ARTIFACT_ID = "artifactId";
    public static final String SERVICE_NAME = "serviceName";
    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";

    private final String serviceName;
    private final boolean enableMDC;
    private final Map<String, CorrelationContext> activeContexts = new ConcurrentHashMap<>();
    private final AtomicLong contextCounter = new AtomicLong(0);

    private ObservabilityService(Builder builder) {
        this.serviceName = builder.serviceName;
        this.enableMDC = builder.enableMDC;
    }

    /**
     * Creates a new observability context with standard correlation fields.
     *
     * @param correlationId unique correlation identifier
     * @param tenantId tenant identifier
     * @param surface surface identifier (api, ui, action, etc.)
     * @param runId run identifier (optional)
     * @param jobId job identifier (optional)
     * @return observability context
     */
    public ObservabilityContext createContext(
            String correlationId,
            String tenantId,
            String surface,
            String runId,
            String jobId) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }
        
        Map<String, String> context = new HashMap<>();
        context.put(CORRELATION_ID, correlationId);
        context.put(TENANT_ID, tenantId);
        context.put(SURFACE, surface);
        context.put(SERVICE_NAME, serviceName);
        
        if (runId != null) context.put(RUN_ID, runId);
        if (jobId != null) context.put(JOB_ID, jobId);
        
        return createContext(context);
    }

    /**
     * Creates a new observability context with full runtime truth identifiers.
     *
     * @param correlationId unique correlation identifier
     * @param tenantId tenant identifier
     * @param surface surface identifier (api, ui, action, etc.)
     * @param runId run identifier (optional)
     * @param jobId job identifier (optional)
     * @param agentId agent identifier (optional)
     * @param pipelineId pipeline identifier (optional)
     * @param artifactId artifact identifier (optional)
     * @return observability context
     */
    public ObservabilityContext createContext(
            String correlationId,
            String tenantId,
            String surface,
            String runId,
            String jobId,
            String agentId,
            String pipelineId,
            String artifactId) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }
        
        Map<String, String> context = new HashMap<>();
        context.put(CORRELATION_ID, correlationId);
        context.put(TENANT_ID, tenantId);
        context.put(SURFACE, surface);
        context.put(SERVICE_NAME, serviceName);
        
        if (runId != null) context.put(RUN_ID, runId);
        if (jobId != null) context.put(JOB_ID, jobId);
        if (agentId != null) context.put(AGENT_ID, agentId);
        if (pipelineId != null) context.put(PIPELINE_ID, pipelineId);
        if (artifactId != null) context.put(ARTIFACT_ID, artifactId);
        
        return createContext(context);
    }

    public ObservabilityContext createContext(String correlationId, String tenantId, String surface) {
        return createContext(correlationId, tenantId, surface, null, null);
    }

    /**
     * Creates an observability context from a map of context values.
     *
     * @param context initial context values
     * @return observability context
     */
    public ObservabilityContext createContext(Map<String, String> context) {
        String contextId = UUID.randomUUID().toString();
        
        // Ensure required fields
        if (!context.containsKey(CORRELATION_ID)) {
            context.put(CORRELATION_ID, generateCorrelationId());
        }
        if (!context.containsKey(SERVICE_NAME)) {
            context.put(SERVICE_NAME, serviceName);
        }
        
        // Generate trace/span IDs if not present
        if (!context.containsKey(TRACE_ID)) {
            context.put(TRACE_ID, generateTraceId());
        }
        if (!context.containsKey(SPAN_ID)) {
            context.put(SPAN_ID, generateSpanId());
        }
        
        CorrelationContext correlationContext = new CorrelationContext(contextId, new HashMap<>(context));
        activeContexts.put(contextId, correlationContext);
        contextCounter.incrementAndGet();
        
        return new ObservabilityContext(correlationContext, this);
    }

    /**
     * Creates an observability context from HTTP headers.
     *
     * @param headers HTTP headers containing correlation information
     * @return observability context
     */
    public ObservabilityContext createContextFromHeaders(Map<String, String> headers) {
        Map<String, String> context = new HashMap<>();
        
        // Extract standard correlation headers
        context.put(CORRELATION_ID, headers.getOrDefault("X-Correlation-ID", generateCorrelationId()));
        context.put(TENANT_ID, headers.get("X-Tenant-ID"));
        context.put(TRACE_ID, headers.get("X-Trace-ID"));
        context.put(SPAN_ID, headers.get("X-Span-ID"));
        context.put(SURFACE, headers.getOrDefault("X-Surface", "api"));
        context.put(RUN_ID, headers.get("X-Run-ID"));
        context.put(JOB_ID, headers.get("X-Job-ID"));
        context.put(AGENT_ID, headers.get("X-Agent-ID"));
        context.put(PIPELINE_ID, headers.get("X-Pipeline-ID"));
        context.put(ARTIFACT_ID, headers.get("X-Artifact-ID"));
        
        // Remove null values
        context.values().removeIf(Objects::isNull);
        
        return createContext(context);
    }

    /**
     * Creates an observability context from a principal.
     *
     * @param principal authenticated principal
     * @param surface surface identifier
     * @return observability context
     */
    public ObservabilityContext createContextFromPrincipal(Principal principal, String surface) {
        Map<String, String> context = new HashMap<>();
        
        if (principal != null) {
            context.put(TENANT_ID, principal.getTenantId());
            context.put("userId", principal.getName());
            context.put("roles", String.join(",", principal.getRoles()));
        }
        
        context.put(SURFACE, surface);
        
        return createContext(context);
    }

    /**
     * Gets the current context from MDC (if enabled).
     *
     * @return current context or empty optional
     */
    public Optional<ObservabilityContext> getCurrentContext() {
        if (!enableMDC) {
            return Optional.empty();
        }
        
        String contextId = MDC.get("contextId");
        if (contextId == null) {
            return Optional.empty();
        }
        
        CorrelationContext correlationContext = activeContexts.get(contextId);
        if (correlationContext == null) {
            return Optional.empty();
        }
        
        return Optional.of(new ObservabilityContext(correlationContext, this, false));
    }

    /**
     * Records a metric with the current context.
     *
     * @param metricName metric name
     * @param value metric value
     */
    public void recordMetric(String metricName, double value) {
        getCurrentContext().ifPresent(context -> context.recordMetric(metricName, value));
    }

    /**
     * Records an event with the current context.
     *
     * @param eventName event name
     * @param metadata event metadata
     */
    public void recordEvent(String eventName, Map<String, Object> metadata) {
        getCurrentContext().ifPresent(context -> context.recordEvent(eventName, metadata));
    }

    /**
     * Gets observability statistics.
     */
    public ObservabilityStats getStats() {
        return new ObservabilityStats(
            serviceName,
            activeContexts.size(),
            contextCounter.get()
        );
    }

    /**
     * Cleans up expired contexts.
     */
    public void cleanupExpiredContexts() {
        Instant cutoff = Instant.now().minusSeconds(300); // 5 minutes
        activeContexts.entrySet().removeIf(entry -> {
            CorrelationContext context = entry.getValue();
            return context.getCreatedAt().isBefore(cutoff);
        });
    }

    /**
     * Generates a new correlation ID.
     */
    private String generateCorrelationId() {
        return "corr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Generates a new trace ID.
     */
    private String generateTraceId() {
        return "trace-" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generates a new span ID.
     */
    private String generateSpanId() {
        return "span-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Internal correlation context storage.
     */
    static class CorrelationContext {
        private final String contextId;
        private final Map<String, String> context;
        private final Map<String, Double> metrics = new ConcurrentHashMap<>();
        private final List<ObservabilityEvent> events = new ArrayList<>();
        private final Instant createdAt;
        private volatile Instant lastActivity;

        CorrelationContext(String contextId, Map<String, String> context) {
            this.contextId = contextId;
            this.context = new ConcurrentHashMap<>(context);
            this.createdAt = Instant.now();
            this.lastActivity = Instant.now();
        }

        String getContextId() {
            return contextId;
        }

        Map<String, String> getContext() {
            return new HashMap<>(context);
        }

        Map<String, Double> getMetrics() {
            return new HashMap<>(metrics);
        }

        List<ObservabilityEvent> getEvents() {
            return new ArrayList<>(events);
        }

        Instant getCreatedAt() {
            return createdAt;
        }

        Instant getLastActivity() {
            return lastActivity;
        }

        void updateLastActivity() {
            this.lastActivity = Instant.now();
        }

        void addMetric(String name, double value) {
            metrics.put(name, value);
            updateLastActivity();
        }

        void addEvent(ObservabilityEvent event) {
            synchronized (events) {
                events.add(event);
            }
            updateLastActivity();
        }

        void updateContext(String key, String value) {
            if (value != null) {
                context.put(key, value);
            } else {
                context.remove(key);
            }
            updateLastActivity();
        }
    }

    /**
     * Observability context for tracking request lifecycle.
     */
    public static class ObservabilityContext implements AutoCloseable {
        private final CorrelationContext correlationContext;
        private final ObservabilityService observabilityService;
        private final String previousContextId;
        private final boolean mdcWasSet;

        ObservabilityContext(CorrelationContext correlationContext, ObservabilityService observabilityService) {
            this(correlationContext, observabilityService, true);
        }

        private ObservabilityContext(
                CorrelationContext correlationContext,
                ObservabilityService observabilityService,
                boolean activateMdc) {
            this.correlationContext = correlationContext;
            this.observabilityService = observabilityService;
            this.previousContextId = activateMdc && observabilityService.enableMDC ? MDC.get("contextId") : null;
            this.mdcWasSet = activateMdc && setupMDC();
        }

        /**
         * Gets the correlation ID.
         */
        public String getCorrelationId() {
            return correlationContext.context.get(CORRELATION_ID);
        }

        /**
         * Gets the tenant ID.
         */
        public String getTenantId() {
            return correlationContext.context.get(TENANT_ID);
        }

        /**
         * Gets the surface.
         */
        public String getSurface() {
            return correlationContext.context.get(SURFACE);
        }

        public String getRunId() {
            return correlationContext.context.get(RUN_ID);
        }

        public String getJobId() {
            return correlationContext.context.get(JOB_ID);
        }

        public String getAgentId() {
            return correlationContext.context.get(AGENT_ID);
        }

        public String getPipelineId() {
            return correlationContext.context.get(PIPELINE_ID);
        }

        public String getArtifactId() {
            return correlationContext.context.get(ARTIFACT_ID);
        }

        public boolean isActive() {
            return observabilityService.activeContexts.containsKey(correlationContext.contextId);
        }

        /**
         * Gets a context value by key.
         */
        public String get(String key) {
            return correlationContext.context.get(key);
        }

        /**
         * Sets a context value.
         */
        public void set(String key, String value) {
            correlationContext.updateContext(key, value);
            if (observabilityService.enableMDC) {
                MDC.put(key, value);
            }
        }

        /**
         * Adds metadata to the context.
         */
        public void addMetadata(String key, String value) {
            set(key, value);
        }

        /**
         * Records a metric.
         */
        public void recordMetric(String metricName, double value) {
            correlationContext.addMetric(metricName, value);
        }

        /**
         * Records an event.
         */
        public void recordEvent(String eventName, Map<String, Object> metadata) {
            ObservabilityEvent event = new ObservabilityEvent(
                eventName,
                Instant.now(),
                new HashMap<>(correlationContext.context),
                metadata != null ? new HashMap<>(metadata) : Map.of()
            );
            correlationContext.addEvent(event);
        }

        /**
         * Creates a child context with additional context.
         */
        public ObservabilityContext createChild(Map<String, String> additionalContext) {
            Map<String, String> childContext = new HashMap<>(correlationContext.context);
            if (additionalContext != null) {
                childContext.putAll(additionalContext);
            }
            // Generate new span ID for child
            childContext.put(SPAN_ID, observabilityService.generateSpanId());
            
            return observabilityService.createContext(childContext);
        }

        /**
         * Gets all context values.
         */
        public Map<String, String> getAllContext() {
            return new HashMap<>(correlationContext.context);
        }

        /**
         * Gets all recorded metrics.
         */
        public Map<String, Object> getAllMetrics() {
            return new HashMap<>(correlationContext.metrics);
        }

        /**
         * Gets all recorded events.
         */
        public Map<String, Object> getAllEvents() {
            Map<String, Object> eventMap = new LinkedHashMap<>();
            synchronized (correlationContext.events) {
                for (ObservabilityEvent event : correlationContext.events) {
                    eventMap.put(event.getEventName(), event.getMetadata());
                }
            }
            return eventMap;
        }

        /**
         * Sets up MDC context.
         */
        private boolean setupMDC() {
            if (!observabilityService.enableMDC) {
                return false;
            }

            try {
                MDC.put("contextId", correlationContext.contextId);
                correlationContext.context.forEach(MDC::put);
                return true;
            } catch (Exception e) {
                log.warn("Failed to setup MDC context: {}", e.getMessage());
                return false;
            }
        }

        /**
         * Cleans up MDC context.
         */
        private void cleanupMDC() {
            if (mdcWasSet) {
                try {
                    MDC.remove("contextId");
                    correlationContext.context.keySet().forEach(MDC::remove);
                    if (previousContextId != null) {
                        CorrelationContext previousContext = observabilityService.activeContexts.get(previousContextId);
                        if (previousContext != null) {
                            MDC.put("contextId", previousContext.contextId);
                            previousContext.context.forEach(MDC::put);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to cleanup MDC context: {}", e.getMessage());
                }
            }
        }

        @Override
        public void close() {
            cleanupMDC();
            observabilityService.activeContexts.remove(correlationContext.contextId);
        }
    }

    /**
     * Observability event.
     */
    public static class ObservabilityEvent {
        private final String eventName;
        private final Instant timestamp;
        private final Map<String, String> context;
        private final Map<String, Object> metadata;

        ObservabilityEvent(String eventName, Instant timestamp, Map<String, String> context, Map<String, Object> metadata) {
            this.eventName = eventName;
            this.timestamp = timestamp;
            this.context = context;
            this.metadata = metadata;
        }

        public String getEventName() {
            return eventName;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public Map<String, String> getContext() {
            return new HashMap<>(context);
        }

        public Map<String, Object> getMetadata() {
            return new HashMap<>(metadata);
        }
    }

    /**
     * Observability statistics.
     */
    public static class ObservabilityStats {
        private final String serviceName;
        private final int activeContexts;
        private final long totalContextsCreated;

        ObservabilityStats(String serviceName, int activeContexts, long totalContextsCreated) {
            this.serviceName = serviceName;
            this.activeContexts = activeContexts;
            this.totalContextsCreated = totalContextsCreated;
        }

        public ObservabilityStats(
                String serviceName,
                int activeContexts,
                int totalContextsCreated,
                int ignoredMetricCount,
                int ignoredEventCount,
                int ignoredErrorCount) {
            this(serviceName, activeContexts, totalContextsCreated);
        }

        public String getServiceName() {
            return serviceName;
        }

        public int getActiveContexts() {
            return activeContexts;
        }

        public int getTotalContextsCreated() {
            return Math.toIntExact(totalContextsCreated);
        }

        @Override
        public String toString() {
            return String.format("ObservabilityStats{service=%s, active=%d, total=%d}",
                    serviceName, activeContexts, totalContextsCreated);
        }
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serviceName = "data-cloud";
        private boolean enableMDC = true;

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder enableMDC(boolean enableMDC) {
            this.enableMDC = enableMDC;
            return this;
        }

        public Builder enableMdc(boolean enableMDC) {
            return enableMDC(enableMDC);
        }

        public ObservabilityService build() {
            return new ObservabilityService(this);
        }
    }
}

package com.ghatana.aep;

import com.ghatana.aep.event.EventCloud;
import io.activej.promise.Promise;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * AEP Engine Interface - All event processing operations.
 *
 * <p>This is the primary interface for interacting with AEP.
 * It provides methods for:
 * <ul>
 *   <li>Event processing</li>
 *   <li>Pattern management</li>
 *   <li>Analytics (anomaly detection, forecasting)</li>
 *   <li>Lifecycle management</li>
 * </ul>
 *
 * <p>This interface uses platform modules:
 * <ul>
 *   <li>{@code platform/java/event-cloud} - Event processing infrastructure</li>
 *   <li>{@code platform/java/observability} - Metrics and tracing</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Primary AEP interface for event processing
 * @doc.layer api
 * @doc.pattern Facade
 * @since 1.0.0
 */
public interface AepEngine extends AutoCloseable {

    // ==================== Event Processing ====================

    /**
     * Process a single event through the AEP pipeline.
     *
     * @param tenantId tenant identifier
     * @param event event to process
     * @return promise of processing result
     */
    Promise<ProcessingResult> process(String tenantId, Event event);

    /**
     * Submit a pipeline for execution.
     *
     * @param tenantId tenant identifier
     * @param pipeline pipeline definition
     */
    void submitPipeline(String tenantId, Pipeline pipeline);

    /**
     * Subscribe to pattern detections.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern to subscribe to (use "*" for all)
     * @param handler detection handler
     * @return subscription for cancellation
     */
    Subscription subscribe(String tenantId, String patternId, Consumer<Detection> handler);

    // ==================== Pattern Management ====================

    /**
     * Register a new pattern.
     *
     * @param tenantId tenant identifier
     * @param definition pattern definition
     * @return promise of registered pattern
     */
    Promise<Pattern> registerPattern(String tenantId, PatternDefinition definition);

    /**
     * Register a custom pattern detector for a tenant.
     * <p>
     * Pattern detectors are invoked during event processing to detect patterns
     * that match the event stream. This allows external modules (e.g., aep-analytics)
     * to register sophisticated pattern detection agents without creating circular
     * module dependencies.
     *
     * @param tenantId tenant identifier
     * @param detector pattern detector implementation
     */
    void registerPatternDetector(String tenantId, PatternDetector detector);

    /**
     * Unregister a pattern detector for a tenant.
     *
     * @param tenantId tenant identifier
     * @param detector pattern detector to remove
     */
    void unregisterPatternDetector(String tenantId, PatternDetector detector);

    /**
     * Get a pattern by ID.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return promise of pattern if found
     */
    Promise<Optional<Pattern>> getPattern(String tenantId, String patternId);

    /**
     * List all patterns for a tenant.
     *
     * @param tenantId tenant identifier
     * @return promise of list of patterns
     */
    Promise<List<Pattern>> listPatterns(String tenantId);

    /**
     * Delete a pattern.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return promise of completion
     */
    Promise<Void> deletePattern(String tenantId, String patternId);

    // ==================== Analytics ====================

    /**
     * Detect anomalies in a list of events.
     *
     * @param tenantId tenant identifier
     * @param events events to analyze
     * @return promise of detected anomalies
     */
    Promise<List<Anomaly>> detectAnomalies(String tenantId, List<Event> events);

    /**
     * Generate a forecast from time series data.
     *
     * @param tenantId tenant identifier
     * @param data time series data
     * @return promise of forecast result
     */
    Promise<Forecast> forecast(String tenantId, TimeSeriesData data);

    // ==================== Lifecycle ====================

    /**
     * Close the engine and release resources.
     */
    @Override
    void close();

    /**
     * Get the underlying EventCloud.
     *
     * @return event cloud instance
     */
    EventCloud eventCloud();

    // ==================== Supporting Types ====================

    /**
     * Event to process.
     *
     * <p>The {@code version} field identifies the schema version of this event envelope.
     * Defaults to {@code "1.0"} when not supplied.
     *
     * <p>The {@code idempotencyKey} field enables duplicate detection. Callers supplying
     * the same key for logically identical events allow the engine to deduplicate retries.
     * If absent, every event is treated as unique.
     */
    record Event(
        String type,
        Map<String, Object> payload,
        Map<String, String> headers,
        java.time.Instant timestamp,
        IdentityContext identityContext,
        ConsentContext consentContext,
        String version,
        Optional<String> idempotencyKey
    ) {
        /** Current default event schema version. */
        public static final String DEFAULT_VERSION = "1.0";

        public Event {
            java.util.Objects.requireNonNull(type, "type required");
            payload = payload != null ? Map.copyOf(payload) : Map.of();
            headers = headers != null ? Map.copyOf(headers) : Map.of();
            timestamp = timestamp != null ? timestamp : java.time.Instant.now();
            identityContext = identityContext != null ? identityContext : IdentityContext.empty();
            consentContext = consentContext != null ? consentContext : ConsentContext.defaultConsent();
            version = (version != null && !version.isBlank()) ? version : DEFAULT_VERSION;
            idempotencyKey = idempotencyKey != null ? idempotencyKey : Optional.empty();
        }

        /** Convenience constructor — uses default version and no idempotency key. */
        public Event(String type, Map<String, Object> payload, Map<String, String> headers,
                     java.time.Instant timestamp) {
            this(type, payload, headers, timestamp,
                 IdentityContext.empty(), ConsentContext.defaultConsent(),
                 DEFAULT_VERSION, Optional.empty());
        }

        public static Event of(String type, Map<String, Object> payload) {
            return new Event(type, payload, Map.of(), java.time.Instant.now(),
                IdentityContext.empty(), ConsentContext.defaultConsent(), DEFAULT_VERSION, Optional.empty());
        }

        public Event withIdentityContext(IdentityContext identityContext) {
            return new Event(type, payload, headers, timestamp, identityContext, consentContext, version, idempotencyKey);
        }

        public Event withConsentContext(ConsentContext consentContext) {
            return new Event(type, payload, headers, timestamp, identityContext, consentContext, version, idempotencyKey);
        }

        public Event withVersion(String version) {
            return new Event(type, payload, headers, timestamp, identityContext, consentContext, version, idempotencyKey);
        }

        public Event withIdempotencyKey(String idempotencyKey) {
            return new Event(type, payload, headers, timestamp, identityContext, consentContext, version,
                Optional.ofNullable(idempotencyKey));
        }

        /**
         * Returns the correlation ID for this event, or {@code null} if not set.
         *
         * <p>The correlation ID is propagated from {@code headers.get("correlationId")}.
         * Events belonging to the same logical operation should share the same correlation ID
         * to enable end-to-end tracing through the AEP pipeline.
         *
         * @return correlation ID string, or {@code null}
         */
        public String correlationId() {
            return headers.get("correlationId");
        }

        /**
         * Returns a copy of this event with the given correlation ID set in the headers.
         *
         * @param correlationId the correlation ID to propagate; if {@code null} the event is returned unchanged
         * @return event with correlationId header set
         */
        public Event withCorrelationId(String correlationId) {
            if (correlationId == null) return this;
            java.util.Map<String, String> newHeaders = new java.util.HashMap<>(headers);
            newHeaders.put("correlationId", correlationId);
            return new Event(type, payload, java.util.Collections.unmodifiableMap(newHeaders),
                timestamp, identityContext, consentContext, version, idempotencyKey);
        }
    }

    /**
     * Identity attributes resolved from the inbound event envelope.
     */
    record IdentityContext(
        Optional<String> userId,
        Optional<String> anonymousId,
        Optional<String> sessionId,
        Optional<String> stitchedId
    ) {
        public IdentityContext {
            userId = userId != null ? userId : Optional.empty();
            anonymousId = anonymousId != null ? anonymousId : Optional.empty();
            sessionId = sessionId != null ? sessionId : Optional.empty();
            stitchedId = stitchedId != null ? stitchedId : Optional.empty();
        }

        public static IdentityContext empty() {
            return new IdentityContext(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    /**
     * Event-level consent and retention context.
     */
    record ConsentContext(
        ConsentStatus status,
        RetentionPolicy retentionPolicy,
        List<String> allowedPurposes
    ) {
        public ConsentContext {
            status = status != null ? status : ConsentStatus.UNKNOWN;
            retentionPolicy = retentionPolicy != null ? retentionPolicy : RetentionPolicy.STANDARD;
            allowedPurposes = allowedPurposes != null ? List.copyOf(allowedPurposes) : List.of();
        }

        public static ConsentContext defaultConsent() {
            return new ConsentContext(ConsentStatus.UNKNOWN, RetentionPolicy.STANDARD, List.of());
        }
    }

    /**
     * Supported consent states for event processing.
     */
    enum ConsentStatus {
        GRANTED,
        DENIED,
        UNKNOWN,
        EXPIRED
    }

    /**
     * Supported retention policies for event storage and downstream handling.
     */
    enum RetentionPolicy {
        STANDARD,
        SHORT_LIVED,
        LONG_LIVED,
        DELETE_ON_REQUEST
    }

    /**
     * Processing result.
     */
    record ProcessingResult(
        String eventId,
        boolean success,
        List<Detection> detections,
        Map<String, Object> metadata
    ) {
        public ProcessingResult {
            java.util.Objects.requireNonNull(eventId, "eventId required");
            detections = detections != null ? List.copyOf(detections) : List.of();
            metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        }

        public static ProcessingResult success(String eventId) {
            return new ProcessingResult(eventId, true, List.of(), Map.of());
        }

        public static ProcessingResult success(String eventId, List<Detection> detections) {
            return new ProcessingResult(eventId, true, detections, Map.of());
        }

        public static ProcessingResult skipped(String eventId, String reason) {
            return new ProcessingResult(
                eventId, false, List.of(),
                Map.of("processed", false, "skipped", true, "reason", reason));
        }

        public static ProcessingResult failed(String eventId, String reason) {
            return new ProcessingResult(
                eventId, false, List.of(),
                Map.of("processed", false, "failed", true, "reason", reason));
        }
    }

    /**
     * Pipeline definition.
     * <p>
     * Pipelines are executed as a DAG (Directed Acyclic Graph) where each step
     * can depend on other steps. The executor ensures steps run in dependency order.
     */
    record Pipeline(
        String id,
        String name,
        List<PipelineStep> steps
    ) {
        public Pipeline {
            java.util.Objects.requireNonNull(id, "id required");
            java.util.Objects.requireNonNull(name, "name required");
            steps = steps != null ? List.copyOf(steps) : List.of();
        }

        /** Returns true if this pipeline has no cycles (valid DAG). */
        public boolean isValidDAG() {
            return !hasCycles();
        }

        private boolean hasCycles() {
            Set<String> visited = new HashSet<>();
            Set<String> recursionStack = new HashSet<>();

            for (PipelineStep step : steps) {
                if (hasCycle(step.id(), visited, recursionStack)) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasCycle(String stepId, Set<String> visited, Set<String> recursionStack) {
            if (recursionStack.contains(stepId)) {
                return true;
            }
            if (visited.contains(stepId)) {
                return false;
            }

            visited.add(stepId);
            recursionStack.add(stepId);

            PipelineStep step = findStep(stepId);
            if (step != null) {
                for (String dep : step.dependsOn()) {
                    if (hasCycle(dep, visited, recursionStack)) {
                        return true;
                    }
                }
            }

            recursionStack.remove(stepId);
            return false;
        }

        private PipelineStep findStep(String stepId) {
            return steps.stream().filter(s -> s.id().equals(stepId)).findFirst().orElse(null);
        }
    }

    /**
     * Pipeline step.
     * <p>
     * Steps can have dependencies on other steps via {@link #dependsOn}. The DAG executor
     * ensures that steps are only executed after all their dependencies have completed successfully.
     */
    record PipelineStep(
        String id,
        String type,
        Map<String, Object> config,
        List<String> dependsOn
    ) {
        public PipelineStep {
            Objects.requireNonNull(id, "id required");
            Objects.requireNonNull(type, "type required");
            config = config != null ? Map.copyOf(config) : Map.of();
            dependsOn = dependsOn != null ? List.copyOf(dependsOn) : List.of();
        }

        /** Convenience constructor for steps without dependencies. */
        public PipelineStep(String id, String type, Map<String, Object> config) {
            this(id, type, config, List.of());
        }

        /** Returns true if this step has no dependencies. */
        public boolean isRoot() {
            return dependsOn.isEmpty();
        }
    }

    /**
     * Pattern definition for registration.
     */
    record PatternDefinition(
        String name,
        String description,
        PatternType type,
        Map<String, Object> config
    ) {
        public PatternDefinition {
            java.util.Objects.requireNonNull(name, "name required");
            java.util.Objects.requireNonNull(type, "type required");
            config = config != null ? Map.copyOf(config) : Map.of();
        }
    }

    /**
     * Pattern types.
     */
    enum PatternType {
        SEQUENCE, THRESHOLD, ANOMALY, CORRELATION, CUSTOM
    }

    /**
     * Registered pattern.
     */
    record Pattern(
        String id,
        String name,
        String description,
        PatternType type,
        Map<String, Object> config,
        java.time.Instant createdAt
    ) {}

    /**
     * Detection result.
     */
    record Detection(
        String patternId,
        String patternName,
        double confidence,
        Map<String, Object> details,
        java.time.Instant detectedAt
    ) {}

    /**
     * Anomaly detection result.
     */
    record Anomaly(
        String eventId,
        String anomalyType,
        double score,
        Map<String, Object> details
    ) {}

    /**
     * Time series data for forecasting.
     */
    record TimeSeriesData(
        String metric,
        List<DataPoint> points
    ) {
        public TimeSeriesData {
            java.util.Objects.requireNonNull(metric, "metric required");
            points = points != null ? List.copyOf(points) : List.of();
        }
    }

    /**
     * Data point in time series.
     */
    record DataPoint(
        java.time.Instant timestamp,
        double value
    ) {}

    /**
     * Forecast result.
     */
    record Forecast(
        String metric,
        List<DataPoint> predictions,
        double confidence,
        Map<String, Object> metadata
    ) {}

    /**
     * Subscription handle.
     */
    interface Subscription {
        void cancel();
        boolean isCancelled();
    }

    /**
     * Pattern detector interface for custom pattern detection logic.
     * <p>
     * Implementations can be registered via {@link #registerPatternDetector(String, PatternDetector)}
     * to participate in event processing pattern detection. This allows external
     * modules (e.g., aep-analytics) to provide sophisticated pattern detection
     * without creating circular module dependencies.
     *
     * <p>Implementations must be thread-safe and non-blocking.
     *
     * @see #registerPatternDetector(String, PatternDetector)
     */
    @FunctionalInterface
    interface PatternDetector {

        /**
         * Detect patterns in the given event.
         *
         * @param tenantId tenant identifier
         * @param event the event to analyze
         * @param patterns registered patterns for the tenant
         * @return promise of detected patterns (empty list if none)
         */
        Promise<List<Detection>> detect(String tenantId, Event event, List<Pattern> patterns);
    }
}

package com.ghatana.aep;

import com.ghatana.aep.event.EventCloud;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     */
    record Event(
        String type,
        Map<String, Object> payload,
        Map<String, String> headers,
        java.time.Instant timestamp
    ) {
        public Event {
            java.util.Objects.requireNonNull(type, "type required");
            payload = payload != null ? Map.copyOf(payload) : Map.of();
            headers = headers != null ? Map.copyOf(headers) : Map.of();
            timestamp = timestamp != null ? timestamp : java.time.Instant.now();
        }

        public static Event of(String type, Map<String, Object> payload) {
            return new Event(type, payload, Map.of(), java.time.Instant.now());
        }
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
    }

    /**
     * Pipeline definition.
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
    }

    /**
     * Pipeline step.
     */
    record PipelineStep(
        String type,
        Map<String, Object> config
    ) {}

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
}

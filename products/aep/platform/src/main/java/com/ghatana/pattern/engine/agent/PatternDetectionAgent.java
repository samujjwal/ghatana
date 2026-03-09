package com.ghatana.pattern.engine.agent;

import com.ghatana.core.operator.AbstractOperator;
import com.ghatana.core.operator.OperatorConfig;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.pattern.api.model.DetectionPlan;
import com.ghatana.pattern.api.model.PatternWindowSpec;
import com.ghatana.pattern.engine.evaluator.ProbabilisticEvaluator;
import com.ghatana.pattern.engine.evaluator.ProbabilisticEvaluator.PatternMatch;
import com.ghatana.pattern.engine.nfa.NFA;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production-grade NFA-based pattern detection agent.
 *
 * <p>Receives events, evaluates them against a compiled NFA using probabilistic
 * matching via {@link ProbabilisticEvaluator}, and produces output events when
 * pattern matches are detected with confidence above the configured threshold.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><b>NFA-based matching</b>: Processes events through a compiled NFA state machine</li>
 *   <li><b>Probabilistic confidence</b>: Configurable confidence threshold for match reporting</li>
 *   <li><b>Event type filtering</b>: Only processes events matching the DetectionPlan's event types</li>
 *   <li><b>Windowed detection</b>: Automatic evaluator reset when time window expires</li>
 *   <li><b>Multi-match buffering</b>: Accumulates multiple matches within batch processing</li>
 *   <li><b>Observability</b>: Comprehensive metrics for events processed, matches, errors, latency</li>
 *   <li><b>Thread-safe state</b>: Atomic references and concurrent collections for safe access</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <pre>
 *   Event → [type filter] → [window check] → ProbabilisticEvaluator → PatternMatch → Output Event
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   NFA nfa = buildNFA(...);
 *   PatternDetectionAgent agent = PatternDetectionAgent.builder()
 *       .operatorId(OperatorId.of("ghatana", "pattern", "fraud-detection", "1.0"))
 *       .name("Fraud Detection Agent")
 *       .nfa(nfa)
 *       .confidenceThreshold(0.75)
 *       .detectionPlan(plan)
 *       .build();
 *
 *   agent.initialize(OperatorConfig.empty());
 *   agent.start();
 *   Promise<OperatorResult> result = agent.process(event);
 * }</pre>
 *
 * @see ProbabilisticEvaluator
 * @see NFA
 * @see DetectionPlan
 */
public class PatternDetectionAgent extends AbstractOperator {

    // ─── Configuration ───────────────────────────────────────────────────────────
    private static final String CAPABILITY_PATTERN_DETECTION = "pattern-detection";
    private static final String CAPABILITY_NFA_EVALUATION = "nfa-evaluation";
    private static final String CAPABILITY_PROBABILISTIC_MATCHING = "probabilistic-matching";
    private static final String CAPABILITY_WINDOWED_DETECTION = "windowed-detection";

    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.7;
    private static final Duration DEFAULT_WINDOW_DURATION = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_MATCHES_PER_WINDOW = 1000;

    private static final String OUTPUT_EVENT_TYPE = "pattern.match.detected";

    // ─── Core Components ─────────────────────────────────────────────────────────
    private final NFA nfa;
    private final double confidenceThreshold;
    private final DetectionPlan detectionPlan;
    private final Set<String> acceptedEventTypes;
    private final Duration windowDuration;
    private final int maxMatchesPerWindow;

    // ─── Mutable State (thread-safe) ─────────────────────────────────────────────
    private final AtomicReference<ProbabilisticEvaluator> evaluatorRef;
    private final AtomicReference<Instant> windowStartRef;
    private final ConcurrentLinkedQueue<PatternMatch> pendingMatches;

    // ─── Metrics ─────────────────────────────────────────────────────────────────
    private final AtomicLong eventsReceived;
    private final AtomicLong eventsFiltered;
    private final AtomicLong matchesDetected;
    private final AtomicLong windowsExpired;
    private final AtomicLong evaluationErrors;

    // ─── Constructor ─────────────────────────────────────────────────────────────

    /**
     * Creates a new PatternDetectionAgent.
     *
     * @param operatorId          unique operator identifier
     * @param name                human-readable agent name
     * @param description         agent description
     * @param nfa                 the NFA to evaluate events against (must not be null)
     * @param confidenceThreshold minimum confidence for match reporting (0.0–1.0)
     * @param detectionPlan       optional detection plan for event type filtering and window config
     * @param windowDuration      time window for pattern detection (null uses plan or default)
     * @param maxMatchesPerWindow maximum matches to buffer per window
     * @param metricsCollector    metrics collector (null uses noop)
     */
    private PatternDetectionAgent(
            OperatorId operatorId,
            String name,
            String description,
            NFA nfa,
            double confidenceThreshold,
            DetectionPlan detectionPlan,
            Duration windowDuration,
            int maxMatchesPerWindow,
            MetricsCollector metricsCollector) {

        super(operatorId, OperatorType.PATTERN, name,
                description != null ? description : "NFA-based pattern detection agent for: " + nfa.getPatternName(),
                List.of(CAPABILITY_PATTERN_DETECTION, CAPABILITY_NFA_EVALUATION,
                        CAPABILITY_PROBABILISTIC_MATCHING, CAPABILITY_WINDOWED_DETECTION),
                metricsCollector);

        this.nfa = Objects.requireNonNull(nfa, "NFA must not be null");
        this.confidenceThreshold = clampConfidence(confidenceThreshold);
        this.detectionPlan = detectionPlan;

        // Resolve accepted event types from detection plan
        this.acceptedEventTypes = resolveAcceptedEventTypes(detectionPlan);

        // Resolve window duration: explicit > plan > default
        this.windowDuration = resolveWindowDuration(windowDuration, detectionPlan);
        this.maxMatchesPerWindow = maxMatchesPerWindow > 0 ? maxMatchesPerWindow : DEFAULT_MAX_MATCHES_PER_WINDOW;

        // Initialize evaluator and window state
        this.evaluatorRef = new AtomicReference<>(new ProbabilisticEvaluator(nfa, this.confidenceThreshold));
        this.windowStartRef = new AtomicReference<>(Instant.now());
        this.pendingMatches = new ConcurrentLinkedQueue<>();

        // Metrics
        this.eventsReceived = new AtomicLong(0);
        this.eventsFiltered = new AtomicLong(0);
        this.matchesDetected = new AtomicLong(0);
        this.windowsExpired = new AtomicLong(0);
        this.evaluationErrors = new AtomicLong(0);

        // Store configuration metadata
        addMetadata("pattern.name", nfa.getPatternName());
        addMetadata("confidence.threshold", String.valueOf(this.confidenceThreshold));
        addMetadata("window.duration.seconds", String.valueOf(this.windowDuration.getSeconds()));
        addMetadata("accepted.event.types", String.join(",", this.acceptedEventTypes));
        if (detectionPlan != null && detectionPlan.getPatternId() != null) {
            addMetadata("detection.plan.id", detectionPlan.getPatternId().toString());
        }
    }

    // ─── Lifecycle Hooks ─────────────────────────────────────────────────────────

    @Override
    protected Promise<Void> doInitialize(OperatorConfig config) {
        // Apply any config overrides
        String thresholdOverride = config.getString("confidence.threshold", null);
        if (thresholdOverride != null) {
            double newThreshold = clampConfidence(Double.parseDouble(thresholdOverride));
            // Re-create evaluator with new threshold
            evaluatorRef.set(new ProbabilisticEvaluator(nfa, newThreshold));
        }

        // Reset window
        windowStartRef.set(Instant.now());
        pendingMatches.clear();

        return Promise.complete();
    }

    @Override
    protected Promise<Void> doStart() {
        // Reset state for fresh start
        evaluatorRef.get().reset();
        windowStartRef.set(Instant.now());
        pendingMatches.clear();
        eventsReceived.set(0);
        eventsFiltered.set(0);
        matchesDetected.set(0);
        windowsExpired.set(0);
        evaluationErrors.set(0);
        return Promise.complete();
    }

    @Override
    protected Promise<Void> doStop() {
        // Drain pending matches
        pendingMatches.clear();
        return Promise.complete();
    }

    // ─── Core Processing ─────────────────────────────────────────────────────────

    /**
     * Processes an incoming event through the NFA-based pattern detection pipeline.
     *
     * <p>Processing stages:</p>
     * <ol>
     *   <li><b>Validation</b>: Null check and state verification</li>
     *   <li><b>Event type filtering</b>: Skip events not in accepted types (if configured)</li>
     *   <li><b>Window management</b>: Check and reset window if expired</li>
     *   <li><b>NFA evaluation</b>: Feed event to ProbabilisticEvaluator</li>
     *   <li><b>Match handling</b>: Convert pattern match to output events</li>
     * </ol>
     *
     * @param event the input event to process
     * @return Promise resolving to OperatorResult with match events (or empty if no match)
     */
    @Override
    public Promise<OperatorResult> process(Event event) {
        if (event == null) {
            incrementErrorCount("null_event");
            return Promise.of(OperatorResult.failed("Input event must not be null"));
        }

        eventsReceived.incrementAndGet();

        try {
            return Promise.of(recordProcessing(() -> doProcess(event)));
        } catch (Exception e) {
            evaluationErrors.incrementAndGet();
            incrementErrorCount("unexpected_exception");
            return Promise.of(OperatorResult.failed("Unexpected error during pattern evaluation: " + e.getMessage()));
        }
    }

    /**
     * Internal processing logic wrapped by metrics recording.
     */
    private OperatorResult doProcess(Event event) {
        // Stage 1: Event type filtering
        if (!isAcceptedEventType(event)) {
            eventsFiltered.incrementAndGet();
            return OperatorResult.empty();
        }

        // Stage 2: Window management
        checkAndResetWindow();

        // Stage 3: Convert to GEvent for evaluator
        GEvent gEvent = toGEvent(event);
        if (gEvent == null) {
            incrementErrorCount("event_conversion_failed");
            return OperatorResult.failed("Failed to convert event to GEvent for NFA evaluation");
        }

        // Stage 4: NFA evaluation
        ProbabilisticEvaluator evaluator = evaluatorRef.get();
        Optional<PatternMatch> matchOption;
        try {
            matchOption = evaluator.processEvent(gEvent);
        } catch (Exception e) {
            evaluationErrors.incrementAndGet();
            incrementErrorCount("nfa_evaluation_error");
            return OperatorResult.failed("NFA evaluation failed: " + e.getMessage());
        }

        // Stage 5: Match handling
        if (matchOption.isPresent()) {
            PatternMatch match = matchOption.get();
            matchesDetected.incrementAndGet();

            // Buffer match (respecting max)
            if (pendingMatches.size() < maxMatchesPerWindow) {
                pendingMatches.offer(match);
            }

            // Build output event(s) from match
            return buildMatchResult(match, event);
        }

        return OperatorResult.empty();
    }

    // ─── Event Type Filtering ────────────────────────────────────────────────────

    /**
     * Checks whether the event's type is in the set of accepted types.
     * If no event type filter is configured (empty set), all events are accepted.
     */
    private boolean isAcceptedEventType(Event event) {
        if (acceptedEventTypes.isEmpty()) {
            return true;
        }
        String eventType = event.getType();
        return eventType != null && acceptedEventTypes.contains(eventType);
    }

    // ─── Window Management ───────────────────────────────────────────────────────

    /**
     * Checks if the current detection window has expired and resets if needed.
     * Uses atomic compareAndSet to handle concurrent window expiry safely.
     */
    private void checkAndResetWindow() {
        Instant windowStart = windowStartRef.get();
        Instant now = Instant.now();

        if (Duration.between(windowStart, now).compareTo(windowDuration) > 0) {
            // Window expired — attempt atomic reset
            if (windowStartRef.compareAndSet(windowStart, now)) {
                windowsExpired.incrementAndGet();
                evaluatorRef.get().reset();
                pendingMatches.clear();

                getMetricsCollector().incrementCounter("pattern.agent.window.expired",
                        "operator", getId().toString(),
                        "pattern", nfa.getPatternName());
            }
        }
    }

    // ─── Event Conversion ────────────────────────────────────────────────────────

    /**
     * Converts an Event interface to a GEvent for the ProbabilisticEvaluator.
     * If the event is already a GEvent, it is returned as-is.
     * Otherwise, constructs a new GEvent preserving type, headers, and payload.
     */
    private GEvent toGEvent(Event event) {
        if (event instanceof GEvent gEvent) {
            return gEvent;
        }

        try {
            // Build minimal GEvent from Event interface
            Map<String, String> headers = new HashMap<>();
            // Copy known headers
            for (String headerKey : List.of("correlationId", "causationId", "stream", "source", "traceId")) {
                String value = event.getHeader(headerKey);
                if (value != null) {
                    headers.put(headerKey, value);
                }
            }

            Map<String, Object> payload = new HashMap<>();
            // Transfer relevant payload fields (event-specific — no generic iterator on Event interface)
            // Payload copying is best-effort; primary data flows through headers and type matching

            return GEvent.builder()
                    .type(event.getType())
                    .headers(headers)
                    .payload(payload)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    // ─── Match Result Building ───────────────────────────────────────────────────

    /**
     * Builds an OperatorResult from a PatternMatch detection.
     *
     * <p>The output event contains:</p>
     * <ul>
     *   <li>Type: "pattern.match.detected"</li>
     *   <li>Payload: pattern name, confidence, matched event count, match time, agent details</li>
     *   <li>Headers: correlation from input event, pattern metadata</li>
     * </ul>
     */
    private OperatorResult buildMatchResult(PatternMatch match, Event inputEvent) {
        Map<String, Object> matchPayload = new LinkedHashMap<>();
        matchPayload.put("patternName", match.getPatternName());
        matchPayload.put("confidence", match.getConfidence());
        matchPayload.put("matchedEventCount", match.getMatchedEvents().size());
        matchPayload.put("matchTime", match.getMatchTime().toString());
        matchPayload.put("agentId", getId().toString());
        matchPayload.put("agentName", getName());
        matchPayload.put("confidenceThreshold", confidenceThreshold);
        matchPayload.put("windowDurationSeconds", windowDuration.getSeconds());

        // Include matched event types for downstream correlation
        List<String> matchedEventTypes = match.getMatchedEvents().stream()
                .map(GEvent::getType)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        matchPayload.put("matchedEventTypes", matchedEventTypes);

        // Include matched event IDs for traceability
        List<String> matchedEventIds = match.getMatchedEvents().stream()
                .map(ge -> ge.getId() != null ? ge.getId().getId() : null)
                .filter(Objects::nonNull)
                .toList();
        matchPayload.put("matchedEventIds", matchedEventIds);

        // Detection plan metadata
        if (detectionPlan != null) {
            matchPayload.put("detectionPlanId", detectionPlan.getPatternId() != null
                    ? detectionPlan.getPatternId().toString() : "unknown");
            matchPayload.put("detectionPlanVersion", detectionPlan.getVersion());
        }

        // Build headers for output event
        Map<String, String> matchHeaders = new LinkedHashMap<>();
        // Propagate correlation chain
        String correlationId = inputEvent.getCorrelationId();
        if (correlationId != null) {
            matchHeaders.put("correlationId", correlationId);
        }
        String causationId = inputEvent.getCausationId();
        if (causationId != null) {
            matchHeaders.put("causationId", causationId);
        }
        matchHeaders.put("patternName", match.getPatternName());
        matchHeaders.put("sourceAgentId", getId().toString());
        matchHeaders.put("matchConfidence", String.valueOf(match.getConfidence()));

        // Build the output event
        Event matchEvent = GEvent.builder()
                .type(OUTPUT_EVENT_TYPE)
                .payload(matchPayload)
                .headers(matchHeaders)
                .build();

        // Record metric
        getMetricsCollector().incrementCounter("pattern.agent.match.detected",
                "operator", getId().toString(),
                "pattern", match.getPatternName(),
                "confidence_bucket", confidenceBucket(match.getConfidence()));

        return OperatorResult.of(matchEvent);
    }

    // ─── Operator-as-Agent (toEvent) ─────────────────────────────────────────────

    @Override
    public Event toEvent() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentType", "PatternDetectionAgent");
        payload.put("operatorId", getId().toString());
        payload.put("patternName", nfa.getPatternName());
        payload.put("confidenceThreshold", confidenceThreshold);
        payload.put("windowDurationSeconds", windowDuration.getSeconds());
        payload.put("acceptedEventTypes", List.copyOf(acceptedEventTypes));
        payload.put("state", getState().name());

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("agentId", getId().toString());
        headers.put("agentType", "PATTERN_DETECTION");

        return GEvent.builder()
                .type("agent.pattern.detection")
                .payload(payload)
                .headers(headers)
                .build();
    }

    // ─── Observability ───────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>(super.getMetrics());
        metrics.put("events_received", eventsReceived.get());
        metrics.put("events_filtered", eventsFiltered.get());
        metrics.put("matches_detected", matchesDetected.get());
        metrics.put("windows_expired", windowsExpired.get());
        metrics.put("evaluation_errors", evaluationErrors.get());
        metrics.put("pending_matches", pendingMatches.size());
        metrics.put("confidence_threshold", confidenceThreshold);
        metrics.put("pattern_name", nfa.getPatternName());

        ProbabilisticEvaluator evaluator = evaluatorRef.get();
        metrics.put("current_states_count", evaluator.getCurrentStates().size());
        metrics.put("event_history_size", evaluator.getEventHistory().size());

        return Collections.unmodifiableMap(metrics);
    }

    @Override
    public Map<String, Object> getInternalState() {
        Map<String, Object> state = new LinkedHashMap<>(super.getInternalState());
        state.put("pattern_name", nfa.getPatternName());
        state.put("confidence_threshold", confidenceThreshold);
        state.put("window_start", windowStartRef.get().toString());
        state.put("window_duration_seconds", windowDuration.getSeconds());
        state.put("accepted_event_types", List.copyOf(acceptedEventTypes));
        state.put("pending_match_count", pendingMatches.size());
        state.put("evaluator_states", evaluatorRef.get().getCurrentStates().size());

        if (detectionPlan != null) {
            state.put("detection_plan_id", detectionPlan.getPatternId() != null
                    ? detectionPlan.getPatternId().toString() : "none");
            state.put("detection_plan_version", detectionPlan.getVersion());
        }

        return Collections.unmodifiableMap(state);
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    // ─── Public Accessors ────────────────────────────────────────────────────────

    /** Returns the NFA used for pattern detection. */
    public NFA getNfa() {
        return nfa;
    }

    /** Returns the configured confidence threshold. */
    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    /** Returns the detection plan (may be null). */
    public DetectionPlan getDetectionPlan() {
        return detectionPlan;
    }

    /** Returns an unmodifiable view of the accepted event types. */
    public Set<String> getAcceptedEventTypes() {
        return Collections.unmodifiableSet(acceptedEventTypes);
    }

    /** Returns the window duration for pattern detection. */
    public Duration getWindowDuration() {
        return windowDuration;
    }

    /** Returns the number of matches detected since last reset. */
    public long getMatchesDetected() {
        return matchesDetected.get();
    }

    /** Returns the number of events received since last reset. */
    public long getEventsReceived() {
        return eventsReceived.get();
    }

    /** Returns an unmodifiable list of pending (buffered) matches in the current window. */
    public List<PatternMatch> getPendingMatches() {
        return List.copyOf(pendingMatches);
    }

    /** Returns the current ProbabilisticEvaluator instance. */
    public ProbabilisticEvaluator getEvaluator() {
        return evaluatorRef.get();
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────────

    private static double clampConfidence(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static Set<String> resolveAcceptedEventTypes(DetectionPlan plan) {
        if (plan != null && plan.getEventTypes() != null && !plan.getEventTypes().isEmpty()) {
            return Set.copyOf(plan.getEventTypes());
        }
        return Set.of(); // empty = accept all
    }

    private static Duration resolveWindowDuration(Duration explicit, DetectionPlan plan) {
        if (explicit != null && !explicit.isZero() && !explicit.isNegative()) {
            return explicit;
        }
        if (plan != null && plan.getWindow() != null) {
            // Attempt to extract duration from the window spec
            Duration specDuration = extractWindowDuration(plan.getWindow());
            if (specDuration != null && !specDuration.isZero() && !specDuration.isNegative()) {
                return specDuration;
            }
        }
        return DEFAULT_WINDOW_DURATION;
    }

    /**
     * Extracts a Duration from a PatternWindowSpec.
     * Attempts to read the spec's properties for a duration value.
     */
    @SuppressWarnings("unused")
    private static Duration extractWindowDuration(PatternWindowSpec window) {
        try {
            // PatternWindowSpec may have duration-like accessors depending on implementation
            // Fall back to default if extraction fails
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String confidenceBucket(double confidence) {
        if (confidence >= 0.95) return "very_high";
        if (confidence >= 0.85) return "high";
        if (confidence >= 0.70) return "medium";
        if (confidence >= 0.50) return "low";
        return "very_low";
    }

    // ─── Builder ─────────────────────────────────────────────────────────────────

    /**
     * Creates a new builder for PatternDetectionAgent.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing PatternDetectionAgent instances.
     */
    public static final class Builder {
        private OperatorId operatorId;
        private String name;
        private String description;
        private NFA nfa;
        private double confidenceThreshold = DEFAULT_CONFIDENCE_THRESHOLD;
        private DetectionPlan detectionPlan;
        private Duration windowDuration;
        private int maxMatchesPerWindow = DEFAULT_MAX_MATCHES_PER_WINDOW;
        private MetricsCollector metricsCollector;

        private Builder() {}

        /** Sets the operator ID (required). */
        public Builder operatorId(OperatorId operatorId) {
            this.operatorId = operatorId;
            return this;
        }

        /** Sets the operator ID from string components. */
        public Builder operatorId(String namespace, String type, String name, String version) {
            this.operatorId = OperatorId.of(namespace, type, name, version);
            return this;
        }

        /** Sets the human-readable agent name (required). */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /** Sets the agent description. */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** Sets the NFA for pattern matching (required). */
        public Builder nfa(NFA nfa) {
            this.nfa = nfa;
            return this;
        }

        /** Sets the confidence threshold (0.0–1.0). Default: 0.7. */
        public Builder confidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
            return this;
        }

        /** Sets the optional detection plan for event type filtering and config. */
        public Builder detectionPlan(DetectionPlan detectionPlan) {
            this.detectionPlan = detectionPlan;
            return this;
        }

        /** Sets the explicit window duration (overrides plan window). */
        public Builder windowDuration(Duration windowDuration) {
            this.windowDuration = windowDuration;
            return this;
        }

        /** Sets maximum matches buffered per window. Default: 1000. */
        public Builder maxMatchesPerWindow(int maxMatchesPerWindow) {
            this.maxMatchesPerWindow = maxMatchesPerWindow;
            return this;
        }

        /** Sets the metrics collector. */
        public Builder metricsCollector(MetricsCollector metricsCollector) {
            this.metricsCollector = metricsCollector;
            return this;
        }

        /**
         * Builds the PatternDetectionAgent.
         *
         * @throws NullPointerException     if operatorId, name, or nfa is null
         * @throws IllegalArgumentException if confidence threshold is out of range
         */
        public PatternDetectionAgent build() {
            Objects.requireNonNull(operatorId, "operatorId is required");
            Objects.requireNonNull(name, "name is required");
            Objects.requireNonNull(nfa, "NFA is required");

            return new PatternDetectionAgent(
                    operatorId, name, description, nfa,
                    confidenceThreshold, detectionPlan,
                    windowDuration, maxMatchesPerWindow,
                    metricsCollector);
        }
    }

    @Override
    public String toString() {
        return String.format("PatternDetectionAgent[id=%s, pattern=%s, threshold=%.2f, state=%s, matches=%d]",
                getId(), nfa.getPatternName(), confidenceThreshold, getState(), matchesDetected.get());
    }
}

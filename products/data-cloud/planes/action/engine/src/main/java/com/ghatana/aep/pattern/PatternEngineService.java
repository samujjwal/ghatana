/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern;

import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.model.EventTimeContext;
import com.ghatana.aep.model.ReplayContext;
import com.ghatana.aep.model.UncertaintyContext;
import com.ghatana.aep.model.CanonicalEvent;
import com.ghatana.aep.pattern.spec.*;
import com.ghatana.aep.pattern.lifecycle.PatternLearningService;
import com.ghatana.aep.pattern.lifecycle.PatternLifecycleState;
import com.ghatana.aep.pattern.lifecycle.PatternLifecyclePolicy;
import com.ghatana.aep.pattern.lifecycle.PatternLifecycleTransition;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Event;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * AEP Pattern Engine Service (P4-03).
 *
 * <p>Implements the event-pattern execution lifecycle:
 * <ul>
 *   <li>Compile PatternSpec to executable runtime plan</li>
 *   <li>Register and manage active patterns</li>
 *   <li>Consume Data Cloud Event Plane records</li>
 *   <li>Detect pattern matches</li>
 *   <li>Create pattern instances with explainability evidence</li>
 *   <li>Support replay mode for recovery</li>
 *   <li>Enforce tenant isolation</li>
 *   <li>Track confidence and uncertainty metadata</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose AEP pattern engine for event-pattern matching and execution
 * @doc.layer product
 * @doc.pattern Service
 */
public class PatternEngineService {

    private final DataCloudClient dataCloudClient;
    private final PatternSpecCompiler compiler;
    private final PatternRegistry registry;
    private final ExplainabilityService explainabilityService;
    private final PatternLearningService learningService;

    // Active patterns per tenant
    private final Map<String, List<ActivePattern>> tenantPatterns = new ConcurrentHashMap<>();

    // Pattern instances (matches in progress)
    private final Map<String, PatternInstance> patternInstances = new ConcurrentHashMap<>();

    public PatternEngineService(
            DataCloudClient dataCloudClient,
            PatternSpecCompiler compiler,
            PatternRegistry registry,
            ExplainabilityService explainabilityService,
            PatternLearningService learningService) {
        this.dataCloudClient = dataCloudClient;
        this.compiler = compiler;
        this.registry = registry;
        this.explainabilityService = explainabilityService;
        this.learningService = learningService;
    }

    // ==================== Pattern Registration ====================

    /**
     * Register and compile a pattern for execution.
     *
     * @param tenantId tenant identifier
     * @param spec pattern specification
     * @return compiled pattern ready for execution
     */
    public Promise<CompiledPattern> registerPattern(String tenantId, PatternSpec spec) {
        // Compile the pattern
        CompiledPattern compiled = PatternSpecCompiler.compileTyped(spec, null);

        // Create active pattern entry
        ActivePattern active = new ActivePattern(
            spec.metadata().name(),
            tenantId,
            spec,
            compiled,
            Instant.now(),
            PatternLifecycleState.ACTIVE
        );

        // Register per tenant
        tenantPatterns
            .computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>())
            .add(active);

        // Persist to registry
        return registry.register(tenantId, spec)
            .map(v -> compiled);
    }

    /**
     * Unregister a pattern from execution.
     */
    public Promise<Boolean> unregisterPattern(String tenantId, String patternId) {
        List<ActivePattern> patterns = tenantPatterns.get(tenantId);
        if (patterns != null) {
            patterns.removeIf(p -> p.patternId().equals(patternId));
        }
        return registry.unregister(tenantId, patternId);
    }

    /**
     * Get all active patterns for a tenant.
     */
    public List<ActivePattern> getActivePatterns(String tenantId) {
        return tenantPatterns.getOrDefault(tenantId, List.of());
    }

    // ==================== Pattern Lifecycle Management (WS2-4) ====================

    /**
     * Transition a pattern to a new lifecycle state with policy validation.
     *
     * <p>WS2-4: Uses canonical PatternLifecyclePolicy to validate transitions
     * and ensures proper governance-sensitive promotion rules are enforced.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @param toState target lifecycle state
     * @param eventType lifecycle event type for the transition
     * @return updated active pattern
     */
    public Promise<ActivePattern> transitionPatternState(
            String tenantId,
            String patternId,
            PatternLifecycleState toState,
            com.ghatana.aep.pattern.lifecycle.PatternLifecycleEventType eventType) {
        List<ActivePattern> patterns = tenantPatterns.get(tenantId);
        if (patterns == null) {
            return Promise.ofException(new IllegalArgumentException("No patterns found for tenant: " + tenantId));
        }

        ActivePattern existing = patterns.stream()
            .filter(p -> p.patternId().equals(patternId))
            .findFirst()
            .orElse(null);

        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Pattern not found: " + patternId));
        }

        // WS2-4: Validate transition using canonical policy
        PatternLifecycleTransition transition = new PatternLifecycleTransition(
            patternId,
            tenantId,
            existing.state(),
            toState,
            eventType,
            Instant.now().toString(),
            Map.of()
        );

        try {
            PatternLifecyclePolicy.requireAllowed(transition);
            PatternLifecyclePolicy.requireMatchingEventType(transition);
        } catch (IllegalArgumentException e) {
            return Promise.ofException(e);
        }

        // Create updated active pattern with new state
        ActivePattern updated = new ActivePattern(
            existing.patternId(),
            existing.tenantId(),
            existing.spec(),
            existing.compiled(),
            existing.activationTime(),
            toState
        );

        // Update in-memory registry
        patterns.removeIf(p -> p.patternId().equals(patternId));
        patterns.add(updated);

        // Persist state transition
        return registry.updateState(tenantId, patternId, toState)
            .map(v -> updated);
    }

    /**
     * Pause an active pattern.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return updated active pattern
     */
    public Promise<ActivePattern> pausePattern(String tenantId, String patternId) {
        return transitionPatternState(
            tenantId,
            patternId,
            PatternLifecycleState.PAUSED,
            com.ghatana.aep.pattern.lifecycle.PatternLifecycleEventType.PATTERN_DEACTIVATED
        );
    }

    /**
     * Resume a paused pattern.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return updated active pattern
     */
    public Promise<ActivePattern> resumePattern(String tenantId, String patternId) {
        return transitionPatternState(
            tenantId,
            patternId,
            PatternLifecycleState.ACTIVE,
            com.ghatana.aep.pattern.lifecycle.PatternLifecycleEventType.PATTERN_ACTIVATED
        );
    }

    /**
     * Retire a pattern from service.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return updated active pattern
     */
    public Promise<ActivePattern> retirePattern(String tenantId, String patternId) {
        return transitionPatternState(
            tenantId,
            patternId,
            PatternLifecycleState.RETIRED,
            com.ghatana.aep.pattern.lifecycle.PatternLifecycleEventType.PATTERN_RETIRED
        );
    }

    /**
     * Mark a pattern as degraded.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return updated active pattern
     */
    public Promise<ActivePattern> markPatternDegraded(String tenantId, String patternId) {
        return transitionPatternState(
            tenantId,
            patternId,
            PatternLifecycleState.DEGRADED,
            com.ghatana.aep.pattern.lifecycle.PatternLifecycleEventType.PATTERN_DEGRADED
        );
    }

    // ==================== Event Consumption ====================

    /**
     * Start consuming events from Data Cloud Event Plane.
     *
     * @param tenantId tenant identifier
     * @param stream event stream name
     * @return subscription handle
     */
    public DataCloudClient.Subscription startEventConsumption(String tenantId, String stream) {
        return dataCloudClient.tailEvents(tenantId, DataCloudClient.TailRequest.fromLatest(), event -> {
            processEvent(tenantId, event);
        });
    }

    /**
     * Process a single event against all active patterns.
     */
    private void processEvent(String tenantId, Event event) {
        List<ActivePattern> patterns = tenantPatterns.get(tenantId);
        if (patterns == null || patterns.isEmpty()) {
            return;
        }

        EventContext<Map<String, Object>> context = toEventContext(tenantId, event);

        for (ActivePattern pattern : patterns) {
            evaluatePattern(tenantId, pattern, context, event);
        }
    }

    /**
     * Evaluate an event against a specific pattern.
     *
     * <p>P4-03: Handles match, no-match, and late event scenarios with
     * proper uncertainty propagation and explainability recording.
     */
    private void evaluatePattern(String tenantId, ActivePattern active, EventContext<Map<String, Object>> context, Event event) {
        CompiledPattern compiled = active.compiled();

        // Check if pattern matches
        PatternMatchResult match = compiled.matcher().match(context);

        if (match.isMatch()) {
            // Create or update pattern instance
            PatternInstance instance = patternInstances.compute(
                instanceKey(tenantId, active.patternId(), eventId(context)),
                (key, existing) -> {
                    if (existing == null) {
                        return createPatternInstance(tenantId, active, context, match, event);
                    } else {
                        return updatePatternInstance(existing, context, match);
                    }
                }
            );

            // Emit explainability evidence
            explainabilityService.recordMatch(tenantId, active.spec(), context, match, instance);

            // Check if pattern is complete
            if (match.isComplete()) {
                completePatternInstance(tenantId, instance, match);
            }
        } else {
            // P4-03: Handle no-match scenario
            handleNoMatch(tenantId, active, context, match);
        }

        // P4-03: Check for late event handling
        handleLateEvent(tenantId, active, context, event);
    }

    /**
     * Handle no-match scenario for a pattern.
     *
     * <p>P4-03: Records no-match events for learning and observability.
     * <p>WS2: Ingests learning feedback into the pattern learning service.
     */
    private void handleNoMatch(String tenantId, ActivePattern active, EventContext<Map<String, Object>> context, PatternMatchResult match) {
        // Record no-match for learning feedback
        LearningFeedback feedback = LearningFeedback.builder()
            .patternId(active.patternId())
            .executionId(eventId(context))
            .feedbackType(LearningFeedback.FeedbackType.ACCURACY)
            .confidence(match.confidence())
            .addMetric("noMatchReason", match.noMatchReason().orElse("unknown"))
            .addMetric("uncertainty", match.uncertainty())
            .build();

        // WS2: Ingest feedback into learning service
        if (learningService != null) {
            learningService.ingestFeedback(tenantId, feedback)
                .whenException(e -> {
                    // Log error but don't fail pattern evaluation
                    System.err.println("Failed to ingest learning feedback: " + e.getMessage());
                });
        }
    }

    /**
     * Handle late event detection.
     *
     * <p>P4-03: Detects if an event arrives after the pattern instance has already
     * completed or expired, and handles it appropriately.
     */
    private void handleLateEvent(String tenantId, ActivePattern active, EventContext<Map<String, Object>> context, Event event) {
        String instanceKey = instanceKey(tenantId, active.patternId(), eventId(context));
        PatternInstance existing = patternInstances.get(instanceKey);

        if (existing != null && existing.state() == PatternInstanceState.COMPLETED) {
            // Event arrived after pattern completion - handle as late event
            long lateEventDelayMs = java.time.Duration.between(existing.startTime(), event.timestamp()).toMillis();
            
            // Record late event for observability
            Map<String, Object> lateEventMetadata = Map.of(
                "patternId", active.patternId(),
                "instanceId", existing.instanceId(),
                "lateEventDelayMs", lateEventDelayMs,
                "originalCompletionTime", existing.startTime()
            );

            // Emit late event notification
            emitLateEventNotification(tenantId, active, context, lateEventMetadata);
        }
    }

    /**
     * Emit a late event notification.
     */
    private void emitLateEventNotification(String tenantId, ActivePattern active, EventContext<Map<String, Object>> context, Map<String, Object> metadata) {
        Event event = Event.builder()
            .type("pattern.late_event")
            .payload(metadata)
            .source("pattern-engine")
            .correlationId(eventId(context))
            .build();

        dataCloudClient.appendEvent(tenantId, event);
    }

    // ==================== Pattern Instance Management ====================

    private PatternInstance createPatternInstance(
            String tenantId,
            ActivePattern active,
            EventContext<Map<String, Object>> context,
            PatternMatchResult match,
            Event event) {

        return new PatternInstance(
            UUID.randomUUID().toString(),
            active.patternId(),
            tenantId,
            PatternInstanceState.MATCHING,
            eventId(context),
            Instant.now(),
            match.confidence(),
            match.uncertainty(),
            new ArrayList<>(List.of(event)),
            Map.of("triggerEvent", eventId(context))
        );
    }

    private PatternInstance updatePatternInstance(
            PatternInstance instance,
            EventContext<Map<String, Object>> context,
            PatternMatchResult match) {

        // Update confidence and uncertainty
        double newConfidence = combineConfidence(instance.confidence(), match.confidence());
        double newUncertainty = combineUncertainty(instance.uncertainty(), match.uncertainty());

        return new PatternInstance(
            instance.instanceId(),
            instance.patternId(),
            instance.tenantId(),
            instance.state(),
            instance.triggerEventId(),
            instance.startTime(),
            newConfidence,
            newUncertainty,
            instance.events(),
            instance.metadata()
        );
    }

    private void completePatternInstance(String tenantId, PatternInstance instance, PatternMatchResult match) {
        PatternInstance completed = new PatternInstance(
            instance.instanceId(),
            instance.patternId(),
            instance.tenantId(),
            PatternInstanceState.COMPLETED,
            instance.triggerEventId(),
            instance.startTime(),
            match.confidence(),
            match.uncertainty(),
            instance.events(),
            Map.of("completionTime", Instant.now(), "finalMatch", match.toString())
        );

        patternInstances.put(instance.instanceId(), completed);

        // Emit completion event
        emitPatternCompletion(tenantId, completed, match);
    }

    // ==================== Replay Support ====================

    /**
     * Replay pattern detection from a checkpoint.
     *
     * @param tenantId tenant identifier
     * @param fromOffset starting offset for replay
     * @param mode replay mode (DRY_RUN or REPLAY_WITH_SIDE_EFFECTS)
     * @return replay statistics
     */
    public Promise<ReplayStatistics> replay(String tenantId, long fromOffset, ReplayMode mode) {
        return dataCloudClient.replayEvents(tenantId, fromOffset, -1, null)
            .map(events -> {
                int processed = 0;
                int matches = 0;

                for (Event event : events) {
                    processed++;

                    if (mode == ReplayMode.DRY_RUN) {
                        // Dry run: evaluate but don't create instances or side effects
                        evaluateDryRun(tenantId, event);
                    } else {
                        // Replay with side effects
                        processEvent(tenantId, event);
                    }

                    // Count matches
                    if (isMatch(tenantId, event)) {
                        matches++;
                    }
                }

                return new ReplayStatistics(processed, matches, Instant.now());
            });
    }

    private void evaluateDryRun(String tenantId, Event event) {
        // Dry run evaluation without side effects
        List<ActivePattern> patterns = tenantPatterns.get(tenantId);
        if (patterns == null) return;

        for (ActivePattern pattern : patterns) {
            // Match without creating instances
            pattern.compiled().matcher().match(toEventContext(tenantId, event));
        }
    }

    private boolean isMatch(String tenantId, Event event) {
        // Check if event triggered any pattern matches
        return patternInstances.values().stream()
            .anyMatch(instance ->
                instance.tenantId().equals(tenantId) &&
                instance.events().stream()
                    .anyMatch(e -> e.headers().getOrDefault("eventId", "")
                        .equals(event.headers().getOrDefault("eventId", "")))
            );
    }

    // ==================== Event Emission ====================

    private void emitPatternCompletion(String tenantId, PatternInstance instance, PatternMatchResult match) {
        Map<String, Object> payload = Map.of(
            "patternId", instance.patternId(),
            "instanceId", instance.instanceId(),
            "confidence", instance.confidence(),
            "uncertainty", instance.uncertainty(),
            "matchResult", match.toMap()
        );

        Event event = Event.builder()
            .type("pattern.completed")
            .payload(payload)
            .source("pattern-engine")
            .correlationId(instance.instanceId())
            .build();

        dataCloudClient.appendEvent(tenantId, event);
    }

    // ==================== Utility Methods ====================

    private String instanceKey(String tenantId, String patternId, String eventId) {
        return String.format("%s:%s:%s", tenantId, patternId, eventId);
    }

    private EventContext<Map<String, Object>> toEventContext(String tenantId, Event event) {
        String eventId = eventId(event);
        CanonicalEvent canonicalEvent = new CanonicalEvent(
            eventId,
            tenantId,
            event.type(),
            event.schemaVersion().orElse("1.0.0"),
            event.timestamp(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of("source", event.source().orElse("data-cloud")),
            List.of(),
            event.correlationId().orElse(eventId),
            event.causationId(),
            event.payload(),
            Map.of(),
            Map.of("headers", event.headers()),
            List.of(),
            event.headers().getOrDefault("idempotencyKey", eventId));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("type", event.type());
        input.put("payload", event.payload());
        input.put("headers", event.headers());
        input.put("timestamp", event.timestamp());

        return new EventContext<>(
            tenantId,
            List.of(canonicalEvent),
            Optional.empty(),
            Optional.empty(),
            Map.of("eventId", eventId),
            new EventTimeContext(
                EventTimeContext.TimeMode.EVENT_TIME,
                Optional.empty(),
                Duration.ZERO,
                EventTimeContext.LateEventBehavior.INCORPORATE,
                Optional.empty()),
            UncertaintyContext.certain(),
            new ReplayContext(ReplayContext.ReplayMode.LIVE, Optional.empty(), Optional.empty(), Optional.empty(), Map.of()),
            Optional.of(input));
    }

    private String eventId(Event event) {
        String headerEventId = event.headers().get("eventId");
        if (headerEventId != null && !headerEventId.isBlank()) {
            return headerEventId;
        }
        return event.correlationId().orElseGet(() -> UUID.nameUUIDFromBytes(
            (event.type() + "|" + event.timestamp() + "|" + event.payload()).getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .toString());
    }

    private String eventId(EventContext<?> context) {
        return context.events().stream()
            .findFirst()
            .map(CanonicalEvent::eventId)
            .orElseGet(() -> Objects.toString(context.bindings().get("eventId"), "unknown-event"));
    }

    private double combineConfidence(double c1, double c2) {
        // Simple confidence combination (can be improved with more sophisticated logic)
        return Math.min(1.0, c1 + c2 * (1 - c1));
    }

    private double combineUncertainty(double u1, double u2) {
        // Uncertainty grows with additional matches
        return Math.min(1.0, Math.max(u1, u2));
    }

    // ==================== Supporting Types ====================

    public record ActivePattern(
        String patternId,
        String tenantId,
        PatternSpec spec,
        CompiledPattern compiled,
        Instant activationTime,
        PatternLifecycleState state
    ) {}

    public record PatternInstance(
        String instanceId,
        String patternId,
        String tenantId,
        PatternInstanceState state,
        String triggerEventId,
        Instant startTime,
        double confidence,
        double uncertainty,
        List<Event> events,
        Map<String, Object> metadata
    ) {}

    public record ReplayStatistics(
        int eventsProcessed,
        int patternsMatched,
        Instant completionTime
    ) {}

    // WS2-4: Use canonical PatternLifecycleState from operator-contracts
    // PatternLifecycleState is now imported from com.ghatana.aep.pattern.lifecycle.PatternLifecycleState

    public enum PatternInstanceState {
        MATCHING, COMPLETED, EXPIRED, CANCELLED
    }

    public enum ReplayMode {
        DRY_RUN,           // Evaluate without side effects
        REPLAY_WITH_SIDE_EFFECTS  // Full replay with side effects
    }

    // ==================== Service Interfaces ====================

    /**
     * Pattern registry for persistence.
     */
    public interface PatternRegistry {
        Promise<Void> register(String tenantId, PatternSpec spec);
        Promise<Boolean> unregister(String tenantId, String patternId);
        Promise<List<PatternSpec>> list(String tenantId);
        Promise<Void> updateState(String tenantId, String patternId, PatternLifecycleState state);
    }

    /**
     * Explainability service for recording match evidence.
     */
    public interface ExplainabilityService {
        void recordMatch(String tenantId, PatternSpec spec, EventContext<?> context, PatternMatchResult match, PatternInstance instance);
    }
}

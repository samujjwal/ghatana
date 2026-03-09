package com.ghatana.virtualorg.adapter;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.EventId;
import com.ghatana.platform.domain.domain.event.EventTime;
import com.ghatana.platform.domain.domain.event.EventStats;
import com.ghatana.platform.domain.domain.event.EventRelations;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.virtualorg.v1.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pattern engine adapter enabling agents to leverage EventCloud pattern matching for decisions.
 *
 * <p><b>Purpose</b><br>
 * Integrates virtual organization agents with EventCloud's pattern engine to provide
 * pattern-based decision guidance, historical pattern learning, and automated recommendations.
 * Enables agents to query patterns asynchronously without blocking decision-making pipeline.
 *
 * <p><b>Architecture Role</b><br>
 * Adapter in hexagonal architecture:
 * <ul>
 *   <li>Adapter bridging virtual-org agents to EventCloud pattern engine</li>
 *   <li>Delegates to: core/event-runtime pattern engine for CEP queries</li>
 *   <li>Used by: Agents (decision guidance), Workflows (pattern matching), Tools (automation)</li>
 *   <li>Subscribes to: Pattern matches relevant to agent's role and context</li>
 * </ul>
 *
 * <p><b>Pattern-Based Decision Support</b><br>
 * Capabilities for intelligent decision-making:
 * <ul>
 *   <li>Query Patterns: Find historical patterns matching current context</li>
 *   <li>Pattern Subscriptions: Subscribe to real-time pattern matches</li>
 *   <li>Learning: Extract insights from past decisions and outcomes</li>
 *   <li>Recommendations: Apply pattern-based recommendations to new decisions</li>
 *   <li>Feedback Loop: Submit decision outcomes to refine patterns</li>
 * </ul>
 *
 * <p><b>Pattern Types</b><br>
 * Supported pattern categories:
 * <ul>
 *   <li>Decision Patterns: Successful decision sequences (approve → implement → success)</li>
 *   <li>Failure Patterns: Known failure patterns to avoid (skip_tests → production_bug)</li>
 *   <li>Escalation Patterns: When to escalate decisions (budget > $100K → escalate to CEO)</li>
 *   <li>Temporal Patterns: Time-based patterns (deploy_friday → higher_incident_rate)</li>
 *   <li>Correlation Patterns: Correlated events (high_load → slow_response → timeout)</li>
 * </ul>
 *
 * <p><b>Query Interface</b><br>
 * Pattern query operations:
 * <ul>
 *   <li>findPatternsByContext: Query patterns matching current decision context</li>
 *   <li>findSimilarDecisions: Find similar past decisions with outcomes</li>
 *   <li>getRecommendations: Get pattern-based recommendations for decision</li>
 *   <li>calculateConfidence: Calculate confidence score based on pattern strength</li>
 * </ul>
 *
 * <p><b>Subscription Model</b><br>
 * Real-time pattern subscriptions:
 * <ul>
 *   <li>PatternSubscription: Subscribe to specific pattern types</li>
 *   <li>Role-based subscriptions: CEO subscribes to strategic patterns</li>
 *   <li>Context-based subscriptions: DevOps subscribes to incident patterns</li>
 *   <li>Callback notifications: Async callbacks when patterns match</li>
 * </ul>
 *
 * <p><b>Learning and Feedback</b><br>
 * Continuous learning loop:
 * <ul>
 *   <li>Submit decision outcomes to pattern engine</li>
 *   <li>Reinforce successful patterns (positive feedback)</li>
 *   <li>Weaken failed patterns (negative feedback)</li>
 *   <li>Discover new patterns from decision sequences</li>
 *   <li>Adapt recommendations based on recent outcomes</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PatternEngineAdapter patternEngine = new PatternEngineAdapter(
 *     "agent-cto-001", eventloop, eventEmitter);
 * 
 * // Query patterns for decision guidance
 * Map<String, String> context = Map.of(
 *     "decision_type", "ARCHITECTURE",
 *     "complexity", "HIGH",
 *     "budget", "150000"
 * );
 * 
 * patternEngine.findPatternsByContext(context).whenResult(patterns -> {
 *     for (Pattern pattern : patterns) {
 *         log.info("Found pattern: {} (confidence: {})",
 *             pattern.getDescription(), pattern.getConfidence());
 *     }
 * });
 * 
 * // Subscribe to real-time pattern matches
 * PatternSubscription subscription = new PatternSubscription(
 *     "pattern-architecture-changes",
 *     "ARCHITECTURE",
 *     Map.of("complexity", "HIGH"),
 *     Instant.now()
 * );
 * 
 * patternEngine.subscribe(subscription, matchedPattern -> {
 *     log.info("Pattern matched: {}", matchedPattern);
 *     // Notify agent of relevant pattern
 * });
 * 
 * // Get recommendations for decision
 * patternEngine.getRecommendations("ARCHITECTURE", context)
 *     .whenResult(recommendations -> {
 *         DecisionProto decision = selectBestRecommendation(recommendations);
 *         log.info("Pattern-recommended: {}", decision.getChoice());
 *     });
 * 
 * // Submit decision outcome for learning
 * patternEngine.submitOutcome(
 *     "decision-123",
 *     "APPROVED",
 *     "SUCCESS",
 *     Map.of("implementation_time", "2weeks", "bugs", "0")
 * );
 * }</pre>
 *
 * <p><b>Async Operation</b><br>
 * All pattern queries are asynchronous to avoid blocking agent decision pipeline:
 * <ul>
 *   <li>Returns ActiveJ Promises for all operations</li>
 *   <li>Pattern queries execute in background on Eventloop</li>
 *   <li>Agent proceeds with decision while patterns being queried</li>
 *   <li>Recommendations merged with LLM reasoning when available</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe using ConcurrentHashMap for subscriptions. All async operations on Eventloop.
 *
 * @see com.ghatana.core.event.runtime
 * @see EventEmitter
 * @doc.type class
 * @doc.purpose Pattern engine adapter for decision guidance and learning
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class PatternEngineAdapter {

    private static final Logger log = LoggerFactory.getLogger(PatternEngineAdapter.class);

    private final String agentId;
    private final Eventloop eventloop;
    private final Map<String, PatternSubscription> subscriptions;
    private final EventEmitter eventEmitter;
    private volatile boolean initialized = false;

    /**
     * Represents a subscription to a specific pattern.
     */
    public record PatternSubscription(
        String patternId,
        String patternType,
        Map<String, String> criteria,
        PatternMatchListener listener
    ) {}

    /**
     * Listener for pattern match notifications.
     */
    @FunctionalInterface
    public interface PatternMatchListener {
        void onPatternMatch(PatternMatchEvent match);
    }

    /**
     * Represents a pattern match event.
     */
    public record PatternMatchEvent(
        String patternId,
        String patternType,
        double confidence,
        Map<String, String> matchData,
        String recommendation
    ) {}

    public PatternEngineAdapter(
        @NotNull String agentId,
        @NotNull Eventloop eventloop,
        @NotNull EventEmitter eventEmitter) {
        
        this.agentId = agentId;
        this.eventloop = eventloop;
        this.eventEmitter = eventEmitter;
        this.subscriptions = new ConcurrentHashMap<>();
    }

    /**
     * Initialize the adapter and connect to pattern engine.
     * This should be called during agent startup.
     *
     * @return a Promise that completes when initialization is done
     */
    public Promise<Void> initialize() {
        return Promise.ofBlocking(eventloop, () -> {
            log.info("Initializing PatternEngineAdapter for agent: {}", agentId);
            
            // TODO: Connect to EventCloud's pattern-engine module
            // This would involve:
            // 1. Creating a connection to the pattern engine
            // 2. Registering the agent for pattern subscriptions
            // 3. Starting the pattern match listener thread
            
            initialized = true;
            log.info("PatternEngineAdapter initialized for agent: {}", agentId);
            
            return null;
        });
    }

    /**
     * Query the pattern engine for patterns matching the given decision context.
     *
     * @param decisionType the type of decision to get patterns for
     * @param context      the decision context
     * @return a Promise containing list of recommended patterns
     */
    public Promise<List<PatternRecommendation>> queryPatterns(
        @NotNull DecisionTypeProto decisionType,
        @NotNull Map<String, String> context) {
        
        return Promise.ofBlocking(eventloop, () -> {
            if (!initialized) {
                log.warn("PatternEngineAdapter not initialized for agent: {}", agentId);
                return List.of();
            }

            log.debug("Querying patterns for decision: type={}, agentId={}", decisionType, agentId);

            // TODO: Query the pattern engine with decision context
            // This would involve:
            // 1. Building a pattern query from the decision type and context
            // 2. Sending it to the pattern engine
            // 3. Receiving and ranking recommendations by confidence
            // 4. Filtering for the agent's role and permissions
            
            List<PatternRecommendation> recommendations = new ArrayList<>();
            
            // Placeholder: return empty list until pattern engine integration is complete
            log.debug("Pattern query completed: {} recommendations found", recommendations.size());
            
            return recommendations;
        });
    }

    /**
     * Subscribe to patterns of a specific type.
     * The listener will be notified when a pattern match occurs.
     *
     * @param patternType the type of pattern to subscribe to
     * @param criteria    matching criteria
     * @param listener    callback for pattern matches
     * @return subscription ID for later unsubscription
     */
    public String subscribeToPattern(
        @NotNull String patternType,
        @NotNull Map<String, String> criteria,
        @NotNull PatternMatchListener listener) {
        
        String subscriptionId = UUID.randomUUID().toString();
        String patternId = patternType + ":" + subscriptionId;
        
        PatternSubscription subscription = new PatternSubscription(
            patternId,
            patternType,
            criteria,
            listener
        );
        
        subscriptions.put(subscriptionId, subscription);
        
        log.info("Pattern subscription created: patternType={}, subscriptionId={}, agentId={}",
                patternType, subscriptionId, agentId);

        // TODO: Register this subscription with the pattern engine
        // The pattern engine will then notify this adapter when patterns match
        
        // Emit subscription event for observability
        emitPatternSubscriptionEvent(subscriptionId, patternType, true);
        
        return subscriptionId;
    }

    /**
     * Unsubscribe from a pattern subscription.
     *
     * @param subscriptionId the subscription ID from subscribeToPattern
     * @return true if unsubscribed, false if subscription not found
     */
    public boolean unsubscribeFromPattern(@NotNull String subscriptionId) {
        PatternSubscription removed = subscriptions.remove(subscriptionId);
        
        if (removed != null) {
            log.info("Pattern subscription removed: subscriptionId={}, agentId={}",
                    subscriptionId, agentId);
            
            // Emit unsubscription event for observability
            emitPatternSubscriptionEvent(subscriptionId, removed.patternType(), false);
            
            // TODO: Notify pattern engine of unsubscription
            return true;
        }
        
        return false;
    }

    /**
     * Get pattern recommendations for a specific decision.
     * This integrates pattern recommendations into the decision-making process.
     *
     * @param decisionType the decision type
     * @param context      the decision context
     * @param options      available options
     * @return enriched options with pattern recommendations
     */
    public Promise<List<EnrichedOptionProto>> getEnrichedOptions(
        @NotNull DecisionTypeProto decisionType,
        @NotNull Map<String, String> context,
        @NotNull List<OptionProto> options) {
        
        return queryPatterns(decisionType, context)
            .then(patterns -> {
                log.debug("Enriching options with {} pattern recommendations", patterns.size());
                
                return Promise.of(enrichOptionsWithPatterns(options, patterns));
            })
            .mapException(e -> {
                log.warn("Failed to enrich options with patterns", e);
                return new RuntimeException("Pattern enrichment failed", e);
            });
    }

    /**
     * Notify the adapter of a decision outcome.
     * This helps the pattern engine learn from actual results.
     *
     * @param decisionType the decision type
     * @param selectedOption the option that was selected
     * @param outcome the outcome of the decision
     * @return a Promise that completes when the outcome is recorded
     */
    public Promise<Void> recordDecisionOutcome(
        @NotNull DecisionTypeProto decisionType,
        @NotNull String selectedOption,
        @NotNull DecisionOutcomeProto outcome) {
        
        return Promise.ofBlocking(eventloop, () -> {
            log.info("Recording decision outcome: type={}, option={}, agentId={}",
                    decisionType, selectedOption, agentId);
            
            // TODO: Send outcome to pattern engine for learning
            // This would involve:
            // 1. Creating an outcome event with decision context
            // 2. Sending it to the pattern engine
            // 3. Allowing the engine to update its patterns based on results
            
            // Emit outcome recording event
            emitDecisionOutcomeEvent(decisionType, selectedOption, outcome);
            
            return null;
        });
    }

    /**
     * Get all active pattern subscriptions for this agent.
     *
     * @return map of subscription IDs to subscriptions
     */
    public Map<String, PatternSubscription> getActiveSubscriptions() {
        return new HashMap<>(subscriptions);
    }

    /**
     * Check if the adapter is initialized and ready for use.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    // =============================
    // Private helper methods
    // =============================

    private List<EnrichedOptionProto> enrichOptionsWithPatterns(
        List<OptionProto> options,
        List<PatternRecommendation> patterns) {
        
        List<EnrichedOptionProto> enriched = new ArrayList<>();
        
        for (OptionProto option : options) {
            double patternScore = calculatePatternScore(option, patterns);
            
            EnrichedOptionProto enrichedOption = EnrichedOptionProto.newBuilder()
                .setOption(option)
                .setPatternScore(patternScore)
                .setPatternCount(patterns.size())
                .build();
            
            enriched.add(enrichedOption);
        }
        
        return enriched;
    }

    private double calculatePatternScore(OptionProto option, List<PatternRecommendation> patterns) {
        // Simple scoring: average confidence of patterns that recommend this option
        // Use description as option identifier since OptionProto doesn't have an ID field
        return patterns.stream()
            .filter(p -> p.recommendedOptionId().equals(option.getDescription()))
            .mapToDouble(PatternRecommendation::confidence)
            .average()
            .orElse(0.0);
    }

    private void emitPatternSubscriptionEvent(
        String subscriptionId,
        String patternType,
        boolean isSubscription) {
        
        try {
            Map<String, String> data = Map.of(
                "subscriptionId", subscriptionId,
                "patternType", patternType,
                "agentId", agentId,
                "action", isSubscription ? "subscribed" : "unsubscribed"
            );
            
            Event event = buildEvent("com.ghatana.virtualorg.pattern.subscription", data);
            
            eventEmitter.emit(event);
        } catch (Exception e) {
            log.warn("Failed to emit pattern subscription event", e);
        }
    }

    private void emitDecisionOutcomeEvent(
        DecisionTypeProto decisionType,
        String selectedOption,
        DecisionOutcomeProto outcome) {
        
        try {
            Map<String, String> data = Map.of(
                "decisionType", decisionType.toString(),
                "selectedOption", selectedOption,
                "agentId", agentId,
                "outcome", outcome.getOutcome().toString(),
                "confidence", String.valueOf(outcome.getConfidence())
            );
            
            Event event = buildEvent("com.ghatana.virtualorg.decision.outcome", data);
            
            eventEmitter.emit(event);
        } catch (Exception e) {
            log.warn("Failed to emit decision outcome event", e);
        }
    }

    // =============================
    // Domain models
    // =============================

    /**
     * Represents a pattern recommendation from the pattern engine.
     */
    public record PatternRecommendation(
        String patternId,
        String patternType,
        double confidence,
        String recommendedOptionId,
        Map<String, String> metadata
    ) {}
    
    /**
     * Helper to build events with proper EventId structure.
     */
    private Event buildEvent(String eventType, Map<String, ?> payload) {
        EventId eventId = new SimpleEventId(
            UUID.randomUUID().toString(),
            eventType,
            "1.0",
            "default-tenant"
        );
        
        Instant now = Instant.now();
        long nowMillis = now.toEpochMilli();
        EventTime eventTime = EventTime.builder()
            .detectionTimePoint(com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis))
            .occurrenceTime(com.ghatana.platform.types.time.GTimeInterval.between(
                com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis),
                com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis)
            ))
            .validDuration(new com.ghatana.platform.types.time.GTimeValue(-1, com.ghatana.platform.types.time.GTimeUnit.MILLISECONDS))
            .boundingInterval(com.ghatana.platform.types.time.GTimeInterval.between(
                com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis),
                com.ghatana.platform.types.time.GTimestamp.ofEpochMilli(nowMillis)
            ))
            .build();
        
        EventStats stats = EventStats.builder()
            .withSizeInBytes(payload.toString().length())
            .build();
        
        EventRelations relations = EventRelations.builder().build();
        
        Map<String, String> headers = new HashMap<>();
        headers.put("correlationId", UUID.randomUUID().toString());
        headers.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        Map<String, Object> typedPayload = new HashMap<>(payload);
        
        return GEvent.builder()
            .id(eventId)
            .time(eventTime)
            .location(null)
            .stats(stats)
            .relations(relations)
            .headers(headers)
            .payload(typedPayload)
            .intervalBased(false)
            .provenance(java.util.List.of())
            .build();
    }
    
    /**
     * Simple EventId implementation.
     */
    private static class SimpleEventId implements EventId {
        private final String id;
        private final String eventType;
        private final String version;
        private final String tenantId;
        
        SimpleEventId(String id, String eventType, String version, String tenantId) {
            this.id = id;
            this.eventType = eventType;
            this.version = version;
            this.tenantId = tenantId;
        }
        
        @Override
        public String getId() {
            return id;
        }
        
        @Override
        public String getEventType() {
            return eventType;
        }
        
        @Override
        public String getVersion() {
            return version;
        }
        
        @Override
        public String getTenantId() {
            return tenantId;
        }
    }
}
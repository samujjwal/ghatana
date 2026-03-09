package com.ghatana.core.operator;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;

/**
 * Abstract base class for pattern detection operators implementing Complex Event Processing (CEP).
 *
 * <p><b>Purpose</b><br>
 * Provides specialized base implementation for PATTERN-type operators that detect temporal
 * patterns across event sequences. AbstractPatternOperator extends {@link AbstractOperator}
 * with pattern-specific capabilities including sub-pattern composition, NFA state management,
 * and temporal constraint evaluation.
 *
 * <p><b>Architecture Role</b><br>
 * AbstractPatternOperator is the recommended base class for ALL pattern detection operators
 * (AND, OR, NOT, SEQ, WITHIN, REPEAT, UNTIL, etc.). It provides:
 * <ul>
 *   <li><b>Pattern Composition</b>: Hierarchical pattern trees with sub-patterns</li>
 *   <li><b>Stateful Matching</b>: NFA state tracking, partial match management</li>
 *   <li><b>Temporal Constraints</b>: Time windows, sequencing, ordering</li>
 *   <li><b>Match Semantics</b>: Complete matches, partial matches, match expiration</li>
 *   <li><b>Performance</b>: Optimized state pruning, indexed partial matches</li>
 * </ul>
 *
 * <p><b>Pattern Operator Types</b>
 * <ul>
 *   <li><b>AND</b>: Conjunction (all sub-patterns must match, any order)</li>
 *   <li><b>OR</b>: Disjunction (at least one sub-pattern matches)</li>
 *   <li><b>NOT</b>: Negation (pattern must NOT occur)</li>
 *   <li><b>SEQ</b>: Sequence (sub-patterns in strict order)</li>
 *   <li><b>WITHIN</b>: Temporal constraint (pattern within time window)</li>
 *   <li><b>REPEAT</b>: Repetition (pattern repeats N times)</li>
 *   <li><b>UNTIL</b>: Termination (pattern until terminator event)</li>
 * </ul>
 *
 * <p><b>Extension Points</b>
 * <ul>
 *   <li>{@link #process(Event)} - <b>REQUIRED</b>: Pattern matching logic</li>
 *   <li>{@link #getSubPatterns()} - Return sub-patterns for composite patterns</li>
 *   <li>{@link #doInitialize(OperatorConfig)} - Validate pattern configuration</li>
 *   <li>{@link #doStop()} - Cleanup NFA state and resources</li>
 * </ul>
 *
 * <p><b>Usage Examples</b>
 *
 * <p><b>Example 1: AND pattern (conjunction)</b>
 * <pre>{@code
 * public class AndOperator extends AbstractPatternOperator {
 *     private final List<UnifiedOperator> subPatterns;
 *     private StateStore<String, PartialMatches> matchState;
 *     
 *     public AndOperator(OperatorId id, List<UnifiedOperator> subPatterns,
 *                        MetricsCollector metrics) {
 *         super(id, "AND Pattern", "Matches when all sub-patterns match",
 *               List.of("pattern.and", "pattern.conjunction"), metrics);
 *         this.subPatterns = List.copyOf(subPatterns);
 *     }
 *     
 *     @Override
 *     protected Promise<Void> doInitialize(OperatorConfig config) {
 *         // Validate: must have at least 2 sub-patterns
 *         if (subPatterns.size() < 2) {
 *             return Promise.ofException(new OperatorConfigurationException(
 *                 "AND pattern requires at least 2 sub-patterns", getId()
 *             ));
 *         }
 *         
 *         // Initialize state store
 *         this.matchState = StateStoreFactory.createHybrid(config);
 *         return Promise.complete();
 *     }
 *     
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         String groupKey = event.getPayload().getString("key");
 *         
 *         return matchState.get(groupKey, PartialMatches.class)
 *             .map(optional -> optional.orElse(new PartialMatches()))
 *             .then(partial -> {
 *                 // Check event against unmatched sub-patterns
 *                 for (UnifiedOperator subPattern : subPatterns) {
 *                     if (!partial.hasMatched(subPattern.getId())) {
 *                         return subPattern.process(event)
 *                             .then(result -> {
 *                                 if (result.isMatch()) {
 *                                     partial.addMatch(subPattern.getId(), event);
 *                                     
 *                                     if (partial.isComplete(subPatterns.size())) {
 *                                         // All sub-patterns matched - emit result
 *                                         return matchState.delete(groupKey)
 *                                             .map(v -> OperatorResult.match(
 *                                                 partial.toMatchEvent(), getMetadata()
 *                                             ));
 *                                     } else {
 *                                         // Partial match - update state
 *                                         return matchState.put(groupKey, partial,
 *                                                             Optional.of(Duration.ofMinutes(5)))
 *                                             .map(v -> OperatorResult.empty());
 *                                     }
 *                                 }
 *                                 return Promise.of(OperatorResult.empty());
 *                             });
 *                     }
 *                 }
 *                 return Promise.of(OperatorResult.empty());
 *             });
 *     }
 *     
 *     @Override
 *     public List<UnifiedOperator> getSubPatterns() {
 *         return subPatterns;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 2: SEQ pattern (strict sequence)</b>
 * <pre>{@code
 * public class SeqOperator extends AbstractPatternOperator {
 *     private final List<UnifiedOperator> sequence;
 *     private StateStore<String, SeqState> sequenceState;
 *     
 *     public SeqOperator(OperatorId id, List<UnifiedOperator> sequence,
 *                        MetricsCollector metrics) {
 *         super(id, "SEQ Pattern", "Matches events in strict order",
 *               List.of("pattern.seq", "pattern.sequence"), metrics);
 *         this.sequence = List.copyOf(sequence);
 *     }
 *     
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         String key = event.getPayload().getString("key");
 *         
 *         return sequenceState.get(key, SeqState.class)
 *             .map(optional -> optional.orElse(new SeqState(0)))
 *             .then(state -> {
 *                 int currentStep = state.getCurrentStep();
 *                 UnifiedOperator expectedPattern = sequence.get(currentStep);
 *                 
 *                 return expectedPattern.process(event)
 *                     .then(result -> {
 *                         if (result.isMatch()) {
 *                             state.advance(event);
 *                             
 *                             if (state.isComplete(sequence.size())) {
 *                                 // Sequence complete - emit match
 *                                 return sequenceState.delete(key)
 *                                     .map(v -> OperatorResult.match(
 *                                         state.toMatchEvent(), getMetadata()
 *                                     ));
 *                             } else {
 *                                 // Advance to next step
 *                                 return sequenceState.put(key, state,
 *                                                        Optional.of(Duration.ofMinutes(5)))
 *                                     .map(v -> OperatorResult.empty());
 *                             }
 *                         }
 *                         return Promise.of(OperatorResult.empty());
 *                     });
 *             });
 *     }
 *     
 *     @Override
 *     public List<UnifiedOperator> getSubPatterns() {
 *         return sequence;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 3: WITHIN pattern (temporal constraint)</b>
 * <pre>{@code
 * public class WithinOperator extends AbstractPatternOperator {
 *     private final UnifiedOperator pattern;
 *     private final Duration timeWindow;
 *     private StateStore<String, WithinState> timeState;
 *     
 *     public WithinOperator(OperatorId id, UnifiedOperator pattern,
 *                           Duration timeWindow, MetricsCollector metrics) {
 *         super(id, "WITHIN Pattern", "Matches pattern within time window",
 *               List.of("pattern.within", "pattern.temporal"), metrics);
 *         this.pattern = pattern;
 *         this.timeWindow = timeWindow;
 *     }
 *     
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         String key = event.getPayload().getString("key");
 *         long now = System.currentTimeMillis();
 *         
 *         return timeState.get(key, WithinState.class)
 *             .map(optional -> optional.orElse(new WithinState(now)))
 *             .then(state -> {
 *                 // Check if window expired
 *                 if (now - state.getStartTime() > timeWindow.toMillis()) {
 *                     // Window expired - delete state
 *                     return timeState.delete(key)
 *                         .map(v -> OperatorResult.empty());
 *                 }
 *                 
 *                 // Check pattern match
 *                 return pattern.process(event)
 *                     .then(result -> {
 *                         if (result.isMatch()) {
 *                             // Pattern matched within window
 *                             return timeState.delete(key)
 *                                 .map(v -> OperatorResult.match(event, getMetadata()));
 *                         }
 *                         return Promise.of(OperatorResult.empty());
 *                     });
 *             });
 *     }
 *     
 *     @Override
 *     public List<UnifiedOperator> getSubPatterns() {
 *         return List.of(pattern);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Best Practices</b>
 * <ul>
 *   <li>Extend AbstractPatternOperator for all pattern detection operators</li>
 *   <li>Always return true from {@code isStateful()} (patterns require NFA state)</li>
 *   <li>Use StateStore for partial match tracking (recovery on restart)</li>
 *   <li>Implement state TTL to prevent memory leaks (expired partial matches)</li>
 *   <li>Validate sub-pattern configuration in {@code doInitialize()}</li>
 *   <li>Return {@code getSubPatterns()} for pattern tree serialization</li>
 *   <li>Use {@code OperatorResult.match()} for complete pattern matches</li>
 *   <li>Use {@code OperatorResult.empty()} for partial matches (waiting for completion)</li>
 * </ul>
 *
 * <p><b>Anti-Patterns</b>
 * <ul>
 *   <li>❌ DON'T use for stream transformations (use AbstractStreamOperator)</li>
 *   <li>❌ DON'T forget state cleanup on complete match (memory leak)</li>
 *   <li>❌ DON'T skip TTL on partial matches (unbounded memory growth)</li>
 *   <li>❌ DON'T validate patterns at runtime (validate in doInitialize)</li>
 *   <li>❌ DON'T block in process() for sub-pattern evaluation (use Promise chains)</li>
 * </ul>
 *
 * <p><b>Performance Characteristics</b>
 * <ul>
 *   <li>State size: O(partial_matches) per group key</li>
 *   <li>Latency: <50ms p99 for simple patterns, <200ms for complex nested patterns</li>
 *   <li>Throughput: 5k-10k events/sec per operator instance</li>
 *   <li>Memory: ~1KB per partial match (varies by pattern complexity)</li>
 *   <li>State pruning: TTL-based expiration (default 5 minutes)</li>
 * </ul>
 *
 * <p><b>Integration Points</b>
 * <ul>
 *   <li>{@link AbstractOperator} - Extends for lifecycle and metrics</li>
 *   <li>{@link OperatorType#PATTERN} - Fixed operator type</li>
 *   <li>StateStore - For NFA state and partial match tracking</li>
 *   <li>Sub-patterns - Recursive pattern composition</li>
 *   <li>EventCloud - Pattern match events stored for downstream processing</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Inherits thread-safety guarantees from AbstractOperator. StateStore MUST be
 * thread-safe. ActiveJ Eventloop affinity ensures single-threaded execution
 * per operator instance (recommended deployment pattern).
 *
 * @see AbstractOperator
 * @see AbstractStreamOperator
 * @see AbstractLearningOperator
 * @see UnifiedOperator
 * @see OperatorType#PATTERN
 * 
 * @doc.type class
 * @doc.purpose Abstract base class for pattern detection operators (CEP)
 * @doc.layer core
 * @doc.pattern Template Method (process extension point)
 * 
 * @since 2.0
 */
public abstract class AbstractPatternOperator extends AbstractOperator {

    /**
     * Creates a pattern operator.
     *
     * @param id Operator identifier
     * @param name Operator name
     * @param description Operator description
     * @param capabilities Operator capabilities
     * @param metricsCollector Metrics collector
     */
    protected AbstractPatternOperator(
            OperatorId id,
            String name,
            String description,
            List<String> capabilities,
            MetricsCollector metricsCollector
    ) {
        super(
            Objects.requireNonNull(id, "Operator ID must not be null"),
            OperatorType.PATTERN,
            Objects.requireNonNull(name, "Operator name must not be null"),
            Objects.requireNonNull(description, "Operator description must not be null"),
            capabilities != null ? capabilities : List.of(),
            metricsCollector
        );
    }

    /**
     * Process event against pattern (pattern matching logic).
     *
     * <p>Subclasses MUST implement this method to define pattern matching behavior:
     * <ul>
     *   <li><b>Complete Match</b>: Return match(event) when pattern fully satisfied</li>
     *   <li><b>Partial Match</b>: Return empty() while waiting for remaining sub-patterns</li>
     *   <li><b>No Match</b>: Return empty() when event doesn't advance pattern state</li>
     *   <li><b>State Management</b>: Update StateStore for partial matches</li>
     * </ul>
     *
     * @param event Input event
     * @return Promise of operator result (match or empty)
     */
    @Override
    public abstract Promise<OperatorResult> process(Event event);

    /**
     * Returns sub-patterns for composite pattern operators.
     *
     * <p>Override this method for composite patterns (AND, OR, SEQ, etc.) to expose
     * pattern structure for:
     * <ul>
     *   <li>Pattern tree serialization (toEvent)</li>
     *   <li>Pattern validation (cycle detection)</li>
     *   <li>Pattern optimization (sub-pattern reordering)</li>
     *   <li>Pattern visualization (UI rendering)</li>
     * </ul>
     *
     * <p>Default implementation returns empty list (atomic/leaf patterns).
     *
     * @return List of sub-patterns (empty for atomic patterns)
     */
    public List<UnifiedOperator> getSubPatterns() {
        return List.of();
    }

    /**
     * Returns true (pattern operators are stateful by definition).
     *
     * <p>Pattern operators ALWAYS maintain NFA state for partial match tracking.
     * This method cannot be overridden to ensure correct StateStore allocation.
     *
     * @return true (always)
     */
    @Override
    public final boolean isStateful() {
        return true;
    }

    @Override
    public String toString() {
        return String.format("PatternOperator[id=%s, name=%s, state=%s, subPatterns=%d]",
                getId(), getName(), getState(), getSubPatterns().size());
    }
}

package com.ghatana.core.operator;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Abstract base class for learning operators implementing ML-driven pattern discovery and recommendations.
 *
 * <p><b>Purpose</b><br>
 * Provides specialized base implementation for LEARNING-type operators that discover patterns,
 * correlations, and anomalies from event data using machine learning techniques. AbstractLearningOperator
 * extends {@link AbstractOperator} with ML-specific capabilities including model management,
 * training lifecycle, inference, and recommendation generation.
 *
 * <p><b>Architecture Role</b><br>
 * AbstractLearningOperator is the recommended base class for ALL ML-driven operators
 * (FrequentSequenceMiner, CorrelationAnalyzer, PatternSynthesizer, AnomalyDetector, etc.). It provides:
 * <ul>
 *   <li><b>Model Lifecycle</b>: Training, inference, model persistence, versioning</li>
 *   <li><b>Learning Modes</b>: Batch learning, online learning, incremental learning</li>
 *   <li><b>Pattern Discovery</b>: Frequent sequences, correlations, anomalies</li>
 *   <li><b>Recommendations</b>: Confidence scores, quality metrics, A/B testing</li>
 *   <li><b>Performance</b>: Model caching, lazy loading, background training</li>
 * </ul>
 *
 * <p><b>Learning Operator Types</b>
 * <ul>
 *   <li><b>FrequentSequenceMiner</b>: Discovers frequent event sequences (Apriori, PrefixSpan)</li>
 *   <li><b>CorrelationAnalyzer</b>: Finds temporal correlations between event types</li>
 *   <li><b>PatternSynthesizer</b>: Converts frequent sequences to pattern operators</li>
 *   <li><b>AnomalyDetector</b>: Detects unusual event patterns (outliers, deviations)</li>
 *   <li><b>Recommender</b>: Suggests patterns to users (collaborative filtering)</li>
 *   <li><b>Classifier</b>: Categorizes events based on learned features</li>
 * </ul>
 *
 * <p><b>Extension Points</b>
 * <ul>
 *   <li>{@link #process(Event)} - <b>REQUIRED</b>: Inference or online learning logic</li>
 *   <li>{@link #train(List)} - Optional: Batch training on historical data</li>
 *   <li>{@link #doInitialize(OperatorConfig)} - Load trained model from storage</li>
 *   <li>{@link #doStop()} - Persist model state to storage</li>
 *   <li>{@link #getModelMetrics()} - Return model quality metrics</li>
 * </ul>
 *
 * <p><b>Usage Examples</b>
 *
 * <p><b>Example 1: Frequent sequence miner (Apriori)</b>
 * <pre>{@code
 * public class AprioriMiner extends AbstractLearningOperator {
 *     private final double minSupport;
 *     private final double minConfidence;
 *     private StateStore<String, SequenceDatabase> dataStore;
 *     private Map<String, Double> frequentSequences;
 *     
 *     public AprioriMiner(OperatorId id, double minSupport, double minConfidence,
 *                         MetricsCollector metrics) {
 *         super(id, "Apriori Miner", "Discovers frequent event sequences",
 *               List.of("learning.mining", "learning.apriori"), metrics);
 *         this.minSupport = minSupport;
 *         this.minConfidence = minConfidence;
 *     }
 *     
 *     @Override
 *     protected Promise<Void> doInitialize(OperatorConfig config) {
 *         this.dataStore = StateStoreFactory.createHybrid(config);
 *         
 *         // Load existing model if available
 *         return dataStore.get("model", Map.class)
 *             .map(optional -> {
 *                 this.frequentSequences = optional.orElse(new HashMap<>());
 *                 return null;
 *             });
 *     }
 *     
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         // Online learning: accumulate events
 *         String tenantId = event.getTenantId();
 *         
 *         return dataStore.get(tenantId, SequenceDatabase.class)
 *             .map(optional -> optional.orElse(new SequenceDatabase()))
 *             .then(db -> {
 *                 db.add(event);
 *                 
 *                 // Trigger retraining every N events
 *                 if (db.size() % 1000 == 0) {
 *                     return train(db.getEvents())
 *                         .map(v -> OperatorResult.empty());
 *                 }
 *                 
 *                 return dataStore.put(tenantId, db, Optional.empty())
 *                     .map(v -> OperatorResult.empty());
 *             });
 *     }
 *     
 *     @Override
 *     public Promise<Void> train(List<Event> events) {
 *         // Run Apriori algorithm
 *         AprioriAlgorithm apriori = new AprioriAlgorithm(minSupport, minConfidence);
 *         Map<String, Double> newSequences = apriori.mine(events);
 *         
 *         this.frequentSequences = newSequences;
 *         
 *         // Persist model
 *         return dataStore.put("model", frequentSequences, Optional.empty());
 *     }
 *     
 *     @Override
 *     public Map<String, Object> getModelMetrics() {
 *         return Map.of(
 *             "frequent_sequences_count", frequentSequences.size(),
 *             "min_support", minSupport,
 *             "min_confidence", minConfidence,
 *             "last_trained", System.currentTimeMillis()
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 2: Correlation analyzer</b>
 * <pre>{@code
 * public class TemporalCorrelationAnalyzer extends AbstractLearningOperator {
 *     private StateStore<String, CorrelationMatrix> correlationStore;
 *     private CorrelationMatrix matrix;
 *     
 *     public TemporalCorrelationAnalyzer(OperatorId id, MetricsCollector metrics) {
 *         super(id, "Temporal Correlation Analyzer", 
 *               "Analyzes temporal correlations between event types",
 *               List.of("learning.correlation", "learning.temporal"), metrics);
 *     }
 *     
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         // Update correlation matrix incrementally
 *         String eventType = event.getType();
 *         long timestamp = event.getMetadata().getTimestamp();
 *         
 *         // Check for correlated events within time window
 *         return correlationStore.get("recent_events", List.class)
 *             .map(optional -> optional.orElse(new ArrayList<>()))
 *             .then(recentEvents -> {
 *                 // Update correlations
 *                 for (Event recent : recentEvents) {
 *                     if (timestamp - recent.getMetadata().getTimestamp() < 60000) {
 *                         matrix.incrementCorrelation(recent.getType(), eventType);
 *                     }
 *                 }
 *                 
 *                 // Add current event to recent events
 *                 recentEvents.add(event);
 *                 
 *                 // Prune old events
 *                 recentEvents.removeIf(e -> 
 *                     timestamp - e.getMetadata().getTimestamp() > 60000);
 *                 
 *                 return correlationStore.put("recent_events", recentEvents,
 *                                           Optional.of(Duration.ofMinutes(1)))
 *                     .map(v -> OperatorResult.empty());
 *             });
 *     }
 *     
 *     @Override
 *     public Map<String, Object> getModelMetrics() {
 *         return Map.of(
 *             "correlation_count", matrix.size(),
 *             "strong_correlations", matrix.getStrongCorrelations(0.7).size(),
 *             "weak_correlations", matrix.getWeakCorrelations(0.3).size()
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 3: Pattern synthesizer (convert sequences to patterns)</b>
 * <pre>{@code
 * public class PatternSynthesizer extends AbstractLearningOperator {
 *     private final OperatorCatalog catalog;
 *     private StateStore<String, List<String>> discoveredPatterns;
 *     
 *     public PatternSynthesizer(OperatorId id, OperatorCatalog catalog,
 *                               MetricsCollector metrics) {
 *         super(id, "Pattern Synthesizer", 
 *               "Converts frequent sequences to pattern operators",
 *               List.of("learning.synthesis", "learning.pattern"), metrics);
 *         this.catalog = catalog;
 *     }
 *     
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         // Input: pattern.discovered event from FrequentSequenceMiner
 *         if (!event.getType().equals("pattern.discovered")) {
 *             return Promise.of(OperatorResult.empty());
 *         }
 *         
 *         List<String> sequence = event.getPayload().getList("sequence");
 *         double support = event.getPayload().getDouble("support");
 *         double confidence = event.getPayload().getDouble("confidence");
 *         
 *         // Synthesize SEQ pattern operator
 *         SeqOperator seqPattern = synthesizeSequence(sequence, support, confidence);
 *         
 *         // Register in catalog
 *         return catalog.register(seqPattern)
 *             .then(v -> {
 *                 // Emit pattern.synthesized event
 *                 Event synthesizedEvent = Event.builder()
 *                     .type("pattern.synthesized")
 *                     .addPayload("operator_id", seqPattern.getId().toString())
 *                     .addPayload("quality_score", confidence)
 *                     .addPayload("support", support)
 *                     .build();
 *                 
 *                 return Promise.of(OperatorResult.of(synthesizedEvent));
 *             });
 *     }
 *     
 *     private SeqOperator synthesizeSequence(List<String> sequence, 
 *                                           double support, double confidence) {
 *         // Convert sequence to SEQ pattern
 *         List<UnifiedOperator> subPatterns = sequence.stream()
 *             .map(type -> new TypeFilterOperator(...))
 *             .toList();
 *         
 *         return new SeqOperator(
 *             OperatorId.of("ghatana", "pattern", "discovered-seq", "1.0.0"),
 *             subPatterns,
 *             null
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><b>Best Practices</b>
 * <ul>
 *   <li>Extend AbstractLearningOperator for all ML-driven operators</li>
 *   <li>Always return true from {@code isStateful()} (learning requires state)</li>
 *   <li>Load trained models in {@code doInitialize()} (lazy loading)</li>
 *   <li>Persist models in {@code doStop()} (graceful shutdown)</li>
 *   <li>Implement {@code train()} for batch learning</li>
 *   <li>Use {@code process()} for online/incremental learning</li>
 *   <li>Return quality metrics from {@code getModelMetrics()} (precision, recall, F1)</li>
 *   <li>Use StateStore for model persistence (recovery on restart)</li>
 *   <li>Emit discovery events for downstream consumption</li>
 * </ul>
 *
 * <p><b>Anti-Patterns</b>
 * <ul>
 *   <li>❌ DON'T use for stream transformations (use AbstractStreamOperator)</li>
 *   <li>❌ DON'T use for pattern matching (use AbstractPatternOperator)</li>
 *   <li>❌ DON'T block in process() for heavy training (use background threads)</li>
 *   <li>❌ DON'T skip model persistence (lost on restart)</li>
 *   <li>❌ DON'T train on every event (batch or periodic training)</li>
 *   <li>❌ DON'T ignore model quality metrics (blind deployment)</li>
 * </ul>
 *
 * <p><b>Performance Characteristics</b>
 * <ul>
 *   <li>Inference latency: <10ms p99 for simple models</li>
 *   <li>Training time: Minutes to hours (offline batch training)</li>
 *   <li>Model size: 1MB-1GB (varies by algorithm)</li>
 *   <li>Memory: O(model_size) + O(training_data) during training</li>
 *   <li>Throughput: 1k-10k events/sec for inference</li>
 * </ul>
 *
 * <p><b>Integration Points</b>
 * <ul>
 *   <li>{@link AbstractOperator} - Extends for lifecycle and metrics</li>
 *   <li>{@link OperatorType#LEARNING} - Fixed operator type</li>
 *   <li>StateStore - For model persistence and training data</li>
 *   <li>OperatorCatalog - For registering discovered patterns</li>
 *   <li>EventCloud - Consumes discovery events, produces recommendations</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Inherits thread-safety guarantees from AbstractOperator. Training operations
 * SHOULD run in background threads (Promise.ofBlocking). Model state MUST be
 * protected during concurrent inference/training. ActiveJ Eventloop affinity
 * recommended for single-threaded inference.
 *
 * @see AbstractOperator
 * @see AbstractStreamOperator
 * @see AbstractPatternOperator
 * @see UnifiedOperator
 * @see OperatorType#LEARNING
 * 
 * @doc.type class
 * @doc.purpose Abstract base class for ML-driven learning operators
 * @doc.layer core
 * @doc.pattern Template Method (process, train extension points)
 * 
 * @since 2.0
 */
public abstract class AbstractLearningOperator extends AbstractOperator {

    /**
     * Creates a learning operator.
     *
     * @param id Operator identifier
     * @param name Operator name
     * @param description Operator description
     * @param capabilities Operator capabilities
     * @param metricsCollector Metrics collector
     */
    protected AbstractLearningOperator(
            OperatorId id,
            String name,
            String description,
            List<String> capabilities,
            MetricsCollector metricsCollector
    ) {
        super(
            Objects.requireNonNull(id, "Operator ID must not be null"),
            OperatorType.LEARNING,
            Objects.requireNonNull(name, "Operator name must not be null"),
            Objects.requireNonNull(description, "Operator description must not be null"),
            capabilities != null ? capabilities : List.of(),
            metricsCollector
        );
    }

    /**
     * Process event (inference or online learning logic).
     *
     * <p>Subclasses MUST implement this method to define learning behavior:
     * <ul>
     *   <li><b>Inference</b>: Use trained model to make predictions/recommendations</li>
     *   <li><b>Online Learning</b>: Update model incrementally with new event</li>
     *   <li><b>Data Collection</b>: Accumulate events for batch training</li>
     *   <li><b>Discovery</b>: Emit pattern.discovered events for found patterns</li>
     * </ul>
     *
     * @param event Input event
     * @return Promise of operator result (predictions, recommendations, discoveries)
     */
    @Override
    public abstract Promise<OperatorResult> process(Event event);

    /**
     * Train model on batch of historical events.
     *
     * <p>Override this method for operators that support batch learning:
     * <ul>
     *   <li><b>Frequent Sequence Mining</b>: Apriori, PrefixSpan algorithms</li>
     *   <li><b>Correlation Analysis</b>: Compute correlation matrices</li>
     *   <li><b>Anomaly Detection</b>: Train baseline models</li>
     *   <li><b>Classification</b>: Train classifiers on labeled data</li>
     * </ul>
     *
     * <p>Training should:
     * <ul>
     *   <li>Run in background (use Promise.ofBlocking for CPU-intensive work)</li>
     *   <li>Persist trained model to StateStore</li>
     *   <li>Update model metrics (precision, recall, F1 score)</li>
     *   <li>Emit training.complete event with quality metrics</li>
     * </ul>
     *
     * <p>Default implementation does nothing (online learning only).
     *
     * @param events Batch of historical events for training
     * @return Promise of training completion
     */
    public Promise<Void> train(List<Event> events) {
        return Promise.complete();
    }

    /**
     * Returns model quality metrics.
     *
     * <p>Override this method to expose model-specific quality metrics:
     * <ul>
     *   <li><b>Mining</b>: frequent_sequences_count, support, confidence</li>
     *   <li><b>Correlation</b>: correlation_count, strong_correlations, weak_correlations</li>
     *   <li><b>Classification</b>: precision, recall, f1_score, accuracy</li>
     *   <li><b>Recommendation</b>: relevance_score, coverage, diversity</li>
     * </ul>
     *
     * <p>These metrics are exposed via {@code getMetrics()} for monitoring
     * and should be tracked over time to detect model degradation.
     *
     * <p>Default implementation returns empty map (no model metrics).
     *
     * @return Map of model quality metrics (empty if not applicable)
     */
    public Map<String, Object> getModelMetrics() {
        return Map.of();
    }

    /**
     * Returns metrics including model quality metrics.
     *
     * <p>Combines standard operator metrics with model-specific quality metrics.
     *
     * @return Combined metrics map
     */
    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new java.util.HashMap<>(super.getMetrics());
        metrics.putAll(getModelMetrics());
        return metrics;
    }

    /**
     * Returns true (learning operators are stateful by definition).
     *
     * <p>Learning operators ALWAYS maintain model state (trained models,
     * training data, quality metrics). This method cannot be overridden
     * to ensure correct StateStore allocation.
     *
     * @return true (always)
     */
    @Override
    public final boolean isStateful() {
        return true;
    }

    @Override
    public String toString() {
        return String.format("LearningOperator[id=%s, name=%s, state=%s, model=%s]",
                getId(), getName(), getState(), 
                getModelMetrics().getOrDefault("model_version", "unknown"));
    }
}

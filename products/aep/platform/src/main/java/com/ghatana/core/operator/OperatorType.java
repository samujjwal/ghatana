package com.ghatana.core.operator;

/**
 * Classification of operators in the Unified Operator Model.
 *
 * <p><b>Purpose</b><br>
 * Provides type-safe categorization of the three operator domains unified under
 * {@link UnifiedOperator}: Stream processing, Pattern detection (CEP), and Learning (ML).
 * Enables catalog queries, pipeline composition, and operator recommendations by type.
 *
 * <p><b>Architecture Role</b><br>
 * Part of the Unified Operator Model (Decision 1, WORLD_CLASS_DESIGN_MASTER.md Section III).
 * All operators MUST declare their type classification. This enables:
 * <ul>
 *   <li>Catalog discovery: {@code catalog.findByType(OperatorType.STREAM)}</li>
 *   <li>Pipeline validation: Ensure operator types are compatible</li>
 *   <li>Metrics scoping: Track operator performance by type</li>
 *   <li>Operator recommendations: Suggest operators based on pipeline context</li>
 *   <li>Execution optimization: Type-specific execution strategies</li>
 * </ul>
 *
 * <p><b>Operator Type Hierarchy</b>
 * <pre>
 * UnifiedOperator (interface)
 *   ├── StreamOperator (STREAM)
 *   │   ├── Filter, Map, FlatMap
 *   │   ├── Window, Join, Reduce
 *   │   └── Aggregate, GroupBy
 *   ├── PatternOperator (PATTERN)
 *   │   ├── SEQ, AND, OR, NOT
 *   │   ├── WITHIN, REPEAT, UNTIL
 *   │   └── FOLLOWED_BY, NOT_FOLLOWED_BY
 *   └── LearningOperator (LEARNING)
 *       ├── FrequentSequenceMiner (Apriori, PrefixSpan)
 *       ├── CorrelationAnalyzer
 *       ├── PatternSynthesizer
 *       └── Recommender (Collaborative Filtering)
 * </pre>
 *
 * <p><b>Usage Examples</b>
 *
 * <p><b>Example 1: Declare operator type</b>
 * <pre>{@code
 * public class FilterOperator extends AbstractOperator {
 *     public FilterOperator() {
 *         super(
 *             OperatorId.of("ghatana", "stream", "filter", "1.0.0"),
 *             OperatorType.STREAM,  // ← Declare type
 *             "Filter Events",
 *             "Filters events by predicate"
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 2: Catalog queries by type</b>
 * <pre>{@code
 * // Find all stream operators
 * List<UnifiedOperator> streamOps = catalog.findByType(OperatorType.STREAM);
 * 
 * // Find all pattern operators
 * List<UnifiedOperator> patternOps = catalog.findByType(OperatorType.PATTERN);
 * 
 * // Find all learning operators
 * List<UnifiedOperator> learningOps = catalog.findByType(OperatorType.LEARNING);
 * }</pre>
 *
 * <p><b>Example 3: Pipeline validation</b>
 * <pre>{@code
 * Pipeline pipeline = Pipeline.create("fraud-detection")
 *     .operator(filterOp)       // STREAM type
 *     .operator(sequenceOp)     // PATTERN type
 *     .operator(recommenderOp); // LEARNING type
 * 
 * // Validate type compatibility
 * for (UnifiedOperator op : pipeline.getOperators()) {
 *     OperatorType type = op.getType();
 *     switch (type) {
 *         case STREAM -> validateStreamOperator(op);
 *         case PATTERN -> validatePatternOperator(op);
 *         case LEARNING -> validateLearningOperator(op);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 4: Metrics scoping by type</b>
 * <pre>{@code
 * // Record operator execution metrics scoped by type
 * Timer timer = meterRegistry.timer("operator.process.duration",
 *     "operator_type", operator.getType().name(),
 *     "operator_name", operator.getName()
 * );
 * 
 * timer.record(() -> operator.process(event));
 * }</pre>
 *
 * <p><b>Example 5: Operator recommendations</b>
 * <pre>{@code
 * // Recommend next operator based on pipeline context
 * Pipeline partialPipeline = Pipeline.create("analysis")
 *     .operator(filterOp);  // STREAM type
 * 
 * // Recommender suggests PATTERN operators after STREAM
 * OperatorType lastType = partialPipeline.getLastOperator().getType();
 * if (lastType == OperatorType.STREAM) {
 *     // Suggest pattern detection operators
 *     List<UnifiedOperator> suggestions = catalog.findByType(OperatorType.PATTERN);
 * }
 * }</pre>
 *
 * <p><b>Example 6: Type-specific execution strategies</b>
 * <pre>{@code
 * // Choose execution strategy based on operator type
 * Promise<OperatorResult> execute(UnifiedOperator operator, Event event) {
 *     return switch (operator.getType()) {
 *         case STREAM -> executeStreamOperator(operator, event);
 *         case PATTERN -> executePatternOperator(operator, event);
 *         case LEARNING -> executeLearningOperator(operator, event);
 *     };
 * }
 * }</pre>
 *
 * <p><b>Type Descriptions</b>
 *
 * <p><b>STREAM Operators</b>
 * <ul>
 *   <li><b>Purpose</b>: Transform, filter, aggregate event streams</li>
 *   <li><b>Characteristics</b>: Stateless or windowed state, high throughput</li>
 *   <li><b>Examples</b>: Filter, Map, FlatMap, Window, Join, Reduce, Aggregate</li>
 *   <li><b>Input/Output</b>: 1:0, 1:1, or 1:N event transformation</li>
 *   <li><b>State</b>: Optional (window buffers, aggregation state)</li>
 *   <li><b>Latency</b>: <1ms p99 (stateless), <10ms p99 (windowed)</li>
 * </ul>
 *
 * <p><b>PATTERN Operators</b>
 * <ul>
 *   <li><b>Purpose</b>: Detect temporal patterns using Complex Event Processing (CEP)</li>
 *   <li><b>Characteristics</b>: Stateful NFA, sequence matching, temporal constraints</li>
 *   <li><b>Examples</b>: SEQ, AND, OR, NOT, WITHIN, REPEAT, UNTIL</li>
 *   <li><b>Input/Output</b>: M:N (buffer events until pattern matches)</li>
 *   <li><b>State</b>: Always (NFA states, partial match buffers)</li>
 *   <li><b>Latency</b>: <50ms p99 (simple patterns), <500ms (complex nested patterns)</li>
 * </ul>
 *
 * <p><b>LEARNING Operators</b>
 * <ul>
 *   <li><b>Purpose</b>: ML-driven pattern discovery and recommendation</li>
 *   <li><b>Characteristics</b>: Adaptive models, statistical analysis, batch processing</li>
 *   <li><b>Examples</b>: FrequentSequenceMiner, CorrelationAnalyzer, PatternSynthesizer, Recommender</li>
 *   <li><b>Input/Output</b>: M:N (analyze batches, emit insights)</li>
 *   <li><b>State</b>: Always (model parameters, training data)</li>
 *   <li><b>Latency</b>: Seconds to minutes (batch-oriented, not real-time)</li>
 * </ul>
 *
 * <p><b>Best Practices</b>
 * <ul>
 *   <li>Use STREAM for stateless transformations (highest throughput)</li>
 *   <li>Use PATTERN for temporal sequence detection (CEP use cases)</li>
 *   <li>Use LEARNING for adaptive pattern discovery (ML use cases)</li>
 *   <li>Chain STREAM → PATTERN → LEARNING in pipelines (filter → detect → learn)</li>
 *   <li>Scope metrics by operator type for performance tracking</li>
 *   <li>Validate type compatibility when composing pipelines</li>
 * </ul>
 *
 * <p><b>Anti-Patterns</b>
 * <ul>
 *   <li>❌ DON'T use PATTERN operators for simple filtering (use STREAM instead)</li>
 *   <li>❌ DON'T use LEARNING operators for real-time critical paths (high latency)</li>
 *   <li>❌ DON'T mix operator types without validation (incompatible I/O)</li>
 *   <li>❌ DON'T create custom types outside this enum (breaks catalog queries)</li>
 * </ul>
 *
 * <p><b>Performance Characteristics</b>
 * <ul>
 *   <li>Enum comparison: O(1) identity check</li>
 *   <li>name() call: O(1) cached string</li>
 *   <li>Memory: ~50 bytes per enum constant (shared JVM-wide)</li>
 * </ul>
 *
 * <p><b>Integration Points</b>
 * <ul>
 *   <li>{@link UnifiedOperator#getType()} - Every operator declares its type</li>
 *   <li>{@link OperatorCatalog#findByType(OperatorType)} - Catalog type queries</li>
 *   <li>{@link OperatorId} - Type encoded in ID string (namespace:type:name:version)</li>
 *   <li>PipelineBuilder - Type-based validation and recommendations</li>
 *   <li>Metrics - operator.process.duration tagged with operator_type</li>
 * </ul>
 *
 * <p><b>Design Decisions</b>
 * <ul>
 *   <li>Three types chosen to align with WORLD_CLASS_DESIGN_MASTER.md Section III</li>
 *   <li>Enum (not interface) for type safety and exhaustive switch compatibility</li>
 *   <li>Uppercase constants follow Java enum convention</li>
 *   <li>No custom types allowed (closed set ensures catalog query correctness)</li>
 * </ul>
 *
 * @see UnifiedOperator
 * @see OperatorId
 * @see OperatorCatalog
 * 
 * @doc.type enum
 * @doc.purpose Classification of operators in Unified Operator Model (Stream, Pattern, Learning)
 * @doc.layer core
 * @doc.pattern Enumeration
 * 
 * @author Ghatana Platform Team
 * @version 2.0.0
 * @since 2025-10-25
 */
public enum OperatorType {
    
    /**
     * Stream processing operators (transformations, filters, aggregations).
     * 
     * <p>Examples: Filter, Map, FlatMap, Window, Join, Reduce
     */
    STREAM,

    /**
     * Pattern detection operators (Complex Event Processing).
     * 
     * <p>Examples: SEQ, AND, OR, NOT, WITHIN, REPEAT, UNTIL
     */
    PATTERN,

    /**
     * Learning operators (machine learning, pattern discovery).
     * 
     * <p>Examples: FrequentSequenceMiner, CorrelationAnalyzer, PatternSynthesizer, Recommender
     */
    LEARNING
}

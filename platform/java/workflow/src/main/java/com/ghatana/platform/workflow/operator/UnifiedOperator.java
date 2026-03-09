package com.ghatana.platform.workflow.operator;

import com.ghatana.platform.types.identity.OperatorId;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.List;
import java.util.Map;

/**
 * Unified interface for all operators in the World-Class Multi-Agent Event Processing System.
 *
 * <p><b>Purpose</b><br>
 * Provides a single abstraction unifying Stream, Pattern, and Learning operators to enable
 * operator composition, cataloging, versioning, deployment, and operator-as-agent serialization
 * to EventCloud. This is the foundation of the Unified Operator Model architecture.
 *
 * <p><b>Architecture Role</b><br>
 * <b>THIS IS THE CORE ABSTRACTION</b> implementing Decision 1 from WORLD_CLASS_DESIGN_MASTER.md
 * Section III "Unified Operator Model". ALL operators (Stream, Pattern, Learning) MUST extend
 * this interface to participate in the platform. UnifiedOperator enables:
 *
 * <ul>
 *   <li><b>Operator Composition</b>: Chain operators into declarative pipelines</li>
 *   <li><b>Operator Cataloging</b>: Discover, query, version, and recommend operators</li>
 *   <li><b>Versioning</b>: Git-like version control with rollback support</li>
 *   <li><b>Deployment</b>: Dynamic operator deployment to running pipelines</li>
 *   <li><b>Operator-as-Agent</b>: Serialize operators to EventCloud events</li>
 *   <li><b>Lifecycle Management</b>: Initialize, start, stop, health checks</li>
 *   <li><b>Observability</b>: Metrics, state inspection, debugging</li>
 * </ul>
 *
 * <p><b>Operator Type Hierarchy</b>
 * <pre>
 * UnifiedOperator (this interface)
 *   │
 *   ├── StreamOperator (base class for stream processing)
 *   │   ├── Filter            (predicate filtering)
 *   │   ├── Map               (1:1 transformation)
 *   │   ├── FlatMap           (1:N transformation)
 *   │   ├── Window            (time/count windowing)
 *   │   ├── Join              (stream joining)
 *   │   └── Reduce/Aggregate  (aggregation)
 *   │
 *   ├── PatternOperator (base class for CEP)
 *   │   ├── SEQ               (sequence detection)
 *   │   ├── AND/OR/NOT        (boolean composition)
 *   │   ├── WITHIN            (temporal constraints)
 *   │   ├── REPEAT            (repetition detection)
 *   │   └── UNTIL             (termination conditions)
 *   │
 *   └── LearningOperator (base class for ML)
 *       ├── FrequentSequenceMiner    (Apriori, PrefixSpan)
 *       ├── CorrelationAnalyzer      (temporal correlation)
 *       ├── PatternSynthesizer       (pattern generation)
 *       └── Recommender              (collaborative filtering)
 * </pre>
 *
 * <p><b>Execution Model</b>
 * <ul>
 *   <li><b>Asynchronous</b>: All processing uses ActiveJ Promise (non-blocking)</li>
 *   <li><b>Input Cardinality</b>: 1 event in (single), N events in (batch)</li>
 *   <li><b>Output Cardinality</b>: 0 (filter drop), 1 (transform), N (fan-out)</li>
 *   <li><b>State</b>: Stateless (no state) or Stateful (local/hybrid state stores)</li>
 *   <li><b>Backpressure</b>: Automatic via Promise composition</li>
 * </ul>
 *
 * <p><b>Usage Examples</b>
 *
 * <p><b>Example 1: Implement stream filter operator</b>
 * <pre>{@code
 * public class FilterOperator extends AbstractOperator {
 *     private final Predicate<Event> predicate;
 *     
 *     public FilterOperator(OperatorId id, Predicate<Event> predicate) {
 *         super(id, OperatorType.STREAM, "Filter", "Filters events by predicate",
 *               List.of("event.filter"), meterRegistry);
 *         this.predicate = predicate;
 *     }
 *     
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         if (predicate.test(event)) {
 *             // Pass through
 *             return Promise.of(OperatorResult.of(event));
 *         } else {
 *             // Drop
 *             return Promise.of(OperatorResult.empty());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 2: Implement pattern sequence detector (CEP)</b>
 * <pre>{@code
 * public class SequenceOperator extends AbstractOperator {
 *     private final List<String> sequence = List.of("login.failed", "transaction");
 *     private final StateStore<String, List<Event>> nfaState;
 *     
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         String userId = event.getPayload().getString("userId");
 *         List<Event> partialMatch = nfaState.get(userId).orElse(new ArrayList<>());
 *         
 *         // Check if event matches next expected type in sequence
 *         if (event.getType().equals(sequence.get(partialMatch.size()))) {
 *             partialMatch.add(event);
 *             
 *             if (partialMatch.size() == sequence.size()) {
 *                 // Complete match - emit pattern.matched event
 *                 Event matchEvent = Event.builder()
 *                     .type("pattern.matched")
 *                     .addPayload("pattern", "fraud-sequence")
 *                     .addPayload("events", partialMatch)
 *                     .build();
 *                 
 *                 nfaState.remove(userId); // Reset NFA state
 *                 return Promise.of(OperatorResult.of(matchEvent));
 *             } else {
 *                 // Partial match - update state
 *                 nfaState.put(userId, partialMatch);
 *                 return Promise.of(OperatorResult.empty());
 *             }
 *         } else {
 *             // No match - reset state
 *             nfaState.remove(userId);
 *             return Promise.of(OperatorResult.empty());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 3: Implement learning operator (Apriori)</b>
 * <pre>{@code
 * public class FrequentSequenceMiner extends AbstractOperator {
 *     private final StateStore<String, Long> sequenceCounts;
 *     private final double minSupport = 0.1;
 *     
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         // Extract sequence from event
 *         List<String> sequence = event.getPayload().getList("sequence");
 *         String sequenceKey = String.join("→", sequence);
 *         
 *         // Increment sequence count
 *         long count = sequenceCounts.get(sequenceKey).orElse(0L) + 1;
 *         sequenceCounts.put(sequenceKey, count);
 *         
 *         // Calculate support (count / total events)
 *         long totalEvents = getTotalEventCount();
 *         double support = (double) count / totalEvents;
 *         
 *         if (support >= minSupport) {
 *             // Emit pattern.discovered event
 *             Event discoveredPattern = Event.builder()
 *                 .type("pattern.discovered")
 *                 .addPayload("sequence", sequence)
 *                 .addPayload("support", support)
 *                 .addPayload("count", count)
 *                 .build();
 *             
 *             return Promise.of(OperatorResult.of(discoveredPattern));
 *         } else {
 *             return Promise.of(OperatorResult.empty());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 4: Lifecycle management</b>
 * <pre>{@code
 * // Create operator
 * UnifiedOperator operator = new FilterOperator(...);
 * assert operator.getState() == OperatorState.CREATED;
 * 
 * // Initialize with configuration
 * OperatorConfig config = OperatorConfig.builder()
 *     .withProperty("batchSize", "100")
 *     .withTimeout(Duration.ofSeconds(5))
 *     .build();
 * 
 * operator.initialize(config).getResult();
 * assert operator.getState() == OperatorState.INITIALIZED;
 * 
 * // Start operator
 * operator.start().getResult();
 * assert operator.getState() == OperatorState.RUNNING;
 * assert operator.isHealthy();
 * 
 * // Process events
 * OperatorResult result = operator.process(event).getResult();
 * 
 * // Graceful shutdown
 * operator.stop().getResult();
 * assert operator.getState() == OperatorState.STOPPED;
 * }</pre>
 *
 * <p><b>Example 5: Operator cataloging and discovery</b>
 * <pre>{@code
 * // Register operator in catalog
 * OperatorCatalog catalog = new EventCloudOperatorCatalog(...);
 * catalog.register(filterOperator);
 * 
 * // Discover by type
 * List<UnifiedOperator> streamOps = catalog.findByType(OperatorType.STREAM);
 * 
 * // Discover by capability
 * List<UnifiedOperator> filterOps = catalog.findByCapability("event.filter");
 * 
 * // Discover by event type (what can process login events)
 * List<UnifiedOperator> loginOps = catalog.findByEventType("login.failed");
 * 
 * // Get operator by ID
 * OperatorId id = OperatorId.parse("ghatana:stream:filter:1.0.0");
 * Optional<UnifiedOperator> op = catalog.get(id);
 * }</pre>
 *
 * <p><b>Example 6: Operator-as-agent serialization to EventCloud</b>
 * <pre>{@code
 * // Serialize operator to event
 * Event operatorEvent = operator.toEvent();
 * assert operatorEvent.getType().equals("operator.registered");
 * assert operatorEvent.getMetadata().get("operatorId")
 *     .equals("ghatana:stream:filter:1.0.0");
 * 
 * // Store in EventCloud
 * eventCloud.append("operator-registry", operatorEvent);
 * 
 * // Deserialize from EventCloud
 * EventCloud.EventStream events = eventCloud.scan("operator-registry", 0);
 * events.forEach(event -> {
 *     if (event.getType().equals("operator.registered")) {
 *         UnifiedOperator restored = UnifiedOperator.fromEvent(event);
 *         catalog.register(restored);
 *     }
 * });
 * }</pre>
 *
 * <p><b>Example 7: Declarative pipeline composition</b>
 * <pre>{@code
 * // Build pipeline using UnifiedOperator abstraction
 * Pipeline pipeline = Pipeline.create("fraud-detection")
 *     .operator(filterOperator)       // STREAM: Filter login events
 *     .operator(sequenceOperator)     // PATTERN: Detect failed login → transaction
 *     .operator(enrichmentOperator)   // STREAM: Enrich with user profile
 *     .operator(minerOperator)        // LEARNING: Learn frequent sequences
 *     .onError((event, error) -> deadLetterQueue.send(event, error))
 *     .build();
 * 
 * // Initialize all operators
 * pipeline.initialize(config).getResult();
 * 
 * // Start pipeline
 * pipeline.start().getResult();
 * 
 * // Process event through pipeline
 * OperatorResult result = pipeline.process(event).getResult();
 * }</pre>
 *
 * <p><b>Best Practices</b>
 * <ul>
 *   <li>Extend {@link AbstractOperator} for common lifecycle/metrics implementation</li>
 *   <li>Use ActiveJ Promise for ALL async operations (no blocking calls)</li>
 *   <li>Return {@code OperatorResult.empty()} for filters (not null)</li>
 *   <li>Implement {@code isStateful()} correctly (affects deployment strategy)</li>
 *   <li>Use {@link OperatorConfig} for configuration (not constructor params)</li>
 *   <li>Track metrics in {@code getMetrics()} (operator.process.count/duration/errors)</li>
 *   <li>Implement {@code toEvent()} for operator catalog persistence</li>
 *   <li>Declare capabilities precisely for accurate discovery</li>
 *   <li>Use hybrid state stores for stateful operators (local + centralized)</li>
 *   <li>Handle errors gracefully (return {@code OperatorResult.failed()} not throw)</li>
 * </ul>
 *
 * <p><b>Anti-Patterns</b>
 * <ul>
 *   <li>❌ DON'T block in process() (use Promise.ofBlocking for IO)</li>
 *   <li>❌ DON'T throw exceptions from process() (return failed result)</li>
 *   <li>❌ DON'T return null from process() (return OperatorResult.empty())</li>
 *   <li>❌ DON'T mutate input events (create new events)</li>
 *   <li>❌ DON'T skip lifecycle methods (always call initialize → start → stop)</li>
 *   <li>❌ DON'T hardcode configuration (use OperatorConfig)</li>
 *   <li>❌ DON'T create operators without OperatorId (catalog won't work)</li>
 *   <li>❌ DON'T mix concurrency models (ActiveJ Promise only, no Reactor/RxJava)</li>
 * </ul>
 *
 * <p><b>Performance Characteristics</b>
 * <ul>
 *   <li><b>Latency</b>:
 *     <ul>
 *       <li>Stream operators: <1ms p99 (stateless), <10ms p99 (windowed)</li>
 *       <li>Pattern operators: <50ms p99 (simple), <500ms (complex nested)</li>
 *       <li>Learning operators: Seconds to minutes (batch-oriented)</li>
 *     </ul>
 *   </li>
 *   <li><b>Throughput</b>:
 *     <ul>
 *       <li>Stream operators: 100k+ events/sec per operator</li>
 *       <li>Pattern operators: 10k-50k events/sec (depends on NFA complexity)</li>
 *       <li>Learning operators: Batch processing (not real-time throughput)</li>
 *     </ul>
 *   </li>
 *   <li><b>Memory</b>:
 *     <ul>
 *       <li>Stateless operators: ~10-50KB per instance</li>
 *       <li>Stateful operators: Depends on state size (configure limits)</li>
 *       <li>Pattern operators: O(k*m) where k=patterns, m=partial matches</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><b>Integration Points</b>
 * <ul>
 *   <li>{@link OperatorCatalog} - Operator registry for discovery/versioning</li>
 *   <li>{@link OperatorId} - Unique operator identification</li>
 *   <li>{@link OperatorType} - Operator classification (STREAM/PATTERN/LEARNING)</li>
 *   <li>{@link OperatorState} - Lifecycle state management</li>
 *   <li>{@link OperatorResult} - Processing outcome representation</li>
 *   <li>{@link OperatorConfig} - Operator configuration</li>
 *   <li>{@link OperatorException} - Error handling hierarchy</li>
 *   <li>{@link AbstractOperator} - Base class implementation</li>
 *   <li>PipelineBuilder - Declarative pipeline composition</li>
 *   <li>EventCloud - Operator-as-agent serialization</li>
 *   <li>StateStore - Hybrid state management</li>
 *   <li>Metrics - Micrometer/OpenTelemetry integration</li>
 * </ul>
 *
 * <p><b>Design Decisions</b>
 * <ul>
 *   <li>ActiveJ Promise: Platform standard for async operations (no Reactor/RxJava)</li>
 *   <li>OperatorResult: Explicit result type (vs Optional/Either) for clarity</li>
 *   <li>Lifecycle methods: Explicit initialize/start/stop for resource management</li>
 *   <li>Operator-as-agent: toEvent/fromEvent enables EventCloud persistence</li>
 *   <li>Interface (not abstract class): Allows custom base classes if needed</li>
 *   <li>Capabilities: String-based (not enum) for extensibility</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Operators MUST be thread-safe for concurrent event processing. Use:
 * <ul>
 *   <li>ActiveJ Eventloop affinity (single-threaded per loop)</li>
 *   <li>Thread-safe state stores (StateStore implementations handle concurrency)</li>
 *   <li>Immutable configuration (OperatorConfig is immutable)</li>
 *   <li>Atomic state transitions (AbstractOperator synchronizes state changes)</li>
 * </ul>
 *
 * @see AbstractOperator
 * @see OperatorCatalog
 * @see OperatorId
 * @see OperatorType
 * @see OperatorState
 * @see OperatorResult
 * @see OperatorConfig
 * @see OperatorException
 * 
 * @doc.type interface
 * @doc.purpose Unified interface for Stream, Pattern, and Learning operators in Event Processing System
 * @doc.layer core
 * @doc.pattern Strategy (operator as pluggable algorithm)
 * 
 * @author Ghatana Platform Team
 * @version 2.0.0
 * @since 2025-10-25
 */
public interface UnifiedOperator {

    // ============================================================================
    // IDENTITY METHODS
    // ============================================================================

    /**
     * Get unique operator identifier (immutable).
     * 
     * <p>Format: {@code {namespace}:{type}:{name}:{version}}
     * <p>Example: {@code ghatana:stream:filter:1.0.0}
     * 
     * @return operator ID
     */
    OperatorId getId();

    /**
     * Get human-readable operator name.
     * 
     * @return operator name (e.g., "Filter Events by Type")
     */
    String getName();

    /**
     * Get operator type classification.
     * 
     * @return operator type (STREAM, PATTERN, or LEARNING)
     */
    OperatorType getType();

    /**
     * Get operator version (semantic versioning).
     * 
     * @return version string (e.g., "1.2.3")
     */
    String getVersion();

    /**
     * Get operator description (for catalog/UI).
     * 
     * @return description
     */
    String getDescription();

    /**
     * Get operator capabilities (for discovery).
     * 
     * <p>Example capabilities:
     * <ul>
     *   <li>{@code event.filter} - Can filter events</li>
     *   <li>{@code event.transform} - Can transform events</li>
     *   <li>{@code pattern.seq} - Can detect sequences</li>
     *   <li>{@code learning.discovery} - Can discover patterns</li>
     * </ul>
     * 
     * @return set of capability strings
     */
    List<String> getCapabilities();

    // ============================================================================
    // EXECUTION METHODS
    // ============================================================================

    /**
     * Process a single event (ActiveJ Promise-based).
     * 
     * <p>All operators MUST return a Promise to enable non-blocking execution.
     * 
     * <p><b>Execution Model:</b>
     * <ul>
     *   <li><b>Stream operators</b>: Transform/filter/aggregate input event</li>
     *   <li><b>Pattern operators</b>: Update NFA state, emit match events</li>
     *   <li><b>Learning operators</b>: Update model state, emit insights</li>
     * </ul>
     * 
     * @param event input event
     * @return Promise of processing result (may contain 0, 1, or multiple output events)
     */
    Promise<OperatorResult> process(Event event);

    /**
     * Process a batch of events for efficiency.
     * 
     * <p>Default implementation processes events sequentially. Operators SHOULD
     * override this for batch optimizations (e.g., vectorized operations, batched state updates).
     * 
     * @param events input events
     * @return Promise of batch processing result
     */
    default Promise<OperatorResult> processBatch(List<Event> events) {
        // Default sequential processing using Promises.toList
        List<Promise<OperatorResult>> promises = events.stream()
            .map(this::process)
            .toList();
        
        return Promises.toList(promises).map(results -> {
            OperatorResult.Builder builder = OperatorResult.builder().success();
            results.forEach(builder::mergeWith);
            return builder.build();
        });
    }

    // ============================================================================
    // LIFECYCLE METHODS
    // ============================================================================

    /**
     * Initialize operator (called once before start).
     * 
     * <p>Use this to:
     * <ul>
     *   <li>Validate configuration</li>
     *   <li>Allocate resources (state stores, connections)</li>
     *   <li>Load pre-trained models (for learning operators)</li>
     * </ul>
     * 
     * @param config operator configuration
     * @return Promise of initialization completion
     * @throws OperatorException if initialization fails
     */
    Promise<Void> initialize(OperatorConfig config);

    /**
     * Start operator execution.
     * 
     * <p>Called after initialization. Operator transitions to RUNNING state.
     * 
     * @return Promise of start completion
     * @throws OperatorException if start fails
     */
    Promise<Void> start();

    /**
     * Stop operator gracefully.
     * 
     * <p>Operator should:
     * <ul>
     *   <li>Finish processing in-flight events</li>
     *   <li>Flush buffered state</li>
     *   <li>Release resources</li>
     * </ul>
     * 
     * @return Promise of stop completion
     */
    Promise<Void> stop();

    /**
     * Check operator health status.
     * 
     * <p>Used for:
     * <ul>
     *   <li>Load balancing decisions</li>
     *   <li>Circuit breaker triggers</li>
     *   <li>Monitoring alerts</li>
     * </ul>
     * 
     * @return true if operator is healthy and ready to process events
     */
    boolean isHealthy();

    /**
     * Get current operator lifecycle state.
     * 
     * @return lifecycle state (CREATED, INITIALIZED, RUNNING, STOPPED, FAILED)
     */
    OperatorState getState();

    // ============================================================================
    // OPERATOR-AS-AGENT EVENT REPRESENTATION
    // ============================================================================

    /**
     * Serialize operator to Event for EventCloud storage.
     * 
     * <p>Event structure:
     * <pre>
     * {
     *   "type": "operator.registered",
     *   "metadata": {
     *     "operatorId": "ghatana:stream:filter:1.0.0",
     *     "tenantId": "tenant123"
     *   },
     *   "payload": {
     *     "type": "STREAM",
     *     "name": "Filter Events by Type",
     *     "version": "1.0.0",
     *     "config": {...},
     *     "capabilities": ["event.filter"]
     *   }
     * }
     * </pre>
     * 
     * @return Event representation
     */
    Event toEvent();

    /**
     * Deserialize operator from Event.
     * 
     * <p>Inverse of {@link #toEvent()}. Used for:
     * <ul>
     *   <li>Loading operators from catalog</li>
     *   <li>Recreating pipelines from EventCloud</li>
     *   <li>Version migration</li>
     * </ul>
     * 
     * @param event Event representation
     * @return UnifiedOperator instance
     * @throws OperatorException if deserialization fails
     */
    static UnifiedOperator fromEvent(Event event) {
        throw new UnsupportedOperationException(
            "Operator deserialization must be implemented by concrete operator types"
        );
    }

    // ============================================================================
    // OBSERVABILITY METHODS
    // ============================================================================

    /**
     * Get operator metrics for monitoring.
     * 
     * <p>Standard metrics:
     * <ul>
     *   <li>{@code operator.process.count} - Total events processed</li>
     *   <li>{@code operator.process.duration} - Processing time histogram</li>
     *   <li>{@code operator.process.errors} - Error count by type</li>
     *   <li>{@code operator.state.size} - State store size (bytes)</li>
     * </ul>
     * 
     * @return metrics map
     */
    Map<String, Object> getMetrics();

    /**
     * Get operator internal state for debugging.
     * 
     * <p>DO NOT expose sensitive data (passwords, keys).
     * 
     * @return state snapshot (for admin/debug only)
     */
    Map<String, Object> getInternalState();

    /**
     * Get operator configuration.
     * 
     * @return current configuration
     */
    OperatorConfig getConfig();

    // ============================================================================
    // METADATA
    // ============================================================================

    /**
     * Get operator metadata (tags, labels, owner).
     * 
     * <p>Used for:
     * <ul>
     *   <li>Discovery and filtering</li>
     *   <li>Organizational grouping</li>
     *   <li>RBAC policies</li>
     * </ul>
     * 
     * @return metadata map
     */
    Map<String, String> getMetadata();

    /**
     * Check if operator requires state (stateful vs stateless).
     * 
     * <p>Stateful operators:
     * <ul>
     *   <li>WindowOperator (aggregation state)</li>
     *   <li>PatternOperator (NFA state)</li>
     *   <li>LearningOperator (model state)</li>
     * </ul>
     * 
     * @return true if operator maintains state
     */
    default boolean isStateful() {
        return false; // Default: stateless
    }

    /**
     * Get operator dependencies (other operators this operator uses).
     * 
     * <p>Used for:
     * <ul>
     *   <li>Dependency resolution</li>
     *   <li>Catalog validation</li>
     *   <li>Pipeline optimization</li>
     * </ul>
     * 
     * @return list of operator IDs this operator depends on
     */
    default List<OperatorId> getDependencies() {
        return List.of(); // Default: no dependencies
    }
}

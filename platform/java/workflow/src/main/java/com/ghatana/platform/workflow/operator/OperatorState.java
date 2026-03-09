package com.ghatana.platform.workflow.operator;

import com.ghatana.platform.types.identity.OperatorId;

/**
 * Lifecycle state of operators in the Unified Operator Model.
 *
 * <p><b>Purpose</b><br>
 * Tracks operator lifecycle progression from creation through initialization, execution,
 * and shutdown. Enforces valid state transitions to prevent invalid operations
 * (e.g., processing events before initialization, stopping a failed operator).
 *
 * <p><b>Architecture Role</b><br>
 * Part of the operator lifecycle management in {@link UnifiedOperator}. Every operator
 * maintains exactly one state at any time. State transitions are validated by
 * {@link AbstractOperator} to ensure correct lifecycle progression. States enable:
 * <ul>
 *   <li>Health checks: Determine if operator is healthy and ready to process events</li>
 *   <li>Graceful shutdown: Stop operators safely without losing in-flight events</li>
 *   <li>Error recovery: Detect failures and trigger recovery/restart logic</li>
 *   <li>Observability: Track operator state transitions in metrics and logs</li>
 *   <li>Pipeline management: Coordinate multi-operator pipeline startup/shutdown</li>
 * </ul>
 *
 * <p><b>State Transition Diagram</b>
 * <pre>
 * CREATED → INITIALIZED → RUNNING → STOPPED
 *     ↓           ↓           ↓
 *   FAILED      FAILED      FAILED
 *
 * Valid Transitions:
 *   CREATED      → INITIALIZED  (via initialize())
 *   CREATED      → FAILED       (via initialize() failure)
 *   INITIALIZED  → RUNNING      (via start())
 *   INITIALIZED  → FAILED       (via start() failure)
 *   RUNNING      → STOPPED      (via stop())
 *   RUNNING      → FAILED       (via process() failure)
 *   STOPPED      → RUNNING      (via start() - restart)
 *   FAILED       → terminal     (cannot recover without restart)
 * </pre>
 *
 * <p><b>Usage Examples</b>
 *
 * <p><b>Example 1: Normal lifecycle progression</b>
 * <pre>{@code
 * UnifiedOperator operator = new FilterOperator(...);
 * assert operator.getState() == OperatorState.CREATED;
 * 
 * // Initialize operator
 * operator.initialize(config).getResult();
 * assert operator.getState() == OperatorState.INITIALIZED;
 * 
 * // Start processing
 * operator.start().getResult();
 * assert operator.getState() == OperatorState.RUNNING;
 * 
 * // Process events
 * OperatorResult result = operator.process(event).getResult();
 * 
 * // Graceful shutdown
 * operator.stop().getResult();
 * assert operator.getState() == OperatorState.STOPPED;
 * }</pre>
 *
 * <p><b>Example 2: Initialization failure</b>
 * <pre>{@code
 * UnifiedOperator operator = new PatternOperator(...);
 * assert operator.getState() == OperatorState.CREATED;
 * 
 * // Invalid configuration causes initialization failure
 * try {
 *     operator.initialize(invalidConfig).getResult();
 * } catch (Exception e) {
 *     // State transitions to FAILED
 *     assert operator.getState() == OperatorState.FAILED;
 *     // Cannot process events in FAILED state
 *     assert !operator.isHealthy();
 * }
 * }</pre>
 *
 * <p><b>Example 3: Runtime failure and recovery</b>
 * <pre>{@code
 * UnifiedOperator operator = new LearningOperator(...);
 * operator.initialize(config).getResult();
 * operator.start().getResult();
 * assert operator.getState() == OperatorState.RUNNING;
 * 
 * // Processing failure transitions to FAILED state
 * try {
 *     operator.process(malformedEvent).getResult();
 * } catch (Exception e) {
 *     assert operator.getState() == OperatorState.FAILED;
 * }
 * 
 * // Recovery requires restart
 * UnifiedOperator recoveredOperator = new LearningOperator(...);
 * recoveredOperator.initialize(config).getResult();
 * recoveredOperator.start().getResult();
 * assert recoveredOperator.getState() == OperatorState.RUNNING;
 * }</pre>
 *
 * <p><b>Example 4: Health checks in pipeline</b>
 * <pre>{@code
 * Pipeline pipeline = Pipeline.create("monitoring")
 *     .addOperator(operator1)
 *     .addOperator(operator2)
 *     .addOperator(operator3);
 * 
 * // Check all operators are healthy (RUNNING state)
 * boolean allHealthy = pipeline.getOperators().stream()
 *     .allMatch(op -> op.getState() == OperatorState.RUNNING && op.isHealthy());
 * 
 * if (!allHealthy) {
 *     // Find failed operators
 *     List<UnifiedOperator> failedOps = pipeline.getOperators().stream()
 *         .filter(op -> op.getState() == OperatorState.FAILED)
 *         .toList();
 *     // Trigger recovery
 * }
 * }</pre>
 *
 * <p><b>Example 5: Restart operator after stop</b>
 * <pre>{@code
 * UnifiedOperator operator = new StreamOperator(...);
 * operator.initialize(config).getResult();
 * operator.start().getResult();
 * assert operator.getState() == OperatorState.RUNNING;
 * 
 * // Stop operator
 * operator.stop().getResult();
 * assert operator.getState() == OperatorState.STOPPED;
 * 
 * // Restart operator (STOPPED → RUNNING transition allowed)
 * operator.start().getResult();
 * assert operator.getState() == OperatorState.RUNNING;
 * }</pre>
 *
 * <p><b>Example 6: State-based metrics</b>
 * <pre>{@code
 * // Track operator state transitions in metrics
 * meterRegistry.gauge("operator.state",
 *     Tags.of("operator_id", operator.getId().toString()),
 *     operator,
 *     op -> op.getState().ordinal()  // CREATED=0, INITIALIZED=1, etc.
 * );
 * 
 * // Count operators by state
 * meterRegistry.gauge("operator.count.running",
 *     catalog.getAll().stream()
 *         .filter(op -> op.getState() == OperatorState.RUNNING)
 *         .count()
 * );
 * }</pre>
 *
 * <p><b>State Descriptions</b>
 *
 * <p><b>CREATED</b>
 * <ul>
 *   <li>Initial state immediately after operator construction</li>
 *   <li>Resources not allocated, configuration not validated</li>
 *   <li>MUST call {@code initialize()} before any other operations</li>
 *   <li>Cannot process events in this state</li>
 * </ul>
 *
 * <p><b>INITIALIZED</b>
 * <ul>
 *   <li>Configuration validated, resources allocated (state stores, connections)</li>
 *   <li>Operator ready to start but not actively processing</li>
 *   <li>Call {@code start()} to transition to RUNNING</li>
 *   <li>Cannot process events in this state</li>
 * </ul>
 *
 * <p><b>RUNNING</b>
 * <ul>
 *   <li>Operator actively processing events</li>
 *   <li>Resources allocated and healthy</li>
 *   <li>Call {@code stop()} for graceful shutdown</li>
 *   <li>Can process events in this state</li>
 * </ul>
 *
 * <p><b>STOPPED</b>
 * <ul>
 *   <li>Operator gracefully stopped (resources released)</li>
 *   <li>Can restart by calling {@code start()} again</li>
 *   <li>In-flight events completed, no data loss</li>
 *   <li>Cannot process events in this state</li>
 * </ul>
 *
 * <p><b>FAILED</b>
 * <ul>
 *   <li>Terminal state indicating unrecoverable error</li>
 *   <li>Initialization error, runtime error, or resource exhaustion</li>
 *   <li>Cannot process events or transition to other states</li>
 *   <li>Requires operator restart to recover</li>
 * </ul>
 *
 * <p><b>Best Practices</b>
 * <ul>
 *   <li>Always check state before calling lifecycle methods</li>
 *   <li>Use {@code isHealthy()} for health checks (state == RUNNING)</li>
 *   <li>Handle state transition failures gracefully</li>
 *   <li>Track state transitions in metrics and logs</li>
 *   <li>Stop operators gracefully during shutdown (call stop())</li>
 *   <li>Replace failed operators instead of attempting inline recovery</li>
 * </ul>
 *
 * <p><b>Anti-Patterns</b>
 * <ul>
 *   <li>❌ DON'T call process() before start() (IllegalStateException)</li>
 *   <li>❌ DON'T call start() before initialize() (IllegalStateException)</li>
 *   <li>❌ DON'T ignore FAILED state (data loss, undefined behavior)</li>
 *   <li>❌ DON'T manually set state (use lifecycle methods)</li>
 *   <li>❌ DON'T skip graceful shutdown (call stop() before exit)</li>
 * </ul>
 *
 * <p><b>Performance Characteristics</b>
 * <ul>
 *   <li>State check: O(1) enum comparison</li>
 *   <li>State transition: O(1) field update (synchronized)</li>
 *   <li>Memory: ~50 bytes per enum constant (shared JVM-wide)</li>
 * </ul>
 *
 * <p><b>Integration Points</b>
 * <ul>
 *   <li>{@link UnifiedOperator#getState()} - Query current operator state</li>
 *   <li>{@link UnifiedOperator#isHealthy()} - Health check (state == RUNNING)</li>
 *   <li>{@link AbstractOperator} - Enforces state transition validation</li>
 *   <li>Metrics - operator.state gauge, operator.transitions counter</li>
 *   <li>Observability - State transitions logged with traceId</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Enum constants are thread-safe (immutable singletons). State transitions in
 * {@link AbstractOperator} are synchronized to prevent concurrent state changes.
 *
 * @see UnifiedOperator
 * @see AbstractOperator
 * @see OperatorId
 * 
 * @doc.type enum
 * @doc.purpose Lifecycle state of operators in Unified Operator Model
 * @doc.layer core
 * @doc.pattern State Machine
 * 
 * @author Ghatana Platform Team
 * @version 2.0.0
 * @since 2025-10-25
 */
public enum OperatorState {
    
    /**
     * Operator instance created, not yet initialized.
     */
    CREATED,

    /**
     * Operator initialized (resources allocated, config validated).
     */
    INITIALIZED,

    /**
     * Operator running (actively processing events).
     */
    RUNNING,

    /**
     * Operator stopped gracefully.
     */
    STOPPED,

    /**
     * Operator failed (initialization error, runtime error, resource exhaustion).
     */
    FAILED
}

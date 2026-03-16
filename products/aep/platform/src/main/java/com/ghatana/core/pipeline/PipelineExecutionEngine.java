package com.ghatana.core.pipeline;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.core.operator.OperatorState;
import com.ghatana.core.operator.UnifiedOperator;
import com.ghatana.core.operator.catalog.OperatorCatalog;
import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Production-grade DAG-based pipeline execution engine.
 *
 * <p><b>Purpose</b><br>
 * Executes a {@link Pipeline}'s directed acyclic graph of operator stages with real
 * operator invocation, edge-based event routing, error/fallback handling, deadline
 * enforcement, and per-stage observability.
 *
 * <p><b>Architecture Role</b><br>
 * The central runtime component that replaces the previously-simulated
 * {@code DefaultPipeline.execute()} with production-grade operator invocation.
 * Resolves each stage's {@link OperatorId} via {@link OperatorCatalog}, invokes
 * {@link UnifiedOperator#process(Event)}, and routes {@link OperatorResult#getOutputEvents()}
 * through the pipeline's edge graph.
 *
 * <p><b>Execution Model</b>
 * <ol>
 *   <li>Validate pipeline structure (stages, edges, DAG-acyclicity)</li>
 *   <li>Topologically sort stages to determine execution order</li>
 *   <li>Identify source stages (no incoming primary edges) — they receive the input event</li>
 *   <li>For each stage in topological order:
 *     <ul>
 *       <li>Resolve operator via catalog</li>
 *       <li>Collect input events from upstream stages' outputs</li>
 *       <li>Execute {@code operator.process(event)} for each input</li>
 *       <li>Route outputs to downstream stages based on edge labels:
 *         <ul>
 *           <li><b>primary</b>: success outputs flow to downstream</li>
 *           <li><b>error</b>: on failure, route to error-handling stage</li>
 *           <li><b>fallback</b>: when stage produces no output, route to fallback</li>
 *           <li><b>broadcast</b>: clone output to all broadcast targets</li>
 *         </ul>
 *       </li>
 *     </ul>
 *   </li>
 *   <li>Collect terminal stage outputs (stages with no outgoing primary edges)</li>
 *   <li>Build {@link PipelineExecutionResult} with per-stage timing and diagnostics</li>
 * </ol>
 *
 * <p><b>Error Handling</b>
 * <ul>
 *   <li><b>Operator not found</b>: stage fails with descriptive error; routes to error edge if present</li>
 *   <li><b>Operator failure</b>: routes to error edge if present; accumulates error if continue-on-error</li>
 *   <li><b>Deadline exceeded</b>: remaining stages skipped, partial result returned</li>
 *   <li><b>No input events</b>: stage skipped (no-op), fallback edge activated if present</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Stateless — all execution state is scoped to the {@link PipelineExecutionContext}.
 * Safe to reuse across concurrent pipeline executions.
 *
 * <p><b>Usage</b>
 * <pre>{@code
 * OperatorCatalog catalog = new DefaultOperatorCatalog();
 * catalog.register(filterOperator);
 * catalog.register(enrichOperator);
 *
 * PipelineExecutionEngine engine = new PipelineExecutionEngine();
 *
 * PipelineExecutionContext ctx = PipelineExecutionContext.builder()
 *     .pipelineId(pipeline.getId())
 *     .tenantId("acme")
 *     .operatorCatalog(catalog)
 *     .deadline(Duration.ofSeconds(30))
 *     .build();
 *
 * PipelineExecutionResult result = engine.execute(pipeline, inputEvent, ctx).getResult();
 * }</pre>
 *
 * @see Pipeline
 * @see PipelineExecutionContext
 * @see PipelineExecutionResult
 * @see StageExecutionResult
 * @see OperatorCatalog
 *
 * @doc.type class
 * @doc.purpose Production-grade DAG pipeline execution engine
 * @doc.layer core
 * @doc.pattern Engine, Strategy
 */
public class PipelineExecutionEngine {

    private static final Logger logger = LoggerFactory.getLogger(PipelineExecutionEngine.class);

    /**
     * Executes a pipeline's DAG against an input event.
     *
     * @param pipeline   the pipeline definition (stages + edges)
     * @param inputEvent the event that enters the pipeline
     * @param context    execution context with catalog, deadline, tenant, etc.
     * @return promise of the execution result containing all output events and per-stage diagnostics
     */
    public Promise<PipelineExecutionResult> execute(Pipeline pipeline, Event inputEvent,
                                                     PipelineExecutionContext context) {
        Objects.requireNonNull(pipeline, "pipeline cannot be null");
        Objects.requireNonNull(inputEvent, "inputEvent cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        long startTimeMs = System.currentTimeMillis();
        String pipelineId = pipeline.getId();

        logger.info("Starting pipeline execution: pipeline={}, execution={}, tenant={}",
                pipelineId, context.getExecutionId(), context.getTenantId());

        // Step 1: Validate pipeline structure
        PipelineValidationResult validation = pipeline.validate();
        if (!validation.isValid()) {
            String errorMsg = "Pipeline validation failed: " + validation.errors();
            logger.error(errorMsg);
            return Promise.of(PipelineExecutionResult.failure(
                    pipelineId, inputEvent, 0, errorMsg));
        }

        // Step 2: Build execution graph
        ExecutionGraph graph = buildExecutionGraph(pipeline);

        // Step 3: Execute stages in topological order
        return executeStages(pipeline, inputEvent, context, graph, startTimeMs);
    }

    // ════════════════════════════════════════════════════════════════
    // Graph construction
    // ════════════════════════════════════════════════════════════════

    /**
     * Builds the execution graph: adjacency lists segmented by edge label,
     * in-degree map, and source/terminal stage identification.
     */
    private ExecutionGraph buildExecutionGraph(Pipeline pipeline) {
        List<PipelineStage> stages = pipeline.getStages();
        List<PipelineEdge> edges = pipeline.getEdges();

        // Stage lookup
        Map<String, PipelineStage> stageMap = new LinkedHashMap<>();
        for (PipelineStage stage : stages) {
            stageMap.put(stage.stageId(), stage);
        }

        // Adjacency lists by edge label
        Map<String, List<EdgeTarget>> primaryAdj = new LinkedHashMap<>();
        Map<String, List<EdgeTarget>> errorAdj = new LinkedHashMap<>();
        Map<String, List<EdgeTarget>> fallbackAdj = new LinkedHashMap<>();
        Map<String, List<EdgeTarget>> broadcastAdj = new LinkedHashMap<>();

        // Incoming edge tracker (for source identification — any edge type disqualifies)
        Set<String> hasIncomingEdge = new HashSet<>();

        for (PipelineStage s : stages) {
            primaryAdj.put(s.stageId(), new ArrayList<>());
            errorAdj.put(s.stageId(), new ArrayList<>());
            fallbackAdj.put(s.stageId(), new ArrayList<>());
            broadcastAdj.put(s.stageId(), new ArrayList<>());
        }

        for (PipelineEdge edge : edges) {
            EdgeTarget target = new EdgeTarget(edge.to(), edge.label());
            hasIncomingEdge.add(edge.to()); // Any edge disqualifies from being a source
            switch (edge.label()) {
                case PipelineEdge.LABEL_PRIMARY -> primaryAdj.get(edge.from()).add(target);
                case PipelineEdge.LABEL_ERROR -> errorAdj.get(edge.from()).add(target);
                case PipelineEdge.LABEL_FALLBACK -> fallbackAdj.get(edge.from()).add(target);
                case PipelineEdge.LABEL_BROADCAST -> broadcastAdj.get(edge.from()).add(target);
                default -> primaryAdj.get(edge.from()).add(target);
            }
        }

        // Source stages: no incoming edges of any kind
        List<String> sourceStages = new ArrayList<>();
        for (PipelineStage stage : stages) {
            if (!hasIncomingEdge.contains(stage.stageId())) {
                sourceStages.add(stage.stageId());
            }
        }

        // Terminal stages: no outgoing primary/broadcast edges
        List<String> terminalStages = new ArrayList<>();
        for (PipelineStage stage : stages) {
            String sid = stage.stageId();
            if (primaryAdj.get(sid).isEmpty() && broadcastAdj.get(sid).isEmpty()) {
                terminalStages.add(sid);
            }
        }

        // Topological sort via Kahn's algorithm (primary + broadcast edges only)
        List<String> topoOrder = topologicalSort(stages, edges);

        return new ExecutionGraph(stageMap, primaryAdj, errorAdj, fallbackAdj, broadcastAdj,
                sourceStages, terminalStages, topoOrder);
    }

    /**
     * Kahn's algorithm for topological sort. Uses primary and broadcast edges
     * (which define the data-flow DAG). Error and fallback edges are excluded
     * since they are conditional and don't create data-flow dependencies.
     */
    private List<String> topologicalSort(List<PipelineStage> stages, List<PipelineEdge> edges) {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> adj = new LinkedHashMap<>();

        for (PipelineStage s : stages) {
            inDegree.put(s.stageId(), 0);
            adj.put(s.stageId(), new ArrayList<>());
        }

        for (PipelineEdge e : edges) {
            String label = e.label();
            // Only primary and broadcast edges form the data-flow DAG
            if (PipelineEdge.LABEL_PRIMARY.equals(label) || PipelineEdge.LABEL_BROADCAST.equals(label)) {
                adj.get(e.from()).add(e.to());
                inDegree.merge(e.to(), 1, Integer::sum);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            result.add(curr);
            for (String neighbor : adj.getOrDefault(curr, List.of())) {
                int newDeg = inDegree.merge(neighbor, -1, Integer::sum);
                if (newDeg == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (result.size() != stages.size()) {
            throw new IllegalStateException("Pipeline contains a cycle — topological sort failed");
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════════
    // Stage execution
    // ════════════════════════════════════════════════════════════════

    /**
     * Executes stages sequentially in topological order. Each stage resolves its
     * operator, processes input events, and routes outputs to downstream stages.
     */
    private Promise<PipelineExecutionResult> executeStages(
            Pipeline pipeline, Event inputEvent, PipelineExecutionContext context,
            ExecutionGraph graph, long startTimeMs) {

        // Per-stage input accumulator: stageId → list of events to process
        Map<String, List<Event>> stageInputs = new ConcurrentHashMap<>();

        // Seed source stages with the pipeline input event
        for (String sourceStageId : graph.sourceStages) {
            stageInputs.computeIfAbsent(sourceStageId, k -> new ArrayList<>()).add(inputEvent);
        }

        // Per-stage results
        List<StageExecutionResult> stageResults = new ArrayList<>();

        // Execute in topological order (sequential — Promise chain)
        return executeStageSequence(graph.topoOrder, 0, graph, context, stageInputs, stageResults)
                .map(unused -> buildFinalResult(pipeline.getId(), inputEvent, graph, stageInputs,
                        stageResults, startTimeMs));
    }

    /**
     * Recursively executes stages in topological order via Promise chaining.
     */
    private Promise<Void> executeStageSequence(
            List<String> topoOrder, int index, ExecutionGraph graph,
            PipelineExecutionContext context, Map<String, List<Event>> stageInputs,
            List<StageExecutionResult> stageResults) {

        if (index >= topoOrder.size()) {
            return Promise.complete();
        }

        // Check deadline
        if (context.isDeadlineExceeded()) {
            logger.warn("Deadline exceeded at stage {}/{}, skipping remaining stages",
                    index, topoOrder.size());
            return Promise.complete();
        }

        String stageId = topoOrder.get(index);
        PipelineStage stage = graph.stageMap.get(stageId);

        if (stage == null) {
            logger.warn("Stage {} not found in pipeline, skipping", stageId);
            return executeStageSequence(topoOrder, index + 1, graph, context, stageInputs, stageResults);
        }

        // Get input events for this stage
        List<Event> inputs = stageInputs.getOrDefault(stageId, List.of());

        if (inputs.isEmpty()) {
            // No input events — activate fallback edge if present
            logger.debug("Stage {} has no input events, checking fallback edges", stageId);
            activateFallbackEdges(stageId, graph, stageInputs, context);
            return executeStageSequence(topoOrder, index + 1, graph, context, stageInputs, stageResults);
        }

        // Execute the stage
        return executeSingleStage(stageId, stage, inputs, graph, context)
                .then(result -> {
                    stageResults.add(result);

                    if (result.success()) {
                        // Route outputs to downstream stages
                        routeOutputs(stageId, result, graph, stageInputs);
                    } else {
                        // Route to error edges
                        routeError(stageId, inputs, graph, stageInputs);

                        if (!context.isContinueOnError()) {
                            logger.error("Stage {} failed and continueOnError=false, aborting pipeline: {}",
                                    stageId, result.errorMessage());
                            return Promise.complete(); // Stop execution
                        }
                        logger.warn("Stage {} failed but continueOnError=true, continuing: {}",
                                stageId, result.errorMessage());
                    }

                    // Continue to next stage
                    return executeStageSequence(topoOrder, index + 1, graph, context,
                            stageInputs, stageResults);
                });
    }

    /**
     * Executes a single stage: resolves operator, processes all input events,
     * merges results.
     */
    private Promise<StageExecutionResult> executeSingleStage(
            String stageId, PipelineStage stage, List<Event> inputs,
            ExecutionGraph graph, PipelineExecutionContext context) {

        OperatorId operatorId = stage.operatorId();
        OperatorCatalog catalog = context.getOperatorCatalog();
        Instant stageStart = Instant.now();

        logger.debug("Executing stage '{}' with operator {} ({} input event(s))",
                stageId, operatorId, inputs.size());

        return catalog.get(operatorId)
                .then(optionalOp -> {
                    if (optionalOp.isEmpty()) {
                        Duration dur = Duration.between(stageStart, Instant.now());
                        String msg = String.format("Operator not found in catalog: %s (stage: %s)", operatorId, stageId);
                        logger.error(msg);
                        return Promise.of(StageExecutionResult.failure(stageId, operatorId, inputs, dur, msg));
                    }

                    UnifiedOperator operator = optionalOp.get();

                    // Validate operator is in a processable state
                    OperatorState opState = operator.getState();
                    if (opState != OperatorState.RUNNING && opState != OperatorState.INITIALIZED) {
                        Duration dur = Duration.between(stageStart, Instant.now());
                        String msg = String.format("Operator %s is in non-processable state: %s (stage: %s)",
                                operatorId, opState, stageId);
                        logger.error(msg);
                        return Promise.of(StageExecutionResult.failure(stageId, operatorId, inputs, dur, msg));
                    }

                    // Process each input event and merge results
                    return processInputEvents(operator, inputs)
                            .map(mergedResult -> {
                                Duration dur = Duration.between(stageStart, Instant.now());
                                if (mergedResult.isSuccess()) {
                                    logger.debug("Stage '{}' succeeded: {} output event(s) in {}ms",
                                            stageId, mergedResult.getOutputEvents().size(), dur.toMillis());
                                    return StageExecutionResult.success(stageId, operatorId, inputs,
                                            mergedResult, dur);
                                } else {
                                    logger.warn("Stage '{}' operator returned failure: {}",
                                            stageId, mergedResult.getErrorMessage());
                                    return StageExecutionResult.failure(stageId, operatorId, inputs,
                                            dur, mergedResult.getErrorMessage());
                                }
                            })
                            .mapException(ex -> {
                                logger.error("Stage '{}' threw exception: {}", stageId, ex.getMessage(), ex);
                                return new RuntimeException(
                                        String.format("Stage '%s' operator exception: %s", stageId, ex.getMessage()), ex);
                            });
                })
                .mapException(ex -> {
                    // Catalog lookup failure
                    logger.error("Failed to resolve operator {} for stage '{}': {}",
                            operatorId, stageId, ex.getMessage(), ex);
                    return new RuntimeException(
                            String.format("Operator catalog error for stage '%s': %s", stageId, ex.getMessage()), ex);
                })
                // Convert exceptions to StageExecutionResult.failure
                .then(
                        result -> Promise.of(result),
                        ex -> {
                            Duration dur = Duration.between(stageStart, Instant.now());
                            return Promise.of(StageExecutionResult.failure(
                                    stageId, operatorId, inputs, dur, ex.getMessage()));
                        }
                );
    }

    /**
     * Processes all input events through an operator, merging results.
     * For single-event input (common path), directly invokes {@code process()}.
     * For multiple events, processes sequentially and merges via {@link OperatorResult.Builder#mergeWith}.
     */
    private Promise<OperatorResult> processInputEvents(UnifiedOperator operator, List<Event> inputs) {
        // Guard against synchronous throws from operators — they must be converted to
        // a failed Promise so the engine's mapException/then-error-handler can catch them.
        try {
            if (inputs.size() == 1) {
                return operator.process(inputs.get(0));
            }

            // Multiple inputs: process sequentially, merge results via Builder
            return processSequential(operator, inputs, 0,
                    OperatorResult.builder().success());
        } catch (Exception e) {
            logger.warn("Operator '{}' threw synchronously during process(): {}",
                    operator.getId(), e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    /**
     * Processes events sequentially through an operator, accumulating merged results.
     */
    private Promise<OperatorResult> processSequential(UnifiedOperator operator, List<Event> inputs,
                                                       int index, OperatorResult.Builder accumulator) {
        if (index >= inputs.size()) {
            return Promise.of(accumulator.build());
        }

        return operator.process(inputs.get(index))
                .then(result -> {
                    accumulator.mergeWith(result);
                    return processSequential(operator, inputs, index + 1, accumulator);
                });
    }

    // ════════════════════════════════════════════════════════════════
    // Output routing
    // ════════════════════════════════════════════════════════════════

    /**
     * Routes successful stage outputs to downstream stages via primary and broadcast edges.
     */
    private void routeOutputs(String stageId, StageExecutionResult result,
                               ExecutionGraph graph, Map<String, List<Event>> stageInputs) {
        List<Event> outputs = result.getOutputEvents();

        if (outputs.isEmpty()) {
            // No output — activate fallback edges
            logger.debug("Stage '{}' produced no output, activating fallback edges", stageId);
            activateFallbackEdges(stageId, graph, stageInputs, null);
            return;
        }

        // Route to primary downstream stages
        for (EdgeTarget target : graph.primaryAdj.getOrDefault(stageId, List.of())) {
            stageInputs.computeIfAbsent(target.stageId, k -> new ArrayList<>()).addAll(outputs);
            logger.trace("Routed {} event(s) from '{}' → '{}' (primary)",
                    outputs.size(), stageId, target.stageId);
        }

        // Route to broadcast downstream stages (same outputs sent to all)
        for (EdgeTarget target : graph.broadcastAdj.getOrDefault(stageId, List.of())) {
            stageInputs.computeIfAbsent(target.stageId, k -> new ArrayList<>()).addAll(outputs);
            logger.trace("Routed {} event(s) from '{}' → '{}' (broadcast)",
                    outputs.size(), stageId, target.stageId);
        }
    }

    /**
     * Routes the original input events to error-handling stages when a stage fails.
     */
    private void routeError(String stageId, List<Event> originalInputs,
                             ExecutionGraph graph, Map<String, List<Event>> stageInputs) {
        List<EdgeTarget> errorTargets = graph.errorAdj.getOrDefault(stageId, List.of());
        if (errorTargets.isEmpty()) {
            logger.debug("Stage '{}' failed but has no error edges", stageId);
            return;
        }

        for (EdgeTarget target : errorTargets) {
            stageInputs.computeIfAbsent(target.stageId, k -> new ArrayList<>()).addAll(originalInputs);
            logger.debug("Routed {} event(s) from '{}' → '{}' (error)",
                    originalInputs.size(), stageId, target.stageId);
        }
    }

    /**
     * Activates fallback edges for a stage that produced no output or had no input.
     * Creates a synthetic "fallback" event carrying the stage ID so the fallback
     * handler knows which stage triggered it.
     */
    private void activateFallbackEdges(String stageId, ExecutionGraph graph,
                                        Map<String, List<Event>> stageInputs,
                                        PipelineExecutionContext context) {
        List<EdgeTarget> fallbackTargets = graph.fallbackAdj.getOrDefault(stageId, List.of());
        if (fallbackTargets.isEmpty()) {
            return;
        }

        // Create a synthetic fallback event
        Event fallbackEvent = Event.builder()
                .type("pipeline.fallback")
                .payload(Map.of(
                        "_fallback_source_stage", stageId,
                        "_fallback_reason", "no_output"
                ))
                .headers(Map.of(
                        "stage", stageId,
                        "fallback", "true"
                ))
                .build();

        for (EdgeTarget target : fallbackTargets) {
            stageInputs.computeIfAbsent(target.stageId, k -> new ArrayList<>()).add(fallbackEvent);
            logger.debug("Activated fallback from '{}' → '{}'", stageId, target.stageId);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Result building
    // ════════════════════════════════════════════════════════════════

    /**
     * Builds the final {@link PipelineExecutionResult} from all stage results.
     * Output events are collected from terminal stages (those with no outgoing primary/broadcast edges).
     */
    private PipelineExecutionResult buildFinalResult(
            String pipelineId, Event inputEvent, ExecutionGraph graph,
            Map<String, List<Event>> stageInputs, List<StageExecutionResult> stageResults,
            long startTimeMs) {

        long processingTimeMs = System.currentTimeMillis() - startTimeMs;

        // Collect output events from terminal stages
        List<Event> outputEvents = new ArrayList<>();
        for (String terminalId : graph.terminalStages) {
            // Terminal stage output = the stage's execution result outputs
            stageResults.stream()
                    .filter(r -> r.stageId().equals(terminalId) && r.success())
                    .findFirst()
                    .ifPresent(r -> outputEvents.addAll(r.getOutputEvents()));
        }

        // Check for any failures
        List<StageExecutionResult> failures = stageResults.stream()
                .filter(r -> !r.success())
                .toList();

        int stagesExecuted = stageResults.size();

        if (failures.isEmpty()) {
            logger.info("Pipeline '{}' completed successfully: {} stages, {} output events, {}ms",
                    pipelineId, stagesExecuted, outputEvents.size(), processingTimeMs);
            return PipelineExecutionResult.success(
                    pipelineId, inputEvent, outputEvents, processingTimeMs, stagesExecuted);
        } else {
            String errorMsg = failures.stream()
                    .map(f -> String.format("Stage '%s': %s", f.stageId(), f.errorMessage()))
                    .collect(Collectors.joining("; "));

            logger.warn("Pipeline '{}' completed with {} failure(s): {}",
                    pipelineId, failures.size(), errorMsg);

            // If we have outputs from successful terminal stages, still return them
            if (!outputEvents.isEmpty()) {
                // Partial success: some stages failed but terminal stages produced output
                return PipelineExecutionResult.success(
                        pipelineId, inputEvent, outputEvents, processingTimeMs, stagesExecuted);
            }

            return PipelineExecutionResult.failure(
                    pipelineId, inputEvent, processingTimeMs, errorMsg);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Internal data structures
    // ════════════════════════════════════════════════════════════════

    /**
     * Edge target: a stage ID and the label of the edge leading to it.
     */
    private record EdgeTarget(String stageId, String label) {}

    /**
     * Pre-computed execution graph derived from a Pipeline definition.
     */
    private record ExecutionGraph(
            Map<String, PipelineStage> stageMap,
            Map<String, List<EdgeTarget>> primaryAdj,
            Map<String, List<EdgeTarget>> errorAdj,
            Map<String, List<EdgeTarget>> fallbackAdj,
            Map<String, List<EdgeTarget>> broadcastAdj,
            List<String> sourceStages,
            List<String> terminalStages,
            List<String> topoOrder
    ) {}
}

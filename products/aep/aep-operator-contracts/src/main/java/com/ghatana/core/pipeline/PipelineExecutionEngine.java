package com.ghatana.core.pipeline;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.platform.domain.event.Event;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * OperatorCatalog catalog = new UnifiedOperatorCatalog();
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

    private final PipelineExecutionGraphBuilder graphBuilder;
    private final PipelineStageExecutor stageExecutor;
    private final PipelineEventRouter eventRouter;
    private AepPipelineMetrics pipelineMetrics = AepPipelineMetrics.noop();

    public PipelineExecutionEngine() {
        this(new PipelineExecutionGraphBuilder(), new PipelineStageExecutor(), new PipelineEventRouter());
    }

    PipelineExecutionEngine(
            PipelineExecutionGraphBuilder graphBuilder,
            PipelineStageExecutor stageExecutor,
            PipelineEventRouter eventRouter
    ) {
        this.graphBuilder = Objects.requireNonNull(graphBuilder, "graphBuilder cannot be null");
        this.stageExecutor = Objects.requireNonNull(stageExecutor, "stageExecutor cannot be null");
        this.eventRouter = Objects.requireNonNull(eventRouter, "eventRouter cannot be null");
    }

    /**
     * Attaches a metrics facade for recording pipeline execution counters and timers.
     *
     * @param metrics the metrics facade; must not be {@code null}
     * @return {@code this} for chaining
     */
    public PipelineExecutionEngine withMetrics(AepPipelineMetrics metrics) {
        this.pipelineMetrics = Objects.requireNonNull(metrics, "metrics cannot be null");
        return this;
    }

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
        String tenantId = context.getTenantId();

        logger.info("Starting pipeline execution: pipeline={}, execution={}, tenant={}",
                pipelineId, context.getExecutionId(), tenantId);
        pipelineMetrics.recordStarted(pipelineId, tenantId);

        // Step 1: Validate pipeline structure
        PipelineValidationResult validation = pipeline.validate();
        if (!validation.isValid()) {
            String errorMsg = "Pipeline validation failed: " + validation.errors();
            logger.error(errorMsg);
            pipelineMetrics.recordFailed(pipelineId, tenantId,
                    System.currentTimeMillis() - startTimeMs);
            return Promise.of(PipelineExecutionResult.failure(
                    pipelineId, inputEvent, 0, errorMsg));
        }

        // Step 2: Build execution graph
        PipelineExecutionGraph graph = graphBuilder.build(pipeline);

        // Step 3: Execute stages in topological order
        return executeStages(pipeline, inputEvent, context, graph, startTimeMs, tenantId);
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
            PipelineExecutionGraph graph, long startTimeMs, String tenantId) {

        // Per-stage input accumulator: stageId → list of events to process
        Map<String, List<Event>> stageInputs = new java.util.concurrent.ConcurrentHashMap<>();

        // Seed source stages with the pipeline input event
        for (String sourceStageId : graph.sourceStages()) {
            stageInputs.computeIfAbsent(sourceStageId, k -> new ArrayList<>()).add(inputEvent);
        }

        // Per-stage results
        List<StageExecutionResult> stageResults = new ArrayList<>();

        // Execute in topological order (sequential — Promise chain)
        return executeStageSequence(graph.topoOrder(), 0, graph, context, stageInputs, stageResults)
                .map(unused -> buildFinalResult(pipeline.getId(), inputEvent, graph, stageInputs,
                        stageResults, startTimeMs, tenantId));
    }

    /**
     * Recursively executes stages in topological order via Promise chaining.
     */
    private Promise<Void> executeStageSequence(
            List<String> topoOrder, int index, PipelineExecutionGraph graph,
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
        PipelineStage stage = graph.stageMap().get(stageId);

        if (stage == null) {
            logger.warn("Stage {} not found in pipeline, skipping", stageId);
            return executeStageSequence(topoOrder, index + 1, graph, context, stageInputs, stageResults);
        }

        // Get input events for this stage
        List<Event> inputs = stageInputs.getOrDefault(stageId, List.of());

        if (inputs.isEmpty()) {
            // No input events — activate fallback edge if present
            logger.debug("Stage {} has no input events, checking fallback edges", stageId);
            eventRouter.activateFallbackEdges(stageId, graph, stageInputs);
            return executeStageSequence(topoOrder, index + 1, graph, context, stageInputs, stageResults);
        }

        // Execute the stage
        return stageExecutor.executeSingleStage(stageId, stage, inputs, context)
                .then(result -> {
                    stageResults.add(result);

                    if (result.success()) {
                        // Route outputs to downstream stages
                        eventRouter.routeOutputs(stageId, result, graph, stageInputs);
                    } else {
                        // Route to error edges
                        eventRouter.routeError(stageId, inputs, graph, stageInputs);

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

    // ════════════════════════════════════════════════════════════════
    // Result building
    // ════════════════════════════════════════════════════════════════

    /**
     * Builds the final {@link PipelineExecutionResult} from all stage results.
     * Output events are collected from terminal stages (those with no outgoing primary/broadcast edges).
     */
    private PipelineExecutionResult buildFinalResult(
            String pipelineId, Event inputEvent, PipelineExecutionGraph graph,
            Map<String, List<Event>> stageInputs, List<StageExecutionResult> stageResults,
            long startTimeMs, String tenantId) {

        long processingTimeMs = System.currentTimeMillis() - startTimeMs;

        // Collect output events from terminal stages
        List<Event> outputEvents = new ArrayList<>();
        for (String terminalId : graph.terminalStages()) {
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
            pipelineMetrics.recordSucceeded(pipelineId, tenantId, processingTimeMs, stagesExecuted);
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
                pipelineMetrics.recordSucceeded(pipelineId, tenantId, processingTimeMs, stagesExecuted);
                return PipelineExecutionResult.success(
                        pipelineId, inputEvent, outputEvents, processingTimeMs, stagesExecuted);
            }

            pipelineMetrics.recordFailed(pipelineId, tenantId, processingTimeMs);
            return PipelineExecutionResult.failure(
                    pipelineId, inputEvent, processingTimeMs, errorMsg);
        }
    }

}

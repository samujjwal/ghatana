package com.ghatana.virtualorg.framework.integration;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.catalog.OperatorCatalog;
import com.ghatana.core.pipeline.Pipeline;
import com.ghatana.core.pipeline.PipelineBuilder;
import com.ghatana.virtualorg.framework.workflow.WorkflowDefinition;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Adapter converting organizational workflows to stream processing pipelines.
 *
 * <p><b>Purpose</b><br>
 * Transforms imperative workflow definitions (sequence of steps with agents)
 * into declarative stream pipelines (chained operators). Enables workflows
 * to leverage stream processing capabilities: backpressure, windowing,
 * partitioning, fault tolerance.
 *
 * <p><b>Architecture Role</b><br>
 * Integration adapter implementing Phase 2 Track 2 (Workflow as Stream Pipeline)
 * from PHASE_BY_PHASE_IMPLEMENTATION_GUIDE.md. Bridges workflow-engine with
 * pipeline-builder and operator-catalog.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Define workflow
 * WorkflowDefinition sprintPlanning = WorkflowDefinition.builder()
 *     .name("Sprint Planning")
 *     .triggerEvent("sprint.started")
 *     .addStep(WorkflowStep.of("Load Backlog", productManagerAgent))
 *     .addStep(WorkflowStep.of("Assign Tasks", ctoAgent))
 *     .addStep(WorkflowStep.of("Review Capacity", architectAgent))
 *     .build();
 *
 * // Convert to stream pipeline
 * WorkflowPipelineAdapter adapter = new WorkflowPipelineAdapter(operatorCatalog);
 * Pipeline pipeline = adapter.toPipeline(sprintPlanning, "tenant123")
 *     .getResult();  // Blocking for demo; use .whenResult() in production
 *
 * // Pipeline is now executable as unified operator graph
 * pipelineExecutor.execute(pipeline);
 * }</pre>
 *
 * <p><b>Conversion Rules</b><br>
 * <ul>
 *   <li>Workflow trigger event → Pipeline source (EventCloudSource)</li>
 *   <li>Workflow steps → Pipeline stages (map operators)</li>
 *   <li>Agent assignments → Operator lookups in catalog</li>
 *   <li>Sequential steps → Linear operator chain</li>
 *   <li>Parallel steps → Fan-out/fan-in (future enhancement)</li>
 *   <li>Error handlers → Dead-letter queue sinks</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Stateless and thread-safe. All operations are idempotent.
 *
 * <p><b>Performance Characteristics</b><br>
 * Conversion is O(n) where n = number of workflow steps.
 * Target: <1ms for typical workflows (5-10 steps).
 *
 * @see WorkflowDefinition
 * @see Pipeline
 * @see PipelineBuilder
 * @doc.type class
 * @doc.purpose Convert workflows to stream pipelines
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class WorkflowPipelineAdapter {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowPipelineAdapter.class);

    private static final String PIPELINE_NAMESPACE = "virtualorg.workflow";
    private static final String PIPELINE_VERSION = "1.0.0";

    private final OperatorCatalog operatorCatalog;

    /**
     * Creates workflow-to-pipeline adapter.
     *
     * @param operatorCatalog operator catalog for resolving agent operators (never null)
     * @throws IllegalArgumentException if operatorCatalog is null
     */
    public WorkflowPipelineAdapter(OperatorCatalog operatorCatalog) {
        this.operatorCatalog = Objects.requireNonNull(operatorCatalog, "OperatorCatalog required");
    }

    /**
     * Converts workflow definition to executable stream pipeline.
     *
     * <p>Performs asynchronous conversion:
     * <ol>
     *   <li>Create PipelineId from workflow name</li>
     *   <li>Resolve agent operators from catalog</li>
     *   <li>Chain operators matching workflow step sequence</li>
     *   <li>Configure error handling and dead-letter queue</li>
     *   <li>Return compiled pipeline ready for execution</li>
     * </ol>
     *
     * <p><b>Pipeline Structure</b><br>
     * <pre>
     * EventCloudSource(triggerEvent)
     *   → Filter(event type match)
     *   → Map(Agent1 operator)
     *   → Map(Agent2 operator)
     *   → ...
     *   → EventCloudSink(completion event)
     *   ↘ DeadLetterQueue (on error)
     * </pre>
     *
     * @param workflow workflow definition to convert (never null)
     * @param tenantId tenant identifier for multi-tenancy (never null)
     * @return promise of compiled pipeline
     * @throws IllegalArgumentException if workflow or tenantId is null
     */
    public Promise<Pipeline> toPipeline(WorkflowDefinition workflow, String tenantId) {
        Objects.requireNonNull(workflow, "WorkflowDefinition required");
        Objects.requireNonNull(tenantId, "tenantId required");

        logger.info("Converting workflow to pipeline: workflow={}, tenant={}",
                   workflow.getName(), tenantId);

        // Create pipeline identifier
        String pipelineIdStr = tenantId + ":" + PIPELINE_NAMESPACE + ":"
            + sanitizeName(workflow.getName()) + ":" + PIPELINE_VERSION;

        // Start building pipeline using the real Pipeline builder API
        PipelineBuilder builder = Pipeline.builder(pipelineIdStr, PIPELINE_VERSION)
            .name(workflow.getName())
            .description("Auto-generated from workflow: " + workflow.getName());

        // Add source stage (filter by trigger event)
        String triggerEvent = workflow.getTriggerEvent();
        OperatorId filterOperatorId = OperatorId.of(tenantId, "core", "filter:eventType", "1.0.0");
        builder.stage("filter", filterOperatorId,
            java.util.Map.of("predicate", "event.type == '" + triggerEvent + "'"));

        // Add workflow steps as stages
        List<WorkflowDefinition.WorkflowStep> steps = workflow.getSteps();
        logger.debug("Adding {} workflow steps to pipeline", steps.size());

        String previousStageId = "filter";
        for (int i = 0; i < steps.size(); i++) {
            WorkflowDefinition.WorkflowStep step = steps.get(i);
            String roleName = step.getAssignedRole();
            String stageId = "step-" + i;

            // Lookup agent operator in catalog
            OperatorId agentOperatorId = OperatorId.of(
                tenantId, "virtualorg", "agent:" + roleName, "1.0.0"
            );

            logger.debug("Adding workflow step {}: name={}, agent={}, operatorId={}",
                       i + 1, step.getDescription(), roleName, agentOperatorId);

            builder.stage(stageId, agentOperatorId);
            builder.edge(previousStageId, stageId);
            previousStageId = stageId;
        }

        // Configure error handling (dead-letter queue)
        String dlqStageId = "dlq";
        OperatorId dlqOperatorId = OperatorId.of(tenantId, "core", "dlq", "1.0.0");
        builder.stage(dlqStageId, dlqOperatorId);
        builder.onError(previousStageId, dlqStageId);

        // Build pipeline
        Pipeline pipeline = builder.build();
        logger.info("Workflow converted to pipeline: workflow={}, stages={}",
                   workflow.getName(), steps.size() + 1);
        return Promise.of(pipeline);
    }

    /**
     * Enriches event with workflow step metadata.
     *
     * <p>Adds step name, index, agent role to event metadata for tracing.
     *
     * @param event input event
     * @param step workflow step
     * @return enriched event
     */
    private Event enrichWithStepMetadata(Event event, WorkflowDefinition.WorkflowStep step) {
        // TODO: Implement proper event enrichment once Event builder API is stable
        // For now, just return the original event
        logger.debug("Event enrichment not yet implemented for step: {}", step.getId());
        return event;
    }

    /**
     * Creates error event for dead-letter queue.
     *
     * @param originalEvent event that caused error
     * @param exception error thrown
     * @param workflow workflow definition
     * @return error event
     */
    private Event createErrorEvent(Event originalEvent, Exception exception, WorkflowDefinition workflow) {
        // TODO: Implement proper error event creation once Event builder API is stable
        // For now, just return the original event
        logger.error("Error event creation not yet implemented for workflow: {}", workflow.getName(), exception);
        return originalEvent;
    }

    /**
     * Sanitizes workflow name for pipeline ID.
     *
     * <p>Replaces spaces with hyphens, converts to lowercase.
     *
     * @param name workflow name
     * @return sanitized name
     */
    private String sanitizeName(String name) {
        return name.toLowerCase()
            .replaceAll("\\s+", "-")
            .replaceAll("[^a-z0-9-]", "");
    }
}


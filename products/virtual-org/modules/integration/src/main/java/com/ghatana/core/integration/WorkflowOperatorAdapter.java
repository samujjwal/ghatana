package com.ghatana.core.integration;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.core.operator.AbstractOperator;
import com.ghatana.core.operator.OperatorConfig;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.core.operator.UnifiedOperator;
import com.ghatana.core.workflow.DecisionWorkflowEngine;
import com.ghatana.core.workflow.WorkflowTask;
import com.ghatana.core.workflow.ApprovalRouter;
import com.ghatana.core.workflow.EscalationManager;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integrates workflow tasks with the operator framework for task processing.
 *
 * <p><b>Purpose</b><br>
 * Treats workflow tasks as operators that can be composed, monitored, and processed
 * through the unified operator framework. Enables tasks to be integrated into pipelines
 * with error handling, retry logic, and state management.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowOperatorAdapter adapter = new WorkflowOperatorAdapter(
 *     workflow, router, escalations
 * );
 *
 * // Convert task to operator
 * UnifiedOperator taskOp = adapter.createTaskOperator(task);
 *
 * // Use in pipeline
 * OperatorChain pipeline = OperatorChain.create(validator)
 *     .then(taskOp)
 *     .then(new PatternOperatorAdapter(anomalyDetector));
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Tasks as first-class operators
 * - Task routing through pipelines
 * - Error handling for task failures
 * - Automatic escalation on errors
 * - Task completion tracking
 * - Event correlation
 *
 * @doc.type class
 * @doc.purpose Workflow-to-operator integration
 * @doc.layer core
 * @doc.pattern Adapter, Bridge
 */
public class WorkflowOperatorAdapter extends AbstractOperator {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowOperatorAdapter.class);

    private final DecisionWorkflowEngine workflow;
    private final ApprovalRouter router;
    private final EscalationManager escalations;
    private final Map<String, WorkflowTaskOperator> taskOperators;

    /**
     * Create workflow operator adapter.
     *
     * @param workflow Workflow engine
     * @param router Approval router
     * @param escalations Escalation manager
     */
    public WorkflowOperatorAdapter(DecisionWorkflowEngine workflow,
                                   ApprovalRouter router,
                                   EscalationManager escalations) {
        super(
            OperatorId.of("ghatana", "workflow", "adapter", "1.0.0"),
            OperatorType.STREAM,
            "Workflow Operator Adapter",
            "Integrates workflow tasks with operator framework",
            List.of("workflow", "integration", "adapter"),
            null
        );
        this.workflow = workflow;
        this.router = router;
        this.escalations = escalations;
        this.taskOperators = new ConcurrentHashMap<>();

        logger.info("Created WorkflowOperatorAdapter: {}", getId());
    }

    /**
     * Create operator from workflow task.
     *
     * @param task Task to convert
     * @return Operator wrapping task
     */
    public UnifiedOperator createTaskOperator(WorkflowTask task) {
        WorkflowTaskOperator taskOp = new WorkflowTaskOperator(task, workflow, router, escalations);
        taskOperators.put(task.getTaskId(), taskOp);
        return taskOp;
    }

    @Override
    public Promise<Void> initialize(OperatorConfig config) {
        logger.info("Initialized WorkflowOperatorAdapter");
        return Promise.complete();
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        // Workflow adapter doesn't process events directly - use task operators instead
        logger.warn("Direct event processing not supported on WorkflowOperatorAdapter");
        return Promise.of(OperatorResult.failed("Direct processing not supported - use createTaskOperator()"));
    }

    @Override
    public Promise<Void> start() {
        logger.info("Started WorkflowOperatorAdapter");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        logger.info("Stopped WorkflowOperatorAdapter");
        return Promise.complete();
    }

    @Override
    public boolean isHealthy() {
        return workflow != null && router != null && escalations != null;
    }

    @Override
    public Event toEvent() {
        throw new UnsupportedOperationException("Workflow operator serialization not yet implemented");
    }

    /**
     * Workflow task wrapped as operator.
     */
    private static class WorkflowTaskOperator extends AbstractOperator {
        private final WorkflowTask task;
        private final DecisionWorkflowEngine workflow;
        private final ApprovalRouter router;
        private final EscalationManager escalations;
        private boolean running = false;

        WorkflowTaskOperator(WorkflowTask task, DecisionWorkflowEngine workflow,
                            ApprovalRouter router, EscalationManager escalations) {
            super(
                OperatorId.of("ghatana", "workflow", task.getTaskId(), "1.0.0"),
                OperatorType.STREAM,
                "Workflow Task: " + task.getTitle(),
                "Executes workflow task: " + task.getDescription(),
                List.of("workflow", "task"),
                null
            );
            this.task = task;
            this.workflow = workflow;
            this.router = router;
            this.escalations = escalations;
        }

        @Override
        public Promise<Void> initialize(OperatorConfig config) {
            workflow.submitTask(task);
            logger.info("Initialized task operator: {}", task.getTaskId());

            String actor = task.getAssigneeId() != null ? task.getAssigneeId() : "system";
            return workflow.transitionTask(
                task.getTaskId(),
                WorkflowTask.State.ASSIGNED,
                actor,
                "Assigned by workflow operator adapter"
            );
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            // Workflow tasks process events as part of workflow execution
            logger.info("Processing event for task: {}", task.getTaskId());
            // TODO: Implement actual event processing logic based on task type
            return Promise.of(OperatorResult.of(event));
        }

        @Override
        public Promise<Void> start() {
            logger.info("Started task: {}", task.getTitle());
            String actor = task.getAssigneeId() != null ? task.getAssigneeId() : "system";
            return workflow.transitionTask(
                    task.getTaskId(),
                    WorkflowTask.State.IN_PROGRESS,
                    actor,
                    "Operator started")
                .whenComplete(($, error) -> {
                    if (error == null) {
                        running = true;
                    } else {
                        running = false;
                        logger.error("Failed to start task {}", task.getTaskId(), error);
                    }
                });
        }

        @Override
        public Promise<Void> stop() {
            running = false;
            logger.info("Stopped task: {}", task.getTaskId());
            return Promise.complete();
        }

        @Override
        public boolean isHealthy() {
            // Check for escalation triggers
            if (escalations.checkAndEscalate(task)) {
                logger.warn("Task escalated due to health check: {}", task.getTaskId());
                return false;
            }
            return running;
        }

        @Override
        public Event toEvent() {
            throw new UnsupportedOperationException("Workflow task operator serialization not yet implemented");
        }

        WorkflowTask getTask() {
            return task;
        }
    }
}

package com.ghatana.kernel.workflow;

import com.ghatana.kernel.adapter.aep.AepKernelAdapter;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates cross-product workflows through AEP operators and agents.
 *
 * <p>Manages complex business processes that span multiple products (e.g.,
 * healthcare payment processing spanning PHR and Finance). Coordinates workflow
 * execution, state management, and error handling across product boundaries.</p>
 *
 * <p>Workflows are composed of steps that execute AEP operators or agents,
 * with support for conditional branching, parallel execution, and compensation
 * (rollback) for failed operations.</p>
 *
 * @doc.type class
 * @doc.purpose Cross-product workflow orchestration via AEP operators/agents
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class CrossProductWorkflowEngine {

    private final AepKernelAdapter aepAdapter;
    private final Map<String, WorkflowDefinition> workflowDefinitions;
    private final Map<String, WorkflowInstance> activeWorkflows;

    public CrossProductWorkflowEngine(AepKernelAdapter aepAdapter) {
        this.aepAdapter = Objects.requireNonNull(aepAdapter, "aepAdapter cannot be null");
        this.workflowDefinitions = new ConcurrentHashMap<>();
        this.activeWorkflows = new ConcurrentHashMap<>();
    }

    /**
     * Registers a workflow definition.
     *
     * @param definition the workflow definition
     */
    public void registerWorkflow(WorkflowDefinition definition) {
        workflowDefinitions.put(definition.getWorkflowId(), definition);
    }

    /**
     * Executes a cross-product workflow.
     *
     * @param workflowId the workflow definition ID
     * @param context the workflow context
     * @return Promise containing workflow result
     */
    public Promise<WorkflowResult> executeWorkflow(String workflowId, WorkflowContext context) {
        WorkflowDefinition definition = workflowDefinitions.get(workflowId);
        if (definition == null) {
            return Promise.ofException(new IllegalArgumentException("Workflow not found: " + workflowId));
        }

        String instanceId = generateInstanceId(workflowId);
        WorkflowInstance instance = new WorkflowInstance(instanceId, definition, context);
        activeWorkflows.put(instanceId, instance);

        return executeWorkflowSteps(instance)
            .whenResult(result -> {
                instance.setStatus(WorkflowStatus.COMPLETED);
                activeWorkflows.remove(instanceId);
            })
            .whenException(e -> {
                instance.setStatus(WorkflowStatus.FAILED);
                // Trigger compensation for completed steps
                compensateWorkflow(instance);
            });
    }

    /**
     * Executes workflow steps in sequence.
     */
    private Promise<WorkflowResult> executeWorkflowSteps(WorkflowInstance instance) {
        List<WorkflowStep> steps = instance.getDefinition().getSteps();
        WorkflowContext context = instance.getContext();

        Promise<WorkflowStepResult> stepPromise = Promise.of(new WorkflowStepResult(null, true, null));

        for (WorkflowStep step : steps) {
            stepPromise = stepPromise.then(previousResult -> {
                if (!previousResult.isSuccess()) {
                    return Promise.of(previousResult);
                }

                // Check condition if present
                if (step.getCondition() != null && !step.getCondition().evaluate(context)) {
                    return Promise.of(new WorkflowStepResult(step.getStepId(), true, "Skipped by condition"));
                }

                return executeStep(step, context);
            });
        }

        return stepPromise.map(finalResult ->
            new WorkflowResult(
                instance.getInstanceId(),
                finalResult.isSuccess() ? WorkflowStatus.COMPLETED : WorkflowStatus.FAILED,
                finalResult.getData()
            )
        );
    }

    /**
     * Executes a single workflow step.
     */
    private Promise<WorkflowStepResult> executeStep(WorkflowStep step, WorkflowContext context) {
        try {
            // Execute through AEP
            return aepAdapter.executeOperator(step.getOperatorId(), step.getInput(), context)
                .map(output -> new WorkflowStepResult(step.getStepId(), true, output))
                .whenException(e -> new WorkflowStepResult(step.getStepId(), false, e.getMessage()));
        } catch (Exception e) {
            return Promise.of(new WorkflowStepResult(step.getStepId(), false, e.getMessage()));
        }
    }

    /**
     * Compensates (rolls back) a failed workflow.
     */
    private void compensateWorkflow(WorkflowInstance instance) {
        List<WorkflowStep> compensationSteps = instance.getDefinition().getCompensationSteps();
        for (WorkflowStep step : compensationSteps) {
            try {
                aepAdapter.executeOperator(step.getOperatorId(), step.getInput(), instance.getContext());
            } catch (Exception e) {
                // Log compensation failure - may require manual intervention
            }
        }
    }

    /**
     * Gets the status of an active workflow.
     */
    public Optional<WorkflowStatus> getWorkflowStatus(String instanceId) {
        WorkflowInstance instance = activeWorkflows.get(instanceId);
        return instance != null ? Optional.of(instance.getStatus()) : Optional.empty();
    }

    private String generateInstanceId(String workflowId) {
        return workflowId + "-" + UUID.randomUUID().toString();
    }

    // ==================== Inner Classes ====================

    public static class WorkflowDefinition {
        private final String workflowId;
        private final String name;
        private final String description;
        private final List<WorkflowStep> steps;
        private final List<WorkflowStep> compensationSteps;

        public WorkflowDefinition(String workflowId, String name, String description,
                                   List<WorkflowStep> steps, List<WorkflowStep> compensationSteps) {
            this.workflowId = workflowId;
            this.name = name;
            this.description = description;
            this.steps = steps;
            this.compensationSteps = compensationSteps;
        }

        public String getWorkflowId() { return workflowId; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<WorkflowStep> getSteps() { return steps; }
        public List<WorkflowStep> getCompensationSteps() { return compensationSteps; }
    }

    public static class WorkflowStep {
        private final String stepId;
        private final String operatorId;
        private final Map<String, Object> input;
        private final StepCondition condition;

        public WorkflowStep(String stepId, String operatorId, Map<String, Object> input, StepCondition condition) {
            this.stepId = stepId;
            this.operatorId = operatorId;
            this.input = input;
            this.condition = condition;
        }

        public String getStepId() { return stepId; }
        public String getOperatorId() { return operatorId; }
        public Map<String, Object> getInput() { return input; }
        public StepCondition getCondition() { return condition; }
    }

    public interface StepCondition {
        boolean evaluate(WorkflowContext context);
    }

    public static class WorkflowContext {
        private final String workflowId;
        private final String tenantId;
        private final String sourceProduct;
        private final String targetProduct;
        private final Map<String, Object> variables;

        public WorkflowContext(String workflowId, String tenantId, String sourceProduct, String targetProduct) {
            this.workflowId = workflowId;
            this.tenantId = tenantId;
            this.sourceProduct = sourceProduct;
            this.targetProduct = targetProduct;
            this.variables = new ConcurrentHashMap<>();
        }

        public String getWorkflowId() { return workflowId; }
        public String getTenantId() { return tenantId; }
        public String getSourceProduct() { return sourceProduct; }
        public String getTargetProduct() { return targetProduct; }
        public Map<String, Object> getVariables() { return variables; }

        public void setVariable(String key, Object value) {
            variables.put(key, value);
        }
    }

    private static class WorkflowInstance {
        private final String instanceId;
        private final WorkflowDefinition definition;
        private final WorkflowContext context;
        private volatile WorkflowStatus status;

        WorkflowInstance(String instanceId, WorkflowDefinition definition, WorkflowContext context) {
            this.instanceId = instanceId;
            this.definition = definition;
            this.context = context;
            this.status = WorkflowStatus.RUNNING;
        }

        String getInstanceId() { return instanceId; }
        WorkflowDefinition getDefinition() { return definition; }
        WorkflowContext getContext() { return context; }
        WorkflowStatus getStatus() { return status; }
        void setStatus(WorkflowStatus status) { this.status = status; }
    }

    public static class WorkflowResult {
        private final String instanceId;
        private final WorkflowStatus status;
        private final Object data;

        public WorkflowResult(String instanceId, WorkflowStatus status, Object data) {
            this.instanceId = instanceId;
            this.status = status;
            this.data = data;
        }

        public String getInstanceId() { return instanceId; }
        public WorkflowStatus getStatus() { return status; }
        public Object getData() { return data; }
    }

    private static class WorkflowStepResult {
        private final String stepId;
        private final boolean success;
        private final Object data;

        WorkflowStepResult(String stepId, boolean success, Object data) {
            this.stepId = stepId;
            this.success = success;
            this.data = data;
        }

        String getStepId() { return stepId; }
        boolean isSuccess() { return success; }
        Object getData() { return data; }
    }

    public enum WorkflowStatus {
        RUNNING,
        COMPLETED,
        FAILED,
        COMPENSATING
    }

    // Stub AEP adapter interface
    public interface AepKernelAdapter {
        Promise<Object> executeOperator(String operatorId, Map<String, Object> input, WorkflowContext context);
    }
}

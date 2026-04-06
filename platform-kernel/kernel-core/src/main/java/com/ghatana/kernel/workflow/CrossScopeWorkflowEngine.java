package com.ghatana.kernel.workflow;

import com.ghatana.kernel.policy.ClassificationDescriptor;
import com.ghatana.kernel.scope.ScopeDescriptor;
import io.activej.promise.Promise;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scope-aware cross-scope workflow engine.
 *
 * <p>Canonical replacement for {@link CrossProductWorkflowEngine}. Orchestrates
 * workflows that span multiple scopes (products, tenants, domain packs, etc.)
 * using {@link ScopeDescriptor} and {@link ClassificationDescriptor} instead of
 * product id strings.</p>
 *
 * <p>Workflows are composed of steps that execute operators, with support for
 * conditional branching, compensation (rollback) for failures, and scope-aware
 * context propagation.</p>
 *
 * @doc.type class
 * @doc.purpose Scope-aware workflow orchestration replacing product-id-based workflows
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public class CrossScopeWorkflowEngine {

    private final OperatorExecutor operatorExecutor;
    private final Map<String, ScopeWorkflowDefinition> workflowDefinitions;
    private final Map<String, ScopeWorkflowInstance> activeWorkflows;

    public CrossScopeWorkflowEngine(OperatorExecutor operatorExecutor) {
        this.operatorExecutor = Objects.requireNonNull(operatorExecutor, "operatorExecutor cannot be null");
        this.workflowDefinitions = new ConcurrentHashMap<>();
        this.activeWorkflows = new ConcurrentHashMap<>();
    }

    /**
     * Registers a workflow definition.
     *
     * @param definition the workflow definition
     */
    public void registerWorkflow(ScopeWorkflowDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null");
        workflowDefinitions.put(definition.getWorkflowId(), definition);
    }

    /**
     * Executes a cross-scope workflow.
     *
     * @param workflowId the workflow definition ID
     * @param context    the scope-aware workflow context
     * @return Promise containing workflow result
     */
    public Promise<ScopeWorkflowResult> executeWorkflow(String workflowId,
                                                         ScopeWorkflowContext context) {
        ScopeWorkflowDefinition definition = workflowDefinitions.get(workflowId);
        if (definition == null) {
            return Promise.ofException(
                    new IllegalArgumentException("Workflow not found: " + workflowId));
        }

        String instanceId = workflowId + "-" + UUID.randomUUID();
        ScopeWorkflowInstance instance = new ScopeWorkflowInstance(instanceId, definition, context);
        activeWorkflows.put(instanceId, instance);

        return executeSteps(instance)
                .whenResult(result -> {
                    instance.setStatus(ScopeWorkflowStatus.COMPLETED);
                    activeWorkflows.remove(instanceId);
                })
                .whenException(e -> {
                    instance.setStatus(ScopeWorkflowStatus.FAILED);
                    compensate(instance);
                });
    }

    /**
     * Gets the status of an active workflow.
     */
    public Optional<ScopeWorkflowStatus> getStatus(String instanceId) {
        ScopeWorkflowInstance instance = activeWorkflows.get(instanceId);
        return instance != null ? Optional.of(instance.getStatus()) : Optional.empty();
    }

    private Promise<ScopeWorkflowResult> executeSteps(ScopeWorkflowInstance instance) {
        List<ScopeWorkflowStep> steps = instance.getDefinition().getSteps();
        ScopeWorkflowContext context = instance.getContext();

        Promise<StepResult> chain = Promise.of(new StepResult(null, true, null));

        for (ScopeWorkflowStep step : steps) {
            chain = chain.then(prev -> {
                if (!prev.success()) {
                    return Promise.of(prev);
                }
                if (step.getCondition() != null && !step.getCondition().evaluate(context)) {
                    return Promise.of(new StepResult(step.getStepId(), true, "Skipped"));
                }
                return operatorExecutor.execute(step.getOperatorId(), step.getInput(), context)
                        .map(output -> new StepResult(step.getStepId(), true, output));
            });
        }

        return chain.map(finalResult ->
                new ScopeWorkflowResult(
                        instance.getInstanceId(),
                        finalResult.success() ? ScopeWorkflowStatus.COMPLETED : ScopeWorkflowStatus.FAILED,
                        finalResult.data()));
    }

    private void compensate(ScopeWorkflowInstance instance) {
        for (ScopeWorkflowStep step : instance.getDefinition().getCompensationSteps()) {
            try {
                operatorExecutor.execute(step.getOperatorId(), step.getInput(), instance.getContext());
            } catch (Exception ignored) {
                // Compensation failure logged at infrastructure level
            }
        }
    }

    // ==================== Adapter Interface ====================

    /**
     * Adapter interface for executing workflow operators.
     */
    public interface OperatorExecutor {
        Promise<Object> execute(String operatorId, Map<String, Object> input,
                                ScopeWorkflowContext context);
    }

    // ==================== Context ====================

    /**
     * Scope-aware workflow context carrying scope descriptors and classification.
     */
    public static class ScopeWorkflowContext {
        private final String workflowId;
        private final String tenantId;
        private final ScopeDescriptor sourceScope;
        private final ScopeDescriptor targetScope;
        private final ClassificationDescriptor classification;
        private final Map<String, Object> variables;

        public ScopeWorkflowContext(String workflowId, String tenantId,
                                    ScopeDescriptor sourceScope, ScopeDescriptor targetScope,
                                    ClassificationDescriptor classification) {
            this.workflowId = Objects.requireNonNull(workflowId);
            this.tenantId = tenantId;
            this.sourceScope = Objects.requireNonNull(sourceScope);
            this.targetScope = Objects.requireNonNull(targetScope);
            this.classification = classification;
            this.variables = new ConcurrentHashMap<>();
        }

        public String getWorkflowId() { return workflowId; }
        public String getTenantId() { return tenantId; }
        public ScopeDescriptor getSourceScope() { return sourceScope; }
        public ScopeDescriptor getTargetScope() { return targetScope; }
        public ClassificationDescriptor getClassification() { return classification; }
        public Map<String, Object> getVariables() { return variables; }

        public void setVariable(String key, Object value) {
            variables.put(key, value);
        }

        @SuppressWarnings("unchecked")
        public <T> T getVariable(String key, Class<T> type) {
            return (T) variables.get(key);
        }
    }

    // ==================== Workflow Types ====================

    /**
     * Scope-aware workflow definition.
     */
    public static class ScopeWorkflowDefinition {
        private final String workflowId;
        private final String name;
        private final String description;
        private final List<ScopeWorkflowStep> steps;
        private final List<ScopeWorkflowStep> compensationSteps;

        public ScopeWorkflowDefinition(String workflowId, String name, String description,
                                       List<ScopeWorkflowStep> steps,
                                       List<ScopeWorkflowStep> compensationSteps) {
            this.workflowId = workflowId;
            this.name = name;
            this.description = description;
            this.steps = List.copyOf(steps);
            this.compensationSteps = compensationSteps != null ? List.copyOf(compensationSteps) : List.of();
        }

        public String getWorkflowId() { return workflowId; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<ScopeWorkflowStep> getSteps() { return steps; }
        public List<ScopeWorkflowStep> getCompensationSteps() { return compensationSteps; }
    }

    /**
     * A single workflow step.
     */
    public static class ScopeWorkflowStep {
        private final String stepId;
        private final String operatorId;
        private final Map<String, Object> input;
        private final ScopeStepCondition condition;

        public ScopeWorkflowStep(String stepId, String operatorId,
                                 Map<String, Object> input, ScopeStepCondition condition) {
            this.stepId = stepId;
            this.operatorId = operatorId;
            this.input = input != null ? Map.copyOf(input) : Map.of();
            this.condition = condition;
        }

        public String getStepId() { return stepId; }
        public String getOperatorId() { return operatorId; }
        public Map<String, Object> getInput() { return input; }
        public ScopeStepCondition getCondition() { return condition; }
    }

    /**
     * Condition for conditional step execution.
     */
    public interface ScopeStepCondition {
        boolean evaluate(ScopeWorkflowContext context);
    }

    /**
     * Scope workflow result.
     */
    public record ScopeWorkflowResult(
            String instanceId,
            ScopeWorkflowStatus status,
            Object data
    ) {}

    /**
     * Workflow status.
     */
    public enum ScopeWorkflowStatus {
        RUNNING, COMPLETED, FAILED, COMPENSATING
    }

    // ==================== Internal ====================

    private record StepResult(String stepId, boolean success, Object data) {}

    private static class ScopeWorkflowInstance {
        private final String instanceId;
        private final ScopeWorkflowDefinition definition;
        private final ScopeWorkflowContext context;
        private volatile ScopeWorkflowStatus status;

        ScopeWorkflowInstance(String instanceId, ScopeWorkflowDefinition definition,
                              ScopeWorkflowContext context) {
            this.instanceId = instanceId;
            this.definition = definition;
            this.context = context;
            this.status = ScopeWorkflowStatus.RUNNING;
        }

        String getInstanceId() { return instanceId; }
        ScopeWorkflowDefinition getDefinition() { return definition; }
        ScopeWorkflowContext getContext() { return context; }
        ScopeWorkflowStatus getStatus() { return status; }
        void setStatus(ScopeWorkflowStatus s) { this.status = s; }
    }
}

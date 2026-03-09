package com.ghatana.products.yappc.domain.workflow;

import com.ghatana.products.yappc.domain.agent.AgentRegistry;
import com.ghatana.products.yappc.domain.agent.AIAgent;
import com.ghatana.products.yappc.domain.agent.AIAgentContext;
import com.ghatana.products.yappc.domain.agent.AgentName;
import com.ghatana.products.yappc.domain.agent.WorkflowRouterAgent;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Service for managing AI-assisted workflows.
 * <p>
 * Handles workflow lifecycle, step transitions, and AI plan integration.
 * Coordinates with AI agents for plan generation and execution.
 *
 * @doc.type class
 * @doc.purpose AI workflow orchestration service
 * @doc.layer product
 * @doc.pattern Service
 */
public class AiWorkflowService {

    private static final Logger LOG = LoggerFactory.getLogger(AiWorkflowService.class);

    private final AiWorkflowRepository workflowRepository;
    private final AiPlanRepository planRepository;
    private final AgentRegistry agentRegistry;

    /**
     * Creates a new AiWorkflowService.
     *
     * @param workflowRepository Repository for workflows
     * @param planRepository Repository for plans
     * @param agentRegistry Registry for AI agents
     */
    public AiWorkflowService(
        @NotNull AiWorkflowRepository workflowRepository,
        @NotNull AiPlanRepository planRepository,
        @NotNull AgentRegistry agentRegistry
    ) {
        this.workflowRepository = Objects.requireNonNull(workflowRepository);
        this.planRepository = Objects.requireNonNull(planRepository);
        this.agentRegistry = Objects.requireNonNull(agentRegistry);
    }

    // ==================== WORKFLOW LIFECYCLE ====================

    /**
     * Creates a new workflow.
     *
     * @param request The creation request
     * @return Promise resolving to the created workflow
     */
    @NotNull
    public Promise<AiWorkflowInstance> createWorkflow(@NotNull CreateWorkflowRequest request) {
        LOG.info("Creating workflow: {} for tenant: {}", request.name(), request.tenantId());

        String workflowId = UUID.randomUUID().toString();
        AiWorkflowInstance workflow = AiWorkflowInstance.create(
            workflowId,
            request.tenantId(),
            request.name(),
            request.description(),
            request.type()
        );

        return workflowRepository.save(workflow);
    }

    /**
     * Gets a workflow by ID.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @return Promise resolving to the workflow if found
     */
    @NotNull
    public Promise<Optional<AiWorkflowInstance>> getWorkflow(
        @NotNull String workflowId,
        @NotNull String tenantId
    ) {
        return workflowRepository.findById(workflowId, tenantId);
    }

    /**
     * Lists workflows for a tenant.
     *
     * @param tenantId The tenant ID
     * @param status Optional status filter
     * @param limit Maximum results
     * @param offset Pagination offset
     * @return Promise resolving to list of workflows
     */
    @NotNull
    public Promise<List<AiWorkflowInstance>> listWorkflows(
        @NotNull String tenantId,
        @Nullable AiWorkflowInstance.WorkflowStatus status,
        int limit,
        int offset
    ) {
        return workflowRepository.findByTenant(tenantId, status, limit, offset);
    }

    /**
     * Deletes a workflow and its associated plans.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @return Promise resolving to true if deleted
     */
    @NotNull
    public Promise<Boolean> deleteWorkflow(@NotNull String workflowId, @NotNull String tenantId) {
        LOG.info("Deleting workflow: {} for tenant: {}", workflowId, tenantId);
        return workflowRepository.delete(workflowId, tenantId);
    }

    // ==================== WORKFLOW STATE TRANSITIONS ====================

    /**
     * Starts a workflow execution.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @return Promise resolving to the started workflow
     */
    @NotNull
    public Promise<AiWorkflowInstance> startWorkflow(
        @NotNull String workflowId,
        @NotNull String tenantId
    ) {
        LOG.info("Starting workflow: {} for tenant: {}", workflowId, tenantId);

        return workflowRepository.findById(workflowId, tenantId)
            .then(optWorkflow -> {
                if (optWorkflow.isEmpty()) {
                    return Promise.ofException(
                        new WorkflowNotFoundException(workflowId)
                    );
                }

                AiWorkflowInstance workflow = optWorkflow.get();
                if (workflow.status() != AiWorkflowInstance.WorkflowStatus.DRAFT &&
                    workflow.status() != AiWorkflowInstance.WorkflowStatus.PENDING) {
                    return Promise.ofException(
                        new InvalidWorkflowStateException(
                            workflow.id(),
                            workflow.status(),
                            "Cannot start workflow in current state"
                        )
                    );
                }

                return workflowRepository.updateStatus(
                    workflowId,
                    tenantId,
                    AiWorkflowInstance.WorkflowStatus.IN_PROGRESS
                );
            });
    }

    /**
     * Pauses a running workflow.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @return Promise resolving to the paused workflow
     */
    @NotNull
    public Promise<AiWorkflowInstance> pauseWorkflow(
        @NotNull String workflowId,
        @NotNull String tenantId
    ) {
        LOG.info("Pausing workflow: {} for tenant: {}", workflowId, tenantId);

        return workflowRepository.findById(workflowId, tenantId)
            .then(optWorkflow -> {
                if (optWorkflow.isEmpty()) {
                    return Promise.ofException(new WorkflowNotFoundException(workflowId));
                }

                AiWorkflowInstance workflow = optWorkflow.get();
                if (workflow.status() != AiWorkflowInstance.WorkflowStatus.IN_PROGRESS) {
                    return Promise.ofException(
                        new InvalidWorkflowStateException(
                            workflow.id(),
                            workflow.status(),
                            "Cannot pause workflow not in progress"
                        )
                    );
                }

                return workflowRepository.updateStatus(
                    workflowId,
                    tenantId,
                    AiWorkflowInstance.WorkflowStatus.PAUSED
                );
            });
    }

    /**
     * Resumes a paused workflow.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @return Promise resolving to the resumed workflow
     */
    @NotNull
    public Promise<AiWorkflowInstance> resumeWorkflow(
        @NotNull String workflowId,
        @NotNull String tenantId
    ) {
        LOG.info("Resuming workflow: {} for tenant: {}", workflowId, tenantId);

        return workflowRepository.findById(workflowId, tenantId)
            .then(optWorkflow -> {
                if (optWorkflow.isEmpty()) {
                    return Promise.ofException(new WorkflowNotFoundException(workflowId));
                }

                AiWorkflowInstance workflow = optWorkflow.get();
                if (workflow.status() != AiWorkflowInstance.WorkflowStatus.PAUSED) {
                    return Promise.ofException(
                        new InvalidWorkflowStateException(
                            workflow.id(),
                            workflow.status(),
                            "Cannot resume workflow not paused"
                        )
                    );
                }

                return workflowRepository.updateStatus(
                    workflowId,
                    tenantId,
                    AiWorkflowInstance.WorkflowStatus.IN_PROGRESS
                );
            });
    }

    /**
     * Cancels a workflow.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @return Promise resolving to the cancelled workflow
     */
    @NotNull
    public Promise<AiWorkflowInstance> cancelWorkflow(
        @NotNull String workflowId,
        @NotNull String tenantId
    ) {
        LOG.info("Cancelling workflow: {} for tenant: {}", workflowId, tenantId);

        return workflowRepository.findById(workflowId, tenantId)
            .then(optWorkflow -> {
                if (optWorkflow.isEmpty()) {
                    return Promise.ofException(new WorkflowNotFoundException(workflowId));
                }

                AiWorkflowInstance workflow = optWorkflow.get();
                if (workflow.isTerminal()) {
                    return Promise.ofException(
                        new InvalidWorkflowStateException(
                            workflow.id(),
                            workflow.status(),
                            "Cannot cancel workflow in terminal state"
                        )
                    );
                }

                return workflowRepository.updateStatus(
                    workflowId,
                    tenantId,
                    AiWorkflowInstance.WorkflowStatus.CANCELLED
                );
            });
    }

    // ==================== STEP TRANSITIONS ====================

    /**
     * Advances to the next step in the workflow.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @param stepResult The result of the current step
     * @return Promise resolving to the updated workflow
     */
    @NotNull
    public Promise<AiWorkflowInstance> advanceStep(
        @NotNull String workflowId,
        @NotNull String tenantId,
        @NotNull AiWorkflowInstance.AiWorkflowStepResult stepResult
    ) {
        LOG.info("Advancing step for workflow: {}", workflowId);

        return workflowRepository.findById(workflowId, tenantId)
            .then(optWorkflow -> {
                if (optWorkflow.isEmpty()) {
                    return Promise.ofException(new WorkflowNotFoundException(workflowId));
                }

                AiWorkflowInstance workflow = optWorkflow.get();
                if (workflow.status() != AiWorkflowInstance.WorkflowStatus.IN_PROGRESS) {
                    return Promise.ofException(
                        new InvalidWorkflowStateException(
                            workflow.id(),
                            workflow.status(),
                            "Cannot advance step when workflow not in progress"
                        )
                    );
                }

                // Save step result
                return workflowRepository.saveStepResult(workflowId, tenantId, stepResult)
                    .then(updated -> {
                        int nextIndex = updated.currentStepIndex() + 1;

                        // Check if workflow is complete
                        if (nextIndex >= updated.totalSteps()) {
                            return workflowRepository.updateStatus(
                                workflowId,
                                tenantId,
                                AiWorkflowInstance.WorkflowStatus.COMPLETED
                            );
                        }

                        // Get next step from plan
                        return planRepository.findActivePlan(workflowId, tenantId)
                            .then(optPlan -> {
                                if (optPlan.isEmpty() || optPlan.get().steps().size() <= nextIndex) {
                                    return Promise.ofException(
                                        new WorkflowExecutionException(
                                            workflowId,
                                            "No active plan or step not found"
                                        )
                                    );
                                }

                                AiPlan.PlanStep nextStep = optPlan.get().steps().get(nextIndex);
                                return workflowRepository.updateCurrentStep(
                                    workflowId,
                                    tenantId,
                                    nextStep.id(),
                                    nextIndex
                                );
                            });
                    });
            });
    }

    /**
     * Goes back to a previous step.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @param stepId The target step ID
     * @return Promise resolving to the updated workflow
     */
    @NotNull
    public Promise<AiWorkflowInstance> goToStep(
        @NotNull String workflowId,
        @NotNull String tenantId,
        @NotNull String stepId
    ) {
        LOG.info("Going to step {} for workflow: {}", stepId, workflowId);

        return workflowRepository.findById(workflowId, tenantId)
            .then(optWorkflow -> {
                if (optWorkflow.isEmpty()) {
                    return Promise.ofException(new WorkflowNotFoundException(workflowId));
                }

                return planRepository.findActivePlan(workflowId, tenantId)
                    .then(optPlan -> {
                        if (optPlan.isEmpty()) {
                            return Promise.ofException(
                                new WorkflowExecutionException(workflowId, "No active plan")
                            );
                        }

                        AiPlan plan = optPlan.get();
                        int stepIndex = -1;
                        for (int i = 0; i < plan.steps().size(); i++) {
                            if (plan.steps().get(i).id().equals(stepId)) {
                                stepIndex = i;
                                break;
                            }
                        }

                        if (stepIndex < 0) {
                            return Promise.ofException(
                                new WorkflowExecutionException(
                                    workflowId,
                                    "Step not found: " + stepId
                                )
                            );
                        }

                        return workflowRepository.updateCurrentStep(
                            workflowId,
                            tenantId,
                            stepId,
                            stepIndex
                        );
                    });
            });
    }

    // ==================== AI PLAN INTEGRATION ====================

    /**
     * Generates an AI plan for the workflow.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @param objective The objective description
     * @return Promise resolving to the generated plan
     */
    @NotNull
    public Promise<AiPlan> generatePlan(
        @NotNull String workflowId,
        @NotNull String tenantId,
        @NotNull String objective
    ) {
        LOG.info("Generating plan for workflow: {}", workflowId);

        return workflowRepository.findById(workflowId, tenantId)
            .then(optWorkflow -> {
                if (optWorkflow.isEmpty()) {
                    return Promise.ofException(new WorkflowNotFoundException(workflowId));
                }

                AiWorkflowInstance workflow = optWorkflow.get();

                // Create plan in generating state
                String planId = UUID.randomUUID().toString();
                AiPlan plan = AiPlan.creating(
                    planId,
                    workflowId,
                    tenantId,
                    objective,
                    "gpt-4"
                );

                List<AiPlan.PlanStep> steps = defaultStepsFor(objective);

                return planRepository.save(plan)
                        .then(savedPlan -> planRepository.updateSteps(planId, tenantId, steps)
                                .then(updated -> planRepository.updateStatus(
                                        planId,
                                        tenantId,
                                        AiPlan.PlanStatus.PENDING_REVIEW
                                )))
                        .then(updated -> workflowRepository.updateStatus(
                                workflowId,
                                tenantId,
                                AiWorkflowInstance.WorkflowStatus.PENDING
                        ).map(w -> updated));
            });
    }

    /**
     * Approves an AI plan.
     *
     * @param planId The plan ID
     * @param tenantId The tenant ID
     * @return Promise resolving to the approved plan
     */
    @NotNull
    public Promise<AiPlan> approvePlan(@NotNull String planId, @NotNull String tenantId) {
        LOG.info("Approving plan: {}", planId);

        return planRepository.findById(planId, tenantId)
            .then(optPlan -> {
                if (optPlan.isEmpty()) {
                    return Promise.ofException(
                        new WorkflowExecutionException("", "Plan not found: " + planId)
                    );
                }

                AiPlan plan = optPlan.get();
                if (plan.status() != AiPlan.PlanStatus.PENDING_REVIEW &&
                    plan.status() != AiPlan.PlanStatus.MODIFIED) {
                    return Promise.ofException(
                        new WorkflowExecutionException(
                            plan.workflowId(),
                            "Cannot approve plan in status: " + plan.status()
                        )
                    );
                }

                return planRepository.updateStatus(planId, tenantId, AiPlan.PlanStatus.APPROVED);
            });
    }

    /**
     * Rejects an AI plan.
     *
     * @param planId The plan ID
     * @param tenantId The tenant ID
     * @param reason The rejection reason
     * @return Promise resolving to the rejected plan
     */
    @NotNull
    public Promise<AiPlan> rejectPlan(
        @NotNull String planId,
        @NotNull String tenantId,
        @Nullable String reason
    ) {
        LOG.info("Rejecting plan: {} with reason: {}", planId, reason);

        return planRepository.updateStatus(planId, tenantId, AiPlan.PlanStatus.REJECTED);
    }

    /**
     * Modifies plan steps.
     *
     * @param planId The plan ID
     * @param tenantId The tenant ID
     * @param steps The modified steps
     * @return Promise resolving to the modified plan
     */
    @NotNull
    public Promise<AiPlan> modifyPlanSteps(
        @NotNull String planId,
        @NotNull String tenantId,
        @NotNull List<AiPlan.PlanStep> steps
    ) {
        LOG.info("Modifying plan steps: {}", planId);

        return planRepository.findById(planId, tenantId)
            .then(optPlan -> {
                if (optPlan.isEmpty()) {
                    return Promise.ofException(
                        new WorkflowExecutionException("", "Plan not found: " + planId)
                    );
                }

                if (!optPlan.get().isModifiable()) {
                    return Promise.ofException(
                        new WorkflowExecutionException(
                            optPlan.get().workflowId(),
                            "Plan is not modifiable in current state"
                        )
                    );
                }

                return planRepository.updateSteps(planId, tenantId, steps)
                    .then(updated -> planRepository.updateStatus(
                        planId,
                        tenantId,
                        AiPlan.PlanStatus.MODIFIED
                    ));
            });
    }

    // ==================== WORKFLOW ROUTING ====================

    /**
     * Routes workflow to the appropriate next step using AI.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @param userInput Optional user input for routing decision
     * @return Promise resolving to the routing decision
     */
    @NotNull
    public Promise<RoutingDecision> routeWorkflow(
        @NotNull String workflowId,
        @NotNull String tenantId,
        @Nullable String userInput
    ) {
        LOG.info("Routing workflow: {}", workflowId);

        return workflowRepository.findById(workflowId, tenantId)
            .then(optWorkflow -> {
                if (optWorkflow.isEmpty()) {
                    return Promise.ofException(new WorkflowNotFoundException(workflowId));
                }

                AiWorkflowInstance workflow = optWorkflow.get();

                return planRepository.findActivePlan(workflowId, tenantId)
                    .then(optPlan -> {
                        if (optPlan.isEmpty()) {
                            return Promise.of(new RoutingDecision(
                                null,
                                "GENERATE_PLAN",
                                "No active plan found, generate one first",
                                1.0
                            ));
                        }

                        AIAgent<WorkflowRouterAgent.RouterInput, WorkflowRouterAgent.RouterOutput> routerAgent =
                            agentRegistry.get(AgentName.WORKFLOW_ROUTER_AGENT);

                        if (routerAgent == null) {
                            return Promise.of(new RoutingDecision(
                                null,
                                "NO_ROUTER",
                                "Workflow router agent is not registered",
                                0.0
                            ));
                        }

                        WorkflowRouterAgent.RouterInput input = WorkflowRouterAgent.RouterInput.builder()
                            .routingType(WorkflowRouterAgent.RoutingType.NEXT_STEP)
                            .workflowId(workflowId)
                            .currentStep(workflow.currentStepId())
                            .intent(userInput)
                            .context(workflow.context())
                            .build();

                        return routerAgent.execute(input, createAgentContext(workflow))
                            .map(r -> r.data())
                            .map(output -> selectDecision(output, workflow));
                    });
            });
    }

    // ==================== HELPER METHODS ====================

                private AIAgentContext createAgentContext(AiWorkflowInstance workflow) {
                return AIAgentContext.builder()
                    .userId("system")
                    .workspaceId("workflow:" + workflow.id())
                    .requestId(UUID.randomUUID().toString())
                    .tenantId(workflow.tenantId())
                    .organizationId(workflow.tenantId())
                    .metadata(Map.of(
                        "workflowId", workflow.id(),
                        "workflowType", workflow.type().name(),
                        "workflowContext", workflow.context()
                    ))
                    .build();
    }

                private static List<AiPlan.PlanStep> defaultStepsFor(String objective) {
                String s0 = UUID.randomUUID().toString();
                String s1 = UUID.randomUUID().toString();
                String s2 = UUID.randomUUID().toString();
                String s3 = UUID.randomUUID().toString();
                String s4 = UUID.randomUUID().toString();
                String s5 = UUID.randomUUID().toString();
                String s6 = UUID.randomUUID().toString();

                return List.of(
                    new AiPlan.PlanStep(
                        s0,
                        "Capture intent",
                        "Clarify the objective and success criteria",
                        AiPlan.PlanStep.StepType.INTENT_CAPTURE,
                        0,
                        List.of(),
                        objective,
                        null,
                        null,
                        true,
                        null
                    ),
                    new AiPlan.PlanStep(
                        s1,
                        "Gather context",
                        "Collect relevant repo, constraints, and dependencies",
                        AiPlan.PlanStep.StepType.CONTEXT_GATHERING,
                        1,
                        List.of(s0),
                        "Identify constraints (stack, architecture, policies) and required capabilities.",
                        null,
                        null,
                        false,
                        null
                    ),
                    new AiPlan.PlanStep(
                        s2,
                        "Generate plan",
                        "Produce an execution plan with ordered steps and dependencies",
                        AiPlan.PlanStep.StepType.PLAN_GENERATION,
                        2,
                        List.of(s1),
                        "Break down the objective into concrete steps; include validation gates.",
                        null,
                        null,
                        true,
                        null
                    ),
                    new AiPlan.PlanStep(
                        s3,
                        "Implement changes",
                        "Execute planned code changes",
                        AiPlan.PlanStep.StepType.CODE_GENERATION,
                        3,
                        List.of(s2),
                        "Implement minimal, safe changes aligned with the plan.",
                        null,
                        null,
                        false,
                        null
                    ),
                    new AiPlan.PlanStep(
                        s4,
                        "Generate/adjust tests",
                        "Create or update tests to validate behavior",
                        AiPlan.PlanStep.StepType.TEST_GENERATION,
                        4,
                        List.of(s3),
                        "Add focused tests covering the change and edge cases.",
                        null,
                        null,
                        false,
                        null
                    ),
                    new AiPlan.PlanStep(
                        s5,
                        "Verify",
                        "Run checks and validate outputs",
                        AiPlan.PlanStep.StepType.VERIFICATION,
                        5,
                        List.of(s4),
                        "Run build/tests and ensure no regressions.",
                        null,
                        null,
                        true,
                        null
                    ),
                    new AiPlan.PlanStep(
                        s6,
                        "Document",
                        "Summarize changes and update docs",
                        AiPlan.PlanStep.StepType.DOCUMENTATION,
                        6,
                        List.of(s5),
                        "Document key decisions and usage.",
                        null,
                        null,
                        false,
                        null
                    )
                );
                }

                private static RoutingDecision selectDecision(
                    WorkflowRouterAgent.RouterOutput output,
                    AiWorkflowInstance workflow
                ) {
                if (output == null || output.decisions() == null || output.decisions().isEmpty()) {
                    return new RoutingDecision(
                        null,
                        "NO_DECISION",
                        "Router produced no decisions",
                        0.0
                    );
                }

                WorkflowRouterAgent.RoutingDecision best = output.decisions().stream()
                    .max(Comparator.comparingDouble(WorkflowRouterAgent.RoutingDecision::confidence))
                    .orElse(output.decisions().getFirst());

                return new RoutingDecision(
                    best.id(),
                    "NEXT_STEP",
                    best.reason(),
                    best.confidence()
                );
                }

    // ==================== REQUEST/RESPONSE TYPES ====================

    /**
     * Request for creating a workflow
     */
    public record CreateWorkflowRequest(
        @NotNull String tenantId,
        @NotNull String name,
        @NotNull String description,
        @NotNull AiWorkflowInstance.WorkflowType type,
        @Nullable String createdBy
    ) {}

    /**
     * Routing decision from AI
     */
    public record RoutingDecision(
        @Nullable String nextStepId,
        @NotNull String action,
        @NotNull String reasoning,
        double confidence
    ) {}

    // ==================== EXCEPTIONS ====================

    /**
     * Exception thrown when workflow is not found
     */
    public static class WorkflowNotFoundException extends RuntimeException {
        private final String workflowId;

        public WorkflowNotFoundException(String workflowId) {
            super("Workflow not found: " + workflowId);
            this.workflowId = workflowId;
        }

        public String getWorkflowId() {
            return workflowId;
        }
    }

    /**
     * Exception thrown for invalid workflow state transitions
     */
    public static class InvalidWorkflowStateException extends RuntimeException {
        private final String workflowId;
        private final AiWorkflowInstance.WorkflowStatus currentStatus;

        public InvalidWorkflowStateException(
            String workflowId,
            AiWorkflowInstance.WorkflowStatus currentStatus,
            String message
        ) {
            super(message);
            this.workflowId = workflowId;
            this.currentStatus = currentStatus;
        }

        public String getWorkflowId() {
            return workflowId;
        }

        public AiWorkflowInstance.WorkflowStatus getCurrentStatus() {
            return currentStatus;
        }
    }

    /**
     * Exception thrown for workflow execution errors
     */
    public static class WorkflowExecutionException extends RuntimeException {
        private final String workflowId;

        public WorkflowExecutionException(String workflowId, String message) {
            super(message);
            this.workflowId = workflowId;
        }

        public String getWorkflowId() {
            return workflowId;
        }
    }
}

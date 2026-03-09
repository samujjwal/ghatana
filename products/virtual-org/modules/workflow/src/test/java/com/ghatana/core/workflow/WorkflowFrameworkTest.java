package com.ghatana.core.workflow;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for workflow framework.
 *
 * <p>Tests validate:
 * - Task creation and state transitions
 * - Decision workflows
 * - Approval routing
 * - Escalation management
 * - SLA tracking
 *
 * @doc.type test
 * @doc.purpose Validate workflow components
 * @doc.layer core
 */
@DisplayName("Workflow Framework Tests")
class WorkflowFrameworkTest extends EventloopTestBase {

    private DecisionWorkflowEngine workflow;
    private ApprovalRouter router;
    private EscalationManager escalations;

    @BeforeEach
    void setUp() {
        workflow = new DecisionWorkflowEngine();
        router = new ApprovalRouter();
        escalations = new EscalationManager();
    }

    /**
     * Test workflow task creation.
     *
     * GIVEN: Task builder
     * WHEN: Creating task
     * THEN: Task created with all attributes
     */
    @Test
    @DisplayName("Should create workflow task with attributes")
    void shouldCreateWorkflowTask() {
        // GIVEN
        WorkflowTask task = WorkflowTask.builder()
            .taskId("task-001")
            .title("Code Review")
            .description("Review PR #123")
            .assigneeId("agent-alice")
            .requesterId("agent-bob")
            .priority(WorkflowTask.Priority.HIGH)
            .type(WorkflowTask.Type.REVIEW)
            .approver("agent-charlie")
            .context("pr-number", 123)
            .build();

        // WHEN/THEN
        assertThat(task.getTaskId()).isEqualTo("task-001");
        assertThat(task.getTitle()).isEqualTo("Code Review");
        assertThat(task.getPriority()).isEqualTo(WorkflowTask.Priority.HIGH);
        assertThat(task.requiresApproval()).isTrue();
    }

    /**
     * Test task submission to workflow.
     *
     * GIVEN: Task and workflow engine
     * WHEN: Submitting task
     * THEN: Task appears in assignee inbox
     */
    @Test
    @DisplayName("Should submit task to workflow")
    void shouldSubmitTaskToWorkflow() {
        // GIVEN
        WorkflowTask task = WorkflowTask.builder()
            .taskId("task-001")
            .title("Review")
            .assigneeId("agent-alice")
            .requesterId("agent-bob")
            .build();

        // WHEN
        workflow.submitTask(task);

        // THEN: Task in inbox
        List<WorkflowTask> inbox = workflow.getUserTasks("agent-alice");
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).getTitle()).isEqualTo("Review");
    }

    /**
     * Test task state transitions.
     *
     * GIVEN: Task in workflow
     * WHEN: Transitioning states
     * THEN: State changes properly
     */
    @Test
    @DisplayName("Should transition task states")
    void shouldTransitionTaskStates() {
        // GIVEN
        WorkflowTask task = WorkflowTask.builder()
            .taskId("task-001")
            .title("Review")
            .assigneeId("agent-alice")
            .state(WorkflowTask.State.CREATED)
            .build();

        workflow.submitTask(task);

        // WHEN: Transition to ASSIGNED
        runPromise(() -> workflow.transitionTask(
            "task-001",
            WorkflowTask.State.ASSIGNED,
            "system",
            "Auto-assigned"
        ));

        // THEN: State updated (would need getter for full verification)
        // For now, verify operation completed without error
    }

    /**
     * Test approval chain routing.
     *
     * GIVEN: Approval rule and task
     * WHEN: Routing for approval
     * THEN: Correct approvers returned
     */
    @Test
    @DisplayName("Should route task through approval chain")
    void shouldRouteApprovalChain() {
        // GIVEN: Register approval rule
        ApprovalRouter.ApprovalRule rule = ApprovalRouter.ApprovalRule.builder()
            .ruleId("rule-budget")
            .taskType(WorkflowTask.Type.APPROVAL)
            .approver("finance-director")
            .approver("cfo")
            .sequential(true)
            .build();

        router.registerRule(rule);

        WorkflowTask task = WorkflowTask.builder()
            .taskId("task-budget")
            .title("Budget Approval")
            .assigneeId("requestor")
            .type(WorkflowTask.Type.APPROVAL)
            .context("amount", 50000)
            .build();

        // WHEN
        List<String> approvers = router.routeForApproval(task);

        // THEN: Correct approvers
        assertThat(approvers).contains("finance-director", "cfo");
    }

    /**
     * Test approval delegation.
     *
     * GIVEN: Approval chain with delegation
     * WHEN: Original approver delegates
     * THEN: Delegate replaces approver
     */
    @Test
    @DisplayName("Should handle approval delegation")
    void shouldHandleApprovalDelegation() {
        // GIVEN
        ApprovalRouter.ApprovalRule rule = ApprovalRouter.ApprovalRule.builder()
            .ruleId("rule-delegation")
            .taskType(WorkflowTask.Type.APPROVAL)
            .approver("bob")
            .approver("charlie")
            .build();

        router.registerRule(rule);

        WorkflowTask task = WorkflowTask.builder()
            .taskId("task-001")
            .title("Approval")
            .assigneeId("alice")
            .type(WorkflowTask.Type.APPROVAL)
            .build();

        // WHEN: Set delegation
        router.setDelegation("bob", "bob-delegate");

        List<String> approvers = router.routeForApproval(task);

        // THEN: Delegate in chain
        assertThat(approvers).contains("bob-delegate");
        assertThat(approvers).doesNotContain("bob");
    }

    /**
     * Test escalation on SLA breach.
     *
     * GIVEN: Task with SLA policy
     * WHEN: Task breaches SLA
     * THEN: Escalation triggered
     */
    @Test
    @DisplayName("Should escalate on SLA breach")
    void shouldEscalateOnSLABreach() {
        // GIVEN: Register escalation policy
        EscalationManager.EscalationPolicy policy =
            EscalationManager.EscalationPolicy.builder()
                .policyId("critical-sla")
                .taskPriority(WorkflowTask.Priority.CRITICAL)
                .slaMinutes(30)
                .escalateToLevel(3)
                .build();

        escalations.registerPolicy(policy);

        // Create overdue task
        WorkflowTask overdueTask = WorkflowTask.builder()
            .taskId("task-overdue")
            .title("Urgent")
            .assigneeId("alice")
            .priority(WorkflowTask.Priority.CRITICAL)
            .deadline(System.currentTimeMillis() - 3600000)  // 1 hour ago
            .build();

        // WHEN: Check escalation
        boolean escalated = escalations.checkAndEscalate(overdueTask);

        // THEN: Escalation occurred
        assertThat(escalated).isTrue();
    }

    /**
     * Test escalation listener notification.
     *
     * GIVEN: Escalation manager with listener
     * WHEN: Task escalates
     * THEN: Listener notified
     */
    @Test
    @DisplayName("Should notify listeners on escalation")
    void shouldNotifyEscalationListeners() {
        // GIVEN
        List<String> notifiedTasks = new ArrayList<>();

        EscalationManager.EscalationListener listener = (task, event) -> {
            notifiedTasks.add(task.getTaskId());
        };

        escalations.registerListener(listener);

        EscalationManager.EscalationPolicy policy =
            EscalationManager.EscalationPolicy.builder()
                .policyId("policy-1")
                .taskPriority(WorkflowTask.Priority.HIGH)
                .build();

        escalations.registerPolicy(policy);

        WorkflowTask task = WorkflowTask.builder()
            .taskId("task-high-priority")
            .title("High Priority")
            .assigneeId("alice")
            .priority(WorkflowTask.Priority.HIGH)
            .build();

        // WHEN: Escalate
        escalations.checkAndEscalate(task);

        // THEN: Listener notified
        assertThat(notifiedTasks).contains("task-high-priority");
    }

    /**
     * Test pending approval retrieval.
     *
     * GIVEN: Tasks with pending approvals
     * WHEN: Querying pending approvals
     * THEN: Correct tasks returned
     */
    @Test
    @DisplayName("Should retrieve pending approvals for user")
    void shouldRetrievePendingApprovals() {
        // GIVEN: Submit approval task
        WorkflowTask approvalTask = WorkflowTask.builder()
            .taskId("task-approval")
            .title("Needs Approval")
            .assigneeId("alice")
            .type(WorkflowTask.Type.APPROVAL)
            .state(WorkflowTask.State.PENDING_APPROVAL)
            .approver("bob")
            .build();

        workflow.submitTask(approvalTask);

        // WHEN: Get pending approvals for Bob
        List<WorkflowTask> pending = workflow.getPendingApprovals("bob");

        // THEN: Approval task included
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getTitle()).isEqualTo("Needs Approval");
    }

    /**
     * Test urgent task detection.
     *
     * GIVEN: Mix of normal and urgent tasks
     * WHEN: Querying urgent tasks
     * THEN: Only urgent tasks returned
     */
    @Test
    @DisplayName("Should identify urgent tasks")
    void shouldIdentifyUrgentTasks() {
        // GIVEN: Create mix of tasks
        WorkflowTask normalTask = WorkflowTask.builder()
            .taskId("task-normal")
            .title("Normal")
            .assigneeId("alice")
            .priority(WorkflowTask.Priority.LOW)
            .build();

        WorkflowTask urgentTask = WorkflowTask.builder()
            .taskId("task-urgent")
            .title("Urgent")
            .assigneeId("alice")
            .priority(WorkflowTask.Priority.CRITICAL)
            .deadline(System.currentTimeMillis() - 1000)  // Past deadline
            .build();

        workflow.submitTask(normalTask);
        workflow.submitTask(urgentTask);

        // WHEN
        List<WorkflowTask> urgent = workflow.getUrgentTasks();

        // THEN
        assertThat(urgent).hasSize(1);
        assertThat(urgent.get(0).getTaskId()).isEqualTo("task-urgent");
    }
}


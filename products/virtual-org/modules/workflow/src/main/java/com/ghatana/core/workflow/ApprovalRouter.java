package com.ghatana.core.workflow;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes approvals through approval chains based on business rules.
 *
 * <p><b>Purpose</b><br>
 * Manages approval routing rules, determines next approver in chain,
 * handles delegation, and tracks approval progress through chains.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ApprovalRouter router = new ApprovalRouter();
 *
 * // Define approval rule
 * ApprovalRule budgetRule = ApprovalRule.builder()
 *     .ruleId("rule-budget")
 *     .taskType(WorkflowTask.Type.APPROVAL)
 *     .condition(task -> (int) task.getContext().get("amount") > 10000)
 *     .approvers(List.of("finance-director", "cfo"))
 *     .sequential(true)  // Sequential (not parallel)
 *     .build();
 *
 * router.registerRule(budgetRule);
 *
 * // Route for approval
 * List<String> approvers = router.routeForApproval(task);
 * }</pre>
 *
 * <p><b>Routing Modes</b><br>
 * - SEQUENTIAL: One approver at a time (a → b → c)
 * - PARALLEL: All approvers simultaneously
 * - ESCALATING: Higher level if rejected
 * - DELEGATED: Handle delegation to replacement
 *
 * @doc.type class
 * @doc.purpose Approval chain routing and rules
 * @doc.layer core
 * @doc.pattern Strategy, Chain of Responsibility
 */
public class ApprovalRouter {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalRouter.class);

    private final Map<String, ApprovalRule> rules;
    private final Map<String, String> delegations;  // delegateFrom -> delegateTo
    private final Map<String, ApprovalProgress> approvalProgress;

    /**
     * Create approval router.
     */
    public ApprovalRouter() {
        this.rules = new ConcurrentHashMap<>();
        this.delegations = new ConcurrentHashMap<>();
        this.approvalProgress = new ConcurrentHashMap<>();

        logger.debug("Created ApprovalRouter");
    }

    /**
     * Register approval rule.
     *
     * @param rule Rule to register
     */
    public void registerRule(ApprovalRule rule) {
        rules.put(rule.getRuleId(), rule);
        logger.info("Registered approval rule: {}", rule.getRuleId());
    }

    /**
     * Route task for approval.
     *
     * @param task Task to route
     * @return List of approvers in approval chain
     */
    public List<String> routeForApproval(WorkflowTask task) {
        // Find matching rule
        ApprovalRule matchingRule = null;
        for (ApprovalRule rule : rules.values()) {
            if (rule.matches(task)) {
                matchingRule = rule;
                break;
            }
        }

        if (matchingRule == null) {
            // Use task's built-in approvers
            return task.getApproverIds();
        }

        // Get approvers from rule
        List<String> approvers = new ArrayList<>(matchingRule.getApprovers());

        // Apply delegations
        approvers.replaceAll(a -> delegations.getOrDefault(a, a));

        logger.info("Routed task {} for approval: {}", task.getTaskId(), approvers);
        return approvers;
    }

    /**
     * Get next approver in chain.
     *
     * @param taskId Task identifier
     * @param approvers List of approvers
     * @return Next approver
     */
    public String getNextApprover(String taskId, List<String> approvers) {
        ApprovalProgress progress = approvalProgress.computeIfAbsent(
            taskId,
            k -> new ApprovalProgress(taskId, approvers)
        );

        return progress.getNextApprover();
    }

    /**
     * Record approval decision.
     *
     * @param taskId Task identifier
     * @param approverId Approver identifier
     * @param approved Approval decision
     */
    public void recordApprovalDecision(String taskId, String approverId, boolean approved) {
        ApprovalProgress progress = approvalProgress.get(taskId);
        if (progress != null) {
            progress.recordDecision(approverId, approved);
            logger.info("Recorded approval decision for {}: approved={}", taskId, approved);
        }
    }

    /**
     * Set approval delegation.
     *
     * @param delegateFrom Original approver
     * @param delegateTo Delegate (replacement)
     */
    public void setDelegation(String delegateFrom, String delegateTo) {
        delegations.put(delegateFrom, delegateTo);
        logger.info("Set delegation: {} → {}", delegateFrom, delegateTo);
    }

    /**
     * Remove approval delegation.
     *
     * @param delegateFrom Original approver
     */
    public void removeDelegation(String delegateFrom) {
        delegations.remove(delegateFrom);
        logger.info("Removed delegation for: {}", delegateFrom);
    }

    /**
     * Get approval progress.
     *
     * @param taskId Task identifier
     * @return Approval progress details
     */
    public Optional<ApprovalProgress> getApprovalProgress(String taskId) {
        return Optional.ofNullable(approvalProgress.get(taskId));
    }

    /**
     * Approval rule definition.
     */
    public static class ApprovalRule {
        private final String ruleId;
        private final WorkflowTask.Type taskType;
        private final ApprovalCondition condition;
        private final List<String> approvers;
        private final boolean sequential;
        private final int escalationLevel;

        private ApprovalRule(Builder builder) {
            this.ruleId = builder.ruleId;
            this.taskType = builder.taskType;
            this.condition = builder.condition;
            this.approvers = Collections.unmodifiableList(builder.approvers);
            this.sequential = builder.sequential;
            this.escalationLevel = builder.escalationLevel;
        }

        public String getRuleId() {
            return ruleId;
        }

        public List<String> getApprovers() {
            return approvers;
        }

        public boolean matches(WorkflowTask task) {
            return task.getType() == taskType && (condition == null || condition.evaluate(task));
        }

        public boolean isSequential() {
            return sequential;
        }

        public int getEscalationLevel() {
            return escalationLevel;
        }

        public static class Builder {
            private String ruleId;
            private WorkflowTask.Type taskType;
            private ApprovalCondition condition;
            private final List<String> approvers = new ArrayList<>();
            private boolean sequential = true;
            private int escalationLevel = 1;

            public Builder ruleId(String ruleId) {
                this.ruleId = ruleId;
                return this;
            }

            public Builder taskType(WorkflowTask.Type taskType) {
                this.taskType = taskType;
                return this;
            }

            public Builder condition(ApprovalCondition condition) {
                this.condition = condition;
                return this;
            }

            public Builder approver(String approverId) {
                this.approvers.add(approverId);
                return this;
            }

            public Builder approvers(Collection<String> approverIds) {
                this.approvers.addAll(approverIds);
                return this;
            }

            public Builder sequential(boolean sequential) {
                this.sequential = sequential;
                return this;
            }

            public Builder escalationLevel(int level) {
                this.escalationLevel = level;
                return this;
            }

            public ApprovalRule build() {
                if (ruleId == null || taskType == null || approvers.isEmpty()) {
                    throw new IllegalArgumentException("ruleId, taskType, and approvers are required");
                }
                return new ApprovalRule(this);
            }
        }

        public static Builder builder() {
            return new Builder();
        }
    }

    /**
     * Approval condition functional interface.
     */
    @FunctionalInterface
    public interface ApprovalCondition {
        boolean evaluate(WorkflowTask task);
    }

    /**
     * Approval progress tracker.
     */
    public static class ApprovalProgress {
        private final String taskId;
        private final List<String> approvers;
        private int currentIndex;
        private final List<ApprovalDecision> decisions;

        public ApprovalProgress(String taskId, List<String> approvers) {
            this.taskId = taskId;
            this.approvers = new ArrayList<>(approvers);
            this.currentIndex = 0;
            this.decisions = new ArrayList<>();
        }

        public String getNextApprover() {
            if (currentIndex < approvers.size()) {
                return approvers.get(currentIndex);
            }
            return null;
        }

        public void recordDecision(String approverId, boolean approved) {
            decisions.add(new ApprovalDecision(approverId, approved, System.currentTimeMillis()));

            if (approved) {
                currentIndex++;
            }
        }

        public int getApprovalPercentage() {
            if (approvers.isEmpty()) return 0;
            return (currentIndex * 100) / approvers.size();
        }

        public boolean isComplete() {
            return currentIndex >= approvers.size();
        }

        public List<ApprovalDecision> getDecisions() {
            return Collections.unmodifiableList(decisions);
        }

        public static class ApprovalDecision {
            private final String approverId;
            private final boolean approved;
            private final long timestamp;

            public ApprovalDecision(String approverId, boolean approved, long timestamp) {
                this.approverId = approverId;
                this.approved = approved;
                this.timestamp = timestamp;
            }

            public String getApproverId() {
                return approverId;
            }

            public boolean isApproved() {
                return approved;
            }

            public long getTimestamp() {
                return timestamp;
            }
        }
    }
}


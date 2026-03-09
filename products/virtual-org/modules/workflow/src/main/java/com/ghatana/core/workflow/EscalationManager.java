package com.ghatana.core.workflow;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages task escalation based on priorities, SLAs, and business rules.
 *
 * <p><b>Purpose</b><br>
 * Automatically escalates tasks when they breach SLA, have high priority,
 * or when explicitly requested. Routes to appropriate higher level.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EscalationManager escalations = new EscalationManager();
 *
 * // Define escalation policy
 * EscalationPolicy policy = EscalationPolicy.builder()
 *     .policyId("critical-sla")
 *     .taskPriority(WorkflowTask.Priority.CRITICAL)
 *     .slaMinutes(30)
 *     .escalateToLevel(3)  // Manager level
 *     .notifyManagement(true)
 *     .build();
 *
 * escalations.registerPolicy(policy);
 *
 * // Monitor and escalate
 * escalations.checkAndEscalate(task);
 * }</pre>
 *
 * <p><b>Escalation Triggers</b><br>
 * - SLA Breach: Task overdue
 * - Priority: High/Critical priority
 * - Explicit: Manual escalation request
 * - Deadlock: No progress for time period
 * - Complexity: Task exceeds capability
 *
 * @doc.type class
 * @doc.purpose Task escalation and SLA management
 * @doc.layer core
 * @doc.pattern Strategy, Observer
 */
public class EscalationManager {

    private static final Logger logger = LoggerFactory.getLogger(EscalationManager.class);

    private final Map<String, EscalationPolicy> policies;
    private final Map<String, EscalationEvent> escalationHistory;
    private final Map<String, Long> lastActivityTime;
    private final List<EscalationListener> listeners;

    /**
     * Create escalation manager.
     */
    public EscalationManager() {
        this.policies = new ConcurrentHashMap<>();
        this.escalationHistory = new ConcurrentHashMap<>();
        this.lastActivityTime = new ConcurrentHashMap<>();
        this.listeners = Collections.synchronizedList(new ArrayList<>());

        logger.debug("Created EscalationManager");
    }

    /**
     * Register escalation policy.
     *
     * @param policy Policy to register
     */
    public void registerPolicy(EscalationPolicy policy) {
        policies.put(policy.getPolicyId(), policy);
        logger.info("Registered escalation policy: {}", policy.getPolicyId());
    }

    /**
     * Check and escalate task if needed.
     *
     * @param task Task to check
     * @return Whether escalation occurred
     */
    public boolean checkAndEscalate(WorkflowTask task) {
        // Check each policy
        for (EscalationPolicy policy : policies.values()) {
            if (policy.shouldEscalate(task)) {
                escalateTask(task, policy);
                return true;
            }
        }

        return false;
    }

    /**
     * Escalate task immediately.
     *
     * @param task Task to escalate
     * @param policy Policy triggering escalation
     */
    private void escalateTask(WorkflowTask task, EscalationPolicy policy) {
        EscalationEvent event = new EscalationEvent(
            task.getTaskId(),
            policy.getPolicyId(),
            policy.getEscalateToLevel(),
            task.getState(),
            System.currentTimeMillis()
        );

        escalationHistory.put(task.getTaskId(), event);

        logger.warn("Escalating task {} - Policy: {} (Level: {})",
                   task.getTaskId(), policy.getPolicyId(), policy.getEscalateToLevel());

        notifyListeners(task, event);
    }

    /**
     * Record task activity (keeps track of inactivity).
     *
     * @param taskId Task identifier
     */
    public void recordActivity(String taskId) {
        lastActivityTime.put(taskId, System.currentTimeMillis());
    }

    /**
     * Check for inactive tasks (deadlock detection).
     *
     * @param inactivityMinutes Minutes of inactivity threshold
     * @return List of inactive tasks
     */
    public List<String> getInactiveTasks(long inactivityMinutes) {
        List<String> inactive = new ArrayList<>();
        long threshold = System.currentTimeMillis() - (inactivityMinutes * 60000);

        for (Map.Entry<String, Long> entry : lastActivityTime.entrySet()) {
            if (entry.getValue() < threshold) {
                inactive.add(entry.getKey());
            }
        }

        return inactive;
    }

    /**
     * Get escalation history for task.
     *
     * @param taskId Task identifier
     * @return Optional escalation event
     */
    public Optional<EscalationEvent> getEscalationHistory(String taskId) {
        return Optional.ofNullable(escalationHistory.get(taskId));
    }

    /**
     * Register escalation listener.
     *
     * @param listener Listener to register
     */
    public void registerListener(EscalationListener listener) {
        listeners.add(listener);
    }

    /**
     * Notify listeners of escalation.
     *
     * @param task Escalated task
     * @param event Escalation event
     */
    private void notifyListeners(WorkflowTask task, EscalationEvent event) {
        for (EscalationListener listener : listeners) {
            try {
                listener.onEscalation(task, event);
            } catch (Exception e) {
                logger.error("Error notifying escalation listener", e);
            }
        }
    }

    /**
     * Escalation policy definition.
     */
    public static class EscalationPolicy {
        private final String policyId;
        private final WorkflowTask.Priority triggerPriority;
        private final long slaBreachiMillis;
        private final int escalateToLevel;
        private final boolean notifyManagement;
        private final String escalationReason;

        private EscalationPolicy(Builder builder) {
            this.policyId = builder.policyId;
            this.triggerPriority = builder.triggerPriority;
            this.slaBreachiMillis = builder.slaMinutes * 60000;
            this.escalateToLevel = builder.escalateToLevel;
            this.notifyManagement = builder.notifyManagement;
            this.escalationReason = builder.escalationReason;
        }

        public String getPolicyId() {
            return policyId;
        }

        public int getEscalateToLevel() {
            return escalateToLevel;
        }

        public boolean shouldEscalate(WorkflowTask task) {
            // Check priority
            if (triggerPriority != null) {
                if (task.getPriority().ordinal() <= triggerPriority.ordinal()) {
                    return true;
                }
            }

            // Check SLA
            if (task.isOverdue()) {
                return true;
            }

            // Check urgency
            if (task.isUrgent()) {
                return true;
            }

            return false;
        }

        public static class Builder {
            private String policyId;
            private WorkflowTask.Priority triggerPriority;
            private long slaMinutes = 240;  // 4 hours default
            private int escalateToLevel = 2;
            private boolean notifyManagement = true;
            private String escalationReason;

            public Builder policyId(String policyId) {
                this.policyId = policyId;
                return this;
            }

            public Builder taskPriority(WorkflowTask.Priority priority) {
                this.triggerPriority = priority;
                return this;
            }

            public Builder slaMinutes(long slaMinutes) {
                this.slaMinutes = slaMinutes;
                return this;
            }

            public Builder escalateToLevel(int level) {
                this.escalateToLevel = level;
                return this;
            }

            public Builder notifyManagement(boolean notify) {
                this.notifyManagement = notify;
                return this;
            }

            public Builder escalationReason(String reason) {
                this.escalationReason = reason;
                return this;
            }

            public EscalationPolicy build() {
                if (policyId == null) {
                    throw new IllegalArgumentException("policyId is required");
                }
                return new EscalationPolicy(this);
            }
        }

        public static Builder builder() {
            return new Builder();
        }
    }

    /**
     * Escalation event record.
     */
    public static class EscalationEvent {
        private final String taskId;
        private final String policyId;
        private final int escalateToLevel;
        private final WorkflowTask.State taskState;
        private final long timestamp;

        public EscalationEvent(String taskId, String policyId, int escalateToLevel,
                             WorkflowTask.State taskState, long timestamp) {
            this.taskId = taskId;
            this.policyId = policyId;
            this.escalateToLevel = escalateToLevel;
            this.taskState = taskState;
            this.timestamp = timestamp;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getPolicyId() {
            return policyId;
        }

        public int getEscalateToLevel() {
            return escalateToLevel;
        }

        public WorkflowTask.State getTaskState() {
            return taskState;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Escalation listener interface.
     */
    @FunctionalInterface
    public interface EscalationListener {
        void onEscalation(WorkflowTask task, EscalationEvent event);
    }
}


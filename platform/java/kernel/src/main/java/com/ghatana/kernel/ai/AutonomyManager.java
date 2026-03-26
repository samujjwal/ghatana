package com.ghatana.kernel.ai;

import java.util.List;
import java.util.Map;

/**
 * Autonomy manager for controlling AI agent autonomy levels.
 *
 * <p>Manages autonomy levels, human-in-the-loop requirements, and
 * autonomous decision tracking for AI agents.</p>
 *
 * @doc.type interface
 * @doc.purpose AI agent autonomy management
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface AutonomyManager {

    /**
     * Configures autonomy level for an agent.
     *
     * @param agentId the agent identifier
     * @param level the autonomy level
     */
    void configureAutonomyLevel(String agentId, AutonomyLevel level);

    /**
     * Checks if human review is required.
     *
     * @param request the agent request
     * @param agent the agent
     * @return true if human review is required
     */
    boolean requiresHumanReview(AgentOrchestrator.AgentRequest request, AgentOrchestrator.KernelAgent agent);

    /**
     * Records an autonomous decision.
     *
     * @param decision the autonomous decision
     */
    void recordAutonomousDecision(AutonomousDecision decision);

    /**
     * Gets autonomous decisions for an agent.
     *
     * @param agentId the agent identifier
     * @param window the time window
     * @return list of autonomous decisions
     */
    List<AutonomousDecision> getAutonomousDecisions(String agentId, TimeWindow window);

    /**
     * Gets autonomy level for an agent.
     *
     * @param agentId the agent identifier
     * @return autonomy level
     */
    AutonomyLevel getAutonomyLevel(String agentId);

    /**
     * Approves a pending autonomous decision.
     *
     * @param decisionId the decision identifier
     * @param approver the approver identifier
     */
    void approveDecision(String decisionId, String approver);

    /**
     * Rejects a pending autonomous decision.
     *
     * @param decisionId the decision identifier
     * @param rejector the rejector identifier
     * @param reason the rejection reason
     */
    void rejectDecision(String decisionId, String rejector, String reason);

    /**
     * Autonomy level enumeration.
     */
    enum AutonomyLevel {
        NONE,           // No autonomy, always requires human approval
        LOW,            // Limited autonomy, requires approval for important decisions
        MEDIUM,         // Moderate autonomy, requires approval for critical decisions
        HIGH,           // High autonomy, rarely requires approval
        FULL            // Full autonomy, no approval required
    }

    /**
     * Represents an autonomous decision.
     */
    class AutonomousDecision {
        private final String decisionId;
        private final String agentId;
        private final AgentOrchestrator.AgentRequest request;
        private final Object result;
        private final boolean requiresReview;
        private final long timestamp;
        private final Map<String, Object> metadata;
        private DecisionStatus status;

        public AutonomousDecision(String agentId, AgentOrchestrator.AgentRequest request, 
                                 Object result, boolean requiresReview) {
            this.decisionId = java.util.UUID.randomUUID().toString();
            this.agentId = agentId;
            this.request = request;
            this.result = result;
            this.requiresReview = requiresReview;
            this.timestamp = System.currentTimeMillis();
            this.metadata = new java.util.HashMap<>();
            this.status = requiresReview ? DecisionStatus.PENDING_REVIEW : DecisionStatus.APPROVED;
        }

        public String getDecisionId() { return decisionId; }
        public String getAgentId() { return agentId; }
        public AgentOrchestrator.AgentRequest getRequest() { return request; }
        public Object getResult() { return result; }
        public boolean requiresReview() { return requiresReview; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Object> getMetadata() { return metadata; }
        public DecisionStatus getStatus() { return status; }

        public void setStatus(DecisionStatus status) {
            this.status = status;
        }
    }

    /**
     * Decision status enumeration.
     */
    enum DecisionStatus {
        PENDING_REVIEW,
        APPROVED,
        REJECTED,
        EXECUTED
    }

    /**
     * Time window for querying decisions.
     */
    class TimeWindow {
        private final long startTime;
        private final long endTime;

        public TimeWindow(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }

        public static TimeWindow last24Hours() {
            long now = System.currentTimeMillis();
            return new TimeWindow(now - 24 * 60 * 60 * 1000, now);
        }

        public static TimeWindow lastWeek() {
            long now = System.currentTimeMillis();
            return new TimeWindow(now - 7 * 24 * 60 * 60 * 1000, now);
        }
    }
}

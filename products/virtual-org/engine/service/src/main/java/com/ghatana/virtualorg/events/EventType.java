package com.ghatana.virtualorg.events;

/**
 * Standard event types for Virtual Organization system.
 * 
 * <p>This enum defines the canonical event taxonomy for agent-to-agent
 * communication, workflow orchestration, and organizational operations.
 * 
 * <p><b>Event Categories:</b>
 * <ul>
 *   <li><b>Agent Lifecycle:</b> Started, stopped, health changes</li>
 *   <li><b>Decisions:</b> Made, approved, rejected, escalated</li>
 *   <li><b>Tasks:</b> Created, assigned, completed, failed</li>
 *   <li><b>Workflows:</b> Started, step completed, failed, completed</li>
 *   <li><b>Collaboration:</b> Code reviews, meetings, knowledge sharing</li>
 *   <li><b>Memory:</b> Stored, retrieved, lessons learned</li>
 *   <li><b>Human-in-Loop:</b> Approval requested, provided, rejected</li>
 * </ul>
 * 
 * <p>Per Virtual_Org_BestOfClass_Consolidated_Plan.md Section 2.4
 * (Event Taxonomy) and PHASE2_IMPLEMENTATION_ROADMAP.md (Event-Driven Architecture).
 * 
 * @doc.type enum
 * @doc.purpose Standard event type constants for agent communication
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum EventType {
    
    // ==================== Agent Lifecycle Events ====================
    
    /**
     * Agent has started and is ready to receive tasks.
     * Payload: agentId, role, capabilities
     */
    AGENT_STARTED("agent.started"),
    
    /**
     * Agent is stopping gracefully.
     * Payload: agentId, reason
     */
    AGENT_STOPPING("agent.stopping"),
    
    /**
     * Agent has stopped.
     * Payload: agentId, finalState
     */
    AGENT_STOPPED("agent.stopped"),
    
    /**
     * Agent health check result.
     * Payload: agentId, status, metrics
     */
    AGENT_HEALTH_CHECK("agent.health.check"),
    
    // ==================== Decision Events ====================
    
    /**
     * Agent has made a decision within its authority.
     * Payload: decisionId, agentId, decision, reasoning, impact
     */
    DECISION_MADE("agent.decision.made"),
    
    /**
     * Decision has been approved (by higher authority).
     * Payload: decisionId, approverId, timestamp
     */
    DECISION_APPROVED("agent.decision.approved"),
    
    /**
     * Decision has been rejected.
     * Payload: decisionId, rejecterId, reason
     */
    DECISION_REJECTED("agent.decision.rejected"),
    
    /**
     * Decision escalated to higher authority.
     * Payload: decisionId, fromAgentId, toAgentId, reason
     */
    DECISION_ESCALATED("agent.decision.escalated"),
    
    // ==================== Task Events ====================
    
    /**
     * New task created.
     * Payload: taskId, title, description, priority, estimatedEffort
     */
    TASK_CREATED("task.created"),
    
    /**
     * Task assigned to an agent.
     * Payload: taskId, assignedTo, assignedBy, deadline
     */
    TASK_ASSIGNED("task.assigned"),
    
    /**
     * Task started by agent.
     * Payload: taskId, agentId, startTime
     */
    TASK_STARTED("task.started"),
    
    /**
     * Task completed successfully.
     * Payload: taskId, agentId, result, completionTime
     */
    TASK_COMPLETED("task.completed"),
    
    /**
     * Task failed with error.
     * Payload: taskId, agentId, error, retryable
     */
    TASK_FAILED("task.failed"),
    
    /**
     * Task blocked waiting for dependency.
     * Payload: taskId, blockedBy, reason
     */
    TASK_BLOCKED("task.blocked"),
    
    // ==================== Workflow Events ====================
    
    /**
     * Workflow instance started.
     * Payload: workflowId, workflowType, initiator, context
     */
    WORKFLOW_STARTED("workflow.started"),
    
    /**
     * Workflow step completed.
     * Payload: workflowId, stepId, stepName, output
     */
    WORKFLOW_STEP_COMPLETED("workflow.step.completed"),
    
    /**
     * Workflow failed.
     * Payload: workflowId, failedStep, error, compensationTriggered
     */
    WORKFLOW_FAILED("workflow.failed"),
    
    /**
     * Workflow completed successfully.
     * Payload: workflowId, outcome, duration
     */
    WORKFLOW_COMPLETED("workflow.completed"),
    
    // ==================== Collaboration Events ====================
    
    /**
     * Code review requested.
     * Payload: reviewId, pullRequestId, author, reviewers
     */
    CODE_REVIEW_REQUESTED("collaboration.code_review.requested"),
    
    /**
     * Code review completed.
     * Payload: reviewId, reviewer, approved, comments
     */
    CODE_REVIEW_COMPLETED("collaboration.code_review.completed"),
    
    /**
     * Meeting scheduled.
     * Payload: meetingId, type, participants, agenda, scheduledTime
     */
    MEETING_SCHEDULED("collaboration.meeting.scheduled"),
    
    /**
     * Meeting started.
     * Payload: meetingId, participants
     */
    MEETING_STARTED("collaboration.meeting.started"),
    
    /**
     * Meeting completed.
     * Payload: meetingId, duration, outcomes, actionItems
     */
    MEETING_COMPLETED("collaboration.meeting.completed"),
    
    /**
     * Knowledge shared (lesson learned, best practice).
     * Payload: knowledgeId, category, content, author, tags
     */
    KNOWLEDGE_SHARED("collaboration.knowledge.shared"),
    
    // ==================== Memory Events ====================
    
    /**
     * Memory stored (episodic or semantic).
     * Payload: memoryId, type, content, embedding, metadata
     */
    MEMORY_STORED("memory.stored"),
    
    /**
     * Memory retrieved.
     * Payload: memoryId, query, relevanceScore
     */
    MEMORY_RETRIEVED("memory.retrieved"),
    
    /**
     * Lesson learned captured.
     * Payload: lessonId, category, what, why, nextTime
     */
    LESSON_LEARNED("memory.lesson.learned"),
    
    // ==================== Human-in-Loop Events ====================
    
    /**
     * Human approval requested.
     * Payload: approvalId, requestType, details, urgency, requester
     */
    APPROVAL_REQUESTED("human.approval.requested"),
    
    /**
     * Human approval provided.
     * Payload: approvalId, approver, approved, comments
     */
    APPROVAL_PROVIDED("human.approval.provided"),
    
    /**
     * Human feedback provided.
     * Payload: feedbackId, context, feedback, sentiment
     */
    HUMAN_FEEDBACK("human.feedback.provided"),
    
    // ==================== Sprint & Planning Events ====================
    
    /**
     * Sprint planning started.
     * Payload: sprintId, participants, backlogItems
     */
    SPRINT_PLANNING_STARTED("sprint.planning.started"),
    
    /**
     * Sprint started.
     * Payload: sprintId, goals, committedStories, duration
     */
    SPRINT_STARTED("sprint.started"),
    
    /**
     * Sprint completed.
     * Payload: sprintId, completedStories, velocity, retrospectiveScheduled
     */
    SPRINT_COMPLETED("sprint.completed"),
    
    /**
     * Daily standup completed.
     * Payload: standupId, participants, updates, blockers
     */
    STANDUP_COMPLETED("standup.completed"),
    
    /**
     * Retrospective completed.
     * Payload: retroId, went_well, improvements, action_items
     */
    RETROSPECTIVE_COMPLETED("retrospective.completed"),
    
    // ==================== Deployment & Operations ====================
    
    /**
     * Deployment requested.
     * Payload: deploymentId, environment, version, artifacts
     */
    DEPLOYMENT_REQUESTED("deployment.requested"),
    
    /**
     * Deployment started.
     * Payload: deploymentId, startTime, performer
     */
    DEPLOYMENT_STARTED("deployment.started"),
    
    /**
     * Deployment completed.
     * Payload: deploymentId, success, duration, metricsSnapshot
     */
    DEPLOYMENT_COMPLETED("deployment.completed"),
    
    /**
     * Deployment rolled back.
     * Payload: deploymentId, reason, rollbackTime
     */
    DEPLOYMENT_ROLLED_BACK("deployment.rolled_back"),
    
    // ==================== Error & Alert Events ====================
    
    /**
     * Error occurred in agent processing.
     * Payload: errorId, agentId, errorType, message, stackTrace
     */
    ERROR_OCCURRED("error.occurred"),
    
    /**
     * Alert triggered (SLO breach, threshold exceeded).
     * Payload: alertId, severity, metric, threshold, currentValue
     */
    ALERT_TRIGGERED("alert.triggered");
    
    private final String eventTypeName;
    
    EventType(String eventTypeName) {
        this.eventTypeName = eventTypeName;
    }
    
    /**
     * Returns the canonical event type string.
     * 
     * @return Event type string (e.g., "agent.started")
     */
    public String getEventTypeName() {
        return eventTypeName;
    }
    
    /**
     * Parses event type string to enum value.
     * 
     * @param eventTypeName Event type string
     * @return Matching EventType enum
     * @throws IllegalArgumentException if no matching event type found
     */
    public static EventType fromString(String eventTypeName) {
        for (EventType type : values()) {
            if (type.eventTypeName.equals(eventTypeName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown event type: " + eventTypeName);
    }
    
    @Override
    public String toString() {
        return eventTypeName;
    }
}

package com.ghatana.virtualorg.framework.hitl;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Audit trail interface for tracking agent actions.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides comprehensive logging and tracking of all agent activities: - Tool
 * executions - Decisions made - Human approvals - State changes
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AuditTrail audit = new InMemoryAuditTrail();
 *
 * audit.recordToolExecution(
 *     "agent-1",
 *     "github.create_pr",
 *     Map.of("title", "Fix bug"),
 *     ToolResult.success(...)
 * );
 *
 * List<AuditEntry> entries = audit.getEntriesForAgent("agent-1").getResult();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Track agent actions for compliance
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface AuditTrail {

    /**
     * Records a tool execution event.
     *
     * @param agentId ID of the agent
     * @param toolName Name of the tool executed
     * @param input Tool input parameters
     * @param output Tool output/result
     * @return Promise of the created audit entry
     */
    Promise<AuditEntry> recordToolExecution(
            String agentId,
            String toolName,
            Map<String, Object> input,
            Map<String, Object> output
    );

    /**
     * Records an agent decision event.
     *
     * @param agentId ID of the agent
     * @param decision Decision description
     * @param reasoning Reasoning behind the decision
     * @param confidence Confidence score
     * @return Promise of the created audit entry
     */
    Promise<AuditEntry> recordDecision(
            String agentId,
            String decision,
            String reasoning,
            double confidence
    );

    /**
     * Records a human approval event.
     *
     * @param agentId ID of the agent
     * @param requestId Approval request ID
     * @param action Action that was approved/rejected
     * @param approved Whether it was approved
     * @param reviewer ID of the human reviewer
     * @param comments Optional reviewer comments
     * @return Promise of the created audit entry
     */
    Promise<AuditEntry> recordApproval(
            String agentId,
            String requestId,
            String action,
            boolean approved,
            String reviewer,
            String comments
    );

    /**
     * Records a state change event.
     *
     * @param agentId ID of the agent
     * @param oldState Previous state
     * @param newState New state
     * @param reason Reason for the change
     * @return Promise of the created audit entry
     */
    Promise<AuditEntry> recordStateChange(
            String agentId,
            String oldState,
            String newState,
            String reason
    );

    /**
     * Records an error event.
     *
     * @param agentId ID of the agent
     * @param errorType Type of error
     * @param errorMessage Error message
     * @param context Additional context
     * @return Promise of the created audit entry
     */
    Promise<AuditEntry> recordError(
            String agentId,
            String errorType,
            String errorMessage,
            Map<String, Object> context
    );

    /**
     * Records a custom event.
     *
     * @param agentId ID of the agent
     * @param eventType Custom event type
     * @param data Event data
     * @return Promise of the created audit entry
     */
    Promise<AuditEntry> recordEvent(
            String agentId,
            String eventType,
            Map<String, Object> data
    );

    /**
     * Retrieves audit entries for a specific agent.
     *
     * @param agentId ID of the agent
     * @return Promise of matching audit entries
     */
    Promise<List<AuditEntry>> getEntriesForAgent(String agentId);

    /**
     * Retrieves audit entries within a time range.
     *
     * @param startTime Start of the range
     * @param endTime End of the range
     * @return Promise of matching audit entries
     */
    Promise<List<AuditEntry>> getEntriesByTimeRange(Instant startTime, Instant endTime);

    /**
     * Retrieves audit entries by event type.
     *
     * @param eventType Type of event to filter by
     * @return Promise of matching audit entries
     */
    Promise<List<AuditEntry>> getEntriesByType(String eventType);

    /**
     * Queries audit entries with filters.
     *
     * @param query Query parameters
     * @return Promise of matching audit entries
     */
    Promise<List<AuditEntry>> query(AuditQuery query);
}

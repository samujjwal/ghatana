package com.ghatana.virtualorg.framework.hitl;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an entry in the audit trail.
 *
 * <p>
 * <b>Purpose</b><br>
 * Immutable record of an agent action or event for compliance and debugging
 * purposes.
 *
 * @doc.type record
 * @doc.purpose Audit trail entry
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AuditEntry(
        String id,
        String agentId,
        String eventType,
        Instant timestamp,
        Map<String, Object> data,
        String correlationId,
        String sessionId
        ) {

    /**
     * Event types for audit entries.
     */
    public static final class EventTypes {

        public static final String TOOL_EXECUTION = "tool.execution";
        public static final String DECISION = "decision";
        public static final String APPROVAL = "approval";
        public static final String STATE_CHANGE = "state.change";
        public static final String ERROR = "error";
        public static final String CUSTOM = "custom";

        private EventTypes() {
        }
    }

    /**
     * Creates a new audit entry builder.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an audit entry for tool execution.
     *
     * @param agentId ID of the agent
     * @param toolName Name of the tool
     * @param input Tool input
     * @param output Tool output
     * @return Audit entry
     */
    public static AuditEntry toolExecution(String agentId, String toolName,
            Map<String, Object> input,
            Map<String, Object> output) {
        return builder()
                .agentId(agentId)
                .eventType(EventTypes.TOOL_EXECUTION)
                .data(Map.of(
                        "tool", toolName,
                        "input", input,
                        "output", output
                ))
                .build();
    }

    /**
     * Creates an audit entry for a decision.
     *
     * @param agentId ID of the agent
     * @param decision Decision description
     * @param reasoning Reasoning behind the decision
     * @param confidence Confidence score
     * @return Audit entry
     */
    public static AuditEntry decision(String agentId, String decision,
            String reasoning, double confidence) {
        return builder()
                .agentId(agentId)
                .eventType(EventTypes.DECISION)
                .data(Map.of(
                        "decision", decision,
                        "reasoning", reasoning,
                        "confidence", confidence
                ))
                .build();
    }

    /**
     * Creates an audit entry for an approval event.
     *
     * @param agentId ID of the agent
     * @param requestId Approval request ID
     * @param action Action that was approved/rejected
     * @param approved Whether it was approved
     * @param reviewer ID of the reviewer
     * @param comments Reviewer comments
     * @return Audit entry
     */
    public static AuditEntry approval(String agentId, String requestId,
            String action, boolean approved,
            String reviewer, String comments) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("request_id", requestId);
        data.put("action", action);
        data.put("approved", approved);
        data.put("reviewer", reviewer);
        if (comments != null) {
            data.put("comments", comments);
        }

        return builder()
                .agentId(agentId)
                .eventType(EventTypes.APPROVAL)
                .data(data)
                .build();
    }

    /**
     * Creates an audit entry for a state change.
     *
     * @param agentId ID of the agent
     * @param oldState Previous state
     * @param newState New state
     * @param reason Reason for change
     * @return Audit entry
     */
    public static AuditEntry stateChange(String agentId, String oldState,
            String newState, String reason) {
        return builder()
                .agentId(agentId)
                .eventType(EventTypes.STATE_CHANGE)
                .data(Map.of(
                        "old_state", oldState,
                        "new_state", newState,
                        "reason", reason != null ? reason : ""
                ))
                .build();
    }

    /**
     * Creates an audit entry for an error.
     *
     * @param agentId ID of the agent
     * @param errorType Type of error
     * @param errorMessage Error message
     * @param context Additional context
     * @return Audit entry
     */
    public static AuditEntry error(String agentId, String errorType,
            String errorMessage, Map<String, Object> context) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("error_type", errorType);
        data.put("error_message", errorMessage);
        if (context != null) {
            data.put("context", context);
        }

        return builder()
                .agentId(agentId)
                .eventType(EventTypes.ERROR)
                .data(data)
                .build();
    }

    /**
     * Builder for creating audit entries.
     */
    public static final class Builder {

        private String id;
        private String agentId;
        private String eventType;
        private Instant timestamp;
        private Map<String, Object> data;
        private String correlationId;
        private String sessionId;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public AuditEntry build() {
            return new AuditEntry(
                    id != null ? id : UUID.randomUUID().toString(),
                    agentId,
                    eventType,
                    timestamp != null ? timestamp : Instant.now(),
                    data != null ? Map.copyOf(data) : Map.of(),
                    correlationId,
                    sessionId
            );
        }
    }
}

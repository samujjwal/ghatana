package com.ghatana.virtualorg.framework.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an agent's decision after reasoning about a task.
 *
 * <p>
 * <b>Purpose</b><br>
 * Captures the output of the agent's thinking phase, including: - Reasoning
 * process (chain of thought) - Decision made - Action to take (if any) -
 * Confidence level
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentDecision decision = AgentDecision.builder()
 *     .reasoning("The PR has test failures. Need to request changes.")
 *     .decision("Request changes on the PR")
 *     .action(AgentAction.of("github_review_pr", Map.of(
 *         "pr_number", 123,
 *         "event", "REQUEST_CHANGES",
 *         "body", "Please fix the failing tests."
 *     )))
 *     .confidence(0.92)
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Agent decision output from reasoning
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class AgentDecision {

    private final String reasoning;
    private final String decision;
    private final AgentAction action;
    private final double confidence;
    private final List<String> alternativeActions;
    private final Map<String, Object> metadata;

    private AgentDecision(Builder builder) {
        this.reasoning = Objects.requireNonNull(builder.reasoning, "reasoning required");
        this.decision = Objects.requireNonNull(builder.decision, "decision required");
        this.action = builder.action;
        this.confidence = builder.confidence;
        this.alternativeActions = builder.alternativeActions != null
                ? List.copyOf(builder.alternativeActions) : List.of();
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }

    public String getReasoning() {
        return reasoning;
    }

    public String getDecision() {
        return decision;
    }

    public AgentAction getAction() {
        return action;
    }

    public double getConfidence() {
        return confidence;
    }

    public List<String> getAlternativeActions() {
        return alternativeActions;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Checks if this decision includes an action to execute.
     *
     * @return true if an action is specified
     */
    public boolean hasAction() {
        return action != null;
    }

    /**
     * Checks if confidence is above a threshold.
     *
     * @param threshold The minimum confidence threshold
     * @return true if confidence >= threshold
     */
    public boolean isConfidentAbove(double threshold) {
        return confidence >= threshold;
    }

    /**
     * Creates a "no action needed" decision.
     *
     * @param reasoning The reasoning for no action
     * @return A decision with no action
     */
    public static AgentDecision noAction(String reasoning) {
        return builder()
                .reasoning(reasoning)
                .decision("No action needed")
                .confidence(1.0)
                .build();
    }

    /**
     * Creates an "escalate" decision.
     *
     * @param reasoning The reasoning for escalation
     * @param reason The escalation reason
     * @return An escalation decision
     */
    public static AgentDecision escalate(String reasoning, String reason) {
        return builder()
                .reasoning(reasoning)
                .decision("Escalate to supervisor")
                .action(AgentAction.escalate(reason))
                .confidence(0.5)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Represents an action the agent wants to take.
     */
    public static final class AgentAction {

        private final String toolName;
        private final Map<String, Object> parameters;
        private final ActionType type;

        public enum ActionType {
            TOOL_CALL,
            DELEGATE,
            ESCALATE,
            COMMUNICATE,
            WAIT
        }

        private AgentAction(String toolName, Map<String, Object> parameters, ActionType type) {
            this.toolName = toolName;
            this.parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
            this.type = type;
        }

        /**
         * Creates a tool call action.
         */
        public static AgentAction of(String toolName, Map<String, Object> parameters) {
            return new AgentAction(toolName, parameters, ActionType.TOOL_CALL);
        }

        /**
         * Creates an escalation action.
         */
        public static AgentAction escalate(String reason) {
            return new AgentAction(null, Map.of("reason", reason), ActionType.ESCALATE);
        }

        /**
         * Creates a delegation action.
         */
        public static AgentAction delegate(String targetAgent, Map<String, Object> taskDetails) {
            Map<String, Object> params = new java.util.HashMap<>(taskDetails);
            params.put("targetAgent", targetAgent);
            return new AgentAction(null, params, ActionType.DELEGATE);
        }

        /**
         * Creates a wait action (for HITL or external input).
         */
        public static AgentAction waitFor(String waitReason) {
            return new AgentAction(null, Map.of("reason", waitReason), ActionType.WAIT);
        }

        public String getToolName() {
            return toolName;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public ActionType getType() {
            return type;
        }

        public boolean isToolCall() {
            return type == ActionType.TOOL_CALL;
        }

        @Override
        public String toString() {
            return "AgentAction{"
                    + "type=" + type
                    + ", toolName='" + toolName + '\''
                    + ", parameters=" + parameters
                    + '}';
        }
    }

    public static final class Builder {

        private String reasoning;
        private String decision;
        private AgentAction action;
        private double confidence = 0.5;
        private List<String> alternativeActions;
        private Map<String, Object> metadata;

        private Builder() {
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder decision(String decision) {
            this.decision = decision;
            return this;
        }

        public Builder action(AgentAction action) {
            this.action = action;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public Builder alternativeActions(List<String> alternatives) {
            this.alternativeActions = alternatives;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public AgentDecision build() {
            return new AgentDecision(this);
        }
    }

    @Override
    public String toString() {
        return "AgentDecision{"
                + "decision='" + decision + '\''
                + ", confidence=" + confidence
                + ", hasAction=" + hasAction()
                + '}';
    }
}

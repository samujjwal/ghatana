package com.ghatana.products.yappc.domain.agent;

/**
 * Registry of all AI agent names.
 * <p>
 * Each agent has a specific responsibility and latency SLA.
 *
 * @doc.type enum
 * @doc.purpose Agent name registry
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum AgentName {

    /**
     * Conversational AI for command interpretation and assistance.
     * Latency SLA: 2000ms (streaming)
     */
    COPILOT_AGENT("CopilotAgent", 2000L),

    /**
     * Natural language query parsing and intent detection.
     * Latency SLA: 300ms
     */
    QUERY_PARSER_AGENT("QueryParserAgent", 300L),

    /**
     * Timeline forecasting, risk scoring, and phase prediction.
     * Latency SLA: 500ms
     */
    PREDICTION_AGENT("PredictionAgent", 500L),

    /**
     * Real-time anomaly detection in metrics and patterns.
     * Latency SLA: 200ms
     */
    ANOMALY_DETECTOR_AGENT("AnomalyDetectorAgent", 200L),

    /**
     * Code scaffolding, test generation, and boilerplate creation.
     * Latency SLA: 5000ms
     */
    CODE_GENERATOR_AGENT("CodeGeneratorAgent", 5000L),

    /**
     * Comment sentiment analysis and mood tracking.
     * Latency SLA: 100ms
     */
    SENTIMENT_AGENT("SentimentAgent", 100L),

    /**
     * Smart suggestions, next actions, and recommendations.
     * Latency SLA: 400ms
     */
    RECOMMENDATION_AGENT("RecommendationAgent", 400L),

    /**
     * Semantic vector search and similarity matching.
     * Latency SLA: 300ms
     */
    SEARCH_AGENT("SearchAgent", 300L),

    /**
     * Intelligent task assignment and escalation routing.
     * Latency SLA: 200ms
     */
    WORKFLOW_ROUTER_AGENT("WorkflowRouterAgent", 200L),

    /**
     * Auto-documentation, release notes, and summaries.
     * Latency SLA: 3000ms
     */
    DOC_GENERATOR_AGENT("DocGeneratorAgent", 3000L);

    private final String displayName;
    private final long latencySLA;

    AgentName(String displayName, long latencySLA) {
        this.displayName = displayName;
        this.latencySLA = latencySLA;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getLatencySLA() {
        return latencySLA;
    }

    /**
     * Finds an agent by display name.
     *
     * @param displayName The display name to search for
     * @return The matching agent name
     * @throws IllegalArgumentException if no match found
     */
    public static AgentName fromDisplayName(String displayName) {
        for (AgentName agent : values()) {
            if (agent.displayName.equals(displayName)) {
                return agent;
            }
        }
        throw new IllegalArgumentException("Unknown agent name: " + displayName);
    }
}

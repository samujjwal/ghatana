package com.ghatana.orchestrator;

import java.time.Instant;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Provides orchestrator dlq event functionality.
 * @doc.layer product
 * @doc.pattern Event
 */
public class OrchestratorDlqEvent {
    public String agentId;
    public String error;
    public Map<String, String> context;
    public String timestamp = Instant.now().toString();

    public OrchestratorDlqEvent(String agentId, String error, Map<String, String> context) {
        this.agentId = agentId;
        this.error = error;
        this.context = context;
    }
}

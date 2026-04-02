package com.ghatana.aep.engine.registry;

import io.activej.promise.Promise;

/**
 * Agent execution service for AEP.
 *
 * @doc.type class
 * @doc.purpose Executes agents and returns results
 * @doc.layer product
 * @doc.pattern Service
 */
public class AgentExecutionService {

    public AgentExecutionService() {
        // Stub
    }

    public Promise<Object> executeAgent(String agentId, Object input) {
        return Promise.of(input);
    }
}

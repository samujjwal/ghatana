package com.ghatana.aep.engine.registry;

import io.activej.promise.Promise;

import java.util.List;

/**
 * Persistent store for agent execution records.
 *
 * @doc.type interface
 * @doc.purpose Persist and query agent execution history records
 * @doc.layer product
 * @doc.pattern SPI
 */
public interface AgentExecutionHistoryStore {

    Promise<Void> append(String agentId, AgentExecutionService.ExecutionRecord record);

    Promise<List<AgentExecutionService.ExecutionRecord>> getHistory(String agentId, int limit);
}

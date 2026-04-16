package com.ghatana.aep.engine.registry;

import io.activej.promise.Promise;

import java.util.List;

/**
 * No-op history store used when execution history persistence is not configured.
 *
 * @doc.type class
 * @doc.purpose Safe no-op implementation of AgentExecutionHistoryStore
 * @doc.layer product
 * @doc.pattern Null Object
 */
public final class NoopAgentExecutionHistoryStore implements AgentExecutionHistoryStore {

    @Override
    public Promise<Void> append(String agentId, AgentExecutionService.ExecutionRecord record) {
        return Promise.complete();
    }

    @Override
    public Promise<List<AgentExecutionService.ExecutionRecord>> getHistory(String agentId, int limit) {
        return Promise.of(List.of());
    }
}

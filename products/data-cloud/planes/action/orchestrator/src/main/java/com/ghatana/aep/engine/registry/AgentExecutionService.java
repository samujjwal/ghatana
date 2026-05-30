package com.ghatana.aep.engine.registry;

import com.ghatana.agent.spi.AgentRegistry;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agent execution service for AEP.
 *
 * <p>Resolves the requested agent from {@link AgentRegistry} and dispatches
 * it through the governed {@link AgentDispatcher} with safety pipeline.
 *
 * <p><b>Governance Requirement:</b> All agent executions must go through the
 * governed dispatcher. Direct LLM gateway bypass is not permitted in production.
 * This service enforces the governed path by requiring a governed dispatcher
 * and removing any direct LLM gateway access.
 *
 * @doc.type class
 * @doc.purpose Executes agents via governed dispatcher with safety pipeline
 * @doc.layer product
 * @doc.pattern Service
 */
public class AgentExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);

    private final AgentRegistry agentRegistry;
    private final AgentDispatcher agentDispatcher;
    private final AgentExecutionHistoryStore historyStore;
    private final AgentMemoryPlaneClient memoryClient;

    /**
     * @param agentRegistry    registry used to look up registered agents; never {@code null}
     * @param agentDispatcher  governed agent dispatcher with safety pipeline; never {@code null}
     * @param historyStore     persistent execution history store; never {@code null}
     * @param memoryClient     memory plane client used to materialize episodic state; never {@code null}
     */
    public AgentExecutionService(
            AgentRegistry agentRegistry,
            AgentDispatcher agentDispatcher,
            AgentExecutionHistoryStore historyStore,
            AgentMemoryPlaneClient memoryClient) {
        this.agentRegistry    = Objects.requireNonNull(agentRegistry, "agentRegistry");
        this.agentDispatcher = Objects.requireNonNull(agentDispatcher, "agentDispatcher");
        this.historyStore     = Objects.requireNonNull(historyStore,  "historyStore");
        this.memoryClient     = Objects.requireNonNull(memoryClient,  "memoryClient");

        log.info("[governance] AgentExecutionService initialized with governed dispatcher - no bypass path available");
    }

    /**
     * Executes an agent by ID with a raw input object.
     *
     * @param agentId agent identifier in the registry
     * @param input   raw input (converted to a string prompt)
     * @return promise resolving to the execution output
     */
    public Promise<Object> executeAgent(String agentId, Object input) {
        return execute(agentId, input).map(result -> result.output());
    }

    /**
     * Executes an agent and returns a structured {@link ExecutionResult}.
     *
     * <p>All executions go through the governed {@link AgentDispatcher} with
     * safety pipeline, policy checks, and evidence collection. No bypass path exists.
     */
    public Promise<ExecutionResult> execute(String agentId, Object input) {
        Objects.requireNonNull(agentId, "agentId");

        long startMs = System.currentTimeMillis();
        String executionId = UUID.randomUUID().toString();

        log.debug("[execution] Using governed AgentDispatcher: agentId={} executionId={}", agentId, executionId);

        // Use empty context and derive a child context for this execution
        AgentContext baseCtx = AgentContext.empty();
        AgentContext ctx = baseCtx.toBuilder()
            .agentId(agentId)
            .turnId(executionId)
            .build();

        return agentDispatcher.dispatch(agentId, input, ctx).then(result -> {
            long durationMs = System.currentTimeMillis() - startMs;
            log.info("[execution] Completed via governed dispatcher: agentId={} executionId={} durationMs={}",
                agentId, executionId, durationMs);

            ExecutionRecord record = new ExecutionRecord(
                executionId,
                "success",
                input,
                result.getOutput(),
                durationMs,
                Instant.now().toString());

            return historyStore.append(agentId, record)
                .then(ignored -> memoryClient.recordExecution(agentId, executionId, input, result.getOutput(), durationMs))
                .map(ignored -> new ExecutionResult(executionId, "success", result.getOutput(), durationMs));
        }).mapException(e -> {
            log.error("[execution] Failed via governed dispatcher: agentId={} executionId={}", agentId, executionId, e);
            return e;
        });
    }

    /**
     * Returns health information for a registered agent.
     */
    public Promise<AgentHealth> checkHealth(String agentId) {
        return agentRegistry.resolve(agentId).map(opt ->
            opt.isPresent()
                ? new AgentHealth("OK", 0L, Instant.now().toString(), 0.0)
                : new AgentHealth("NOT_FOUND", 0L, "", 0.0)
        );
    }

    /**
     * Returns execution history from the configured persistent store.
     */
    public Promise<List<ExecutionRecord>> getHistory(String agentId, int limit) {
        return historyStore.getHistory(agentId, limit);
    }

    /**
     * Returns agent memory state from the configured memory plane client.
     */
    public Promise<AgentMemory> getMemory(String agentId) {
        return memoryClient.getMemory(agentId);
    }

    // ─── Result types ──────────────────────────────────────────────────────────

    public record ExecutionResult(String executionId, String status, Object output, long durationMs) {}

    public record AgentHealth(String status, long uptimeMs, String lastExecutionTime, double failureRate) {}

    public record ExecutionRecord(
            String executionId, String status, Object input, Object output, long durationMs, String timestamp) {}

    public record AgentMemory(
            List<Object> episodic, Map<String, Object> semantic, Map<String, Object> procedural, String lastUpdated) {}
}

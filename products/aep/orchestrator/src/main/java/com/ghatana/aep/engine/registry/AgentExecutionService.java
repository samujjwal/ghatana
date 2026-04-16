package com.ghatana.aep.engine.registry;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.agent.spi.AgentRegistry;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Agent execution service for AEP.
 *
 * <p>Resolves the requested agent from {@link AgentRegistry}, builds a
 * {@link CompletionRequest} from the caller-supplied {@code input}, dispatches
 * it to the configured {@link LLMGateway}, and wraps the result in an
 * {@link ExecutionResult}.
 *
 * @doc.type class
 * @doc.purpose Executes agents via LLM gateway with registry resolution
 * @doc.layer product
 * @doc.pattern Service
 */
public class AgentExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);
    private static final int DEFAULT_MAX_TOKENS = 1024;

    private final AgentRegistry agentRegistry;
    private final LLMGateway llmGateway;
    private final AgentExecutionHistoryStore historyStore;
    private final AgentMemoryPlaneClient memoryClient;

    /**
     * @param agentRegistry registry used to look up registered agents; never {@code null}
     * @param llmGateway    gateway to the configured LLM provider(s); never {@code null}
     */
    public AgentExecutionService(AgentRegistry agentRegistry, LLMGateway llmGateway) {
        this(agentRegistry, llmGateway, new NoopAgentExecutionHistoryStore(), new AgentMemoryPlaneClient.Noop());
    }

    /**
     * @param agentRegistry registry used to look up registered agents; never {@code null}
     * @param llmGateway    gateway to the configured LLM provider(s); never {@code null}
     * @param historyStore  persistent execution history store; never {@code null}
     * @param memoryClient  memory plane client used to materialize episodic state; never {@code null}
     */
    public AgentExecutionService(
            AgentRegistry agentRegistry,
            LLMGateway llmGateway,
            AgentExecutionHistoryStore historyStore,
            AgentMemoryPlaneClient memoryClient) {
        this.agentRegistry = Objects.requireNonNull(agentRegistry, "agentRegistry");
        this.llmGateway    = Objects.requireNonNull(llmGateway,    "llmGateway");
        this.historyStore  = Objects.requireNonNull(historyStore,  "historyStore");
        this.memoryClient  = Objects.requireNonNull(memoryClient,  "memoryClient");
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
     */
    public Promise<ExecutionResult> execute(String agentId, Object input) {
        Objects.requireNonNull(agentId, "agentId");

        long startMs = System.currentTimeMillis();
        String executionId = UUID.randomUUID().toString();

        return agentRegistry.resolve(agentId).<ExecutionResult>then(optAgent -> {
            if (optAgent.isEmpty()) {
                log.warn("[execution] Agent not found: agentId={}", agentId);
                return Promise.ofException(
                    new IllegalArgumentException("No agent registered with id: " + agentId));
            }

            String prompt = input != null ? input.toString() : "";
            CompletionRequest request = CompletionRequest.builder()
                .prompt(prompt)
                .maxTokens(DEFAULT_MAX_TOKENS)
                .metadata(Map.of(
                    "agentId",     agentId,
                    "executionId", executionId
                ))
                .build();

            log.debug("[execution] Dispatching LLM request: agentId={} executionId={}", agentId, executionId);

            return llmGateway.complete(request).then((CompletionResult result) -> {
                long durationMs = System.currentTimeMillis() - startMs;
                log.info("[execution] Completed: agentId={} executionId={} durationMs={} tokens={}",
                    agentId, executionId, durationMs, result.getTokensUsed());

                ExecutionRecord record = new ExecutionRecord(
                    executionId,
                    "success",
                    input,
                    result.getText(),
                    durationMs,
                    Instant.now().toString());

                return historyStore.append(agentId, record)
                    .then(ignored -> memoryClient.recordExecution(agentId, executionId, input, result.getText(), durationMs))
                    .map(ignored -> new ExecutionResult(executionId, "success", result.getText(), durationMs));
            });
        }).mapException(e -> {
            log.error("[execution] Failed: agentId={} executionId={}", agentId, executionId, e);
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

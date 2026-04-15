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

    /**
     * @param agentRegistry registry used to look up registered agents; never {@code null}
     * @param llmGateway    gateway to the configured LLM provider(s); never {@code null}
     */
    public AgentExecutionService(AgentRegistry agentRegistry, LLMGateway llmGateway) {
        this.agentRegistry = Objects.requireNonNull(agentRegistry, "agentRegistry");
        this.llmGateway    = Objects.requireNonNull(llmGateway,    "llmGateway");
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

            return llmGateway.complete(request).map((CompletionResult result) -> {
                long durationMs = System.currentTimeMillis() - startMs;
                log.info("[execution] Completed: agentId={} executionId={} durationMs={} tokens={}",
                    agentId, executionId, durationMs, result.getTokensUsed());
                return new ExecutionResult(executionId, "success", result.getText(), durationMs);
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
     * Returns execution history. Currently returns an empty list;
     * backed by a run-ledger in full deployments.
     */
    public Promise<List<ExecutionRecord>> getHistory(String agentId, int limit) {
        return Promise.of(List.of());
    }

    /**
     * Returns agent memory state. Currently returns an empty placeholder.
     */
    public Promise<AgentMemory> getMemory(String agentId) {
        return Promise.of(new AgentMemory(List.of(), Map.of(), Map.of(), Instant.now().toString()));
    }

    // ─── Result types ──────────────────────────────────────────────────────────

    public record ExecutionResult(String executionId, String status, Object output, long durationMs) {}

    public record AgentHealth(String status, long uptimeMs, String lastExecutionTime, double failureRate) {}

    public record ExecutionRecord(
            String executionId, String status, Object input, Object output, long durationMs, String timestamp) {}

    public record AgentMemory(
            List<Object> episodic, Map<String, Object> semantic, Map<String, Object> procedural, String lastUpdated) {}
}

package com.ghatana.aep.engine.registry;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.agent.spi.AgentRegistry;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

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
    private static final int MAX_PROMPT_TOKEN_BUDGET = 8192;
    private static final String EXECUTABLE_METADATA_KEY = "executable";
    private static final String REGISTRATION_MODE_METADATA_KEY = "registrationMode";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern US_SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern SECRET_PATTERN = Pattern.compile(
        "(?i)\\b(api[_-]?key|token|secret|password)\\s*[:=]\\s*[^\\s,;]+");

    private final AgentRegistry agentRegistry;
    private final LLMGateway llmGateway;
    private final AgentDispatcher agentDispatcher;
    private final AgentExecutionHistoryStore historyStore;
    private final AgentMemoryPlaneClient memoryClient;

    /**
     * @param agentRegistry registry used to look up registered agents; never {@code null}
     * @param llmGateway    gateway to the configured LLM provider(s); never {@code null}
     */
    public AgentExecutionService(AgentRegistry agentRegistry, LLMGateway llmGateway) {
        this(agentRegistry, llmGateway, null, new NoopAgentExecutionHistoryStore(), new AgentMemoryPlaneClient.Noop());
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
        this(agentRegistry, llmGateway, null, historyStore, memoryClient);
    }

    /**
     * @param agentRegistry    registry used to look up registered agents; never {@code null}
     * @param llmGateway       gateway to the configured LLM provider(s); never {@code null}
     * @param agentDispatcher  governed agent dispatcher with safety pipeline; may be {@code null}
     * @param historyStore     persistent execution history store; never {@code null}
     * @param memoryClient     memory plane client used to materialize episodic state; never {@code null}
     */
    public AgentExecutionService(
            AgentRegistry agentRegistry,
            LLMGateway llmGateway,
            AgentDispatcher agentDispatcher,
            AgentExecutionHistoryStore historyStore,
            AgentMemoryPlaneClient memoryClient) {
        this.agentRegistry    = Objects.requireNonNull(agentRegistry, "agentRegistry");
        this.llmGateway       = Objects.requireNonNull(llmGateway,    "llmGateway");
        this.agentDispatcher = agentDispatcher;
        this.historyStore     = Objects.requireNonNull(historyStore,  "historyStore");
        this.memoryClient     = Objects.requireNonNull(memoryClient,  "memoryClient");
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
     * <p>If {@link AgentDispatcher} is configured, uses the governed execution path
     * with safety pipeline and evidence collection. Otherwise falls back to direct
     * {@link LLMGateway} calls for backward compatibility.
     */
    public Promise<ExecutionResult> execute(String agentId, Object input) {
        Objects.requireNonNull(agentId, "agentId");

        long startMs = System.currentTimeMillis();
        String executionId = UUID.randomUUID().toString();

        if (agentDispatcher != null) {
            return executeViaDispatcher(agentId, input, executionId, startMs);
        }

        return executeViaLlmGateway(agentId, input, executionId, startMs);
    }

    private Promise<ExecutionResult> executeViaDispatcher(
            String agentId, Object input, String executionId, long startMs) {
        log.debug("[execution] Using governed AgentDispatcher: agentId={} executionId={}", agentId, executionId);

        // Use empty context and derive a child context for this execution
        AgentContext baseCtx = AgentContext.empty();
        AgentContext ctx = baseCtx.toBuilder()
            .agentId(agentId)
            .turnId(executionId)
            .build();

        return agentDispatcher.dispatch(agentId, input, ctx).then(result -> {
            long durationMs = System.currentTimeMillis() - startMs;
            log.info("[execution] Completed via dispatcher: agentId={} executionId={} durationMs={}",
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
            log.error("[execution] Failed via dispatcher: agentId={} executionId={}", agentId, executionId, e);
            return e;
        });
    }

    private Promise<ExecutionResult> executeViaLlmGateway(
            String agentId, Object input, String executionId, long startMs) {
        log.debug("[execution] Using direct LLMGateway (legacy path): agentId={} executionId={}", agentId, executionId);

        return agentRegistry.resolve(agentId).<ExecutionResult>then(optAgent -> {
            if (optAgent.isEmpty()) {
                log.warn("[execution] Agent not found: agentId={}", agentId);
                return Promise.ofException(
                    new IllegalArgumentException("No agent registered with id: " + agentId));
            }

            if (isNonExecutableRegistration(optAgent.get().descriptor())) {
                String registrationMode = String.valueOf(
                    optAgent.get().descriptor().getMetadata().getOrDefault(
                        REGISTRATION_MODE_METADATA_KEY,
                        "unknown"
                    )
                );
                log.warn("[execution] Rejecting non-executable agent registration: agentId={} mode={}",
                    agentId, registrationMode);
                return Promise.ofException(new IllegalStateException(
                    "Agent '" + agentId + "' is registered for discovery only and cannot be executed"
                ));
            }

            String rawPrompt = input != null ? input.toString() : "";
            String prompt = redactPrompt(rawPrompt);
            enforcePromptBudget(prompt);

            Map<String, Object> requestMetadata = new HashMap<>();
            requestMetadata.put("agentId", agentId);
            requestMetadata.put("executionId", executionId);
            requestMetadata.put("privacyRedactionApplied", !prompt.equals(rawPrompt));
            requestMetadata.put("fallbackMode", "disabled");
            requestMetadata.put("auditRequired", true);
            requestMetadata.put("costBudgetMaxTokens", DEFAULT_MAX_TOKENS);
            requestMetadata.put("promptTokenBudget", MAX_PROMPT_TOKEN_BUDGET);

            CompletionRequest request = CompletionRequest.builder()
                .prompt(prompt)
                .maxTokens(DEFAULT_MAX_TOKENS)
                .metadata(requestMetadata)
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
            }).mapException(error -> new IllegalStateException(
                "Model unavailable for agent '" + agentId + "' and fallback is disabled", error));
        }).mapException(e -> {
            log.error("[execution] Failed: agentId={} executionId={}", agentId, executionId, e);
            return e;
        });
    }

    private String redactPrompt(String prompt) {
        String redacted = EMAIL_PATTERN.matcher(prompt).replaceAll("[REDACTED_EMAIL]");
        redacted = US_SSN_PATTERN.matcher(redacted).replaceAll("[REDACTED_SSN]");
        redacted = SECRET_PATTERN.matcher(redacted).replaceAll(match -> {
            String token = match.group();
            int separatorIndex = Math.max(token.indexOf(':'), token.indexOf('='));
            if (separatorIndex < 0) {
                return "[REDACTED_SECRET]";
            }
            return token.substring(0, separatorIndex + 1) + "[REDACTED_SECRET]";
        });
        return redacted;
    }

    private void enforcePromptBudget(String prompt) {
        int estimatedTokens = Math.max(1, (int) Math.ceil(prompt.length() / 4.0d));
        if (estimatedTokens > MAX_PROMPT_TOKEN_BUDGET) {
            throw new IllegalArgumentException(
                "Agent prompt exceeds token budget: estimated=" + estimatedTokens
                    + " budget=" + MAX_PROMPT_TOKEN_BUDGET);
        }
    }

    private boolean isNonExecutableRegistration(com.ghatana.agent.AgentDescriptor descriptor) {
        Object executable = descriptor.getMetadata().get(EXECUTABLE_METADATA_KEY);
        return Boolean.FALSE.equals(executable);
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

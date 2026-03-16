package com.ghatana.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpClient;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * OpenAI completion service with tool/function calling support using ActiveJ HttpClient.
 *
 * <p>Implements the OpenAI Chat Completions API ({@code /v1/chat/completions}) with full
 * support for tool/function calling, enabling agents to invoke external tools during
 * reasoning.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * LLMConfiguration config = LLMConfiguration.builder()
 *     .apiKey(System.getenv("OPENAI_API_KEY"))
 *     .modelName("gpt-4o-mini")
 *     .temperature(0.7)
 *     .maxTokens(4096)
 *     .build();
 *
 * ToolAwareOpenAICompletionService service = new ToolAwareOpenAICompletionService(
 *     config, httpClient, metrics);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose OpenAI Chat Completions service with tool calling via ActiveJ HttpClient
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class ToolAwareOpenAICompletionService implements ToolAwareCompletionService {

    private static final Logger log = LoggerFactory.getLogger(ToolAwareOpenAICompletionService.class);
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final LLMConfiguration config;
    private final HttpClient httpClient;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper;

    public ToolAwareOpenAICompletionService(
            LLMConfiguration config,
            HttpClient httpClient,
            MetricsCollector metrics) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Promise<CompletionResult> complete(CompletionRequest request) {
        return completeWithTools(request, List.of());
    }

    @Override
    public Promise<List<CompletionResult>> completeBatch(List<CompletionRequest> requests) {
        List<Promise<CompletionResult>> promises = requests.stream()
                .map(this::complete)
                .collect(Collectors.toList());
        return Promises.toList(promises);
    }

    @Override
    public Promise<CompletionResult> completeWithTools(
            CompletionRequest request,
            List<ToolDefinition> tools) {
        return executeWithRetry(() -> {
            try {
                String jsonBody = buildRequestBody(request, tools);
                return sendRequest(jsonBody).map(this::parseResponse);
            } catch (JsonProcessingException e) {
                return Promise.ofException(
                        new RuntimeException("Failed to serialize OpenAI request", e));
            }
        }, 0);
    }

    @Override
    public Promise<CompletionResult> continueWithToolResults(
            CompletionRequest request,
            List<ToolCallResult> results) {
        return executeWithRetry(() -> {
            try {
                String jsonBody = buildContinuationBody(request, results);
                return sendRequest(jsonBody).map(this::parseResponse);
            } catch (JsonProcessingException e) {
                return Promise.ofException(
                        new RuntimeException("Failed to serialize tool continuation request", e));
            }
        }, 0);
    }

    @Override
    public LLMConfiguration getConfig() {
        return config;
    }

    @Override
    public MetricsCollector getMetricsCollector() {
        return metrics;
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    // ─── Request building ────────────────────────────────────────────────────

    /**
     * Builds the OpenAI Chat Completions request body.
     *
     * <p>OpenAI tool calling format:
     * <pre>{@code
     * {
     *   "model": "gpt-4o-mini",
     *   "messages": [{"role": "user", "content": "..."}],
     *   "tools": [{
     *     "type": "function",
     *     "function": {
     *       "name": "...",
     *       "description": "...",
     *       "parameters": { "type": "object", "properties": {...}, "required": [...] }
     *     }
     *   }],
     *   "tool_choice": "auto"
     * }
     * }</pre>
     */
    private String buildRequestBody(CompletionRequest request, List<ToolDefinition> tools)
            throws JsonProcessingException {

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", resolveModel());
        payload.put("messages", buildMessages(request));
        payload.put("max_tokens", config.getMaxTokens() > 0 ? config.getMaxTokens() : 4096);

        if (config.getTemperature() >= 0) {
            payload.put("temperature", config.getTemperature());
        }

        if (!tools.isEmpty()) {
            payload.put("tools", tools.stream()
                    .map(this::toOpenAIToolFormat)
                    .collect(Collectors.toList()));
            payload.put("tool_choice", "auto");
        }

        if (request.getStopSequences() != null && !request.getStopSequences().isEmpty()) {
            payload.put("stop", request.getStopSequences());
        }

        return objectMapper.writeValueAsString(payload);
    }

    /**
     * Builds messages array from CompletionRequest.
     */
    private List<Map<String, Object>> buildMessages(CompletionRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // System prompt if present
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }

        // Conversation history
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (ChatMessage msg : request.getMessages()) {
                messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
        } else if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
            messages.add(Map.of("role", "user", "content", request.getPrompt()));
        }

        return messages;
    }

    /**
     * Converts a ToolDefinition to OpenAI function calling format.
     */
    private Map<String, Object> toOpenAIToolFormat(ToolDefinition tool) {
        Map<String, Object> properties = new HashMap<>();
        for (var entry : tool.getParameters().entrySet()) {
            var param = entry.getValue();
            properties.put(entry.getKey(), Map.of(
                    "type", param.type(),
                    "description", param.description()
            ));
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        if (!tool.getRequiredParameters().isEmpty()) {
            parameters.put("required", tool.getRequiredParameters());
        }

        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", tool.getName(),
                        "description", tool.getDescription(),
                        "parameters", parameters
                )
        );
    }

    /**
     * Builds a continuation request after tool execution.
     *
     * <p>The OpenAI continuation format includes:
     * <ol>
     *   <li>Original user messages</li>
     *   <li>Assistant message with {@code tool_calls}</li>
     *   <li>Tool result messages with role {@code tool}</li>
     * </ol>
     */
    private String buildContinuationBody(CompletionRequest request, List<ToolCallResult> results)
            throws JsonProcessingException {

        List<Map<String, Object>> messages = new ArrayList<>(buildMessages(request));

        // Assistant message showing which tools were called
        List<Map<String, Object>> toolCalls = results.stream()
                .map(r -> Map.of(
                        "id", (Object) r.getToolCallId(),
                        "type", "function",
                        "function", Map.of(
                                "name", r.getToolName(),
                                "arguments", "{}"  // Original args stored in ToolCallResult if needed
                        )
                ))
                .collect(Collectors.toList());

        messages.add(Map.of("role", "assistant", "tool_calls", toolCalls, "content", ""));

        // Tool result messages
        for (ToolCallResult result : results) {
            messages.add(Map.of(
                    "role", "tool",
                    "tool_call_id", result.getToolCallId(),
                    "content", String.valueOf(result.getResult())
            ));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", resolveModel());
        payload.put("messages", messages);
        payload.put("max_tokens", config.getMaxTokens() > 0 ? config.getMaxTokens() : 4096);
        if (config.getTemperature() >= 0) {
            payload.put("temperature", config.getTemperature());
        }

        return objectMapper.writeValueAsString(payload);
    }

    // ─── HTTP transport ──────────────────────────────────────────────────────

    private Promise<String> sendRequest(String jsonBody) {
        String url = (config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL)
                + "/v1/chat/completions";

        HttpRequest httpRequest = HttpRequest.post(url)
                .withHeader(HttpHeaders.AUTHORIZATION,
                        HttpHeaderValue.of("Bearer " + config.getApiKey()))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("application/json"))
                .withBody(ByteBuf.wrapForReading(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build();

        log.debug("Sending OpenAI request to {}: {} chars", url, jsonBody.length());
        long start = System.currentTimeMillis();

        return httpClient.request(httpRequest)
                .then(response -> response.loadBody())
                .then((body, e) -> {
                    long latency = System.currentTimeMillis() - start;
                    if (e != null) {
                        metrics.incrementCounter("llm.openai.error");
                        metrics.recordTimer("llm.openai.latency", latency);
                        return Promise.ofException(e);
                    }
                    metrics.incrementCounter("llm.openai.request");
                    metrics.recordTimer("llm.openai.latency", latency);
                    String responseBody = body.getString(java.nio.charset.StandardCharsets.UTF_8);
                    log.debug("OpenAI response in {}ms", latency);
                    return Promise.of(responseBody);
                });
    }

    // ─── Response parsing ─────────────────────────────────────────────────────

    private CompletionResult parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // Surface API errors
            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("Unknown OpenAI error");
                throw new RuntimeException("OpenAI API error: " + errorMsg);
            }

            JsonNode choices = root.path("choices");
            if (choices.isEmpty()) {
                throw new RuntimeException("OpenAI returned no choices");
            }

            JsonNode choice = choices.get(0);
            JsonNode message = choice.path("message");
            String finishReason = choice.path("finish_reason").asText("stop");
            String content = message.path("content").asText("");

            // Extract tool calls if present
            List<ToolCall> toolCalls = new ArrayList<>();
            JsonNode toolCallsNode = message.path("tool_calls");
            if (toolCallsNode.isArray()) {
                for (JsonNode tc : toolCallsNode) {
                    String id = tc.path("id").asText();
                    String name = tc.path("function").path("name").asText();
                    String args = tc.path("function").path("arguments").asText("{}");
                    toolCalls.add(ToolCall.of(id, name, args));
                }
            }

            // Token usage
            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            int totalTokens = usage.path("total_tokens").asInt(0);

            return CompletionResult.builder()
                    .text(content)
                    .modelUsed(resolveModel())
                    .finishReason(finishReason)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .tokensUsed(totalTokens)
                    .toolCalls(toolCalls)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: {}", json, e);
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }

    // ─── Retry logic ──────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface RequestSupplier {
        Promise<CompletionResult> get();
    }

    private Promise<CompletionResult> executeWithRetry(RequestSupplier supplier, int attempt) {
        return supplier.get().then((result, e) -> {
            if (e == null) {
                metrics.incrementCounter("llm.openai.success");
                return Promise.of(result);
            }
            metrics.incrementCounter("llm.openai.failure");
            if (shouldRetry(e, attempt)) {
                long delay = Math.min(1000L * (1L << attempt), 30_000L);
                log.warn("OpenAI request failed (attempt {}), retrying in {}ms: {}",
                        attempt + 1, delay, e.getMessage());
                return Promises.delay(delay).then(() -> executeWithRetry(supplier, attempt + 1));
            }
            return Promise.ofException(e);
        });
    }

    private boolean shouldRetry(Throwable error, int attempt) {
        if (attempt >= Math.max(config.getMaxRetries(), 3)) return false;
        String msg = error.getMessage();
        return msg != null && (msg.contains("timeout") || msg.contains("connection")
                || msg.contains("429") || msg.contains("500")
                || msg.contains("502") || msg.contains("503"));
    }

    private String resolveModel() {
        return config.getModelName() != null ? config.getModelName() : DEFAULT_MODEL;
    }
}

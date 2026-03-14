package com.ghatana.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpClient;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpRequest;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Anthropic Claude completion service with tool calling support.
 *
 * <p>Implements Anthropic's Messages API with function calling capabilities.
 *
 * <h3>API Differences from OpenAI</h3>
 * <ul>
 *   <li>API Key header: x-api-key (not Authorization)</li>
 *   <li>API Version header: anthropic-version: 2023-06-01</li>
 *   <li>Model names: claude-3-opus, claude-3-sonnet, claude-3-haiku</li>
 *   <li>Tool format: Different JSON structure than OpenAI</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Anthropic Claude completion service with tool calling
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class ToolAwareAnthropicCompletionService implements ToolAwareCompletionService {
    
    private static final Logger log = LoggerFactory.getLogger(ToolAwareAnthropicCompletionService.class);
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String API_VERSION = "2023-06-01";

    private final LLMConfiguration config;
    private final HttpClient httpClient;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper;

    public ToolAwareAnthropicCompletionService(
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
                String jsonBody = createToolRequestBody(request, tools);
                return sendRequest(jsonBody).map(this::parseResponse);
            } catch (JsonProcessingException e) {
                return Promise.ofException(new RuntimeException("Failed to create Anthropic request", e));
            }
        }, 0);
    }

    @Override
    public Promise<CompletionResult> continueWithToolResults(
            CompletionRequest request,
            List<ToolCallResult> results) {
        
        return executeWithRetry(() -> {
            try {
                String jsonBody = createContinueRequestBody(request, results);
                return sendRequest(jsonBody).map(this::parseResponse);
            } catch (JsonProcessingException e) {
                return Promise.ofException(new RuntimeException("Failed to create continuation request", e));
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
        return "anthropic";
    }

    /**
     * Create Anthropic Messages API request with tools.
     *
     * <p>Anthropic tool format:
     * <pre>{@code
     * {
     *   "model": "claude-3-opus-20240229",
     *   "messages": [{
     *     "role": "user",
     *     "content": "What's the weather?"
     *   }],
     *   "tools": [{
     *     "name": "get_weather",
     *     "description": "Get weather for location",
     *     "input_schema": {
     *       "type": "object",
     *       "properties": {...}
     *     }
     *   }],
     *   "max_tokens": 1024
     * }
     * }</pre>
     */
    private String createToolRequestBody(CompletionRequest request, List<ToolDefinition> tools) 
            throws JsonProcessingException {
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", config.getModelName());
        payload.put("max_tokens", config.getMaxTokens() > 0 ? config.getMaxTokens() : 4096);
        
        // Anthropic requires messages array, not a single prompt
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "user",
                "content", request.getPrompt()
        ));
        payload.put("messages", messages);

        // Add tools if provided
        if (!tools.isEmpty()) {
            List<Map<String, Object>> anthropicTools = tools.stream()
                    .map(this::toAnthropicToolFormat)
                    .collect(Collectors.toList());
            payload.put("tools", anthropicTools);
        }

        // Optional parameters
        if (config.getTemperature() > 0) {
            payload.put("temperature", config.getTemperature());
        }

        return objectMapper.writeValueAsString(payload);
    }

    /**
     * Convert ToolDefinition to Anthropic format.
     *
     * <p>Anthropic uses "input_schema" instead of "parameters".
     */
    private Map<String, Object> toAnthropicToolFormat(ToolDefinition tool) {
        Map<String, Object> anthropicTool = new HashMap<>();
        anthropicTool.put("name", tool.getName());
        anthropicTool.put("description", tool.getDescription());
        
        // Convert parameters to input_schema
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        
        // Build properties from parameters
        Map<String, Object> properties = new HashMap<>();
        for (var entry : tool.getParameters().entrySet()) {
            var param = entry.getValue();
            properties.put(entry.getKey(), Map.of(
                    "type", param.type(),
                    "description", param.description()
            ));
        }
        inputSchema.put("properties", properties);
        
        // Add required fields
        if (!tool.getRequiredParameters().isEmpty()) {
            inputSchema.put("required", tool.getRequiredParameters());
        }
        
        anthropicTool.put("input_schema", inputSchema);
        return anthropicTool;
    }

    /**
     * Create continuation request with tool results.
     *
     * <p>Anthropic format for tool results:
     * <pre>{@code
     * {
     *   "messages": [
     *     {"role": "user", "content": "What's the weather?"},
     *     {"role": "assistant", "content": [
     *       {"type": "tool_use", "id": "call_123", "name": "get_weather", "input": {...}}
     *     ]},
     *     {"role": "user", "content": [
     *       {"type": "tool_result", "tool_use_id": "call_123", "content": "72°F"}
     *     ]}
     *   ]
     * }
     * }</pre>
     */
    private String createContinueRequestBody(CompletionRequest request, List<ToolCallResult> results)
            throws JsonProcessingException {
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", config.getModelName());
        payload.put("max_tokens", config.getMaxTokens() > 0 ? config.getMaxTokens() : 4096);

        // Build messages with tool results
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // Original user message
        messages.add(Map.of("role", "user", "content", request.getPrompt()));
        
        // Assistant message with tool_use (would come from previous response)
        // Note: In practice, you'd store the previous assistant response
        // For now, we construct it from the results
        List<Map<String, Object>> assistantContent = results.stream()
                .map(result -> Map.of(
                        "type", "tool_use",
                        "id", result.getToolCallId(),
                        "name", result.getToolName(),
                        "input", (Object) Map.of() // Would contain original arguments
                ))
                .collect(Collectors.toList());
        messages.add(Map.of("role", "assistant", "content", assistantContent));

        // User message with tool_result
        List<Map<String, Object>> toolResultContent = results.stream()
                .map(result -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", "tool_result");
                    map.put("tool_use_id", result.getToolCallId());
                    map.put("content", result.getResult());
                    return map;
                })
                .collect(Collectors.toList());
        messages.add(Map.of("role", "user", "content", toolResultContent));

        payload.put("messages", messages);

        if (config.getTemperature() > 0) {
            payload.put("temperature", config.getTemperature());
        }

        return objectMapper.writeValueAsString(payload);
    }

    /**
     * Send HTTP request to Anthropic API.
     */
    private Promise<ByteBuf> sendRequest(String jsonBody) {
        long startTime = System.currentTimeMillis();

        String url = (config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL) 
                + "/v1/messages";

        HttpRequest httpRequest = HttpRequest.post(url)
                .withHeader(io.activej.http.HttpHeaders.of("x-api-key"), config.getApiKey())
                .withHeader(io.activej.http.HttpHeaders.of("anthropic-version"), API_VERSION)
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("application/json"))
                .withBody(ByteBuf.wrapForReading(jsonBody.getBytes()))
                .build();

        log.debug("Sending Anthropic request: {}", jsonBody);

        return httpClient.request(httpRequest)
                .then(response -> response.loadBody())
                .then((body, e) -> {
                    long latency = System.currentTimeMillis() - startTime;
                    
                    if (e != null) {
                        metrics.incrementCounter("llm.anthropic.error");
                        metrics.recordTimer("llm.anthropic.latency", latency);
                        return Promise.ofException(e);
                    }

                    metrics.incrementCounter("llm.anthropic.request");
                    metrics.recordTimer("llm.anthropic.latency", latency);
                    
                    return Promise.of(body);
                });
    }

    /**
     * Parse Anthropic API response.
     *
     * <p>Response format:
     * <pre>{@code
     * {
     *   "id": "msg_123",
     *   "type": "message",
     *   "role": "assistant",
     *   "content": [
     *     {"type": "text", "text": "I'll check the weather"},
     *     {"type": "tool_use", "id": "call_123", "name": "get_weather", "input": {...}}
     *   ],
     *   "stop_reason": "tool_use"
     * }
     * }</pre>
     */
    private CompletionResult parseResponse(ByteBuf body) {
        try {
            String responseText = body.getString(java.nio.charset.StandardCharsets.UTF_8);
            log.debug("Anthropic response: {}", responseText);

            JsonNode root = objectMapper.readTree(responseText);
            JsonNode content = root.get("content");
            
            if (content == null || !content.isArray()) {
                throw new RuntimeException("Invalid Anthropic response: missing content array");
            }

            StringBuilder textBuilder = new StringBuilder();
            List<ToolCall> toolCalls = new ArrayList<>();

            // Parse content blocks
            for (JsonNode block : content) {
                String type = block.get("type").asText();
                
                if ("text".equals(type)) {
                    textBuilder.append(block.get("text").asText());
                } else if ("tool_use".equals(type)) {
                    String id = block.get("id").asText();
                    String name = block.get("name").asText();
                    JsonNode input = block.get("input");
                    
                    // Convert input to Map
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = objectMapper.convertValue(input, Map.class);
                    
                    toolCalls.add(ToolCall.of(id, name, arguments));
                }
            }

            // Extract usage stats
            JsonNode usage = root.get("usage");
            int inputTokens = usage != null ? usage.path("input_tokens").asInt(0) : 0;
            int outputTokens = usage != null ? usage.path("output_tokens").asInt(0) : 0;

            return CompletionResult.builder()
                    .text(textBuilder.toString())
                    .toolCalls(toolCalls)
                    .promptTokens(inputTokens)
                    .completionTokens(outputTokens)
                    .tokensUsed(inputTokens + outputTokens)
                    .modelUsed(config.getModelName())
                    .latencyMs(0)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Anthropic response", e);
        }
    }

    /**
     * Execute with retry logic.
     */
    private Promise<CompletionResult> executeWithRetry(
            java.util.function.Supplier<Promise<CompletionResult>> supplier,
            int attempt) {
        
        return supplier.get()
                .then((result, e) -> {
                    if (e == null) {
                        metrics.incrementCounter("llm.anthropic.success");
                        return Promise.of(result);
                    } else {
                        metrics.incrementCounter("llm.anthropic.failure");
                        if (shouldRetry(e, attempt)) {
                            long delay = calculateBackoff(attempt);
                            log.warn("Anthropic request failed (attempt {}), retrying in {}ms: {}",
                                    attempt + 1, delay, e.getMessage());
                            return Promises.delay(delay).then(() -> executeWithRetry(supplier, attempt + 1));
                        }
                        return Promise.ofException(e);
                    }
                });
    }

    private boolean shouldRetry(Throwable e, int attempt) {
        return attempt < config.getMaxRetries() && isRetryableError(e);
    }

    private boolean isRetryableError(Throwable e) {
        String message = e.getMessage();
        return message != null && (
                message.contains("timeout") ||
                message.contains("429") ||  // Rate limit
                message.contains("500") ||  // Server error
                message.contains("502") ||  // Bad gateway
                message.contains("503")     // Service unavailable
        );
    }

    private long calculateBackoff(int attempt) {
        return Math.min(1000L * (1L << attempt), 30000L); // Exponential backoff, max 30s
    }
}

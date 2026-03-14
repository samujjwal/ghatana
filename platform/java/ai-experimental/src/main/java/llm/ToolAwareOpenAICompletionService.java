package com.ghatana.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpClient;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool-aware OpenAI completion service with function calling support.
 * 
 * @doc.type class
 * @doc.purpose OpenAI completion service with tool calling
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 * 
 * <p>Extends {@link OpenAICompletionService} to add tool/function calling capabilities
 * required by the {@link ToolAwareCompletionService} interface.</p>
 * 
 * <h3>Usage</h3>
 * <pre>{@code
 * ToolAwareCompletionService service = new ToolAwareOpenAICompletionService(config, httpClient, metrics);
 * 
 * // Define tools
 * List<ToolDefinition> tools = List.of(
 *     ToolDefinition.builder()
 *         .name("search_code")
 *         .description("Search codebase")
 *         .build()
 * );
 * 
 * // Complete with tools
 * CompletionResult result = service.completeWithTools(request, tools).getResult();
 * 
 * // Handle tool calls
 * if (result.hasToolCalls()) {
 *     List<ToolCallResult> results = executeTools(result.getToolCalls());
 *     CompletionResult finalResult = service.continueWithToolResults(request, results).getResult();
 * }
 * }</pre>
 */
public class ToolAwareOpenAICompletionService extends OpenAICompletionService implements ToolAwareCompletionService {
    
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final LLMConfiguration config;
    private final MetricsCollector metrics;
    
    public ToolAwareOpenAICompletionService(
            LLMConfiguration config,
            HttpClient httpClient,
            MetricsCollector metrics) {
        super(config, httpClient, metrics);
        this.config = config;
        this.httpClient = httpClient;
        this.metrics = metrics;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public Promise<CompletionResult> completeWithTools(CompletionRequest request, List<ToolDefinition> tools) {
        try {
            String url = (config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL) + "/v1/chat/completions";
            String jsonBody = createToolRequestBody(request, tools);
            
            io.activej.http.HttpRequest httpRequest = io.activej.http.HttpRequest.post(url)
                    .withHeader(io.activej.http.HttpHeaders.AUTHORIZATION, 
                               io.activej.http.HttpHeaderValue.of("Bearer " + config.getApiKey()))
                    .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, 
                               io.activej.http.HttpHeaderValue.of("application/json"))
                    .withBody(ByteBuf.wrapForReading(jsonBody.getBytes()))
                    .build();

            long startTime = System.currentTimeMillis();
            
            return httpClient.request(httpRequest)
                    .then(response -> response.loadBody())
                    .map(body -> {
                        long latency = System.currentTimeMillis() - startTime;
                        String responseBody = body.getString(java.nio.charset.StandardCharsets.UTF_8);
                        
                        if (responseBody.contains("\"error\"")) {
                            throw new RuntimeException("OpenAI API Error: " + responseBody);
                        }
                        
                        return parseToolResponse(responseBody, latency);
                    });
                    
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<CompletionResult> continueWithToolResults(
            CompletionRequest request,
            List<ToolCallResult> toolResults) {
        
        try {
            String url = (config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL) + "/v1/chat/completions";
            String jsonBody = createContinueRequestBody(request, toolResults);
            
            io.activej.http.HttpRequest httpRequest = io.activej.http.HttpRequest.post(url)
                    .withHeader(io.activej.http.HttpHeaders.AUTHORIZATION, 
                               io.activej.http.HttpHeaderValue.of("Bearer " + config.getApiKey()))
                    .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, 
                               io.activej.http.HttpHeaderValue.of("application/json"))
                    .withBody(ByteBuf.wrapForReading(jsonBody.getBytes()))
                    .build();

            long startTime = System.currentTimeMillis();
            
            return httpClient.request(httpRequest)
                    .then(response -> response.loadBody())
                    .map(body -> {
                        long latency = System.currentTimeMillis() - startTime;
                        String responseBody = body.getString(java.nio.charset.StandardCharsets.UTF_8);
                        
                        if (responseBody.contains("\"error\"")) {
                            throw new RuntimeException("OpenAI API Error: " + responseBody);
                        }
                        
                        return parseToolResponse(responseBody, latency);
                    });
                    
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    /**
     * Creates request body with tools for OpenAI function calling.
     */
    private String createToolRequestBody(CompletionRequest request, List<ToolDefinition> tools) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", config.getModelName());
        
        // Add messages
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            payload.put("messages", request.getMessages().stream().map(msg -> {
                Map<String, String> m = new HashMap<>();
                m.put("role", msg.getRole().getValue());
                m.put("content", msg.getContent());
                if (msg.getName() != null) m.put("name", msg.getName());
                return m;
            }).collect(Collectors.toList()));
        } else {
            payload.put("messages", List.of(Map.of("role", "user", "content", request.getPrompt())));
        }
        
        payload.put("max_tokens", request.getMaxTokens());
        payload.put("temperature", request.getTemperature());
        
        // Add tools in OpenAI format
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools.stream()
                    .map(ToolDefinition::toOpenAIFormat)
                    .collect(Collectors.toList()));
            payload.put("tool_choice", "auto"); // Let model decide
        }
        
        return objectMapper.writeValueAsString(payload);
    }
    
    /**
     * Creates request body for continuing conversation with tool results.
     */
    private String createContinueRequestBody(CompletionRequest request, List<ToolCallResult> toolResults) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", config.getModelName());
        
        // Build messages including tool results
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // Add original messages
        if (request.getMessages() != null) {
            messages.addAll(request.getMessages().stream().map(msg -> {
                Map<String, Object> m = new HashMap<>();
                m.put("role", msg.getRole().getValue());
                m.put("content", msg.getContent());
                if (msg.getName() != null) m.put("name", msg.getName());
                return m;
            }).collect(Collectors.toList()));
        }
        
        // Add tool result messages
        for (ToolCallResult toolResult : toolResults) {
            Map<String, Object> toolMsg = new HashMap<>();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", toolResult.getToolCallId());
            toolMsg.put("content", toolResult.getResult());
            messages.add(toolMsg);
        }
        
        payload.put("messages", messages);
        payload.put("max_tokens", request.getMaxTokens());
        payload.put("temperature", request.getTemperature());
        
        return objectMapper.writeValueAsString(payload);
    }
    
    /**
     * Parses OpenAI response that may contain tool calls.
     */
    private CompletionResult parseToolResponse(String json, long latency) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choice = root.path("choices").get(0);
            JsonNode message = choice.path("message");
            
            String content = message.path("content").asText("");
            String finishReason = choice.path("finish_reason").asText();
            
            // Parse token usage
            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt();
            int completionTokens = usage.path("completion_tokens").asInt();
            int totalTokens = usage.path("total_tokens").asInt();
            
            // Parse tool calls if present
            List<ToolCall> toolCalls = new ArrayList<>();
            JsonNode toolCallsNode = message.path("tool_calls");
            if (toolCallsNode.isArray()) {
                for (JsonNode toolCallNode : toolCallsNode) {
                    String id = toolCallNode.path("id").asText();
                    String functionName = toolCallNode.path("function").path("name").asText();
                    String argsJson = toolCallNode.path("function").path("arguments").asText();
                    
                    // Parse arguments JSON
                    Map<String, Object> arguments = objectMapper.readValue(argsJson, Map.class);
                    
                    toolCalls.add(ToolCall.of(id, functionName, arguments));
                }
            }
            
            return CompletionResult.builder()
                    .text(content)
                    .finishReason(finishReason)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .tokensUsed(totalTokens)
                    .modelUsed(root.path("model").asText())
                    .latencyMs(latency)
                    .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                    .build();
                    
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI tool response", e);
        }
    }
}

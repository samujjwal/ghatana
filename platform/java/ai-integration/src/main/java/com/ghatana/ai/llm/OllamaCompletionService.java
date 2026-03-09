package com.ghatana.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpClient;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
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
 * Ollama completion service implementation using ActiveJ HttpClient.
 *
 * <p>Ollama provides a local LLM inference server with an OpenAI-compatible API.
 * This service connects to local Ollama instances for cost-free, privacy-focused LLM operations.
 *
 * <p><b>Supported Models</b>:
 * <ul>
 *   <li>llama3 (Meta Llama 3)</li>
 *   <li>mixtral (Mixtral 8x7B)</li>
 *   <li>codellama (Code Llama)</li>
 *   <li>qwen (Qwen models)</li>
 *   <li>mistral (Mistral models)</li>
 * </ul>
 *
 * <p><b>Usage</b>:
 * <pre>{@code
 * LLMConfiguration config = LLMConfiguration.builder()
 *     .baseUrl("http://localhost:11434")
 *     .model("llama3")
 *     .temperature(0.7)
 *     .maxTokens(4000)
 *     .build();
 *
 * OllamaCompletionService service = new OllamaCompletionService(
 *     config, httpClient, metrics);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Ollama local LLM completion service
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class OllamaCompletionService implements CompletionService {
    private static final Logger logger = LoggerFactory.getLogger(OllamaCompletionService.class);
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3";

    private final LLMConfiguration config;
    private final HttpClient httpClient;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper;

    public OllamaCompletionService(LLMConfiguration config, HttpClient httpClient, MetricsCollector metrics) {
        this.config = Objects.requireNonNull(config);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.metrics = Objects.requireNonNull(metrics);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Promise<CompletionResult> complete(CompletionRequest request) {
        return executeWithRetry(request, 0);
    }

    @Override
    public Promise<List<CompletionResult>> completeBatch(List<CompletionRequest> requests) {
        List<Promise<CompletionResult>> promises = requests.stream()
                .map(this::complete)
                .collect(Collectors.toList());
        return Promises.toList(promises);
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
        return "ollama";
    }

    private Promise<CompletionResult> executeWithRetry(CompletionRequest request, int attempt) {
        return executeRequest(request)
                .then((response, e) -> {
                    if (e == null) {
                        metrics.incrementCounter("llm.ollama.success");
                        return Promise.of(response);
                    } else {
                        metrics.incrementCounter("llm.ollama.failure");
                        if (shouldRetry(e, attempt)) {
                            long delay = calculateBackoff(attempt);
                            logger.warn("Ollama request failed (attempt {}), retrying in {}ms: {}", attempt + 1, delay, e.getMessage());
                            
                            return Promises.delay(delay).then(() -> executeWithRetry(request, attempt + 1));
                        }
                        return Promise.ofException(e);
                    }
                });
    }

    private Promise<CompletionResult> executeRequest(CompletionRequest request) {
        try {
            String url = (config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL) + "/v1/chat/completions";
            String model = config.getModelName() != null ? config.getModelName() : DEFAULT_MODEL;

            // Build Ollama request payload (OpenAI-compatible)
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("messages", buildMessages(request));
            
            payload.put("temperature", config.getTemperature());
            payload.put("max_tokens", config.getMaxTokens());
            
            if (request.getStopSequences() != null && !request.getStopSequences().isEmpty()) {
                payload.put("stop", request.getStopSequences());
            }

            String jsonPayload = objectMapper.writeValueAsString(payload);
            
            HttpRequest httpRequest = HttpRequest.post(url)
                    .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("application/json"))
                    .withBody(ByteBuf.wrapForReading(jsonPayload.getBytes()))
                    .build();

            long startTime = System.currentTimeMillis();
            
            return httpClient.request(httpRequest)
                    .then(response -> response.loadBody())
                    .map(body -> {
                        long duration = System.currentTimeMillis() - startTime;
                        metrics.recordTimer("llm.ollama.latency", duration);
                        
                        String responseBody = body.getString(java.nio.charset.StandardCharsets.UTF_8);
                        
                        if (responseBody.contains("\"error\"")) {
                            logger.error("Ollama API error: {}", responseBody);
                            throw new RuntimeException("Ollama API error: " + responseBody);
                        }

                        return parseResponse(responseBody, model, duration);
                    });

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize Ollama request", e);
            return Promise.ofException(new RuntimeException("Failed to serialize request", e));
        }
    }

    private List<Map<String, Object>> buildMessages(CompletionRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // Add conversation history if provided
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (ChatMessage msg : request.getMessages()) {
                messages.add(Map.of(
                    "role", msg.getRole(),
                    "content", msg.getContent()
                ));
            }
        } else if (request.getPrompt() != null && !request.getPrompt().isEmpty()) {
            // Single prompt as user message
            messages.add(Map.of(
                "role", "user",
                "content", request.getPrompt()
            ));
        }
        
        return messages;
    }

    private CompletionResult parseResponse(String json, String model, long latency) {
        try {
            JsonNode root = objectMapper.readTree(json);
            
            // Ollama OpenAI-compatible response format
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                logger.error("Invalid Ollama response format: missing choices");
                throw new RuntimeException("Invalid response format");
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            String content = message != null ? message.get("content").asText() : "";
            String finishReason = firstChoice.has("finish_reason") ? firstChoice.get("finish_reason").asText() : "stop";

            // Extract usage stats
            int promptTokens = 0;
            int completionTokens = 0;
            int totalTokens = 0;
            
            JsonNode usage = root.get("usage");
            if (usage != null) {
                promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
                completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
                totalTokens = usage.has("total_tokens") ? usage.get("total_tokens").asInt() : 0;
            }

            // Track token metrics (no need to increment by value - just count occurrences)
            metrics.incrementCounter("llm.ollama.requests");

            return CompletionResult.builder()
                    .text(content)
                    .modelUsed(model)
                    .finishReason(finishReason)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .tokensUsed(totalTokens)
                    .latencyMs(latency)
                    .build();

        } catch (Exception e) {
            logger.error("Failed to parse Ollama response", e);
            throw new RuntimeException("Failed to parse response", e);
        }
    }

    private boolean shouldRetry(Throwable error, int attempt) {
        if (attempt >= config.getMaxRetries()) {
            return false;
        }

        // Retry on network errors, timeouts, and 5xx errors
        String message = error.getMessage();
        return message != null && (
                message.contains("timeout") ||
                message.contains("connection") ||
                message.contains("500") ||
                message.contains("502") ||
                message.contains("503")
        );
    }

    private long calculateBackoff(int attempt) {
        // Exponential backoff: 1s, 2s, 4s, 8s, ...
        return Math.min(1000L * (1L << attempt), 10000L);
    }
}

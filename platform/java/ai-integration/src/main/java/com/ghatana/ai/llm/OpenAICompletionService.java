package com.ghatana.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.bytebuf.ByteBuf;
import io.activej.eventloop.Eventloop;
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
 * OpenAI completion service implementation using ActiveJ HttpClient.
 *
 * @doc.type class
 * @doc.purpose OpenAI completion service
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class OpenAICompletionService implements CompletionService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAICompletionService.class);
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";

    private final LLMConfiguration config;
    private final HttpClient httpClient;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper;

    public OpenAICompletionService(LLMConfiguration config, HttpClient httpClient, MetricsCollector metrics) {
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
        return "openai";
    }

    private Promise<CompletionResult> executeWithRetry(CompletionRequest request, int attempt) {
        return executeRequest(request)
                .then((response, e) -> {
                    if (e == null) {
                        metrics.incrementCounter("llm.openai.success");
                        return Promise.of(response);
                    } else {
                        metrics.incrementCounter("llm.openai.failure");
                        if (shouldRetry(e, attempt)) {
                            long delay = calculateBackoff(attempt);
                            logger.warn("OpenAI request failed (attempt {}), retrying in {}ms: {}", attempt + 1, delay, e.getMessage());
                            
                            // Use Eventloop delay for non-blocking retry
                            return Promises.delay(delay).then(() -> executeWithRetry(request, attempt + 1));
                        }
                        return Promise.ofException(e);
                    }
                });
    }

    private Promise<CompletionResult> executeRequest(CompletionRequest request) {
        try {
            String url = (config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL) + "/v1/chat/completions";
            String jsonBody = createRequestBody(request);
            
            HttpRequest httpRequest = HttpRequest.post(url)
                    .withHeader(HttpHeaders.AUTHORIZATION, HttpHeaderValue.of("Bearer " + config.getApiKey()))
                    .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("application/json"))
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
                        
                        return parseResponse(responseBody, latency);
                    });
        } catch (JsonProcessingException e) {
            return Promise.ofException(e);
        }
    }

    private String createRequestBody(CompletionRequest request) throws JsonProcessingException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", config.getModelName());
        
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
        
        if (request.getResponseFormat() != null) {
             payload.put("response_format", Map.of("type", request.getResponseFormat()));
        }

        return objectMapper.writeValueAsString(payload);
    }

    private CompletionResult parseResponse(String json, long latency) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choice = root.path("choices").get(0);
            String content = choice.path("message").path("content").asText();
            String finishReason = choice.path("finish_reason").asText();
            
            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt();
            int completionTokens = usage.path("completion_tokens").asInt();
            int totalTokens = usage.path("total_tokens").asInt();

            return CompletionResult.builder()
                    .text(content)
                    .finishReason(finishReason)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .tokensUsed(totalTokens)
                    .modelUsed(root.path("model").asText())
                    .latencyMs(latency)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }

    private boolean shouldRetry(Exception e, int attempt) {
        return attempt < config.getMaxRetries(); // Simple retry policy for now
    }

    private long calculateBackoff(int attempt) {
        return (long) (Math.pow(2, attempt) * 1000);
    }
}

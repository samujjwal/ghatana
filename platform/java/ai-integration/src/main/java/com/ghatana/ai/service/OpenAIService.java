package com.ghatana.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @doc.type class
 * @doc.purpose OpenAI implementation of the LLMService using the Chat Completions API.
 * @doc.layer core
 * @doc.pattern Service
 */
public class OpenAIService implements LLMService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIService.class);
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final String apiKey;
    private final String model;
    private final Executor executor = Executors.newCachedThreadPool();
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAIService(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public OpenAIService(String apiKey, String model) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey cannot be null");
        this.model = Objects.requireNonNull(model, "model cannot be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public Promise<String> generate(String prompt) {
        return Promise.ofBlocking(executor, () -> {
            logger.info("Calling OpenAI ({}) with prompt length={}", model, prompt.length());
            return callChatCompletion(prompt);
        });
    }

    @Override
    public Promise<String> generateStructured(String prompt) {
        return generate(prompt);
    }

    private String callChatCompletion(String prompt) throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "messages", new Object[]{
                        Map.of("role", "user", "content", prompt)
                },
                "max_tokens", 4096,
                "temperature", 0.7
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "OpenAI API error " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText();
    }
}

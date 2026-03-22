package com.ghatana.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * OpenAI-backed LLMService.
 *
 * <p>Issues requests to {@code POST https://api.openai.com/v1/chat/completions}
 * using {@link java.net.http.HttpClient} (JDK 11+ built-in, no extra deps).
 * The result is the assistant message's {@code content} string.
 *
 * <p>Requires the environment variable {@code OPENAI_API_KEY} or the key
 * passed directly to the constructor.
 *
 * @doc.type class
 * @doc.purpose OpenAI Chat Completions implementation of LLMService
 * @doc.layer core
 * @doc.pattern Service
 */
public class OpenAIService implements LLMService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIService.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Executor executor = Executors.newCachedThreadPool();

    /**
     * Create with explicit API key, using the default model.
     *
     * @param apiKey OpenAI API key (must not be blank)
     */
    public OpenAIService(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    /**
     * Create with explicit API key and model name.
     *
     * @param apiKey OpenAI API key (must not be blank)
     * @param model  model identifier (e.g. "gpt-4o", "gpt-3.5-turbo")
     */
    public OpenAIService(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model != null ? model : DEFAULT_MODEL;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public Promise<String> generate(String prompt) {
        return Promise.ofBlocking(executor, () -> callChatCompletions(prompt, false));
    }

    @Override
    public Promise<String> generateStructured(String prompt) {
        // For structured output, request JSON mode by appending an instruction.
        // The OpenAI JSON mode guarantees the response is valid JSON when enabled.
        return Promise.ofBlocking(executor, () -> callChatCompletions(prompt, true));
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private String callChatCompletions(String prompt, boolean jsonMode) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API key is not configured. Set OPENAI_API_KEY env var.");
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.7);
        body.put("max_tokens", 2048);

        if (jsonMode) {
            body.putObject("response_format").put("type", "json_object");
        }

        var messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);

        String requestBody = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CHAT_COMPLETIONS_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        logger.debug("Calling OpenAI Chat Completions (model={}, jsonMode={})", model, jsonMode);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.error("OpenAI API error: status={}, body={}", response.statusCode(), response.body());
            throw new RuntimeException(
                    "OpenAI API returned HTTP " + response.statusCode() + ": " + response.body());
        }

        var root = mapper.readTree(response.body());
        String content = root.path("choices").get(0)
                .path("message").path("content").asText();

        logger.debug("OpenAI response received ({} chars)", content.length());
        return content;
    }
}

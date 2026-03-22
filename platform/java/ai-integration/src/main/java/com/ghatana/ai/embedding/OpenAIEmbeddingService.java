package com.ghatana.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

/**
 * Implementation of EmbeddingService backed by the OpenAI Embeddings REST API.
 *
 * <p>Makes HTTP calls to {@code POST https://api.openai.com/v1/embeddings}
 * for both single-text and batch embedding requests.
 *
 * @doc.type class
 * @doc.purpose Provides OpenAI REST-based embedding vectors for semantic search and retrieval
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class OpenAIEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIEmbeddingService.class);
    private static final String EMBEDDINGS_URL = "https://api.openai.com/v1/embeddings";

    private final LLMConfiguration config;
    private final MetricsCollector metricsCollector;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new OpenAIEmbeddingService instance.
     *
     * @param config The LLM configuration (must include API key and model name)
     * @param metricsCollector The metrics collector for monitoring
     */
    public OpenAIEmbeddingService(LLMConfiguration config, MetricsCollector metricsCollector) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector cannot be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Promise<EmbeddingResult> createEmbedding(String text) {
        Objects.requireNonNull(text, "text cannot be null");
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            final long startTime = System.currentTimeMillis();
            try {
                log.debug("Creating embedding for text (length: {})", text.length());
                metricsCollector.incrementCounter("ai.embedding.requests", "model", config.getModelName());

                float[] vector = callEmbeddingsApi(text);
                EmbeddingResult result = new EmbeddingResult(text, vector, config.getModelName());

                long duration = System.currentTimeMillis() - startTime;
                metricsCollector.recordTimer("ai.embedding.latency", duration);
                metricsCollector.incrementCounter("ai.embedding.latency.by_model", "model", config.getModelName());
                log.debug("Successfully created embedding in {}ms", duration);
                return result;

            } catch (Exception e) {
                metricsCollector.incrementCounter("ai.embedding.errors",
                        "model", config.getModelName(), "error", e.getClass().getSimpleName());
                log.error("Error creating embedding: {}", e.getMessage(), e);
                throw e;
            }
        });
    }

    @Override
    public Promise<List<EmbeddingResult>> createEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Promise.of(List.of());
        }
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            final long startTime = System.currentTimeMillis();
            try {
                log.debug("Creating batch embeddings for {} texts", texts.size());
                metricsCollector.incrementCounter("ai.embedding.batch_requests", "model", config.getModelName());

                List<EmbeddingResult> results = callBatchEmbeddingsApi(texts);

                long duration = System.currentTimeMillis() - startTime;
                metricsCollector.recordTimer("ai.embedding.batch_latency", duration);
                metricsCollector.incrementCounter("ai.embedding.batch_latency.by_model", "model", config.getModelName());
                log.debug("Successfully created {} embeddings in {}ms", results.size(), duration);
                return results;

            } catch (Exception e) {
                metricsCollector.incrementCounter("ai.embedding.batch_errors",
                        "model", config.getModelName(), "error", e.getClass().getSimpleName());
                log.error("Error creating batch embeddings: {}", e.getMessage(), e);
                throw e;
            }
        });
    }

    /**
     * Calls the OpenAI Embeddings API for a single text input.
     *
     * @param text Text to embed
     * @return Embedding vector as float array
     */
    private float[] callEmbeddingsApi(String text) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModelName());
        body.put("input", text);

        JsonNode response = sendRequest(body);
        JsonNode dataArray = response.get("data");
        if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
            throw new IllegalStateException("OpenAI embeddings API returned empty data array");
        }
        return extractVector(dataArray.get(0).get("embedding"));
    }

    /**
     * Calls the OpenAI Embeddings API for multiple texts in a single batch request.
     * Results are returned in the same order as the input texts via the {@code index} field.
     *
     * @param texts Texts to embed
     * @return List of EmbeddingResult in the same order as inputs
     */
    private List<EmbeddingResult> callBatchEmbeddingsApi(List<String> texts) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModelName());
        ArrayNode inputArray = body.putArray("input");
        for (String text : texts) {
            inputArray.add(text);
        }

        JsonNode response = sendRequest(body);
        JsonNode dataArray = response.get("data");
        if (dataArray == null || !dataArray.isArray()) {
            throw new IllegalStateException("OpenAI batch embeddings API returned invalid data");
        }

        // Results may arrive in any order; sort by index field before mapping to texts
        List<float[]> vectorsByIndex = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            vectorsByIndex.add(null);
        }
        for (JsonNode item : dataArray) {
            int index = item.get("index").asInt();
            vectorsByIndex.set(index, extractVector(item.get("embedding")));
        }

        List<EmbeddingResult> results = new ArrayList<>(texts.size());
        String modelName = response.path("model").asText(config.getModelName());
        for (int i = 0; i < texts.size(); i++) {
            float[] vector = vectorsByIndex.get(i);
            if (vector == null) {
                throw new IllegalStateException("Missing embedding for index " + i + " in batch response");
            }
            results.add(new EmbeddingResult(texts.get(i), vector, modelName));
        }
        return results;
    }

    /**
     * Sends the request body to the OpenAI Embeddings endpoint and parses the JSON response.
     */
    private JsonNode sendRequest(ObjectNode body) throws Exception {
        String requestJson = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(EMBEDDINGS_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("OpenAI embeddings API error: status={} body={}", response.statusCode(), response.body());
            throw new RuntimeException("OpenAI embeddings API returned status " + response.statusCode()
                    + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    /**
     * Converts a JSON array of doubles into a float array.
     */
    private float[] extractVector(JsonNode embeddingNode) {
        if (embeddingNode == null || !embeddingNode.isArray()) {
            throw new IllegalStateException("Embedding node is missing or not an array");
        }
        float[] vector = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            vector[i] = (float) embeddingNode.get(i).asDouble();
        }
        return vector;
    }

    @Override
    public LLMConfiguration getConfig() {
        return config;
    }

    @Override
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
}

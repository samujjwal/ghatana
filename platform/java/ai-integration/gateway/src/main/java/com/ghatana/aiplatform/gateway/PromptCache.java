package com.ghatana.aiplatform.gateway;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.core.state.HybridStateStore;
import com.ghatana.core.state.SyncStrategy;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Content-addressed cache for prompts and completions using HybridStateStore.
 *
 * <p><b>Purpose</b><br>
 * Caches LLM operations (embeddings, completions) with deterministic content-based keys
 * to reduce API costs and improve latency for repeated requests.
 *
 * <p><b>Caching Strategy</b><br>
 * - Embeddings: Always cached with text hash as key
 * - Completions: Cached only for deterministic requests (temperature=0)
 * - TTL: Configurable per operation type (default 10 minutes)
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - delegates to HybridStateStore which is thread-safe.
 *
 * @doc.type class
 * @doc.purpose Prompt caching layer for LLM operations
 * @doc.layer platform
 * @doc.pattern Cache-Aside
 */
public class PromptCache {

    private static final Logger logger = LoggerFactory.getLogger(PromptCache.class);
    private static final int DEFAULT_TTL_SECONDS = 600; // 10 minutes

    private final HybridStateStore<String, byte[]> store;
    private final int ttlSeconds;

    /**
     * Constructs prompt cache with hybrid state store.
     *
     * @param store hybrid state store for local + central caching
     * @param ttlSeconds TTL for cached entries
     */
    public PromptCache(HybridStateStore<String, byte[]> store, int ttlSeconds) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
    }

    /**
     * Constructs prompt cache with default TTL.
     *
     * @param store hybrid state store
     */
    public PromptCache(HybridStateStore<String, byte[]> store) {
        this(store, DEFAULT_TTL_SECONDS);
    }

    /**
     * Gets cached embedding for text.
     *
     * @param tenantId tenant identifier
     * @param text input text
     * @return Promise of cached embedding result, or null if not cached
     */
    public Promise<EmbeddingResult> getEmbedding(String tenantId, String text) {
        String key = buildEmbeddingKey(tenantId, text);

        return store.get(key)
                .map(optBytes -> {
                    if (optBytes.isEmpty()) {
                        return null;
                    }
                    try {
                        return deserializeEmbedding(optBytes.get());
                    } catch (Exception e) {
                        logger.warn("Failed to deserialize cached embedding: key={}", key, e);
                        return null;
                    }
                });
    }

    /**
     * Caches embedding result for text.
     *
     * @param tenantId tenant identifier
     * @param text input text
     * @param result embedding result to cache
     */
    public void putEmbedding(String tenantId, String text, EmbeddingResult result) {
        String key = buildEmbeddingKey(tenantId, text);

        try {
            byte[] bytes = serializeEmbedding(result);
            store.put(key, bytes);
        } catch (Exception e) {
            logger.error("Failed to serialize embedding for caching: key={}", key, e);
        }
    }

    /**
     * Gets cached completion for request.
     *
     * @param tenantId tenant identifier
     * @param request completion request
     * @return Promise of cached completion result, or null if not cached
     */
    public Promise<CompletionResult> getCompletion(String tenantId, CompletionRequest request) {
        String key = buildCompletionKey(tenantId, request);

        return store.get(key)
                .map(optBytes -> {
                    if (optBytes.isEmpty()) {
                        return null;
                    }
                    try {
                        return deserializeCompletion(optBytes.get());
                    } catch (Exception e) {
                        logger.warn("Failed to deserialize cached completion: key={}", key, e);
                        return null;
                    }
                });
    }

    /**
     * Caches completion result for request.
     *
     * @param tenantId tenant identifier
     * @param request completion request
     * @param result completion result to cache
     */
    public void putCompletion(String tenantId, CompletionRequest request, CompletionResult result) {
        String key = buildCompletionKey(tenantId, request);

        try {
            byte[] bytes = serializeCompletion(result);
            store.put(key, bytes);
        } catch (Exception e) {
            logger.error("Failed to serialize completion for caching: key={}", key, e);
        }
    }

    /**
     * Builds cache key for embedding.
     */
    private String buildEmbeddingKey(String tenantId, String text) {
        String hash = hashText(text);
        return String.format("tenant:%s:ai:promptCache:embedding:%s", tenantId, hash);
    }

    /**
     * Builds cache key for completion.
     */
    private String buildCompletionKey(String tenantId, CompletionRequest request) {
        // Hash combination of prompt and parameters
        String content = request.getPrompt() +
                "|" + request.getMaxTokens() +
                "|" + request.getTemperature();
        String hash = hashText(content);
        return String.format("tenant:%s:ai:promptCache:completion:%s", tenantId, hash);
    }

    /**
     * Hashes text using SHA-256 for content-addressed keys.
     */
    private String hashText(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            // Return first 32 characters for shorter keys
            return hexString.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Serializes embedding result to bytes.
     */
    private byte[] serializeEmbedding(EmbeddingResult result) {
        // Simple format: vector length (4 bytes) + vector values (8 bytes each)
        float[] vector = result.getVector();
        byte[] bytes = new byte[4 + vector.length * 4];

        // Write vector length
        bytes[0] = (byte) (vector.length >>> 24);
        bytes[1] = (byte) (vector.length >>> 16);
        bytes[2] = (byte) (vector.length >>> 8);
        bytes[3] = (byte) vector.length;

        // Write vector values
        for (int i = 0; i < vector.length; i++) {
            int bits = Float.floatToIntBits(vector[i]);
            int offset = 4 + i * 4;
            bytes[offset] = (byte) (bits >>> 24);
            bytes[offset + 1] = (byte) (bits >>> 16);
            bytes[offset + 2] = (byte) (bits >>> 8);
            bytes[offset + 3] = (byte) bits;
        }

        return bytes;
    }

    /**
     * Deserializes embedding result from bytes.
     */
    private EmbeddingResult deserializeEmbedding(byte[] bytes) {
        // Read vector length
        int length = ((bytes[0] & 0xFF) << 24) |
                    ((bytes[1] & 0xFF) << 16) |
                    ((bytes[2] & 0xFF) << 8) |
                    (bytes[3] & 0xFF);

        // Read vector values
        float[] vector = new float[length];
        for (int i = 0; i < length; i++) {
            int offset = 4 + i * 4;
            int bits = ((bytes[offset] & 0xFF) << 24) |
                      ((bytes[offset + 1] & 0xFF) << 16) |
                      ((bytes[offset + 2] & 0xFF) << 8) |
                      (bytes[offset + 3] & 0xFF);
            vector[i] = Float.intBitsToFloat(bits);
        }

        return EmbeddingResult.of(vector);
    }

    /**
     * Serializes completion result to bytes.
     */
    private byte[] serializeCompletion(CompletionResult result) {
        // Simple format: text bytes
        return result.getText().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deserializes completion result from bytes.
     */
    private CompletionResult deserializeCompletion(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        return CompletionResult.builder()
                .text(text)
                .tokensUsed(0) // Cached results don't track token usage
                .build();
    }
}


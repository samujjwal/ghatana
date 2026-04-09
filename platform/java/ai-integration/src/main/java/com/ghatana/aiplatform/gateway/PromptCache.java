package com.ghatana.aiplatform.gateway;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.core.state.HybridStateStore;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Caches LLM responses (embeddings and completions) in a {@link HybridStateStore}.
 *
 * <p>Cache keys are deterministic hashes derived from tenant ID and input content.
 * Objects are serialized to JSON bytes for storage and deserialized on retrieval.
 * A miss returns {@code Promise.of(null)}.
 *
 * @doc.type class
 * @doc.purpose Cache layer for LLM embeddings and completions to reduce provider API calls
 * @doc.layer platform
 * @doc.pattern Cache
 */
public class PromptCache {

    private static final Logger logger = LoggerFactory.getLogger(PromptCache.class);

    private static final int DEFAULT_TTL_SECONDS = 600;

    private final HybridStateStore<String, byte[]> store;
    private final int ttlSeconds;

    /**
     * Creates a PromptCache with custom TTL.
     *
     * @param store      backing state store
     * @param ttlSeconds entry time-to-live (informational; eviction controlled by the store)
     */
    public PromptCache(HybridStateStore<String, byte[]> store, int ttlSeconds) {
        this.store = store;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Creates a PromptCache with the default TTL of {@value #DEFAULT_TTL_SECONDS} seconds.
     *
     * @param store backing state store
     */
    public PromptCache(HybridStateStore<String, byte[]> store) {
        this(store, DEFAULT_TTL_SECONDS);
    }

    /**
     * Retrieves a cached embedding for the given tenant and text.
     *
     * @param tenantId tenant identifier
     * @param text     input text
     * @return cached result, or {@code Promise.of(null)} on a miss
     */
    public Promise<EmbeddingResult> getEmbedding(String tenantId, String text) {
        String key = buildEmbeddingKey(tenantId, text);
        return store.get(key).map(opt -> opt.map(this::deserializeEmbedding).orElse(null));
    }

    /**
     * Stores an embedding in the cache.
     *
     * @param tenantId tenant identifier
     * @param text     input text
     * @param result   embedding result to cache
     */
    public void putEmbedding(String tenantId, String text, EmbeddingResult result) {
        String key = buildEmbeddingKey(tenantId, text);
        store.put(key, serializeEmbedding(result));
        logger.debug("Cached embedding for tenant={} key={}", tenantId, key);
    }

    /**
     * Retrieves a cached completion for the given tenant and request.
     *
     * @param tenantId tenant identifier
     * @param request  completion request
     * @return cached result, or {@code Promise.of(null)} on a miss
     */
    public Promise<CompletionResult> getCompletion(String tenantId, CompletionRequest request) {
        String key = buildCompletionKey(tenantId, request);
        return store.get(key).map(opt -> opt.map(this::deserializeCompletion).orElse(null));
    }

    /**
     * Stores a completion in the cache.
     *
     * @param tenantId tenant identifier
     * @param request  completion request used as cache key input
     * @param result   completion result to cache
     */
    public void putCompletion(String tenantId, CompletionRequest request, CompletionResult result) {
        String key = buildCompletionKey(tenantId, request);
        store.put(key, serializeCompletion(result));
        logger.debug("Cached completion for tenant={} key={}", tenantId, key);
    }

    // ------------------------------------------------------------------ //
    //  Key construction                                                    //
    // ------------------------------------------------------------------ //

    private String buildEmbeddingKey(String tenantId, String text) {
        return "emb:" + tenantId + ":" + hashText(text);
    }

    private String buildCompletionKey(String tenantId, CompletionRequest request) {
        String requestJson;
        try {
            requestJson = JsonUtils.toJson(request);
        } catch (Exception e) {
            logger.warn("Failed to serialize completion request for cache key; using toString fallback", e);
            requestJson = request.toString();
        }
        return "cmp:" + tenantId + ":" + hashText(requestJson);
    }

    private String hashText(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present in Java SE
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    // ------------------------------------------------------------------ //
    //  Serialization helpers                                               //
    // ------------------------------------------------------------------ //

    private byte[] serializeEmbedding(EmbeddingResult result) {
        try {
            return JsonUtils.toJsonBytes(result);
        } catch (Exception e) {
            logger.error("Failed to serialize EmbeddingResult for cache", e);
            return new byte[0];
        }
    }

    private EmbeddingResult deserializeEmbedding(byte[] bytes) {
        try {
            return JsonUtils.fromJson(new ByteArrayInputStream(bytes), EmbeddingResult.class);
        } catch (Exception e) {
            logger.warn("Failed to deserialize cached EmbeddingResult; cache miss", e);
            return null;
        }
    }

    private byte[] serializeCompletion(CompletionResult result) {
        try {
            return JsonUtils.toJsonBytes(result);
        } catch (Exception e) {
            logger.error("Failed to serialize CompletionResult for cache", e);
            return new byte[0];
        }
    }

    private CompletionResult deserializeCompletion(byte[] bytes) {
        try {
            return JsonUtils.fromJson(new ByteArrayInputStream(bytes), CompletionResult.class);
        } catch (Exception e) {
            logger.warn("Failed to deserialize cached CompletionResult; cache miss", e);
            return null;
        }
    }
}

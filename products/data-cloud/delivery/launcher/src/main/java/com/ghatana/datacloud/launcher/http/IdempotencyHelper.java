package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.spi.WriteIdempotencyStore;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic idempotency helper for mutating HTTP requests.
 *
 * <p>Provides reusable idempotency checking and storage logic for all mutating routes
 * (POST, PUT, DELETE, PATCH). Uses the X-Idempotency-Key header to identify retried requests
 * and return cached responses without re-executing the write operation.
 *
 * <h2>DC-BE-002: Idempotency Infrastructure</h2>
 * This helper replaces the idempotency logic previously embedded in EntityCrudHandler,
 * making it reusable across all handlers:
 * - EntityCrudHandler (entity CRUD operations)
 * - PipelineHandler (pipeline creation/update)
 * - EventHandler (event append)
 * - GovernanceHandler (governance operations)
 * - AnalyticsHandler (analytics queries)
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In handler constructor
 * private final IdempotencyHelper idempotencyHelper;
 *
 * // Check idempotency before executing write
 * String idempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
 * Promise<HttpResponse> cachedResponse = idempotencyHelper.checkIdempotencyOrNull(
 *     tenantId, operationScope, idempotencyKey);
 * if (cachedResponse != null) return cachedResponse;
 *
 * // After successful write, store response for future retries
 * idempotencyHelper.storeIdempotency(tenantId, operationScope, idempotencyKey, responseBody);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Generic idempotency helper for all mutating HTTP requests
 * @doc.layer product
 * @doc.pattern Helper
 */
public class IdempotencyHelper {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyHelper.class);

    private final WriteIdempotencyStore idempotencyStore;
    private final HttpHandlerSupport http;

    // In-memory fallback for embedded/local profiles (lost on restart)
    private final Map<String, IdempotencyEntry> inMemoryStore = new ConcurrentHashMap<>();
    private static final int IDEMPOTENCY_MAX_ENTRIES = 10_000;
    private static final long IDEMPOTENCY_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private record IdempotencyEntry(Map<String, Object> responseBody, Instant storedAt) {}

    /**
     * Creates an idempotency helper with a durable store.
     *
     * @param idempotencyStore durable idempotency store (required for non-embedded profiles)
     * @param http              HTTP helper for creating responses
     */
    public IdempotencyHelper(WriteIdempotencyStore idempotencyStore, HttpHandlerSupport http) {
        this.idempotencyStore = idempotencyStore;
        this.http = http;
    }

    /**
     * Creates an idempotency helper with in-memory only storage.
     * Suitable for embedded/local profiles where durability is not required.
     *
     * @param http HTTP helper for creating responses
     */
    public IdempotencyHelper(HttpHandlerSupport http) {
        this.idempotencyStore = null;
        this.http = http;
    }

    /**
     * Checks if a cached response exists for the given idempotency key.
     *
     * @param tenantId        tenant owning the request
     * @param operationScope  scope identifier for the operation (e.g., "entities:collection", "pipelines")
     * @param idempotencyKey  caller-supplied idempotency key from X-Idempotency-Key header
     * @return cached HTTP response if found and valid, null if not found or expired
     */
    public Promise<HttpResponse> checkIdempotencyOrNull(String tenantId,
                                                         String operationScope,
                                                         String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }

        // Prefer durable store when available (non-embedded profiles)
        if (idempotencyStore != null) {
            Optional<Map<String, Object>> cached = idempotencyStore.get(tenantId, operationScope, idempotencyKey);
            if (cached.isPresent()) {
                log.info("[idempotency] Returning durable cached response for scope={}, key={}",
                    operationScope, idempotencyKey);
                return Promise.of(http.jsonResponse(cached.get()));
            }
            return null;
        }

        // Fall back to in-memory store for local/embedded profiles
        String key = inMemoryKey(tenantId, operationScope, idempotencyKey);
        IdempotencyEntry entry = inMemoryStore.get(key);
        if (entry != null && Instant.now().minusMillis(IDEMPOTENCY_TTL_MS).isBefore(entry.storedAt())) {
            log.info("[idempotency] Returning in-memory cached response for scope={}, key={}",
                operationScope, key);
            return Promise.of(http.jsonResponse(entry.responseBody()));
        }
        return null;
    }

    /**
     * Stores the response body for the given idempotency key.
     *
     * @param tenantId        tenant owning the request
     * @param operationScope  scope identifier for the operation (e.g., "entities:collection", "pipelines")
     * @param idempotencyKey  caller-supplied idempotency key from X-Idempotency-Key header
     * @param responseBody    response body to cache
     */
    public void storeIdempotency(String tenantId,
                                 String operationScope,
                                 String idempotencyKey,
                                 Map<String, Object> responseBody) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        // Prefer durable store when available
        if (idempotencyStore != null) {
            idempotencyStore.put(tenantId, operationScope, idempotencyKey, responseBody);
            return;
        }

        // In-memory fallback: evict expired entries when approaching capacity
        if (inMemoryStore.size() >= IDEMPOTENCY_MAX_ENTRIES) {
            Instant cutoff = Instant.now().minusMillis(IDEMPOTENCY_TTL_MS);
            inMemoryStore.entrySet().removeIf(e -> e.getValue().storedAt().isBefore(cutoff));
        }
        inMemoryStore.put(
            inMemoryKey(tenantId, operationScope, idempotencyKey),
            new IdempotencyEntry(responseBody, Instant.now()));
    }

    private String inMemoryKey(String tenantId, String operationScope, String idempotencyKey) {
        return tenantId + "/" + operationScope + "/" + idempotencyKey;
    }
}

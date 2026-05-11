package com.ghatana.platform.observability.idempotency;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Helper for idempotency checks and storage.
 *
 * <p>P0-07: Provides utilities for checking idempotency keys, computing payload hashes,
 * and handling idempotency conflicts. Handlers use this to implement idempotent behavior.
 *
 * @doc.type class
 * @doc.purpose Idempotency helper utilities for handlers
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class IdempotencyHelper {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyHelper.class);
    private static final String HEADER_IDEMPOTENCY_KEY = "X-Idempotency-Key";
    private static final String HEADER_IDEMPOTENCY_RESULT = "X-Idempotency-Result";

    private IdempotencyHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Extracts the idempotency key from the request headers.
     *
     * @param request the HTTP request
     * @return the idempotency key, or null if not present
     */
    public static String extractIdempotencyKey(HttpRequest request) {
        return request.getHeader(HttpHeaders.of(HEADER_IDEMPOTENCY_KEY));
    }

    /**
     * Computes a hash of the request body payload for conflict detection.
     *
     * @param request the HTTP request
     * @return SHA-256 hash of the payload, or empty string if no body
     */
    public static String computePayloadHash(HttpRequest request) {
        var buf = request.getBody();
        if (buf == null || buf.readRemaining() == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = buf.getString(StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("[Idempotency] Failed to compute payload hash", e);
            return "";
        }
    }

    /**
     * Checks if an operation with the given idempotency key has already been executed.
     *
     * @param store the idempotency store
     * @param tenantId the tenant ID
     * @param scope the idempotency scope (route action + resource ID)
     * @param idempotencyKey the client-provided idempotency key
     * @param principalId the principal/client ID
     * @return a promise that completes with the cached response, or null if not found
     */
    public static Promise<Object> checkIdempotency(IdempotencyStore store, String tenantId, String scope,
                                                  String idempotencyKey, String principalId) {
        return store.get(tenantId, scope, idempotencyKey, principalId)
            .map(entry -> {
                if (entry != null) {
                    log.info("[Idempotency] Returning cached response for scope={}, key={}", scope, idempotencyKey);
                    return entry.response();
                }
                return null;
            });
    }

    /**
     * Checks for a conflict (same key with different payload).
     *
     * @param store the idempotency store
     * @param tenantId the tenant ID
     * @param scope the idempotency scope
     * @param idempotencyKey the client-provided idempotency key
     * @param principalId the principal/client ID
     * @param payloadHash hash of the current request payload
     * @return a promise that completes with true if there's a conflict
     */
    public static Promise<Boolean> checkConflict(IdempotencyStore store, String tenantId, String scope,
                                               String idempotencyKey, String principalId, String payloadHash) {
        return store.hasConflict(tenantId, scope, idempotencyKey, principalId, payloadHash);
    }

    /**
     * Stores the response for an idempotent operation.
     *
     * @param store the idempotency store
     * @param tenantId the tenant ID
     * @param scope the idempotency scope
     * @param idempotencyKey the client-provided idempotency key
     * @param principalId the principal/client ID
     * @param payloadHash hash of the request payload
     * @param response the response to cache
     * @return a promise that completes when the entry is stored
     */
    public static Promise<Void> storeResponse(IdempotencyStore store, String tenantId, String scope,
                                              String idempotencyKey, String principalId, String payloadHash,
                                              Object response) {
        return store.put(tenantId, scope, idempotencyKey, principalId, payloadHash, response);
    }

    /**
     * Adds idempotency headers to a response.
     *
     * @param response the HTTP response
     * @param result the idempotency result (replay or original)
     * @return the response with idempotency headers
     */
    public static HttpResponse addIdempotencyHeaders(HttpResponse response, String result) {
        // Since HttpResponse is immutable in ActiveJ, we return the response as-is.
        // Headers should be added during response construction using the builder pattern.
        // This method is kept for API compatibility but does not modify the response.
        return response;
    }

    /**
     * Gets the idempotency result header name.
     *
     * @return the header name for idempotency result
     */
    public static String getIdempotencyResultHeader() {
        return HEADER_IDEMPOTENCY_RESULT;
    }

    /**
     * Gets the idempotency key header name.
     *
     * @return the header name for idempotency key
     */
    public static String getIdempotencyKeyHeader() {
        return HEADER_IDEMPOTENCY_KEY;
    }
}

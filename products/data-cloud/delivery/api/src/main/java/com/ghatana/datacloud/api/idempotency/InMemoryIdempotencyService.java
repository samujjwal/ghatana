/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.idempotency;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory implementation of {@link IdempotencyService}.
 *
 * <p>Use this implementation for process-local deployments or inject a durable
 * {@link IdempotencyService} when idempotency records must survive restarts.
 *
 * @doc.type class
 * @doc.purpose In-memory idempotency key storage
 * @doc.layer product
 * @doc.pattern Service Implementation
 */
public class InMemoryIdempotencyService implements IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryIdempotencyService.class);
    private static final Duration TTL = Duration.ofHours(24);

    private final Map<String, IdempotencyRecord> store = new ConcurrentHashMap<>();
    private final Set<String> nonIdempotentPaths = Set.of(
        "/api/v1/action/pipelines/",
        "/api/v1/alerts/",
        "/api/v1/brain/attention/",
        "/api/v1/brain/patterns/",
        "/api/v1/action/learning/",
        "/api/v1/mastery/obsolescence/",
        "/api/v1/mastery/learning-deltas/",
        "/api/v1/mastery/",
        "/api/v1/action/memory/"
    );

    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    public InMemoryIdempotencyService() {
        // Schedule periodic cleanup of expired keys
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredKeys, 1, 1, TimeUnit.HOURS);
        log.info("[idempotency] InMemoryIdempotencyService initialized with TTL={}", TTL);
    }

    @Override
    public Promise<Optional<IdempotencyRecord>> get(String key) {
        IdempotencyRecord record = store.get(key);
        if (record == null) {
            return Promise.of(Optional.empty());
        }
        
        // Check if expired
        if (Instant.now().isAfter(Instant.ofEpochMilli(record.createdAt()).plus(TTL))) {
            store.remove(key);
            return Promise.of(Optional.empty());
        }
        
        return Promise.of(Optional.of(record));
    }

    @Override
    public Promise<Void> store(String key, IdempotencyRecord record) {
        store.put(key, record);
        log.debug("[idempotency] Stored key={} method={} path={}", key, record.method(), record.path());
        return Promise.complete();
    }

    @Override
    public Promise<Void> delete(String key) {
        store.remove(key);
        log.debug("[idempotency] Deleted key={}", key);
        return Promise.complete();
    }

    @Override
    public boolean isIdempotentOperation(String method, String path) {
        // POST/PUT/PATCH operations may be idempotent
        if (!method.equals("POST") && !method.equals("PUT") && !method.equals("PATCH")) {
            return true; // GET, DELETE are naturally idempotent
        }

        // Check if path is in non-idempotent list
        for (String nonIdempotentPath : nonIdempotentPaths) {
            if (path.startsWith(nonIdempotentPath)) {
                return false;
            }
        }

        return true;
    }

    private void cleanupExpiredKeys() {
        Instant cutoff = Instant.now().minus(TTL);
        int before = store.size();
        
        store.entrySet().removeIf(entry -> {
            Instant createdAt = Instant.ofEpochMilli(entry.getValue().createdAt());
            return createdAt.isBefore(cutoff);
        });
        
        int after = store.size();
        log.info("[idempotency] Cleanup: removed {} expired keys, {} remaining", before - after, after);
    }

    /**
     * Hash request body for comparison.
     */
    public static String hashRequestBody(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("[idempotency] Failed to hash request body", e);
            return String.valueOf(body.hashCode());
        }
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

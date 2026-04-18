/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.auth;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory {@link TokenBlocklist} for testing only.
 *
 * <p><b>WARNING:</b> This implementation stores blocked tokens in memory
 * and does not persist across restarts. Do NOT use in production.
 *
 * @doc.type class
 * @doc.purpose In-memory token blocklist for testing
 * @doc.layer product
 * @doc.pattern Repository
 */
public class InMemoryTokenBlocklist implements TokenBlocklist {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTokenBlocklist.class);

    private final Map<String, Long> blocklist = new ConcurrentHashMap<>();
    private final AtomicInteger cleanupCount = new AtomicInteger(0);

    @Override
    @NotNull
    public Promise<Void> block(@NotNull String jti, long expiresAt) {
        blocklist.put(jti, expiresAt);
        log.debug("Blocked token jti={} (expiresAt={})", jti, expiresAt);
        return Promise.complete();
    }

    @Override
    @NotNull
    public Promise<Boolean> isBlocked(@NotNull String jti) {
        Long expiresAt = blocklist.get(jti);
        if (expiresAt == null) {
            return Promise.of(false);
        }
        
        // Check if expired
        long now = System.currentTimeMillis();
        if (expiresAt < now) {
            blocklist.remove(jti);
            return Promise.of(false);
        }
        
        log.debug("Token jti={} is blocked", jti);
        return Promise.of(true);
    }

    @Override
    @NotNull
    public Promise<Integer> cleanupExpired() {
        long now = System.currentTimeMillis();
        int before = blocklist.size();
        blocklist.entrySet().removeIf(entry -> entry.getValue() < now);
        int removed = before - blocklist.size();
        cleanupCount.addAndGet(removed);
        
        if (removed > 0) {
            log.info("Cleaned up {} expired blocklist entries", removed);
        }
        
        return Promise.of(removed);
    }

    /**
     * Returns the current number of blocked tokens (for testing).
     */
    public int size() {
        return blocklist.size();
    }

    /**
     * Returns the total number of cleanup runs (for testing).
     */
    public int getCleanupCount() {
        return cleanupCount.get();
    }
}

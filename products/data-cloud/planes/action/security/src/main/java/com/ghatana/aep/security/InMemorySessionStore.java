/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T-20: In-memory {@link SessionStore} for development and test environments.
 *
 * <p>Maintains session tokens in a {@link ConcurrentHashMap} keyed by token
 * string, with expiry stored as epoch-second timestamps. Expired entries are
 * lazily evicted on each {@link #isValid} call.
 *
 * <p><b>Not suitable for production</b> — state is lost on restart and
 * is not shared across multiple server instances. Use {@link RedisSessionStore}
 * in production.
 *
 * @doc.type class
 * @doc.purpose In-memory session store for dev/test
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, Long> sessions = new ConcurrentHashMap<>();

    @Override
    public void put(String token, Duration ttl) {
        long expiryEpoch = System.currentTimeMillis() / 1000L + ttl.toSeconds();
        sessions.put(token, expiryEpoch);
    }

    @Override
    public boolean isValid(String token) {
        Long expiry = sessions.get(token);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() / 1000L > expiry) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    @Override
    public void remove(String token) {
        sessions.remove(token);
    }

    /**
     * Evicts all expired entries from the backing map.
     * Called lazily by {@link SessionFilter} on each request.
     */
    void evictExpired() {
        long now = System.currentTimeMillis() / 1000L;
        sessions.entrySet().removeIf(e -> e.getValue() < now);
    }
}

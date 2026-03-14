/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.security;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.Executor;

/**
 * Brute-force protection via Redis counters and account lockout (STORY-K01-005).
 *
 * <h3>Policy</h3>
 * <ul>
 *   <li>Per-IP: 10 failures within 60 s → HTTP 429 (Too Many Requests)</li>
 *   <li>Per-account: 5 failures within 900 s → account locked for 900 s</li>
 * </ul>
 *
 * <h3>Redis key layout</h3>
 * <pre>
 * rl:fail:ip:{ip}        — INCR counter, TTL 60 s
 * rl:fail:acct:{userId}  — INCR counter, TTL 900 s
 * rl:lock:{userId}        — SET marker, TTL 900 s (lock sentinel)
 * </pre>
 *
 * <p>All Redis operations execute on the provided blocking {@code executor} so the
 * ActiveJ event loop is never blocked.
 *
 * @doc.type class
 * @doc.purpose Brute-force detection and account lockout (K01-005)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class BruteForceGuard {

    private static final Logger log = LoggerFactory.getLogger(BruteForceGuard.class);

    // Key prefixes
    private static final String PREFIX_IP   = "rl:fail:ip:";
    private static final String PREFIX_ACCT = "rl:fail:acct:";
    private static final String PREFIX_LOCK = "rl:lock:";

    // Thresholds
    private static final int  IP_LIMIT   = 10;
    private static final long IP_TTL_S   = 60L;
    private static final int  ACCT_LIMIT = 5;
    private static final long ACCT_TTL_S = 900L;

    private final JedisPool jedis;
    private final Executor  executor;

    /**
     * @param jedis    connection pool to Redis
     * @param executor blocking executor for Redis I/O
     */
    public BruteForceGuard(JedisPool jedis, Executor executor) {
        this.jedis = jedis;
        this.executor = executor;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Records a login failure for the given source IP and account.
     *
     * <p>Returns the resulting {@link BruteForceDecision} indicating whether the
     * IP or account has breached its limit and should be rejected.
     *
     * @param sourceIp client IP address
     * @param userId   account identifier (pass {@code null} if unknown at this stage)
     * @return async {@link BruteForceDecision}
     */
    public Promise<BruteForceDecision> recordFailure(String sourceIp, String userId) {
        return Promise.ofBlocking(executor, () -> {
            boolean ipBlocked   = incrementAndCheck(PREFIX_IP + sourceIp, IP_LIMIT, IP_TTL_S);
            boolean acctLocked  = false;

            if (userId != null) {
                acctLocked = incrementAndCheck(PREFIX_ACCT + userId, ACCT_LIMIT, ACCT_TTL_S);
                if (acctLocked) {
                    lockAccount(userId);
                }
            }

            if (ipBlocked) {
                log.warn("IP rate limit reached: ip={}", sourceIp);
            }
            if (acctLocked) {
                log.warn("Account locked due to brute-force: user={}", userId);
            }

            BruteForceDecision.Reason reason =
                acctLocked ? BruteForceDecision.Reason.ACCOUNT_LOCKED
                : ipBlocked ? BruteForceDecision.Reason.IP_RATE_LIMITED
                : BruteForceDecision.Reason.NONE;
            return new BruteForceDecision(reason);
        });
    }

    /**
     * Returns {@code true} if the account currently has a lock sentinel in Redis.
     *
     * @param userId account identifier
     * @return async boolean
     */
    public Promise<Boolean> isLocked(String userId) {
        return Promise.ofBlocking(executor, () -> {
            try (var j = jedis.getResource()) {
                return j.exists(PREFIX_LOCK + userId);
            }
        });
    }

    /**
     * Clears failure counters and any lock marker on successful authentication.
     * Should be called after a successful credential check.
     *
     * @param userId account identifier
     * @return async completion
     */
    public Promise<Void> clearFailures(String userId) {
        return Promise.ofBlocking(executor, () -> {
            try (var j = jedis.getResource()) {
                j.del(PREFIX_ACCT + userId, PREFIX_LOCK + userId);
            }
            log.debug("Cleared brute-force counters for user={}", userId);
            return null;
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * INCR the counter key; set its TTL on first increment; return true if limit exceeded.
     */
    private boolean incrementAndCheck(String key, int limit, long ttlSeconds) {
        try (var j = jedis.getResource()) {
            long count = j.incr(key);
            if (count == 1L) {
                // First failure in this window — set expiry
                j.expire(key, ttlSeconds);
            }
            return count >= limit;
        }
    }

    /**
     * Sets a lock sentinel with a 15-minute TTL (matches account failure window).
     * Using NX (set-if-absent) is intentionally omitted: re-locking on each
     * over-threshold failure refreshes the lockout window, which is the desired
     * behaviour (attacker keeps extending their own lockout).
     */
    private void lockAccount(String userId) {
        try (var j = jedis.getResource()) {
            j.setex(PREFIX_LOCK + userId, ACCT_TTL_S, "locked");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Result type
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Outcome of a failed login attempt evaluation.
     *
     * @param reason why the request should be blocked, or {@link Reason#NONE} if still within limits
     */
    public record BruteForceDecision(Reason reason) {

        /** Whether the request should be rejected as a result of brute-force limits. */
        public boolean shouldBlock() {
            return reason != Reason.NONE;
        }

        public enum Reason {
            /** Below all thresholds. */
            NONE,
            /** Per-IP attempt limit reached. */
            IP_RATE_LIMITED,
            /** Per-account failure limit reached — account is now locked. */
            ACCOUNT_LOCKED
        }
    }
}

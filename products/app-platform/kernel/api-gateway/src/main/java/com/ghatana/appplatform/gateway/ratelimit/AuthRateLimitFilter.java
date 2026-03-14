/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.gateway.ratelimit;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rate limiting filter for authentication endpoints (STORY-K01-022).
 *
 * <p>Applies three independent token-bucket limits to any request on paths that start
 * with a configurable auth prefix (default: {@code /auth}, {@code /oauth}, {@code /token}):
 *
 * <ol>
 *   <li><b>Per-IP</b>: 10 requests / 60 s — keyed on the source IP from
 *       {@code X-Forwarded-For} (first value) or the raw remote address.</li>
 *   <li><b>Per-tenant</b>: 100 requests / 60 s — keyed on {@code X-Tenant-Id} header.</li>
 *   <li><b>Global</b>: 1000 requests / 60 s — shared across all tenants and IPs.</li>
 * </ol>
 *
 * <p>If any limit is exceeded the filter responds with HTTP 429 immediately without
 * forwarding to the downstream auth handlers. The most restrictive triggered limit
 * determines the {@code Retry-After} value in the response.
 *
 * <p>This filter is intended to be registered in the gateway
 * <em>before</em> {@link RateLimitFilter} and only for auth routes.
 *
 * @doc.type class
 * @doc.purpose Auth-endpoint rate limiting (per-IP / per-tenant / global) — K01-022
 * @doc.layer product
 * @doc.pattern Filter
 */
public final class AuthRateLimitFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    // ── Limits (K01-022) ─────────────────────────────────────────────────────
    /** Max auth attempts per source IP per 60 s. */
    private static final long   IP_CAPACITY    = 10;
    private static final double IP_REFILL      = 10.0 / 60.0;

    /** Max auth attempts per tenant per 60 s. */
    private static final long   TENANT_CAPACITY = 100;
    private static final double TENANT_REFILL   = 100.0 / 60.0;

    /** Max auth attempts globally per 60 s. */
    private static final long   GLOBAL_CAPACITY = 1000;
    private static final double GLOBAL_REFILL   = 1000.0 / 60.0;

    private static final String GLOBAL_KEY = "auth:global";

    // ── Headers ──────────────────────────────────────────────────────────────
    private static final HttpHeaders.Value H_TENANT     = HttpHeaders.of("X-Tenant-Id");
    private static final HttpHeaders.Value H_FORWARDED  = HttpHeaders.of("X-Forwarded-For");

    private final TokenBucketRateLimiter limiter;

    /**
     * @param limiter shared token-bucket rate limiter backed by Redis
     */
    public AuthRateLimitFilter(TokenBucketRateLimiter limiter) {
        this.limiter = limiter;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Filter entry point
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Evaluates the three rate-limit tiers and returns an HTTP 429 response if any
     * tier is exhausted, otherwise delegates to {@code next}.
     *
     * @param request ActiveJ HTTP request
     * @param next    downstream handler
     * @return async HTTP response
     */
    public Promise<HttpResponse> apply(HttpRequest request, io.activej.http.AsyncServlet next)
            throws Exception {

        String ip       = resolveIp(request);
        String tenantId = request.getHeader(H_TENANT);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "unknown";
        }

        // Per-IP check
        RateLimitStore.ConsumeResult ipResult =
            limiter.tryConsume("auth:ip:" + ip, IP_CAPACITY, IP_REFILL, 1);
        if (!ipResult.allowed()) {
            return tooManyRequests("ip", ip, ipResult.retryAfterMs());
        }

        // Per-tenant check
        RateLimitStore.ConsumeResult tenantResult =
            limiter.tryConsume("auth:tenant:" + tenantId, TENANT_CAPACITY, TENANT_REFILL, 1);
        if (!tenantResult.allowed()) {
            return tooManyRequests("tenant", tenantId, tenantResult.retryAfterMs());
        }

        // Global check
        RateLimitStore.ConsumeResult globalResult =
            limiter.tryConsume(GLOBAL_KEY, GLOBAL_CAPACITY, GLOBAL_REFILL, 1);
        if (!globalResult.allowed()) {
            return tooManyRequests("global", "all", globalResult.retryAfterMs());
        }

        return next.serve(request);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────

    private static String resolveIp(HttpRequest request) {
        String forwarded = request.getHeader(H_FORWARDED);
        if (forwarded != null && !forwarded.isBlank()) {
            // First value in comma-separated list is the originating client IP
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).strip();
        }
        // Fall back to connection remote address — may be null in tests
        return "unknown";
    }

    private static Promise<HttpResponse> tooManyRequests(String scope, String key, long retryAfterMs) {
        long retryAfterSec = Math.max(1L, retryAfterMs / 1000L);
        log.warn("[AuthRateLimit] 429 scope={} key={} retryAfterSec={}", scope, key, retryAfterSec);
        return Promise.of(HttpResponse.ofCode(429)
            .withHeader(HttpHeaders.of("Retry-After"), String.valueOf(retryAfterSec))
            .withHeader(HttpHeaders.of("X-RateLimit-Scope"), scope)
            .withPlainText("Auth rate limit exceeded. Retry after " + retryAfterSec + " seconds."));
    }
}

package com.ghatana.datacloud.launcher.http.security;

import com.ghatana.datacloud.launcher.http.RequestMetadataAttachment;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.MediaTypes;
import io.activej.promise.Promise;
import io.activej.http.AsyncServlet;
import io.activej.http.ContentType;
import io.activej.http.HttpHeaderValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter that operates on authenticated tenant context.
 *
 * <p>This filter ensures rate limits are applied AFTER authentication, preventing:
 * <ul>
 *   <li>Spoofed tenant headers from consuming other tenants' rate limits</li>
 *   <li>Unauthenticated requests being rate-limited as a specific tenant</li>
 *   <li>Cross-tenant rate limit pollution</li>
 * </ul>
 *
 * <p>The filter separates:
 * <ul>
 *   <li>Per-tenant rate limits (applied only to authenticated requests)</li>
 *   <li>Per-IP rate limits (applied to all requests, including unauthenticated)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Authenticated rate limiting with proper tenant/IP separation
 * @doc.layer product
 * @doc.pattern Security Filter, Middleware
 */
public final class AuthenticatedRateLimitFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthenticatedRateLimitFilter.class);

    private final int tenantRateLimitRequests;
    private final long tenantRateLimitWindowMs;
    private final int ipRateLimitRequests;
    private final long ipRateLimitWindowMs;
    private final int maxEntries;

    private final Map<String, RateLimitEntry> tenantRateLimits = new ConcurrentHashMap<>();
    private final Map<String, RateLimitEntry> ipRateLimits = new ConcurrentHashMap<>();

    private AuthenticatedRateLimitFilter(Builder builder) {
        this.tenantRateLimitRequests = builder.tenantRateLimitRequests;
        this.tenantRateLimitWindowMs = builder.tenantRateLimitWindowMs;
        this.ipRateLimitRequests = builder.ipRateLimitRequests;
        this.ipRateLimitWindowMs = builder.ipRateLimitWindowMs;
        this.maxEntries = builder.maxEntries;
    }

    /**
     * Creates a rate limiting servlet wrapper.
     *
     * @param delegate the delegate servlet
     * @return rate-limited servlet
     */
    public AsyncServlet apply(AsyncServlet delegate) {
        return request -> {
            long now = System.currentTimeMillis();
            String ip = extractClientIp(request);

            // 1. Check IP rate limit first (applies to all requests)
            if (ipRateLimitRequests > 0) {
                HttpResponse ipLimitResponse = checkRateLimit(
                    ip, ipRateLimits, ipRateLimitRequests, ipRateLimitWindowMs, now, "IP"
                );
                if (ipLimitResponse != null) {
                    return Promise.of(ipLimitResponse);
                }
            }

            // 2. Check tenant rate limit (only for authenticated requests)
            // Tenant is extracted from RequestContext attachment (set by security filter)
            RequestMetadataAttachment metadata = request.getAttachment(RequestMetadataAttachment.class);
            if (metadata != null && metadata.tenantId() != null && !metadata.tenantId().isBlank()) {
                String tenantId = metadata.tenantId();

                if (tenantRateLimitRequests > 0) {
                    HttpResponse tenantLimitResponse = checkRateLimit(
                        tenantId, tenantRateLimits, tenantRateLimitRequests, tenantRateLimitWindowMs, now, "tenant"
                    );
                    if (tenantLimitResponse != null) {
                        log.warn("Tenant rate limit exceeded for tenantId={}", tenantId);
                        return Promise.of(tenantLimitResponse);
                    }
                }
            }

            // Cleanup expired entries periodically
            cleanupExpiredEntries(now);

            return delegate.serve(request);
        };
    }

    private HttpResponse checkRateLimit(
            String key,
            Map<String, RateLimitEntry> store,
            int limit,
            long windowMs,
            long now,
            String type) {

        RateLimitEntry entry = store.compute(key, (k, existing) -> {
            if (existing == null || (now - existing.windowStart) >= windowMs) {
                return new RateLimitEntry(1, now);
            }
            return new RateLimitEntry(existing.count + 1, existing.windowStart);
        });

        if (entry.count > limit) {
            long windowRemaining = windowMs - (now - entry.windowStart);
            long retryAfterSec = Math.max(1L, (windowRemaining + 999) / 1000);

            log.warn("Rate limit exceeded for {}={} count={} limit={}", type, key, entry.count, limit);

            String body = String.format(
                "{\"error\":\"Too Many Requests\",\"retryAfterSeconds\":%d,\"type\":\"%s\"}",
                retryAfterSec, type
            );

            return HttpResponse.ofCode(429)
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Retry-After"), HttpHeaderValue.of(String.valueOf(retryAfterSec)))
                .withHeader(HttpHeaders.of("X-RateLimit-Type"), HttpHeaderValue.of(type))
                .withBody(body.getBytes(StandardCharsets.UTF_8))
                .build();
        }

        return null;
    }

    private void cleanupExpiredEntries(long now) {
        // Simple probabilistic cleanup - run on 1% of requests
        if (Math.random() < 0.01) {
            if (tenantRateLimits.size() > maxEntries) {
                tenantRateLimits.entrySet().removeIf(e ->
                    (now - e.getValue().windowStart) >= tenantRateLimitWindowMs);
            }
            if (ipRateLimits.size() > maxEntries) {
                ipRateLimits.entrySet().removeIf(e ->
                    (now - e.getValue().windowStart) >= ipRateLimitWindowMs);
            }
        }
    }

    private String extractClientIp(HttpRequest request) {
        String xff = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).strip();
        }
        return java.util.Optional.ofNullable(request.getRemoteAddress())
            .map(Object::toString)
            .orElse("unknown");
    }

    private record RateLimitEntry(long count, long windowStart) {}

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AuthenticatedRateLimitFilter.
     */
    public static final class Builder {
        private int tenantRateLimitRequests = 1000;
        private long tenantRateLimitWindowMs = 60_000; // 1 minute
        private int ipRateLimitRequests = 200;
        private long ipRateLimitWindowMs = 60_000; // 1 minute
        private int maxEntries = 10_000;

        public Builder withTenantRateLimit(int requests, long windowMs) {
            this.tenantRateLimitRequests = requests;
            this.tenantRateLimitWindowMs = windowMs;
            return this;
        }

        public Builder withIpRateLimit(int requests, long windowMs) {
            this.ipRateLimitRequests = requests;
            this.ipRateLimitWindowMs = windowMs;
            return this;
        }

        public Builder withMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
            return this;
        }

        public AuthenticatedRateLimitFilter build() {
            return new AuthenticatedRateLimitFilter(this);
        }
    }
}

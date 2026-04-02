package com.ghatana.platform.governance.security;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Token-bucket rate limiting filter for ActiveJ HTTP endpoints.
 *
 * <p>Tracks request timestamps per client key (by default the {@code X-Forwarded-For} header,
 * falling back to the remote address). When a client exceeds {@code maxRequests} within a
 * {@code windowSeconds} sliding window, the filter responds with HTTP 429 Too Many Requests.
 *
 * <p>The internal state map is bounded in practice by the number of distinct clients that have
 * been active within the retention window. Entries for inactive clients are pruned on every
 * request by the same client once the window has expired.
 *
 * <p>Usage:
 * <pre>{@code
 * RateLimitFilter rateLimiter = new RateLimitFilter(100, 60); // 100 req / 60 s
 * AsyncServlet rateLimitedServlet = rateLimiter.wrap(myServlet);
 *
 * RateLimitFilter tenantAwareLimiter = new RateLimitFilter(
 *         100,
 *         60,
 *         request -> request.getHeader(HttpHeaders.of("X-Tenant-ID")));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Sliding-window rate limiting middleware for ActiveJ HTTP servlets
 * @doc.layer platform
 * @doc.pattern Filter
 */
public class RateLimitFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final int maxRequests;
    private final long windowSeconds;
    private final Function<io.activej.http.HttpRequest, String> clientKeyResolver;
    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    /**
     * Creates a rate limiter with the given parameters.
     *
     * @param maxRequests   maximum number of allowed requests per client within the window
     * @param windowSeconds sliding window size in seconds
     */
    public RateLimitFilter(int maxRequests, long windowSeconds) {
        this(maxRequests, windowSeconds, RateLimitFilter::defaultClientKey);
    }

    /**
     * Creates a rate limiter with a custom client-key extractor.
     *
     * @param maxRequests maximum number of allowed requests per client within the window
     * @param windowSeconds sliding window size in seconds
     * @param clientKeyResolver function used to derive the client bucket key from each request
     */
    public RateLimitFilter(
            int maxRequests,
            long windowSeconds,
            Function<io.activej.http.HttpRequest, String> clientKeyResolver) {
        if (maxRequests <= 0) throw new IllegalArgumentException("maxRequests must be > 0");
        if (windowSeconds <= 0) throw new IllegalArgumentException("windowSeconds must be > 0");
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.clientKeyResolver = Objects.requireNonNull(clientKeyResolver, "clientKeyResolver");
    }

    /**
     * Wraps a delegate servlet with rate-limit enforcement.
     *
     * @param delegate the servlet to protect
     * @return a new servlet that applies rate limiting before delegating
     */
    public AsyncServlet wrap(AsyncServlet delegate) {
        return request -> {
            String clientKey = resolveClientKey(request);
            if (isRateLimited(clientKey)) {
                log.warn("Rate limit exceeded for client: {}", clientKey);
                return Promise.of(tooManyRequests());
            }
            return delegate.serve(request);
        };
    }

    private String resolveClientKey(io.activej.http.HttpRequest request) {
        String resolvedKey = clientKeyResolver.apply(request);
        if (resolvedKey != null && !resolvedKey.isBlank()) {
            return resolvedKey.trim();
        }
        return defaultClientKey(request);
    }

    private static String defaultClientKey(io.activej.http.HttpRequest request) {
        String forwarded = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (forwarded != null && !forwarded.isBlank()) {
            // Use only the first address if there are multiple (proxy chain)
            int commaIdx = forwarded.indexOf(',');
            return commaIdx > 0 ? forwarded.substring(0, commaIdx).trim() : forwarded.trim();
        }
        var remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.toString() : "unknown";
    }

    private boolean isRateLimited(String clientKey) {
        long now = Instant.now().getEpochSecond();
        long windowStart = now - windowSeconds;

        Deque<Long> timestamps = requestLog.computeIfAbsent(clientKey, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // Remove timestamps outside the window
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxRequests) {
                return true;
            }

            timestamps.addLast(now);
            return false;
        }
    }

    private static HttpResponse tooManyRequests() {
        return HttpResponse.ofCode(429)
                .withHeader(HttpHeaders.of("Retry-After"), "60")
                .withBody("Too Many Requests".getBytes())
                .build();
    }
}

package com.ghatana.platform.http.security.filter;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
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
 * @doc.type class
 * @doc.purpose Sliding-window rate limiting middleware for ActiveJ HTTP servlets
 * @doc.layer platform
 * @doc.pattern Filter
 */
public class RateLimitFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final int maxRequests;
    private final long windowSeconds;
    private final Function<HttpRequest, String> clientKeyResolver;
    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    public RateLimitFilter(int maxRequests, long windowSeconds) {
        this(maxRequests, windowSeconds, RateLimitFilter::defaultClientKey);
    }

    public RateLimitFilter(
            int maxRequests,
            long windowSeconds,
            Function<HttpRequest, String> clientKeyResolver) {
        if (maxRequests <= 0) throw new IllegalArgumentException("maxRequests must be > 0");
        if (windowSeconds <= 0) throw new IllegalArgumentException("windowSeconds must be > 0");
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.clientKeyResolver = Objects.requireNonNull(clientKeyResolver, "clientKeyResolver");
    }

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

    private String resolveClientKey(HttpRequest request) {
        String resolvedKey = clientKeyResolver.apply(request);
        if (resolvedKey != null && !resolvedKey.isBlank()) {
            return resolvedKey.trim();
        }
        return defaultClientKey(request);
    }

    private static String defaultClientKey(HttpRequest request) {
        String forwarded = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (forwarded != null && !forwarded.isBlank()) {
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

/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.middleware;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP middleware that enforces per-client request rate limiting.
 *
 * <h2>Algorithm</h2>
 *
 * Fixed-window counter: each client (identified by {@code X-Forwarded-For} or the TCP remote
 * address) is allowed at most {@code maxRequests} requests within a rolling {@code
 * windowMillis}-millisecond window. When the limit is exceeded the middleware returns {@code 429
 * Too Many Requests} immediately and the delegate servlet is never invoked.
 *
 * <h2>Headers</h2>
 *
 * Every response carries:
 *
 * <ul>
 *   <li>{@code X-RateLimit-Limit} — configured maximum per window
 *   <li>{@code X-RateLimit-Remaining} — remaining requests in the current window
 *   <li>{@code X-RateLimit-Reset} — epoch-second at which the window resets (only present when the
 *       limit has been reached)
 *   <li>{@code Retry-After} — seconds until reset (only on 429 responses)
 * </ul>
 *
 * <h2>Thread Safety</h2>
 *
 * All mutable state lives in a {@link ConcurrentHashMap}. Each bucket is an immutable snapshot
 * replaced atomically via {@link ConcurrentHashMap#compute}, so no external locking is required.
 *
 * @doc.type class
 * @doc.purpose Enforce HTTP-layer per-client rate limits to prevent abuse
 * @doc.layer product
 * @doc.pattern Middleware, Decorator
 */
public class RateLimitFilter implements AsyncServlet {

  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

  /** Standard rate-limit headers (draft-ietf-httpapi-ratelimit-headers). */
  public static final HttpHeader HEADER_LIMIT = HttpHeaders.of("X-RateLimit-Limit");

  public static final HttpHeader HEADER_REMAINING = HttpHeaders.of("X-RateLimit-Remaining");
  public static final HttpHeader HEADER_RESET = HttpHeaders.of("X-RateLimit-Reset");
  public static final HttpHeader HEADER_RETRY_AFTER = HttpHeaders.RETRY_AFTER;

  private final int maxRequests;
  private final long windowMillis;
  private final AsyncServlet delegate;

  /** Keyed by client identifier → current window bucket. */
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  /**
   * Creates a rate-limit filter with default limits: 100 requests per 60 s.
   *
   * @param delegate the servlet to invoke when the request is within limits
   */
  public RateLimitFilter(AsyncServlet delegate) {
    this(100, 60_000L, delegate);
  }

  /**
   * Creates a rate-limit filter with custom limits.
   *
   * @param maxRequests maximum allowed requests per window
   * @param windowMillis sliding window duration in milliseconds
   * @param delegate the servlet to invoke when the request is within limits
   * @throws IllegalArgumentException if {@code maxRequests} ≤ 0 or {@code windowMillis} ≤ 0
   */
  public RateLimitFilter(int maxRequests, long windowMillis, AsyncServlet delegate) {
    if (maxRequests <= 0) {
      throw new IllegalArgumentException("maxRequests must be > 0, got " + maxRequests);
    }
    if (windowMillis <= 0) {
      throw new IllegalArgumentException("windowMillis must be > 0, got " + windowMillis);
    }
    this.maxRequests = maxRequests;
    this.windowMillis = windowMillis;
    this.delegate = delegate;
  }

  @Override
  public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
    String clientId = resolveClientId(request);
    long now = currentTimeMillis();

    // Atomically update or create the bucket for this client
    Bucket bucket =
        buckets.compute(
            clientId,
            (key, existing) -> {
              if (existing == null || now >= existing.windowStart + windowMillis) {
                // Fresh window
                return new Bucket(now, maxRequests - 1);
              }
              return new Bucket(existing.windowStart, existing.remaining - 1);
            });

    int remaining = Math.max(0, bucket.remaining);

    if (bucket.remaining < 0) {
      // Limit exceeded — reject immediately
      long resetEpochSec = (bucket.windowStart + windowMillis) / 1000L;
      long retryAfterSec = Math.max(1L, resetEpochSec - (now / 1000L));
      log.warn(
          "[rate-limit] client={} exceeded {} req / {} ms — retry after {} s",
          clientId,
          maxRequests,
          windowMillis,
          retryAfterSec);

      return Promise.of(
          HttpResponse.ofCode(429)
              .withHeader(HEADER_LIMIT, String.valueOf(maxRequests))
              .withHeader(HEADER_REMAINING, "0")
              .withHeader(HEADER_RESET, String.valueOf(resetEpochSec))
              .withHeader(HEADER_RETRY_AFTER, String.valueOf(retryAfterSec))
              .withPlainText("Too Many Requests")
              .build());
    }

    // Within limits — forward to delegate and annotate the response
    return delegate.serve(request).map(response -> annotate(response, remaining));
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private HttpResponse annotate(HttpResponse response, int remaining) {
    HttpResponse.Builder builder = HttpResponse.ofCode(response.getCode());
    for (var entry : response.getHeaders()) {
      builder.withHeader(entry.getKey(), entry.getValue());
    }
    builder
        .withHeader(HEADER_LIMIT, String.valueOf(maxRequests))
        .withHeader(HEADER_REMAINING, String.valueOf(remaining))
        .withBody(response.getBody());
    return builder.build();
  }

  /**
   * Resolves a stable client identifier from the request.
   *
   * <p>Prefers {@code X-Forwarded-For} (first value, stripped of whitespace) so that requests
   * routed via a load-balancer are rated per originating IP, not per proxy IP. Falls back to an
   * internal {@code "unknown"} key if no address can be determined — this is intentionally
   * conservative (all anonymous traffic shares one bucket) rather than silently bypassing the
   * limit.
   */
  String resolveClientId(HttpRequest request) {
    // First, try X-Forwarded-For header (for load-balanced scenarios)
    String forwarded = request.getHeader(HttpHeaders.X_FORWARDED_FOR);
    if (forwarded != null && !forwarded.isBlank()) {
      // Take only the first IP in a comma-separated list
      int comma = forwarded.indexOf(',');
      return (comma > 0 ? forwarded.substring(0, comma) : forwarded).strip();
    }

    // Fallback to remote address; since ActiveJ's HttpRequest doesn't provide
    // reliable cross-platform access, return a conservative bucket key
    return "unknown";
  }

  /** Overrideable in tests to control time. */
  long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  // -------------------------------------------------------------------------
  // Internal state
  // -------------------------------------------------------------------------

  /** Immutable snapshot of a single client's current window. */
  record Bucket(long windowStart, int remaining) {}
}

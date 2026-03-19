/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.middleware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RateLimitFilter}.
 *
 * <p>The tests manipulate the "clock" by subclassing {@link RateLimitFilter} and overriding {@link
 * RateLimitFilter#currentTimeMillis()}, which avoids any real-time sleeping while keeping the
 * window semantics fully exercised.
 *
 * @doc.type class
 * @doc.purpose Verify rate-limit enforcement, header injection, and window reset
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("RateLimitFilter Tests")
class RateLimitFilterTest extends EventloopTestBase {

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static final String ALWAYS_200_BODY = "OK";

  /** Servlet that always returns 200. */
  private static io.activej.http.AsyncServlet okServlet() {
    return req ->
        io.activej.promise.Promise.of(HttpResponse.ok200().withPlainText(ALWAYS_200_BODY).build());
  }

  /** Builds a minimal GET request against the given path with an explicit client IP. */
  private static HttpRequest requestFromIp(String path, String ip) {
    return HttpRequest.builder(HttpMethod.GET, "http://localhost" + path)
        .withHeader(HttpHeaders.X_FORWARDED_FOR, ip)
        .build();
  }

  /** Controllable-clock subclass of RateLimitFilter. */
  private static class FixedClockFilter extends RateLimitFilter {

    private long now;

    FixedClockFilter(
        int maxRequests,
        long windowMillis,
        io.activej.http.AsyncServlet delegate,
        long startMillis) {
      super(maxRequests, windowMillis, delegate);
      this.now = startMillis;
    }

    void advanceBy(long millis) {
      now += millis;
    }

    @Override
    long currentTimeMillis() {
      return now;
    }
  }

  // -------------------------------------------------------------------------
  // Tests
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should forward request when within rate limit")
  void shouldForwardWhenWithinLimit() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(3, 60_000L, okServlet());
    HttpRequest req = requestFromIp("/api/test", "10.0.0.1");

    HttpResponse response = runPromise(() -> filter.serve(req));

    assertThat(response.getCode()).isEqualTo(200);
    assertThat(response.getHeader(RateLimitFilter.HEADER_LIMIT)).isEqualTo("3");
    // First request → 2 remaining
    assertThat(response.getHeader(RateLimitFilter.HEADER_REMAINING)).isEqualTo("2");
  }

  @Test
  @DisplayName("should return 429 after limit is exhausted")
  void shouldReturn429WhenLimitExhausted() throws Exception {
    FixedClockFilter filter = new FixedClockFilter(2, 60_000L, okServlet(), 1_000_000L);
    HttpRequest req = requestFromIp("/api/test", "10.0.0.2");

    // Consume both allowed slots
    runPromise(() -> filter.serve(req)); // remaining = 1
    runPromise(() -> filter.serve(req)); // remaining = 0
    // Third request must be rejected
    HttpResponse rejected = runPromise(() -> filter.serve(req));

    assertThat(rejected.getCode()).isEqualTo(429);
    assertThat(rejected.getHeader(RateLimitFilter.HEADER_LIMIT)).isEqualTo("2");
    assertThat(rejected.getHeader(RateLimitFilter.HEADER_REMAINING)).isEqualTo("0");
    assertThat(rejected.getHeader(RateLimitFilter.HEADER_RESET)).isNotNull();
    assertThat(rejected.getHeader(RateLimitFilter.HEADER_RETRY_AFTER)).isNotNull();
  }

  @Test
  @DisplayName("should reset the window after window duration elapses")
  void shouldResetWindowAfterDuration() throws Exception {
    long windowMs = 5_000L;
    FixedClockFilter filter = new FixedClockFilter(1, windowMs, okServlet(), 1_000_000L);
    HttpRequest req = requestFromIp("/api/test", "10.0.0.3");

    // Exhaust the single allowed slot
    runPromise(() -> filter.serve(req)); // remaining = 0
    HttpResponse rejected = runPromise(() -> filter.serve(req));
    assertThat(rejected.getCode()).isEqualTo(429);

    // Advance time past the window
    filter.advanceBy(windowMs + 1);

    // Should be allowed again
    HttpResponse allowed = runPromise(() -> filter.serve(req));
    assertThat(allowed.getCode()).isEqualTo(200);
    assertThat(allowed.getHeader(RateLimitFilter.HEADER_REMAINING)).isEqualTo("0");
  }

  @Test
  @DisplayName("should track each client independently")
  void shouldTrackClientsIndependently() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(1, 60_000L, okServlet());

    HttpRequest clientA = requestFromIp("/api/test", "192.168.1.1");
    HttpRequest clientB = requestFromIp("/api/test", "192.168.1.2");

    // Exhaust client A
    runPromise(() -> filter.serve(clientA)); // A: remaining = 0
    HttpResponse aRejected = runPromise(() -> filter.serve(clientA));
    assertThat(aRejected.getCode()).isEqualTo(429);

    // Client B should still be allowed
    HttpResponse bAllowed = runPromise(() -> filter.serve(clientB));
    assertThat(bAllowed.getCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("should use X-Forwarded-For first IP when header contains multiple IPs")
  void shouldUseFirstForwardedIp() {
    RateLimitFilter filter = new RateLimitFilter(5, 60_000L, okServlet());
    HttpRequest req =
        HttpRequest.builder(HttpMethod.GET, "http://localhost/api/test")
            .withHeader(
                HttpHeaders.register("X-Forwarded-For"), "203.0.113.5, 198.51.100.1, 10.0.0.1")
            .build();

    String resolved = filter.resolveClientId(req);
    assertThat(resolved).isEqualTo("203.0.113.5");
  }

  @Test
  @DisplayName("should reject invalid constructor arguments")
  void shouldRejectInvalidConstructorArgs() {
    assertThatThrownBy(() -> new RateLimitFilter(0, 1000L, okServlet()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxRequests");

    assertThatThrownBy(() -> new RateLimitFilter(5, 0L, okServlet()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("windowMillis");
  }

  @Test
  @DisplayName("should attach rate-limit headers to every successful response")
  void shouldAttachHeadersToEverySuccessfulResponse() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(10, 60_000L, okServlet());
    HttpRequest req = requestFromIp("/api/test", "10.0.0.9");

    for (int i = 9; i >= 0; i--) {
      HttpResponse resp = runPromise(() -> filter.serve(req));
      assertThat(resp.getCode()).isEqualTo(200);
      assertThat(resp.getHeader(RateLimitFilter.HEADER_LIMIT)).isEqualTo("10");
      assertThat(Integer.parseInt(resp.getHeader(RateLimitFilter.HEADER_REMAINING)))
          .isGreaterThanOrEqualTo(0);
    }
  }
}

/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.gateway.ratelimit;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RateLimitFilter}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for per-tenant rate limit HTTP filter (STORY-K11)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter — Unit Tests")
class RateLimitFilterTest extends EventloopTestBase {

    @Mock
    private RateLimitStore store;

    private RateLimitFilter filter;

    private static final io.activej.http.AsyncServlet OK_SERVLET = req -> Promise.of(HttpResponse.ok200().build());

    @BeforeEach
    void setUp() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(store, 100L, 10.0);
        filter = new RateLimitFilter(limiter);
    }

    @Test
    @DisplayName("known tenant within rate limit — forwards to next and returns 200")
    void allowedTenant_forwards() {
        when(store.tryConsume(eq("tenant:t-finance"), anyLong(), anyDouble(), anyLong()))
                .thenReturn(new RateLimitStore.ConsumeResult(true, 99L, 0L));

        HttpRequest request = HttpRequest.get("http://localhost/api/v2/ledger")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "t-finance")
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, OK_SERVLET));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("rate limit exceeded — returns 429 with Retry-After header")
    void rateLimitExceeded_returns429() {
        when(store.tryConsume(eq("tenant:t-finance"), anyLong(), anyDouble(), anyLong()))
                .thenReturn(new RateLimitStore.ConsumeResult(false, 0L, 5000L));

        HttpRequest request = HttpRequest.get("http://localhost/api/v2/ledger")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "t-finance")
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, OK_SERVLET));

        assertThat(response.getCode()).isEqualTo(429);
        assertThat(response.getHeader(HttpHeaders.of("Retry-After"))).isNotBlank();
    }

    @Test
    @DisplayName("missing X-Tenant-Id — uses 'anonymous' bucket and allows when within limit")
    void missingTenantId_usesAnonymousBucket_andAllows() {
        when(store.tryConsume(eq("tenant:anonymous"), anyLong(), anyDouble(), anyLong()))
                .thenReturn(new RateLimitStore.ConsumeResult(true, 50L, 0L));

        HttpRequest request = HttpRequest.get("http://localhost/api/v2/ledger").build();

        HttpResponse response = runPromise(() -> filter.apply(request, OK_SERVLET));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("blank X-Tenant-Id — uses 'anonymous' bucket")
    void blankTenantId_usesAnonymousBucket() {
        when(store.tryConsume(eq("tenant:anonymous"), anyLong(), anyDouble(), anyLong()))
                .thenReturn(new RateLimitStore.ConsumeResult(true, 100L, 0L));

        HttpRequest request = HttpRequest.get("http://localhost/api/v2/ledger")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "   ")
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, OK_SERVLET));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("429 response includes Retry-After in seconds, rounded up from ms")
    void retryAfterHeader_convertedToSeconds() {
        when(store.tryConsume(anyString(), anyLong(), anyDouble(), anyLong()))
                .thenReturn(new RateLimitStore.ConsumeResult(false, 0L, 3500L));

        HttpRequest request = HttpRequest.get("http://localhost/api/v2/ledger")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "t-abc")
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, OK_SERVLET));

        assertThat(response.getCode()).isEqualTo(429);
        // 3500ms → at least 3s
        String retryAfter = response.getHeader(HttpHeaders.of("Retry-After"));
        assertThat(Integer.parseInt(retryAfter)).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("rate limit with 0ms retry — Retry-After is at least 1 second")
    void retryAfterZeroMs_minimumOneSecond() {
        when(store.tryConsume(anyString(), anyLong(), anyDouble(), anyLong()))
                .thenReturn(new RateLimitStore.ConsumeResult(false, 0L, 0L));

        HttpRequest request = HttpRequest.get("http://localhost/api/v2/ledger")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "t-xyz")
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, OK_SERVLET));

        assertThat(response.getCode()).isEqualTo(429);
        String retryAfter = response.getHeader(HttpHeaders.of("Retry-After"));
        assertThat(Integer.parseInt(retryAfter)).isGreaterThanOrEqualTo(1);
    }
}

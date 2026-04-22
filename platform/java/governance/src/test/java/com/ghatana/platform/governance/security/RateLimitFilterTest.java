/*
 * Copyright (c) 2025 Ghatana Technologies // GH-90000
 * Platform Governance — RateLimitFilter Tests
 */
package com.ghatana.platform.governance.security;

import com.ghatana.platform.http.security.filter.RateLimitFilter;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link RateLimitFilter}.
 *
 * <p>Verifies: constructor validation, sliding-window per-client enforcement,
 * X-Forwarded-For client key extraction, multi-client isolation, and the
 * delegate passthrough on allowed requests.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the sliding-window rate limiting middleware
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RateLimitFilter [GH-90000]")
class RateLimitFilterTest extends EventloopTestBase {

    // -----------------------------------------------------------------------
    // Delegate stub that always returns HTTP 200
    // -----------------------------------------------------------------------

    private static final AsyncServlet OK_SERVLET =
            request -> Promise.of(HttpResponse.ok200().build()); // GH-90000

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("constructor validation [GH-90000]")
    class ConstructorValidation {

        @Test
        @DisplayName("zero maxRequests throws IllegalArgumentException [GH-90000]")
        void zeroMaxRequestsThrows() { // GH-90000
            assertThatThrownBy(() -> new RateLimitFilter(0, 60)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("maxRequests must be > 0 [GH-90000]");
        }

        @Test
        @DisplayName("negative maxRequests throws IllegalArgumentException [GH-90000]")
        void negativeMaxRequestsThrows() { // GH-90000
            assertThatThrownBy(() -> new RateLimitFilter(-1, 60)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("maxRequests must be > 0 [GH-90000]");
        }

        @Test
        @DisplayName("zero windowSeconds throws IllegalArgumentException [GH-90000]")
        void zeroWindowSecondsThrows() { // GH-90000
            assertThatThrownBy(() -> new RateLimitFilter(10, 0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("windowSeconds must be > 0 [GH-90000]");
        }

        @Test
        @DisplayName("negative windowSeconds throws IllegalArgumentException [GH-90000]")
        void negativeWindowSecondsThrows() { // GH-90000
            assertThatThrownBy(() -> new RateLimitFilter(10, -5)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("windowSeconds must be > 0 [GH-90000]");
        }

        @Test
        @DisplayName("valid parameters create instance without error [GH-90000]")
        void validParametersCreateInstance() { // GH-90000
            assertThat(new RateLimitFilter(100, 60)).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("null client key resolver throws NullPointerException [GH-90000]")
        void nullClientKeyResolverThrows() { // GH-90000
            assertThatThrownBy(() -> new RateLimitFilter(100, 60, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("clientKeyResolver [GH-90000]");
        }
    }

    // -----------------------------------------------------------------------
    // Sliding-window enforcement
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("sliding window enforcement [GH-90000]")
    class SlidingWindowEnforcement {

        @Test
        @DisplayName("requests below limit are passed through (HTTP 200) [GH-90000]")
        void requestsBelowLimitPassThrough() { // GH-90000
            RateLimitFilter filter = new RateLimitFilter(3, 60); // GH-90000
            AsyncServlet servlet = filter.wrap(OK_SERVLET); // GH-90000
            HttpRequest request = buildRequestWithXff("192.168.1.1 [GH-90000]");

            // First two requests should pass
            int status1 = runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); // GH-90000
            int status2 = runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); // GH-90000

            assertThat(status1).isEqualTo(200); // GH-90000
            assertThat(status2).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("request at the limit is passed through [GH-90000]")
        void requestAtLimitPassesThrough() { // GH-90000
            RateLimitFilter filter = new RateLimitFilter(2, 60); // GH-90000
            AsyncServlet servlet = filter.wrap(OK_SERVLET); // GH-90000
            HttpRequest request = buildRequestWithXff("10.0.0.1 [GH-90000]");

            // First two should pass (limit = 2) // GH-90000
            runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); // GH-90000
            int statusAtLimit = runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); // GH-90000

            assertThat(statusAtLimit).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("request over the limit returns HTTP 429 [GH-90000]")
        void requestOverLimitReturns429() { // GH-90000
            RateLimitFilter filter = new RateLimitFilter(2, 60); // GH-90000
            AsyncServlet servlet = filter.wrap(OK_SERVLET); // GH-90000
            HttpRequest request = buildRequestWithXff("10.0.0.2 [GH-90000]");

            // Exhaust the budget
            runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); // GH-90000
            runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); // GH-90000

            // This one exceeds the limit
            int status = runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(429); // GH-90000
        }

        @Test
        @DisplayName("delegate is not invoked when rate limit is exceeded [GH-90000]")
        void delegateNotCalledWhenLimited() { // GH-90000
            int[] delegateCalls = {0};
            AsyncServlet countingServlet = request -> {
                delegateCalls[0]++;
                return Promise.of(HttpResponse.ok200().build()); // GH-90000
            };

            RateLimitFilter filter = new RateLimitFilter(1, 60); // GH-90000
            AsyncServlet servlet = filter.wrap(countingServlet); // GH-90000
            HttpRequest request = buildRequestWithXff("10.0.1.1 [GH-90000]");

            // Exhaust budget (1 allowed) // GH-90000
            runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); // GH-90000

            // This call should be blocked — delegate must not be invoked
            runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); // GH-90000

            assertThat(delegateCalls[0]).isEqualTo(1); // GH-90000
        }
    }

    // -----------------------------------------------------------------------
    // Client key extraction
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("client key extraction [GH-90000]")
    class ClientKeyExtraction {

        @Test
        @DisplayName("X-Forwarded-For header is used as client key over remote address [GH-90000]")
        void xForwardedForTakesPrecedence() { // GH-90000
            RateLimitFilter filter = new RateLimitFilter(1, 60); // GH-90000
            AsyncServlet servlet = filter.wrap(OK_SERVLET); // GH-90000

            // First request keyed by X-Forwarded-For: 1.2.3.4
            HttpRequest xffRequest = buildRequestWithXff("1.2.3.4 [GH-90000]");
            runPromise(() -> servlet.serve(xffRequest).map(HttpResponse::getCode)); // GH-90000

            // Second request from same 1.2.3.4 — should be limited
            int status = runPromise(() -> servlet.serve(xffRequest).map(HttpResponse::getCode)); // GH-90000
            assertThat(status).isEqualTo(429); // GH-90000
        }

        @Test
        @DisplayName("first IP in comma-separated X-Forwarded-For chain is used [GH-90000]")
        void firstIpInXffChainIsUsed() { // GH-90000
            RateLimitFilter filter = new RateLimitFilter(1, 60); // GH-90000
            AsyncServlet servlet = filter.wrap(OK_SERVLET); // GH-90000

            // Proxy chain: "1.2.3.4, 5.6.7.8" — only 1.2.3.4 should count
            HttpRequest chainRequest = buildRequestWithXff("1.2.3.4, 5.6.7.8 [GH-90000]");
            runPromise(() -> servlet.serve(chainRequest).map(HttpResponse::getCode)); // GH-90000

            // Another request with same chain (same first IP) should be rate-limited // GH-90000
            int status = runPromise(() -> servlet.serve(chainRequest).map(HttpResponse::getCode)); // GH-90000
            assertThat(status).isEqualTo(429); // GH-90000
        }
    }

    // -----------------------------------------------------------------------
    // Multi-client isolation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("multi-client isolation [GH-90000]")
    class MultiClientIsolation {

        @Test
        @DisplayName("different clients have independent rate limit buckets [GH-90000]")
        void differentClientsAreIndependent() { // GH-90000
            RateLimitFilter filter = new RateLimitFilter(1, 60); // GH-90000
            AsyncServlet servlet = filter.wrap(OK_SERVLET); // GH-90000

            HttpRequest requestA = buildRequestWithXff("192.168.0.1 [GH-90000]");
            HttpRequest requestB = buildRequestWithXff("192.168.0.2 [GH-90000]");

            // Exhaust client A's budget
            runPromise(() -> servlet.serve(requestA).map(HttpResponse::getCode)); // GH-90000
            int statusA = runPromise(() -> servlet.serve(requestA).map(HttpResponse::getCode)); // GH-90000

            // Client B should still be allowed (fresh bucket) // GH-90000
            int statusB = runPromise(() -> servlet.serve(requestB).map(HttpResponse::getCode)); // GH-90000

            assertThat(statusA).isEqualTo(429); // A is limited // GH-90000
            assertThat(statusB).isEqualTo(200); // B is independent // GH-90000
        }

        @Test
        @DisplayName("N distinct clients each get their own full quota [GH-90000]")
        void nClientsGetIndependentQuotas() { // GH-90000
            int quota = 2;
            RateLimitFilter filter = new RateLimitFilter(quota, 60); // GH-90000
            AsyncServlet servlet = filter.wrap(OK_SERVLET); // GH-90000

            for (int i = 0; i < 5; i++) { // GH-90000
                final int clientIdx = i; // effectively final for lambda capture
                HttpRequest req = buildRequestWithXff("10.0.0." + clientIdx); // GH-90000
                for (int j = 0; j < quota; j++) { // GH-90000
                    int status = runPromise(() -> servlet.serve(req).map(HttpResponse::getCode)); // GH-90000
                    assertThat(status) // GH-90000
                            .as("client %d, request %d should pass", clientIdx, j + 1) // GH-90000
                            .isEqualTo(200); // GH-90000
                }
                // One over quota should be blocked
                int overStatus = runPromise(() -> servlet.serve(req).map(HttpResponse::getCode)); // GH-90000
                assertThat(overStatus) // GH-90000
                        .as("client %d over-quota request should be blocked", clientIdx) // GH-90000
                        .isEqualTo(429); // GH-90000
            }
        }

        @Test
        @DisplayName("custom client key resolver can isolate tenants behind one forwarded IP [GH-90000]")
        void customClientKeyResolverCanIsolateTenants() { // GH-90000
            RateLimitFilter filter =
                    new RateLimitFilter( // GH-90000
                            1,
                            60,
                            request -> {
                                String tenantId = request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-ID [GH-90000]"));
                                String clientIp = request.getHeader(io.activej.http.HttpHeaders.of("X-Forwarded-For [GH-90000]"));
                                return (tenantId == null ? "unknown" : tenantId) + "|" + clientIp; // GH-90000
                            });
            AsyncServlet servlet = filter.wrap(OK_SERVLET); // GH-90000

            HttpRequest tenantARequest = buildRequestWithTenantAndXff("tenant-a", "192.168.0.10"); // GH-90000
            HttpRequest tenantBRequest = buildRequestWithTenantAndXff("tenant-b", "192.168.0.10"); // GH-90000

            int tenantAFirst = runPromise(() -> servlet.serve(tenantARequest).map(HttpResponse::getCode)); // GH-90000
            int tenantASecond = runPromise(() -> servlet.serve(tenantARequest).map(HttpResponse::getCode)); // GH-90000
            int tenantBFirst = runPromise(() -> servlet.serve(tenantBRequest).map(HttpResponse::getCode)); // GH-90000

            assertThat(tenantAFirst).isEqualTo(200); // GH-90000
            assertThat(tenantASecond).isEqualTo(429); // GH-90000
            assertThat(tenantBFirst).isEqualTo(200); // GH-90000
        }
    }

    // -----------------------------------------------------------------------
    // Retry-After header
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("429 response headers [GH-90000]")
    class TooManyRequestsResponse {

        @Test
        @DisplayName("rate-limited response includes Retry-After header [GH-90000]")
        void rateLimitedResponseIncludesRetryAfterHeader() { // GH-90000
            RateLimitFilter filter = new RateLimitFilter(1, 60); // GH-90000
            AsyncServlet servlet = filter.wrap(OK_SERVLET); // GH-90000
            HttpRequest request = buildRequestWithXff("203.0.113.1 [GH-90000]");

            // Exhaust budget
            runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); // GH-90000

            // Rate-limited response
            HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000
            assertThat(response.getCode()).isEqualTo(429); // GH-90000
            assertThat(response.getHeader(io.activej.http.HttpHeaders.of("Retry-After [GH-90000]")))
                    .isEqualTo("60 [GH-90000]");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Build a minimal GET request with no X-Forwarded-For header.
     * In-memory ActiveJ tests have no real socket, so getRemoteAddress() is null; // GH-90000
     * all calls to this method share the "unknown" rate-limit bucket.
     * Use only when client identity is irrelevant to the test.
     */
    private static HttpRequest buildRequest() { // GH-90000
        return HttpRequest.get("http://localhost/api/test [GH-90000]")
                .withHeader(io.activej.http.HttpHeaders.HOST, "localhost") // GH-90000
                .build(); // GH-90000
    }

    /**
     * Build a request with the given X-Forwarded-For value.
     * The xffValue becomes the client key used by the rate limiter,
     * enabling deterministic per-client isolation in in-memory tests.
     *
     * @param xffValue single IP or comma-separated proxy chain (e.g. "1.2.3.4, 5.6.7.8") // GH-90000
     */
    private static HttpRequest buildRequestWithXff(String xffValue) { // GH-90000
        return HttpRequest.get("http://localhost/api/test [GH-90000]")
                .withHeader(io.activej.http.HttpHeaders.HOST, "localhost") // GH-90000
                .withHeader(io.activej.http.HttpHeaders.of("X-Forwarded-For [GH-90000]"), xffValue)
                .build(); // GH-90000
    }

    private static HttpRequest buildRequestWithTenantAndXff(String tenantId, String xffValue) { // GH-90000
        return HttpRequest.get("http://localhost/api/test [GH-90000]")
                .withHeader(io.activej.http.HttpHeaders.HOST, "localhost") // GH-90000
                .withHeader(io.activej.http.HttpHeaders.of("X-Tenant-ID [GH-90000]"), tenantId)
                .withHeader(io.activej.http.HttpHeaders.of("X-Forwarded-For [GH-90000]"), xffValue)
                .build(); // GH-90000
    }
}

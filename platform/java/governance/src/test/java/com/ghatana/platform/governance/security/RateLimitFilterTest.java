/*
 * Copyright (c) 2025 Ghatana Technologies 
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
@DisplayName("RateLimitFilter")
class RateLimitFilterTest extends EventloopTestBase {

    // -----------------------------------------------------------------------
    // Delegate stub that always returns HTTP 200
    // -----------------------------------------------------------------------

    private static final AsyncServlet OK_SERVLET =
            request -> Promise.of(HttpResponse.ok200().build()); 

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("zero maxRequests throws IllegalArgumentException")
        void zeroMaxRequestsThrows() { 
            assertThatThrownBy(() -> new RateLimitFilter(0, 60)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("maxRequests must be > 0");
        }

        @Test
        @DisplayName("negative maxRequests throws IllegalArgumentException")
        void negativeMaxRequestsThrows() { 
            assertThatThrownBy(() -> new RateLimitFilter(-1, 60)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("maxRequests must be > 0");
        }

        @Test
        @DisplayName("zero windowSeconds throws IllegalArgumentException")
        void zeroWindowSecondsThrows() { 
            assertThatThrownBy(() -> new RateLimitFilter(10, 0)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("windowSeconds must be > 0");
        }

        @Test
        @DisplayName("negative windowSeconds throws IllegalArgumentException")
        void negativeWindowSecondsThrows() { 
            assertThatThrownBy(() -> new RateLimitFilter(10, -5)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("windowSeconds must be > 0");
        }

        @Test
        @DisplayName("valid parameters create instance without error")
        void validParametersCreateInstance() { 
            assertThat(new RateLimitFilter(100, 60)).isNotNull(); 
        }

        @Test
        @DisplayName("null client key resolver throws NullPointerException")
        void nullClientKeyResolverThrows() { 
            assertThatThrownBy(() -> new RateLimitFilter(100, 60, null)) 
                    .isInstanceOf(NullPointerException.class) 
                    .hasMessageContaining("clientKeyResolver");
        }
    }

    // -----------------------------------------------------------------------
    // Sliding-window enforcement
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("sliding window enforcement")
    class SlidingWindowEnforcement {

        @Test
        @DisplayName("requests below limit are passed through (HTTP 200)")
        void requestsBelowLimitPassThrough() { 
            RateLimitFilter filter = new RateLimitFilter(3, 60); 
            AsyncServlet servlet = filter.wrap(OK_SERVLET); 
            HttpRequest request = buildRequestWithXff("192.168.1.1");

            // First two requests should pass
            int status1 = runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); 
            int status2 = runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); 

            assertThat(status1).isEqualTo(200); 
            assertThat(status2).isEqualTo(200); 
        }

        @Test
        @DisplayName("request at the limit is passed through")
        void requestAtLimitPassesThrough() { 
            RateLimitFilter filter = new RateLimitFilter(2, 60); 
            AsyncServlet servlet = filter.wrap(OK_SERVLET); 
            HttpRequest request = buildRequestWithXff("10.0.0.1");

            // First two should pass (limit = 2) 
            runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); 
            int statusAtLimit = runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); 

            assertThat(statusAtLimit).isEqualTo(200); 
        }

        @Test
        @DisplayName("request over the limit returns HTTP 429")
        void requestOverLimitReturns429() { 
            RateLimitFilter filter = new RateLimitFilter(2, 60); 
            AsyncServlet servlet = filter.wrap(OK_SERVLET); 
            HttpRequest request = buildRequestWithXff("10.0.0.2");

            // Exhaust the budget
            runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); 
            runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); 

            // This one exceeds the limit
            int status = runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); 

            assertThat(status).isEqualTo(429); 
        }

        @Test
        @DisplayName("delegate is not invoked when rate limit is exceeded")
        void delegateNotCalledWhenLimited() { 
            int[] delegateCalls = {0};
            AsyncServlet countingServlet = request -> {
                delegateCalls[0]++;
                return Promise.of(HttpResponse.ok200().build()); 
            };

            RateLimitFilter filter = new RateLimitFilter(1, 60); 
            AsyncServlet servlet = filter.wrap(countingServlet); 
            HttpRequest request = buildRequestWithXff("10.0.1.1");

            // Exhaust budget (1 allowed) 
            runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); 

            // This call should be blocked — delegate must not be invoked
            runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); 

            assertThat(delegateCalls[0]).isEqualTo(1); 
        }
    }

    // -----------------------------------------------------------------------
    // Client key extraction
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("client key extraction")
    class ClientKeyExtraction {

        @Test
        @DisplayName("X-Forwarded-For header is used as client key over remote address")
        void xForwardedForTakesPrecedence() { 
            RateLimitFilter filter = new RateLimitFilter(1, 60); 
            AsyncServlet servlet = filter.wrap(OK_SERVLET); 

            // First request keyed by X-Forwarded-For: 1.2.3.4
            HttpRequest xffRequest = buildRequestWithXff("1.2.3.4");
            runPromise(() -> servlet.serve(xffRequest).map(HttpResponse::getCode)); 

            // Second request from same 1.2.3.4 — should be limited
            int status = runPromise(() -> servlet.serve(xffRequest).map(HttpResponse::getCode)); 
            assertThat(status).isEqualTo(429); 
        }

        @Test
        @DisplayName("first IP in comma-separated X-Forwarded-For chain is used")
        void firstIpInXffChainIsUsed() { 
            RateLimitFilter filter = new RateLimitFilter(1, 60); 
            AsyncServlet servlet = filter.wrap(OK_SERVLET); 

            // Proxy chain: "1.2.3.4, 5.6.7.8" — only 1.2.3.4 should count
            HttpRequest chainRequest = buildRequestWithXff("1.2.3.4, 5.6.7.8");
            runPromise(() -> servlet.serve(chainRequest).map(HttpResponse::getCode)); 

            // Another request with same chain (same first IP) should be rate-limited 
            int status = runPromise(() -> servlet.serve(chainRequest).map(HttpResponse::getCode)); 
            assertThat(status).isEqualTo(429); 
        }
    }

    // -----------------------------------------------------------------------
    // Multi-client isolation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("multi-client isolation")
    class MultiClientIsolation {

        @Test
        @DisplayName("different clients have independent rate limit buckets")
        void differentClientsAreIndependent() { 
            RateLimitFilter filter = new RateLimitFilter(1, 60); 
            AsyncServlet servlet = filter.wrap(OK_SERVLET); 

            HttpRequest requestA = buildRequestWithXff("192.168.0.1");
            HttpRequest requestB = buildRequestWithXff("192.168.0.2");

            // Exhaust client A's budget
            runPromise(() -> servlet.serve(requestA).map(HttpResponse::getCode)); 
            int statusA = runPromise(() -> servlet.serve(requestA).map(HttpResponse::getCode)); 

            // Client B should still be allowed (fresh bucket) 
            int statusB = runPromise(() -> servlet.serve(requestB).map(HttpResponse::getCode)); 

            assertThat(statusA).isEqualTo(429); // A is limited 
            assertThat(statusB).isEqualTo(200); // B is independent 
        }

        @Test
        @DisplayName("N distinct clients each get their own full quota")
        void nClientsGetIndependentQuotas() { 
            int quota = 2;
            RateLimitFilter filter = new RateLimitFilter(quota, 60); 
            AsyncServlet servlet = filter.wrap(OK_SERVLET); 

            for (int i = 0; i < 5; i++) { 
                final int clientIdx = i; // effectively final for lambda capture
                HttpRequest req = buildRequestWithXff("10.0.0." + clientIdx); 
                for (int j = 0; j < quota; j++) { 
                    int status = runPromise(() -> servlet.serve(req).map(HttpResponse::getCode)); 
                    assertThat(status) 
                            .as("client %d, request %d should pass", clientIdx, j + 1) 
                            .isEqualTo(200); 
                }
                // One over quota should be blocked
                int overStatus = runPromise(() -> servlet.serve(req).map(HttpResponse::getCode)); 
                assertThat(overStatus) 
                        .as("client %d over-quota request should be blocked", clientIdx) 
                        .isEqualTo(429); 
            }
        }

        @Test
        @DisplayName("custom client key resolver can isolate tenants behind one forwarded IP")
        void customClientKeyResolverCanIsolateTenants() { 
            RateLimitFilter filter =
                    new RateLimitFilter( 
                            1,
                            60,
                            request -> {
                                String tenantId = request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-ID"));
                                String clientIp = request.getHeader(io.activej.http.HttpHeaders.of("X-Forwarded-For"));
                                return (tenantId == null ? "unknown" : tenantId) + "|" + clientIp; 
                            });
            AsyncServlet servlet = filter.wrap(OK_SERVLET); 

            HttpRequest tenantARequest = buildRequestWithTenantAndXff("tenant-a", "192.168.0.10"); 
            HttpRequest tenantBRequest = buildRequestWithTenantAndXff("tenant-b", "192.168.0.10"); 

            int tenantAFirst = runPromise(() -> servlet.serve(tenantARequest).map(HttpResponse::getCode)); 
            int tenantASecond = runPromise(() -> servlet.serve(tenantARequest).map(HttpResponse::getCode)); 
            int tenantBFirst = runPromise(() -> servlet.serve(tenantBRequest).map(HttpResponse::getCode)); 

            assertThat(tenantAFirst).isEqualTo(200); 
            assertThat(tenantASecond).isEqualTo(429); 
            assertThat(tenantBFirst).isEqualTo(200); 
        }
    }

    // -----------------------------------------------------------------------
    // Retry-After header
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("429 response headers")
    class TooManyRequestsResponse {

        @Test
        @DisplayName("rate-limited response includes Retry-After header")
        void rateLimitedResponseIncludesRetryAfterHeader() { 
            RateLimitFilter filter = new RateLimitFilter(1, 60); 
            AsyncServlet servlet = filter.wrap(OK_SERVLET); 
            HttpRequest request = buildRequestWithXff("203.0.113.1");

            // Exhaust budget
            runPromise(() -> servlet.serve(request).map(HttpResponse::getCode)); 

            // Rate-limited response
            HttpResponse response = runPromise(() -> servlet.serve(request)); 
            assertThat(response.getCode()).isEqualTo(429); 
            assertThat(response.getHeader(io.activej.http.HttpHeaders.of("Retry-After")))
                    .isEqualTo("60");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Build a minimal GET request with no X-Forwarded-For header.
     * In-memory ActiveJ tests have no real socket, so getRemoteAddress() is null; 
     * all calls to this method share the "unknown" rate-limit bucket.
     * Use only when client identity is irrelevant to the test.
     */
    private static HttpRequest buildRequest() { 
        return HttpRequest.get("http://localhost/api/test")
                .withHeader(io.activej.http.HttpHeaders.HOST, "localhost") 
                .build(); 
    }

    /**
     * Build a request with the given X-Forwarded-For value.
     * The xffValue becomes the client key used by the rate limiter,
     * enabling deterministic per-client isolation in in-memory tests.
     *
     * @param xffValue single IP or comma-separated proxy chain (e.g. "1.2.3.4, 5.6.7.8") 
     */
    private static HttpRequest buildRequestWithXff(String xffValue) { 
        return HttpRequest.get("http://localhost/api/test")
                .withHeader(io.activej.http.HttpHeaders.HOST, "localhost") 
                .withHeader(io.activej.http.HttpHeaders.of("X-Forwarded-For"), xffValue)
                .build(); 
    }

    private static HttpRequest buildRequestWithTenantAndXff(String tenantId, String xffValue) { 
        return HttpRequest.get("http://localhost/api/test")
                .withHeader(io.activej.http.HttpHeaders.HOST, "localhost") 
                .withHeader(io.activej.http.HttpHeaders.of("X-Tenant-ID"), tenantId)
                .withHeader(io.activej.http.HttpHeaders.of("X-Forwarded-For"), xffValue)
                .build(); 
    }
}

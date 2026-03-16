/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AepSecurityFilter}.
 *
 * <p>Verifies security-header injection, CORS preflight, payload-size enforcement,
 * rate limiting, and client-IP resolution — all without a live HTTP server.
 *
 * <p>Uses {@link EventloopTestBase} so that ActiveJ {@code Promise} resolution
 * behaves correctly inside the test eventloop.
 *
 * @doc.type class
 * @doc.purpose Unit tests for OWASP security filter (headers, CORS, rate-limit, payload)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepSecurityFilter")
@ExtendWith(MockitoExtension.class)
class AepSecurityFilterTest extends EventloopTestBase {

    private static final String OK_URL = "http://localhost/api/v1/test";

    @Mock
    private AsyncServlet nextServlet;

    @BeforeEach
    void setUpNextServlet() throws Exception {
        // lenient() prevents UnnecessaryStubbingException in tests that short-circuit
        // before calling next (CORS preflight, 413 payload-size rejection, etc.).
        lenient().when(nextServlet.serve(any()))
                .thenReturn(Promise.of(HttpResponse.ofCode(200).build()));
    }

    // =========================================================================
    // Security Headers
    // =========================================================================

    @Nested
    @DisplayName("Security Headers")
    class SecurityHeaderTests {

        @Test
        @DisplayName("adds X-Content-Type-Options: nosniff")
        void adds_xContentTypeOptions() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpResponse resp = serve(filter, HttpRequest.get(OK_URL).build());

            assertThat(resp.getHeader(HttpHeaders.of("X-Content-Type-Options")))
                    .isEqualTo("nosniff");
        }

        @Test
        @DisplayName("adds X-Frame-Options: DENY")
        void adds_xFrameOptions() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpResponse resp = serve(filter, HttpRequest.get(OK_URL).build());

            assertThat(resp.getHeader(HttpHeaders.of("X-Frame-Options")))
                    .isEqualTo("DENY");
        }

        @Test
        @DisplayName("adds X-XSS-Protection: 0 (disables legacy XSS filter)")
        void adds_xXssProtectionDisabled() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpResponse resp = serve(filter, HttpRequest.get(OK_URL).build());

            assertThat(resp.getHeader(HttpHeaders.of("X-XSS-Protection")))
                    .isEqualTo("0");
        }

        @Test
        @DisplayName("adds Strict-Transport-Security with max-age=31536000 and preload")
        void adds_strictTransportSecurity() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpResponse resp = serve(filter, HttpRequest.get(OK_URL).build());

            String hsts = resp.getHeader(HttpHeaders.of("Strict-Transport-Security"));
            assertThat(hsts)
                    .contains("max-age=31536000")
                    .contains("includeSubDomains")
                    .contains("preload");
        }

        @Test
        @DisplayName("adds Content-Security-Policy blocking frame-ancestors and form-action")
        void adds_contentSecurityPolicy() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpResponse resp = serve(filter, HttpRequest.get(OK_URL).build());

            String csp = resp.getHeader(HttpHeaders.of("Content-Security-Policy"));
            assertThat(csp)
                    .contains("frame-ancestors 'none'")
                    .contains("form-action 'none'");
        }

        @Test
        @DisplayName("adds Referrer-Policy header")
        void adds_referrerPolicy() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpResponse resp = serve(filter, HttpRequest.get(OK_URL).build());

            assertThat(resp.getHeader(HttpHeaders.of("Referrer-Policy")))
                    .isNotNull()
                    .isNotEmpty();
        }

        @Test
        @DisplayName("adds Permissions-Policy header disabling camera, mic, geo, payment")
        void adds_permissionsPolicy() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpResponse resp = serve(filter, HttpRequest.get(OK_URL).build());

            String pp = resp.getHeader(HttpHeaders.of("Permissions-Policy"));
            assertThat(pp)
                    .contains("camera=()")
                    .contains("microphone=()")
                    .contains("geolocation=()")
                    .contains("payment=()");
        }

        @Test
        @DisplayName("adds Cache-Control: no-store")
        void adds_cacheControlNoStore() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpResponse resp = serve(filter, HttpRequest.get(OK_URL).build());

            assertThat(resp.getHeader(HttpHeaders.of("Cache-Control")))
                    .isEqualTo("no-store");
        }

        @Test
        @DisplayName("adds X-Request-Id header (unique UUID per request)")
        void adds_xRequestId() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpResponse r1 = serve(filter, HttpRequest.get(OK_URL).build());
            HttpResponse r2 = serve(filter, HttpRequest.get(OK_URL).build());

            String id1 = r1.getHeader(HttpHeaders.of("X-Request-Id"));
            String id2 = r2.getHeader(HttpHeaders.of("X-Request-Id"));

            assertThat(id1).isNotNull().isNotEmpty();
            assertThat(id2).isNotNull().isNotEmpty();
            assertThat(id1).isNotEqualTo(id2); // each request gets its own ID
        }

        @Test
        @DisplayName("adds Access-Control-Allow-Origin matching configured origin")
        void adds_accessControlAllowOrigin_matchesConfig() {
            String origin = "https://app.ghatana.com";
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet, origin);
            HttpResponse resp = serve(filter, HttpRequest.get(OK_URL).build());

            assertThat(resp.getHeader(HttpHeaders.of("Access-Control-Allow-Origin")))
                    .isEqualTo(origin);
        }

        @Test
        @DisplayName("default constructor allows all origins (*)")
        void defaultConstructor_allowsAllOrigins() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpResponse resp = serve(filter, HttpRequest.get(OK_URL).build());

            assertThat(resp.getHeader(HttpHeaders.of("Access-Control-Allow-Origin")))
                    .isEqualTo("*");
        }

        @Test
        @DisplayName("null allowedOrigins arg falls back to wildcard")
        void nullAllowedOrigins_fallsBackToWildcard() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet, null);
            HttpResponse resp = serve(filter, HttpRequest.get(OK_URL).build());

            assertThat(resp.getHeader(HttpHeaders.of("Access-Control-Allow-Origin")))
                    .isEqualTo("*");
        }

        @Test
        @DisplayName("security headers are injected even when downstream returns non-200")
        void securityHeaders_presentOnErrorResponse() throws Exception {
            when(nextServlet.serve(any()))
                    .thenReturn(Promise.of(HttpResponse.ofCode(500).build()));
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpResponse resp = serve(filter, HttpRequest.get(OK_URL).build());

            assertThat(resp.getCode()).isEqualTo(500);
            assertThat(resp.getHeader(HttpHeaders.of("X-Content-Type-Options")))
                    .isEqualTo("nosniff");
        }
    }

    // =========================================================================
    // CORS Preflight
    // =========================================================================

    @Nested
    @DisplayName("CORS Preflight")
    class CorsPreflightTests {

        @Test
        @DisplayName("OPTIONS request returns 204 without calling next servlet")
        void options_returns204() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpRequest req = HttpRequest.builder(HttpMethod.OPTIONS, OK_URL).build();
            HttpResponse resp = serve(filter, req);

            assertThat(resp.getCode()).isEqualTo(204);
        }

        @Test
        @DisplayName("OPTIONS response includes Access-Control-Allow-Methods")
        void options_hasAllowMethods() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpRequest req = HttpRequest.builder(HttpMethod.OPTIONS, OK_URL).build();
            HttpResponse resp = serve(filter, req);

            String methods = resp.getHeader(HttpHeaders.of("Access-Control-Allow-Methods"));
            assertThat(methods)
                    .isNotNull()
                    .contains("GET")
                    .contains("POST");
        }

        @Test
        @DisplayName("OPTIONS response echoes Access-Control-Max-Age")
        void options_hasMaxAge() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpRequest req = HttpRequest.builder(HttpMethod.OPTIONS, OK_URL).build();
            HttpResponse resp = serve(filter, req);

            assertThat(resp.getHeader(HttpHeaders.of("Access-Control-Max-Age")))
                    .isNotNull();
        }

        @Test
        @DisplayName("OPTIONS response reflects requested headers if present")
        void options_reflectsRequestedHeaders() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpRequest req = HttpRequest.builder(HttpMethod.OPTIONS, OK_URL)
                    .withHeader(HttpHeaders.of("Access-Control-Request-Headers"),
                                "Content-Type, X-Tenant-Id")
                    .build();
            HttpResponse resp = serve(filter, req);

            String allowed = resp.getHeader(HttpHeaders.of("Access-Control-Allow-Headers"));
            assertThat(allowed)
                    .isNotNull()
                    .contains("Content-Type");
        }

        @Test
        @DisplayName("OPTIONS includes configured origin in Allow-Origin")
        void options_includesConfiguredOrigin() {
            String origin = "https://dashboard.ghatana.com";
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet, origin);
            HttpRequest req = HttpRequest.builder(HttpMethod.OPTIONS, OK_URL).build();
            HttpResponse resp = serve(filter, req);

            assertThat(resp.getHeader(HttpHeaders.of("Access-Control-Allow-Origin")))
                    .isEqualTo(origin);
        }
    }

    // =========================================================================
    // Payload Size Limit
    // =========================================================================

    @Nested
    @DisplayName("Payload Size Limit")
    class PayloadSizeTests {

        /** Slightly above the 16 MiB maximum. */
        private static final long OVER_LIMIT = AepInputValidator.MAX_REQUEST_BODY_BYTES + 1L;

        @Test
        @DisplayName("Content-Length exceeding max → 413 Payload Too Large")
        void contentLengthOverMax_returns413() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpRequest req = HttpRequest.post(OK_URL)
                    .withHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(OVER_LIMIT))
                    .build();

            HttpResponse resp = serve(filter, req);

            assertThat(resp.getCode()).isEqualTo(413);
        }

        @Test
        @DisplayName("Content-Length exactly at max → passes through")
        void contentLengthAtMax_passesThroughWith200() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpRequest req = HttpRequest.post(OK_URL)
                    .withHeader(HttpHeaders.CONTENT_LENGTH,
                                String.valueOf(AepInputValidator.MAX_REQUEST_BODY_BYTES))
                    .build();

            HttpResponse resp = serve(filter, req);

            assertThat(resp.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("Malformed Content-Length → accepted (let downstream decide)")
        void malformedContentLength_doesNotThrow() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpRequest req = HttpRequest.post(OK_URL)
                    .withHeader(HttpHeaders.CONTENT_LENGTH, "not-a-number")
                    .build();

            HttpResponse resp = serve(filter, req);

            // Malformed header must not cause a 500 — downstream decides
            assertThat(resp.getCode()).isNotEqualTo(500);
        }

        @Test
        @DisplayName("Request without Content-Length → passes through")
        void noContentLength_passesThroughWith200() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpRequest req = HttpRequest.get(OK_URL).build();

            HttpResponse resp = serve(filter, req);

            assertThat(resp.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("413 response includes X-Content-Type-Options: nosniff")
        void payloadTooLarge_hasNoSniff() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);
            HttpRequest req = HttpRequest.post(OK_URL)
                    .withHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(OVER_LIMIT))
                    .build();

            HttpResponse resp = serve(filter, req);

            assertThat(resp.getCode()).isEqualTo(413);
            assertThat(resp.getHeader(HttpHeaders.of("X-Content-Type-Options")))
                    .isEqualTo("nosniff");
        }
    }

    // =========================================================================
    // Rate Limiting
    // =========================================================================

    @Nested
    @DisplayName("Rate Limiting")
    class RateLimitTests {

        private static final String TEST_IP = "192.168.0.99";

        /**
         * Returns a GET request with {@code X-Forwarded-For} set to a fixed IP.
         */
        private HttpRequest requestFromIp(String ip) {
            return HttpRequest.get(OK_URL)
                    .withHeader(HttpHeaders.of("X-Forwarded-For"), ip)
                    .build();
        }

        @Test
        @DisplayName("first 200 requests from same IP pass through (status 200)")
        void first200Requests_passThrough() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);

            HttpResponse last200 = null;
            for (int i = 0; i < 200; i++) {
                last200 = serve(filter, requestFromIp(TEST_IP));
            }

            assertThat(last200).isNotNull();
            assertThat(last200.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("201st request from same IP within window → 429 Too Many Requests")
        void request201_exceedsRateLimit_returns429() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);

            for (int i = 0; i < 200; i++) {
                serve(filter, requestFromIp(TEST_IP));
            }
            HttpResponse resp = serve(filter, requestFromIp(TEST_IP));

            assertThat(resp.getCode()).isEqualTo(429);
        }

        @Test
        @DisplayName("429 response includes Retry-After header")
        void rateLimited_response_hasRetryAfterHeader() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);

            for (int i = 0; i < 200; i++) {
                serve(filter, requestFromIp(TEST_IP));
            }
            HttpResponse resp = serve(filter, requestFromIp(TEST_IP));

            assertThat(resp.getHeader(HttpHeaders.of("Retry-After")))
                    .isNotNull();
        }

        @Test
        @DisplayName("different IPs have independent rate-limit buckets")
        void differentIps_haveIndependentBuckets() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);

            // Exhaust the limit for IP "10.0.0.1"
            for (int i = 0; i < 201; i++) {
                serve(filter, requestFromIp("10.0.0.1"));
            }

            // "10.0.0.2" should still be within its own bucket
            HttpResponse resp = serve(filter, requestFromIp("10.0.0.2"));
            assertThat(resp.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("XFF leftmost address is used as client IP")
        void xff_leftmostAddress_usedAsClientIp() {
            AepSecurityFilter filter = new AepSecurityFilter(nextServlet);

            // Use a comma-chain: real client is "172.16.0.5", proxy is "10.0.0.1"
            HttpRequest req = HttpRequest.get(OK_URL)
                    .withHeader(HttpHeaders.of("X-Forwarded-For"), "172.16.0.5, 10.0.0.1")
                    .build();

            // Fill up 200 for "172.16.0.5" using the chain form
            for (int i = 0; i < 200; i++) {
                serve(filter, req);
            }
            HttpResponse resp201 = serve(filter, req);

            // Should 429 on the 201st using leftmost "172.16.0.5" as the key
            assertThat(resp201.getCode()).isEqualTo(429);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Synchronously serves the request via the filter within the EventloopTestBase.
     */
    private HttpResponse serve(AsyncServlet filter, HttpRequest request) {
        return runPromise(() -> filter.serve(request));
    }
}

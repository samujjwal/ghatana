package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DmosApiRateLimiter}.
 *
 * @doc.type class
 * @doc.purpose Tests for DMOS API rate-limit wrapper: bypass safety, enforcement, client
 *     key priority, configurable limits, and 429 JSON envelope contract
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmosApiRateLimiter")
class DmosApiRateLimiterTest extends EventloopTestBase {

    @BeforeEach
    void resetState() {
        DmosApiRateLimiter.setTestRuntimeOverride(null);
        DmosApiRateLimiter.resetFilter();
    }

    @AfterEach
    void clearState() {
        DmosApiRateLimiter.setTestRuntimeOverride(null);
        DmosApiRateLimiter.resetFilter();
    }

    // -------------------------------------------------------------------------
    // Test-bypass safety
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("test bypass")
    class TestBypass {

        @Test
        @DisplayName("bypasses rate limiting when testRuntimeOverride=true")
        void shouldBypassRateLimitInTestRuntime() {
            DmosApiRateLimiter.setTestRuntimeOverride(true);

            AtomicInteger calls = new AtomicInteger();
            AsyncServlet delegate = request -> {
                calls.incrementAndGet();
                return Promise.of(HttpResponse.ok200().build());
            };
            AsyncServlet wrapped = DmosApiRateLimiter.wrap(delegate, DmosMetricsCollector.disabled(), "rate-limiter-test");

            for (int i = 0; i < 80; i++) {
                HttpRequest request = tenantRequest("tenant-bypass");
                HttpResponse response = runPromise(() -> wrapped.serve(request));
                assertThat(response.getCode()).isEqualTo(200);
            }

            assertThat(calls.get()).isEqualTo(80);
        }

        @Test
        @DisplayName("DMOS_ENV=test does NOT bypass rate limiting (production safety)")
        void shouldNotBypassWhenDmosEnvIsTest() {
            // Explicitly disable bypass to simulate a non-Gradle JVM where
            // org.gradle.test.worker is absent. If DMOS_ENV=test were honoured this
            // would pass all 61 requests; if correctly ignored it enforces the limit.
            DmosApiRateLimiter.setTestRuntimeOverride(false);

            AsyncServlet delegate = request -> Promise.of(HttpResponse.ok200().build());
            AsyncServlet wrapped = DmosApiRateLimiter.wrap(delegate, DmosMetricsCollector.disabled(), "rate-limiter-test");
            String tenant = "tenant-env-test-" + UUID.randomUUID();

            for (int i = 0; i < DmosApiRateLimiter.DEFAULT_MAX_REQUESTS; i++) {
                HttpResponse r = runPromise(() -> wrapped.serve(tenantRequest(tenant)));
                assertThat(r.getCode()).isEqualTo(200);
            }

            // 61st request must be blocked — DMOS_ENV=test must have no effect
            HttpResponse throttled = runPromise(() -> wrapped.serve(tenantRequest(tenant)));
            assertThat(throttled.getCode()).isEqualTo(429);
        }
    }

    // -------------------------------------------------------------------------
    // Enforcement
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("rate limit enforcement")
    class Enforcement {

        @Test
        @DisplayName("enforces default limit (60 req/60s) per tenant when test bypass is off")
        void shouldEnforceDefaultLimitPerTenant() {
            DmosApiRateLimiter.setTestRuntimeOverride(false);

            AsyncServlet delegate = request -> Promise.of(HttpResponse.ok200().build());
            AsyncServlet wrapped = DmosApiRateLimiter.wrap(delegate, DmosMetricsCollector.disabled(), "rate-limiter-test");
            String tenant = "tenant-" + UUID.randomUUID();

            for (int i = 0; i < DmosApiRateLimiter.DEFAULT_MAX_REQUESTS; i++) {
                HttpResponse r = runPromise(() -> wrapped.serve(tenantRequest(tenant)));
                assertThat(r.getCode()).as("request %d should pass", i + 1).isEqualTo(200);
            }

            HttpResponse throttled = runPromise(() -> wrapped.serve(tenantRequest(tenant)));
            assertThat(throttled.getCode()).isEqualTo(429);
        }

        @Test
        @DisplayName("different tenants have independent buckets")
        void shouldIsolateTenantBuckets() {
            DmosApiRateLimiter.setTestRuntimeOverride(false);

            AsyncServlet delegate = request -> Promise.of(HttpResponse.ok200().build());
            AsyncServlet wrapped = DmosApiRateLimiter.wrap(delegate, DmosMetricsCollector.disabled(), "rate-limiter-test");

            String tenantA = "tenant-A-" + UUID.randomUUID();
            String tenantB = "tenant-B-" + UUID.randomUUID();

            // Exhaust tenant A
            for (int i = 0; i < DmosApiRateLimiter.DEFAULT_MAX_REQUESTS; i++) {
                runPromise(() -> wrapped.serve(tenantRequest(tenantA)));
            }
            HttpResponse throttledA = runPromise(() -> wrapped.serve(tenantRequest(tenantA)));
            assertThat(throttledA.getCode()).isEqualTo(429);

            // Tenant B must not be affected
            HttpResponse allowedB = runPromise(() -> wrapped.serve(tenantRequest(tenantB)));
            assertThat(allowedB.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("falls back to X-Forwarded-For IP when no tenant header present")
        void shouldFallBackToForwardedForIp() {
            DmosApiRateLimiter.setTestRuntimeOverride(false);

            AsyncServlet delegate = request -> Promise.of(HttpResponse.ok200().build());
            AsyncServlet wrapped = DmosApiRateLimiter.wrap(delegate, DmosMetricsCollector.disabled(), "rate-limiter-test");

            HttpRequest xffRequest = HttpRequest.get("http://localhost/v1/x")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), "203.0.113.5")
                .build();

            for (int i = 0; i < DmosApiRateLimiter.DEFAULT_MAX_REQUESTS; i++) {
                runPromise(() -> wrapped.serve(xffRequest));
            }
            HttpResponse throttled = runPromise(() -> wrapped.serve(xffRequest));
            assertThat(throttled.getCode()).isEqualTo(429);
        }

        @Test
        @DisplayName("uses first IP in comma-separated X-Forwarded-For chain")
        void shouldUseFirstIpInXffChain() {
            DmosApiRateLimiter.setTestRuntimeOverride(false);

            AsyncServlet delegate = request -> Promise.of(HttpResponse.ok200().build());
            AsyncServlet wrapped = DmosApiRateLimiter.wrap(delegate, DmosMetricsCollector.disabled(), "rate-limiter-test");

            HttpRequest chainA = HttpRequest.get("http://localhost/v1/x")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), "1.2.3.4, 5.6.7.8")
                .build();
            HttpRequest chainB = HttpRequest.get("http://localhost/v1/x")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), "1.2.3.4, 9.9.9.9")
                .build();

            // Both requests share client key "ip:1.2.3.4"
            for (int i = 0; i < DmosApiRateLimiter.DEFAULT_MAX_REQUESTS; i++) {
                runPromise(() -> wrapped.serve(chainA));
            }
            HttpResponse throttled = runPromise(() -> wrapped.serve(chainB));
            assertThat(throttled.getCode()).isEqualTo(429);
        }
    }

    // -------------------------------------------------------------------------
    // 429 JSON envelope contract
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("429 response contract")
    class TooManyRequestsContract {

        @Test
        @DisplayName("429 body is a JSON error envelope")
        void shouldReturn429WithJsonEnvelope() throws Exception {
            DmosApiRateLimiter.setTestRuntimeOverride(false);

            AsyncServlet delegate = request -> Promise.of(HttpResponse.ok200().build());
            AsyncServlet wrapped = DmosApiRateLimiter.wrap(delegate, DmosMetricsCollector.disabled(), "rate-limiter-test");
            String tenant = "tenant-429-json-" + UUID.randomUUID();

            for (int i = 0; i < DmosApiRateLimiter.DEFAULT_MAX_REQUESTS; i++) {
                runPromise(() -> wrapped.serve(tenantRequest(tenant)));
            }

            HttpResponse response = runPromise(() -> wrapped.serve(tenantRequest(tenant)));
            assertThat(response.getCode()).isEqualTo(429);

            String contentType = response.getHeader(HttpHeaders.of("Content-Type"));
            assertThat(contentType).contains("application/json");

            String body = response.getBody().getString(StandardCharsets.UTF_8);
            assertThat(body).contains("\"error\"");
            assertThat(body).contains("RATE_LIMITED");
            assertThat(body).contains("\"message\"");
            assertThat(body).contains("Rate limit exceeded");
        }

        @Test
        @DisplayName("429 response includes Retry-After header matching window seconds")
        void shouldIncludeRetryAfterHeader() {
            DmosApiRateLimiter.setTestRuntimeOverride(false);

            AsyncServlet delegate = request -> Promise.of(HttpResponse.ok200().build());
            AsyncServlet wrapped = DmosApiRateLimiter.wrap(delegate, DmosMetricsCollector.disabled(), "rate-limiter-test");
            String tenant = "tenant-retry-after-" + UUID.randomUUID();

            for (int i = 0; i < DmosApiRateLimiter.DEFAULT_MAX_REQUESTS; i++) {
                runPromise(() -> wrapped.serve(tenantRequest(tenant)));
            }

            HttpResponse response = runPromise(() -> wrapped.serve(tenantRequest(tenant)));
            assertThat(response.getCode()).isEqualTo(429);

            String retryAfter = response.getHeader(HttpHeaders.of("Retry-After"));
            assertThat(retryAfter).isEqualTo(
                String.valueOf(DmosApiRateLimiter.DEFAULT_WINDOW_SECONDS));
        }
    }

    // -------------------------------------------------------------------------
    // Configurable limits
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("configurable limits")
    class ConfigurableLimits {

        @Test
        @DisplayName("getConfiguredMaxRequests returns DEFAULT_MAX_REQUESTS when env var absent")
        void defaultMaxRequests() {
            assertThat(DmosApiRateLimiter.getConfiguredMaxRequests())
                .isEqualTo(DmosApiRateLimiter.DEFAULT_MAX_REQUESTS);
        }

        @Test
        @DisplayName("getConfiguredWindowSeconds returns DEFAULT_WINDOW_SECONDS when env var absent")
        void defaultWindowSeconds() {
            assertThat(DmosApiRateLimiter.getConfiguredWindowSeconds())
                .isEqualTo(DmosApiRateLimiter.DEFAULT_WINDOW_SECONDS);
        }
    }

    // -------------------------------------------------------------------------
    // resolveClientKey priority (via reflection — package-private)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("resolveClientKey priority")
    class ClientKeyPriority {

        @Test
        @DisplayName("prefers X-Tenant-ID over X-Forwarded-For and remote address")
        void shouldPreferTenantId() throws Exception {
            Method resolver = DmosApiRateLimiter.class
                .getDeclaredMethod("resolveClientKey", HttpRequest.class);
            resolver.setAccessible(true);

            HttpRequest request = HttpRequest.get("http://localhost/v1/x")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), " tenant-42 ")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), "10.0.0.1")
                .build();
            assertThat((String) resolver.invoke(null, request)).isEqualTo("tenant:tenant-42");
        }

        @Test
        @DisplayName("falls back to first IP in X-Forwarded-For chain when no tenant")
        void shouldUseForwardedForWhenNoTenant() throws Exception {
            Method resolver = DmosApiRateLimiter.class
                .getDeclaredMethod("resolveClientKey", HttpRequest.class);
            resolver.setAccessible(true);

            HttpRequest chainRequest = HttpRequest.get("http://localhost/v1/x")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), "10.0.0.1, 10.0.0.2")
                .build();
            assertThat((String) resolver.invoke(null, chainRequest)).isEqualTo("ip:10.0.0.1");

            HttpRequest singleIp = HttpRequest.get("http://localhost/v1/x")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), "192.168.1.1")
                .build();
            assertThat((String) resolver.invoke(null, singleIp)).isEqualTo("ip:192.168.1.1");
        }

        @Test
        @DisplayName("returns ip:unknown when no headers and no remote address")
        void shouldReturnUnknownWhenNoHeaders() throws Exception {
            Method resolver = DmosApiRateLimiter.class
                .getDeclaredMethod("resolveClientKey", HttpRequest.class);
            resolver.setAccessible(true);

            HttpRequest noHeaders = HttpRequest.get("http://localhost/v1/x").build();
            assertThat((String) resolver.invoke(null, noHeaders)).isEqualTo("ip:unknown");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static HttpRequest tenantRequest(String tenantId) {
        return HttpRequest.get("http://localhost/v1/workspaces/ws-1/approvals")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .build();
    }
}

/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.yappc.services.lifecycle.auth;

import com.ghatana.platform.governance.security.ApiKeyAuthFilter;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.RateLimitFilter;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.governance.security.TenantIsolationHttpFilter;
import com.ghatana.platform.security.rbac.InMemoryPolicyRepository;
import com.ghatana.platform.security.rbac.PolicyService;
import com.ghatana.platform.security.rbac.RBACFilter;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the complete YAPPC auth filter chain.
 *
 * <p>Exercises the full security stack assembled in the order a real request traverses:
 * <ol>
 *   <li>{@link ApiKeyAuthFilter} — API key validation and {@link TenantContext} population</li>
 *   <li>{@link RateLimitFilter} — per-tenant/per-IP rate cap</li>
 *   <li>{@link TenantIsolationHttpFilter} — cross-tenant resource fence</li>
 *   <li>{@link RBACFilter} — role-based access control</li>
 *   <li>Delegate — the actual business handler</li>
 * </ol>
 *
 * <p>Tests cover complete positive flows, short-circuit failures, rate exhaustion, and
 * concurrent request isolation (TenantContext thread safety).
 *
 * @doc.type class
 * @doc.purpose E2E integration tests for the complete YAPPC security filter chain
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Auth Filter Chain — E2E Integration Tests")
class AuthFilterChainE2ETest extends EventloopTestBase {

    // ── Test Principals ───────────────────────────────────────────────────────

    private static final String TENANT_ALPHA = "tenant-alpha";
    private static final String TENANT_BETA  = "tenant-beta";

    private static final String ADMIN_KEY  = "admin-key-alpha";
    private static final String WRITER_KEY = "writer-key-alpha";
    private static final String VIEWER_KEY = "viewer-key-alpha";
    private static final String BETA_KEY   = "admin-key-beta";

    private static final ApiKeyResolver RESOLVER = key -> switch (key) {
        case ADMIN_KEY  -> Optional.of(new Principal("admin-user",  List.of("admin"),  TENANT_ALPHA));
        case WRITER_KEY -> Optional.of(new Principal("writer-user", List.of("writer"), TENANT_ALPHA));
        case VIEWER_KEY -> Optional.of(new Principal("viewer-user", List.of("viewer"), TENANT_ALPHA));
        case BETA_KEY   -> Optional.of(new Principal("admin-beta",  List.of("admin"),  TENANT_BETA));
        default         -> Optional.empty();
    };

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Full four-filter chain — happy path and short-circuit rejection
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Full four-filter chain")
    class FullFilterChain {

        /**
         * Builds the canonical filter stack:
         * ApiKey → RateLimit → TenantIsolation → RBAC → delegate
         */
        private AsyncServlet buildChain(PolicyService policyService,
                                        String requiredAction,
                                        String resource,
                                        AsyncServlet delegate) {
            ApiKeyAuthFilter apiKeyFilter     = new ApiKeyAuthFilter(RESOLVER);
            RateLimitFilter  rateLimitFilter  = new RateLimitFilter(1_000, 60);
            RBACFilter       rbacFilter       = new RBACFilter(policyService, requiredAction, resource);

            return apiKeyFilter.secure(
                    rateLimitFilter.wrap(
                            TenantIsolationHttpFilter.wrap(
                                    rbacFilter.secure(delegate)
                            )
                    )
            );
        }

        @Test
        @DisplayName("admin with write access traverses all four filters and reaches delegate")
        void adminWritePassesAllFilters() {
            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository());
            policyService.createPolicy("admin-write", "admin write policy", "admin",
                    "yappc:lifecycle-api", java.util.Set.of("write"));

            AtomicReference<String> capturedTenant = new AtomicReference<>();
            AsyncServlet delegate = req -> {
                capturedTenant.set(TenantContext.getCurrentTenantId());
                return HttpResponse.ok200().toPromise();
            };

            AsyncServlet chain = buildChain(policyService, "write", "yappc:lifecycle-api", delegate);

            HttpRequest request = requestWithApiKey(HttpMethod.POST, "/api/v1/lifecycle/advance", ADMIN_KEY);
            HttpResponse response = runPromise(() -> chain.serve(request));

            assertThat(response.getCode())
                    .as("Admin with write policy must reach the delegate")
                    .isEqualTo(200);
            assertThat(capturedTenant.get())
                    .as("TenantContext must be populated with the admin's tenant")
                    .isEqualTo(TENANT_ALPHA);
        }

        @Test
        @DisplayName("missing API key short-circuits at first filter — downstream not reached")
        void missingApiKeyShortCircuitsAtFirstFilter() {
            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository());
            AtomicReference<Boolean> delegateReached = new AtomicReference<>(false);
            AsyncServlet delegate = req -> {
                delegateReached.set(true);
                return HttpResponse.ok200().toPromise();
            };

            AsyncServlet chain = buildChain(policyService, "read", "yappc:lifecycle-api", delegate);

            HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/lifecycle/phases")
                    .build(); // no X-API-Key

            HttpResponse response = runPromise(() -> chain.serve(request));

            assertThat(response.getCode())
                    .as("Missing API key must be rejected with 401")
                    .isEqualTo(401);
            assertThat(delegateReached.get())
                    .as("Delegate must not be reached when API key is missing")
                    .isFalse();
        }

        @Test
        @DisplayName("invalid API key short-circuits at first filter — downstream not reached")
        void invalidApiKeyShortCircuitsAtFirstFilter() {
            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository());
            AtomicReference<Boolean> delegateReached = new AtomicReference<>(false);
            AsyncServlet delegate = req -> {
                delegateReached.set(true);
                return HttpResponse.ok200().toPromise();
            };

            AsyncServlet chain = buildChain(policyService, "read", "yappc:lifecycle-api", delegate);

            HttpRequest request = requestWithApiKey(HttpMethod.GET, "/api/v1/lifecycle/phases", "bogus-key");
            HttpResponse response = runPromise(() -> chain.serve(request));

            assertThat(response.getCode())
                    .as("Invalid API key must be rejected with 401")
                    .isEqualTo(401);
            assertThat(delegateReached.get())
                    .as("Delegate must not be reached when API key is invalid")
                    .isFalse();
        }

        @Test
        @DisplayName("viewer role rejected at RBAC filter when write action required")
        void viewerRejectedByRbacOnWriteAction() {
            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository());
            policyService.createPolicy("viewer-read", "viewer read policy", "viewer",
                    "yappc:lifecycle-api", java.util.Set.of("read")); // only read granted

            AtomicReference<Boolean> delegateReached = new AtomicReference<>(false);
            AsyncServlet delegate = req -> {
                delegateReached.set(true);
                return HttpResponse.ok200().toPromise();
            };

            AsyncServlet chain = buildChain(policyService, "write", "yappc:lifecycle-api", delegate);

            HttpRequest request = requestWithApiKey(HttpMethod.POST, "/api/v1/lifecycle/advance", VIEWER_KEY);
            HttpResponse response = runPromise(() -> chain.serve(request));

            assertThat(response.getCode())
                    .as("Viewer lacking write permission must be rejected with 403")
                    .isEqualTo(403);
            assertThat(delegateReached.get())
                    .as("Delegate must not be reached when RBAC denies access")
                    .isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Rate limit integration
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rate limit integration with auth")
    class RateLimitIntegration {

        @Test
        @DisplayName("requests within the limit all pass through the full chain")
        void requestsWithinLimitAllPass() {
            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository());
            policyService.createPolicy("writer-write", "writer write policy", "writer",
                    "yappc:lifecycle-api", java.util.Set.of("write"));

            // High limit so no requests are throttled during this test
            ApiKeyAuthFilter apiKeyFilter    = new ApiKeyAuthFilter(RESOLVER);
            RateLimitFilter  rateLimitFilter = new RateLimitFilter(1_000, 60);
            RBACFilter       rbacFilter      = new RBACFilter(policyService, "write", "yappc:lifecycle-api");
            AsyncServlet     delegate        = req -> HttpResponse.ok200().toPromise();

            AsyncServlet chain = apiKeyFilter.secure(
                    rateLimitFilter.wrap(
                            TenantIsolationHttpFilter.wrap(rbacFilter.secure(delegate))));

            for (int i = 0; i < 5; i++) {
                HttpRequest req = requestWithApiKey(HttpMethod.POST, "/api/v1/lifecycle/advance", WRITER_KEY);
                HttpResponse response = runPromise(() -> chain.serve(req));
                assertThat(response.getCode())
                        .as("Request %d should pass (within rate limit)", i + 1)
                        .isEqualTo(200);
            }
        }

        @Test
        @DisplayName("requests exceeding the per-second rate cap are rejected with 429")
        void requestsExceedingRateLimitRejected() {
            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository());
            policyService.createPolicy("writer-write-ratelimit", "writer write policy", "writer",
                    "yappc:lifecycle-api", java.util.Set.of("write"));

            // Extremely low limit: 2 requests allowed per window
            ApiKeyAuthFilter apiKeyFilter    = new ApiKeyAuthFilter(RESOLVER);
            RateLimitFilter  rateLimitFilter = new RateLimitFilter(2, 60);
            RBACFilter       rbacFilter      = new RBACFilter(policyService, "write", "yappc:lifecycle-api");
            AsyncServlet     delegate        = req -> HttpResponse.ok200().toPromise();

            AsyncServlet chain = apiKeyFilter.secure(
                    rateLimitFilter.wrap(
                            TenantIsolationHttpFilter.wrap(rbacFilter.secure(delegate))));

            // First two requests must pass
            for (int i = 0; i < 2; i++) {
                HttpRequest req = requestWithApiKey(HttpMethod.POST, "/api/v1/lifecycle/advance", WRITER_KEY);
                HttpResponse response = runPromise(() -> chain.serve(req));
                assertThat(response.getCode())
                        .as("Request %d must pass (within limit)", i + 1)
                        .isEqualTo(200);
            }

            // Third request must be throttled
            HttpRequest req = requestWithApiKey(HttpMethod.POST, "/api/v1/lifecycle/advance", WRITER_KEY);
            HttpResponse rateLimited = runPromise(() -> chain.serve(req));
            assertThat(rateLimited.getCode())
                    .as("Third request must be rejected with 429 (rate limit exceeded)")
                    .isEqualTo(429);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Concurrent request TenantContext isolation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrent request TenantContext isolation")
    class ConcurrentTenantIsolation {

        @Test
        @DisplayName("concurrent requests for different tenants maintain isolated TenantContext")
        void concurrentRequestsDoNotLeakTenantContext() throws Exception {
            AtomicReference<String> tenantCapturedByAlpha = new AtomicReference<>();
            AtomicReference<String> tenantCapturedByBeta  = new AtomicReference<>();
            CountDownLatch bothInDelegate = new CountDownLatch(2);
            CountDownLatch bothSynced     = new CountDownLatch(1);

            AsyncServlet alphaDelegate = req -> {
                bothInDelegate.countDown();
                try { bothSynced.await(1, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                tenantCapturedByAlpha.set(TenantContext.getCurrentTenantId());
                return HttpResponse.ok200().toPromise();
            };

            AsyncServlet betaDelegate = req -> {
                bothInDelegate.countDown();
                try { bothSynced.await(1, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                tenantCapturedByBeta.set(TenantContext.getCurrentTenantId());
                return HttpResponse.ok200().toPromise();
            };

            ApiKeyAuthFilter alphaFilter = new ApiKeyAuthFilter(RESOLVER);
            ApiKeyAuthFilter betaFilter  = new ApiKeyAuthFilter(RESOLVER);

            AsyncServlet alphaChain = alphaFilter.secure(TenantIsolationHttpFilter.wrap(alphaDelegate));
            AsyncServlet betaChain  = betaFilter.secure(TenantIsolationHttpFilter.wrap(betaDelegate));

            HttpRequest alphaRequest = requestWithApiKey(HttpMethod.GET, "/api/v1/lifecycle/phases", ADMIN_KEY);
            HttpRequest betaRequest  = requestWithApiKey(HttpMethod.GET, "/api/v1/lifecycle/phases", BETA_KEY);

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<HttpResponse> alphaFuture = executor.submit(() ->
                        runPromise(() -> alphaChain.serve(alphaRequest)));
                Future<HttpResponse> betaFuture  = executor.submit(() ->
                        runPromise(() -> betaChain.serve(betaRequest)));

                // Let both requests reach the delegate before releasing
                boolean reached = bothInDelegate.await(2, java.util.concurrent.TimeUnit.SECONDS);
                bothSynced.countDown();

                HttpResponse alphaResp = alphaFuture.get(2, java.util.concurrent.TimeUnit.SECONDS);
                HttpResponse betaResp  = betaFuture.get(2, java.util.concurrent.TimeUnit.SECONDS);

                assertThat(alphaResp.getCode()).as("Alpha request must succeed").isEqualTo(200);
                assertThat(betaResp.getCode()).as("Beta request must succeed").isEqualTo(200);

                // Each request must have seen only its own tenant
                assertThat(tenantCapturedByAlpha.get())
                        .as("Alpha request delegate saw tenant-alpha, not tenant-beta")
                        .isEqualTo(TENANT_ALPHA);
                assertThat(tenantCapturedByBeta.get())
                        .as("Beta request delegate saw tenant-beta, not tenant-alpha")
                        .isEqualTo(TENANT_BETA);
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("TenantContext is cleared after each request — no carryover to next request")
        void tenantContextDoesNotCarryOverBetweenSequentialRequests() {
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(RESOLVER);
            AsyncServlet delegate   = req -> HttpResponse.ok200().toPromise();
            AsyncServlet chain      = filter.secure(TenantIsolationHttpFilter.wrap(delegate));

            // First request for tenant-alpha
            HttpRequest alphaRequest = requestWithApiKey(HttpMethod.GET, "/api/v1/lifecycle/phases", ADMIN_KEY);
            runPromise(() -> chain.serve(alphaRequest));

            // After the request the context must revert to the default
            assertThat(TenantContext.getCurrentTenantId())
                    .as("TenantContext must revert to default after first request")
                    .isEqualTo("default-tenant");

            // Second request for tenant-beta — must see its own context, not alpha's
            AtomicReference<String> capturedInSecond = new AtomicReference<>();
            AsyncServlet capturingDelegate = req -> {
                capturedInSecond.set(TenantContext.getCurrentTenantId());
                return HttpResponse.ok200().toPromise();
            };
            AsyncServlet betaChain = filter.secure(TenantIsolationHttpFilter.wrap(capturingDelegate));

            HttpRequest betaRequest = requestWithApiKey(HttpMethod.GET, "/api/v1/lifecycle/phases", BETA_KEY);
            runPromise(() -> betaChain.serve(betaRequest));

            assertThat(capturedInSecond.get())
                    .as("Second request for tenant-beta must not see tenant-alpha's context")
                    .isEqualTo(TENANT_BETA);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static HttpRequest requestWithApiKey(HttpMethod method, String path, String apiKey) {
        return HttpRequest.builder(method, "http://localhost" + path)
                .withHeader(HttpHeaders.of("X-API-Key"), apiKey)
                                .withHeader(HttpHeaders.of("X-Forwarded-For"), "127.0.0.1")
                .build();
    }
}

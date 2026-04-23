/*
 * Copyright (c) 2026 Ghatana // GH-90000
 */
package com.ghatana.yappc.services.lifecycle.auth;

import com.ghatana.platform.http.security.filter.ApiKeyAuthFilter;
import com.ghatana.platform.http.security.filter.RBACFilter;
import com.ghatana.platform.http.security.filter.RateLimitFilter;
import com.ghatana.platform.http.security.filter.TenantIsolationHttpFilter;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.security.rbac.InMemoryPolicyRepository;
import com.ghatana.platform.security.rbac.PolicyService;
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
 * concurrent request isolation (TenantContext thread safety). // GH-90000
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

    private static final ApiKeyResolver RESOLVER = key -> switch (key) { // GH-90000
        case ADMIN_KEY  -> Optional.of(new Principal("admin-user",  List.of("admin"),  TENANT_ALPHA));
        case WRITER_KEY -> Optional.of(new Principal("writer-user", List.of("writer"), TENANT_ALPHA));
        case VIEWER_KEY -> Optional.of(new Principal("viewer-user", List.of("viewer"), TENANT_ALPHA));
        case BETA_KEY   -> Optional.of(new Principal("admin-beta",  List.of("admin"),  TENANT_BETA));
        default         -> Optional.empty(); // GH-90000
    };

    @AfterEach
    void clearTenantContext() { // GH-90000
        TenantContext.clear(); // GH-90000
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
        private AsyncServlet buildChain(PolicyService policyService, // GH-90000
                                        String requiredAction,
                                        String resource,
                                        AsyncServlet delegate) {
            ApiKeyAuthFilter apiKeyFilter     = new ApiKeyAuthFilter(RESOLVER); // GH-90000
            RateLimitFilter  rateLimitFilter  = new RateLimitFilter(1_000, 60); // GH-90000
            RBACFilter       rbacFilter       = new RBACFilter(policyService, requiredAction, resource); // GH-90000

            return apiKeyFilter.secure( // GH-90000
                    rateLimitFilter.wrap( // GH-90000
                            TenantIsolationHttpFilter.wrap( // GH-90000
                                    rbacFilter.secure(delegate) // GH-90000
                            )
                    )
            );
        }

        @Test
        @DisplayName("admin with write access traverses all four filters and reaches delegate")
        void adminWritePassesAllFilters() { // GH-90000
            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository()); // GH-90000
            policyService.createPolicy("admin-write", "admin write policy", "admin", // GH-90000
                    "yappc:lifecycle-api", java.util.Set.of("write"));

            AtomicReference<String> capturedTenant = new AtomicReference<>(); // GH-90000
            AsyncServlet delegate = req -> {
                capturedTenant.set(TenantContext.getCurrentTenantId()); // GH-90000
                return HttpResponse.ok200().toPromise(); // GH-90000
            };

            AsyncServlet chain = buildChain(policyService, "write", "yappc:lifecycle-api", delegate); // GH-90000

            HttpRequest request = requestWithApiKey(HttpMethod.POST, "/api/v1/lifecycle/advance", ADMIN_KEY); // GH-90000
            HttpResponse response = runPromise(() -> chain.serve(request)); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("Admin with write policy must reach the delegate")
                    .isEqualTo(200); // GH-90000
            assertThat(capturedTenant.get()) // GH-90000
                    .as("TenantContext must be populated with the admin's tenant")
                    .isEqualTo(TENANT_ALPHA); // GH-90000
        }

        @Test
        @DisplayName("missing API key short-circuits at first filter — downstream not reached")
        void missingApiKeyShortCircuitsAtFirstFilter() { // GH-90000
            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository()); // GH-90000
            AtomicReference<Boolean> delegateReached = new AtomicReference<>(false); // GH-90000
            AsyncServlet delegate = req -> {
                delegateReached.set(true); // GH-90000
                return HttpResponse.ok200().toPromise(); // GH-90000
            };

            AsyncServlet chain = buildChain(policyService, "read", "yappc:lifecycle-api", delegate); // GH-90000

            HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/lifecycle/phases") // GH-90000
                    .build(); // no X-API-Key // GH-90000

            HttpResponse response = runPromise(() -> chain.serve(request)); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("Missing API key must be rejected with 401")
                    .isEqualTo(401); // GH-90000
            assertThat(delegateReached.get()) // GH-90000
                    .as("Delegate must not be reached when API key is missing")
                    .isFalse(); // GH-90000
        }

        @Test
        @DisplayName("invalid API key short-circuits at first filter — downstream not reached")
        void invalidApiKeyShortCircuitsAtFirstFilter() { // GH-90000
            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository()); // GH-90000
            AtomicReference<Boolean> delegateReached = new AtomicReference<>(false); // GH-90000
            AsyncServlet delegate = req -> {
                delegateReached.set(true); // GH-90000
                return HttpResponse.ok200().toPromise(); // GH-90000
            };

            AsyncServlet chain = buildChain(policyService, "read", "yappc:lifecycle-api", delegate); // GH-90000

            HttpRequest request = requestWithApiKey(HttpMethod.GET, "/api/v1/lifecycle/phases", "bogus-key"); // GH-90000
            HttpResponse response = runPromise(() -> chain.serve(request)); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("Invalid API key must be rejected with 401")
                    .isEqualTo(401); // GH-90000
            assertThat(delegateReached.get()) // GH-90000
                    .as("Delegate must not be reached when API key is invalid")
                    .isFalse(); // GH-90000
        }

        @Test
        @DisplayName("viewer role rejected at RBAC filter when write action required")
        void viewerRejectedByRbacOnWriteAction() { // GH-90000
            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository()); // GH-90000
            policyService.createPolicy("viewer-read", "viewer read policy", "viewer", // GH-90000
                    "yappc:lifecycle-api", java.util.Set.of("read")); // only read granted

            AtomicReference<Boolean> delegateReached = new AtomicReference<>(false); // GH-90000
            AsyncServlet delegate = req -> {
                delegateReached.set(true); // GH-90000
                return HttpResponse.ok200().toPromise(); // GH-90000
            };

            AsyncServlet chain = buildChain(policyService, "write", "yappc:lifecycle-api", delegate); // GH-90000

            HttpRequest request = requestWithApiKey(HttpMethod.POST, "/api/v1/lifecycle/advance", VIEWER_KEY); // GH-90000
            HttpResponse response = runPromise(() -> chain.serve(request)); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("Viewer lacking write permission must be rejected with 403")
                    .isEqualTo(403); // GH-90000
            assertThat(delegateReached.get()) // GH-90000
                    .as("Delegate must not be reached when RBAC denies access")
                    .isFalse(); // GH-90000
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
        void requestsWithinLimitAllPass() { // GH-90000
            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository()); // GH-90000
            policyService.createPolicy("writer-write", "writer write policy", "writer", // GH-90000
                    "yappc:lifecycle-api", java.util.Set.of("write"));

            // High limit so no requests are throttled during this test
            ApiKeyAuthFilter apiKeyFilter    = new ApiKeyAuthFilter(RESOLVER); // GH-90000
            RateLimitFilter  rateLimitFilter = new RateLimitFilter(1_000, 60); // GH-90000
            RBACFilter       rbacFilter      = new RBACFilter(policyService, "write", "yappc:lifecycle-api"); // GH-90000
            AsyncServlet     delegate        = req -> HttpResponse.ok200().toPromise(); // GH-90000

            AsyncServlet chain = apiKeyFilter.secure( // GH-90000
                    rateLimitFilter.wrap( // GH-90000
                            TenantIsolationHttpFilter.wrap(rbacFilter.secure(delegate)))); // GH-90000

            for (int i = 0; i < 5; i++) { // GH-90000
                HttpRequest req = requestWithApiKey(HttpMethod.POST, "/api/v1/lifecycle/advance", WRITER_KEY); // GH-90000
                HttpResponse response = runPromise(() -> chain.serve(req)); // GH-90000
                assertThat(response.getCode()) // GH-90000
                        .as("Request %d should pass (within rate limit)", i + 1) // GH-90000
                        .isEqualTo(200); // GH-90000
            }
        }

        @Test
        @DisplayName("requests exceeding the per-second rate cap are rejected with 429")
        void requestsExceedingRateLimitRejected() { // GH-90000
            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository()); // GH-90000
            policyService.createPolicy("writer-write-ratelimit", "writer write policy", "writer", // GH-90000
                    "yappc:lifecycle-api", java.util.Set.of("write"));

            // Extremely low limit: 2 requests allowed per window
            ApiKeyAuthFilter apiKeyFilter    = new ApiKeyAuthFilter(RESOLVER); // GH-90000
            RateLimitFilter  rateLimitFilter = new RateLimitFilter(2, 60); // GH-90000
            RBACFilter       rbacFilter      = new RBACFilter(policyService, "write", "yappc:lifecycle-api"); // GH-90000
            AsyncServlet     delegate        = req -> HttpResponse.ok200().toPromise(); // GH-90000

            AsyncServlet chain = apiKeyFilter.secure( // GH-90000
                    rateLimitFilter.wrap( // GH-90000
                            TenantIsolationHttpFilter.wrap(rbacFilter.secure(delegate)))); // GH-90000

            // First two requests must pass
            for (int i = 0; i < 2; i++) { // GH-90000
                HttpRequest req = requestWithApiKey(HttpMethod.POST, "/api/v1/lifecycle/advance", WRITER_KEY); // GH-90000
                HttpResponse response = runPromise(() -> chain.serve(req)); // GH-90000
                assertThat(response.getCode()) // GH-90000
                        .as("Request %d must pass (within limit)", i + 1) // GH-90000
                        .isEqualTo(200); // GH-90000
            }

            // Third request must be throttled
            HttpRequest req = requestWithApiKey(HttpMethod.POST, "/api/v1/lifecycle/advance", WRITER_KEY); // GH-90000
            HttpResponse rateLimited = runPromise(() -> chain.serve(req)); // GH-90000
            assertThat(rateLimited.getCode()) // GH-90000
                    .as("Third request must be rejected with 429 (rate limit exceeded)")
                    .isEqualTo(429); // GH-90000
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
        void concurrentRequestsDoNotLeakTenantContext() throws Exception { // GH-90000
            AtomicReference<String> tenantCapturedByAlpha = new AtomicReference<>(); // GH-90000
            AtomicReference<String> tenantCapturedByBeta  = new AtomicReference<>(); // GH-90000
            CountDownLatch bothInDelegate = new CountDownLatch(2); // GH-90000
            CountDownLatch bothSynced     = new CountDownLatch(1); // GH-90000

            AsyncServlet alphaDelegate = req -> {
                bothInDelegate.countDown(); // GH-90000
                try { bothSynced.await(1, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {} // GH-90000
                tenantCapturedByAlpha.set(TenantContext.getCurrentTenantId()); // GH-90000
                return HttpResponse.ok200().toPromise(); // GH-90000
            };

            AsyncServlet betaDelegate = req -> {
                bothInDelegate.countDown(); // GH-90000
                try { bothSynced.await(1, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {} // GH-90000
                tenantCapturedByBeta.set(TenantContext.getCurrentTenantId()); // GH-90000
                return HttpResponse.ok200().toPromise(); // GH-90000
            };

            ApiKeyAuthFilter alphaFilter = new ApiKeyAuthFilter(RESOLVER); // GH-90000
            ApiKeyAuthFilter betaFilter  = new ApiKeyAuthFilter(RESOLVER); // GH-90000

            AsyncServlet alphaChain = alphaFilter.secure(TenantIsolationHttpFilter.wrap(alphaDelegate)); // GH-90000
            AsyncServlet betaChain  = betaFilter.secure(TenantIsolationHttpFilter.wrap(betaDelegate)); // GH-90000

            HttpRequest alphaRequest = requestWithApiKey(HttpMethod.GET, "/api/v1/lifecycle/phases", ADMIN_KEY); // GH-90000
            HttpRequest betaRequest  = requestWithApiKey(HttpMethod.GET, "/api/v1/lifecycle/phases", BETA_KEY); // GH-90000

            ExecutorService executor = Executors.newFixedThreadPool(2); // GH-90000
            try {
                Future<HttpResponse> alphaFuture = executor.submit(() -> // GH-90000
                        runPromise(() -> alphaChain.serve(alphaRequest))); // GH-90000
                Future<HttpResponse> betaFuture  = executor.submit(() -> // GH-90000
                        runPromise(() -> betaChain.serve(betaRequest))); // GH-90000

                // Let both requests reach the delegate before releasing
                boolean reached = bothInDelegate.await(2, java.util.concurrent.TimeUnit.SECONDS); // GH-90000
                bothSynced.countDown(); // GH-90000

                HttpResponse alphaResp = alphaFuture.get(2, java.util.concurrent.TimeUnit.SECONDS); // GH-90000
                HttpResponse betaResp  = betaFuture.get(2, java.util.concurrent.TimeUnit.SECONDS); // GH-90000

                assertThat(alphaResp.getCode()).as("Alpha request must succeed").isEqualTo(200);
                assertThat(betaResp.getCode()).as("Beta request must succeed").isEqualTo(200);

                // Each request must have seen only its own tenant
                assertThat(tenantCapturedByAlpha.get()) // GH-90000
                        .as("Alpha request delegate saw tenant-alpha, not tenant-beta")
                        .isEqualTo(TENANT_ALPHA); // GH-90000
                assertThat(tenantCapturedByBeta.get()) // GH-90000
                        .as("Beta request delegate saw tenant-beta, not tenant-alpha")
                        .isEqualTo(TENANT_BETA); // GH-90000
            } finally {
                executor.shutdownNow(); // GH-90000
            }
        }

        @Test
        @DisplayName("TenantContext is cleared after each request — no carryover to next request")
        void tenantContextDoesNotCarryOverBetweenSequentialRequests() { // GH-90000
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(RESOLVER); // GH-90000
            AsyncServlet delegate   = req -> HttpResponse.ok200().toPromise(); // GH-90000
            AsyncServlet chain      = filter.secure(TenantIsolationHttpFilter.wrap(delegate)); // GH-90000

            // First request for tenant-alpha
            HttpRequest alphaRequest = requestWithApiKey(HttpMethod.GET, "/api/v1/lifecycle/phases", ADMIN_KEY); // GH-90000
            runPromise(() -> chain.serve(alphaRequest)); // GH-90000

            // After the request the context must revert to the default
            assertThat(TenantContext.getCurrentTenantId()) // GH-90000
                    .as("TenantContext must revert to default after first request")
                    .isEqualTo("default-tenant");

            // Second request for tenant-beta — must see its own context, not alpha's
            AtomicReference<String> capturedInSecond = new AtomicReference<>(); // GH-90000
            AsyncServlet capturingDelegate = req -> {
                capturedInSecond.set(TenantContext.getCurrentTenantId()); // GH-90000
                return HttpResponse.ok200().toPromise(); // GH-90000
            };
            AsyncServlet betaChain = filter.secure(TenantIsolationHttpFilter.wrap(capturingDelegate)); // GH-90000

            HttpRequest betaRequest = requestWithApiKey(HttpMethod.GET, "/api/v1/lifecycle/phases", BETA_KEY); // GH-90000
            runPromise(() -> betaChain.serve(betaRequest)); // GH-90000

            assertThat(capturedInSecond.get()) // GH-90000
                    .as("Second request for tenant-beta must not see tenant-alpha's context")
                    .isEqualTo(TENANT_BETA); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static HttpRequest requestWithApiKey(HttpMethod method, String path, String apiKey) { // GH-90000
        return HttpRequest.builder(method, "http://localhost" + path) // GH-90000
                .withHeader(HttpHeaders.of("X-API-Key"), apiKey)
                                .withHeader(HttpHeaders.of("X-Forwarded-For"), "127.0.0.1")
                .build(); // GH-90000
    }
}

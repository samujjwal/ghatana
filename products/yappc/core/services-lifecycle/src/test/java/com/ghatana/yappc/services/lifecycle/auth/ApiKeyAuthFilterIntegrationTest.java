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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying the YAPPC auth filter chain behaviour (plan 4.3.5–4.3.7). // GH-90000
 *
 * <p>Tests exercise {@link ApiKeyAuthFilter} and {@link TenantIsolationHttpFilter} in isolation,
 * using lightweight in-memory request/response objects instead of a live HTTP server. This keeps
 * tests fast and deterministic while covering the exact security contracts specified in the plan:
 * <ol>
 *   <li>Missing {@code X-API-Key} header → {@code 401 Unauthorized}</li>
 *   <li>Valid API key via resolver → {@code 200 OK} with {@link TenantContext} populated</li>
 *   <li>Cross-tenant resource access → {@code 403 Forbidden}</li>
 *   <li>Strict isolation filter without tenant header → {@code 403 Forbidden}</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Integration tests for YAPPC auth filter chain (plan 4.3.5–4.3.7) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ApiKeyAuthFilter Integration Tests (4.3.5–4.3.7)")
class ApiKeyAuthFilterIntegrationTest extends EventloopTestBase {

    private static final String TENANT_ALPHA  = "tenant-alpha";
    private static final String TENANT_BETA   = "tenant-beta";
    private static final String VALID_KEY     = "svc-key-tenant-alpha";
    private static final String INVALID_KEY   = "bogus-key";

    /** Resolver that maps VALID_KEY → tenant-alpha Principal; everything else → empty. */
    private final ApiKeyResolver RESOLVER = key ->
            VALID_KEY.equals(key) // GH-90000
                    ? Optional.of(new Principal("svc-account", List.of("agent"), TENANT_ALPHA))
                    : Optional.empty(); // GH-90000

    @AfterEach
    void clearTenantContext() { // GH-90000
        TenantContext.clear(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4.3.5 — Missing X-API-Key → 401 Unauthorized
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("4.3.5 — Missing API key")
    class MissingApiKey {

        @Test
        @DisplayName("request without X-API-Key header → 401 Unauthorized")
        void shouldReturn401WhenApiKeyHeaderMissing() { // GH-90000
            // GIVEN
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(Set.of(VALID_KEY)); // GH-90000
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise(); // GH-90000
            HttpRequest request = request(HttpMethod.GET, "/api/v1/agents"); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> filter.secure(delegate).serve(request)); // GH-90000

            // THEN
            assertThat(response.getCode()) // GH-90000
                    .as("Missing API key must yield 401")
                    .isEqualTo(401); // GH-90000
        }

        @Test
        @DisplayName("request with wrong API key → 401 Unauthorized")
        void shouldReturn401WhenApiKeyIsInvalid() { // GH-90000
            // GIVEN
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(Set.of(VALID_KEY)); // GH-90000
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise(); // GH-90000
            HttpRequest request = requestWithApiKey(HttpMethod.GET, "/api/v1/agents", INVALID_KEY); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> filter.secure(delegate).serve(request)); // GH-90000

            // THEN
            assertThat(response.getCode()) // GH-90000
                    .as("Invalid API key must yield 401")
                    .isEqualTo(401); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4.3.6 — Valid API key → 200 OK + TenantContext populated
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("4.3.6 — Valid API key with tenant scope")
    class ValidApiKeyWithTenantScope {

        @Test
        @DisplayName("valid key resolved to tenant-alpha → delegate receives 200, TenantContext = tenant-alpha")
        void shouldPopulateTenantContextForValidApiKey() { // GH-90000
            // GIVEN
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(RESOLVER); // GH-90000
            AtomicReference<String> capturedTenant = new AtomicReference<>(); // GH-90000
            AsyncServlet delegate = req -> {
                capturedTenant.set(TenantContext.getCurrentTenantId()); // GH-90000
                return HttpResponse.ok200().toPromise(); // GH-90000
            };
            HttpRequest request = requestWithApiKey(HttpMethod.GET, "/api/v1/agents", VALID_KEY); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> filter.secure(delegate).serve(request)); // GH-90000

            // THEN — 200 returned and TenantContext carried the correct tenant
            assertThat(response.getCode()).as("Valid key must pass through to delegate").isEqualTo(200);
            assertThat(capturedTenant.get()) // GH-90000
                    .as("TenantContext must contain the tenant from the resolved Principal")
                    .isEqualTo(TENANT_ALPHA); // GH-90000
        }

        @Test
        @DisplayName("valid resolver-based key attaches Principal to request for downstream RBAC")
        void shouldAttachPrincipalForDownstreamAuthorization() { // GH-90000
            // GIVEN
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(RESOLVER); // GH-90000
            AtomicReference<Principal> capturedPrincipal = new AtomicReference<>(); // GH-90000
            AsyncServlet delegate = req -> {
                capturedPrincipal.set(req.getAttachment(Principal.class)); // GH-90000
                return HttpResponse.ok200().toPromise(); // GH-90000
            };
            HttpRequest request = requestWithApiKey(HttpMethod.GET, "/api/v1/agents", VALID_KEY); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> filter.secure(delegate).serve(request)); // GH-90000

            // THEN
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(capturedPrincipal.get()).isNotNull(); // GH-90000
            assertThat(capturedPrincipal.get().getTenantId()).isEqualTo(TENANT_ALPHA); // GH-90000
            assertThat(capturedPrincipal.get().getRoles()).contains("agent");
        }

        @Test
        @DisplayName("TenantContext is cleared after the request scope, preventing context leakage")
        void tenantContextIsClearedAfterRequest() { // GH-90000
            // GIVEN
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(RESOLVER); // GH-90000
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise(); // GH-90000
            HttpRequest request = requestWithApiKey(HttpMethod.GET, "/api/v1/agents", VALID_KEY); // GH-90000

            // WHEN — dispatch then check context on same thread (simulates next-request) // GH-90000
            runPromise(() -> filter.secure(delegate).serve(request)); // GH-90000
            String tenantAfterRequest = TenantContext.getCurrentTenantId(); // GH-90000

            // THEN — TenantContext.scope(principal) auto-closed; falls back to "default-tenant" // GH-90000
            assertThat(tenantAfterRequest) // GH-90000
                    .as("TenantContext must not leak across request boundaries")
                    .isEqualTo("default-tenant");
        }

        @Test
        @DisplayName("allowlist-based filter does NOT populate TenantContext (no resolver)")
        void allowlistFilterDoesNotSetTenantContext() { // GH-90000
            // GIVEN — allowlist mode (no ApiKeyResolver); TenantContext stays at default // GH-90000
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(Set.of(VALID_KEY)); // GH-90000
            AtomicReference<String> capturedTenant = new AtomicReference<>(); // GH-90000
            AsyncServlet delegate = req -> {
                capturedTenant.set(TenantContext.getCurrentTenantId()); // GH-90000
                return HttpResponse.ok200().toPromise(); // GH-90000
            };
            HttpRequest request = requestWithApiKey(HttpMethod.GET, "/api/v1/agents", VALID_KEY); // GH-90000

            // WHEN
            runPromise(() -> filter.secure(delegate).serve(request)); // GH-90000

            // THEN — context not populated by filter alone; stays at TenantContext default
            assertThat(capturedTenant.get()) // GH-90000
                    .as("Allowlist filter (no resolver) does not set TenantContext")
                    .isEqualTo("default-tenant");
        }
    }

    @Nested
    @DisplayName("RBAC enforcement with resolver principals")
    class RbacEnforcement {

        @Test
        @DisplayName("viewer role can read but cannot write")
        void viewerRoleReadOnly() { // GH-90000
            ApiKeyResolver viewerResolver = key ->
                    "viewer-key".equals(key) // GH-90000
                            ? Optional.of(new Principal("viewer-user", List.of("viewer"), TENANT_ALPHA))
                            : Optional.empty(); // GH-90000

            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository()); // GH-90000
            policyService.createPolicy("viewer-read", "viewer read policy", "viewer", "yappc:lifecycle-api", Set.of("read"));

            ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(viewerResolver); // GH-90000
            RBACFilter readFilter = new RBACFilter(policyService, "read", "yappc:lifecycle-api"); // GH-90000
            RBACFilter writeFilter = new RBACFilter(policyService, "write", "yappc:lifecycle-api"); // GH-90000

            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise(); // GH-90000

            HttpRequest readRequest = requestWithApiKey(HttpMethod.GET, "/api/v1/lifecycle/phases", "viewer-key"); // GH-90000
            HttpResponse readResponse = runPromise(() -> authFilter.secure(readFilter.secure(delegate)).serve(readRequest)); // GH-90000
            assertThat(readResponse.getCode()).isEqualTo(200); // GH-90000

            HttpRequest writeRequest = requestWithApiKey(HttpMethod.POST, "/api/v1/lifecycle/advance", "viewer-key"); // GH-90000
            HttpResponse writeResponse = runPromise(() -> authFilter.secure(writeFilter.secure(delegate)).serve(writeRequest)); // GH-90000
            assertThat(writeResponse.getCode()).isEqualTo(403); // GH-90000
        }

        @Test
        @DisplayName("read-only endpoint chain protects metrics-like endpoint")
        void readOnlyChainProtectsMetricsEndpoint() { // GH-90000
            ApiKeyResolver resolver = key ->
                "metrics-viewer-key".equals(key) // GH-90000
                    ? Optional.of(new Principal("metrics-viewer", List.of("viewer"), TENANT_ALPHA))
                    : Optional.empty(); // GH-90000

            PolicyService policyService = new PolicyService(new InMemoryPolicyRepository()); // GH-90000
            policyService.createPolicy( // GH-90000
                "viewer-read-metrics",
                "viewer read metrics policy",
                "viewer",
                    "yappc:lifecycle-api",
                Set.of("read"));

            ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(resolver); // GH-90000
                RBACFilter readFilter = new RBACFilter(policyService, "read", "yappc:lifecycle-api"); // GH-90000
            RateLimitFilter rateLimitFilter = new RateLimitFilter(100, 60); // GH-90000

            AsyncServlet metricsDelegate = req -> HttpResponse.ok200().withPlainText("metrics").toPromise();
            AsyncServlet securedMetrics = authFilter.secure(readFilter.secure(rateLimitFilter.wrap(metricsDelegate))); // GH-90000

            HttpRequest missingKeyRequest = HttpRequest.builder(HttpMethod.GET, "http://localhost/metrics") // GH-90000
                    .withHeader(HttpHeaders.of("X-Forwarded-For"), "127.0.0.1")
                    .build(); // GH-90000
            HttpResponse missingKey = runPromise(() -> // GH-90000
                    securedMetrics.serve(missingKeyRequest)); // GH-90000
            assertThat(missingKey.getCode()).isEqualTo(401); // GH-90000

            HttpRequest validKeyRequest = HttpRequest.builder(HttpMethod.GET, "http://localhost/metrics") // GH-90000
                    .withHeader(HttpHeaders.of("X-Forwarded-For"), "127.0.0.1")
                    .withHeader(HttpHeaders.of("X-API-Key"), "metrics-viewer-key")
                    .build(); // GH-90000
            HttpResponse validKey = runPromise(() -> securedMetrics.serve(validKeyRequest)); // GH-90000
            assertThat(validKey.getCode()).isEqualTo(200); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4.3.7 — Wrong tenant's resource → 403 Forbidden
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("4.3.7 — Cross-tenant resource access denied")
    class CrossTenantResourceAccessDenied {

        /**
         * Simulates a downstream handler that enforces tenant ownership of the requested resource.
         * This is the business-layer guard that complements the auth filter; together they form
         * the full tenant isolation contract.
         */
        private static final AsyncServlet TENANT_ENFORCING_DELEGATE = req -> {
            String activeTenant  = TenantContext.getCurrentTenantId(); // GH-90000
            String resourceTenant = req.getHeader(HttpHeaders.of("X-Resource-Tenant"));
            if (resourceTenant != null && !activeTenant.equals(resourceTenant)) { // GH-90000
                return HttpResponse.ofCode(403) // GH-90000
                        .withJson("{\"error\":{\"code\":\"FORBIDDEN\"," // GH-90000
                                + "\"message\":\"Resource belongs to a different tenant\"}}")
                        .toPromise(); // GH-90000
            }
            return HttpResponse.ok200().toPromise(); // GH-90000
        };

        @Test
        @DisplayName("API key for tenant-alpha accessing tenant-beta's resource → 403 Forbidden")
        void shouldReturn403WhenResourceBelongsToDifferentTenant() { // GH-90000
            // GIVEN — key resolves to tenant-alpha, but the resource belongs to tenant-beta
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(RESOLVER); // GH-90000
            HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/workspaces/W1") // GH-90000
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_KEY)
                    .withHeader(HttpHeaders.of("X-Resource-Tenant"), TENANT_BETA) // different owner
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> filter.secure(TENANT_ENFORCING_DELEGATE).serve(request)); // GH-90000

            // THEN
            assertThat(response.getCode()) // GH-90000
                    .as("Cross-tenant resource access must be rejected with 403")
                    .isEqualTo(403); // GH-90000
        }

        @Test
        @DisplayName("API key for tenant-alpha accessing tenant-alpha's resource → 200 OK")
        void shouldReturn200WhenResourceBelongsToSameTenant() { // GH-90000
            // GIVEN — same tenant owns both the key and the resource
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(RESOLVER); // GH-90000
            HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/workspaces/W1") // GH-90000
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_KEY)
                    .withHeader(HttpHeaders.of("X-Resource-Tenant"), TENANT_ALPHA) // same owner
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> filter.secure(TENANT_ENFORCING_DELEGATE).serve(request)); // GH-90000

            // THEN
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("TenantIsolationHttpFilter in strict mode without X-Tenant-ID header → 403")
        void strictTenantIsolationFilterRejectsMissingTenantHeader() { // GH-90000
            // GIVEN — strict mode requires X-Tenant-ID; missing header → 403
            AsyncServlet strictFilter = TenantIsolationHttpFilter.strict( // GH-90000
                    req -> HttpResponse.ok200().toPromise()); // GH-90000
            HttpRequest request = request(HttpMethod.GET, "/api/v1/agents"); // no X-Tenant-ID // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> strictFilter.serve(request)); // GH-90000

            // THEN
            assertThat(response.getCode()) // GH-90000
                    .as("Strict isolation filter must reject requests without X-Tenant-ID")
                    .isEqualTo(403); // GH-90000
        }

        @Test
        @DisplayName("TenantIsolationHttpFilter in lenient mode without X-Tenant-ID → 200 (default-tenant)")
        void lenientTenantIsolationFilterAllowsMissingTenantHeader() { // GH-90000
            // GIVEN — lenient mode falls back to "default-tenant" when header is absent
            AtomicReference<String> capturedTenant = new AtomicReference<>(); // GH-90000
            AsyncServlet lenientFilter = TenantIsolationHttpFilter.wrap(req -> { // GH-90000
                capturedTenant.set(TenantContext.getCurrentTenantId()); // GH-90000
                return HttpResponse.ok200().toPromise(); // GH-90000
            });
            HttpRequest request = request(HttpMethod.GET, "/api/v1/agents"); // no X-Tenant-ID // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> lenientFilter.serve(request)); // GH-90000

            // THEN — request passes; default-tenant is used
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            assertThat(capturedTenant.get()).isEqualTo("default-tenant");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static HttpRequest request(HttpMethod method, String path) { // GH-90000
        return HttpRequest.builder(method, "http://localhost" + path).build(); // GH-90000
    }

    private static HttpRequest requestWithApiKey(HttpMethod method, String path, String apiKey) { // GH-90000
        return HttpRequest.builder(method, "http://localhost" + path) // GH-90000
                .withHeader(HttpHeaders.of("X-API-Key"), apiKey)
                .build(); // GH-90000
    }
}

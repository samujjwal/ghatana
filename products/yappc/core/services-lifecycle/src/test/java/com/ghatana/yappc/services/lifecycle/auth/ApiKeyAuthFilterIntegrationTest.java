package com.ghatana.yappc.services.lifecycle.auth;

import com.ghatana.platform.governance.security.ApiKeyAuthFilter;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.governance.security.TenantIsolationHttpFilter;
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
 * Integration tests verifying the YAPPC auth filter chain behaviour (plan 4.3.5–4.3.7).
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
 * @doc.purpose Integration tests for YAPPC auth filter chain (plan 4.3.5–4.3.7)
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
            VALID_KEY.equals(key)
                    ? Optional.of(new Principal("svc-account", List.of("agent"), TENANT_ALPHA))
                    : Optional.empty();

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4.3.5 — Missing X-API-Key → 401 Unauthorized
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("4.3.5 — Missing API key")
    class MissingApiKey {

        @Test
        @DisplayName("request without X-API-Key header → 401 Unauthorized")
        void shouldReturn401WhenApiKeyHeaderMissing() {
            // GIVEN
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(Set.of(VALID_KEY));
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise();
            HttpRequest request = request(HttpMethod.GET, "/api/v1/agents");

            // WHEN
            HttpResponse response = runPromise(() -> filter.secure(delegate).serve(request));

            // THEN
            assertThat(response.getCode())
                    .as("Missing API key must yield 401")
                    .isEqualTo(401);
        }

        @Test
        @DisplayName("request with wrong API key → 401 Unauthorized")
        void shouldReturn401WhenApiKeyIsInvalid() {
            // GIVEN
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(Set.of(VALID_KEY));
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise();
            HttpRequest request = requestWithApiKey(HttpMethod.GET, "/api/v1/agents", INVALID_KEY);

            // WHEN
            HttpResponse response = runPromise(() -> filter.secure(delegate).serve(request));

            // THEN
            assertThat(response.getCode())
                    .as("Invalid API key must yield 401")
                    .isEqualTo(401);
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
        void shouldPopulateTenantContextForValidApiKey() {
            // GIVEN
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(RESOLVER);
            AtomicReference<String> capturedTenant = new AtomicReference<>();
            AsyncServlet delegate = req -> {
                capturedTenant.set(TenantContext.getCurrentTenantId());
                return HttpResponse.ok200().toPromise();
            };
            HttpRequest request = requestWithApiKey(HttpMethod.GET, "/api/v1/agents", VALID_KEY);

            // WHEN
            HttpResponse response = runPromise(() -> filter.secure(delegate).serve(request));

            // THEN — 200 returned and TenantContext carried the correct tenant
            assertThat(response.getCode()).as("Valid key must pass through to delegate").isEqualTo(200);
            assertThat(capturedTenant.get())
                    .as("TenantContext must contain the tenant from the resolved Principal")
                    .isEqualTo(TENANT_ALPHA);
        }

        @Test
        @DisplayName("TenantContext is cleared after the request scope, preventing context leakage")
        void tenantContextIsClearedAfterRequest() {
            // GIVEN
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(RESOLVER);
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise();
            HttpRequest request = requestWithApiKey(HttpMethod.GET, "/api/v1/agents", VALID_KEY);

            // WHEN — dispatch then check context on same thread (simulates next-request)
            runPromise(() -> filter.secure(delegate).serve(request));
            String tenantAfterRequest = TenantContext.getCurrentTenantId();

            // THEN — TenantContext.scope(principal) auto-closed; falls back to "default-tenant"
            assertThat(tenantAfterRequest)
                    .as("TenantContext must not leak across request boundaries")
                    .isEqualTo("default-tenant");
        }

        @Test
        @DisplayName("allowlist-based filter does NOT populate TenantContext (no resolver)")
        void allowlistFilterDoesNotSetTenantContext() {
            // GIVEN — allowlist mode (no ApiKeyResolver); TenantContext stays at default
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(Set.of(VALID_KEY));
            AtomicReference<String> capturedTenant = new AtomicReference<>();
            AsyncServlet delegate = req -> {
                capturedTenant.set(TenantContext.getCurrentTenantId());
                return HttpResponse.ok200().toPromise();
            };
            HttpRequest request = requestWithApiKey(HttpMethod.GET, "/api/v1/agents", VALID_KEY);

            // WHEN
            runPromise(() -> filter.secure(delegate).serve(request));

            // THEN — context not populated by filter alone; stays at TenantContext default
            assertThat(capturedTenant.get())
                    .as("Allowlist filter (no resolver) does not set TenantContext")
                    .isEqualTo("default-tenant");
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
            String activeTenant  = TenantContext.getCurrentTenantId();
            String resourceTenant = req.getHeader(HttpHeaders.of("X-Resource-Tenant"));
            if (resourceTenant != null && !activeTenant.equals(resourceTenant)) {
                return HttpResponse.ofCode(403)
                        .withJson("{\"error\":{\"code\":\"FORBIDDEN\","
                                + "\"message\":\"Resource belongs to a different tenant\"}}")
                        .toPromise();
            }
            return HttpResponse.ok200().toPromise();
        };

        @Test
        @DisplayName("API key for tenant-alpha accessing tenant-beta's resource → 403 Forbidden")
        void shouldReturn403WhenResourceBelongsToDifferentTenant() {
            // GIVEN — key resolves to tenant-alpha, but the resource belongs to tenant-beta
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(RESOLVER);
            HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/workspaces/W1")
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_KEY)
                    .withHeader(HttpHeaders.of("X-Resource-Tenant"), TENANT_BETA) // different owner
                    .build();

            // WHEN
            HttpResponse response = runPromise(() -> filter.secure(TENANT_ENFORCING_DELEGATE).serve(request));

            // THEN
            assertThat(response.getCode())
                    .as("Cross-tenant resource access must be rejected with 403")
                    .isEqualTo(403);
        }

        @Test
        @DisplayName("API key for tenant-alpha accessing tenant-alpha's resource → 200 OK")
        void shouldReturn200WhenResourceBelongsToSameTenant() {
            // GIVEN — same tenant owns both the key and the resource
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(RESOLVER);
            HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/workspaces/W1")
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_KEY)
                    .withHeader(HttpHeaders.of("X-Resource-Tenant"), TENANT_ALPHA) // same owner
                    .build();

            // WHEN
            HttpResponse response = runPromise(() -> filter.secure(TENANT_ENFORCING_DELEGATE).serve(request));

            // THEN
            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("TenantIsolationHttpFilter in strict mode without X-Tenant-ID header → 403")
        void strictTenantIsolationFilterRejectsMissingTenantHeader() {
            // GIVEN — strict mode requires X-Tenant-ID; missing header → 403
            AsyncServlet strictFilter = TenantIsolationHttpFilter.strict(
                    req -> HttpResponse.ok200().toPromise());
            HttpRequest request = request(HttpMethod.GET, "/api/v1/agents"); // no X-Tenant-ID

            // WHEN
            HttpResponse response = runPromise(() -> strictFilter.serve(request));

            // THEN
            assertThat(response.getCode())
                    .as("Strict isolation filter must reject requests without X-Tenant-ID")
                    .isEqualTo(403);
        }

        @Test
        @DisplayName("TenantIsolationHttpFilter in lenient mode without X-Tenant-ID → 200 (default-tenant)")
        void lenientTenantIsolationFilterAllowsMissingTenantHeader() {
            // GIVEN — lenient mode falls back to "default-tenant" when header is absent
            AtomicReference<String> capturedTenant = new AtomicReference<>();
            AsyncServlet lenientFilter = TenantIsolationHttpFilter.wrap(req -> {
                capturedTenant.set(TenantContext.getCurrentTenantId());
                return HttpResponse.ok200().toPromise();
            });
            HttpRequest request = request(HttpMethod.GET, "/api/v1/agents"); // no X-Tenant-ID

            // WHEN
            HttpResponse response = runPromise(() -> lenientFilter.serve(request));

            // THEN — request passes; default-tenant is used
            assertThat(response.getCode()).isEqualTo(200);
            assertThat(capturedTenant.get()).isEqualTo("default-tenant");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static HttpRequest request(HttpMethod method, String path) {
        return HttpRequest.builder(method, "http://localhost" + path).build();
    }

    private static HttpRequest requestWithApiKey(HttpMethod method, String path, String apiKey) {
        return HttpRequest.builder(method, "http://localhost" + path)
                .withHeader(HttpHeaders.of("X-API-Key"), apiKey)
                .build();
    }
}

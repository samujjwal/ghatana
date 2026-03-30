package com.ghatana.yappc.services.lifecycle.auth;

import com.ghatana.platform.governance.security.ApiKeyAuthFilter;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.rbac.InMemoryPolicyRepository;
import com.ghatana.platform.security.rbac.PolicyService;
import com.ghatana.platform.security.rbac.RBACFilter;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * YAPPC Role Matrix Test — validates the complete permission matrix across all
 * YAPPC service resources and roles.
 *
 * <h2>Role Matrix</h2>
 * <pre>
 * Role     | yappc:lifecycle-api    | yappc:ai-api           | yappc:scaffold-api
 * ---------+------------------------+------------------------+-------------------
 * admin    | read + write (via *)   | read + write (via *)   | read + write (via *)
 * editor   | read + write           | read + write           | read + write
 * agent    | read + write           | read + write           | read + write
 * viewer   | read only              | read only              | read only
 * unknown  | denied                 | denied                 | denied
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Enforces the full YAPPC role permission matrix across all service resources
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YAPPC Role Matrix Tests")
class YappcRoleMatrixTest extends EventloopTestBase {

    private static final String TENANT = "tenant-alpha";

    /** Canonical YAPPC resources that correspond to each service's API. */
    enum YappcResource {
        LIFECYCLE("yappc:lifecycle-api"),
        AI("yappc:ai-api"),
        SCAFFOLD("yappc:scaffold-api");

        final String resourceName;

        YappcResource(String resourceName) {
            this.resourceName = resourceName;
        }
    }

    /** YAPPC role names used in policy and API key mappings. */
    enum YappcRole {
        ADMIN("admin"),
        EDITOR("editor"),
        AGENT("agent"),
        VIEWER("viewer");

        final String roleName;

        YappcRole(String roleName) {
            this.roleName = roleName;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin: full access to all resources
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("admin role — read and write on all resources")
    class AdminRoleMatrix {

        @ParameterizedTest
        @EnumSource(YappcResource.class)
        @DisplayName("admin can READ on {}")
        void adminCanRead(YappcResource resource) {
            PolicyService policyService = buildPolicyService(resource.resourceName);
            AsyncServlet secured = buildSecuredChain("admin-key", YappcRole.ADMIN, policyService, "read", resource.resourceName);

            HttpResponse response = runPromise(() ->
                    secured.serve(requestWithKey(HttpMethod.GET, "/api/v1/test", "admin-key")));

            assertThat(response.getCode())
                    .as("admin should READ on " + resource.resourceName)
                    .isEqualTo(200);
        }

        @ParameterizedTest
        @EnumSource(YappcResource.class)
        @DisplayName("admin can WRITE on {}")
        void adminCanWrite(YappcResource resource) {
            PolicyService policyService = buildPolicyService(resource.resourceName);
            AsyncServlet secured = buildSecuredChain("admin-key", YappcRole.ADMIN, policyService, "write", resource.resourceName);

            HttpResponse response = runPromise(() ->
                    secured.serve(requestWithKey(HttpMethod.POST, "/api/v1/test", "admin-key")));

            assertThat(response.getCode())
                    .as("admin should WRITE on " + resource.resourceName)
                    .isEqualTo(200);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Editor: read + write on all resources
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("editor role — read and write on all resources")
    class EditorRoleMatrix {

        @ParameterizedTest
        @EnumSource(YappcResource.class)
        @DisplayName("editor can READ on {}")
        void editorCanRead(YappcResource resource) {
            PolicyService policyService = buildPolicyService(resource.resourceName);
            AsyncServlet secured = buildSecuredChain("editor-key", YappcRole.EDITOR, policyService, "read", resource.resourceName);

            HttpResponse response = runPromise(() ->
                    secured.serve(requestWithKey(HttpMethod.GET, "/api/v1/test", "editor-key")));

            assertThat(response.getCode())
                    .as("editor should READ on " + resource.resourceName)
                    .isEqualTo(200);
        }

        @ParameterizedTest
        @EnumSource(YappcResource.class)
        @DisplayName("editor can WRITE on {}")
        void editorCanWrite(YappcResource resource) {
            PolicyService policyService = buildPolicyService(resource.resourceName);
            AsyncServlet secured = buildSecuredChain("editor-key", YappcRole.EDITOR, policyService, "write", resource.resourceName);

            HttpResponse response = runPromise(() ->
                    secured.serve(requestWithKey(HttpMethod.POST, "/api/v1/test", "editor-key")));

            assertThat(response.getCode())
                    .as("editor should WRITE on " + resource.resourceName)
                    .isEqualTo(200);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Agent: read + write on all resources (service-to-service use case)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("agent role — read and write on all resources")
    class AgentRoleMatrix {

        @ParameterizedTest
        @EnumSource(YappcResource.class)
        @DisplayName("agent can READ on {}")
        void agentCanRead(YappcResource resource) {
            PolicyService policyService = buildPolicyService(resource.resourceName);
            AsyncServlet secured = buildSecuredChain("agent-key", YappcRole.AGENT, policyService, "read", resource.resourceName);

            HttpResponse response = runPromise(() ->
                    secured.serve(requestWithKey(HttpMethod.GET, "/api/v1/test", "agent-key")));

            assertThat(response.getCode())
                    .as("agent should READ on " + resource.resourceName)
                    .isEqualTo(200);
        }

        @ParameterizedTest
        @EnumSource(YappcResource.class)
        @DisplayName("agent can WRITE on {}")
        void agentCanWrite(YappcResource resource) {
            PolicyService policyService = buildPolicyService(resource.resourceName);
            AsyncServlet secured = buildSecuredChain("agent-key", YappcRole.AGENT, policyService, "write", resource.resourceName);

            HttpResponse response = runPromise(() ->
                    secured.serve(requestWithKey(HttpMethod.POST, "/api/v1/test", "agent-key")));

            assertThat(response.getCode())
                    .as("agent should WRITE on " + resource.resourceName)
                    .isEqualTo(200);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Viewer: read-only, no write
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("viewer role — read only, writes denied")
    class ViewerRoleMatrix {

        @ParameterizedTest
        @EnumSource(YappcResource.class)
        @DisplayName("viewer can READ on {}")
        void viewerCanRead(YappcResource resource) {
            PolicyService policyService = buildPolicyService(resource.resourceName);
            AsyncServlet secured = buildSecuredChain("viewer-key", YappcRole.VIEWER, policyService, "read", resource.resourceName);

            HttpResponse response = runPromise(() ->
                    secured.serve(requestWithKey(HttpMethod.GET, "/api/v1/test", "viewer-key")));

            assertThat(response.getCode())
                    .as("viewer should READ on " + resource.resourceName)
                    .isEqualTo(200);
        }

        @ParameterizedTest
        @EnumSource(YappcResource.class)
        @DisplayName("viewer CANNOT WRITE on {}")
        void viewerCannotWrite(YappcResource resource) {
            PolicyService policyService = buildPolicyService(resource.resourceName);
            AsyncServlet secured = buildSecuredChain("viewer-key", YappcRole.VIEWER, policyService, "write", resource.resourceName);

            HttpResponse response = runPromise(() ->
                    secured.serve(requestWithKey(HttpMethod.POST, "/api/v1/test", "viewer-key")));

            assertThat(response.getCode())
                    .as("viewer must NOT WRITE on " + resource.resourceName)
                    .isEqualTo(403);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unknown / unregistered roles — all access denied
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("unknown role — all access denied")
    class UnknownRoleDenied {

        @ParameterizedTest
        @EnumSource(YappcResource.class)
        @DisplayName("key with unknown role gets 403 on READ for {}")
        void unknownRoleDeniedRead(YappcResource resource) {
            PolicyService policyService = buildPolicyService(resource.resourceName);
            // Resolver maps "unknown-key" to a Principal with role "unknown" — no policies match
            ApiKeyResolver resolver = key ->
                    "unknown-key".equals(key)
                            ? Optional.of(new Principal("unknown-user", List.of("unknown"), TENANT))
                            : Optional.empty();
            ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(resolver);
            RBACFilter readFilter = new RBACFilter(policyService, "read", resource.resourceName);
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise();
            AsyncServlet secured = authFilter.secure(readFilter.secure(delegate));

            HttpResponse response = runPromise(() ->
                    secured.serve(requestWithKey(HttpMethod.GET, "/api/v1/test", "unknown-key")));

            assertThat(response.getCode())
                    .as("unknown role must be denied READ on " + resource.resourceName)
                    .isEqualTo(403);
        }

        @ParameterizedTest
        @EnumSource(YappcResource.class)
        @DisplayName("key with unknown role gets 403 on WRITE for {}")
        void unknownRoleDeniedWrite(YappcResource resource) {
            PolicyService policyService = buildPolicyService(resource.resourceName);
            ApiKeyResolver resolver = key ->
                    "unknown-key".equals(key)
                            ? Optional.of(new Principal("unknown-user", List.of("unknown"), TENANT))
                            : Optional.empty();
            ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(resolver);
            RBACFilter writeFilter = new RBACFilter(policyService, "write", resource.resourceName);
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise();
            AsyncServlet secured = authFilter.secure(writeFilter.secure(delegate));

            HttpResponse response = runPromise(() ->
                    secured.serve(requestWithKey(HttpMethod.POST, "/api/v1/test", "unknown-key")));

            assertThat(response.getCode())
                    .as("unknown role must be denied WRITE on " + resource.resourceName)
                    .isEqualTo(403);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Missing / invalid API key — always 401 regardless of resource
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("missing or invalid API key — always 401")
    class MissingKeyDenied {

        @ParameterizedTest
        @EnumSource(YappcResource.class)
        @DisplayName("no API key header → 401 on {} read")
        void noKeyReturns401OnRead(YappcResource resource) {
            PolicyService policyService = buildPolicyService(resource.resourceName);
            ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(Set.of("valid-key"));
            RBACFilter readFilter = new RBACFilter(policyService, "read", resource.resourceName);
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise();
            AsyncServlet secured = authFilter.secure(readFilter.secure(delegate));

            HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/test").build();
            HttpResponse response = runPromise(() -> secured.serve(request));

            assertThat(response.getCode())
                    .as("missing API key must yield 401 on " + resource.resourceName)
                    .isEqualTo(401);
        }

        @ParameterizedTest
        @EnumSource(YappcResource.class)
        @DisplayName("invalid API key → 401 on {} write")
        void invalidKeyReturns401OnWrite(YappcResource resource) {
            PolicyService policyService = buildPolicyService(resource.resourceName);
            ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(Set.of("valid-key"));
            RBACFilter writeFilter = new RBACFilter(policyService, "write", resource.resourceName);
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise();
            AsyncServlet secured = authFilter.secure(writeFilter.secure(delegate));

            HttpResponse response = runPromise(() ->
                    secured.serve(requestWithKey(HttpMethod.POST, "/api/v1/test", "bad-key")));

            assertThat(response.getCode())
                    .as("invalid API key must yield 401 on " + resource.resourceName)
                    .isEqualTo(401);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the standard four-role policy service for a given resource name.
     * Mirrors the policies in {@link com.ghatana.yappc.services.security.YappcApiSecurity}.
     */
    private static PolicyService buildPolicyService(String resource) {
        PolicyService service = new PolicyService(new InMemoryPolicyRepository());
        service.createPolicy("admin-" + resource, "admin all", "admin", resource, Set.of("*"));
        service.createPolicy("editor-" + resource, "editor rw", "editor", resource, Set.of("read", "write"));
        service.createPolicy("agent-" + resource, "agent rw", "agent", resource, Set.of("read", "write"));
        service.createPolicy("viewer-" + resource, "viewer r", "viewer", resource, Set.of("read"));
        return service;
    }

    /**
     * Builds a two-filter chain (auth → RBAC) for the given key, role, and action.
     */
    private static AsyncServlet buildSecuredChain(
            String apiKey, YappcRole role, PolicyService policyService, String action, String resource) {
        ApiKeyResolver resolver = key ->
                apiKey.equals(key)
                        ? Optional.of(new Principal(role.roleName + "-user", List.of(role.roleName), TENANT))
                        : Optional.empty();
        ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(resolver);
        RBACFilter rbacFilter = new RBACFilter(policyService, action, resource);
        AsyncServlet delegate = req -> HttpResponse.ok200().toPromise();
        return authFilter.secure(rbacFilter.secure(delegate));
    }

    private static HttpRequest requestWithKey(HttpMethod method, String path, String apiKey) {
        return HttpRequest.builder(method, "http://localhost" + path)
                .withHeader(HttpHeaders.of("X-API-Key"), apiKey)
                .build();
    }
}

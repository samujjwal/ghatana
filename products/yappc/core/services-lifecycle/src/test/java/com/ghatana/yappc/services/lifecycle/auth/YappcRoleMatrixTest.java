package com.ghatana.yappc.services.lifecycle.auth;

import com.ghatana.platform.http.security.filter.ApiKeyAuthFilter;
import com.ghatana.platform.http.security.filter.RBACFilter;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.rbac.InMemoryPolicyRepository;
import com.ghatana.platform.security.rbac.PolicyService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * <p><b>Role Matrix</b></p>
 * <pre>
 * Role     | yappc:lifecycle-api    | yappc:ai-api           | yappc:scaffold-api
 * ---------+------------------------+------------------------+-------------------
 * admin    | read + write (via *)   | read + write (via *)   | read + write (via *) // GH-90000
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
@DisplayName("YAPPC Role Matrix Tests [GH-90000]")
class YappcRoleMatrixTest extends EventloopTestBase {

    private static final String TENANT = "tenant-alpha";

    /** Canonical YAPPC resources that correspond to each service's API. */
    enum YappcResource {
        LIFECYCLE("yappc:lifecycle-api [GH-90000]"),
        AI("yappc:ai-api [GH-90000]"),
        SCAFFOLD("yappc:scaffold-api [GH-90000]");

        final String resourceName;

        YappcResource(String resourceName) { // GH-90000
            this.resourceName = resourceName;
        }
    }

    /** YAPPC role names used in policy and API key mappings. */
    enum YappcRole {
        ADMIN("admin [GH-90000]"),
        EDITOR("editor [GH-90000]"),
        AGENT("agent [GH-90000]"),
        VIEWER("viewer [GH-90000]");

        final String roleName;

        YappcRole(String roleName) { // GH-90000
            this.roleName = roleName;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin: full access to all resources
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("admin role — read and write on all resources [GH-90000]")
    class AdminRoleMatrix {

        @ParameterizedTest
        @EnumSource(YappcResource.class) // GH-90000
        @DisplayName("admin can READ on {} [GH-90000]")
        void adminCanRead(YappcResource resource) { // GH-90000
            PolicyService policyService = buildPolicyService(resource.resourceName); // GH-90000
            AsyncServlet secured = buildSecuredChain("admin-key", YappcRole.ADMIN, policyService, "read", resource.resourceName); // GH-90000

            HttpResponse response = runPromise(() -> // GH-90000
                    secured.serve(requestWithKey(HttpMethod.GET, "/api/v1/test", "admin-key"))); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("admin should READ on " + resource.resourceName) // GH-90000
                    .isEqualTo(200); // GH-90000
        }

        @ParameterizedTest
        @EnumSource(YappcResource.class) // GH-90000
        @DisplayName("admin can WRITE on {} [GH-90000]")
        void adminCanWrite(YappcResource resource) { // GH-90000
            PolicyService policyService = buildPolicyService(resource.resourceName); // GH-90000
            AsyncServlet secured = buildSecuredChain("admin-key", YappcRole.ADMIN, policyService, "write", resource.resourceName); // GH-90000

            HttpResponse response = runPromise(() -> // GH-90000
                    secured.serve(requestWithKey(HttpMethod.POST, "/api/v1/test", "admin-key"))); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("admin should WRITE on " + resource.resourceName) // GH-90000
                    .isEqualTo(200); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Editor: read + write on all resources
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("editor role — read and write on all resources [GH-90000]")
    class EditorRoleMatrix {

        @ParameterizedTest
        @EnumSource(YappcResource.class) // GH-90000
        @DisplayName("editor can READ on {} [GH-90000]")
        void editorCanRead(YappcResource resource) { // GH-90000
            PolicyService policyService = buildPolicyService(resource.resourceName); // GH-90000
            AsyncServlet secured = buildSecuredChain("editor-key", YappcRole.EDITOR, policyService, "read", resource.resourceName); // GH-90000

            HttpResponse response = runPromise(() -> // GH-90000
                    secured.serve(requestWithKey(HttpMethod.GET, "/api/v1/test", "editor-key"))); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("editor should READ on " + resource.resourceName) // GH-90000
                    .isEqualTo(200); // GH-90000
        }

        @ParameterizedTest
        @EnumSource(YappcResource.class) // GH-90000
        @DisplayName("editor can WRITE on {} [GH-90000]")
        void editorCanWrite(YappcResource resource) { // GH-90000
            PolicyService policyService = buildPolicyService(resource.resourceName); // GH-90000
            AsyncServlet secured = buildSecuredChain("editor-key", YappcRole.EDITOR, policyService, "write", resource.resourceName); // GH-90000

            HttpResponse response = runPromise(() -> // GH-90000
                    secured.serve(requestWithKey(HttpMethod.POST, "/api/v1/test", "editor-key"))); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("editor should WRITE on " + resource.resourceName) // GH-90000
                    .isEqualTo(200); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Agent: read + write on all resources (service-to-service use case) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("agent role — read and write on all resources [GH-90000]")
    class AgentRoleMatrix {

        @ParameterizedTest
        @EnumSource(YappcResource.class) // GH-90000
        @DisplayName("agent can READ on {} [GH-90000]")
        void agentCanRead(YappcResource resource) { // GH-90000
            PolicyService policyService = buildPolicyService(resource.resourceName); // GH-90000
            AsyncServlet secured = buildSecuredChain("agent-key", YappcRole.AGENT, policyService, "read", resource.resourceName); // GH-90000

            HttpResponse response = runPromise(() -> // GH-90000
                    secured.serve(requestWithKey(HttpMethod.GET, "/api/v1/test", "agent-key"))); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("agent should READ on " + resource.resourceName) // GH-90000
                    .isEqualTo(200); // GH-90000
        }

        @ParameterizedTest
        @EnumSource(YappcResource.class) // GH-90000
        @DisplayName("agent can WRITE on {} [GH-90000]")
        void agentCanWrite(YappcResource resource) { // GH-90000
            PolicyService policyService = buildPolicyService(resource.resourceName); // GH-90000
            AsyncServlet secured = buildSecuredChain("agent-key", YappcRole.AGENT, policyService, "write", resource.resourceName); // GH-90000

            HttpResponse response = runPromise(() -> // GH-90000
                    secured.serve(requestWithKey(HttpMethod.POST, "/api/v1/test", "agent-key"))); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("agent should WRITE on " + resource.resourceName) // GH-90000
                    .isEqualTo(200); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Viewer: read-only, no write
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("viewer role — read only, writes denied [GH-90000]")
    class ViewerRoleMatrix {

        @ParameterizedTest
        @EnumSource(YappcResource.class) // GH-90000
        @DisplayName("viewer can READ on {} [GH-90000]")
        void viewerCanRead(YappcResource resource) { // GH-90000
            PolicyService policyService = buildPolicyService(resource.resourceName); // GH-90000
            AsyncServlet secured = buildSecuredChain("viewer-key", YappcRole.VIEWER, policyService, "read", resource.resourceName); // GH-90000

            HttpResponse response = runPromise(() -> // GH-90000
                    secured.serve(requestWithKey(HttpMethod.GET, "/api/v1/test", "viewer-key"))); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("viewer should READ on " + resource.resourceName) // GH-90000
                    .isEqualTo(200); // GH-90000
        }

        @ParameterizedTest
        @EnumSource(YappcResource.class) // GH-90000
        @DisplayName("viewer CANNOT WRITE on {} [GH-90000]")
        void viewerCannotWrite(YappcResource resource) { // GH-90000
            PolicyService policyService = buildPolicyService(resource.resourceName); // GH-90000
            AsyncServlet secured = buildSecuredChain("viewer-key", YappcRole.VIEWER, policyService, "write", resource.resourceName); // GH-90000

            HttpResponse response = runPromise(() -> // GH-90000
                    secured.serve(requestWithKey(HttpMethod.POST, "/api/v1/test", "viewer-key"))); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("viewer must NOT WRITE on " + resource.resourceName) // GH-90000
                    .isEqualTo(403); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unknown / unregistered roles — all access denied
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("unknown role — all access denied [GH-90000]")
    class UnknownRoleDenied {

        @ParameterizedTest
        @EnumSource(YappcResource.class) // GH-90000
        @DisplayName("key with unknown role gets 403 on READ for {} [GH-90000]")
        void unknownRoleDeniedRead(YappcResource resource) { // GH-90000
            PolicyService policyService = buildPolicyService(resource.resourceName); // GH-90000
            // Resolver maps "unknown-key" to a Principal with role "unknown" — no policies match
            ApiKeyResolver resolver = key ->
                    "unknown-key".equals(key) // GH-90000
                            ? Optional.of(new Principal("unknown-user", List.of("unknown [GH-90000]"), TENANT))
                            : Optional.empty(); // GH-90000
            ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(resolver); // GH-90000
            RBACFilter readFilter = new RBACFilter(policyService, "read", resource.resourceName); // GH-90000
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise(); // GH-90000
            AsyncServlet secured = authFilter.secure(readFilter.secure(delegate)); // GH-90000

            HttpResponse response = runPromise(() -> // GH-90000
                    secured.serve(requestWithKey(HttpMethod.GET, "/api/v1/test", "unknown-key"))); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("unknown role must be denied READ on " + resource.resourceName) // GH-90000
                    .isEqualTo(403); // GH-90000
        }

        @ParameterizedTest
        @EnumSource(YappcResource.class) // GH-90000
        @DisplayName("key with unknown role gets 403 on WRITE for {} [GH-90000]")
        void unknownRoleDeniedWrite(YappcResource resource) { // GH-90000
            PolicyService policyService = buildPolicyService(resource.resourceName); // GH-90000
            ApiKeyResolver resolver = key ->
                    "unknown-key".equals(key) // GH-90000
                            ? Optional.of(new Principal("unknown-user", List.of("unknown [GH-90000]"), TENANT))
                            : Optional.empty(); // GH-90000
            ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(resolver); // GH-90000
            RBACFilter writeFilter = new RBACFilter(policyService, "write", resource.resourceName); // GH-90000
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise(); // GH-90000
            AsyncServlet secured = authFilter.secure(writeFilter.secure(delegate)); // GH-90000

            HttpResponse response = runPromise(() -> // GH-90000
                    secured.serve(requestWithKey(HttpMethod.POST, "/api/v1/test", "unknown-key"))); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("unknown role must be denied WRITE on " + resource.resourceName) // GH-90000
                    .isEqualTo(403); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Missing / invalid API key — always 401 regardless of resource
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("missing or invalid API key — always 401 [GH-90000]")
    class MissingKeyDenied {

        @ParameterizedTest
        @EnumSource(YappcResource.class) // GH-90000
        @DisplayName("no API key header → 401 on {} read [GH-90000]")
        void noKeyReturns401OnRead(YappcResource resource) { // GH-90000
            PolicyService policyService = buildPolicyService(resource.resourceName); // GH-90000
            ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(Set.of("valid-key [GH-90000]"));
            RBACFilter readFilter = new RBACFilter(policyService, "read", resource.resourceName); // GH-90000
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise(); // GH-90000
            AsyncServlet secured = authFilter.secure(readFilter.secure(delegate)); // GH-90000

            HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/test").build(); // GH-90000
            HttpResponse response = runPromise(() -> secured.serve(request)); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("missing API key must yield 401 on " + resource.resourceName) // GH-90000
                    .isEqualTo(401); // GH-90000
        }

        @ParameterizedTest
        @EnumSource(YappcResource.class) // GH-90000
        @DisplayName("invalid API key → 401 on {} write [GH-90000]")
        void invalidKeyReturns401OnWrite(YappcResource resource) { // GH-90000
            PolicyService policyService = buildPolicyService(resource.resourceName); // GH-90000
            ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(Set.of("valid-key [GH-90000]"));
            RBACFilter writeFilter = new RBACFilter(policyService, "write", resource.resourceName); // GH-90000
            AsyncServlet delegate = req -> HttpResponse.ok200().toPromise(); // GH-90000
            AsyncServlet secured = authFilter.secure(writeFilter.secure(delegate)); // GH-90000

            HttpResponse response = runPromise(() -> // GH-90000
                    secured.serve(requestWithKey(HttpMethod.POST, "/api/v1/test", "bad-key"))); // GH-90000

            assertThat(response.getCode()) // GH-90000
                    .as("invalid API key must yield 401 on " + resource.resourceName) // GH-90000
                    .isEqualTo(401); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the standard four-role policy service for a given resource name.
     * Mirrors the policies in {@link com.ghatana.yappc.services.security.YappcApiSecurity}.
     */
    private static PolicyService buildPolicyService(String resource) { // GH-90000
        PolicyService service = new PolicyService(new InMemoryPolicyRepository()); // GH-90000
        service.createPolicy("admin-" + resource, "admin all", "admin", resource, Set.of("* [GH-90000]"));
        service.createPolicy("editor-" + resource, "editor rw", "editor", resource, Set.of("read", "write")); // GH-90000
        service.createPolicy("agent-" + resource, "agent rw", "agent", resource, Set.of("read", "write")); // GH-90000
        service.createPolicy("viewer-" + resource, "viewer r", "viewer", resource, Set.of("read [GH-90000]"));
        return service;
    }

    /**
     * Builds a two-filter chain (auth → RBAC) for the given key, role, and action. // GH-90000
     */
    private static AsyncServlet buildSecuredChain( // GH-90000
            String apiKey, YappcRole role, PolicyService policyService, String action, String resource) {
        ApiKeyResolver resolver = key ->
                apiKey.equals(key) // GH-90000
                        ? Optional.of(new Principal(role.roleName + "-user", List.of(role.roleName), TENANT)) // GH-90000
                        : Optional.empty(); // GH-90000
        ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(resolver); // GH-90000
        RBACFilter rbacFilter = new RBACFilter(policyService, action, resource); // GH-90000
        AsyncServlet delegate = req -> HttpResponse.ok200().toPromise(); // GH-90000
        return authFilter.secure(rbacFilter.secure(delegate)); // GH-90000
    }

    private static HttpRequest requestWithKey(HttpMethod method, String path, String apiKey) { // GH-90000
        return HttpRequest.builder(method, "http://localhost" + path) // GH-90000
                .withHeader(HttpHeaders.of("X-API-Key [GH-90000]"), apiKey)
                .build(); // GH-90000
    }
}

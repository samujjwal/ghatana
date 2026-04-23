package com.ghatana.platform.http.security.filter;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.security.rbac.InMemoryPolicyRepository;
import com.ghatana.platform.security.rbac.PolicyService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for RBACFilter HTTP servlet access control
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RBACFilter — role-based access control on HTTP endpoints")
class RBACFilterTest extends EventloopTestBase {

    private PolicyService policyService;
    private RBACFilter rbacFilter;

    private static final AsyncServlet OK_DELEGATE = req -> Promise.of(HttpResponse.ok200().build()); // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        InMemoryPolicyRepository repository = new InMemoryPolicyRepository(); // GH-90000
        policyService = new PolicyService(repository); // GH-90000
        // Grant ADMIN role permission "write" on resource "data"
        policyService.createPolicy("admin-write", "admin write data", "ADMIN", "data", Set.of("write"));
        // Grant USER role permission "read" on resource "data"
        policyService.createPolicy("user-read", "user read data", "USER", "data", Set.of("read"));

        rbacFilter = new RBACFilter(policyService, "write", "data"); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        TenantContext.clear(); // GH-90000
    }

    // ── No principal ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("secure() returns 401 when no principal in request or TenantContext")
    void returns401WhenNoPrincipal() { // GH-90000
        AsyncServlet secured = rbacFilter.secure(OK_DELEGATE); // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/data").build();

        HttpResponse response = runPromise(() -> secured.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
    }

    @Test
    @DisplayName("401 response body contains UNAUTHORIZED code")
    void unauthorizedBodyContainsCode() { // GH-90000
        AsyncServlet secured = rbacFilter.secure(OK_DELEGATE); // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/data").build();

        HttpResponse response = runPromise(() -> secured.serve(request)); // GH-90000

        String body = response.getBody() != null ? new String(response.getBody().asArray()) : ""; // GH-90000
        assertThat(body).contains("UNAUTHORIZED");
    }

    // ── Principal present but lacks permission ────────────────────────────────

    @Test
    @DisplayName("secure() returns 403 when principal exists but lacks permission")
    void returns403WhenPrincipalLacksPermission() { // GH-90000
        Principal viewer = new Principal("viewer", List.of("USER"), "tenant-1");
        AsyncServlet secured = rbacFilter.secure(OK_DELEGATE); // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/data").build();

        HttpResponse response = runPromise(() -> { // GH-90000
            try (TenantContext.Scope scope = TenantContext.scope(viewer)) { // GH-90000
                return secured.serve(request); // GH-90000
            }
        });

        assertThat(response.getCode()).isEqualTo(403); // GH-90000
    }

    @Test
    @DisplayName("403 response body contains FORBIDDEN code")
    void forbiddenBodyContainsCode() { // GH-90000
        Principal viewer = new Principal("viewer", List.of("USER"), "tenant-1");
        AsyncServlet secured = rbacFilter.secure(OK_DELEGATE); // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/data").build();

        HttpResponse response = runPromise(() -> { // GH-90000
            try (TenantContext.Scope scope = TenantContext.scope(viewer)) { // GH-90000
                return secured.serve(request); // GH-90000
            }
        });

        String body = response.getBody() != null ? new String(response.getBody().asArray()) : ""; // GH-90000
        assertThat(body).contains("FORBIDDEN");
    }

    // ── Principal with required permission ────────────────────────────────────

    @Test
    @DisplayName("secure() delegates to inner servlet when principal has required permission")
    void delegatesWhenPrincipalHasPermission() { // GH-90000
        Principal admin = new Principal("admin-user", List.of("ADMIN"), "tenant-1");
        AsyncServlet secured = rbacFilter.secure(OK_DELEGATE); // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/data").build();

        HttpResponse response = runPromise(() -> { // GH-90000
            try (TenantContext.Scope scope = TenantContext.scope(admin)) { // GH-90000
                return secured.serve(request); // GH-90000
            }
        });

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    // ── Request attachment ────────────────────────────────────────────────────

    @Test
    @DisplayName("secure() checks request attachment before TenantContext")
    void checksRequestAttachmentFirst() { // GH-90000
        Principal admin = new Principal("attached-admin", List.of("ADMIN"), "tenant-1");
        // Do NOT set TenantContext — principal is only in request attachment
        AsyncServlet secured = rbacFilter.secure(OK_DELEGATE); // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/data").build();
        request.attach(Principal.class, admin); // GH-90000

        HttpResponse response = runPromise(() -> secured.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("secure() returns different AsyncServlet wrapping each call")
    void secureReturnsNewServletEachCall() { // GH-90000
        AsyncServlet s1 = rbacFilter.secure(OK_DELEGATE); // GH-90000
        AsyncServlet s2 = rbacFilter.secure(OK_DELEGATE); // GH-90000
        assertThat(s1).isNotSameAs(s2); // GH-90000
    }

    // ── Null / empty resource ─────────────────────────────────────────────────

    @Test
    @DisplayName("filter with null resource is handled gracefully — no principal returns 401")
    void nullResourceHandledGracefully() { // GH-90000
        RBACFilter nullResourceFilter = new RBACFilter(policyService, "read", null); // GH-90000
        AsyncServlet secured = nullResourceFilter.secure(OK_DELEGATE); // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/any").build();

        HttpResponse response = runPromise(() -> secured.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
    }
}

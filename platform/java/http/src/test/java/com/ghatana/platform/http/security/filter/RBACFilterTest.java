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

    private static final AsyncServlet OK_DELEGATE = req -> Promise.of(HttpResponse.ok200().build()); 

    @BeforeEach
    void setUp() { 
        InMemoryPolicyRepository repository = new InMemoryPolicyRepository(); 
        policyService = new PolicyService(repository); 
        // Grant ADMIN role permission "write" on resource "data"
        policyService.createPolicy("admin-write", "admin write data", "ADMIN", "data", Set.of("write"));
        // Grant USER role permission "read" on resource "data"
        policyService.createPolicy("user-read", "user read data", "USER", "data", Set.of("read"));

        rbacFilter = new RBACFilter(policyService, "write", "data"); 
    }

    @AfterEach
    void tearDown() { 
        TenantContext.clear(); 
    }

    // ── No principal ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("secure() returns 401 when no principal in request or TenantContext")
    void returns401WhenNoPrincipal() { 
        AsyncServlet secured = rbacFilter.secure(OK_DELEGATE); 
        HttpRequest request = HttpRequest.get("http://localhost/data").build();

        HttpResponse response = runPromise(() -> secured.serve(request)); 

        assertThat(response.getCode()).isEqualTo(401); 
    }

    @Test
    @DisplayName("401 response body contains UNAUTHORIZED code")
    void unauthorizedBodyContainsCode() { 
        AsyncServlet secured = rbacFilter.secure(OK_DELEGATE); 
        HttpRequest request = HttpRequest.get("http://localhost/data").build();

        HttpResponse response = runPromise(() -> secured.serve(request)); 

        String body = response.getBody() != null ? new String(response.getBody().asArray()) : ""; 
        assertThat(body).contains("UNAUTHORIZED");
    }

    // ── Principal present but lacks permission ────────────────────────────────

    @Test
    @DisplayName("secure() returns 403 when principal exists but lacks permission")
    void returns403WhenPrincipalLacksPermission() { 
        Principal viewer = new Principal("viewer", List.of("USER"), "tenant-1");
        AsyncServlet secured = rbacFilter.secure(OK_DELEGATE); 
        HttpRequest request = HttpRequest.get("http://localhost/data").build();

        HttpResponse response = runPromise(() -> { 
            try (TenantContext.Scope scope = TenantContext.scope(viewer)) { 
                return secured.serve(request); 
            }
        });

        assertThat(response.getCode()).isEqualTo(403); 
    }

    @Test
    @DisplayName("403 response body contains FORBIDDEN code")
    void forbiddenBodyContainsCode() { 
        Principal viewer = new Principal("viewer", List.of("USER"), "tenant-1");
        AsyncServlet secured = rbacFilter.secure(OK_DELEGATE); 
        HttpRequest request = HttpRequest.get("http://localhost/data").build();

        HttpResponse response = runPromise(() -> { 
            try (TenantContext.Scope scope = TenantContext.scope(viewer)) { 
                return secured.serve(request); 
            }
        });

        String body = response.getBody() != null ? new String(response.getBody().asArray()) : ""; 
        assertThat(body).contains("FORBIDDEN");
    }

    // ── Principal with required permission ────────────────────────────────────

    @Test
    @DisplayName("secure() delegates to inner servlet when principal has required permission")
    void delegatesWhenPrincipalHasPermission() { 
        Principal admin = new Principal("admin-user", List.of("ADMIN"), "tenant-1");
        AsyncServlet secured = rbacFilter.secure(OK_DELEGATE); 
        HttpRequest request = HttpRequest.get("http://localhost/data").build();

        HttpResponse response = runPromise(() -> { 
            try (TenantContext.Scope scope = TenantContext.scope(admin)) { 
                return secured.serve(request); 
            }
        });

        assertThat(response.getCode()).isEqualTo(200); 
    }

    // ── Request attachment ────────────────────────────────────────────────────

    @Test
    @DisplayName("secure() checks request attachment before TenantContext")
    void checksRequestAttachmentFirst() { 
        Principal admin = new Principal("attached-admin", List.of("ADMIN"), "tenant-1");
        // Do NOT set TenantContext — principal is only in request attachment
        AsyncServlet secured = rbacFilter.secure(OK_DELEGATE); 
        HttpRequest request = HttpRequest.get("http://localhost/data").build();
        request.attach(Principal.class, admin); 

        HttpResponse response = runPromise(() -> secured.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("secure() returns different AsyncServlet wrapping each call")
    void secureReturnsNewServletEachCall() { 
        AsyncServlet s1 = rbacFilter.secure(OK_DELEGATE); 
        AsyncServlet s2 = rbacFilter.secure(OK_DELEGATE); 
        assertThat(s1).isNotSameAs(s2); 
    }

    // ── Null / empty resource ─────────────────────────────────────────────────

    @Test
    @DisplayName("filter with null resource is handled gracefully — no principal returns 401")
    void nullResourceHandledGracefully() { 
        RBACFilter nullResourceFilter = new RBACFilter(policyService, "read", null); 
        AsyncServlet secured = nullResourceFilter.secure(OK_DELEGATE); 
        HttpRequest request = HttpRequest.get("http://localhost/any").build();

        HttpResponse response = runPromise(() -> secured.serve(request)); 

        assertThat(response.getCode()).isEqualTo(401); 
    }
}

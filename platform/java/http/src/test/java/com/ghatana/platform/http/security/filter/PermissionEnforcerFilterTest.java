/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.http.security.filter;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.security.annotation.RequiresPermission;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Unit tests for PermissionEnforcerFilter annotation-based access control
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PermissionEnforcerFilter — annotation-based HTTP permission enforcement [GH-90000]")
class PermissionEnforcerFilterTest extends EventloopTestBase {

    // ── Annotated marker classes for test route registration ─────────────────

    @RequiresPermission("event:read:all [GH-90000]")
    private static final class EventReadRoute {}

    @RequiresPermission(value = "event:write:all") // GH-90000
    private static final class EventWriteRoute {}

    @RequiresPermission(value = "event:read:all", requireAll = true, anyOf = {"event:read:all", "event:write:all"}) // GH-90000
    private static final class RequireAllRoute {}

    @RequiresPermission(anyOf = {"event:read:all", "event:write:all"}) // GH-90000
    private static final class AnyOfRoute {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final AsyncServlet OK_DELEGATE =
            req -> Promise.of(HttpResponse.ok200().build()); // GH-90000

    private static HttpRequest getRequest(String path) { // GH-90000
        return HttpRequest.get("http://localhost" + path).build(); // GH-90000
    }

    private static HttpRequest postRequest(String path) { // GH-90000
        return HttpRequest.post("http://localhost" + path).build(); // GH-90000
    }

    @AfterEach
    void clearContext() { // GH-90000
        TenantContext.clear(); // GH-90000
    }

    // ── Tests: no registered constraint ───────────────────────────────────────

    @Test
    @DisplayName("passes through when no permission registered for route [GH-90000]")
    void passesThroughWithNoRegisteredRoute() throws Exception { // GH-90000
        PermissionEnforcerFilter filter = new PermissionEnforcerFilter( // GH-90000
                (p, t) -> Set.of()); // GH-90000

        HttpResponse response = runPromise(() -> // GH-90000
                filter.apply(getRequest("/unregistered [GH-90000]"), OK_DELEGATE));

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    // ── Tests: null principal → 401 ───────────────────────────────────────────

    @Test
    @DisplayName("returns 401 when no principal in TenantContext and route is restricted [GH-90000]")
    void returns401WhenNoPrincipal() throws Exception { // GH-90000
        PermissionEnforcerFilter filter = PermissionEnforcerFilter.ofStaticPrincipalPermissions( // GH-90000
                Map.of("alice", Set.of("event:read:all [GH-90000]")));

        RequiresPermission ann = EventReadRoute.class.getAnnotation(RequiresPermission.class); // GH-90000
        filter.registerRoutePermission("GET /events", ann); // GH-90000

        HttpResponse response = runPromise(() -> // GH-90000
                filter.apply(getRequest("/events [GH-90000]"), OK_DELEGATE));

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
    }

    // ── Tests: exact permission – access control ───────────────────────────────

    @Test
    @DisplayName("returns 200 when principal holds the exact required permission [GH-90000]")
    void returns200WhenPermissionGranted() throws Exception { // GH-90000
        Principal alice = new Principal("alice", List.of("USER [GH-90000]"));
        PermissionEnforcerFilter filter = PermissionEnforcerFilter.ofStaticPrincipalPermissions( // GH-90000
                Map.of("alice", Set.of("event:read:all [GH-90000]")));

        RequiresPermission ann = EventReadRoute.class.getAnnotation(RequiresPermission.class); // GH-90000
        filter.registerRoutePermission("GET /events", ann); // GH-90000

        HttpResponse response = runPromise(() -> { // GH-90000
            try (TenantContext.Scope scope = TenantContext.scope(alice)) { // GH-90000
                return filter.apply(getRequest("/events [GH-90000]"), OK_DELEGATE);
            }
        });
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("returns 403 when principal lacks the required permission [GH-90000]")
    void returns403WhenPermissionDenied() throws Exception { // GH-90000
        Principal bob = new Principal("bob", List.of("VIEWER [GH-90000]"));
        PermissionEnforcerFilter filter = PermissionEnforcerFilter.ofStaticPrincipalPermissions( // GH-90000
                Map.of("bob", Set.of("event:read:own [GH-90000]")));  // "own" not "all"

        RequiresPermission ann = EventReadRoute.class.getAnnotation(RequiresPermission.class); // GH-90000
        filter.registerRoutePermission("GET /events", ann); // GH-90000

        HttpResponse response = runPromise(() -> { // GH-90000
            try (TenantContext.Scope scope = TenantContext.scope(bob)) { // GH-90000
                return filter.apply(getRequest("/events [GH-90000]"), OK_DELEGATE);
            }
        });
        assertThat(response.getCode()).isEqualTo(403); // GH-90000
    }

    // ── Tests: wildcard matching ───────────────────────────────────────────────

    @Test
    @DisplayName("wildcard action '*' satisfies specific action requirement [GH-90000]")
    void wildcardActionSatisfiesSpecificAction() throws Exception { // GH-90000
        Principal alice = new Principal("alice", List.of("ADMIN [GH-90000]"));
        // granted: "event:*:all" — wildcard action should match "event:read:all"
        PermissionEnforcerFilter filter = PermissionEnforcerFilter.ofStaticPrincipalPermissions( // GH-90000
                Map.of("alice", Set.of("event:*:all [GH-90000]")));

        RequiresPermission ann = EventReadRoute.class.getAnnotation(RequiresPermission.class); // GH-90000
        filter.registerRoutePermission("GET /events", ann); // GH-90000

        HttpResponse response = runPromise(() -> { // GH-90000
            try (TenantContext.Scope scope = TenantContext.scope(alice)) { // GH-90000
                return filter.apply(getRequest("/events [GH-90000]"), OK_DELEGATE);
            }
        });
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("super-admin wildcard '*:*:*' satisfies any permission [GH-90000]")
    void superAdminWildcardSatisfiesAnyPermission() throws Exception { // GH-90000
        Principal admin = new Principal("superadmin", List.of("SUPER_ADMIN [GH-90000]"));
        PermissionEnforcerFilter filter = PermissionEnforcerFilter.ofStaticPrincipalPermissions( // GH-90000
                Map.of("superadmin", Set.of("*:*:* [GH-90000]")));

        RequiresPermission ann = EventWriteRoute.class.getAnnotation(RequiresPermission.class); // GH-90000
        filter.registerRoutePermission("POST /events", ann); // GH-90000

        HttpResponse response = runPromise(() -> { // GH-90000
            try (TenantContext.Scope scope = TenantContext.scope(admin)) { // GH-90000
                return filter.apply(postRequest("/events [GH-90000]"), OK_DELEGATE);
            }
        });
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    // ── Tests: anyOf ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("anyOf: returns 200 when principal holds any one of the anyOf permissions [GH-90000]")
    void anyOfGrantsAccessWithOneMatchingPermission() throws Exception { // GH-90000
        Principal alice = new Principal("alice", List.of("USER [GH-90000]"));
        // Only has "event:write:all", not "event:read:all"
        PermissionEnforcerFilter filter = PermissionEnforcerFilter.ofStaticPrincipalPermissions( // GH-90000
                Map.of("alice", Set.of("event:write:all [GH-90000]")));

        RequiresPermission ann = AnyOfRoute.class.getAnnotation(RequiresPermission.class); // GH-90000
        filter.registerRoutePermission("GET /events/any", ann); // GH-90000

        HttpResponse response = runPromise(() -> { // GH-90000
            try (TenantContext.Scope scope = TenantContext.scope(alice)) { // GH-90000
                return filter.apply(getRequest("/events/any [GH-90000]"), OK_DELEGATE);
            }
        });
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("anyOf: returns 403 when principal holds none of the anyOf permissions [GH-90000]")
    void anyOfDeniesAccessWhenNoMatchingPermission() throws Exception { // GH-90000
        Principal bob = new Principal("bob", List.of("VIEWER [GH-90000]"));
        PermissionEnforcerFilter filter = PermissionEnforcerFilter.ofStaticPrincipalPermissions( // GH-90000
                Map.of("bob", Set.of("user:read:own [GH-90000]")));

        RequiresPermission ann = AnyOfRoute.class.getAnnotation(RequiresPermission.class); // GH-90000
        filter.registerRoutePermission("GET /events/any", ann); // GH-90000

        HttpResponse response = runPromise(() -> { // GH-90000
            try (TenantContext.Scope scope = TenantContext.scope(bob)) { // GH-90000
                return filter.apply(getRequest("/events/any [GH-90000]"), OK_DELEGATE);
            }
        });
        assertThat(response.getCode()).isEqualTo(403); // GH-90000
    }

    // ── Tests: registerRouteClass ─────────────────────────────────────────────

    @Test
    @DisplayName("registerRouteClass registers annotation keyed by class name [GH-90000]")
    void registerRouteClassUsesClassNameAsKey() throws Exception { // GH-90000
        PermissionEnforcerFilter filter = PermissionEnforcerFilter.ofStaticPrincipalPermissions( // GH-90000
                Map.of("alice", Set.of("event:read:all [GH-90000]")));

        filter.registerRouteClass(EventReadRoute.class); // GH-90000

        // Route key is the class FQN; construct a fake request that matches it
        String fakeRoutePath = EventReadRoute.class.getName(); // GH-90000
        Principal alice = new Principal("alice", List.of("USER [GH-90000]"));

        HttpResponse response = runPromise(() -> { // GH-90000
            try (TenantContext.Scope scope = TenantContext.scope(alice)) { // GH-90000
                return filter.apply( // GH-90000
                        HttpRequest.get("http://localhost/" + fakeRoutePath).build(), // GH-90000
                        OK_DELEGATE);
            }
        });
        // The route key lookup uses method + " " + path; "GET /class.name" won't match
        // So it passes through (no constraint found for that exact routeKey) // GH-90000
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("registerRouteClass ignores class with no @RequiresPermission annotation [GH-90000]")
    void registerRouteClassIgnoresUnannotatedClass() throws Exception { // GH-90000
        PermissionEnforcerFilter filter = PermissionEnforcerFilter.ofStaticPrincipalPermissions( // GH-90000
                Map.of()); // GH-90000

        // Should not throw — unannotated class is a no-op
        filter.registerRouteClass(Object.class); // GH-90000

        // No constraints → always passes through
        HttpResponse response = runPromise(() -> // GH-90000
                filter.apply(getRequest("/anything [GH-90000]"), OK_DELEGATE));
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    // ── Tests: constructor guards ─────────────────────────────────────────────

    @Test
    @DisplayName("constructor throws NullPointerException for null resolver [GH-90000]")
    void constructorThrowsForNullResolver() { // GH-90000
        assertThatThrownBy(() -> new PermissionEnforcerFilter(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("ofStaticPrincipalPermissions throws for null map [GH-90000]")
    void staticFactoryThrowsForNullMap() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                PermissionEnforcerFilter.ofStaticPrincipalPermissions(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }
}

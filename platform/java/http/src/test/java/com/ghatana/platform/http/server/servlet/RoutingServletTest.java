package com.ghatana.platform.http.server.servlet;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for RoutingServlet HTTP path dispatching and route handling
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RoutingServlet — HTTP route registration, dispatch, and 404 handling")
class RoutingServletTest extends EventloopTestBase {

    private RoutingServlet servlet;

    @BeforeEach
    void setUp() { 
        servlet = new RoutingServlet(); 
    }

    // ── Route registration ────────────────────────────────────────────────────

    @Test
    @DisplayName("new servlet has zero routes")
    void newServletHasZeroRoutes() { 
        assertThat(servlet.getRouteCount()).isEqualTo(0); 
    }

    @Test
    @DisplayName("addRoute() increments route count")
    void addRouteIncrementsCount() { 
        servlet.addRoute(HttpMethod.GET, "/ping", req -> HttpResponse.ok200().build()); 
        assertThat(servlet.getRouteCount()).isEqualTo(1); 
    }

    @Test
    @DisplayName("addAsyncRoute() increments route count")
    void addAsyncRouteIncrementsCount() { 
        servlet.addAsyncRoute(HttpMethod.POST, "/items", 
                req -> Promise.of(HttpResponse.ofCode(201).build())); 
        assertThat(servlet.getRouteCount()).isEqualTo(1); 
    }

    // ── Exact-match routing ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /ping returns 200 from registered sync route")
    void exactGetMatchReturns200() { 
        servlet.addRoute(HttpMethod.GET, "/ping", req -> HttpResponse.ok200().build()); 

        HttpRequest request = HttpRequest.get("http://localhost/ping").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
    }

    @Test
    @DisplayName("POST route returns 201 from registered async route")
    void asyncPostRouteReturns201() { 
        servlet.addAsyncRoute(HttpMethod.POST, "/items", 
                req -> Promise.of(HttpResponse.ofCode(201).build())); 

        HttpRequest request = HttpRequest.post("http://localhost/items").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(201); 
    }

    // ── 404 for unregistered routes ───────────────────────────────────────────

    @Test
    @DisplayName("unregistered path returns 404")
    void unregisteredPathReturns404() { 
        HttpRequest request = HttpRequest.get("http://localhost/missing").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(404); 
    }

    @Test
    @DisplayName("registered GET route returns 404 for DELETE on same path")
    void getRouteDoesNotMatchDelete() { 
        servlet.addRoute(HttpMethod.GET, "/resource", req -> HttpResponse.ok200().build()); 

        HttpRequest request = HttpRequest.post("http://localhost/resource").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(404); 
    }

    // ── Path parameter patterns ───────────────────────────────────────────────

    @Test
    @DisplayName("path pattern /users/:id matches /users/42")
    void pathParameterRouteMatchesConcreteValue() { 
        servlet.addRoute(HttpMethod.GET, "/users/:id", 
                req -> HttpResponse.ok200().build()); 

        HttpRequest request = HttpRequest.get("http://localhost/users/42").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
    }

    @Test
    @DisplayName("pattern route does not match missing segment")
    void patternRouteDoesNotMatchMissingSegment() { 
        servlet.addRoute(HttpMethod.GET, "/users/:id", 
                req -> HttpResponse.ok200().build()); 

        // /users without ID segment should not match /users/:id
        HttpRequest request = HttpRequest.get("http://localhost/users").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(404); 
    }

    // ── Route merging ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("merge() combines routes from another servlet")
    void mergeCombinesRoutes() { 
        RoutingServlet other = new RoutingServlet(); 
        other.addRoute(HttpMethod.GET, "/a", req -> HttpResponse.ok200().build()); 
        other.addRoute(HttpMethod.GET, "/b", req -> HttpResponse.ok200().build()); 

        servlet.addRoute(HttpMethod.GET, "/c", req -> HttpResponse.ok200().build()); 
        servlet.merge(other); 

        assertThat(servlet.getRouteCount()).isEqualTo(3); 
    }

    @Test
    @DisplayName("merge(null) is a no-op and returns the same servlet")
    void mergeNullIsNoOp() { 
        servlet.addRoute(HttpMethod.GET, "/x", req -> HttpResponse.ok200().build()); 
        RoutingServlet returned = servlet.merge(null); 

        assertThat(returned).isSameAs(servlet); 
        assertThat(servlet.getRouteCount()).isEqualTo(1); 
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clear() removes all registered routes")
    void clearRemovesAllRoutes() { 
        servlet.addRoute(HttpMethod.GET, "/a", req -> HttpResponse.ok200().build()); 
        servlet.addRoute(HttpMethod.POST, "/b", req -> HttpResponse.ok200().build()); 
        servlet.clear(); 

        assertThat(servlet.getRouteCount()).isEqualTo(0); 
    }
}

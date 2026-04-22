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
@DisplayName("RoutingServlet — HTTP route registration, dispatch, and 404 handling [GH-90000]")
class RoutingServletTest extends EventloopTestBase {

    private RoutingServlet servlet;

    @BeforeEach
    void setUp() { // GH-90000
        servlet = new RoutingServlet(); // GH-90000
    }

    // ── Route registration ────────────────────────────────────────────────────

    @Test
    @DisplayName("new servlet has zero routes [GH-90000]")
    void newServletHasZeroRoutes() { // GH-90000
        assertThat(servlet.getRouteCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("addRoute() increments route count [GH-90000]")
    void addRouteIncrementsCount() { // GH-90000
        servlet.addRoute(HttpMethod.GET, "/ping", req -> HttpResponse.ok200().build()); // GH-90000
        assertThat(servlet.getRouteCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("addAsyncRoute() increments route count [GH-90000]")
    void addAsyncRouteIncrementsCount() { // GH-90000
        servlet.addAsyncRoute(HttpMethod.POST, "/items", // GH-90000
                req -> Promise.of(HttpResponse.ofCode(201).build())); // GH-90000
        assertThat(servlet.getRouteCount()).isEqualTo(1); // GH-90000
    }

    // ── Exact-match routing ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /ping returns 200 from registered sync route [GH-90000]")
    void exactGetMatchReturns200() { // GH-90000
        servlet.addRoute(HttpMethod.GET, "/ping", req -> HttpResponse.ok200().build()); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/ping [GH-90000]").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("POST route returns 201 from registered async route [GH-90000]")
    void asyncPostRouteReturns201() { // GH-90000
        servlet.addAsyncRoute(HttpMethod.POST, "/items", // GH-90000
                req -> Promise.of(HttpResponse.ofCode(201).build())); // GH-90000

        HttpRequest request = HttpRequest.post("http://localhost/items [GH-90000]").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(201); // GH-90000
    }

    // ── 404 for unregistered routes ───────────────────────────────────────────

    @Test
    @DisplayName("unregistered path returns 404 [GH-90000]")
    void unregisteredPathReturns404() { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/missing [GH-90000]").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(404); // GH-90000
    }

    @Test
    @DisplayName("registered GET route returns 404 for DELETE on same path [GH-90000]")
    void getRouteDoesNotMatchDelete() { // GH-90000
        servlet.addRoute(HttpMethod.GET, "/resource", req -> HttpResponse.ok200().build()); // GH-90000

        HttpRequest request = HttpRequest.post("http://localhost/resource [GH-90000]").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(404); // GH-90000
    }

    // ── Path parameter patterns ───────────────────────────────────────────────

    @Test
    @DisplayName("path pattern /users/:id matches /users/42 [GH-90000]")
    void pathParameterRouteMatchesConcreteValue() { // GH-90000
        servlet.addRoute(HttpMethod.GET, "/users/:id", // GH-90000
                req -> HttpResponse.ok200().build()); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/users/42 [GH-90000]").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("pattern route does not match missing segment [GH-90000]")
    void patternRouteDoesNotMatchMissingSegment() { // GH-90000
        servlet.addRoute(HttpMethod.GET, "/users/:id", // GH-90000
                req -> HttpResponse.ok200().build()); // GH-90000

        // /users without ID segment should not match /users/:id
        HttpRequest request = HttpRequest.get("http://localhost/users [GH-90000]").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(404); // GH-90000
    }

    // ── Route merging ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("merge() combines routes from another servlet [GH-90000]")
    void mergeCombinesRoutes() { // GH-90000
        RoutingServlet other = new RoutingServlet(); // GH-90000
        other.addRoute(HttpMethod.GET, "/a", req -> HttpResponse.ok200().build()); // GH-90000
        other.addRoute(HttpMethod.GET, "/b", req -> HttpResponse.ok200().build()); // GH-90000

        servlet.addRoute(HttpMethod.GET, "/c", req -> HttpResponse.ok200().build()); // GH-90000
        servlet.merge(other); // GH-90000

        assertThat(servlet.getRouteCount()).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("merge(null) is a no-op and returns the same servlet [GH-90000]")
    void mergeNullIsNoOp() { // GH-90000
        servlet.addRoute(HttpMethod.GET, "/x", req -> HttpResponse.ok200().build()); // GH-90000
        RoutingServlet returned = servlet.merge(null); // GH-90000

        assertThat(returned).isSameAs(servlet); // GH-90000
        assertThat(servlet.getRouteCount()).isEqualTo(1); // GH-90000
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clear() removes all registered routes [GH-90000]")
    void clearRemovesAllRoutes() { // GH-90000
        servlet.addRoute(HttpMethod.GET, "/a", req -> HttpResponse.ok200().build()); // GH-90000
        servlet.addRoute(HttpMethod.POST, "/b", req -> HttpResponse.ok200().build()); // GH-90000
        servlet.clear(); // GH-90000

        assertThat(servlet.getRouteCount()).isEqualTo(0); // GH-90000
    }
}

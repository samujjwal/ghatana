package com.ghatana.platform.http.server.filter;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for FilterChain filter composition and execution order
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("FilterChain — filter composition and chain-of-responsibility execution [GH-90000]")
class FilterChainTest extends EventloopTestBase {

    private static AsyncServlet okServlet() { // GH-90000
        return request -> Promise.of(HttpResponse.ok200().build()); // GH-90000
    }

    private static HttpRequest getRequest() { // GH-90000
        return HttpRequest.get("http://localhost/test [GH-90000]").build();
    }

    // ── Basic construction ────────────────────────────────────────────────────

    @Test
    @DisplayName("create() returns empty chain with zero filters [GH-90000]")
    void createReturnsEmptyChain() { // GH-90000
        FilterChain chain = FilterChain.create(); // GH-90000
        assertThat(chain.getFilterCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("addFilter() increments filter count [GH-90000]")
    void addFilterIncrementsCount() { // GH-90000
        FilterChain.Filter noop = (req, next) -> next.serve(req); // GH-90000
        FilterChain chain = FilterChain.create() // GH-90000
                .addFilter(noop) // GH-90000
                .addFilter(noop); // GH-90000

        assertThat(chain.getFilterCount()).isEqualTo(2); // GH-90000
    }

    // ── Pass-through behavior ─────────────────────────────────────────────────

    @Test
    @DisplayName("build() with no filters passes request directly to base servlet [GH-90000]")
    void emptyChainPassesRequestToBaseServlet() { // GH-90000
        AsyncServlet servlet = FilterChain.create().build(okServlet()); // GH-90000
        HttpResponse response = runPromise(() -> servlet.serve(getRequest())); // GH-90000
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("single pass-through filter delegates to base servlet [GH-90000]")
    void singlePassThroughFilterDelegates() { // GH-90000
        FilterChain.Filter passThrough = (req, next) -> next.serve(req); // GH-90000
        AsyncServlet servlet = FilterChain.create().addFilter(passThrough).build(okServlet()); // GH-90000

        HttpResponse response = runPromise(() -> servlet.serve(getRequest())); // GH-90000
        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    // ── Short-circuit behavior ────────────────────────────────────────────────

    @Test
    @DisplayName("filter can short-circuit and return response without calling next [GH-90000]")
    void filterCanShortCircuit() { // GH-90000
        FilterChain.Filter blockingFilter = (req, next) -> // GH-90000
                Promise.of(HttpResponse.ofCode(401).build()); // GH-90000

        AtomicInteger baseServletCalls = new AtomicInteger(0); // GH-90000
        AsyncServlet countingServlet = request -> {
            baseServletCalls.incrementAndGet(); // GH-90000
            return Promise.of(HttpResponse.ok200().build()); // GH-90000
        };

        AsyncServlet servlet = FilterChain.create().addFilter(blockingFilter).build(countingServlet); // GH-90000
        HttpResponse response = runPromise(() -> servlet.serve(getRequest())); // GH-90000

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
        assertThat(baseServletCalls.get()).isEqualTo(0); // GH-90000
    }

    // ── Execution order ───────────────────────────────────────────────────────

    @Test
    @DisplayName("filters execute in registration order (first-registered first) [GH-90000]")
    void filtersExecuteInRegistrationOrder() { // GH-90000
        StringBuilder order = new StringBuilder(); // GH-90000

        FilterChain.Filter first = (req, next) -> { // GH-90000
            order.append("1 [GH-90000]");
            return next.serve(req).map(r -> { // GH-90000
                order.append("1' [GH-90000]");
                return r;
            });
        };

        FilterChain.Filter second = (req, next) -> { // GH-90000
            order.append("2 [GH-90000]");
            return next.serve(req).map(r -> { // GH-90000
                order.append("2' [GH-90000]");
                return r;
            });
        };

        AsyncServlet servlet = FilterChain.create() // GH-90000
                .addFilter(first) // GH-90000
                .addFilter(second) // GH-90000
                .build(okServlet()); // GH-90000

        runPromise(() -> servlet.serve(getRequest())); // GH-90000

        // Request: first → second → servlet; Response: 2' → 1'
        assertThat(order.toString()).isEqualTo("122'1' [GH-90000]");
    }

    // ── Header injection via filter ───────────────────────────────────────────

    @Test
    @DisplayName("filter can inject response header after base servlet responds [GH-90000]")
    void filterCanInjectResponseHeader() { // GH-90000
        FilterChain.Filter headerFilter = (req, next) -> // GH-90000
                next.serve(req).map(resp -> HttpResponse.ofCode(resp.getCode()) // GH-90000
                        .withHeader(io.activej.http.HttpHeaders.of("X-Filter [GH-90000]"), "applied")
                        .build()); // GH-90000

        AsyncServlet servlet = FilterChain.create().addFilter(headerFilter).build(okServlet()); // GH-90000
        HttpResponse response = runPromise(() -> servlet.serve(getRequest())); // GH-90000

        assertThat(response.getHeader(io.activej.http.HttpHeaders.of("X-Filter [GH-90000]"))).isEqualTo("applied [GH-90000]");
    }

    // ── Multiple filters that short-circuit at different points ───────────────

    @Test
    @DisplayName("second filter short-circuits without reaching base servlet [GH-90000]")
    void secondFilterShortCircuitsChain() { // GH-90000
        AtomicInteger baseServletCalls = new AtomicInteger(0); // GH-90000

        FilterChain.Filter passThrough = (req, next) -> next.serve(req); // GH-90000
        FilterChain.Filter blocker = (req, next) -> // GH-90000
                Promise.of(HttpResponse.ofCode(403).build()); // GH-90000

        AsyncServlet countingServlet = request -> {
            baseServletCalls.incrementAndGet(); // GH-90000
            return Promise.of(HttpResponse.ok200().build()); // GH-90000
        };

        AsyncServlet servlet = FilterChain.create() // GH-90000
                .addFilter(passThrough) // GH-90000
                .addFilter(blocker) // GH-90000
                .build(countingServlet); // GH-90000

        HttpResponse response = runPromise(() -> servlet.serve(getRequest())); // GH-90000

        assertThat(response.getCode()).isEqualTo(403); // GH-90000
        assertThat(baseServletCalls.get()).isEqualTo(0); // GH-90000
    }

    // ── Additive chain behavior ───────────────────────────────────────────────

    @Test
    @DisplayName("addFilter() returns same FilterChain instance for fluent chaining [GH-90000]")
    void addFilterReturnsSameInstance() { // GH-90000
        FilterChain chain = FilterChain.create(); // GH-90000
        FilterChain.Filter noop = (req, next) -> next.serve(req); // GH-90000

        FilterChain returned = chain.addFilter(noop); // GH-90000
        assertThat(returned).isSameAs(chain); // GH-90000
    }
}

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
@DisplayName("FilterChain — filter composition and chain-of-responsibility execution")
class FilterChainTest extends EventloopTestBase {

    private static AsyncServlet okServlet() {
        return request -> Promise.of(HttpResponse.ok200().build());
    }

    private static HttpRequest getRequest() {
        return HttpRequest.get("http://localhost/test").build();
    }

    // ── Basic construction ────────────────────────────────────────────────────

    @Test
    @DisplayName("create() returns empty chain with zero filters")
    void createReturnsEmptyChain() {
        FilterChain chain = FilterChain.create();
        assertThat(chain.getFilterCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("addFilter() increments filter count")
    void addFilterIncrementsCount() {
        FilterChain.Filter noop = (req, next) -> next.serve(req);
        FilterChain chain = FilterChain.create()
                .addFilter(noop)
                .addFilter(noop);

        assertThat(chain.getFilterCount()).isEqualTo(2);
    }

    // ── Pass-through behavior ─────────────────────────────────────────────────

    @Test
    @DisplayName("build() with no filters passes request directly to base servlet")
    void emptyChainPassesRequestToBaseServlet() {
        AsyncServlet servlet = FilterChain.create().build(okServlet());
        HttpResponse response = runPromise(() -> servlet.serve(getRequest()));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("single pass-through filter delegates to base servlet")
    void singlePassThroughFilterDelegates() {
        FilterChain.Filter passThrough = (req, next) -> next.serve(req);
        AsyncServlet servlet = FilterChain.create().addFilter(passThrough).build(okServlet());

        HttpResponse response = runPromise(() -> servlet.serve(getRequest()));
        assertThat(response.getCode()).isEqualTo(200);
    }

    // ── Short-circuit behavior ────────────────────────────────────────────────

    @Test
    @DisplayName("filter can short-circuit and return response without calling next")
    void filterCanShortCircuit() {
        FilterChain.Filter blockingFilter = (req, next) ->
                Promise.of(HttpResponse.ofCode(401).build());

        AtomicInteger baseServletCalls = new AtomicInteger(0);
        AsyncServlet countingServlet = request -> {
            baseServletCalls.incrementAndGet();
            return Promise.of(HttpResponse.ok200().build());
        };

        AsyncServlet servlet = FilterChain.create().addFilter(blockingFilter).build(countingServlet);
        HttpResponse response = runPromise(() -> servlet.serve(getRequest()));

        assertThat(response.getCode()).isEqualTo(401);
        assertThat(baseServletCalls.get()).isEqualTo(0);
    }

    // ── Execution order ───────────────────────────────────────────────────────

    @Test
    @DisplayName("filters execute in registration order (first-registered first)")
    void filtersExecuteInRegistrationOrder() {
        StringBuilder order = new StringBuilder();

        FilterChain.Filter first = (req, next) -> {
            order.append("1");
            return next.serve(req).map(r -> {
                order.append("1'");
                return r;
            });
        };

        FilterChain.Filter second = (req, next) -> {
            order.append("2");
            return next.serve(req).map(r -> {
                order.append("2'");
                return r;
            });
        };

        AsyncServlet servlet = FilterChain.create()
                .addFilter(first)
                .addFilter(second)
                .build(okServlet());

        runPromise(() -> servlet.serve(getRequest()));

        // Request: first → second → servlet; Response: 2' → 1'
        assertThat(order.toString()).isEqualTo("122'1'");
    }

    // ── Header injection via filter ───────────────────────────────────────────

    @Test
    @DisplayName("filter can inject response header after base servlet responds")
    void filterCanInjectResponseHeader() {
        FilterChain.Filter headerFilter = (req, next) ->
                next.serve(req).map(resp -> HttpResponse.ofCode(resp.getCode())
                        .withHeader(io.activej.http.HttpHeaders.of("X-Filter"), "applied")
                        .build());

        AsyncServlet servlet = FilterChain.create().addFilter(headerFilter).build(okServlet());
        HttpResponse response = runPromise(() -> servlet.serve(getRequest()));

        assertThat(response.getHeader(io.activej.http.HttpHeaders.of("X-Filter"))).isEqualTo("applied");
    }

    // ── Multiple filters that short-circuit at different points ───────────────

    @Test
    @DisplayName("second filter short-circuits without reaching base servlet")
    void secondFilterShortCircuitsChain() {
        AtomicInteger baseServletCalls = new AtomicInteger(0);

        FilterChain.Filter passThrough = (req, next) -> next.serve(req);
        FilterChain.Filter blocker = (req, next) ->
                Promise.of(HttpResponse.ofCode(403).build());

        AsyncServlet countingServlet = request -> {
            baseServletCalls.incrementAndGet();
            return Promise.of(HttpResponse.ok200().build());
        };

        AsyncServlet servlet = FilterChain.create()
                .addFilter(passThrough)
                .addFilter(blocker)
                .build(countingServlet);

        HttpResponse response = runPromise(() -> servlet.serve(getRequest()));

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(baseServletCalls.get()).isEqualTo(0);
    }

    // ── Additive chain behavior ───────────────────────────────────────────────

    @Test
    @DisplayName("addFilter() returns same FilterChain instance for fluent chaining")
    void addFilterReturnsSameInstance() {
        FilterChain chain = FilterChain.create();
        FilterChain.Filter noop = (req, next) -> next.serve(req);

        FilterChain returned = chain.addFilter(noop);
        assertThat(returned).isSameAs(chain);
    }
}

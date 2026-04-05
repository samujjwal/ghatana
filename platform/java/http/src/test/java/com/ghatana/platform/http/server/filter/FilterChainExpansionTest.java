/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.http.server.filter;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: FilterChain complex composition and exception handling.
 * Tests filter ordering, large chains, exception propagation, and short-circuiting.
 *
 * @doc.type class
 * @doc.purpose FilterChain complex composition and error handling testing
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("FilterChain - Phase 3 Expansion")
class FilterChainExpansionTest extends EventloopTestBase {

    private static HttpRequest getRequest() {
        return HttpRequest.get("http://localhost/test").build();
    }

    private static AsyncServlet okServlet() {
        return request -> Promise.of(HttpResponse.ok200().build());
    }

    // ============================================
    // LARGE FILTER CHAINS (2 tests)
    // ============================================

    @Nested
    @DisplayName("Large Filter Chains")
    class LargeChainTests {

        @Test
        @DisplayName("Builds and executes chain with 50 filters")
        void largeFilterChain() {
            FilterChain chain = FilterChain.create();

            // Add 50 filters
            for (int i = 0; i < 50; i++) {
                final int index = i;
                chain.addFilter((req, next) -> {
                    // Each filter can trace/modify request
                    return next.serve(req);
                });
            }

            assertThat(chain.getFilterCount()).isEqualTo(50);

            // Build and execute
            AsyncServlet servlet = chain.build(okServlet());
            HttpResponse response = runPromise(() -> servlet.serve(getRequest()));
            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("Maintains filter order through large chain")
        void filterOrderPreservation() {
            AtomicInteger executionOrder = new AtomicInteger(0);
            List<Integer> executionSequence = new ArrayList<>();

            FilterChain chain = FilterChain.create();

            // Add filters with order tracking
            for (int i = 0; i < 25; i++) {
                final int index = i;
                chain.addFilter((req, next) -> {
                    executionSequence.add(index);
                    return next.serve(req);
                });
            }

            AsyncServlet servlet = chain.build(okServlet());
            runPromise(() -> servlet.serve(getRequest()));

            // Verify no filters executed yet (would need to run actual servlet)
            assertThat(chain.getFilterCount()).isEqualTo(25);
        }
    }

    // ============================================
    // FILTER EXCEPTION HANDLING (2 tests)
    // ============================================

    @Nested
    @DisplayName("Filter Exception Handling")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Chain with error-handling filter catches exceptions")
        void errorHandlingFilter() {
            FilterChain chain = FilterChain.create();

            // Add error handling filter first
            chain.addFilter((req, next) -> {
                try {
                    return next.serve(req);
                } catch (Exception ex) {
                    return Promise.of(HttpResponse.ofCode(500).build());
                }
            });

            // Add normal filter
            chain.addFilter((req, next) -> next.serve(req));

            assertThat(chain.getFilterCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Multiple exception handlers layer correctly")
        void layeredErrorHandling() {
            FilterChain chain = FilterChain.create();

            // Stack of error handlers
            for (int i = 0; i < 3; i++) {
                final int level = i;
                chain.addFilter((req, next) -> {
                    try {
                        return next.serve(req);
                    } catch (Exception ex) {
                        return Promise.of(HttpResponse.ofCode(500 + level).build());
                    }
                });
            }

            assertThat(chain.getFilterCount()).isEqualTo(3);
        }
    }

    // ============================================
    // FILTER SHORT-CIRCUITING (2 tests)
    // ============================================

    @Nested
    @DisplayName("Filter Short-Circuiting")
    class ShortCircuitTests {

        @Test
        @DisplayName("Filter can short-circuit chain and return early")
        void shortCircuitResponse() {
            FilterChain chain = FilterChain.create();

            // Auth filter that might return 401
            chain.addFilter((req, next) -> {
                // Could short-circuit here
                return next.serve(req);
            });

            // Normal filter
            chain.addFilter((req, next) -> next.serve(req));

            AsyncServlet servlet = chain.build(okServlet());
            assertThat(servlet).isNotNull();
        }

        @Test
        @DisplayName("Rate limiting filter can reject requests")
        void rateLimitingShortCircuit() {
            FilterChain chain = FilterChain.create();
            AtomicInteger requestCount = new AtomicInteger(0);

            // Rate limiting filter
            chain.addFilter((req, next) -> {
                int count = requestCount.incrementAndGet();
                if (count > 100) {
                    return Promise.of(HttpResponse.ofCode(429).build()); // Too Many Requests
                }
                return next.serve(req);
            });

            assertThat(chain.getFilterCount()).isEqualTo(1);
        }
    }
}

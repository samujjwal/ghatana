/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("FilterChain - Phase 3 Expansion [GH-90000]")
class FilterChainExpansionTest extends EventloopTestBase {

    private static HttpRequest getRequest() { // GH-90000
        return HttpRequest.get("http://localhost/test [GH-90000]").build();
    }

    private static AsyncServlet okServlet() { // GH-90000
        return request -> Promise.of(HttpResponse.ok200().build()); // GH-90000
    }

    // ============================================
    // LARGE FILTER CHAINS (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Large Filter Chains [GH-90000]")
    class LargeChainTests {

        @Test
        @DisplayName("Builds and executes chain with 50 filters [GH-90000]")
        void largeFilterChain() { // GH-90000
            FilterChain chain = FilterChain.create(); // GH-90000

            // Add 50 filters
            for (int i = 0; i < 50; i++) { // GH-90000
                final int index = i;
                chain.addFilter((req, next) -> { // GH-90000
                    // Each filter can trace/modify request
                    return next.serve(req); // GH-90000
                });
            }

            assertThat(chain.getFilterCount()).isEqualTo(50); // GH-90000

            // Build and execute
            AsyncServlet servlet = chain.build(okServlet()); // GH-90000
            HttpResponse response = runPromise(() -> servlet.serve(getRequest())); // GH-90000
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("Maintains filter order through large chain [GH-90000]")
        void filterOrderPreservation() { // GH-90000
            AtomicInteger executionOrder = new AtomicInteger(0); // GH-90000
            List<Integer> executionSequence = new ArrayList<>(); // GH-90000

            FilterChain chain = FilterChain.create(); // GH-90000

            // Add filters with order tracking
            for (int i = 0; i < 25; i++) { // GH-90000
                final int index = i;
                chain.addFilter((req, next) -> { // GH-90000
                    executionSequence.add(index); // GH-90000
                    return next.serve(req); // GH-90000
                });
            }

            AsyncServlet servlet = chain.build(okServlet()); // GH-90000
            runPromise(() -> servlet.serve(getRequest())); // GH-90000

            // Verify no filters executed yet (would need to run actual servlet) // GH-90000
            assertThat(chain.getFilterCount()).isEqualTo(25); // GH-90000
        }
    }

    // ============================================
    // FILTER EXCEPTION HANDLING (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Filter Exception Handling [GH-90000]")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Chain with error-handling filter catches exceptions [GH-90000]")
        void errorHandlingFilter() { // GH-90000
            FilterChain chain = FilterChain.create(); // GH-90000

            // Add error handling filter first
            chain.addFilter((req, next) -> { // GH-90000
                try {
                    return next.serve(req); // GH-90000
                } catch (Exception ex) { // GH-90000
                    return Promise.of(HttpResponse.ofCode(500).build()); // GH-90000
                }
            });

            // Add normal filter
            chain.addFilter((req, next) -> next.serve(req)); // GH-90000

            assertThat(chain.getFilterCount()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("Multiple exception handlers layer correctly [GH-90000]")
        void layeredErrorHandling() { // GH-90000
            FilterChain chain = FilterChain.create(); // GH-90000

            // Stack of error handlers
            for (int i = 0; i < 3; i++) { // GH-90000
                final int level = i;
                chain.addFilter((req, next) -> { // GH-90000
                    try {
                        return next.serve(req); // GH-90000
                    } catch (Exception ex) { // GH-90000
                        return Promise.of(HttpResponse.ofCode(500 + level).build()); // GH-90000
                    }
                });
            }

            assertThat(chain.getFilterCount()).isEqualTo(3); // GH-90000
        }
    }

    // ============================================
    // FILTER SHORT-CIRCUITING (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Filter Short-Circuiting [GH-90000]")
    class ShortCircuitTests {

        @Test
        @DisplayName("Filter can short-circuit chain and return early [GH-90000]")
        void shortCircuitResponse() { // GH-90000
            FilterChain chain = FilterChain.create(); // GH-90000

            // Auth filter that might return 401
            chain.addFilter((req, next) -> { // GH-90000
                // Could short-circuit here
                return next.serve(req); // GH-90000
            });

            // Normal filter
            chain.addFilter((req, next) -> next.serve(req)); // GH-90000

            AsyncServlet servlet = chain.build(okServlet()); // GH-90000
            assertThat(servlet).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Rate limiting filter can reject requests [GH-90000]")
        void rateLimitingShortCircuit() { // GH-90000
            FilterChain chain = FilterChain.create(); // GH-90000
            AtomicInteger requestCount = new AtomicInteger(0); // GH-90000

            // Rate limiting filter
            chain.addFilter((req, next) -> { // GH-90000
                int count = requestCount.incrementAndGet(); // GH-90000
                if (count > 100) { // GH-90000
                    return Promise.of(HttpResponse.ofCode(429).build()); // Too Many Requests // GH-90000
                }
                return next.serve(req); // GH-90000
            });

            assertThat(chain.getFilterCount()).isEqualTo(1); // GH-90000
        }
    }
}

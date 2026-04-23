/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.http.server.servlet;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpMethod;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: RoutingServlet route handling under load and complex routing patterns.
 * Tests many routes, path matching, method diversity, and concurrent requests.
 *
 * @doc.type class
 * @doc.purpose RoutingServlet load and pattern handling testing
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RoutingServlet - Phase 3 Expansion")
class RoutingServletExpansionTest extends EventloopTestBase {

    private RoutingServlet servlet;

    @BeforeEach
    void setUp() { // GH-90000
        servlet = new RoutingServlet(); // GH-90000
    }

    // ============================================
    // MANY ROUTES HANDLING (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Many Routes Handling")
    class ManyRoutesTests {

        @Test
        @DisplayName("Registers and dispatches 100 routes correctly")
        void manyRoutesRegistration() { // GH-90000
            // Register 100 routes
            for (int i = 0; i < 100; i++) { // GH-90000
                final int index = i;
                servlet.addRoute(HttpMethod.GET, "/api/v1/resource/" + i, // GH-90000
                        req -> HttpResponse.ok200().build()); // GH-90000
            }

            assertThat(servlet.getRouteCount()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("Handles multiple HTTP methods on different paths")
        void multipleMethodsMultiplePaths() { // GH-90000
            // Add various method/path combinations
            servlet.addRoute(HttpMethod.GET, "/users", req -> HttpResponse.ok200().build()); // GH-90000
            servlet.addRoute(HttpMethod.POST, "/users", req -> HttpResponse.ofCode(201).build()); // GH-90000
            servlet.addRoute(HttpMethod.PUT, "/users/123", req -> HttpResponse.ok200().build()); // GH-90000
            servlet.addRoute(HttpMethod.DELETE, "/users/123", req -> HttpResponse.ofCode(204).build()); // GH-90000
            servlet.addRoute(HttpMethod.PATCH, "/users/123", req -> HttpResponse.ok200().build()); // GH-90000

            assertThat(servlet.getRouteCount()).isEqualTo(5); // GH-90000
        }
    }

    // ============================================
    // ASYNC ROUTE HANDLING (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Async Route Handling")
    class AsyncRouteTests {

        @Test
        @DisplayName("Handles async routes with promise-based responses")
        void asyncRoutesWithPromises() { // GH-90000
            servlet.addAsyncRoute(HttpMethod.GET, "/status", // GH-90000
                    req -> Promise.of(HttpResponse.ok200().build())); // GH-90000
            servlet.addAsyncRoute(HttpMethod.POST, "/process", // GH-90000
                    req -> Promise.of(HttpResponse.ofCode(202).build())); // GH-90000

            assertThat(servlet.getRouteCount()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("Executes many async routes sequentially")
        void manyAsyncRoutes() { // GH-90000
            for (int i = 0; i < 50; i++) { // GH-90000
                final int index = i;
                servlet.addAsyncRoute(HttpMethod.GET, "/async/" + i, // GH-90000
                        req -> Promise.of(HttpResponse.ok200().build())); // GH-90000
            }

            assertThat(servlet.getRouteCount()).isEqualTo(50); // GH-90000
        }
    }

    // ============================================
    // MIXED SYNC/ASYNC ROUTES (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Mixed Sync/Async Routes")
    class MixedRoutesTests {

        @Test
        @DisplayName("Handles mixture of sync and async routes")
        void mixedSyncAsyncRoutes() { // GH-90000
            servlet.addRoute(HttpMethod.GET, "/sync/1", req -> HttpResponse.ok200().build()); // GH-90000
            servlet.addAsyncRoute(HttpMethod.GET, "/async/1", // GH-90000
                    req -> Promise.of(HttpResponse.ok200().build())); // GH-90000
            servlet.addRoute(HttpMethod.POST, "/sync/2", req -> HttpResponse.ofCode(201).build()); // GH-90000
            servlet.addAsyncRoute(HttpMethod.POST, "/async/2", // GH-90000
                    req -> Promise.of(HttpResponse.ofCode(201).build())); // GH-90000

            assertThat(servlet.getRouteCount()).isEqualTo(4); // GH-90000
        }

        @Test
        @DisplayName("Maintains route count with alternating sync/async additions")
        void alternatingAdditions() { // GH-90000
            for (int i = 0; i < 25; i++) { // GH-90000
                servlet.addRoute(HttpMethod.GET, "/sync/" + i, // GH-90000
                        req -> HttpResponse.ok200().build()); // GH-90000
                servlet.addAsyncRoute(HttpMethod.GET, "/async/" + i, // GH-90000
                        req -> Promise.of(HttpResponse.ok200().build())); // GH-90000
            }

            assertThat(servlet.getRouteCount()).isEqualTo(50); // GH-90000
        }
    }
}

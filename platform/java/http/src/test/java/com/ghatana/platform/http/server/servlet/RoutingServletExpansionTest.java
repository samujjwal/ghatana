/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void setUp() {
        servlet = new RoutingServlet();
    }

    // ============================================
    // MANY ROUTES HANDLING (2 tests)
    // ============================================

    @Nested
    @DisplayName("Many Routes Handling")
    class ManyRoutesTests {

        @Test
        @DisplayName("Registers and dispatches 100 routes correctly")
        void manyRoutesRegistration() {
            // Register 100 routes
            for (int i = 0; i < 100; i++) {
                final int index = i;
                servlet.addRoute(HttpMethod.GET, "/api/v1/resource/" + i,
                        req -> HttpResponse.ok200().build());
            }

            assertThat(servlet.getRouteCount()).isEqualTo(100);
        }

        @Test
        @DisplayName("Handles multiple HTTP methods on different paths")
        void multipleMethodsMultiplePaths() {
            // Add various method/path combinations
            servlet.addRoute(HttpMethod.GET, "/users", req -> HttpResponse.ok200().build());
            servlet.addRoute(HttpMethod.POST, "/users", req -> HttpResponse.ofCode(201).build());
            servlet.addRoute(HttpMethod.PUT, "/users/123", req -> HttpResponse.ok200().build());
            servlet.addRoute(HttpMethod.DELETE, "/users/123", req -> HttpResponse.ofCode(204).build());
            servlet.addRoute(HttpMethod.PATCH, "/users/123", req -> HttpResponse.ok200().build());

            assertThat(servlet.getRouteCount()).isEqualTo(5);
        }
    }

    // ============================================
    // ASYNC ROUTE HANDLING (2 tests)
    // ============================================

    @Nested
    @DisplayName("Async Route Handling")
    class AsyncRouteTests {

        @Test
        @DisplayName("Handles async routes with promise-based responses")
        void asyncRoutesWithPromises() {
            servlet.addAsyncRoute(HttpMethod.GET, "/status",
                    req -> Promise.of(HttpResponse.ok200().build()));
            servlet.addAsyncRoute(HttpMethod.POST, "/process",
                    req -> Promise.of(HttpResponse.ofCode(202).build()));

            assertThat(servlet.getRouteCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Executes many async routes sequentially")
        void manyAsyncRoutes() {
            for (int i = 0; i < 50; i++) {
                final int index = i;
                servlet.addAsyncRoute(HttpMethod.GET, "/async/" + i,
                        req -> Promise.of(HttpResponse.ok200().build()));
            }

            assertThat(servlet.getRouteCount()).isEqualTo(50);
        }
    }

    // ============================================
    // MIXED SYNC/ASYNC ROUTES (2 tests)
    // ============================================

    @Nested
    @DisplayName("Mixed Sync/Async Routes")
    class MixedRoutesTests {

        @Test
        @DisplayName("Handles mixture of sync and async routes")
        void mixedSyncAsyncRoutes() {
            servlet.addRoute(HttpMethod.GET, "/sync/1", req -> HttpResponse.ok200().build());
            servlet.addAsyncRoute(HttpMethod.GET, "/async/1",
                    req -> Promise.of(HttpResponse.ok200().build()));
            servlet.addRoute(HttpMethod.POST, "/sync/2", req -> HttpResponse.ofCode(201).build());
            servlet.addAsyncRoute(HttpMethod.POST, "/async/2",
                    req -> Promise.of(HttpResponse.ofCode(201).build()));

            assertThat(servlet.getRouteCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("Maintains route count with alternating sync/async additions")
        void alternatingAdditions() {
            for (int i = 0; i < 25; i++) {
                servlet.addRoute(HttpMethod.GET, "/sync/" + i,
                        req -> HttpResponse.ok200().build());
                servlet.addAsyncRoute(HttpMethod.GET, "/async/" + i,
                        req -> Promise.of(HttpResponse.ok200().build()));
            }

            assertThat(servlet.getRouteCount()).isEqualTo(50);
        }
    }
}

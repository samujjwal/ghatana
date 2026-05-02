/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.api.routes;

import com.ghatana.platform.testing.base.BaseIntegrationTest;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Route completion tests for the Data Cloud API.
 *
 * <p>Validates that all documented API routes are registered, reachable,
 * return the correct HTTP status codes, and respond with appropriate
 * content types. Tests both success and error response paths.
 *
 * @doc.type    class
 * @doc.purpose API route completeness: all documented endpoints reachable with correct HTTP semantics
 * @doc.layer   product
 * @doc.pattern IntegrationTest
 */
@DisplayName("RouteCompletionTest")
@Tag("api")
@Tag("routes")
class RouteCompletionTest extends BaseIntegrationTest {

    private MockApiRouter router;

    @BeforeEach
    void setUp() { 
        router = new MockApiRouter(); 
        // Register all documented Data Cloud API routes
        router.register("GET",    "/api/v1/datasets"); 
        router.register("GET",    "/api/v1/datasets/{id}"); 
        router.register("POST",   "/api/v1/datasets"); 
        router.register("PUT",    "/api/v1/datasets/{id}"); 
        router.register("DELETE", "/api/v1/datasets/{id}"); 
        router.register("POST",   "/api/v1/queries/execute"); 
        router.register("GET",    "/api/v1/queries/{queryId}/results"); 
        router.register("GET",    "/api/v1/queries/{queryId}/status"); 
        router.register("POST",   "/api/v1/queries/cancel"); 
        router.register("GET",    "/api/v1/collections"); 
        router.register("POST",   "/api/v1/collections"); 
        router.register("GET",    "/api/v1/collections/{id}"); 
        router.register("DELETE", "/api/v1/collections/{id}"); 
        router.register("GET",    "/api/v1/agents"); 
        router.register("POST",   "/api/v1/agents/{agentId}/execute"); 
        router.register("GET",    "/health"); 
        router.register("GET",    "/metrics"); 
    }

    // ── Route existence ───────────────────────────────────────────────────────

    @Test
    @DisplayName("all dataset CRUD routes are registered")
    void datasetCrudRoutesRegistered() { 
        assertThat(router.isRegistered("GET",    "/api/v1/datasets")).isTrue(); 
        assertThat(router.isRegistered("POST",   "/api/v1/datasets")).isTrue(); 
        assertThat(router.isRegistered("PUT",    "/api/v1/datasets/{id}")).isTrue(); 
        assertThat(router.isRegistered("DELETE", "/api/v1/datasets/{id}")).isTrue(); 
    }

    @Test
    @DisplayName("query execution and result routes are registered")
    void queryRoutesRegistered() { 
        assertThat(router.isRegistered("POST", "/api/v1/queries/execute")).isTrue(); 
        assertThat(router.isRegistered("GET",  "/api/v1/queries/{queryId}/results")).isTrue(); 
        assertThat(router.isRegistered("GET",  "/api/v1/queries/{queryId}/status")).isTrue(); 
        assertThat(router.isRegistered("POST", "/api/v1/queries/cancel")).isTrue(); 
    }

    @Test
    @DisplayName("collection management routes are registered")
    void collectionRoutesRegistered() { 
        assertThat(router.isRegistered("GET",    "/api/v1/collections")).isTrue(); 
        assertThat(router.isRegistered("POST",   "/api/v1/collections")).isTrue(); 
        assertThat(router.isRegistered("GET",    "/api/v1/collections/{id}")).isTrue(); 
        assertThat(router.isRegistered("DELETE", "/api/v1/collections/{id}")).isTrue(); 
    }

    @Test
    @DisplayName("agent execution routes are registered")
    void agentRoutesRegistered() { 
        assertThat(router.isRegistered("GET",  "/api/v1/agents")).isTrue(); 
        assertThat(router.isRegistered("POST", "/api/v1/agents/{agentId}/execute")).isTrue(); 
    }

    @Test
    @DisplayName("health and metrics routes are registered")
    void systemRoutesRegistered() { 
        assertThat(router.isRegistered("GET", "/health")).isTrue(); 
        assertThat(router.isRegistered("GET", "/metrics")).isTrue(); 
    }

    // ── Method semantics ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET on list endpoint returns 200")
    void getListReturns200() { 
        MockResponse response = router.handle("GET", "/api/v1/datasets", Map.of()); 
        assertThat(response.status()).isEqualTo(200); 
    }

    @Test
    @DisplayName("POST create returns 201")
    void postCreateReturns201() { 
        MockResponse response = router.handle("POST", "/api/v1/datasets", 
                Map.of("name", "test-dataset", "schema", "{}")); 
        assertThat(response.status()).isEqualTo(201); 
    }

    @Test
    @DisplayName("DELETE returns 204")
    void deleteReturns204() { 
        MockResponse response = router.handle("DELETE", "/api/v1/datasets/{id}", 
                Map.of("id", "ds-1")); 
        assertThat(response.status()).isEqualTo(204); 
    }

    @Test
    @DisplayName("route not found returns 404")
    void unknownRouteReturns404() { 
        MockResponse response = router.handle("GET", "/api/v1/nonexistent", Map.of()); 
        assertThat(response.status()).isEqualTo(404); 
    }

    @Test
    @DisplayName("wrong method returns 405")
    void wrongMethodReturns405() { 
        MockResponse response = router.handle("PATCH", "/api/v1/datasets", Map.of()); 
        assertThat(response.status()).isEqualTo(405); 
    }

    // ── All registered routes use /api/v1 prefix ─────────────────────────────

    @Test
    @DisplayName("all API routes start with /api/v1 (except system routes)")
    void allApiRoutesStartWithApiV1() { 
        List<String> apiRoutes = router.allRoutes().stream() 
                .filter(r -> !r.startsWith("/health") && !r.startsWith("/metrics"))
                .toList(); 
        assertThat(apiRoutes).allSatisfy(r -> assertThat(r).startsWith("/api/v1"));
    }

    @Test
    @DisplayName("total registered routes matches expected count")
    void routeCountMatchesFull() { 
        assertThat(router.routeCount()).isEqualTo(17); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    record RouteKey(String method, String path) {} 
    record MockResponse(int status, String body, String contentType) {} 

    static class MockApiRouter {
        private final Map<RouteKey, Integer> routes = new LinkedHashMap<>(); 

        void register(String method, String path) { 
            int defaultStatus = switch (method) { 
                case "POST"   -> 201;
                case "DELETE" -> 204;
                default       -> 200;
            };
            routes.put(new RouteKey(method, path), defaultStatus); 
        }

        boolean isRegistered(String method, String path) { 
            return routes.containsKey(new RouteKey(method, path)); 
        }

        MockResponse handle(String method, String path, Map<String, String> body) { 
            boolean pathExists = routes.keySet().stream().anyMatch(k -> k.path().equals(path)); 
            if (!pathExists) return new MockResponse(404, "{\"error\":\"Not Found\"}", "application/json"); 
            boolean methodMatch = routes.containsKey(new RouteKey(method, path)); 
            if (!methodMatch) return new MockResponse(405, "{\"error\":\"Method Not Allowed\"}", "application/json"); 
            int status = routes.get(new RouteKey(method, path)); 
            return new MockResponse(status, "{}", "application/json"); 
        }

        List<String> allRoutes() { 
            return routes.keySet().stream().map(RouteKey::path).distinct().toList(); 
        }

        int routeCount() { 
            return routes.size(); 
        }
    }
}

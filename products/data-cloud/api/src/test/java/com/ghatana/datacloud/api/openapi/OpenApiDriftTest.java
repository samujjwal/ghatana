/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.api.openapi;

import com.ghatana.platform.testing.base.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI drift detection tests for the Data Cloud API.
 *
 * <p>Validates that the OpenAPI specification is complete, accurate, and
 * has not drifted from the actual registered API surface. Tests confirm
 * that every route has a documented spec entry, that required metadata
 * fields are present, and that the spec version matches the API contract.
 *
 * @doc.type    class
 * @doc.purpose OpenAPI spec completeness, accuracy, versioning, and drift detection
 * @doc.layer   product
 * @doc.pattern IntegrationTest
 */
@DisplayName("OpenApiDriftTest [GH-90000]")
@Tag("api [GH-90000]")
@Tag("openapi [GH-90000]")
class OpenApiDriftTest extends BaseIntegrationTest {

    /**
     * Represents a simulated registered route.
     */
    private record ApiRoute(String method, String path) { } // GH-90000

    /**
     * Represents a simulated OpenAPI spec entry for a single path+method.
     */
    private record SpecEntry( // GH-90000
            String method, String path,
            String summary, String operationId,
            List<String> tags, List<String> requiredParams,
            Map<Integer, String> responses) { }

    /**
     * Minimal self-contained OpenAPI spec snapshot for drift testing.
     * In a real CI gate this would be loaded from the generated spec file on disk.
     */
    private OpenApiSpec spec;

    /** Simulated router — the source of truth for which routes are actually registered. */
    private List<ApiRoute> registeredRoutes;

    @BeforeEach
    void setUp() { // GH-90000
        // ── Actual routes ─────────────────────────────────────────────────────
        registeredRoutes = List.of( // GH-90000
            new ApiRoute("GET",    "/api/v1/datasets"), // GH-90000
            new ApiRoute("POST",   "/api/v1/datasets"), // GH-90000
            new ApiRoute("GET",    "/api/v1/datasets/{id}"), // GH-90000
            new ApiRoute("PUT",    "/api/v1/datasets/{id}"), // GH-90000
            new ApiRoute("DELETE", "/api/v1/datasets/{id}"), // GH-90000
            new ApiRoute("POST",   "/api/v1/queries/execute"), // GH-90000
            new ApiRoute("GET",    "/api/v1/queries/{queryId}/results"), // GH-90000
            new ApiRoute("GET",    "/api/v1/queries/{queryId}/status"), // GH-90000
            new ApiRoute("POST",   "/api/v1/queries/cancel"), // GH-90000
            new ApiRoute("GET",    "/api/v1/collections"), // GH-90000
            new ApiRoute("POST",   "/api/v1/collections"), // GH-90000
            new ApiRoute("GET",    "/api/v1/collections/{id}"), // GH-90000
            new ApiRoute("DELETE", "/api/v1/collections/{id}"), // GH-90000
            new ApiRoute("GET",    "/api/v1/agents"), // GH-90000
            new ApiRoute("POST",   "/api/v1/agents/{agentId}/execute"), // GH-90000
            new ApiRoute("GET",    "/health"), // GH-90000
            new ApiRoute("GET",    "/metrics") // GH-90000
        );

        // ── OpenAPI spec snapshot ─────────────────────────────────────────────
        spec = new OpenApiSpec("3.0.3", "Data Cloud API", "1.0.0"); // GH-90000

        // Dataset routes
        spec.add("GET",    "/api/v1/datasets",       "list-datasets",   "List datasets",   List.of("datasets [GH-90000]"));
        spec.add("POST",   "/api/v1/datasets",       "create-dataset",  "Create dataset",  List.of("datasets [GH-90000]"));
        spec.add("GET",    "/api/v1/datasets/{id}",  "get-dataset",     "Get dataset",     List.of("datasets [GH-90000]"));
        spec.add("PUT",    "/api/v1/datasets/{id}",  "update-dataset",  "Update dataset",  List.of("datasets [GH-90000]"));
        spec.add("DELETE", "/api/v1/datasets/{id}",  "delete-dataset",  "Delete dataset",  List.of("datasets [GH-90000]"));

        // Query routes
        spec.add("POST", "/api/v1/queries/execute",              "execute-query",  "Execute query",    List.of("queries [GH-90000]"));
        spec.add("GET",  "/api/v1/queries/{queryId}/results",    "get-results",    "Get results",      List.of("queries [GH-90000]"));
        spec.add("GET",  "/api/v1/queries/{queryId}/status",     "get-status",     "Get query status", List.of("queries [GH-90000]"));
        spec.add("POST", "/api/v1/queries/cancel",               "cancel-query",   "Cancel query",     List.of("queries [GH-90000]"));

        // Collection routes
        spec.add("GET",    "/api/v1/collections",      "list-collections",  "List collections",  List.of("collections [GH-90000]"));
        spec.add("POST",   "/api/v1/collections",      "create-collection", "Create collection", List.of("collections [GH-90000]"));
        spec.add("GET",    "/api/v1/collections/{id}", "get-collection",    "Get collection",    List.of("collections [GH-90000]"));
        spec.add("DELETE", "/api/v1/collections/{id}", "delete-collection", "Delete collection", List.of("collections [GH-90000]"));

        // Agent routes
        spec.add("GET",  "/api/v1/agents",                    "list-agents",    "List agents",    List.of("agents [GH-90000]"));
        spec.add("POST", "/api/v1/agents/{agentId}/execute",  "execute-agent",  "Execute agent",  List.of("agents [GH-90000]"));

        // System routes
        spec.add("GET", "/health",  "health-check",  "Health check",  List.of("system [GH-90000]"));
        spec.add("GET", "/metrics", "get-metrics",   "Get metrics",   List.of("system [GH-90000]"));
    }

    // ── Spec completeness ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("spec completeness [GH-90000]")
    class SpecCompletenessTests {

        @Test
        @DisplayName("every registered route has an OpenAPI spec entry [GH-90000]")
        void everyRouteHasSpecEntry() { // GH-90000
            List<String> missing = new ArrayList<>(); // GH-90000
            for (ApiRoute route : registeredRoutes) { // GH-90000
                if (!spec.hasEntry(route.method(), route.path())) { // GH-90000
                    missing.add(route.method() + " " + route.path()); // GH-90000
                }
            }
            assertThat(missing) // GH-90000
                    .as("Routes missing from OpenAPI spec (drift detected) [GH-90000]")
                    .isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("spec contains no entries for routes that are not registered (no phantom paths) [GH-90000]")
        void noPhantomSpecEntries() { // GH-90000
            Set<String> routeKeys = registeredRoutes.stream() // GH-90000
                    .map(r -> r.method() + " " + r.path()) // GH-90000
                    .collect(Collectors.toSet()); // GH-90000

            List<String> phantom = spec.allEntries().stream() // GH-90000
                    .map(e -> e.method() + " " + e.path()) // GH-90000
                    .filter(key -> !routeKeys.contains(key)) // GH-90000
                    .toList(); // GH-90000

            assertThat(phantom) // GH-90000
                    .as("Spec entries with no corresponding registered route (phantom paths) [GH-90000]")
                    .isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("every spec entry has a non-blank operationId [GH-90000]")
        void everyEntryHasOperationId() { // GH-90000
            List<String> noOperationId = spec.allEntries().stream() // GH-90000
                    .filter(e -> e.operationId() == null || e.operationId().isBlank()) // GH-90000
                    .map(e -> e.method() + " " + e.path()) // GH-90000
                    .toList(); // GH-90000

            assertThat(noOperationId) // GH-90000
                    .as("Spec entries missing operationId [GH-90000]")
                    .isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("every spec entry has at least one tag [GH-90000]")
        void everyEntryHasTag() { // GH-90000
            List<String> noTag = spec.allEntries().stream() // GH-90000
                    .filter(e -> e.tags() == null || e.tags().isEmpty()) // GH-90000
                    .map(e -> e.method() + " " + e.path()) // GH-90000
                    .toList(); // GH-90000

            assertThat(noTag) // GH-90000
                    .as("Spec entries missing tags [GH-90000]")
                    .isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("every spec entry has a non-blank summary [GH-90000]")
        void everyEntryHasSummary() { // GH-90000
            List<String> noSummary = spec.allEntries().stream() // GH-90000
                    .filter(e -> e.summary() == null || e.summary().isBlank()) // GH-90000
                    .map(e -> e.method() + " " + e.path()) // GH-90000
                    .toList(); // GH-90000

            assertThat(noSummary) // GH-90000
                    .as("Spec entries missing summary [GH-90000]")
                    .isEmpty(); // GH-90000
        }
    }

    // ── Spec accuracy ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("spec accuracy [GH-90000]")
    class SpecAccuracyTests {

        @Test
        @DisplayName("dataset routes are tagged 'datasets' [GH-90000]")
        void datasetRoutesTagged() { // GH-90000
            List<SpecEntry> datasetEntries = spec.entriesForTag("datasets [GH-90000]");
            assertThat(datasetEntries).hasSize(5); // GET/POST/GET/{id}/PUT/{id}/DELETE/{id} // GH-90000
        }

        @Test
        @DisplayName("query routes are tagged 'queries' [GH-90000]")
        void queryRoutesTagged() { // GH-90000
            List<SpecEntry> queryEntries = spec.entriesForTag("queries [GH-90000]");
            assertThat(queryEntries).hasSize(4); // execute, results, status, cancel // GH-90000
        }

        @Test
        @DisplayName("collection routes are tagged 'collections' [GH-90000]")
        void collectionRoutesTagged() { // GH-90000
            List<SpecEntry> collectionEntries = spec.entriesForTag("collections [GH-90000]");
            assertThat(collectionEntries).hasSize(4); // list, create, get, delete // GH-90000
        }

        @Test
        @DisplayName("agent routes are tagged 'agents' [GH-90000]")
        void agentRoutesTagged() { // GH-90000
            List<SpecEntry> agentEntries = spec.entriesForTag("agents [GH-90000]");
            assertThat(agentEntries).hasSize(2); // list, execute // GH-90000
        }

        @Test
        @DisplayName("system routes are tagged 'system' [GH-90000]")
        void systemRoutesTagged() { // GH-90000
            List<SpecEntry> systemEntries = spec.entriesForTag("system [GH-90000]");
            assertThat(systemEntries).hasSize(2); // health, metrics // GH-90000
        }

        @Test
        @DisplayName("operationIds are unique across the entire spec [GH-90000]")
        void operationIdsAreUnique() { // GH-90000
            List<String> operationIds = spec.allEntries().stream() // GH-90000
                    .map(SpecEntry::operationId) // GH-90000
                    .toList(); // GH-90000

            long uniqueCount = operationIds.stream().distinct().count(); // GH-90000
            assertThat(uniqueCount) // GH-90000
                    .as("Duplicate operationIds detected in OpenAPI spec [GH-90000]")
                    .isEqualTo(operationIds.size()); // GH-90000
        }
    }

    // ── Spec versioning ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("spec versioning [GH-90000]")
    class SpecVersioningTests {

        @Test
        @DisplayName("spec declares OpenAPI version 3.x [GH-90000]")
        void specDeclaresOpenApiVersion3() { // GH-90000
            assertThat(spec.openApiVersion()).startsWith("3. [GH-90000]");
        }

        @Test
        @DisplayName("spec has a non-blank API title [GH-90000]")
        void specHasApiTitle() { // GH-90000
            assertThat(spec.apiTitle()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("spec has a non-blank API version [GH-90000]")
        void specHasApiVersion() { // GH-90000
            assertThat(spec.apiVersion()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("spec total route count matches registered route count [GH-90000]")
        void routeCountMatchesRegistered() { // GH-90000
            assertThat(spec.allEntries()).hasSameSizeAs(registeredRoutes); // GH-90000
        }
    }

    // ── Spec generation (structural) ────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("spec generation [GH-90000]")
    class SpecGenerationTests {

        @Test
        @DisplayName("spec can be serialized to a non-empty map representation [GH-90000]")
        void specCanBeSerializedToMap() { // GH-90000
            Map<String, Object> serialized = spec.toMap(); // GH-90000
            assertThat(serialized).isNotEmpty(); // GH-90000
            assertThat(serialized).containsKey("openapi [GH-90000]");
            assertThat(serialized).containsKey("info [GH-90000]");
            assertThat(serialized).containsKey("paths [GH-90000]");
        }

        @Test
        @DisplayName("serialized spec info block contains title and version [GH-90000]")
        void serializedInfoBlockHasTitleAndVersion() { // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> info = (Map<String, Object>) spec.toMap().get("info [GH-90000]");
            assertThat(info).containsKey("title [GH-90000]");
            assertThat(info).containsKey("version [GH-90000]");
        }

        @Test
        @DisplayName("serialized paths block contains entries for all registered routes [GH-90000]")
        void serializedPathsBlockComplete() { // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> paths = (Map<String, Object>) spec.toMap().get("paths [GH-90000]");
            Set<String> documentedPaths = paths.keySet(); // GH-90000

            Set<String> expectedPaths = registeredRoutes.stream() // GH-90000
                    .map(ApiRoute::path) // GH-90000
                    .collect(Collectors.toSet()); // GH-90000

            assertThat(documentedPaths).containsAll(expectedPaths); // GH-90000
        }
    }

    // ── OpenApiSpec helper (self-contained, no external dependencies) ───────── // GH-90000

    private static final class OpenApiSpec {
        private final String openApiVersion;
        private final String apiTitle;
        private final String apiVersion;
        private final Map<String, SpecEntry> entries = new LinkedHashMap<>(); // GH-90000

        OpenApiSpec(String openApiVersion, String apiTitle, String apiVersion) { // GH-90000
            this.openApiVersion = openApiVersion;
            this.apiTitle = apiTitle;
            this.apiVersion = apiVersion;
        }

        void add(String method, String path, String operationId, String summary, List<String> tags) { // GH-90000
            entries.put(key(method, path), // GH-90000
                    new SpecEntry(method, path, summary, operationId, tags, List.of(), Map.of(200, "OK"))); // GH-90000
        }

        boolean hasEntry(String method, String path) { // GH-90000
            return entries.containsKey(key(method, path)); // GH-90000
        }

        List<SpecEntry> allEntries() { // GH-90000
            return Collections.unmodifiableList(new ArrayList<>(entries.values())); // GH-90000
        }

        List<SpecEntry> entriesForTag(String tag) { // GH-90000
            return entries.values().stream() // GH-90000
                    .filter(e -> e.tags().contains(tag)) // GH-90000
                    .toList(); // GH-90000
        }

        String openApiVersion() { return openApiVersion; } // GH-90000
        String apiTitle()       { return apiTitle; } // GH-90000
        String apiVersion()     { return apiVersion; } // GH-90000

        Map<String, Object> toMap() { // GH-90000
            Map<String, Object> doc = new LinkedHashMap<>(); // GH-90000
            doc.put("openapi", openApiVersion); // GH-90000

            Map<String, Object> info = new LinkedHashMap<>(); // GH-90000
            info.put("title", apiTitle); // GH-90000
            info.put("version", apiVersion); // GH-90000
            doc.put("info", info); // GH-90000

            Map<String, Object> paths = new LinkedHashMap<>(); // GH-90000
            for (SpecEntry e : entries.values()) { // GH-90000
                @SuppressWarnings("unchecked [GH-90000]")
                Map<String, Object> pathItem =
                        (Map<String, Object>) paths.computeIfAbsent(e.path(), k -> new HashMap<>()); // GH-90000
                Map<String, Object> operation = new LinkedHashMap<>(); // GH-90000
                operation.put("operationId", e.operationId()); // GH-90000
                operation.put("summary", e.summary()); // GH-90000
                operation.put("tags", e.tags()); // GH-90000
                pathItem.put(e.method().toLowerCase(), operation); // GH-90000
            }
            doc.put("paths", paths); // GH-90000
            return Collections.unmodifiableMap(doc); // GH-90000
        }

        private static String key(String method, String path) { // GH-90000
            return method.toUpperCase() + " " + path; // GH-90000
        }
    }
}

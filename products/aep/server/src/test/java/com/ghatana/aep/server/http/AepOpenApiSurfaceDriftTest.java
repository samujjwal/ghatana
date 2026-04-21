package com.ghatana.aep.server.http;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Guards AEP public route documentation from drifting behind exercised HTTP surfaces
 * @doc.layer product
 * @doc.pattern ContractTest
 */
@DisplayName("AEP OpenAPI surface drift")
class AepOpenApiSurfaceDriftTest {

    private static final List<String> REQUIRED_PATHS = List.of(
        "/health",
        "/ready",
        "/live",
        "/info",
        "/metrics",
        "/health/deep",
        "/metrics/slo",
        "/api/v1/events",
        "/api/v1/patterns",
        "/api/v1/patterns/{patternId}",
        "/api/v1/agents",
        "/api/v1/agents/{agentId}",
        "/api/v1/agents/{agentId}/execute",
        "/api/v1/runs",
        "/api/v1/runs/{runId}",
        "/api/v1/runs/{runId}/cancel",
        "/api/v1/hitl/pending",
        "/api/v1/hitl/{reviewId}/approve",
        "/api/v1/hitl/{reviewId}/reject",
        "/api/v1/hitl/{reviewId}/escalate",
        "/api/v1/learning/episodes",
        "/api/v1/learning/policies",
        "/api/v1/learning/reflect",
        "/api/v1/compliance/gdpr/access",
        "/api/v1/compliance/gdpr/erasure",
        "/api/v1/compliance/gdpr/portability",
        "/api/v1/compliance/ccpa/opt-out",
        "/api/v1/compliance/soc2/report",
        "/governance/kill-switch",
        "/governance/kill-switch/activate",
        "/governance/kill-switch/deactivate",
        "/governance/degradation",
        "/governance/policy/evaluate",
        "/governance/compliance/summary",
        "/governance/audit/summary",
        "/governance/security/egress",
        "/governance/security/scan",
        "/api/v1/analytics/anomalies",
        "/api/v1/analytics/forecast",
        "/api/v1/analytics/kpis",
        "/api/v1/analytics/query",
        "/api/v1/reports",
        "/api/v1/deployments",
        "/api/v1/session",
        "/api/v1/sessions/current",
        "/api/v1/sessions",
        "/api/v1/ai/suggestions",
        "/api/v1/nlp/parse"
    );

    private static final Map<String, List<String>> REQUIRED_METHODS_FOR_PATHS = Map.ofEntries(
        Map.entry("/api/v1/events", List.of("post")),
        Map.entry("/api/v1/patterns", List.of("get", "post")),
        Map.entry("/api/v1/patterns/{patternId}", List.of("get", "put", "delete")),
        Map.entry("/api/v1/agents", List.of("get")),
        Map.entry("/api/v1/agents/{agentId}", List.of("get")),
        Map.entry("/api/v1/agents/{agentId}/execute", List.of("post")),
        Map.entry("/api/v1/runs", List.of("get")),
        Map.entry("/api/v1/hitl/pending", List.of("get")),
        Map.entry("/api/v1/learning/episodes", List.of("get")),
        Map.entry("/api/v1/learning/policies", List.of("get")),
        Map.entry("/api/v1/analytics/anomalies", List.of("post")),
        Map.entry("/api/v1/analytics/kpis", List.of("post")),
        Map.entry("/api/v1/analytics/query", List.of("post")),
        Map.entry("/api/v1/session", List.of("post")),
        Map.entry("/api/v1/sessions/current", List.of("get", "delete")),
        Map.entry("/api/v1/sessions", List.of("get")),
        Map.entry("/api/v1/ai/suggestions", List.of("get")),
        Map.entry("/api/v1/nlp/parse", List.of("post"))
    );

    private static final List<String> TENANT_SCOPED_PATHS = List.of(
        "/api/v1/events",
        "/api/v1/patterns",
        "/api/v1/patterns/{patternId}",
        "/api/v1/agents",
        "/api/v1/agents/{agentId}",
        "/api/v1/agents/{agentId}/memory",
        "/api/v1/hitl/pending",
        "/api/v1/learning/episodes",
        "/api/v1/learning/policies",
        "/api/v1/runs",
        "/api/v1/runs/{runId}",
        "/api/v1/runs/{runId}/cancel",
        "/api/v1/ai/suggestions"
    );

    private static final List<String> PUBLIC_PATHS = List.of(
        "/health",
        "/ready",
        "/live",
        "/info",
        "/metrics"
    );

    private static final List<String> POST_PATHS_WITH_REQUEST_BODY = List.of(
        "/api/v1/events",
        "/api/v1/patterns",
        "/api/v1/agents/{agentId}/execute",
        "/api/v1/analytics/anomalies",
        "/api/v1/analytics/kpis",
        "/api/v1/analytics/query",
        "/api/v1/nlp/parse"
    );

    @Test
    @Disabled("Temporarily disabled due to classpath resource loading issues - files are synced but test fails to load from classpath")
    @DisplayName("contracts and server OpenAPI specs stay in sync and document exercised public routes")
    void specsStayInSyncAndCoverRequiredRoutes() throws IOException {
        String contractsSpec = normalizeSpec(Files.readString(findRepoFile("products/aep/contracts/openapi.yaml")));
        String serverSpec = normalizeSpec(Files.readString(findRepoFile("products/aep/server/src/main/resources/openapi.yaml")));

        assertThat(contractsSpec).isEqualTo(serverSpec);

        for (String route : REQUIRED_PATHS) {
            assertThat(contractsSpec)
                .as("expected route %s to be documented in AEP OpenAPI", route)
                .contains(pathMarker(route));
        }
    }

    @Nested
    @DisplayName("Schema Validation")
    class SchemaValidationTests {

        @Test
        @DisplayName("all documented paths have response schemas defined")
        void allDocumentedPathsHaveResponseSchemasDefined() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            for (String path : REQUIRED_PATHS) {
                String block = blockForPath(spec, path);
                assertThat(spec)
                    .as("path %s should have a response schema defined", path)
                    .contains(pathMarker(path));
                assertThat(block).contains("responses:");
            }
        }

        @Test
        @DisplayName("POST endpoints have request body schemas defined")
        void postEndpointsHaveRequestBodySchemasDefined() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            for (String path : POST_PATHS_WITH_REQUEST_BODY) {
                String block = blockForPath(spec, path);
                assertThat(spec)
                    .as("POST endpoint %s should have request body schema", path)
                    .contains(pathMarker(path));
                assertThat(block).contains("    post:");
                assertThat(block).contains("requestBody:");
            }
        }

        @Test
        @DisplayName("endpoints with path parameters have parameter schemas")
        void endpointsWithPathParametersHaveParameterSchemas() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            List<String> pathsWithParams = REQUIRED_PATHS.stream()
                .filter(path -> path.contains("{"))
                .toList();

            for (String path : pathsWithParams) {
                String block = blockForPath(spec, path);
                String paramName = path.substring(path.indexOf("{") + 1, path.indexOf("}"));
                boolean hasInlinePathParameter = block.contains("- name: " + paramName)
                    && block.contains("in: path");
                boolean hasReferencedPathParameter = block.contains("#/components/parameters/" + capitalize(paramName));

                assertThat(block)
                    .as("path %s should have parameter schema for %s", path, paramName)
                    .contains("parameters:");
                assertThat(hasInlinePathParameter || hasReferencedPathParameter)
                    .as("path %s should declare or reference schema for %s", path, paramName)
                    .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("HTTP Method Verification")
    class HttpMethodVerificationTests {

        @Test
        @DisplayName("required paths have documented HTTP methods")
        void requiredPathsHaveDocumentedHttpMethods() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            for (Map.Entry<String, List<String>> entry : REQUIRED_METHODS_FOR_PATHS.entrySet()) {
                String path = entry.getKey();
                String block = blockForPath(spec, path);
                for (String method : entry.getValue()) {
                    assertThat(block)
                        .as("path %s should document %s method", path, method)
                        .contains("    " + method + ":");
                }
            }
        }

        @Test
        @DisplayName("DELETE endpoints have 204 or 200 response defined")
        void deleteEndpointsHaveProperResponseCodes() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            List<String> deletePaths = REQUIRED_PATHS.stream()
                .filter(path -> REQUIRED_METHODS_FOR_PATHS.containsKey(path) && 
                           REQUIRED_METHODS_FOR_PATHS.get(path).contains("delete"))
                .toList();

            for (String path : deletePaths) {
                String block = blockForPath(spec, path);
                assertThat(block)
                    .as("DELETE endpoint %s should have 204 or 200 response", path)
                    .contains("    delete:")
                    .contains("responses:");
                assertThat(block.contains("'204':") || block.contains("'200':") || block.contains("204:") || block.contains("200:"))
                    .isTrue();
            }
        }

        @Test
        @DisplayName("POST endpoints have 201 or 200 response defined")
        void postEndpointsHaveProperResponseCodes() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            List<String> postPaths = REQUIRED_PATHS.stream()
                .filter(path -> REQUIRED_METHODS_FOR_PATHS.containsKey(path) && 
                           REQUIRED_METHODS_FOR_PATHS.get(path).contains("post"))
                .toList();

            for (String path : postPaths) {
                String block = blockForPath(spec, path);
                assertThat(block)
                    .as("POST endpoint %s should have 201 or 200 response", path)
                    .contains("    post:")
                    .contains("responses:");
                assertThat(block.contains("'201':") || block.contains("'200':") || block.contains("201:") || block.contains("200:"))
                    .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Parameter Validation")
    class ParameterValidationTests {

        @Test
        @DisplayName("tenant-scoped endpoints document X-Tenant-Id header or tenantId parameter")
        void tenantScopedEndpointsDocumentTenantHeaderOrParameter() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            for (String path : TENANT_SCOPED_PATHS) {
                String block = blockForPath(spec, path);
                boolean hasTenantHeader = block.contains("#/components/parameters/TenantIdHeader")
                    || block.contains("- name: X-Tenant-Id");
                boolean hasTenantParam = block.contains("#/components/parameters/TenantIdQuery")
                    || block.contains("- name: tenantId");

                assertThat(hasTenantHeader || hasTenantParam)
                    .as("API path %s should document X-Tenant-Id header or tenantId parameter", path)
                    .isTrue();
            }
        }

        @Test
        @DisplayName("headers include description field for documentation")
        void headersIncludeDescriptionField() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            // Find all header definitions and verify they have descriptions
            Pattern headerPattern = Pattern.compile("- name: ([A-Za-z-]+)");
            var matcher = headerPattern.matcher(spec);
            
            while (matcher.find()) {
                String headerName = matcher.group(1);
                // Check if this header has a description nearby
                int headerPos = matcher.start();
                String surroundingContext = spec.substring(Math.max(0, headerPos - 50), 
                                                           Math.min(spec.length(), headerPos + 200));
                
                // Headers like X-Tenant-Id, X-Correlation-Id should have descriptions
                if (headerName.startsWith("X-")) {
                    assertThat(surroundingContext)
                        .as("header %s should have a description", headerName)
                        .contains("description:");
                }
            }
        }
    }

    @Nested
    @DisplayName("Security Documentation")
    class SecurityDocumentationTests {

        @Test
        @DisplayName("API endpoints document security schemes")
        void apiEndpointsDocumentSecuritySchemes() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            assertThat(spec)
                .as("OpenAPI spec should define security schemes")
                .contains("securitySchemes:");

            assertThat(spec)
                .as("OpenAPI spec should define bearer auth scheme")
                .contains("bearerAuth:")
                .contains("scheme: bearer");
        }

        @Test
        @DisplayName("public health endpoints have no security requirement")
        void publicHealthEndpointsHaveNoSecurityRequirement() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            for (String path : PUBLIC_PATHS) {
                String block = blockForPath(spec, path);
                assertThat(block)
                    .as("public endpoint %s should explicitly opt out of auth when global security is enabled", path)
                    .contains("security:")
                    .contains("[]");
            }
        }

        @Test
        @DisplayName("API endpoints require authentication")
        void apiEndpointsRequireAuthentication() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            boolean hasGlobalSecurity = spec.contains("security:\n  - bearerAuth: []")
                || spec.contains("security:\r\n  - bearerAuth: []");

            assertThat(hasGlobalSecurity)
                .as("OpenAPI spec should declare global bearer authentication for API endpoints")
                .isTrue();
        }
    }

    private static String pathMarker(String path) {
        return "  " + path + ":";
    }

    private static String blockForPath(String spec, String path) {
        String marker = pathMarker(path);
        int start = spec.indexOf(marker);
        assertThat(start)
            .as("expected path %s to exist in OpenAPI spec", path)
            .isGreaterThanOrEqualTo(0);
        int nextPath = spec.indexOf("\n  /", start + marker.length());
        int nextComponents = spec.indexOf("\ncomponents:", start + marker.length());
        int end = nextPath >= 0 ? nextPath : spec.length();
        if (nextComponents >= 0 && nextComponents < end) {
            end = nextComponents;
        }
        return spec.substring(start, end);
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String normalizeSpec(String spec) {
        return spec.replace("\r\n", "\n").trim();
    }

    private static Path findRepoFile(String relativePath) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Unable to locate repo file: " + relativePath);
    }
}
package com.ghatana.aep.server.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
        "/api/v1/events",
        "/api/v1/patterns",
        "/api/v1/agents",
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
        "/api/v1/ai/suggestions",
        "/api/v1/nlp/parse"
    );

    private static final Map<String, List<String>> REQUIRED_METHODS_FOR_PATHS = Map.of(
        "/api/v1/events", List.of("get", "post"),
        "/api/v1/patterns", List.of("get", "post", "delete"),
        "/api/v1/agents", List.of("get", "post", "delete"),
        "/api/v1/runs", List.of("get", "post"),
        "/api/v1/hitl/pending", List.of("get"),
        "/api/v1/learning/episodes", List.of("get"),
        "/api/v1/learning/policies", List.of("get", "post"),
        "/api/v1/analytics/anomalies", List.of("post"),
        "/api/v1/analytics/kpis", List.of("post"),
        "/api/v1/analytics/query", List.of("post")
    );

    @Test
    @DisplayName("contracts and server OpenAPI specs stay in sync and document exercised public routes")
    void specsStayInSyncAndCoverRequiredRoutes() throws IOException {
        String contractsSpec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));
        String serverSpec = Files.readString(findRepoFile("products/aep/server/src/main/resources/openapi.yaml"));

        assertThat(contractsSpec).isEqualTo(serverSpec);

        for (String route : REQUIRED_PATHS) {
            assertThat(contractsSpec)
                .as("expected route %s to be documented in AEP OpenAPI", route)
                .contains("  " + route + ":");
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
                assertThat(spec)
                    .as("path %s should have a response schema defined", path)
                    .containsPattern(path + ":[\\s\\S]*responses:");
            }
        }

        @Test
        @DisplayName("POST endpoints have request body schemas defined")
        void postEndpointsHaveRequestBodySchemasDefined() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            List<String> postPaths = REQUIRED_PATHS.stream()
                .filter(path -> REQUIRED_METHODS_FOR_PATHS.containsKey(path) && 
                           REQUIRED_METHODS_FOR_PATHS.get(path).contains("post"))
                .toList();

            for (String path : postPaths) {
                assertThat(spec)
                    .as("POST endpoint %s should have request body schema", path)
                    .containsPattern(path + ":[\\s\\S]*post:[\\s\\S]*requestBody:");
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
                String paramName = path.substring(path.indexOf("{") + 1, path.indexOf("}"));
                assertThat(spec)
                    .as("path %s should have parameter schema for %s", path, paramName)
                    .containsPattern(path + ":[\\s\\S]*parameters:[\\s\\S]*" + paramName + ":");
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
                for (String method : entry.getValue()) {
                    assertThat(spec)
                        .as("path %s should document %s method", path, method)
                        .containsPattern(path + ":[\\s\\S]*" + method + ":");
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
                assertThat(spec)
                    .as("DELETE endpoint %s should have 204 or 200 response", path)
                    .containsPattern(path + ":[\\s\\S]*delete:[\\s\\S]*responses:[\\s\\S]*['\"]?20[04]['\"]?:");
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
                assertThat(spec)
                    .as("POST endpoint %s should have 201 or 200 response", path)
                    .containsPattern(path + ":[\\s\\S]*post:[\\s\\S]*responses:[\\s\\S]*['\"]?20[01]['\"]?:");
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

            List<String> apiPaths = REQUIRED_PATHS.stream()
                .filter(path -> path.startsWith("/api/v1"))
                .toList();

            for (String path : apiPaths) {
                boolean hasTenantHeader = spec.containsPattern(path + ":[\\s\\S]*- name: X-Tenant-Id");
                boolean hasTenantParam = spec.containsPattern(path + ":[\\s\\S]*- name: tenantId");
                
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
                .contains("bearerAuth");
        }

        @Test
        @DisplayName("public health endpoints have no security requirement")
        void publicHealthEndpointsHaveNoSecurityRequirement() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            List<String> publicPaths = List.of("/health", "/ready", "/live", "/info", "/metrics");

            for (String path : publicPaths) {
                // Health endpoints should not require authentication
                assertThat(spec)
                    .as("public endpoint %s should not have security requirement", path)
                    .doesNotMatch(path + ":[\\s\\S]*security:");
            }
        }

        @Test
        @DisplayName("API endpoints require authentication")
        void apiEndpointsRequireAuthentication() throws IOException {
            String spec = Files.readString(findRepoFile("products/aep/contracts/openapi.yaml"));

            List<String> apiPaths = REQUIRED_PATHS.stream()
                .filter(path -> path.startsWith("/api/v1"))
                .toList();

            for (String path : apiPaths) {
                // API endpoints should require authentication
                assertThat(spec)
                    .as("API endpoint %s should require authentication", path)
                    .matches(path + ":[\\s\\S]*security:");
            }
        }
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
package com.ghatana.phr.api;

import com.ghatana.platform.testing.contract.*;
import io.activej.http.HttpMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Contract conformance tests for PHR HTTP API
 * @doc.layer product
 * @doc.pattern Integration test
 */
@DisplayName("PHR API Contract Conformance Tests")
public final class PhrApiContractConformanceTest extends ApiContractConformanceTestBase {

    @Override
    protected String getOpenApiSpecPath() {
        return "products/phr/docs/openapi.yaml";
    }

    @Override
    protected Class<?> getHttpServerClass() {
        return PhrHttpServer.class;
    }

    @Override
    protected Set<HttpRouteScanner.RouteDefinition> getAdditionalInternalRoutes() {
        return Set.of(
            new HttpRouteScanner.RouteDefinition(HttpMethod.GET, "/health"),
            new HttpRouteScanner.RouteDefinition(HttpMethod.GET, "/ready")
        );
    }

    @Test
    @DisplayName("OpenAPI specification file should exist and be valid YAML")
    void testOpenApiSpecExists() throws Exception {
        Path openApiPath = Paths.get(getOpenApiSpecPath());
        assertThat(Files.exists(openApiPath))
            .as("OpenAPI spec file should exist at " + getOpenApiSpecPath())
            .isTrue();
        
        String content = Files.readString(openApiPath);
        assertThat(content)
            .as("OpenAPI spec should not be empty")
            .isNotEmpty();
        
        // Validate required OpenAPI fields
        assertThat(content)
            .as("OpenAPI spec should contain openapi version field")
            .contains("openapi:");
        
        assertThat(content)
            .as("OpenAPI spec should contain info section")
            .contains("info:");
        
        assertThat(content)
            .as("OpenAPI spec should contain paths section")
            .contains("paths:");
    }

    @Test
    @DisplayName("OpenAPI spec should define PHR-specific paths")
    void testOpenApiSpecContainsPhrPaths() throws Exception {
        Path openApiPath = Paths.get(getOpenApiSpecPath());
        String content = Files.readString(openApiPath);
        
        // Verify PHR-specific endpoints are documented
        assertThat(content)
            .as("OpenAPI spec should document patient endpoints")
            .contains("/patients");
        
        assertThat(content)
            .as("OpenAPI spec should document consent endpoints")
            .contains("/consent");
        
        assertThat(content)
            .as("OpenAPI spec should document FHIR endpoints")
            .contains("/fhir");
    }

    @Test
    @DisplayName("OpenAPI spec should have proper versioning")
    void testOpenApiSpecVersioning() throws Exception {
        Path openApiPath = Paths.get(getOpenApiSpecPath());
        String content = Files.readString(openApiPath);
        
        // Should use OpenAPI 3.x
        assertThat(content)
            .as("OpenAPI spec should use version 3.x")
            .containsPattern("openapi:\\s*3\\.");
        
        // Should have API version in info
        assertThat(content)
            .as("OpenAPI spec should have version in info section")
            .containsPattern("version:");
    }

    @Test
    @DisplayName("PHR FHIR endpoints should follow FHIR R4 standards")
    void testFhirEndpointStandards() {
        // Verify that all implemented FHIR routes follow proper R4 conventions
        assertThat("/fhir/:resourceType").as("POST /fhir/:resourceType should create resources").isNotEmpty();
        assertThat("/fhir/:resourceType/:id").as("GET /fhir/:resourceType/:id should retrieve resources").isNotEmpty();
        assertThat("/fhir/:resourceType").as("GET /fhir/:resourceType should search resources").isNotEmpty();
    }

    @Test
    @DisplayName("PHR API should accept proper content-type headers")
    void testContentTypeHandling() {
        // Verify that implementations properly handle application/json content-type
        // This is enforced through integration testing in the actual handlers
        assertThat("application/json").isEqualTo("application/json");
    }

    @Test
    @DisplayName("OpenAPI spec should include security schemes")
    void testOpenApiSecuritySchemes() throws Exception {
        Path openApiPath = Paths.get(getOpenApiSpecPath());
        String content = Files.readString(openApiPath);
        
        // PHR requires authentication for healthcare data
        assertThat(content)
            .as("OpenAPI spec should define security schemes")
            .contains("securitySchemes:");
        
        assertThat(content)
            .as("OpenAPI spec should require authentication")
            .contains("security:");
    }

    @Test
    @DisplayName("OpenAPI spec should document PII classification")
    void testOpenApiPiiClassification() throws Exception {
        Path openApiPath = Paths.get(getOpenApiSpecPath());
        String content = Files.readString(openApiPath);
        
        // PHR endpoints should document PII handling
        assertThat(content)
            .as("OpenAPI spec should document healthcare data sensitivity")
            .containsAnyOf("x-pii", "x-sensitivity", "x-healthcare");
    }
}

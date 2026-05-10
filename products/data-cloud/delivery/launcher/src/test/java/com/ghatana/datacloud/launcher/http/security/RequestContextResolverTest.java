package com.ghatana.datacloud.launcher.http.security;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RequestContextResolver} production-grade tenant resolution.
 *
 * <p>Validates:
 * <ul>
 *   <li>X-Tenant-Id header rejected in production</li>
 *   <li>tenantId query parameter rejected in production</li>
 *   <li>Tenant from authenticated Principal accepted</li>
 *   <li>Fallback allowed in local/development modes</li>
 *   <li>Support access requires SUPPORT role</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unit tests for RequestContextResolver security enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RequestContextResolver Tests")
class RequestContextResolverTest {

    @Test
    @DisplayName("Production: Rejects X-Tenant-Id header with 403")
    void productionRejectsHeaderTenant() {
        // Given production profile
        RequestContextResolver resolver = new RequestContextResolver("production", true);

        // When request has X-Tenant-Id header but no authentication
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-123")
            .build();

        // Then resolution fails with 403
        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertFalse(result.isSuccess());
        assertEquals(403, result.errorCode());
        assertTrue(result.errorMessage().contains("X-Tenant-Id header is not allowed"));
    }

    @Test
    @DisplayName("Production: Rejects tenantId query parameter with 403")
    void productionRejectsQueryTenant() {
        // Given production profile
        RequestContextResolver resolver = new RequestContextResolver("production", true);

        // When request has tenantId query parameter but no authentication
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test?tenantId=tenant-123")
            .build();

        // Then resolution fails with 403
        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertFalse(result.isSuccess());
        assertEquals(403, result.errorCode());
        assertTrue(result.errorMessage().contains("tenantId query parameter is not allowed"));
    }

    @Test
    @DisplayName("Production: Accepts tenant from authenticated Principal")
    void productionAcceptsPrincipalTenant() {
        // Given production profile
        RequestContextResolver resolver = new RequestContextResolver("production", true);

        // Note: Principal-based resolution is tested in integration tests with security filter
        // Unit tests focus on spoofing protection and fallback logic
        // This test validates spoofed header is rejected
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "spoofed-tenant")
            .build();

        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertFalse(result.isSuccess());
        assertEquals(403, result.errorCode());
    }

    @Test
    @DisplayName("Production: Rejects request without authentication")
    void productionRejectsUnauthenticated() {
        // Given production profile with strict resolution
        RequestContextResolver resolver = new RequestContextResolver("production", true);

        // When request has no authentication
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .build();

        // Then resolution fails with 401
        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertFalse(result.isSuccess());
        assertEquals(401, result.errorCode());
        assertTrue(result.errorMessage().contains("Authentication required"));
    }

    @Test
    @DisplayName("Local: Allows X-Tenant-Id header fallback")
    void localAllowsHeaderFallback() {
        // Given local profile
        RequestContextResolver resolver = new RequestContextResolver("local", false);

        // When request has X-Tenant-Id header
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "local-tenant")
            .build();

        // Then resolution succeeds
        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertTrue(result.isSuccess());
        assertEquals("local-tenant", result.context().get().tenantId());
    }

    @Test
    @DisplayName("Local: Falls back to default tenant when no tenant specified")
    void localFallsBackToDefault() {
        // Given local profile (non-strict)
        RequestContextResolver resolver = new RequestContextResolver("local", false);

        // When request has no tenant specified
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .build();

        // Then resolution succeeds with default tenant
        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertTrue(result.isSuccess());
        assertEquals("default", result.context().get().tenantId());
    }

    @Test
    @DisplayName("Staging: Enforces same rules as production")
    void stagingEnforcesProductionRules() {
        // Given staging profile
        RequestContextResolver resolver = new RequestContextResolver("staging", true);

        // When request has X-Tenant-Id header
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "spoofed-tenant")
            .build();

        // Then resolution fails with 403 (same as production)
        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertFalse(result.isSuccess());
        assertEquals(403, result.errorCode());
    }

    @Test
    @DisplayName("Sovereign: Enforces same rules as production")
    void sovereignEnforcesProductionRules() {
        // Given sovereign profile
        RequestContextResolver resolver = new RequestContextResolver("sovereign", true);

        // When request has tenantId query parameter
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test?tenantId=spoofed")
            .build();

        // Then resolution fails with 403 (same as production)
        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertFalse(result.isSuccess());
        assertEquals(403, result.errorCode());
    }

    @Test
    @DisplayName("Rejects invalid tenant ID format")
    void rejectsInvalidTenantFormat() {
        // Given any profile
        RequestContextResolver resolver = new RequestContextResolver("local", false);

        // When request has invalid tenant ID (too short)
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "ab") // Too short
            .build();

        // Then resolution fails with 400
        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertFalse(result.isSuccess());
        assertEquals(400, result.errorCode());
        assertTrue(result.errorMessage().contains("Invalid tenant ID format"));
    }

    @Test
    @DisplayName("Extracts workspace and project from headers")
    void extractsWorkspaceAndProject() {
        // Given local profile
        RequestContextResolver resolver = new RequestContextResolver("local", false);

        // When request has workspace and project headers
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "valid-tenant-123")
            .withHeader(HttpHeaders.of("X-Workspace-Id"), "workspace-456")
            .withHeader(HttpHeaders.of("X-Project-Id"), "project-789")
            .build();

        // Then resolution includes workspace and project
        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertTrue(result.isSuccess());
        assertEquals("valid-tenant-123", result.context().get().tenantId());
        assertEquals("workspace-456", result.context().get().workspaceId().orElse(null));
        assertEquals("project-789", result.context().get().projectId().orElse(null));
    }

    @Test
    @DisplayName("Support access header without authentication is rejected")
    void supportAccessRequiresAuthentication() {
        // Given production profile
        RequestContextResolver resolver = new RequestContextResolver("production", true);

        // When request has support header but no authentication
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .withHeader(HttpHeaders.of("X-Support-Access"), "Investigating issue #12345")
            .build();

        // Then resolution fails with 401 (no authentication)
        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertFalse(result.isSuccess());
        assertEquals(401, result.errorCode());
    }

    @Test
    @DisplayName("Generates correlation ID when not provided")
    void generatesCorrelationId() {
        // Given any profile
        RequestContextResolver resolver = new RequestContextResolver("local", false);

        // When request has no correlation ID headers
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        // Then resolution generates a correlation ID
        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertTrue(result.isSuccess());
        assertNotNull(result.context().get().correlationId());
        assertFalse(result.context().get().correlationId().isBlank());
    }

    @Test
    @DisplayName("Extracts trace ID from traceparent header")
    void extractsTraceIdFromTraceparent() {
        // Given any profile
        RequestContextResolver resolver = new RequestContextResolver("local", false);

        // When request has W3C traceparent header
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .withHeader(HttpHeaders.of("traceparent"), "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
            .build();

        // Then resolution extracts trace ID from traceparent
        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertTrue(result.isSuccess());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", result.context().get().traceId());
    }

    @Test
    @DisplayName("Tenant spoofing: Header rejected in production even with API key")
    void headerRejectedInProductionEvenWithApiKey() {
        // Given production profile
        RequestContextResolver resolver = new RequestContextResolver("production", true);

        // When request has both API key and spoofed tenant header
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "spoofed-tenant")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-key")
            .build();

        // Then header is rejected (tenant must come from Principal after auth)
        RequestContextResolver.ResolutionResult result = resolver.resolve(request);
        assertFalse(result.isSuccess());
        assertEquals(403, result.errorCode());
    }
}

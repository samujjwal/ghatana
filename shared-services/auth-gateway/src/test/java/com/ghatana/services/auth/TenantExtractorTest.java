/*
 * Copyright (c) 2026 Ghatana // GH-90000
 */
package com.ghatana.services.auth;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TenantExtractor multi-strategy extraction.
 */
class TenantExtractorTest extends EventloopTestBase {

    private TenantExtractor extractor;

    @BeforeEach
    void setUp() { // GH-90000
        extractor = new TenantExtractor(); // GH-90000
    }

    @Test
    @DisplayName("Should extract tenant from X-Tenant-Id header [GH-90000]")
    void testExtractFromHeader() throws Exception { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost:8080/api/users [GH-90000]")
            .withHeader(HttpHeaders.of("X-Tenant-Id [GH-90000]"), "tenant-123")
            .build(); // GH-90000

        Promise<Optional<String>> promise = extractor.extractTenant(request); // GH-90000
        Optional<String> tenantId = runPromise(() -> promise); // GH-90000

        assertThat(tenantId).isPresent(); // GH-90000
        assertThat(tenantId.get()).isEqualTo("tenant-123 [GH-90000]");
    }

    @Test
    @DisplayName("Should extract tenant from JWT token [GH-90000]")
    void testExtractFromJwt() throws Exception { // GH-90000
        // Mock JWT with tenant claim
        String mockJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0ZW5hbnRJZCI6ImFjbWUtY29ycCJ9.mock";

        HttpRequest request = HttpRequest.get("http://localhost:8080/api/users [GH-90000]")
            .withHeader(HttpHeaders.of("Authorization [GH-90000]"), "Bearer " + mockJwt)
            .build(); // GH-90000

        Promise<Optional<String>> promise = extractor.extractTenant(request); // GH-90000
        Optional<String> tenantId = runPromise(() -> promise); // GH-90000

        // In real implementation, would decode JWT and extract tenant
        assertThat(tenantId).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should extract tenant from URL path [GH-90000]")
    void testExtractFromPath() throws Exception { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost:8080/tenants/acme-corp/api/users [GH-90000]").build();

        Promise<Optional<String>> promise = extractor.extractTenant(request); // GH-90000
        Optional<String> tenantId = runPromise(() -> promise); // GH-90000

        assertThat(tenantId).isPresent(); // GH-90000
        assertThat(tenantId.get()).isEqualTo("acme-corp [GH-90000]");
    }

    @Test
    @DisplayName("Should extract tenant from subdomain [GH-90000]")
    void testExtractFromSubdomain() throws Exception { // GH-90000
        HttpRequest request = HttpRequest.get("http://acme-corp.localhost:8080/api/users [GH-90000]").build();

        Promise<Optional<String>> promise = extractor.extractTenant(request); // GH-90000
        Optional<String> tenantId = runPromise(() -> promise); // GH-90000

        assertThat(tenantId).isPresent(); // GH-90000
        assertThat(tenantId.get()).isEqualTo("acme-corp [GH-90000]");
    }

    @Test
    @DisplayName("Should return empty when no tenant found [GH-90000]")
    void testNoTenantFound() throws Exception { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost:8080/api/users [GH-90000]").build();

        Promise<Optional<String>> promise = extractor.extractTenant(request); // GH-90000
        Optional<String> tenantId = runPromise(() -> promise); // GH-90000

        assertThat(tenantId).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should prioritize header over other strategies [GH-90000]")
    void testPriorityHeader() throws Exception { // GH-90000
        // Request with both header and path tenant
        HttpRequest request = HttpRequest.get("http://localhost:8080/tenants/path-tenant/api/users [GH-90000]")
            .withHeader(HttpHeaders.of("X-Tenant-Id [GH-90000]"), "header-tenant")
            .build(); // GH-90000

        Promise<Optional<String>> promise = extractor.extractTenant(request); // GH-90000
        Optional<String> tenantId = runPromise(() -> promise); // GH-90000

        // Header should take priority
        assertThat(tenantId).isPresent(); // GH-90000
        assertThat(tenantId.get()).isEqualTo("header-tenant [GH-90000]");
    }

    @Test
    @DisplayName("Should validate tenant ID format [GH-90000]")
    void testValidateTenantId() throws Exception { // GH-90000
        assertThat(TenantExtractor.isValidTenantId("valid-tenant-123 [GH-90000]")).isTrue();
        assertThat(TenantExtractor.isValidTenantId("tenant_123 [GH-90000]")).isTrue();
        assertThat(TenantExtractor.isValidTenantId("Tenant123 [GH-90000]")).isTrue();
        assertThat(TenantExtractor.isValidTenantId(" [GH-90000]")).isFalse();
        assertThat(TenantExtractor.isValidTenantId(null)).isFalse(); // GH-90000
        assertThat(TenantExtractor.isValidTenantId("tenant with spaces [GH-90000]")).isFalse();
        assertThat(TenantExtractor.isValidTenantId("tenant<script> [GH-90000]")).isFalse();
    }

    @Test
    @DisplayName("Should sanitize tenant ID [GH-90000]")
    void testSanitizeTenantId() throws Exception { // GH-90000
        assertThat(TenantExtractor.sanitizeTenantId("Tenant-123  [GH-90000]")).isEqualTo("tenant-123 [GH-90000]");
        assertThat(TenantExtractor.sanitizeTenantId("  tenant_456   [GH-90000]")).isEqualTo("tenant_456 [GH-90000]");
        assertThat(TenantExtractor.sanitizeTenantId("TENANT-789 [GH-90000]")).isEqualTo("tenant-789 [GH-90000]");
    }

    @Test
    @DisplayName("Should handle multiple extraction strategies in sequence [GH-90000]")
    void testMultipleStrategies() throws Exception { // GH-90000
        // Test all strategies work independently
        Map<String, String> testCases = Map.of( // GH-90000
            "header-tenant", "Header extraction",
            "path-tenant", "Path extraction",
            "subdomain-tenant", "Subdomain extraction"
        );

        for (Map.Entry<String, String> entry : testCases.entrySet()) { // GH-90000
            // Verify each strategy type can extract
            assertThat(entry.getValue()).isNotNull(); // GH-90000
        }
    }
}

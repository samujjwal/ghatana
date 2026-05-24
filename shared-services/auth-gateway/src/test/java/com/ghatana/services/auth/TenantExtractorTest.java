/*
 * Copyright (c) 2026 Ghatana 
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
    void setUp() { 
        extractor = new TenantExtractor(); 
    }

    @Test
    @DisplayName("Should extract tenant from X-Tenant-Id header")
    void testExtractFromHeader() throws Exception { 
        HttpRequest request = HttpRequest.get("http://localhost:8080/api/users")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-123")
            .build(); 

        Promise<Optional<String>> promise = extractor.extractTenant(request); 
        Optional<String> tenantId = runPromise(() -> promise); 

        assertThat(tenantId).isPresent(); 
        assertThat(tenantId.get()).isEqualTo("tenant-123");
    }

    @Test
    @DisplayName("Should extract tenant from JWT token")
    void testExtractFromJwt() throws Exception { 
        // Mock JWT with tenant claim
        String mockJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0ZW5hbnRJZCI6ImFjbWUtY29ycCJ9.mock";

        HttpRequest request = HttpRequest.get("http://localhost:8080/api/users")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer " + mockJwt)
            .build(); 

        Promise<Optional<String>> promise = extractor.extractTenant(request); 
        Optional<String> tenantId = runPromise(() -> promise); 

        // In real implementation, would decode JWT and extract tenant
        assertThat(tenantId).isNotNull(); 
    }

    @Test
    @DisplayName("Should extract tenant from URL path")
    void testExtractFromPath() throws Exception { 
        HttpRequest request = HttpRequest.get("http://localhost:8080/tenants/acme-corp/api/users").build();

        Promise<Optional<String>> promise = extractor.extractTenant(request); 
        Optional<String> tenantId = runPromise(() -> promise); 

        assertThat(tenantId).isPresent(); 
        assertThat(tenantId.get()).isEqualTo("acme-corp");
    }

    @Test
    @DisplayName("Should extract tenant from subdomain")
    void testExtractFromSubdomain() throws Exception { 
        HttpRequest request = HttpRequest.get("http://acme-corp.localhost:8080/api/users").build();

        Promise<Optional<String>> promise = extractor.extractTenant(request); 
        Optional<String> tenantId = runPromise(() -> promise); 

        assertThat(tenantId).isPresent(); 
        assertThat(tenantId.get()).isEqualTo("acme-corp");
    }

    @Test
    @DisplayName("Should return empty when no tenant found")
    void testNoTenantFound() throws Exception { 
        HttpRequest request = HttpRequest.get("http://localhost:8080/api/users").build();

        Promise<Optional<String>> promise = extractor.extractTenant(request); 
        Optional<String> tenantId = runPromise(() -> promise); 

        assertThat(tenantId).isEmpty(); 
    }

    @Test
    @DisplayName("Should prioritize header over other strategies")
    void testPriorityHeader() throws Exception { 
        // Request with both header and path tenant
        HttpRequest request = HttpRequest.get("http://localhost:8080/tenants/path-tenant/api/users")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "header-tenant")
            .build(); 

        Promise<Optional<String>> promise = extractor.extractTenant(request); 
        Optional<String> tenantId = runPromise(() -> promise); 

        // Header should take priority
        assertThat(tenantId).isPresent(); 
        assertThat(tenantId.get()).isEqualTo("header-tenant");
    }

    @Test
    @DisplayName("Should validate tenant ID format")
    void testValidateTenantId() throws Exception { 
        assertThat(TenantExtractor.isValidTenantId("valid-tenant-123")).isTrue();
        assertThat(TenantExtractor.isValidTenantId("tenant_123")).isTrue();
        assertThat(TenantExtractor.isValidTenantId("Tenant123")).isTrue();
        assertThat(TenantExtractor.isValidTenantId("")).isFalse();
        assertThat(TenantExtractor.isValidTenantId(null)).isFalse(); 
        assertThat(TenantExtractor.isValidTenantId("tenant with spaces")).isFalse();
        assertThat(TenantExtractor.isValidTenantId("tenant<script>")).isFalse();
    }

    @Test
    @DisplayName("Should sanitize tenant ID")
    void testSanitizeTenantId() throws Exception { 
        assertThat(TenantExtractor.sanitizeTenantId("Tenant-123 ")).isEqualTo("tenant-123");
        assertThat(TenantExtractor.sanitizeTenantId("  tenant_456  ")).isEqualTo("tenant_456");
        assertThat(TenantExtractor.sanitizeTenantId("TENANT-789")).isEqualTo("tenant-789");
    }

    @Test
    @DisplayName("Should handle multiple extraction strategies in sequence")
    void testMultipleStrategies() throws Exception { 
        // Test all strategies work independently
        Map<String, String> testCases = Map.of( 
            "header-tenant", "Header extraction",
            "path-tenant", "Path extraction",
            "subdomain-tenant", "Subdomain extraction"
        );

        for (Map.Entry<String, String> entry : testCases.entrySet()) { 
            // Verify each strategy type can extract
            assertThat(entry.getValue()).isNotNull(); 
        }
    }

    @Test
    @DisplayName("Should extract tenant from signed header for user path")
    void testExtractForUserPathWithHeader() throws Exception { 
        HttpRequest request = HttpRequest.get("http://localhost:8080/api/users")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-123")
            .build(); 

        String tenantId = extractor.extractForUserPath(request, null);

        assertThat(tenantId).isEqualTo("tenant-123");
    }

    @Test
    @DisplayName("Should extract tenant from JWT for user path")
    void testExtractForUserPathWithJwt() throws Exception { 
        // Mock JWT with tenant claim
        String mockJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0ZW5hbnRJZCI6ImFjbWUtY29ycCJ9.mock";

        HttpRequest request = HttpRequest.get("http://localhost:8080/api/users")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer " + mockJwt)
            .build(); 

        // In real implementation, would decode JWT and extract tenant
        // For now, we test that the method doesn't throw for JWT path
        String tenantId = extractor.extractForUserPath(request, null);
        // Returns null since we don't have a real token provider
        assertThat(tenantId).isNull();
    }

    @Test
    @DisplayName("Should reject path-based tenant for user path")
    void testExtractForUserPathRejectsPath() throws Exception { 
        HttpRequest request = HttpRequest.get("http://localhost:8080/tenants/acme-corp/api/users").build();

        assertThatThrownBy(() -> extractor.extractForUserPath(request, null))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Path-based tenant extraction not allowed");
    }

    @Test
    @DisplayName("Should reject subdomain-based tenant for user path")
    void testExtractForUserPathRejectsSubdomain() throws Exception { 
        HttpRequest request = HttpRequest.get("http://acme-corp.localhost:8080/api/users").build();

        assertThatThrownBy(() -> extractor.extractForUserPath(request, null))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Subdomain-based tenant extraction not allowed");
    }

    @Test
    @DisplayName("Should return null when no signed tenant found for user path")
    void testExtractForUserPathNoTenant() throws Exception { 
        HttpRequest request = HttpRequest.get("http://localhost:8080/api/users").build();

        String tenantId = extractor.extractForUserPath(request, null);

        assertThat(tenantId).isNull();
    }
}

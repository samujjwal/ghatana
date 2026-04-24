/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.auth;

import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tenant extraction and isolation contract coverage
 * @doc.layer shared-services
 * @doc.pattern Test
 */
@DisplayName("Tenant Isolation Contract Tests")
class TenantIsolationContractTest extends EventloopTestBase {

    private TenantExtractor extractor;
    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        extractor = new TenantExtractor();
        tokenProvider = JwtTokenProviders.fromSharedSecret(
            "0123456789abcdef0123456789abcdef",
            120_000
        );
    }

    @Test
    @DisplayName("extracts tenant from JWT claim when no explicit tenant header is present")
    void shouldExtractTenantFromJwtClaim() {
        String token = tokenProvider.createToken(
            "user-1",
            List.of("USER"),
            Map.of("tenantId", "tenant-jwt")
        );

        HttpRequest request = HttpRequest.get("http://localhost:8080/api/private")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();

        String tenant = extractor.extract(request, tokenProvider);
        assertThat(tenant).isEqualTo("tenant-jwt");
    }

    @Test
    @DisplayName("explicit tenant header has precedence over JWT tenant claim")
    void shouldPreferHeaderOverJwtTenantClaim() {
        String token = tokenProvider.createToken(
            "user-2",
            List.of("USER"),
            Map.of("tenantId", "tenant-jwt")
        );

        HttpRequest request = HttpRequest.get("http://localhost:8080/api/private")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-header")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();

        String tenant = extractor.extract(request, tokenProvider);
        assertThat(tenant).isEqualTo("tenant-header");
    }

    @Test
    @DisplayName("invalid tenant header is rejected and safe path tenant is used")
    void shouldRejectInvalidHeaderAndFallbackToPath() {
        HttpRequest request = HttpRequest.get("http://localhost:8080/tenants/path-tenant/api/private")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant<script>")
            .build();

        Optional<String> tenant = runPromise(() -> extractor.extractTenant(request));
        assertThat(tenant).contains("path-tenant");
    }

    @Test
    @DisplayName("returns empty when all extraction sources are invalid")
    void shouldReturnEmptyWhenSourcesAreInvalid() {
        HttpRequest request = HttpRequest.get("http://localhost:8080/tenants/bad%20tenant/api/private")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), " ")
            .build();

        Optional<String> tenant = runPromise(() -> extractor.extractTenant(request));
        assertThat(tenant).isEmpty();
    }
}

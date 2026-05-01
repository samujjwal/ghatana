/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import com.ghatana.datacloud.launcher.http.DataCloudSecurityFilter;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration-level tenant isolation tests exercising the real
 * {@link DataCloudSecurityFilter} chain.
 *
 * <p>Asserts that:
 * <ul>
 *   <li>JWT token issued for tenant-A is rejected when the request targets tenant-B
 *       via {@code X-Tenant-Id} header override (cross-tenant mismatch: HTTP 403).</li>
 *   <li>JWT token missing the required tenant claim is rejected (HTTP 401).</li>
 *   <li>API key principal with no tenant is rejected in enforcing mode (HTTP 400,
 *       TENANT_REQUIRED error code).</li>
 *   <li>JWT token for tenant-A with matching or absent header passes through (HTTP 200).</li>
 *   <li>Public probes bypass authentication entirely (HTTP 200 without credentials).</li>
 * </ul>
 *
 * <p>These are filter-level integration assertions complementary to
 * {@code DataCloudSecurityFilterJwtTest} (unit-level) and
 * {@code RequestObservationFilterTenantValidationTest} (request-boundary level).
 *
 * @doc.type class
 * @doc.purpose Cross-tenant denial integration tests for DataCloudSecurityFilter
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Tenant Isolation - DataCloudSecurityFilter cross-tenant enforcement")
public class TenantIsolationTest extends EventloopTestBase {

    private static final String VALID_TOKEN    = "jwt-token-tenant-a";
    private static final String TENANT_A       = "tenant-a";
    private static final String TENANT_B       = "tenant-b";
    private static final String PROTECTED_PATH = "/api/v1/entities/orders";

    private JwtTokenProvider jwtProvider;

    private static final AsyncServlet OK_DELEGATE =
            request -> Promise.of(HttpResponse.ok200().build());

    @BeforeEach
    void setUp() {
        jwtProvider = mock(JwtTokenProvider.class);
        when(jwtProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(Optional.of("user-a"));
        when(jwtProvider.getRolesFromToken(VALID_TOKEN)).thenReturn(List.of("viewer"));
        when(jwtProvider.extractClaims(VALID_TOKEN))
                .thenReturn(Optional.of(Map.of("tenant_id", TENANT_A)));
    }

    private DataCloudSecurityFilter enforcing() {
        return DataCloudSecurityFilter.builder()
                .jwtProvider(jwtProvider)
                .enforcing(true)
                .build();
    }

    // -------------------------------------------------------------------------
    // JWT cross-tenant mismatch
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("JWT cross-tenant isolation")
    class JwtCrossTenantIsolationTests {

        @Test
        @DisplayName("JWT for tenant-A with X-Tenant-Id: tenant-B header returns 403")
        void jwtTenantACrossTenantHeaderBReturns403() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest request = HttpRequest.get("http://localhost" + PROTECTED_PATH)
                    .withHeader(HttpHeaders.of("Authorization"), "Bearer " + VALID_TOKEN)
                    .withHeader(HttpHeaders.of("X-Tenant-Id"), TENANT_B)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
        }

        @Test
        @DisplayName("JWT for tenant-A with matching X-Tenant-Id header passes through")
        void jwtTenantAMatchingHeaderPassesThrough() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest request = HttpRequest.get("http://localhost" + PROTECTED_PATH)
                    .withHeader(HttpHeaders.of("Authorization"), "Bearer " + VALID_TOKEN)
                    .withHeader(HttpHeaders.of("X-Tenant-Id"), TENANT_A)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("JWT for tenant-A without X-Tenant-Id header passes through")
        void jwtTenantAWithoutHeaderPassesThrough() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest request = HttpRequest.get("http://localhost" + PROTECTED_PATH)
                    .withHeader(HttpHeaders.of("Authorization"), "Bearer " + VALID_TOKEN)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("JWT with missing tenant claim returns 401")
        void jwtMissingTenantClaimReturns401() {
            when(jwtProvider.extractClaims(VALID_TOKEN))
                    .thenReturn(Optional.of(Map.of("sub", "user-a"))); // no tenant_id claim

            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest request = HttpRequest.get("http://localhost" + PROTECTED_PATH)
                    .withHeader(HttpHeaders.of("Authorization"), "Bearer " + VALID_TOKEN)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(401);
        }

        @Test
        @DisplayName("JWT cross-tenant via tenantId query parameter also returns 403")
        void jwtTenantACrossTenantQueryParamReturns403() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest request = HttpRequest.get(
                            "http://localhost" + PROTECTED_PATH + "?tenantId=" + TENANT_B)
                    .withHeader(HttpHeaders.of("Authorization"), "Bearer " + VALID_TOKEN)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
        }
    }

    // -------------------------------------------------------------------------
    // API key principal tenant scoping
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("API key principal tenant scoping")
    class ApiKeyTenantScopingTests {

        private static final String VALID_KEY   = "sk-test-a";
        private static final String INVALID_KEY = "sk-unknown";

        @Test
        @DisplayName("API key principal without tenant in enforcing mode returns 400 TENANT_REQUIRED")
        void apiKeyPrincipalWithoutTenantEnforcingReturns400() {
            ApiKeyResolver resolver = mock(ApiKeyResolver.class);
            when(resolver.resolve(VALID_KEY))
                    .thenReturn(Optional.of(new Principal("service-a", List.of("admin"), "")));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(resolver)
                    .enforcing(true)
                    .build();

            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest request = HttpRequest.get("http://localhost" + PROTECTED_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_KEY)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(400);
        }

        @Test
        @DisplayName("invalid API key returns 401 for protected route")
        void invalidApiKeyReturns401() {
            ApiKeyResolver resolver = mock(ApiKeyResolver.class);
            when(resolver.resolve(INVALID_KEY)).thenReturn(Optional.empty());

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(resolver)
                    .enforcing(true)
                    .build();

            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest request = HttpRequest.get("http://localhost" + PROTECTED_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), INVALID_KEY)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(401);
        }

        @Test
        @DisplayName("missing auth credentials return 401 for protected route")
        void missingAuthReturns401() {
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .jwtProvider(jwtProvider)
                    .enforcing(true)
                    .build();

            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest request = HttpRequest.get("http://localhost" + PROTECTED_PATH)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(401);
        }
    }

    // -------------------------------------------------------------------------
    // Public probe bypass
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Public probe bypass")
    class PublicProbBypassTests {

        @Test
        @DisplayName("/health is accessible without authentication")
        void healthProbeAccessibleWithoutAuth() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest request = HttpRequest.get("http://localhost/health")
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("/metrics is accessible without authentication")
        void metricsProbeAccessibleWithoutAuth() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest request = HttpRequest.get("http://localhost/metrics")
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }
    }
}

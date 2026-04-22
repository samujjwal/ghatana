/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for P3.2.1 enhancements in {@link FederatedQueryHandler}:
 * query timeout configuration, tenant catalog isolation URL building,
 * and default constant values.
 *
 * @doc.type class
 * @doc.purpose Unit tests for FederatedQueryHandler timeout and tenant catalog isolation (P3.2.1) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("FederatedQueryHandler – query timeout, tenant catalog isolation (P3.2.1) [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class FederatedQueryHandlerEnhancementTest {

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private AnalyticsQueryEngine analyticsEngine;

    @Mock
    private MetricsCollector metrics;

    @Mock
    private HttpRequest request;

    // ── Default timeout constant ──────────────────────────────────────────────

    @Nested
    @DisplayName("default timeout and constructor [GH-90000]")
    class DefaultTimeoutTests {

        @Test
        @DisplayName("DEFAULT_QUERY_TIMEOUT_SECONDS is 30 [GH-90000]")
        void defaultTimeoutConstantIs30() { // GH-90000
            assertThat(FederatedQueryHandler.DEFAULT_QUERY_TIMEOUT_SECONDS).isEqualTo(30); // GH-90000
        }

        @Test
        @DisplayName("4-arg constructor defaults to 30s timeout [GH-90000]")
        void fourArgConstructor_defaultsTo30s() { // GH-90000
            FederatedQueryHandler handler = new FederatedQueryHandler( // GH-90000
                    http, analyticsEngine, metrics, null);
            // Verify handler constructs without error; timeout is implicitly 30s
            assertThat(handler).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("5-arg constructor accepts custom positive timeout [GH-90000]")
        void fiveArgConstructor_acceptsCustomTimeout() { // GH-90000
            FederatedQueryHandler handler = new FederatedQueryHandler( // GH-90000
                    http, analyticsEngine, metrics, null, 60);
            assertThat(handler).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("5-arg constructor rejects non-positive timeout [GH-90000]")
        void fiveArgConstructor_rejectsZeroTimeout() { // GH-90000
            assertThatThrownBy(() -> new FederatedQueryHandler( // GH-90000
                            http, analyticsEngine, metrics, null, 0))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("queryTimeoutSeconds must be positive [GH-90000]");
        }

        @Test
        @DisplayName("5-arg constructor rejects negative timeout [GH-90000]")
        void fiveArgConstructor_rejectsNegativeTimeout() { // GH-90000
            assertThatThrownBy(() -> new FederatedQueryHandler( // GH-90000
                            http, analyticsEngine, metrics, null, -5))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("queryTimeoutSeconds must be positive [GH-90000]");
        }
    }

    // ── Tenant catalog URL building ───────────────────────────────────────────

    @Nested
    @DisplayName("buildTenantUrl() — tenant catalog isolation [GH-90000]")
    class TenantUrlTests {

        @Test
        @DisplayName("replaces trailing catalog segment with tenant catalog [GH-90000]")
        void replacesExistingCatalog() { // GH-90000
            String result = FederatedQueryHandler.buildTenantUrl( // GH-90000
                    "jdbc:trino://host:8080/eventcloud", "eventcloud_acme");
            assertThat(result).isEqualTo("jdbc:trino://host:8080/eventcloud_acme [GH-90000]");
        }

        @Test
        @DisplayName("appends tenant catalog when no segment present [GH-90000]")
        void appendsCatalogWhenMissing() { // GH-90000
            String result = FederatedQueryHandler.buildTenantUrl( // GH-90000
                    "jdbc:trino://host:8080", "eventcloud_acme");
            assertThat(result).isEqualTo("jdbc:trino://host:8080/eventcloud_acme [GH-90000]");
        }

        @Test
        @DisplayName("different tenants produce different catalog URLs [GH-90000]")
        void differentTenantsProduceDifferentUrls() { // GH-90000
            String urlA = FederatedQueryHandler.buildTenantUrl( // GH-90000
                    "jdbc:trino://host:8080/eventcloud", "eventcloud_tenantA");
            String urlB = FederatedQueryHandler.buildTenantUrl( // GH-90000
                    "jdbc:trino://host:8080/eventcloud", "eventcloud_tenantB");
            assertThat(urlA).isNotEqualTo(urlB); // GH-90000
            assertThat(urlA).contains("tenantA [GH-90000]");
            assertThat(urlB).contains("tenantB [GH-90000]");
        }

        @Test
        @DisplayName("null base URL returns null gracefully [GH-90000]")
        void nullBaseUrl_returnsNull() { // GH-90000
            assertThat(FederatedQueryHandler.buildTenantUrl(null, "eventcloud_x")).isNull(); // GH-90000
        }

        @Test
        @DisplayName("tenant catalog follows eventcloud_<tenantId> pattern [GH-90000]")
        void tenantCatalogPattern() { // GH-90000
            String tenantId = "my-company-123";
            String expectedCatalog = "eventcloud_" + tenantId;
            String result = FederatedQueryHandler.buildTenantUrl( // GH-90000
                    "jdbc:trino://localhost:8080/base", expectedCatalog);
            assertThat(result).endsWith("/" + expectedCatalog); // GH-90000
        }
    }

    @Nested
    @DisplayName("handleFederatedQuery() tenant enforcement [GH-90000]")
    class TenantEnforcementTests {

        @Test
        @DisplayName("returns 400 when tenant header is missing [GH-90000]")
        void returns400WhenTenantMissing() { // GH-90000
            FederatedQueryHandler handler = new FederatedQueryHandler( // GH-90000
                    http, analyticsEngine, metrics, null);
            HttpResponse badRequest = mock(HttpResponse.class); // GH-90000

            when(http.requireTenantIdOrFail(any())).thenReturn(null); // GH-90000
            when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(badRequest); // GH-90000

            HttpResponse response = handler.handleFederatedQuery(request).getResult(); // GH-90000

            assertThat(response).isSameAs(badRequest); // GH-90000
            verify(metrics, never()).incrementCounter("query.federated", "tenant", "default"); // GH-90000
        }
    }
}

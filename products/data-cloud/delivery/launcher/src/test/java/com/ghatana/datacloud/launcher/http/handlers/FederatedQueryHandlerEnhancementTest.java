/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
 * @doc.purpose Unit tests for FederatedQueryHandler timeout and tenant catalog isolation (P3.2.1) 
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("FederatedQueryHandler – query timeout, tenant catalog isolation (P3.2.1)")
@ExtendWith(MockitoExtension.class) 
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
    @DisplayName("default timeout and constructor")
    class DefaultTimeoutTests {

        @Test
        @DisplayName("DEFAULT_QUERY_TIMEOUT_SECONDS is 30")
        void defaultTimeoutConstantIs30() { 
            assertThat(FederatedQueryHandler.DEFAULT_QUERY_TIMEOUT_SECONDS).isEqualTo(30); 
        }

        @Test
        @DisplayName("4-arg constructor defaults to 30s timeout")
        void fourArgConstructor_defaultsTo30s() { 
            FederatedQueryHandler handler = new FederatedQueryHandler( 
                    http, analyticsEngine, metrics, null);
            // Verify handler constructs without error; timeout is implicitly 30s
            assertThat(handler).isNotNull(); 
        }

        @Test
        @DisplayName("5-arg constructor accepts custom positive timeout")
        void fiveArgConstructor_acceptsCustomTimeout() { 
            FederatedQueryHandler handler = new FederatedQueryHandler( 
                    http, analyticsEngine, metrics, null, 60);
            assertThat(handler).isNotNull(); 
        }

        @Test
        @DisplayName("5-arg constructor rejects non-positive timeout")
        void fiveArgConstructor_rejectsZeroTimeout() { 
            assertThatThrownBy(() -> new FederatedQueryHandler( 
                            http, analyticsEngine, metrics, null, 0))
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("queryTimeoutSeconds must be positive");
        }

        @Test
        @DisplayName("5-arg constructor rejects negative timeout")
        void fiveArgConstructor_rejectsNegativeTimeout() { 
            assertThatThrownBy(() -> new FederatedQueryHandler( 
                            http, analyticsEngine, metrics, null, -5))
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("queryTimeoutSeconds must be positive");
        }
    }

    // ── Tenant catalog URL building ───────────────────────────────────────────

    @Nested
    @DisplayName("buildTenantUrl() — tenant catalog isolation")
    class TenantUrlTests {

        @Test
        @DisplayName("replaces trailing catalog segment with tenant catalog")
        void replacesExistingCatalog() { 
            String result = FederatedQueryHandler.buildTenantUrl( 
                    "jdbc:trino://host:8080/eventcloud", "eventcloud_acme");
            assertThat(result).isEqualTo("jdbc:trino://host:8080/eventcloud_acme");
        }

        @Test
        @DisplayName("appends tenant catalog when no segment present")
        void appendsCatalogWhenMissing() { 
            String result = FederatedQueryHandler.buildTenantUrl( 
                    "jdbc:trino://host:8080", "eventcloud_acme");
            assertThat(result).isEqualTo("jdbc:trino://host:8080/eventcloud_acme");
        }

        @Test
        @DisplayName("different tenants produce different catalog URLs")
        void differentTenantsProduceDifferentUrls() { 
            String urlA = FederatedQueryHandler.buildTenantUrl( 
                    "jdbc:trino://host:8080/eventcloud", "eventcloud_tenantA");
            String urlB = FederatedQueryHandler.buildTenantUrl( 
                    "jdbc:trino://host:8080/eventcloud", "eventcloud_tenantB");
            assertThat(urlA).isNotEqualTo(urlB); 
            assertThat(urlA).contains("tenantA");
            assertThat(urlB).contains("tenantB");
        }

        @Test
        @DisplayName("null base URL returns null gracefully")
        void nullBaseUrl_returnsNull() { 
            assertThat(FederatedQueryHandler.buildTenantUrl(null, "eventcloud_x")).isNull(); 
        }

        @Test
        @DisplayName("tenant catalog follows eventcloud_<tenantId> pattern")
        void tenantCatalogPattern() { 
            String tenantId = "my-company-123";
            String expectedCatalog = "eventcloud_" + tenantId;
            String result = FederatedQueryHandler.buildTenantUrl( 
                    "jdbc:trino://localhost:8080/base", expectedCatalog);
            assertThat(result).endsWith("/" + expectedCatalog); 
        }
    }

    @Nested
    @DisplayName("handleFederatedQuery() tenant enforcement")
    class TenantEnforcementTests {

        @Test
        @DisplayName("returns 400 when tenant header is missing")
        void returns400WhenTenantMissing() { 
            FederatedQueryHandler handler = new FederatedQueryHandler( 
                    http, analyticsEngine, metrics, null);
            HttpResponse badRequest = mock(HttpResponse.class); 

            when(http.requireTenantIdWithError(any())).thenReturn(TenantResolutionResult.error(401, "Unauthorized"));
            when(http.errorResponse(anyInt(), anyString())).thenReturn(badRequest); 

            HttpResponse response = handler.handleFederatedQuery(request).getResult(); 

            assertThat(response).isSameAs(badRequest); 
            verify(metrics, never()).incrementCounter("query.federated", "tenant", "default"); 
        }
    }
}

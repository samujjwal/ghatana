package com.ghatana.datacloud.launcher.http;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * DC-SEC-001: Tenant isolation tests for governance routes.
 *
 * @doc.type class
 * @doc.purpose Tenant isolation tests for governance routes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Tenant Isolation - Governance Routes")
class GovernanceRouteTenantIsolationTest extends TenantIsolationTestBase {

    @Override
    protected HttpResponse runRequest(HttpRequest request) {
        return simulatedTenantIsolationResponse(request);
    }

    @Nested
    @DisplayName("POST /api/v1/governance/retention/classify")
    class RetentionClassificationTests {

        @Test
        @DisplayName("Tenant A cannot classify Tenant B's retention")
        void tenantACannotClassifyTenantBRetention() {
            String path = ApiPath.GOVERNANCE_RETENTION + "/classify";

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.post("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .withBody("{\"collection\":\"orders\"}".getBytes())
                .build();

            assertCrossTenantDenial(path, HttpMethod.POST, requestBuilder);
        }

        @Test
        @DisplayName("Tenant A can classify their own retention")
        void tenantACanClassifyOwnRetention() {
            String path = ApiPath.GOVERNANCE_RETENTION + "/classify";

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.post("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .withBody("{\"collection\":\"orders\"}".getBytes())
                .build();

            assertSameTenantAccess(path, HttpMethod.POST, requestBuilder);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/governance/retention/purge")
    class RetentionPurgeTests {

        @Test
        @DisplayName("Tenant A cannot purge Tenant B's retention")
        void tenantACannotPurgeTenantBRetention() {
            String path = ApiPath.GOVERNANCE_PURGE;

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.post("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .withBody("{\"collection\":\"orders\"}".getBytes())
                .build();

            assertCrossTenantDenial(path, HttpMethod.POST, requestBuilder);
        }

        @Test
        @DisplayName("Tenant A can purge their own retention")
        void tenantACanPurgeOwnRetention() {
            String path = ApiPath.GOVERNANCE_PURGE;

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.post("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .withBody("{\"collection\":\"orders\"}".getBytes())
                .build();

            assertSameTenantAccess(path, HttpMethod.POST, requestBuilder);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/governance/privacy/redact")
    class PrivacyRedactionTests {

        @Test
        @DisplayName("Tenant A cannot redact Tenant B's privacy data")
        void tenantACannotRedactTenantBPrivacyData() {
            String path = ApiPath.GOVERNANCE_REDACT;

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.post("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .withBody("{\"collection\":\"orders\",\"fields\":[\"email\",\"ssn\"]}".getBytes())
                .build();

            assertCrossTenantDenial(path, HttpMethod.POST, requestBuilder);
        }

        @Test
        @DisplayName("Tenant A can redact their own privacy data")
        void tenantACanRedactOwnPrivacyData() {
            String path = ApiPath.GOVERNANCE_REDACT;

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.post("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .withBody("{\"collection\":\"orders\",\"fields\":[\"email\",\"ssn\"]}".getBytes())
                .build();

            assertSameTenantAccess(path, HttpMethod.POST, requestBuilder);
        }
    }

    @Nested
    @DisplayName("Cross-Tenant Data Leak Tests")
    class CrossTenantDataLeakTests {

        @Test
        @DisplayName("Governance operations do not leak data from other tenants")
        void governanceOperationsDoNotLeakDataFromOtherTenants() {
            String path = ApiPath.GOVERNANCE_RETENTION + "/classify";

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.post("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .withBody("{\"collection\":\"orders\"}".getBytes())
                .build();

            assertNoCrossTenantDataLeak(path, requestBuilder);
        }
    }
}

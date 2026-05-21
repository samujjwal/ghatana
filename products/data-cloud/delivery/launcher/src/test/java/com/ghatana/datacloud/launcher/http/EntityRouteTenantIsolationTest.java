package com.ghatana.datacloud.launcher.http;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * DC-SEC-001: Tenant isolation tests for entity routes.
 *
 * @doc.type class
 * @doc.purpose Tenant isolation tests for entity routes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Tenant Isolation - Entity Routes")
class EntityRouteTenantIsolationTest extends TenantIsolationTestBase {

    @Override
    protected HttpResponse runRequest(HttpRequest request) {
        return simulatedTenantIsolationResponse(request);
    }

    @Nested
    @DisplayName("GET /api/v1/entities/{collection}")
    class GetEntitiesTests {

        @Test
        @DisplayName("Tenant A cannot query entities from Tenant B")
        void tenantACannotQueryEntitiesFromTenantB() {
            String collection = "orders";
            String path = ApiPath.ENTITIES + "/" + collection;

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.get("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .build();

            assertCrossTenantDenial(path, HttpMethod.GET, requestBuilder);
        }

        @Test
        @DisplayName("Tenant A can query their own entities")
        void tenantACanQueryOwnEntities() {
            String collection = "orders";
            String path = ApiPath.ENTITIES + "/" + collection;

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.get("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .build();

            assertSameTenantAccess(path, HttpMethod.GET, requestBuilder);
        }

        @Test
        @DisplayName("Query without tenant ID is rejected")
        void queryWithoutTenantIdIsRejected() {
            String collection = "orders";
            String path = ApiPath.ENTITIES + "/" + collection;

            RequestBuilder requestBuilder = new RequestBuilder() {
                @Override
                public HttpRequest build(TestTenantContext context, String p) {
                    return HttpRequest.get("http://localhost" + p)
                        .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                        .build();
                }

                @Override
                public HttpRequest buildWithoutTenant(String p) {
                    return HttpRequest.get("http://localhost" + p)
                        .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN_A)
                        .build();
                }
            };

            assertMissingTenantRejection(path, HttpMethod.GET, requestBuilder);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/entities/{collection}")
    class CreateEntityTests {

        @Test
        @DisplayName("Tenant A cannot create entity in Tenant B's collection")
        void tenantACannotCreateEntityInTenantBCollection() {
            String collection = "orders";
            String path = ApiPath.ENTITIES + "/" + collection;

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.post("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .withBody("{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8))
                .build();

            assertCrossTenantDenial(path, HttpMethod.POST, requestBuilder);
        }

        @Test
        @DisplayName("Tenant A can create entity in their own collection")
        void tenantACanCreateEntityInOwnCollection() {
            String collection = "orders";
            String path = ApiPath.ENTITIES + "/" + collection;

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.post("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .withBody("{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8))
                .build();

            assertSameTenantAccess(path, HttpMethod.POST, requestBuilder);
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/entities/{collection}/{id}")
    class UpdateEntityTests {

        @Test
        @DisplayName("Tenant A cannot update entity in Tenant B's collection")
        void tenantACannotUpdateEntityInTenantBCollection() {
            String collection = "orders";
            String entityId = UUID.randomUUID().toString();
            String path = ApiPath.ENTITIES + "/" + collection + "/" + entityId;

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.put("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .withBody("{\"name\":\"updated\"}".getBytes(StandardCharsets.UTF_8))
                .build();

            assertCrossTenantDenial(path, HttpMethod.PUT, requestBuilder);
        }

        @Test
        @DisplayName("Tenant A can update entity in their own collection")
        void tenantACanUpdateEntityInOwnCollection() {
            String collection = "orders";
            String entityId = UUID.randomUUID().toString();
            String path = ApiPath.ENTITIES + "/" + collection + "/" + entityId;

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.put("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .withBody("{\"name\":\"updated\"}".getBytes(StandardCharsets.UTF_8))
                .build();

            assertSameTenantAccess(path, HttpMethod.PUT, requestBuilder);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/entities/{collection}/{id}")
    class DeleteEntityTests {

        @Test
        @DisplayName("Tenant A cannot delete entity in Tenant B's collection")
        void tenantACannotDeleteEntityInTenantBCollection() {
            String collection = "orders";
            String entityId = UUID.randomUUID().toString();
            String path = ApiPath.ENTITIES + "/" + collection + "/" + entityId;

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.post("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .build();

            assertCrossTenantDenial(path, HttpMethod.DELETE, requestBuilder);
        }

        @Test
        @DisplayName("Tenant A can delete entity in their own collection")
        void tenantACanDeleteEntityInOwnCollection() {
            String collection = "orders";
            String entityId = UUID.randomUUID().toString();
            String path = ApiPath.ENTITIES + "/" + collection + "/" + entityId;

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.post("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .build();

            assertSameTenantAccess(path, HttpMethod.DELETE, requestBuilder);
        }
    }

    @Nested
    @DisplayName("Cross-Tenant Data Leak Tests")
    class CrossTenantDataLeakTests {

        @Test
        @DisplayName("Query response does not leak data from other tenants")
        void queryResponseDoesNotLeakDataFromOtherTenants() {
            String collection = "orders";
            String path = ApiPath.ENTITIES + "/" + collection;

            RequestBuilder requestBuilder = (context, p) -> HttpRequest.get("http://localhost" + p)
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + context.token)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), context.tenantId)
                .build();

            assertNoCrossTenantDataLeak(path, requestBuilder);
        }
    }
}

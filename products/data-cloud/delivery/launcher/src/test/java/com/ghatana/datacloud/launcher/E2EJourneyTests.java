/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.QueryPlan;
import com.ghatana.datacloud.analytics.QueryResult;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServerTestBase;
import com.ghatana.datacloud.launcher.http.TestConstants;
import com.ghatana.datacloud.spi.EntityStore;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end HTTP journey tests for the Data Cloud launcher.
 *
 * <p>These tests replace the previous in-memory placeholder suite with real HTTP
 * requests against a started {@link DataCloudHttpServer}. The backing services are
 * mocked only at the product boundary so the router, handlers, request parsing,
 * tenant propagation, and response envelopes are exercised for real.
 *
 * @doc.type class
 * @doc.purpose Real HTTP-backed end-to-end journey coverage for core launcher surfaces
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Data Cloud launcher end-to-end journeys")
class E2EJourneyTests extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;
    private EntityStore mockEntityStore;
    private AnalyticsQueryEngine mockAnalyticsEngine;

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        mockEntityStore = mock(EntityStore.class);
        mockAnalyticsEngine = mock(AnalyticsQueryEngine.class);

        when(mockClient.entityStore()).thenReturn(mockEntityStore);
        lenient().when(mockEntityStore.count(any(), any())).thenReturn(Promise.of(0L));
        lenient().when(mockClient.appendEvent(anyString(), any()))
                .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        port = findFreePort();
    }

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port, null, null, mockAnalyticsEngine);
        server.start();
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS);
    }

    @Nested
    @DisplayName("Server health journey")
    class ServerHealthJourney {

        @Test
        @DisplayName("GET /health reports the server as ready")
        void healthEndpointReturnsReady() throws Exception {
            startServer();

            HttpResponse<String> response = get("/health");

            assertStatusCode(response, TestConstants.HTTP_OK);
            assertThat(response.body()).contains("UP");
        }
    }

    @Nested
    @DisplayName("Data explorer journey")
    class DataExplorerJourney {

        @Test
        @DisplayName("POST collection metadata then list collections through entity routes")
        void createAndListCollectionsThroughHttpRoutes() throws Exception {
            DataCloudClient.Entity savedCollection = DataCloudClient.Entity.of(
                    "sales_2026",
                    "dc_collections",
                    Map.of("name", "Sales 2026", "storageTier", "HOT"));
            when(mockClient.save(eq(TestConstants.TENANT_DEFAULT), eq("dc_collections"), any()))
                    .thenReturn(Promise.of(savedCollection));
            when(mockClient.query(eq(TestConstants.TENANT_DEFAULT), eq("dc_collections"), any()))
                    .thenReturn(Promise.of(List.of(savedCollection)));

            startServer();

            HttpResponse<String> createResponse = postJson(
                    "/api/v1/entities/dc_collections",
                    Map.of("name", "Sales 2026", "storageTier", "HOT"),
                    withTenant(TestConstants.TENANT_DEFAULT));

            assertStatusCode(createResponse, TestConstants.HTTP_OK);
            Map<String, Object> created = parseJsonResponse(createResponse);
            assertThat(created.get("id")).isEqualTo("sales_2026");
            assertThat(created.get("collection")).isEqualTo("dc_collections");

            HttpResponse<String> listResponse = get(
                    "/api/v1/entities/dc_collections",
                    withTenant(TestConstants.TENANT_DEFAULT));

            assertStatusCode(listResponse, TestConstants.HTTP_OK);
            Map<String, Object> listed = parseJsonResponse(listResponse);
            assertThat((List<?>) listed.get("entities")).hasSize(1);
            assertThat(listResponse.body()).contains("sales_2026");
        }
    }

    @Nested
    @DisplayName("Event ingestion journey")
    class EventIngestionJourney {

        @Test
        @DisplayName("POST /api/v1/events appends an event and GET /api/v1/events replays it")
        void appendThenReadEvents() throws Exception {
            DataCloudClient.Event persistedEvent = DataCloudClient.Event.builder()
                    .type("ENTITY_CREATED")
                    .payload(Map.of("entityId", "sales_2026"))
                    .build();
            when(mockClient.queryEvents(eq(TestConstants.TENANT_DEFAULT), any()))
                    .thenReturn(Promise.of(List.of(persistedEvent)));

            startServer();

            HttpResponse<String> appendResponse = postJson(
                    "/api/v1/events",
                    Map.of("type", "ENTITY_CREATED", "data", Map.of("entityId", "sales_2026")),
                    withTenant(TestConstants.TENANT_DEFAULT));

            assertStatusCode(appendResponse, TestConstants.HTTP_OK);
            Map<String, Object> appendBody = parseJsonResponse(appendResponse);
            assertThat(appendBody.get("offset")).isEqualTo(1);
            assertThat(appendBody.get("type")).isEqualTo("ENTITY_CREATED");

            HttpResponse<String> readResponse = get(
                    "/api/v1/events?from=0",
                    withTenant(TestConstants.TENANT_DEFAULT));

            assertStatusCode(readResponse, TestConstants.HTTP_OK);
            Map<String, Object> readBody = parseJsonResponse(readResponse);
            assertThat((List<?>) readBody.get("events")).hasSize(1);
            assertThat(readResponse.body()).contains("ENTITY_CREATED");
        }
    }

    @Nested
    @DisplayName("Storage cost journey")
    class StorageCostJourney {

        @Test
        @DisplayName("GET /api/v1/queries/estimate returns a query cost estimate")
        void queryCostEstimateReturnsCostEnvelope() throws Exception {
            when(mockAnalyticsEngine.submitQuery(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.of(buildQueryResult("q-est-1")));
            when(mockAnalyticsEngine.getPlan("q-est-1"))
                    .thenReturn(Promise.of(buildQueryPlan("q-est-1", 5.0d)));

            startServer();

            HttpResponse<String> response = get(
                    "/api/v1/queries/estimate?sql=SELECT+*+FROM+sales_2026",
                    withTenant(TestConstants.TENANT_DEFAULT));

            assertStatusCode(response, TestConstants.HTTP_OK);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsKeys("queryId", "estimatedCostDcc", "currency", "breakdown");
            assertThat(body.get("currency")).isEqualTo("DCC");
        }

        @Test
        @DisplayName("GET /api/v1/collections/:id/cost-report returns per-collection cost data")
        void collectionCostReportReturnsCostData() throws Exception {
            when(mockAnalyticsEngine.submitQuery(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.of(buildQueryResult("q-cost-1")));
            when(mockAnalyticsEngine.getPlan("q-cost-1"))
                    .thenReturn(Promise.of(buildQueryPlan("q-cost-1", 10.0d)));

            startServer();

            HttpResponse<String> response = get(
                    "/api/v1/collections/sales_2026/cost-report",
                    withTenant(TestConstants.TENANT_DEFAULT));

            assertStatusCode(response, TestConstants.HTTP_OK);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsKeys("collectionId", "tiers", "currency");
            assertThat(body.get("collectionId")).isEqualTo("sales_2026");
        }
    }

    private QueryResult buildQueryResult(String queryId) {
        return QueryResult.builder()
                .queryId(queryId)
                .rows(List.of())
                .rowCount(0)
                .columnCount(0)
                .executionTimeMs(5L)
                .optimized(true)
                .build();
    }

    private QueryPlan buildQueryPlan(String queryId, double estimatedCost) {
        return QueryPlan.builder()
                .queryId(queryId)
                .estimatedCost(estimatedCost)
                .optimized(true)
                .dataSources(List.of("sales_2026"))
                .build();
    }
}

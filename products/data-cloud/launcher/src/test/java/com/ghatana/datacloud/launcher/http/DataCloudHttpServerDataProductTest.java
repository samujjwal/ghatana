/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for data product publish, discover, and subscribe endpoints (P4.4.1).
 */
@DisplayName("DataCloudHttpServer – data products API (P4.4.1)")
class DataCloudHttpServerDataProductTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        port = findFreePort();
    }

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS);
    }

    @Test
    @DisplayName("POST /api/v1/data-products publishes catalog entry with inferred schema")
    void publishDataProductPersistsDescriptor() throws Exception {
        DataCloudClient.Entity sample = entity("sample-1", "orders", Map.of("orderId", "o-1", "amount", 42.5, "status", "complete"));
        DataCloudClient.Entity savedDescriptor = entity("product-1", "dc_data_products", Map.of("name", "Orders Product", "collection", "orders"));

        when(mockClient.query(anyString(), eq("orders"), any())).thenReturn(Promise.of(List.of(sample)));
        when(mockClient.save(anyString(), eq("dc_data_products"), any())).thenReturn(Promise.of(savedDescriptor));

        startServer();

        HttpResponse<String> response = postJson("/api/v1/data-products", Map.of(
            "name", "Orders Product",
            "collection", "orders",
            "description", "Published catalog entry",
            "sla", Map.of("freshnessSeconds", 600, "completenessTarget", 0.9),
            "access", Map.of("allowedSubscribers", List.of("tenant-a"))
        ));

        assertStatusCode(response, 200);
        Map<String, Object> body = parseJsonResponse(response);
        assertThat(body).containsEntry("productId", "product-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> descriptor = (Map<String, Object>) body.get("descriptor");
        assertThat(descriptor).containsEntry("collection", "orders");
        assertThat(descriptor).containsEntry("qualityStatus", "HEALTHY");
        assertThat(descriptor).containsKey("schema");
        assertThat(descriptor).containsKey("lineage");
    }

    @Test
    @DisplayName("GET /api/v1/data-products returns enriched quality snapshots")
    void listDataProductsReturnsEnrichedItems() throws Exception {
        DataCloudClient.Entity product = entity("product-1", "dc_data_products", Map.of(
                "name", "Orders Product",
                "collection", "orders",
                "sla", Map.of("freshnessSeconds", 600, "completenessTarget", 0.9)
            ));
        DataCloudClient.Entity sample = entity("sample-1", "orders", Map.of("orderId", "o-1", "amount", 42.5, "status", "complete"));

        when(mockClient.query(anyString(), eq("dc_data_products"), any())).thenReturn(Promise.of(List.of(product)));
        when(mockClient.query(anyString(), eq("orders"), any())).thenReturn(Promise.of(List.of(sample)));

        startServer();

        HttpResponse<String> response = get("/api/v1/data-products");

        assertStatusCode(response, 200);
        Map<String, Object> body = parseJsonResponse(response);
        assertThat(body).containsEntry("count", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst()).containsEntry("collection", "orders");
        assertThat(items.getFirst()).containsKey("quality");
    }

    @Test
    @DisplayName("POST /api/v1/data-products/:productId/subscribe creates subscription when consumer is allowed")
    void subscribeCreatesSubscription() throws Exception {
        DataCloudClient.Entity product = entity("product-1", "dc_data_products", Map.of(
                "collection", "orders",
                "access", Map.of("allowedSubscribers", List.of("tenant-a"))
            ));
        DataCloudClient.Entity subscription = entity("subscription-1", "dc_data_product_subscriptions", Map.of("productId", "product-1", "consumerId", "tenant-a"));

        when(mockClient.findById(anyString(), eq("dc_data_products"), eq("product-1")))
            .thenReturn(Promise.of(Optional.of(product)));
        when(mockClient.save(anyString(), eq("dc_data_product_subscriptions"), any()))
            .thenReturn(Promise.of(subscription));

        startServer();

        HttpResponse<String> response = postJson(
            "/api/v1/data-products/product-1/subscribe",
            Map.of("consumerId", "tenant-a"));

        assertStatusCode(response, 200);
        Map<String, Object> body = parseJsonResponse(response);
        assertThat(body).containsEntry("subscriptionId", "subscription-1");
        assertThat(body).containsEntry("status", "ACTIVE");
    }

    private DataCloudClient.Entity entity(String id, String collection, Map<String, Object> data) {
        Instant now = Instant.now();
        return new DataCloudClient.Entity(id, collection, data, now, now, 1L);
    }
}
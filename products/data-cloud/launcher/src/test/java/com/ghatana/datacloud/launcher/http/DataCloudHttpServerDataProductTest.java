/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Integration tests for data product publish, discover, and subscribe endpoints (P4.4.1). // GH-90000
 */
@DisplayName("DataCloudHttpServer – data products API (P4.4.1) [GH-90000]")
class DataCloudHttpServerDataProductTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        port = findFreePort(); // GH-90000
    }

    @Override
    protected void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS); // GH-90000
    }

    @Test
    @DisplayName("POST /api/v1/data-products publishes catalog entry with inferred schema [GH-90000]")
    void publishDataProductPersistsDescriptor() throws Exception { // GH-90000
        DataCloudClient.Entity sample = entity("sample-1", "orders", Map.of("orderId", "o-1", "amount", 42.5, "status", "complete")); // GH-90000
        DataCloudClient.Entity savedDescriptor = entity("product-1", "dc_data_products", Map.of("name", "Orders Product", "collection", "orders")); // GH-90000

        when(mockClient.query(anyString(), eq("orders [GH-90000]"), any())).thenReturn(Promise.of(List.of(sample)));
        when(mockClient.save(anyString(), eq("dc_data_products [GH-90000]"), any())).thenReturn(Promise.of(savedDescriptor));

        startServer(); // GH-90000

        HttpResponse<String> response = postJson("/api/v1/data-products", Map.of( // GH-90000
            "name", "Orders Product",
            "collection", "orders",
            "description", "Published catalog entry",
            "sla", Map.of("freshnessSeconds", 600, "completenessTarget", 0.9), // GH-90000
            "access", Map.of("allowedSubscribers", List.of("tenant-a [GH-90000]"))
        ));

        assertStatusCode(response, 200); // GH-90000
        Map<String, Object> body = parseJsonResponse(response); // GH-90000
        assertThat(body).containsEntry("productId", "product-1"); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> descriptor = (Map<String, Object>) body.get("descriptor [GH-90000]");
        assertThat(descriptor).containsEntry("collection", "orders"); // GH-90000
        assertThat(descriptor).containsEntry("qualityStatus", "HEALTHY"); // GH-90000
        assertThat(descriptor).containsKey("schema [GH-90000]");
        assertThat(descriptor).containsKey("lineage [GH-90000]");
    }

    @Test
    @DisplayName("GET /api/v1/data-products returns enriched quality snapshots [GH-90000]")
    void listDataProductsReturnsEnrichedItems() throws Exception { // GH-90000
        DataCloudClient.Entity product = entity("product-1", "dc_data_products", Map.of( // GH-90000
                "name", "Orders Product",
                "collection", "orders",
                "sla", Map.of("freshnessSeconds", 600, "completenessTarget", 0.9) // GH-90000
            ));
        DataCloudClient.Entity sample = entity("sample-1", "orders", Map.of("orderId", "o-1", "amount", 42.5, "status", "complete")); // GH-90000

        when(mockClient.query(anyString(), eq("dc_data_products [GH-90000]"), any())).thenReturn(Promise.of(List.of(product)));
        when(mockClient.query(anyString(), eq("orders [GH-90000]"), any())).thenReturn(Promise.of(List.of(sample)));

        startServer(); // GH-90000

        HttpResponse<String> response = get("/api/v1/data-products [GH-90000]");

        assertStatusCode(response, 200); // GH-90000
        Map<String, Object> body = parseJsonResponse(response); // GH-90000
        assertThat(body).containsEntry("count", 1); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items [GH-90000]");
        assertThat(items).hasSize(1); // GH-90000
        assertThat(items.getFirst()).containsEntry("collection", "orders"); // GH-90000
        assertThat(items.getFirst()).containsKey("quality [GH-90000]");
    }

    @Test
    @DisplayName("POST /api/v1/data-products/:productId/subscribe creates subscription when consumer is allowed [GH-90000]")
    void subscribeCreatesSubscription() throws Exception { // GH-90000
        DataCloudClient.Entity product = entity("product-1", "dc_data_products", Map.of( // GH-90000
                "collection", "orders",
                "access", Map.of("allowedSubscribers", List.of("tenant-a [GH-90000]"))
            ));
        DataCloudClient.Entity subscription = entity("subscription-1", "dc_data_product_subscriptions", Map.of("productId", "product-1", "consumerId", "tenant-a")); // GH-90000

        when(mockClient.findById(anyString(), eq("dc_data_products [GH-90000]"), eq("product-1 [GH-90000]")))
            .thenReturn(Promise.of(Optional.of(product))); // GH-90000
        when(mockClient.save(anyString(), eq("dc_data_product_subscriptions [GH-90000]"), any()))
            .thenReturn(Promise.of(subscription)); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> response = postJson( // GH-90000
            "/api/v1/data-products/product-1/subscribe",
            Map.of("consumerId", "tenant-a")); // GH-90000

        assertStatusCode(response, 200); // GH-90000
        Map<String, Object> body = parseJsonResponse(response); // GH-90000
        assertThat(body).containsEntry("subscriptionId", "subscription-1"); // GH-90000
        assertThat(body).containsEntry("status", "ACTIVE"); // GH-90000
    }

    private DataCloudClient.Entity entity(String id, String collection, Map<String, Object> data) { // GH-90000
        Instant now = Instant.now(); // GH-90000
        return new DataCloudClient.Entity(id, collection, data, now, now, 1L); // GH-90000
    }
}
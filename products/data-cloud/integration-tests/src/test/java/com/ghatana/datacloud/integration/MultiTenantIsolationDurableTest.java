/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloud.DataCloudConfig.DataCloudProfile;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-Tenant Isolation Durable Tests
 *
 * Proves tenant isolation through the live launcher against real durable providers
 * (PostgreSQL, Kafka) using Testcontainers. This test validates tenant isolation logic // GH-90000
 * AND proves isolation against production-grade infrastructure.
 *
 * @doc.type class
 * @doc.purpose Prove tenant isolation against real durable providers using Testcontainers
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Multi-Tenant Isolation Durable Tests (Real Persistence)")
@Tag("integration")
class MultiTenantIsolationDurableTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {}; // GH-90000
    private static final String COLLECTION = "tenant_isolation_entities_durable";

    private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>( // GH-90000
        DockerImageName.parse("postgres:16-alpine"))
        .withDatabaseName("datacloud_test")
        .withUsername("test_user")
        .withPassword("test_pass");

    private static final KafkaContainer KAFKA = new KafkaContainer( // GH-90000
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    private final ObjectMapper objectMapper = new ObjectMapper(); // GH-90000
    private final HttpClient httpClient = HttpClient.newHttpClient(); // GH-90000

    private DataCloudClient client;
    private DataCloudHttpServer server;
    private int port;

    static {
        POSTGRESQL.start(); // GH-90000
        KAFKA.start(); // GH-90000
    }

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        DataCloudConfig config = DataCloudConfig.builder() // GH-90000
            .profile(DataCloudProfile.STAGING) // GH-90000
            .customConfig(Map.of( // GH-90000
                "postgresql.url", POSTGRESQL.getJdbcUrl(), // GH-90000
                "postgresql.username", POSTGRESQL.getUsername(), // GH-90000
                "postgresql.password", POSTGRESQL.getPassword(), // GH-90000
                "kafka.bootstrapServers", KAFKA.getBootstrapServers() // GH-90000
            ))
            .build(); // GH-90000
        client = DataCloud.create(config); // GH-90000
        port = findFreePort(); // GH-90000
        server = new DataCloudHttpServer(client, port); // GH-90000
        server.start(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) { // GH-90000
            server.stop(); // GH-90000
        }
        if (client != null) { // GH-90000
            client.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("query endpoint returns only the requesting tenant's entities from PostgreSQL")
    void queryEndpointReturnsOnlyTheRequestingTenantsEntitiesFromPostgreSQL() throws Exception { // GH-90000
        String tenantAId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION, // GH-90000
            Map.of("name", "tenant-a-doc", "owner", "tenant-a"), "tenant-a").body().get("id"));
        String tenantBId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION, // GH-90000
            Map.of("name", "tenant-b-doc", "owner", "tenant-b"), "tenant-b").body().get("id"));

        ParsedHttpResponse tenantAQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?limit=10", // GH-90000
            null, "tenant-a");
        ParsedHttpResponse tenantBQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?limit=10", // GH-90000
            null, "tenant-b");

        assertThat(tenantAQuery.statusCode()).isEqualTo(200); // GH-90000
        assertThat(tenantBQuery.statusCode()).isEqualTo(200); // GH-90000
        assertThat(entityIds(tenantAQuery.body())).containsExactly(tenantAId); // GH-90000
        assertThat(entityIds(tenantBQuery.body())).containsExactly(tenantBId); // GH-90000
    }

    @Test
    @DisplayName("cross-tenant reads and deletes do not expose another tenant's entity in PostgreSQL")
    void crossTenantReadsAndDeletesDoNotExposeAnotherTenantsEntityInPostgreSQL() throws Exception { // GH-90000
        String tenantAId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION, // GH-90000
            Map.of("name", "secret-doc", "classification", "private"), "tenant-a").body().get("id"));

        ParsedHttpResponse crossTenantRead = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + tenantAId, // GH-90000
            null, "tenant-b");
        ParsedHttpResponse crossTenantDelete = sendJson("DELETE", "/api/v1/entities/" + COLLECTION + "/" + tenantAId, // GH-90000
            null, "tenant-b");
        ParsedHttpResponse ownerRead = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + tenantAId, // GH-90000
            null, "tenant-a");

        assertThat(crossTenantRead.statusCode()).isEqualTo(404); // GH-90000
        assertThat(crossTenantDelete.statusCode()).isEqualTo(404); // GH-90000
        assertThat(ownerRead.statusCode()).isEqualTo(200); // GH-90000
        assertThat(ownerRead.body()).containsEntry("id", tenantAId); // GH-90000
        assertThat(asMap(ownerRead.body().get("data"))).containsEntry("classification", "private");
    }

    @Test
    @DisplayName("tenant isolation persists across server restarts with PostgreSQL")
    void tenantIsolationPersistsAcrossServerRestartsWithPostgreSQL() throws Exception { // GH-90000
        String tenantAId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION, // GH-90000
            Map.of("name", "persistent-doc", "owner", "tenant-a"), "tenant-a").body().get("id"));

        // Stop and restart server
        server.stop(); // GH-90000
        Thread.sleep(1000); // Wait for graceful shutdown // GH-90000
        server = new DataCloudHttpServer(client, port); // GH-90000
        server.start(); // GH-90000
        Thread.sleep(1000); // Wait for startup // GH-90000

        // Verify tenant A can still access their entity
        ParsedHttpResponse ownerRead = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + tenantAId, // GH-90000
            null, "tenant-a");
        assertThat(ownerRead.statusCode()).isEqualTo(200); // GH-90000
        assertThat(ownerRead.body()).containsEntry("id", tenantAId); // GH-90000

        // Verify tenant B still cannot access tenant A's entity
        ParsedHttpResponse crossTenantRead = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + tenantAId, // GH-90000
            null, "tenant-b");
        assertThat(crossTenantRead.statusCode()).isEqualTo(404); // GH-90000
    }

    @Test
    @DisplayName("concurrent tenant operations maintain isolation in PostgreSQL")
    void concurrentTenantOperationsMaintainIsolationInPostgreSQL() throws Exception { // GH-90000
        List<Thread> threads = new ArrayList<>(); // GH-90000
        List<String> tenantAIds = new ArrayList<>(); // GH-90000
        List<String> tenantBIds = new ArrayList<>(); // GH-90000

        // Create entities for tenant A concurrently
        for (int i = 0; i < 10; i++) { // GH-90000
            final int index = i;
            Thread thread = new Thread(() -> { // GH-90000
                try {
                    String id = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION, // GH-90000
                        Map.of("name", "tenant-a-doc-" + index, "owner", "tenant-a"), "tenant-a") // GH-90000
                        .body().get("id"));
                    synchronized (tenantAIds) { // GH-90000
                        tenantAIds.add(id); // GH-90000
                    }
                } catch (Exception e) { // GH-90000
                    throw new RuntimeException(e); // GH-90000
                }
            });
            threads.add(thread); // GH-90000
            thread.start(); // GH-90000
        }

        // Create entities for tenant B concurrently
        for (int i = 0; i < 10; i++) { // GH-90000
            final int index = i;
            Thread thread = new Thread(() -> { // GH-90000
                try {
                    String id = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION, // GH-90000
                        Map.of("name", "tenant-b-doc-" + index, "owner", "tenant-b"), "tenant-b") // GH-90000
                        .body().get("id"));
                    synchronized (tenantBIds) { // GH-90000
                        tenantBIds.add(id); // GH-90000
                    }
                } catch (Exception e) { // GH-90000
                    throw new RuntimeException(e); // GH-90000
                }
            });
            threads.add(thread); // GH-90000
            thread.start(); // GH-90000
        }

        // Wait for all threads to complete
        for (Thread thread : threads) { // GH-90000
            thread.join(10000); // GH-90000
        }

        // Verify tenant A can only see their entities
        ParsedHttpResponse tenantAQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?limit=100", // GH-90000
            null, "tenant-a");
        assertThat(tenantAQuery.statusCode()).isEqualTo(200); // GH-90000
        assertThat(entityIds(tenantAQuery.body())).containsExactlyInAnyOrderElementsOf(tenantAIds); // GH-90000

        // Verify tenant B can only see their entities
        ParsedHttpResponse tenantBQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?limit=100", // GH-90000
            null, "tenant-b");
        assertThat(tenantBQuery.statusCode()).isEqualTo(200); // GH-90000
        assertThat(entityIds(tenantBQuery.body())).containsExactlyInAnyOrderElementsOf(tenantBIds); // GH-90000
    }

    @Test
    @DisplayName("tenant-specific indexes work correctly in PostgreSQL")
    void tenantSpecificIndexesWorkCorrectlyInPostgreSQL() throws Exception { // GH-90000
        // Create multiple entities for tenant A
        for (int i = 0; i < 5; i++) { // GH-90000
            sendJson("POST", "/api/v1/entities/" + COLLECTION, // GH-90000
                Map.of("name", "tenant-a-doc-" + i, "owner", "tenant-a", "category", "cat-" + (i % 2)), "tenant-a"); // GH-90000
        }

        // Create entities for tenant B with same categories
        for (int i = 0; i < 5; i++) { // GH-90000
            sendJson("POST", "/api/v1/entities/" + COLLECTION, // GH-90000
                Map.of("name", "tenant-b-doc-" + i, "owner", "tenant-b", "category", "cat-" + (i % 2)), "tenant-b"); // GH-90000
        }

        // Query tenant A entities by category
        ParsedHttpResponse tenantAQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?filter=category:cat-0&limit=100", // GH-90000
            null, "tenant-a");

        // Query tenant B entities by category
        ParsedHttpResponse tenantBQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?filter=category:cat-0&limit=100", // GH-90000
            null, "tenant-b");

        assertThat(tenantAQuery.statusCode()).isEqualTo(200); // GH-90000
        assertThat(tenantBQuery.statusCode()).isEqualTo(200); // GH-90000
        assertThat(entityIds(tenantAQuery.body())).hasSize(3); // Should only get tenant A's entities // GH-90000
        assertThat(entityIds(tenantBQuery.body())).hasSize(3); // Should only get tenant B's entities // GH-90000
    }

    private ParsedHttpResponse sendJson( // GH-90000
        String method,
        String path,
        Map<String, Object> body,
        String tenantId
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Accept", "application/json") // GH-90000
            .header("X-Tenant-Id", tenantId); // GH-90000

        if (body != null) { // GH-90000
            builder.header("Content-Type", "application/json"); // GH-90000
            builder.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))); // GH-90000
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody()); // GH-90000
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString()); // GH-90000
        Map<String, Object> parsedBody = response.body() == null || response.body().isBlank() // GH-90000
            ? Map.of() // GH-90000
            : objectMapper.readValue(response.body(), MAP_TYPE); // GH-90000

        return new ParsedHttpResponse(response.statusCode(), parsedBody); // GH-90000
    }

    @SuppressWarnings("unchecked")
    private List<String> entityIds(Map<String, Object> responseBody) { // GH-90000
        return ((List<Map<String, Object>>) responseBody.get("entities")).stream()
            .map(entity -> String.valueOf(entity.get("id")))
            .toList(); // GH-90000
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) { // GH-90000
        return (Map<String, Object>) value; // GH-90000
    }

    private int findFreePort() throws IOException { // GH-90000
        try (ServerSocket socket = new ServerSocket(0)) { // GH-90000
            return socket.getLocalPort(); // GH-90000
        }
    }

    private record ParsedHttpResponse(int statusCode, Map<String, Object> body) {} // GH-90000
}

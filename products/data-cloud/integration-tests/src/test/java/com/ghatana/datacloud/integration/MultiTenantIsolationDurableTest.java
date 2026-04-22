/*
 * Copyright (c) 2026 Ghatana Inc.
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-Tenant Isolation Durable Tests
 *
 * Proves tenant isolation through the live launcher against real durable providers
 * (PostgreSQL, Kafka) using Testcontainers. This test validates tenant isolation logic
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

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String COLLECTION = "tenant_isolation_entities_durable";

    private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:16-alpine"))
        .withDatabaseName("datacloud_test")
        .withUsername("test_user")
        .withPassword("test_pass");

    private static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private DataCloudClient client;
    private DataCloudHttpServer server;
    private int port;

    static {
        POSTGRESQL.start();
        KAFKA.start();
    }

    @BeforeEach
    void setUp() throws Exception {
        DataCloudConfig config = DataCloudConfig.builder()
            .profile(DataCloudProfile.DISTRIBUTED)
            .customConfig(Map.of(
                "postgresql.url", POSTGRESQL.getJdbcUrl(),
                "postgresql.username", POSTGRESQL.getUsername(),
                "postgresql.password", POSTGRESQL.getPassword(),
                "kafka.bootstrapServers", KAFKA.getBootstrapServers()
            ))
            .build();
        client = DataCloud.create(config);
        port = findFreePort();
        server = new DataCloudHttpServer(client, port);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (client != null) {
            client.close();
        }
    }

    @Test
    @DisplayName("query endpoint returns only the requesting tenant's entities from PostgreSQL")
    void queryEndpointReturnsOnlyTheRequestingTenantsEntitiesFromPostgreSQL() throws Exception {
        String tenantAId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION,
            Map.of("name", "tenant-a-doc", "owner", "tenant-a"), "tenant-a").body().get("id"));
        String tenantBId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION,
            Map.of("name", "tenant-b-doc", "owner", "tenant-b"), "tenant-b").body().get("id"));

        ParsedHttpResponse tenantAQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?limit=10",
            null, "tenant-a");
        ParsedHttpResponse tenantBQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?limit=10",
            null, "tenant-b");

        assertThat(tenantAQuery.statusCode()).isEqualTo(200);
        assertThat(tenantBQuery.statusCode()).isEqualTo(200);
        assertThat(entityIds(tenantAQuery.body())).containsExactly(tenantAId);
        assertThat(entityIds(tenantBQuery.body())).containsExactly(tenantBId);
    }

    @Test
    @DisplayName("cross-tenant reads and deletes do not expose another tenant's entity in PostgreSQL")
    void crossTenantReadsAndDeletesDoNotExposeAnotherTenantsEntityInPostgreSQL() throws Exception {
        String tenantAId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION,
            Map.of("name", "secret-doc", "classification", "private"), "tenant-a").body().get("id"));

        ParsedHttpResponse crossTenantRead = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + tenantAId,
            null, "tenant-b");
        ParsedHttpResponse crossTenantDelete = sendJson("DELETE", "/api/v1/entities/" + COLLECTION + "/" + tenantAId,
            null, "tenant-b");
        ParsedHttpResponse ownerRead = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + tenantAId,
            null, "tenant-a");

        assertThat(crossTenantRead.statusCode()).isEqualTo(404);
        assertThat(crossTenantDelete.statusCode()).isEqualTo(404);
        assertThat(ownerRead.statusCode()).isEqualTo(200);
        assertThat(ownerRead.body()).containsEntry("id", tenantAId);
        assertThat(asMap(ownerRead.body().get("data"))).containsEntry("classification", "private");
    }

    @Test
    @DisplayName("tenant isolation persists across server restarts with PostgreSQL")
    void tenantIsolationPersistsAcrossServerRestartsWithPostgreSQL() throws Exception {
        String tenantAId = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION,
            Map.of("name", "persistent-doc", "owner", "tenant-a"), "tenant-a").body().get("id"));

        // Stop and restart server
        server.stop();
        Thread.sleep(1000); // Wait for graceful shutdown
        server = new DataCloudHttpServer(client, port);
        server.start();
        Thread.sleep(1000); // Wait for startup

        // Verify tenant A can still access their entity
        ParsedHttpResponse ownerRead = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + tenantAId,
            null, "tenant-a");
        assertThat(ownerRead.statusCode()).isEqualTo(200);
        assertThat(ownerRead.body()).containsEntry("id", tenantAId);

        // Verify tenant B still cannot access tenant A's entity
        ParsedHttpResponse crossTenantRead = sendJson("GET", "/api/v1/entities/" + COLLECTION + "/" + tenantAId,
            null, "tenant-b");
        assertThat(crossTenantRead.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("concurrent tenant operations maintain isolation in PostgreSQL")
    void concurrentTenantOperationsMaintainIsolationInPostgreSQL() throws Exception {
        List<Thread> threads = new ArrayList<>();
        List<String> tenantAIds = new ArrayList<>();
        List<String> tenantBIds = new ArrayList<>();

        // Create entities for tenant A concurrently
        for (int i = 0; i < 10; i++) {
            final int index = i;
            Thread thread = new Thread(() -> {
                try {
                    String id = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION,
                        Map.of("name", "tenant-a-doc-" + index, "owner", "tenant-a"), "tenant-a")
                        .body().get("id"));
                    synchronized (tenantAIds) {
                        tenantAIds.add(id);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            threads.add(thread);
            thread.start();
        }

        // Create entities for tenant B concurrently
        for (int i = 0; i < 10; i++) {
            final int index = i;
            Thread thread = new Thread(() -> {
                try {
                    String id = String.valueOf(sendJson("POST", "/api/v1/entities/" + COLLECTION,
                        Map.of("name", "tenant-b-doc-" + index, "owner", "tenant-b"), "tenant-b")
                        .body().get("id"));
                    synchronized (tenantBIds) {
                        tenantBIds.add(id);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(10000);
        }

        // Verify tenant A can only see their entities
        ParsedHttpResponse tenantAQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?limit=100",
            null, "tenant-a");
        assertThat(tenantAQuery.statusCode()).isEqualTo(200);
        assertThat(entityIds(tenantAQuery.body())).containsExactlyInAnyOrderElementsOf(tenantAIds);

        // Verify tenant B can only see their entities
        ParsedHttpResponse tenantBQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?limit=100",
            null, "tenant-b");
        assertThat(tenantBQuery.statusCode()).isEqualTo(200);
        assertThat(entityIds(tenantBQuery.body())).containsExactlyInAnyOrderElementsOf(tenantBIds);
    }

    @Test
    @DisplayName("tenant-specific indexes work correctly in PostgreSQL")
    void tenantSpecificIndexesWorkCorrectlyInPostgreSQL() throws Exception {
        // Create multiple entities for tenant A
        for (int i = 0; i < 5; i++) {
            sendJson("POST", "/api/v1/entities/" + COLLECTION,
                Map.of("name", "tenant-a-doc-" + i, "owner", "tenant-a", "category", "cat-" + (i % 2)), "tenant-a");
        }

        // Create entities for tenant B with same categories
        for (int i = 0; i < 5; i++) {
            sendJson("POST", "/api/v1/entities/" + COLLECTION,
                Map.of("name", "tenant-b-doc-" + i, "owner", "tenant-b", "category", "cat-" + (i % 2)), "tenant-b");
        }

        // Query tenant A entities by category
        ParsedHttpResponse tenantAQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?filter=category:cat-0&limit=100",
            null, "tenant-a");

        // Query tenant B entities by category
        ParsedHttpResponse tenantBQuery = sendJson("GET", "/api/v1/entities/" + COLLECTION + "?filter=category:cat-0&limit=100",
            null, "tenant-b");

        assertThat(tenantAQuery.statusCode()).isEqualTo(200);
        assertThat(tenantBQuery.statusCode()).isEqualTo(200);
        assertThat(entityIds(tenantAQuery.body())).hasSize(3); // Should only get tenant A's entities
        assertThat(entityIds(tenantBQuery.body())).hasSize(3); // Should only get tenant B's entities
    }

    private ParsedHttpResponse sendJson(
        String method,
        String path,
        Map<String, Object> body,
        String tenantId
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Accept", "application/json")
            .header("X-Tenant-Id", tenantId);

        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> parsedBody = response.body() == null || response.body().isBlank()
            ? Map.of()
            : objectMapper.readValue(response.body(), MAP_TYPE);

        return new ParsedHttpResponse(response.statusCode(), parsedBody);
    }

    @SuppressWarnings("unchecked")
    private List<String> entityIds(Map<String, Object> responseBody) {
        return ((List<Map<String, Object>>) responseBody.get("entities")).stream()
            .map(entity -> String.valueOf(entity.get("id")))
            .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private record ParsedHttpResponse(int statusCode, Map<String, Object> body) {}
}

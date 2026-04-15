/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.sdk.smoke;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import com.ghatana.datacloud.sdk.generated.DataCloudJavaSdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the generated Data-Cloud Java SDK against a running launcher.
 *
 * @doc.type    class
 * @doc.purpose SDK smoke tests against a live Data-Cloud HTTP server
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("SDK Smoke Tests")
@Tag("smoke")
class SDKSmokeTest {

    private DataCloudClient backendClient;
    private DataCloudHttpServer server;
    private DataCloudJavaSdk sdk;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        backendClient = DataCloud.forTesting();
        port = findFreePort();
        server = new DataCloudHttpServer(backendClient, port);
        server.start();
        sdk = new DataCloudJavaSdk("http://localhost:" + port, "smoke-tenant");
    }

    @AfterEach
    void tearDown() {
        if (sdk != null) {
            sdk.close();
        }
        if (server != null) {
            server.stop();
        }
        if (backendClient != null) {
            backendClient.close();
        }
    }

    @Test
    @DisplayName("generated SDK reports health from the running launcher")
    void generatedSdkReportsHealth() {
        Map<String, Object> response = sdk.health();

        assertThat(response).containsEntry("status", "UP");
    }

    @Test
    @DisplayName("generated SDK performs entity CRUD round-trip against running launcher")
    void generatedSdkPerformsEntityCrudRoundTrip() {
        Map<String, Object> created = sdk.createEntity("sdk_smoke_entities", Map.of("name", "Ada", "role", "admin"));
        String entityId = created.get("id").toString();

        Map<String, Object> fetched = sdk.getEntity("sdk_smoke_entities", entityId);
        Map<String, Object> queried = sdk.queryEntities("sdk_smoke_entities", 10);
        Map<String, Object> deleted = sdk.deleteEntity("sdk_smoke_entities", entityId);
        Object data = fetched.get("data");
        Object entities = queried.get("entities");
        List<String> entityIds = asObjectList(entities).stream()
            .map(item -> ((Map<?, ?>) item).get("id"))
            .map(String::valueOf)
            .toList();

        assertThat(created).containsEntry("collection", "sdk_smoke_entities");
        assertThat(fetched).containsEntry("id", entityId);
        assertThat(data).isInstanceOf(Map.class);
        assertThat(asObjectMap(data).get("name")).isEqualTo("Ada");
        assertThat(queried).containsEntry("count", 1);
        assertThat(entities).isInstanceOf(List.class);
        assertThat(entityIds).contains(entityId);
        assertThat(deleted).containsEntry("deleted", true);
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asObjectMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asObjectList(Object value) {
        return (List<Object>) value;
    }
}

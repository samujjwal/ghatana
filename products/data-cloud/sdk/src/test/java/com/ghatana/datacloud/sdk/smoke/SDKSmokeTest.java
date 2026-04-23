/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() throws Exception { // GH-90000
        backendClient = DataCloud.forTesting(); // GH-90000
        port = findFreePort(); // GH-90000
        server = new DataCloudHttpServer(backendClient, port); // GH-90000
        server.start(); // GH-90000
        sdk = new DataCloudJavaSdk("http://localhost:" + port, "smoke-tenant"); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (sdk != null) { // GH-90000
            sdk.close(); // GH-90000
        }
        if (server != null) { // GH-90000
            server.stop(); // GH-90000
        }
        if (backendClient != null) { // GH-90000
            backendClient.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("generated SDK reports health from the running launcher")
    void generatedSdkReportsHealth() { // GH-90000
        Map<String, Object> response = sdk.health(); // GH-90000

        assertThat(response).containsEntry("status", "UP"); // GH-90000
    }

    @Test
    @DisplayName("generated SDK performs entity CRUD round-trip against running launcher")
    void generatedSdkPerformsEntityCrudRoundTrip() { // GH-90000
        Map<String, Object> created = sdk.createEntity("sdk_smoke_entities", Map.of("name", "Ada", "role", "admin")); // GH-90000
        String entityId = created.get("id").toString();

        Map<String, Object> fetched = sdk.getEntity("sdk_smoke_entities", entityId); // GH-90000
        Map<String, Object> queried = sdk.queryEntities("sdk_smoke_entities", 10); // GH-90000
        Map<String, Object> deleted = sdk.deleteEntity("sdk_smoke_entities", entityId); // GH-90000
        Object data = fetched.get("data");
        Object entities = queried.get("entities");
        List<String> entityIds = asObjectList(entities).stream() // GH-90000
            .map(item -> ((Map<?, ?>) item).get("id"))
            .map(String::valueOf) // GH-90000
            .toList(); // GH-90000

        assertThat(created).containsEntry("collection", "sdk_smoke_entities"); // GH-90000
        assertThat(fetched).containsEntry("id", entityId); // GH-90000
        assertThat(data).isInstanceOf(Map.class); // GH-90000
        assertThat(asObjectMap(data).get("name")).isEqualTo("Ada");
        assertThat(queried).containsEntry("count", 1); // GH-90000
        assertThat(entities).isInstanceOf(List.class); // GH-90000
        assertThat(entityIds).contains(entityId); // GH-90000
        assertThat(deleted).containsEntry("deleted", true); // GH-90000
    }

    private int findFreePort() throws IOException { // GH-90000
        try (ServerSocket socket = new ServerSocket(0)) { // GH-90000
            return socket.getLocalPort(); // GH-90000
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asObjectMap(Object value) { // GH-90000
        return (Map<String, Object>) value; // GH-90000
    }

    @SuppressWarnings("unchecked")
    private List<Object> asObjectList(Object value) { // GH-90000
        return (List<Object>) value; // GH-90000
    }
}

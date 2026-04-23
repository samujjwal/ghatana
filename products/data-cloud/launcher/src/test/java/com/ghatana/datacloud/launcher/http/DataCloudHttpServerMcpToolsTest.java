package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.plugins.knowledgegraph.KnowledgeGraphPlugin;
import com.ghatana.datacloud.plugins.lineage.LineagePlugin;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Integration tests for MCP tool discovery and invocation over the Data Cloud HTTP surface
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – MCP Tools")
class DataCloudHttpServerMcpToolsTest {

    private static final String TENANT_ID = "tenant-mcp";
    private static final String TEST_JWT_SECRET = "0123456789abcdef0123456789abcdef";

    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000
    private DataCloudClient client;
    private EntityStore entityStore;
    private LineagePlugin lineagePlugin;
    private KnowledgeGraphPlugin knowledgeGraphPlugin;
    private DataCloudHttpServer server;
    private HttpClient httpClient;
    private int port;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        client = mock(DataCloudClient.class); // GH-90000
        entityStore = mock(EntityStore.class); // GH-90000
        lineagePlugin = mock(LineagePlugin.class); // GH-90000
        knowledgeGraphPlugin = mock(KnowledgeGraphPlugin.class); // GH-90000
        httpClient = HttpClient.newHttpClient(); // GH-90000

        when(client.entityStore()).thenReturn(entityStore); // GH-90000

        DataCloudClient.Entity collectionMetadata = new DataCloudClient.Entity( // GH-90000
            "orders",
            "dc_collections",
            Map.of( // GH-90000
                "name", "orders",
                "schema", Map.of("fields", List.of(Map.of("name", "orderId", "type", "string", "required", true))) // GH-90000
            ),
            Instant.parse("2026-04-18T08:55:00Z"),
            Instant.parse("2026-04-18T08:56:00Z"),
            1L
        );
        DataCloudClient.Entity order1 = new DataCloudClient.Entity( // GH-90000
            "order-1",
            "orders",
            Map.of("orderId", "order-1", "status", "ready"), // GH-90000
            Instant.parse("2026-04-18T08:57:00Z"),
            Instant.parse("2026-04-18T08:58:00Z"),
            1L
        );

        when(client.findById(TENANT_ID, "dc_collections", "orders")) // GH-90000
            .thenReturn(Promise.of(Optional.of(collectionMetadata))); // GH-90000
        when(client.query(eq(TENANT_ID), eq("orders"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of(order1))); // GH-90000
        when(client.query(eq(TENANT_ID), eq("_governance_retention_policies"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of())); // GH-90000
        when(entityStore.count(any(TenantContext.class), any(EntityStore.QuerySpec.class))).thenReturn(Promise.of(1L)); // GH-90000
        when(lineagePlugin.getUpstreamLineage(TENANT_ID, "orders")) // GH-90000
            .thenReturn(Promise.of(Set.of())); // GH-90000
        when(lineagePlugin.getDownstreamLineage(TENANT_ID, "orders")) // GH-90000
            .thenReturn(Promise.of(Set.of())); // GH-90000
        when(knowledgeGraphPlugin.queryEdges(any())).thenReturn(Promise.of(List.of())); // GH-90000
        when(client.appendEvent(eq(TENANT_ID), any(DataCloudClient.Event.class))) // GH-90000
            .thenReturn(Promise.of(DataCloudClient.Offset.of(42L))); // GH-90000

        port = randomPort(); // GH-90000
        server = new DataCloudHttpServer(client, port) // GH-90000
            .withJwtProvider(jwtProvider()) // GH-90000
            .withLineagePlugin(lineagePlugin) // GH-90000
            .withKnowledgeGraphPlugin(knowledgeGraphPlugin); // GH-90000
        server.start(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) { // GH-90000
            server.stop(); // GH-90000
        }
    }

    @Test
    void listsDiscoverableMcpTools() throws IOException, InterruptedException { // GH-90000
        HttpRequest request = authenticatedRequest("/mcp/v1/tools").GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // GH-90000

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        JsonNode body = mapper.readTree(response.body()); // GH-90000
        assertThat(body.path("tools")).hasSize(4);
        assertThat(body.path("tools").get(0).path("name").asText()).isEqualTo("data_cloud_get_context");
    }

    @Test
    void rejectsUnauthenticatedMcpDiscoveryRequests() throws IOException, InterruptedException { // GH-90000
        HttpRequest request = HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://localhost:" + port + "/mcp/v1/tools?tenantId=" + TENANT_ID)) // GH-90000
            .GET() // GH-90000
            .build(); // GH-90000

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // GH-90000

        assertThat(response.statusCode()).isEqualTo(401); // GH-90000
    }

    @Test
    void executesJsonRpcToolCallForCollectionContext() throws IOException, InterruptedException { // GH-90000
        String body = """
            {
              "jsonrpc": "2.0",
              "id": "req-1",
              "method": "tools/call",
              "params": {
                "name": "data_cloud_get_context",
                "arguments": {
                  "collection": "orders"
                }
              }
            }
            """;

        HttpRequest request = authenticatedRequest("/mcp/v1/tools")
            .header("Content-Type", "application/json") // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
            .build(); // GH-90000

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // GH-90000

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        JsonNode json = mapper.readTree(response.body()); // GH-90000
        assertThat(json.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(json.path("result").path("content").get(0).path("text").asText()).contains("orders");
        assertThat(json.path("result").path("content").get(0).path("text").asText()).contains("entityCount");
    }

    private HttpRequest.Builder authenticatedRequest(String path) { // GH-90000
        return HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://localhost:" + port + path + "?tenantId=" + TENANT_ID)) // GH-90000
            .header("Authorization", "Bearer " + createToken()); // GH-90000
    }

    private JwtTokenProvider jwtProvider() { // GH-90000
        return JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L); // GH-90000
    }

    private String createToken() { // GH-90000
        return jwtProvider().createToken("integration-user", List.of("viewer"), Map.of("tenant_id", TENANT_ID));
    }

    private int randomPort() throws IOException { // GH-90000
        try (ServerSocket socket = new ServerSocket(0)) { // GH-90000
            return socket.getLocalPort(); // GH-90000
        }
    }
}
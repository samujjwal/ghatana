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

    private final ObjectMapper mapper = new ObjectMapper(); 
    private DataCloudClient client;
    private EntityStore entityStore;
    private LineagePlugin lineagePlugin;
    private KnowledgeGraphPlugin knowledgeGraphPlugin;
    private DataCloudHttpServer server;
    private HttpClient httpClient;
    private int port;

    @BeforeEach
    void setUp() throws Exception { 
        client = mock(DataCloudClient.class); 
        entityStore = mock(EntityStore.class); 
        lineagePlugin = mock(LineagePlugin.class); 
        knowledgeGraphPlugin = mock(KnowledgeGraphPlugin.class); 
        httpClient = HttpClient.newHttpClient(); 

        when(client.entityStore()).thenReturn(entityStore); 

        DataCloudClient.Entity collectionMetadata = new DataCloudClient.Entity( 
            "orders",
            "dc_collections",
            Map.of( 
                "name", "orders",
                "schema", Map.of("fields", List.of(Map.of("name", "orderId", "type", "string", "required", true))) 
            ),
            Instant.parse("2026-04-18T08:55:00Z"),
            Instant.parse("2026-04-18T08:56:00Z"),
            1L
        );
        DataCloudClient.Entity order1 = new DataCloudClient.Entity( 
            "order-1",
            "orders",
            Map.of("orderId", "order-1", "status", "ready"), 
            Instant.parse("2026-04-18T08:57:00Z"),
            Instant.parse("2026-04-18T08:58:00Z"),
            1L
        );

        when(client.findById(TENANT_ID, "dc_collections", "orders")) 
            .thenReturn(Promise.of(Optional.of(collectionMetadata))); 
        when(client.query(eq(TENANT_ID), eq("orders"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of(order1))); 
        when(client.query(eq(TENANT_ID), eq("_governance_retention_policies"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of())); 
        when(entityStore.count(any(TenantContext.class), any(EntityStore.QuerySpec.class))).thenReturn(Promise.of(1L)); 
        when(lineagePlugin.getUpstreamLineage(TENANT_ID, "orders")) 
            .thenReturn(Promise.of(Set.of())); 
        when(lineagePlugin.getDownstreamLineage(TENANT_ID, "orders")) 
            .thenReturn(Promise.of(Set.of())); 
        when(knowledgeGraphPlugin.queryEdges(any())).thenReturn(Promise.of(List.of())); 
        when(client.appendEvent(eq(TENANT_ID), any(DataCloudClient.Event.class))) 
            .thenReturn(Promise.of(DataCloudClient.Offset.of(42L))); 

        port = randomPort(); 
        server = new DataCloudHttpServer(client, port) 
            .withJwtProvider(jwtProvider()) 
            .withLineagePlugin(lineagePlugin) 
            .withKnowledgeGraphPlugin(knowledgeGraphPlugin); 
        server.start(); 
    }

    @AfterEach
    void tearDown() { 
        if (server != null) { 
            server.stop(); 
        }
    }

    @Test
    void listsDiscoverableMcpTools() throws IOException, InterruptedException { 
        HttpRequest request = authenticatedRequest("/mcp/v1/tools").GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()); 

        assertThat(response.statusCode()).isEqualTo(200); 
        JsonNode body = mapper.readTree(response.body()); 
        assertThat(body.path("tools")).hasSize(4);
        assertThat(body.path("tools").get(0).path("name").asText()).isEqualTo("data_cloud_get_context");
    }

    @Test
    void rejectsUnauthenticatedMcpDiscoveryRequests() throws IOException, InterruptedException { 
        HttpRequest request = HttpRequest.newBuilder() 
            .uri(URI.create("http://localhost:" + port + "/mcp/v1/tools?tenantId=" + TENANT_ID)) 
            .GET() 
            .build(); 

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()); 

        assertThat(response.statusCode()).isEqualTo(401); 
    }

    @Test
    void executesJsonRpcToolCallForCollectionContext() throws IOException, InterruptedException { 
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
            .header("Content-Type", "application/json") 
            .POST(HttpRequest.BodyPublishers.ofString(body)) 
            .build(); 

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()); 

        assertThat(response.statusCode()).isEqualTo(200); 
        JsonNode json = mapper.readTree(response.body()); 
        assertThat(json.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(json.path("result").path("content").get(0).path("text").asText()).contains("orders");
        assertThat(json.path("result").path("content").get(0).path("text").asText()).contains("entityCount");
    }

    private HttpRequest.Builder authenticatedRequest(String path) { 
        return HttpRequest.newBuilder() 
            .uri(URI.create("http://localhost:" + port + path + "?tenantId=" + TENANT_ID)) 
            .header("Authorization", "Bearer " + createToken()); 
    }

    private JwtTokenProvider jwtProvider() { 
        return JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L); 
    }

    private String createToken() { 
        return jwtProvider().createToken("integration-user", List.of("viewer"), Map.of("tenant_id", TENANT_ID));
    }

    private int randomPort() throws IOException { 
        try (ServerSocket socket = new ServerSocket(0)) { 
            return socket.getLocalPort(); 
        }
    }
}
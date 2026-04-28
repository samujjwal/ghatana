package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.plugins.knowledgegraph.KnowledgeGraphPlugin;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.datacloud.plugins.lineage.LineagePlugin;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.datacloud.spi.EventLogStore;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Integration test coverage for the unified collection context endpoint
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Collection Context Endpoint")
class DataCloudHttpServerCollectionContextTest {

    private static final String TENANT_ID = "tenant-ctx";
    private static final String TEST_JWT_SECRET = "0123456789abcdef0123456789abcdef";

    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000
    private DataCloudClient client;
    private EntityStore entityStore;
    private LineagePlugin lineagePlugin;
    private KnowledgeGraphPlugin knowledgeGraphPlugin;
    private DataCloudHttpServer server;
    private HttpClient httpClient;
    private int port;
    private AtomicReference<List<DataCloudClient.Entity>> orderEntities;
    private DataCloudClient.Entity collectionMetadata;
    private DataCloudClient.Entity retentionPolicy;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        entityStore = mock(EntityStore.class); // GH-90000
        lineagePlugin = mock(LineagePlugin.class); // GH-90000
        knowledgeGraphPlugin = mock(KnowledgeGraphPlugin.class); // GH-90000
        httpClient = HttpClient.newHttpClient(); // GH-90000

        collectionMetadata = DataCloudClient.Entity.of( // GH-90000
            "orders",
            "dc_collections",
            Map.of( // GH-90000
                "name", "orders",
                "schema", Map.of( // GH-90000
                    "fields", List.of( // GH-90000
                        Map.of("name", "orderId", "type", "string", "required", true), // GH-90000
                        Map.of("name", "email", "type", "string", "required", false), // GH-90000
                        Map.of("name", "customerId", "type", "string", "required", true) // GH-90000
                    )
                )
            )
        );
        collectionMetadata = new DataCloudClient.Entity( // GH-90000
            collectionMetadata.id(), // GH-90000
            collectionMetadata.collection(), // GH-90000
            collectionMetadata.data(), // GH-90000
            Instant.parse("2026-04-18T08:55:00Z"),
            Instant.parse("2026-04-18T08:56:00Z"),
            1L
        );

        DataCloudClient.Entity order1 = DataCloudClient.Entity.of( // GH-90000
            "order-1",
            "orders",
            Map.of("orderId", "order-1", "email", "alice@example.com", "customerId", "cust-1") // GH-90000
        );
        order1 = new DataCloudClient.Entity(order1.id(), order1.collection(), order1.data(), // GH-90000
            Instant.parse("2026-04-18T08:57:00Z"), Instant.parse("2026-04-18T08:58:00Z"), 1L);
        DataCloudClient.Entity order2 = DataCloudClient.Entity.of( // GH-90000
            "order-2",
            "orders",
            Map.of("orderId", "order-2", "customerId", "cust-1") // GH-90000
        );
        order2 = new DataCloudClient.Entity(order2.id(), order2.collection(), order2.data(), // GH-90000
            Instant.parse("2026-04-18T08:58:00Z"), Instant.parse("2026-04-18T08:59:00Z"), 1L);
        retentionPolicy = DataCloudClient.Entity.of( // GH-90000
            "policy-orders",
            "_governance_retention_policies",
            Map.of( // GH-90000
                "collection", "orders",
                "tier", "compliance",
                "status", "active",
                "piiFields", List.of("email"),
                "reason", "Contains customer communications"
            )
        );
        retentionPolicy = new DataCloudClient.Entity(retentionPolicy.id(), retentionPolicy.collection(), retentionPolicy.data(), // GH-90000
            Instant.parse("2026-04-18T08:00:00Z"), Instant.parse("2026-04-18T08:30:00Z"), 1L);
        orderEntities = new AtomicReference<>(List.of(order1, order2)); // GH-90000
        client = new StubDataCloudClient(); // GH-90000

        when(entityStore.count(any(TenantContext.class), argThat(querySpec -> "orders".equals(querySpec.collection())))) // GH-90000
            .thenAnswer(invocation -> Promise.of((long) orderEntities.get().size())); // GH-90000
        when(entityStore.count(any(TenantContext.class), argThat(querySpec -> "missing".equals(querySpec.collection())))) // GH-90000
            .thenReturn(Promise.of(0L)); // GH-90000
        when(lineagePlugin.getUpstreamLineage(TENANT_ID, "orders")) // GH-90000
            .thenReturn(Promise.of(Set.of("tenant-ctx:raw_orders")));
        when(lineagePlugin.getDownstreamLineage(TENANT_ID, "orders")) // GH-90000
            .thenReturn(Promise.of(Set.of("tenant-ctx:invoice_snapshots")));
        when(knowledgeGraphPlugin.getNeighbors(eq("orders"), anyInt(), eq(TENANT_ID)))
            .thenAnswer(invocation -> { // GH-90000
                int depth = invocation.getArgument(1, Integer.class); // GH-90000
                if (depth <= 1) { // GH-90000
                    return Promise.of(List.of(GraphNode.builder().id("customers").type("ENTITY").tenantId(TENANT_ID).build()));
                }
                return Promise.of(List.of( // GH-90000
                    GraphNode.builder().id("customers").type("ENTITY").tenantId(TENANT_ID).build(),
                    GraphNode.builder().id("accounts").type("ENTITY").tenantId(TENANT_ID).build(),
                    GraphNode.builder().id("regions").type("ENTITY").tenantId(TENANT_ID).build()
                ));
            });
        when(knowledgeGraphPlugin.getNodeEdges("orders", TENANT_ID)) // GH-90000
            .thenReturn(Promise.of(List.of(GraphEdge.builder() // GH-90000
                .id("edge-1")
                .sourceNodeId("orders")
                .targetNodeId("customers")
                .relationshipType("BELONGS_TO")
                .tenantId(TENANT_ID) // GH-90000
                .build()))); // GH-90000
        when(knowledgeGraphPlugin.getNodeEdges("customers", TENANT_ID)) // GH-90000
            .thenReturn(Promise.of(List.of(GraphEdge.builder() // GH-90000
                .id("edge-2")
                .sourceNodeId("customers")
                .targetNodeId("accounts")
                .relationshipType("ASSOCIATED_WITH")
                .tenantId(TENANT_ID) // GH-90000
                .build()))); // GH-90000
        when(knowledgeGraphPlugin.getNodeEdges("accounts", TENANT_ID)) // GH-90000
            .thenReturn(Promise.of(List.of(GraphEdge.builder() // GH-90000
                .id("edge-3")
                .sourceNodeId("accounts")
                .targetNodeId("regions")
                .relationshipType("OPERATES_IN")
                .tenantId(TENANT_ID) // GH-90000
                .build()))); // GH-90000
        when(knowledgeGraphPlugin.getNodeEdges("regions", TENANT_ID)) // GH-90000
            .thenReturn(Promise.of(List.of())); // GH-90000
        when(knowledgeGraphPlugin.queryEdges(any())) // GH-90000
            .thenReturn(Promise.of(List.of())); // GH-90000

        port = randomPort(); // GH-90000
        server = new DataCloudHttpServer(client, port) // GH-90000
            .withJwtProvider(jwtProvider()) // GH-90000
            .withLineagePlugin(lineagePlugin) // GH-90000
            .withKnowledgeGraphPlugin(knowledgeGraphPlugin) // GH-90000
            ;
        server.start(); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        if (server != null) { // GH-90000
            server.stop(); // GH-90000
        }
    }

    @Test
    void returnsUnifiedCollectionContextDocument() throws IOException, InterruptedException { // GH-90000
        HttpRequest request = authorizedGet("/api/v1/context/orders?tenantId=" + TENANT_ID); // GH-90000

        HttpResponse<String> response = send(request); // GH-90000

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        JsonNode body = mapper.readTree(response.body()); // GH-90000
        assertThat(body.path("collection").asText()).isEqualTo("orders");
        assertThat(body.path("schema").path("fields")).hasSize(3);
        assertThat(body.path("lineage").path("upstream").get(0).asText()).isEqualTo("raw_orders");
        assertThat(body.path("governance").path("retentionTier").asText()).isEqualTo("compliance");
        assertThat(body.path("governance").path("piiFields").get(0).asText()).isEqualTo("email");
        assertThat(body.path("freshness").path("lastEntityUpdatedAt").asText()).isEqualTo("2026-04-18T08:59:00Z");
        assertThat(body.path("generationTimeMs").asLong()).isLessThan(5000L);
        assertThat(body.path("statisticalProfile").path("entityCount").asLong()).isEqualTo(2L);
        assertThat(body.path("relationships").get(0).path("type").asText()).isEqualTo("BELONGS_TO");
    }

    @Test
    void returnsNotFoundForUnknownCollection() throws IOException, InterruptedException { // GH-90000
        HttpRequest request = authorizedGet("/api/v1/context/missing?tenantId=" + TENANT_ID); // GH-90000

        HttpResponse<String> response = send(request); // GH-90000

        assertThat(response.statusCode()).isEqualTo(404); // GH-90000
        assertThat(response.body()).containsIgnoringCase("not found");
    }

    @Test
    void updatesContextDocumentWhenUnderlyingEntitiesChange() throws IOException, InterruptedException { // GH-90000
        HttpRequest request = authorizedGet("/api/v1/context/orders?tenantId=" + TENANT_ID); // GH-90000

        HttpResponse<String> initialResponse = send(request); // GH-90000
        assertThat(initialResponse.statusCode()).isEqualTo(200); // GH-90000
        JsonNode initialBody = mapper.readTree(initialResponse.body()); // GH-90000
        assertThat(initialBody.path("statisticalProfile").path("entityCount").asLong()).isEqualTo(2L);

        DataCloudClient.Entity order3 = DataCloudClient.Entity.of( // GH-90000
            "order-3",
            "orders",
            Map.of("orderId", "order-3", "customerId", "cust-2", "email", "bob@example.com") // GH-90000
        );
        order3 = new DataCloudClient.Entity(order3.id(), order3.collection(), order3.data(), // GH-90000
            Instant.parse("2026-04-18T09:00:00Z"), Instant.parse("2026-04-18T09:01:00Z"), 1L);
        orderEntities.set(List.of(orderEntities.get().get(0), orderEntities.get().get(1), order3)); // GH-90000

        HttpResponse<String> refreshedResponse = send(request); // GH-90000
        assertThat(refreshedResponse.statusCode()).isEqualTo(200); // GH-90000
        JsonNode refreshedBody = mapper.readTree(refreshedResponse.body()); // GH-90000
        assertThat(refreshedBody.path("statisticalProfile").path("entityCount").asLong()).isEqualTo(3L);
        assertThat(refreshedBody.path("freshness").path("lastEntityUpdatedAt").asText()).isEqualTo("2026-04-18T09:01:00Z");
    }

    @Test
    void traversesRelationshipsUpToThreeLevels() throws IOException, InterruptedException { // GH-90000
        HttpRequest request = authorizedGet("/api/v1/context/orders?tenantId=" + TENANT_ID + "&depth=3"); // GH-90000

        HttpResponse<String> response = send(request); // GH-90000

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        JsonNode body = mapper.readTree(response.body()); // GH-90000
        assertThat(body.path("relationshipDepth").asInt()).isEqualTo(3);
        assertThat(body.path("relationships")).hasSize(3);
        assertThat(body.path("relationships").get(0).path("depth").asInt()).isEqualTo(1);
        assertThat(body.path("relationships").get(2).path("target").asText()).isEqualTo("regions");
        assertThat(body.path("relationships").get(2).path("depth").asInt()).isEqualTo(3);
    }

    private JwtTokenProvider jwtProvider() { // GH-90000
        return JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L); // GH-90000
    }

    private String createToken() { // GH-90000
        return jwtProvider().createToken("integration-user", List.of("viewer"), Map.of("tenant_id", TENANT_ID));
    }

    private HttpRequest authorizedGet(String path) { // GH-90000
        return HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://localhost:" + port + path)) // GH-90000
            .header("Authorization", "Bearer " + createToken()) // GH-90000
            .GET() // GH-90000
            .build(); // GH-90000
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException { // GH-90000
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // GH-90000
        } catch (IOException firstFailure) { // GH-90000
            httpClient = HttpClient.newHttpClient(); // GH-90000
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // GH-90000
        }
    }

    private int randomPort() throws IOException { // GH-90000
        try (ServerSocket socket = new ServerSocket(0)) { // GH-90000
            return socket.getLocalPort(); // GH-90000
        }
    }

    private final class StubDataCloudClient implements DataCloudClient {

        @Override
        public Promise<Entity> save(String tenantId, String collection, Map<String, Object> data) { // GH-90000
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public Promise<Optional<Entity>> findById(String tenantId, String collection, String id) { // GH-90000
            if (!TENANT_ID.equals(tenantId)) { // GH-90000
                return Promise.of(Optional.empty()); // GH-90000
            }
            if ("dc_collections".equals(collection) && "orders".equals(id)) { // GH-90000
                return Promise.of(Optional.of(collectionMetadata)); // GH-90000
            }
            return Promise.of(Optional.empty()); // GH-90000
        }

        @Override
        public Promise<List<Entity>> query(String tenantId, String collection, Query query) { // GH-90000
            if (!TENANT_ID.equals(tenantId)) { // GH-90000
                return Promise.of(List.of()); // GH-90000
            }
            if ("orders".equals(collection)) { // GH-90000
                return Promise.of(orderEntities.get()); // GH-90000
            }
            if ("dc_collections".equals(collection)) { // GH-90000
                return Promise.of(List.of(collectionMetadata)); // GH-90000
            }
            if ("_governance_retention_policies".equals(collection)) { // GH-90000
                return Promise.of(List.of(retentionPolicy)); // GH-90000
            }
            return Promise.of(List.of()); // GH-90000
        }

        @Override
        public Promise<Void> delete(String tenantId, String collection, String id) { // GH-90000
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public Promise<Offset> appendEvent(String tenantId, Event event) { // GH-90000
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public Promise<List<Event>> queryEvents(String tenantId, EventQuery query) { // GH-90000
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public Subscription tailEvents(String tenantId, TailRequest request, java.util.function.Consumer<Event> handler) { // GH-90000
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public void close() { // GH-90000
        }

        @Override
        public EntityStore entityStore() { // GH-90000
            return entityStore;
        }

        @Override
        public EventLogStore eventLogStore() { // GH-90000
            throw new UnsupportedOperationException("Not used in this test");
        }
    }
}
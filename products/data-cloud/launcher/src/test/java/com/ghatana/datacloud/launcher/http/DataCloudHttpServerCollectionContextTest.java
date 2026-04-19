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
import com.ghatana.platform.domain.eventstore.EventLogStore;
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

    private final ObjectMapper mapper = new ObjectMapper();
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
    void setUp() throws Exception {
        entityStore = mock(EntityStore.class);
        lineagePlugin = mock(LineagePlugin.class);
        knowledgeGraphPlugin = mock(KnowledgeGraphPlugin.class);
        httpClient = HttpClient.newHttpClient();

        collectionMetadata = DataCloudClient.Entity.of(
            "orders",
            "dc_collections",
            Map.of(
                "name", "orders",
                "schema", Map.of(
                    "fields", List.of(
                        Map.of("name", "orderId", "type", "string", "required", true),
                        Map.of("name", "email", "type", "string", "required", false),
                        Map.of("name", "customerId", "type", "string", "required", true)
                    )
                )
            )
        );
        collectionMetadata = new DataCloudClient.Entity(
            collectionMetadata.id(),
            collectionMetadata.collection(),
            collectionMetadata.data(),
            Instant.parse("2026-04-18T08:55:00Z"),
            Instant.parse("2026-04-18T08:56:00Z"),
            1L
        );

        DataCloudClient.Entity order1 = DataCloudClient.Entity.of(
            "order-1",
            "orders",
            Map.of("orderId", "order-1", "email", "alice@example.com", "customerId", "cust-1")
        );
        order1 = new DataCloudClient.Entity(order1.id(), order1.collection(), order1.data(),
            Instant.parse("2026-04-18T08:57:00Z"), Instant.parse("2026-04-18T08:58:00Z"), 1L);
        DataCloudClient.Entity order2 = DataCloudClient.Entity.of(
            "order-2",
            "orders",
            Map.of("orderId", "order-2", "customerId", "cust-1")
        );
        order2 = new DataCloudClient.Entity(order2.id(), order2.collection(), order2.data(),
            Instant.parse("2026-04-18T08:58:00Z"), Instant.parse("2026-04-18T08:59:00Z"), 1L);
        retentionPolicy = DataCloudClient.Entity.of(
            "policy-orders",
            "_governance_retention_policies",
            Map.of(
                "collection", "orders",
                "tier", "compliance",
                "status", "active",
                "piiFields", List.of("email"),
                "reason", "Contains customer communications"
            )
        );
        retentionPolicy = new DataCloudClient.Entity(retentionPolicy.id(), retentionPolicy.collection(), retentionPolicy.data(),
            Instant.parse("2026-04-18T08:00:00Z"), Instant.parse("2026-04-18T08:30:00Z"), 1L);
        orderEntities = new AtomicReference<>(List.of(order1, order2));
        client = new StubDataCloudClient();

        when(entityStore.count(any(TenantContext.class), argThat(querySpec -> "orders".equals(querySpec.collection()))))
            .thenAnswer(invocation -> Promise.of((long) orderEntities.get().size()));
        when(entityStore.count(any(TenantContext.class), argThat(querySpec -> "missing".equals(querySpec.collection()))))
            .thenReturn(Promise.of(0L));
        when(lineagePlugin.getUpstreamLineage(TENANT_ID, "orders"))
            .thenReturn(Promise.of(Set.of("tenant-ctx:raw_orders")));
        when(lineagePlugin.getDownstreamLineage(TENANT_ID, "orders"))
            .thenReturn(Promise.of(Set.of("tenant-ctx:invoice_snapshots")));
        when(knowledgeGraphPlugin.getNeighbors(eq("orders"), anyInt(), eq(TENANT_ID)))
            .thenAnswer(invocation -> {
                int depth = invocation.getArgument(1, Integer.class);
                if (depth <= 1) {
                    return Promise.of(List.of(GraphNode.builder().id("customers").type("ENTITY").tenantId(TENANT_ID).build()));
                }
                return Promise.of(List.of(
                    GraphNode.builder().id("customers").type("ENTITY").tenantId(TENANT_ID).build(),
                    GraphNode.builder().id("accounts").type("ENTITY").tenantId(TENANT_ID).build(),
                    GraphNode.builder().id("regions").type("ENTITY").tenantId(TENANT_ID).build()
                ));
            });
        when(knowledgeGraphPlugin.getNodeEdges("orders", TENANT_ID))
            .thenReturn(Promise.of(List.of(GraphEdge.builder()
                .id("edge-1")
                .sourceNodeId("orders")
                .targetNodeId("customers")
                .relationshipType("BELONGS_TO")
                .tenantId(TENANT_ID)
                .build())));
        when(knowledgeGraphPlugin.getNodeEdges("customers", TENANT_ID))
            .thenReturn(Promise.of(List.of(GraphEdge.builder()
                .id("edge-2")
                .sourceNodeId("customers")
                .targetNodeId("accounts")
                .relationshipType("ASSOCIATED_WITH")
                .tenantId(TENANT_ID)
                .build())));
        when(knowledgeGraphPlugin.getNodeEdges("accounts", TENANT_ID))
            .thenReturn(Promise.of(List.of(GraphEdge.builder()
                .id("edge-3")
                .sourceNodeId("accounts")
                .targetNodeId("regions")
                .relationshipType("OPERATES_IN")
                .tenantId(TENANT_ID)
                .build())));
        when(knowledgeGraphPlugin.getNodeEdges("regions", TENANT_ID))
            .thenReturn(Promise.of(List.of()));
        when(knowledgeGraphPlugin.queryEdges(any()))
            .thenReturn(Promise.of(List.of()));

        port = randomPort();
        server = new DataCloudHttpServer(client, port)
            .withJwtProvider(jwtProvider())
            .withLineagePlugin(lineagePlugin)
            .withKnowledgeGraphPlugin(knowledgeGraphPlugin)
            ;
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void returnsUnifiedCollectionContextDocument() throws IOException, InterruptedException {
        HttpRequest request = authorizedGet("/api/v1/context/orders?tenantId=" + TENANT_ID);

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.path("collection").asText()).isEqualTo("orders");
        assertThat(body.path("schema").path("fields")).hasSize(3);
        assertThat(body.path("lineage").path("upstream").get(0).asText()).isEqualTo("raw_orders");
        assertThat(body.path("governance").path("retentionTier").asText()).isEqualTo("compliance");
        assertThat(body.path("governance").path("piiFields").get(0).asText()).isEqualTo("email");
        assertThat(body.path("freshness").path("lastEntityUpdatedAt").asText()).isEqualTo("2026-04-18T08:59:00Z");
        assertThat(body.path("generationTimeMs").asLong()).isLessThan(250L);
        assertThat(body.path("statisticalProfile").path("entityCount").asLong()).isEqualTo(2L);
        assertThat(body.path("relationships").get(0).path("type").asText()).isEqualTo("BELONGS_TO");
    }

    @Test
    void returnsNotFoundForUnknownCollection() throws IOException, InterruptedException {
        HttpRequest request = authorizedGet("/api/v1/context/missing?tenantId=" + TENANT_ID);

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).containsIgnoringCase("not found");
    }

    @Test
    void updatesContextDocumentWhenUnderlyingEntitiesChange() throws IOException, InterruptedException {
        HttpRequest request = authorizedGet("/api/v1/context/orders?tenantId=" + TENANT_ID);

        HttpResponse<String> initialResponse = send(request);
        assertThat(initialResponse.statusCode()).isEqualTo(200);
        JsonNode initialBody = mapper.readTree(initialResponse.body());
        assertThat(initialBody.path("statisticalProfile").path("entityCount").asLong()).isEqualTo(2L);

        DataCloudClient.Entity order3 = DataCloudClient.Entity.of(
            "order-3",
            "orders",
            Map.of("orderId", "order-3", "customerId", "cust-2", "email", "bob@example.com")
        );
        order3 = new DataCloudClient.Entity(order3.id(), order3.collection(), order3.data(),
            Instant.parse("2026-04-18T09:00:00Z"), Instant.parse("2026-04-18T09:01:00Z"), 1L);
        orderEntities.set(List.of(orderEntities.get().get(0), orderEntities.get().get(1), order3));

        HttpResponse<String> refreshedResponse = send(request);
        assertThat(refreshedResponse.statusCode()).isEqualTo(200);
        JsonNode refreshedBody = mapper.readTree(refreshedResponse.body());
        assertThat(refreshedBody.path("statisticalProfile").path("entityCount").asLong()).isEqualTo(3L);
        assertThat(refreshedBody.path("freshness").path("lastEntityUpdatedAt").asText()).isEqualTo("2026-04-18T09:01:00Z");
    }

    @Test
    void traversesRelationshipsUpToThreeLevels() throws IOException, InterruptedException {
        HttpRequest request = authorizedGet("/api/v1/context/orders?tenantId=" + TENANT_ID + "&depth=3");

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.path("relationshipDepth").asInt()).isEqualTo(3);
        assertThat(body.path("relationships")).hasSize(3);
        assertThat(body.path("relationships").get(0).path("depth").asInt()).isEqualTo(1);
        assertThat(body.path("relationships").get(2).path("target").asText()).isEqualTo("regions");
        assertThat(body.path("relationships").get(2).path("depth").asInt()).isEqualTo(3);
    }

    private JwtTokenProvider jwtProvider() {
        return JwtTokenProviders.fromSharedSecret(TEST_JWT_SECRET, 60000L);
    }

    private String createToken() {
        return jwtProvider().createToken("integration-user", List.of("viewer"), Map.of("tenant_id", TENANT_ID));
    }

    private HttpRequest authorizedGet(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + createToken())
            .GET()
            .build();
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException firstFailure) {
            httpClient = HttpClient.newHttpClient();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private int randomPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private final class StubDataCloudClient implements DataCloudClient {

        @Override
        public Promise<Entity> save(String tenantId, String collection, Map<String, Object> data) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public Promise<Optional<Entity>> findById(String tenantId, String collection, String id) {
            if (!TENANT_ID.equals(tenantId)) {
                return Promise.of(Optional.empty());
            }
            if ("dc_collections".equals(collection) && "orders".equals(id)) {
                return Promise.of(Optional.of(collectionMetadata));
            }
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<List<Entity>> query(String tenantId, String collection, Query query) {
            if (!TENANT_ID.equals(tenantId)) {
                return Promise.of(List.of());
            }
            if ("orders".equals(collection)) {
                return Promise.of(orderEntities.get());
            }
            if ("dc_collections".equals(collection)) {
                return Promise.of(List.of(collectionMetadata));
            }
            if ("_governance_retention_policies".equals(collection)) {
                return Promise.of(List.of(retentionPolicy));
            }
            return Promise.of(List.of());
        }

        @Override
        public Promise<Void> delete(String tenantId, String collection, String id) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public Promise<Offset> appendEvent(String tenantId, Event event) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public Promise<List<Event>> queryEvents(String tenantId, EventQuery query) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public Subscription tailEvents(String tenantId, TailRequest request, java.util.function.Consumer<Event> handler) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public void close() {
        }

        @Override
        public EntityStore entityStore() {
            return entityStore;
        }

        @Override
        public EventLogStore eventLogStore() {
            throw new UnsupportedOperationException("Not used in this test");
        }
    }
}
package com.ghatana.yappc.knowledge.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Validates tenant-scoped knowledge graph query latency and index plans against a seeded PostgreSQL dataset
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
@DisplayName("KGScaleValidation Integration Tests")
public class KGScaleValidationTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-03";
    private static final String PROJECT_ID = "project-02";
    private static final String WORKSPACE_ID = "workspace-04";
    private static final String SOURCE_NODE_ID = "node-000120";
    private static final int P95_TARGET_MS = 500;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg15")
            .withDatabaseName("knowledge_graph_test")
            .withUsername("test")
            .withPassword("test");

    private static KGNodeRepository nodeRepository;
    private static KGEdgeRepository edgeRepository;
    private static DataSource dataSource;

    @BeforeAll
    public static void setUp() throws Exception {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable throwable) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable, "Skipping knowledge graph scale validation because Docker is unavailable");

        POSTGRES.start();

        PGSimpleDataSource pgSimpleDataSource = new PGSimpleDataSource();
        pgSimpleDataSource.setURL(POSTGRES.getJdbcUrl());
        pgSimpleDataSource.setUser(POSTGRES.getUsername());
        pgSimpleDataSource.setPassword(POSTGRES.getPassword());
        dataSource = pgSimpleDataSource;

        applyMigrations();
        seedDataset();

        nodeRepository = new KGNodeRepository(dataSource, new ObjectMapper(), Runnable::run);
        edgeRepository = new KGEdgeRepository(dataSource, new ObjectMapper(), Runnable::run);
    }

    @Test
    @DisplayName("findNodesByType stays within latency target and uses tenant-leading type index")
    public void findNodesByTypeStaysWithinLatencyTargetAndUsesTenantLeadingTypeIndex() throws Exception {
        List<YAPPCGraphNode> nodes = runPromise(() -> nodeRepository.findNodesByType("SERVICE", TENANT_ID, 250));
        LatencySnapshot latency = measure(() -> runPromise(() -> nodeRepository.findNodesByType("SERVICE", TENANT_ID, 250)));
        String plan = explain(
                "EXPLAIN SELECT node_id FROM kg_nodes WHERE tenant_id = ? AND node_type = ? ORDER BY updated_at DESC LIMIT ?",
                statement -> {
                    statement.setString(1, TENANT_ID);
                    statement.setString(2, "SERVICE");
                    statement.setInt(3, 250);
                });

        assertThat(nodes).isNotEmpty();
        assertThat(latency.p95Ms()).isLessThan(P95_TARGET_MS);
        assertThat(plan).doesNotContain("Seq Scan on kg_nodes");
    }

    @Test
    @DisplayName("findNodesByProject stays within latency target and uses tenant-leading project index")
    public void findNodesByProjectStaysWithinLatencyTargetAndUsesTenantLeadingProjectIndex() throws Exception {
        List<YAPPCGraphNode> nodes = runPromise(() -> nodeRepository.findNodesByProject(PROJECT_ID, TENANT_ID));
        LatencySnapshot latency = measure(() -> runPromise(() -> nodeRepository.findNodesByProject(PROJECT_ID, TENANT_ID)));
        String plan = explain(
                "EXPLAIN SELECT node_id FROM kg_nodes WHERE tenant_id = ? AND project_id = ? ORDER BY updated_at DESC",
                statement -> {
                    statement.setString(1, TENANT_ID);
                    statement.setString(2, PROJECT_ID);
                });

        assertThat(nodes).isNotEmpty();
        assertThat(latency.p95Ms()).isLessThan(P95_TARGET_MS);
        assertThat(plan).doesNotContain("Seq Scan on kg_nodes");
    }

    @Test
    @DisplayName("findEdgesFromSource stays within latency target and uses tenant-leading source index")
    public void findEdgesFromSourceStaysWithinLatencyTargetAndUsesTenantLeadingSourceIndex() throws Exception {
        List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesFromSource(
                SOURCE_NODE_ID,
                TENANT_ID,
                Set.of("DEPENDS_ON", "USES", "CALLS")));
        LatencySnapshot latency = measure(() -> runPromise(() -> edgeRepository.findEdgesFromSource(
                SOURCE_NODE_ID,
                TENANT_ID,
                Set.of("DEPENDS_ON", "USES", "CALLS"))));
        String plan = explain(
                "EXPLAIN SELECT edge_id FROM kg_edges WHERE tenant_id = ? AND from_node_id = ? ORDER BY updated_at DESC",
                statement -> {
                    statement.setString(1, TENANT_ID);
                    statement.setString(2, SOURCE_NODE_ID);
                });

        assertThat(edges).isNotEmpty();
        assertThat(latency.p95Ms()).isLessThan(P95_TARGET_MS);
        assertThat(plan).contains("idx_kg_edges_tenant_source_relationship");
        assertThat(plan).doesNotContain("Seq Scan on kg_edges");
    }

    @Test
    @DisplayName("findEdgesForWorkspace stays within latency target and uses tenant-leading workspace index")
    public void findEdgesForWorkspaceStaysWithinLatencyTargetAndUsesTenantLeadingWorkspaceIndex() throws Exception {
        List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesForWorkspace(
                WORKSPACE_ID,
                TENANT_ID,
                Set.of("DEPENDS_ON", "USES", "CALLS")));
        LatencySnapshot latency = measure(() -> runPromise(() -> edgeRepository.findEdgesForWorkspace(
                WORKSPACE_ID,
                TENANT_ID,
                Set.of("DEPENDS_ON", "USES", "CALLS"))));
        String plan = explain(
                "EXPLAIN SELECT edge_id FROM kg_edges WHERE tenant_id = ? AND workspace_id = ? ORDER BY updated_at DESC",
                statement -> {
                    statement.setString(1, TENANT_ID);
                    statement.setString(2, WORKSPACE_ID);
                });

        assertThat(edges).isNotEmpty();
        assertThat(latency.p95Ms()).isLessThan(P95_TARGET_MS);
        assertThat(plan).doesNotContain("Seq Scan on kg_edges");
    }

    private static void applyMigrations() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String migration : List.of(
                    "db/migration/V001__kg_tables.sql",
                    "db/migration/V002__kg_scale_indexes.sql",
                    "db/migration/V003__kg_tenant_isolation.sql")) {
                for (String sqlStatement : splitStatements(readResource(migration))) {
                    statement.execute(sqlStatement);
                }
            }
            // Analyze tables after index creation to update statistics
            statement.execute("ANALYZE kg_nodes");
            statement.execute("ANALYZE kg_edges");
        }
    }

    private static void seedDataset() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement nodeInsert = connection.prepareStatement("""
                    INSERT INTO kg_nodes (
                        node_id, node_type, label, description, embedding, properties_json, tags_json,
                        tenant_id, project_id, workspace_id, created_by, created_at, updated_at, version, labels_json
                    ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    """);
                 PreparedStatement edgeInsert = connection.prepareStatement("""
                    INSERT INTO kg_edges (
                        edge_id, from_node_id, to_node_id, relationship_type, properties_json,
                        tenant_id, project_id, workspace_id, created_by, created_at, updated_at, version, labels_json
                    ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    """)) {
                seedNodes(nodeInsert);
                seedEdges(edgeInsert);
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("ANALYZE kg_nodes");
                statement.execute("ANALYZE kg_edges");
            }
            connection.commit();
        }
    }

    private static void seedNodes(PreparedStatement statement) throws Exception {
        List<String> tenants = List.of("tenant-01", "tenant-02", "tenant-03", "tenant-04", "tenant-05", "tenant-06");
        Instant baseTime = Instant.parse("2026-04-06T00:00:00Z");
        for (String tenantId : tenants) {
            for (int nodeNumber = 0; nodeNumber < 4000; nodeNumber++) {
                String nodeId = String.format(Locale.ROOT, "node-%06d", nodeNumber);
                String nodeType = switch (nodeNumber % 5) {
                    case 0 -> "SERVICE";
                    case 1 -> "API";
                    case 2 -> "CLASS";
                    case 3 -> "DOCUMENT";
                    default -> "TEST";
                };
                String projectId = String.format(Locale.ROOT, "project-%02d", nodeNumber % 8);
                String workspaceId = String.format(Locale.ROOT, "workspace-%02d", nodeNumber % 10);
                Instant createdAt = baseTime.plusSeconds(nodeNumber);
                Instant updatedAt = createdAt.plusSeconds(60);

                statement.setString(1, nodeId);
                statement.setString(2, nodeType);
                statement.setString(3, tenantId + "-" + nodeId);
                statement.setString(4, "Synthetic node for scale validation");
                statement.setNull(5, java.sql.Types.OTHER);
                statement.setString(6, "{\"language\":\"java\"}");
                statement.setString(7, "[\"scale\",\"validation\"]");
                statement.setString(8, tenantId);
                statement.setString(9, projectId);
                statement.setString(10, workspaceId);
                statement.setString(11, "kg-scale-test");
                statement.setTimestamp(12, java.sql.Timestamp.from(createdAt));
                statement.setTimestamp(13, java.sql.Timestamp.from(updatedAt));
                statement.setString(14, "1.0");
                statement.setString(15, "{\"suite\":\"kg-scale\"}");
                statement.addBatch();

                if (nodeNumber % 500 == 499) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private static void seedEdges(PreparedStatement statement) throws Exception {
        List<String> tenants = List.of("tenant-01", "tenant-02", "tenant-03", "tenant-04", "tenant-05", "tenant-06");
        Instant baseTime = Instant.parse("2026-04-06T00:00:00Z");
        for (String tenantId : tenants) {
            int edgeNumber = 0;
            for (int nodeNumber = 0; nodeNumber < 3990; nodeNumber++) {
                for (int offset = 1; offset <= 2; offset++) {
                    String sourceNodeId = String.format(Locale.ROOT, "node-%06d", nodeNumber);
                    String targetNodeId = String.format(Locale.ROOT, "node-%06d", nodeNumber + offset);
                    String relationshipType = switch ((nodeNumber + offset) % 3) {
                        case 0 -> "DEPENDS_ON";
                        case 1 -> "USES";
                        default -> "CALLS";
                    };
                    String projectId = String.format(Locale.ROOT, "project-%02d", nodeNumber % 8);
                    String workspaceId = String.format(Locale.ROOT, "workspace-%02d", nodeNumber % 10);
                    Instant createdAt = baseTime.plusSeconds(edgeNumber);
                    Instant updatedAt = createdAt.plusSeconds(30);

                    statement.setString(1, String.format(Locale.ROOT, "edge-%06d", edgeNumber));
                    statement.setString(2, sourceNodeId);
                    statement.setString(3, targetNodeId);
                    statement.setString(4, relationshipType);
                    statement.setString(5, "{\"weight\":1}");
                    statement.setString(6, tenantId);
                    statement.setString(7, projectId);
                    statement.setString(8, workspaceId);
                    statement.setString(9, "kg-scale-test");
                    statement.setTimestamp(10, java.sql.Timestamp.from(createdAt));
                    statement.setTimestamp(11, java.sql.Timestamp.from(updatedAt));
                    statement.setString(12, "1.0");
                    statement.setString(13, "{\"suite\":\"kg-scale\"}");
                    statement.addBatch();

                    edgeNumber++;
                    if (edgeNumber % 1000 == 0) {
                        statement.executeBatch();
                    }
                }
            }
            statement.executeBatch();
        }
    }

    private LatencySnapshot measure(QueryInvocation invocation) throws Exception {
        for (int index = 0; index < 5; index++) {
            invocation.run();
        }

        List<Double> durationsMs = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            long startedAt = System.nanoTime();
            invocation.run();
            long completedAt = System.nanoTime();
            durationsMs.add((completedAt - startedAt) / 1_000_000.0d);
        }

        List<Double> sorted = durationsMs.stream().sorted(Comparator.naturalOrder()).toList();
        return new LatencySnapshot(percentile(sorted, 50), percentile(sorted, 95), percentile(sorted, 99));
    }

    private String explain(String sql, SqlBinder binder) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder plan = new StringBuilder();
                while (resultSet.next()) {
                    plan.append(resultSet.getString(1)).append('\n');
                }
                return plan.toString();
            }
        }
    }

    private static List<String> splitStatements(String sql) {
        return Arrays.stream(sql.split(";\\s*(?:\\r?\\n|$)"))
                .map(String::trim)
                .filter(statement -> !statement.isBlank())
                .toList();
    }

    private static String readResource(String resourcePath) throws IOException {
        try (InputStream inputStream = KGScaleValidationTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static double percentile(List<Double> sortedDurationsMs, int percentile) {
        int index = (int) Math.ceil((percentile / 100.0d) * sortedDurationsMs.size()) - 1;
        return sortedDurationsMs.get(Math.max(0, index));
    }

    @FunctionalInterface
    private interface QueryInvocation {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws Exception;
    }

    private record LatencySnapshot(double p50Ms, double p95Ms, double p99Ms) {
    }
}

package com.ghatana.yappc.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose JDBC persistence for artifact graph nodes and edges extracted by the compiler scanner
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public final class ArtifactGraphRepository {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGraphRepository.class);
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public ArtifactGraphRepository(DataSource dataSource) {
        this(dataSource, new ObjectMapper(), Runnable::run);
    }

    public ArtifactGraphRepository(DataSource dataSource, ObjectMapper objectMapper, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public Promise<Void> saveNodes(String productId, String tenantId, List<ArtifactNodeDto> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Promise.of(null);
        }
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO artifact_nodes (
                    node_id, node_type, node_name, file_path, content_snippet,
                    properties_json, tags_json, tenant_id, project_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, node_id) DO UPDATE SET
                    node_type = EXCLUDED.node_type,
                    node_name = EXCLUDED.node_name,
                    file_path = EXCLUDED.file_path,
                    content_snippet = EXCLUDED.content_snippet,
                    properties_json = EXCLUDED.properties_json,
                    tags_json = EXCLUDED.tags_json,
                    updated_at = EXCLUDED.updated_at
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (ArtifactNodeDto node : nodes) {
                    statement.setString(1, node.id());
                    statement.setString(2, node.type());
                    statement.setString(3, node.name());
                    statement.setString(4, node.filePath());
                    statement.setString(5, node.content());
                    statement.setString(6, writeJson(node.properties()));
                    statement.setString(7, writeJson(node.tags()));
                    statement.setString(8, node.tenantId());
                    statement.setString(9, node.projectId());
                    Instant now = Instant.now();
                    statement.setTimestamp(10, Timestamp.from(now));
                    statement.setTimestamp(11, Timestamp.from(now));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        }).whenComplete((result, e) -> {
            if (e == null) {
                log.info("Saved {} artifact nodes for product {}", nodes.size(), productId);
            }
        }).map(v -> null);
    }

    public Promise<Void> saveEdges(String productId, String tenantId, List<ArtifactEdgeDto> edges) {
        if (edges == null || edges.isEmpty()) {
            return Promise.of(null);
        }
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO artifact_edges (
                    source_node_id, target_node_id, relationship_type,
                    properties_json, tenant_id, project_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, source_node_id, target_node_id, relationship_type) DO UPDATE SET
                    properties_json = EXCLUDED.properties_json,
                    updated_at = EXCLUDED.updated_at
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (ArtifactEdgeDto edge : edges) {
                    statement.setString(1, edge.sourceNodeId());
                    statement.setString(2, edge.targetNodeId());
                    statement.setString(3, edge.relationshipType());
                    statement.setString(4, writeJson(edge.properties()));
                    statement.setString(5, tenantId);
                    statement.setString(6, productId);
                    Instant now = Instant.now();
                    statement.setTimestamp(7, Timestamp.from(now));
                    statement.setTimestamp(8, Timestamp.from(now));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        }).whenComplete((result, e) -> {
            if (e == null) {
                log.info("Saved {} artifact edges for product {}", edges.size(), productId);
            }
        }).map(v -> null);
    }

    public Promise<List<ArtifactNodeDto>> findNodesByProduct(String productId, String tenantId, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT node_id, node_type, node_name, file_path, content_snippet,
                       properties_json, tags_json, tenant_id, project_id
                FROM artifact_nodes
                WHERE tenant_id = ? AND project_id = ?
                ORDER BY updated_at DESC
                LIMIT ?
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, productId);
                statement.setInt(3, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<ArtifactNodeDto> nodes = new ArrayList<>();
                    while (resultSet.next()) {
                        nodes.add(mapNode(resultSet));
                    }
                    return nodes;
                }
            }
        });
    }

    public Promise<List<ArtifactNodeDto>> findNodesByIds(List<String> nodeIds, String tenantId) {
        if (nodeIds.isEmpty()) {
            return Promise.of(List.of());
        }
        return Promise.ofBlocking(executor, () -> {
            String placeholders = nodeIds.stream().map(id -> "?").collect(Collectors.joining(", "));
            String sql = """
                SELECT node_id, node_type, node_name, file_path, content_snippet,
                       properties_json, tags_json, tenant_id, project_id
                FROM artifact_nodes
                WHERE tenant_id = ? AND node_id IN (%s)
                """.formatted(placeholders);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                for (int index = 0; index < nodeIds.size(); index++) {
                    statement.setString(index + 2, nodeIds.get(index));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<ArtifactNodeDto> nodes = new ArrayList<>();
                    while (resultSet.next()) {
                        nodes.add(mapNode(resultSet));
                    }
                    return nodes;
                }
            }
        });
    }

    public Promise<List<ArtifactEdgeDto>> findEdgesByProduct(String productId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT source_node_id, target_node_id, relationship_type, properties_json
                FROM artifact_edges
                WHERE tenant_id = ? AND project_id = ?
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, productId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<ArtifactEdgeDto> edges = new ArrayList<>();
                    while (resultSet.next()) {
                        edges.add(mapEdge(resultSet));
                    }
                    return edges;
                }
            }
        });
    }

    public Promise<Boolean> deleteGraphForProduct(String productId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection()) {
                String deleteEdges = "DELETE FROM artifact_edges WHERE tenant_id = ? AND project_id = ?";
                try (PreparedStatement edgeStmt = connection.prepareStatement(deleteEdges)) {
                    edgeStmt.setString(1, tenantId);
                    edgeStmt.setString(2, productId);
                    edgeStmt.executeUpdate();
                }
                String deleteNodes = "DELETE FROM artifact_nodes WHERE tenant_id = ? AND project_id = ?";
                try (PreparedStatement nodeStmt = connection.prepareStatement(deleteNodes)) {
                    nodeStmt.setString(1, tenantId);
                    nodeStmt.setString(2, productId);
                    return nodeStmt.executeUpdate() > 0;
                }
            }
        });
    }

    private ArtifactNodeDto mapNode(ResultSet resultSet) throws SQLException {
        return new ArtifactNodeDto(
            resultSet.getString("node_id"),
            resultSet.getString("node_type"),
            resultSet.getString("node_name"),
            resultSet.getString("file_path"),
            resultSet.getString("content_snippet"),
            readJson(resultSet.getString("properties_json"), OBJECT_MAP, Map.of()),
            readJson(resultSet.getString("tags_json"), STRING_LIST, List.of()),
            resultSet.getString("tenant_id"),
            resultSet.getString("project_id")
        );
    }

    private ArtifactEdgeDto mapEdge(ResultSet resultSet) throws SQLException {
        return new ArtifactEdgeDto(
            resultSet.getString("source_node_id"),
            resultSet.getString("target_node_id"),
            resultSet.getString("relationship_type"),
            readJson(resultSet.getString("properties_json"), OBJECT_MAP, Map.of())
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize artifact graph data", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> typeReference, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, typeReference);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize artifact graph data", exception);
        }
    }
}

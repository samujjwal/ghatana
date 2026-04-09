package com.ghatana.yappc.knowledge.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * @doc.type class
 * @doc.purpose Persists and retrieves knowledge graph edges from JDBC storage
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class KGEdgeRepository {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public KGEdgeRepository(DataSource dataSource) {
        this(dataSource, new ObjectMapper(), Runnable::run);
    }

    public KGEdgeRepository(DataSource dataSource, ObjectMapper objectMapper, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public Promise<YAPPCGraphEdge> saveEdge(YAPPCGraphEdge edge) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    INSERT INTO kg_edges (
                        edge_id, from_node_id, to_node_id, relationship_type, properties_json,
                        tenant_id, project_id, workspace_id, created_by, created_at,
                        updated_at, version, labels_json
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, edge_id) DO UPDATE SET
                        from_node_id = EXCLUDED.from_node_id,
                        to_node_id = EXCLUDED.to_node_id,
                        relationship_type = EXCLUDED.relationship_type,
                        properties_json = EXCLUDED.properties_json,
                        project_id = EXCLUDED.project_id,
                        workspace_id = EXCLUDED.workspace_id,
                        created_by = EXCLUDED.created_by,
                        created_at = EXCLUDED.created_at,
                        updated_at = EXCLUDED.updated_at,
                        version = EXCLUDED.version,
                        labels_json = EXCLUDED.labels_json
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                bindEdge(statement, edge);
                statement.executeUpdate();
                return edge;
            }
        });
    }

    public Promise<List<YAPPCGraphEdge>> findEdgesFromSource(String sourceNodeId, String tenantId, Set<String> relationshipTypes) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    SELECT edge_id, from_node_id, to_node_id, relationship_type, properties_json,
                           tenant_id, project_id, workspace_id, created_by, created_at,
                           updated_at, version, labels_json
                    FROM kg_edges
                    WHERE tenant_id = ? AND from_node_id = ?
                    ORDER BY updated_at DESC
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, sourceNodeId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    java.util.ArrayList<YAPPCGraphEdge> edges = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        YAPPCGraphEdge edge = mapEdge(resultSet);
                        if (relationshipTypes.isEmpty() || relationshipTypes.contains(edge.relationshipType().name())) {
                            edges.add(edge);
                        }
                    }
                    return edges;
                }
            }
        });
    }

    public Promise<List<YAPPCGraphEdge>> findEdgesForWorkspace(String workspaceId, String tenantId, Set<String> relationshipTypes) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    SELECT edge_id, from_node_id, to_node_id, relationship_type, properties_json,
                           tenant_id, project_id, workspace_id, created_by, created_at,
                           updated_at, version, labels_json
                    FROM kg_edges
                    WHERE tenant_id = ? AND workspace_id = ?
                    ORDER BY updated_at DESC
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    java.util.ArrayList<YAPPCGraphEdge> edges = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        YAPPCGraphEdge edge = mapEdge(resultSet);
                        if (relationshipTypes.isEmpty() || relationshipTypes.contains(edge.relationshipType().name())) {
                            edges.add(edge);
                        }
                    }
                    return edges;
                }
            }
        });
    }

    public Promise<List<YAPPCGraphEdge>> findEdgesToTarget(String targetNodeId, String tenantId, Set<String> relationshipTypes) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    SELECT edge_id, from_node_id, to_node_id, relationship_type, properties_json,
                           tenant_id, project_id, workspace_id, created_by, created_at,
                           updated_at, version, labels_json
                    FROM kg_edges
                    WHERE tenant_id = ? AND to_node_id = ?
                    ORDER BY updated_at DESC
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, targetNodeId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    java.util.ArrayList<YAPPCGraphEdge> edges = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        YAPPCGraphEdge edge = mapEdge(resultSet);
                        if (relationshipTypes.isEmpty() || relationshipTypes.contains(edge.relationshipType().name())) {
                            edges.add(edge);
                        }
                    }
                    return edges;
                }
            }
        });
    }

    public Promise<List<YAPPCGraphEdge>> findEdgesByProject(String projectId, String tenantId, Set<String> relationshipTypes) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    SELECT edge_id, from_node_id, to_node_id, relationship_type, properties_json,
                           tenant_id, project_id, workspace_id, created_by, created_at,
                           updated_at, version, labels_json
                    FROM kg_edges
                    WHERE tenant_id = ? AND project_id = ?
                    ORDER BY updated_at DESC
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, projectId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    java.util.ArrayList<YAPPCGraphEdge> edges = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        YAPPCGraphEdge edge = mapEdge(resultSet);
                        if (relationshipTypes.isEmpty() || relationshipTypes.contains(edge.relationshipType().name())) {
                            edges.add(edge);
                        }
                    }
                    return edges;
                }
            }
        });
    }

    public Promise<Integer> countEdgesByTenant(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT COUNT(*) AS edge_count FROM kg_edges WHERE tenant_id = ?";
            Connection connection = dataSource.getConnection();
            try {
                PreparedStatement statement = connection.prepareStatement(sql);
                try {
                statement.setString(1, tenantId);
                    ResultSet resultSet = statement.executeQuery();
                    try {
                    if (!resultSet.next()) {
                        return 0;
                    }
                    return resultSet.getInt("edge_count");
                    } finally {
                        resultSet.close();
                    }
                } finally {
                    statement.close();
                }
            } finally {
                connection.close();
            }
        });
    }

    public Promise<Boolean> deleteEdge(String edgeId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM kg_edges WHERE tenant_id = ? AND edge_id = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, edgeId);
                return statement.executeUpdate() > 0;
            }
        });
    }

    private void bindEdge(PreparedStatement statement, YAPPCGraphEdge edge) throws SQLException {
        YAPPCGraphMetadata metadata = edge.metadata();
        statement.setString(1, edge.id());
        statement.setString(2, edge.sourceNodeId());
        statement.setString(3, edge.targetNodeId());
        statement.setString(4, edge.relationshipType().name());
        statement.setString(5, writeJson(edge.properties()));
        statement.setString(6, metadata.tenantId());
        statement.setString(7, metadata.projectId());
        statement.setString(8, metadata.workspaceId());
        statement.setString(9, metadata.createdBy());
        statement.setTimestamp(10, Timestamp.from(metadata.createdAt()));
        statement.setTimestamp(11, Timestamp.from(metadata.updatedAt()));
        statement.setString(12, metadata.version());
        statement.setString(13, writeJson(metadata.labels()));
    }

    private YAPPCGraphEdge mapEdge(ResultSet resultSet) throws SQLException {
        Instant createdAt = resultSet.getTimestamp("created_at").toInstant();
        Instant updatedAt = resultSet.getTimestamp("updated_at").toInstant();
        Map<String, Object> properties = readJson(resultSet.getString("properties_json"), OBJECT_MAP, Map.of());
        Map<String, String> labels = readJson(resultSet.getString("labels_json"), STRING_MAP, Map.of());

        return YAPPCGraphEdge.builder()
                .id(resultSet.getString("edge_id"))
                .sourceNodeId(resultSet.getString("from_node_id"))
                .targetNodeId(resultSet.getString("to_node_id"))
                .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.valueOf(resultSet.getString("relationship_type")))
                .properties(properties)
                .metadata(new YAPPCGraphMetadata(
                        resultSet.getString("tenant_id"),
                        resultSet.getString("project_id"),
                        resultSet.getString("workspace_id"),
                        resultSet.getString("created_by"),
                        createdAt,
                        updatedAt,
                        resultSet.getString("version"),
                        labels))
                .build();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize knowledge graph edge data", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> typeReference, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, typeReference);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize knowledge graph edge data", exception);
        }
    }
}

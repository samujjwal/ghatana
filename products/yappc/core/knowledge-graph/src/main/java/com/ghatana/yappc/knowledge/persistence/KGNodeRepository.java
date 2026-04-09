package com.ghatana.yappc.knowledge.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose Persists and retrieves knowledge graph nodes from JDBC storage
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class KGNodeRepository {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public KGNodeRepository(DataSource dataSource) {
        this(dataSource, new ObjectMapper(), Runnable::run);
    }

    public KGNodeRepository(DataSource dataSource, ObjectMapper objectMapper, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public Promise<YAPPCGraphNode> saveNode(YAPPCGraphNode node) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    INSERT INTO kg_nodes (
                        node_id, node_type, label, description, embedding, properties_json, tags_json,
                        tenant_id, project_id, workspace_id, created_by, created_at, updated_at,
                        version, labels_json
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, node_id) DO UPDATE SET
                        node_type = EXCLUDED.node_type,
                        label = EXCLUDED.label,
                        description = EXCLUDED.description,
                        embedding = EXCLUDED.embedding,
                        properties_json = EXCLUDED.properties_json,
                        tags_json = EXCLUDED.tags_json,
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
                bindNode(statement, node);
                statement.executeUpdate();
                return node;
            }
        });
    }

    public Promise<List<YAPPCGraphNode>> findNodesByType(String type, String tenantId, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    SELECT node_id, node_type, label, description, properties_json, tags_json,
                           tenant_id, project_id, workspace_id, created_by, created_at, updated_at,
                           version, labels_json
                    FROM kg_nodes
                    WHERE tenant_id = ? AND node_type = ?
                    ORDER BY updated_at DESC
                    LIMIT ?
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, type);
                statement.setInt(3, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    java.util.ArrayList<YAPPCGraphNode> nodes = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        nodes.add(mapNode(resultSet));
                    }
                    return nodes;
                }
            }
        });
    }

    public Promise<List<YAPPCGraphNode>> findNodesByIds(List<String> nodeIds, String tenantId) {
        if (nodeIds.isEmpty()) {
            return Promise.of(List.of());
        }

        return Promise.ofBlocking(executor, () -> {
            String placeholders = nodeIds.stream().map(id -> "?").collect(Collectors.joining(", "));
            String sql = """
                    SELECT node_id, node_type, label, description, properties_json, tags_json,
                           tenant_id, project_id, workspace_id, created_by, created_at, updated_at,
                           version, labels_json
                    FROM kg_nodes
                    WHERE tenant_id = ? AND node_id IN (%s)
                    """.formatted(placeholders);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                for (int index = 0; index < nodeIds.size(); index++) {
                    statement.setString(index + 2, nodeIds.get(index));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    java.util.ArrayList<YAPPCGraphNode> nodes = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        nodes.add(mapNode(resultSet));
                    }
                    return nodes;
                }
            }
        });
    }

    public Promise<Optional<YAPPCGraphNode>> findNodeById(String nodeId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    SELECT node_id, node_type, label, description, properties_json, tags_json,
                           tenant_id, project_id, workspace_id, created_by, created_at, updated_at,
                           version, labels_json
                    FROM kg_nodes
                    WHERE tenant_id = ? AND node_id = ?
                    """;
            Connection connection = dataSource.getConnection();
            try {
                PreparedStatement statement = connection.prepareStatement(sql);
                try {
                statement.setString(1, tenantId);
                statement.setString(2, nodeId);
                    ResultSet resultSet = statement.executeQuery();
                    try {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapNode(resultSet));
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

    public Promise<List<YAPPCGraphNode>> findNodesByProject(String projectId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    SELECT node_id, node_type, label, description, properties_json, tags_json,
                           tenant_id, project_id, workspace_id, created_by, created_at, updated_at,
                           version, labels_json
                    FROM kg_nodes
                    WHERE tenant_id = ? AND project_id = ?
                    ORDER BY updated_at DESC
                    """;
            Connection connection = dataSource.getConnection();
            try {
                PreparedStatement statement = connection.prepareStatement(sql);
                try {
                statement.setString(1, tenantId);
                statement.setString(2, projectId);
                    ResultSet resultSet = statement.executeQuery();
                    try {
                        if (!resultSet.next()) {
                            return List.of();
                        }
                        java.util.ArrayList<YAPPCGraphNode> nodes = new java.util.ArrayList<>();
                        do {
                        nodes.add(mapNode(resultSet));
                        } while (resultSet.next());
                        return nodes;
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

    public Promise<Integer> countNodesByTenant(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT COUNT(*) AS node_count FROM kg_nodes WHERE tenant_id = ?";
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
                    return resultSet.getInt("node_count");
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

    public Promise<Boolean> deleteNode(String nodeId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM kg_nodes WHERE tenant_id = ? AND node_id = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, nodeId);
                return statement.executeUpdate() > 0;
            }
        });
    }

    private void bindNode(PreparedStatement statement, YAPPCGraphNode node) throws SQLException {
        YAPPCGraphMetadata metadata = node.metadata();
        statement.setString(1, node.id());
        statement.setString(2, node.type().name());
        statement.setString(3, node.name());
        statement.setString(4, node.description());
        statement.setString(5, null);
        statement.setString(6, writeJson(node.properties()));
        statement.setString(7, writeJson(node.tags().stream().sorted().toList()));
        statement.setString(8, metadata.tenantId());
        statement.setString(9, metadata.projectId());
        statement.setString(10, metadata.workspaceId());
        statement.setString(11, metadata.createdBy());
        statement.setTimestamp(12, Timestamp.from(metadata.createdAt()));
        statement.setTimestamp(13, Timestamp.from(metadata.updatedAt()));
        statement.setString(14, metadata.version());
        statement.setString(15, writeJson(metadata.labels()));
    }

    private YAPPCGraphNode mapNode(ResultSet resultSet) throws SQLException {
        Instant createdAt = resultSet.getTimestamp("created_at").toInstant();
        Instant updatedAt = resultSet.getTimestamp("updated_at").toInstant();
        Map<String, Object> properties = readJson(resultSet.getString("properties_json"), OBJECT_MAP, Map.of());
        List<String> tags = readJson(resultSet.getString("tags_json"), STRING_LIST, List.of());
        Map<String, String> labels = readJson(resultSet.getString("labels_json"), STRING_MAP, Map.of());

        return YAPPCGraphNode.builder()
                .id(resultSet.getString("node_id"))
                .type(YAPPCGraphNode.YAPPCNodeType.valueOf(resultSet.getString("node_type")))
                .name(resultSet.getString("label"))
                .description(resultSet.getString("description"))
                .properties(properties)
                .tags(new LinkedHashSet<>(tags))
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
            throw new IllegalArgumentException("Unable to serialize knowledge graph node data", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> typeReference, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, typeReference);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize knowledge graph node data", exception);
        }
    }
}

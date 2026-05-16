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
 * 
 * P4-4: Added snapshot/version ID tracking for incremental upsert and tombstone support for removed files/symbols.
 * Nodes and edges now track which repository snapshot they came from, enabling cross-scan diffing.
 * Removed nodes are tombstoned rather than hard-deleted to preserve history.
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
        return saveNodes(productId, tenantId, nodes, null, null);
    }

    /**
     * P4-4: Save nodes with snapshot/version ID tracking for incremental upsert.
     * Snapshot ID identifies which repository scan produced these nodes.
     * Version ID enables tracking of graph evolution over time.
     */
    public Promise<Void> saveNodes(String productId, String tenantId, List<ArtifactNodeDto> nodes, String snapshotId, String versionId) {
        if (nodes == null || nodes.isEmpty()) {
            return Promise.of(null);
        }
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO artifact_nodes (
                    node_id, node_type, node_name, file_path, content_snippet,
                    properties_json, tags_json, tenant_id, project_id, content_checksum,
                    created_at, updated_at, snapshot_id, version_id, is_tombstone
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, node_id) DO UPDATE SET
                    node_type = EXCLUDED.node_type,
                    node_name = EXCLUDED.node_name,
                    file_path = EXCLUDED.file_path,
                    content_snippet = EXCLUDED.content_snippet,
                    properties_json = EXCLUDED.properties_json,
                    tags_json = EXCLUDED.tags_json,
                    content_checksum = EXCLUDED.content_checksum,
                    updated_at = EXCLUDED.updated_at,
                    snapshot_id = EXCLUDED.snapshot_id,
                    version_id = EXCLUDED.version_id,
                    is_tombstone = EXCLUDED.is_tombstone
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
                    statement.setString(10, computeChecksum(node));
                    Instant now = Instant.now();
                    statement.setTimestamp(11, Timestamp.from(now));
                    statement.setTimestamp(12, Timestamp.from(now));
                    statement.setString(13, snapshotId);
                    statement.setString(14, versionId);
                    statement.setBoolean(15, false); // is_tombstone
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        }).whenComplete((result, e) -> {
            if (e == null) {
                log.info("Saved {} artifact nodes for product {} (snapshotId={}, versionId={})", 
                    nodes.size(), productId, snapshotId, versionId);
            }
        }).map(v -> null);
    }

    public Promise<Void> saveEdges(String productId, String tenantId, List<ArtifactEdgeDto> edges) {
        return saveEdges(productId, tenantId, edges, null, null);
    }

    /**
     * P4-4: Save edges with snapshot/version ID tracking for incremental upsert.
     */
    public Promise<Void> saveEdges(String productId, String tenantId, List<ArtifactEdgeDto> edges, String snapshotId, String versionId) {
        if (edges == null || edges.isEmpty()) {
            return Promise.of(null);
        }
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO artifact_edges (
                    source_node_id, target_node_id, relationship_type,
                    properties_json, tenant_id, project_id, created_at, updated_at, snapshot_id, version_id, is_tombstone
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, source_node_id, target_node_id, relationship_type) DO UPDATE SET
                    properties_json = EXCLUDED.properties_json,
                    updated_at = EXCLUDED.updated_at,
                    snapshot_id = EXCLUDED.snapshot_id,
                    version_id = EXCLUDED.version_id,
                    is_tombstone = EXCLUDED.is_tombstone
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
                    statement.setString(9, snapshotId);
                    statement.setString(10, versionId);
                    statement.setBoolean(11, false); // is_tombstone
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        }).whenComplete((result, e) -> {
            if (e == null) {
                log.info("Saved {} artifact edges for product {} (snapshotId={}, versionId={})", 
                    edges.size(), productId, snapshotId, versionId);
            }
        }).map(v -> null);
    }

    public Promise<List<ArtifactNodeDto>> findNodesByProduct(String productId, String tenantId, int limit) {
        return findNodesByProduct(productId, tenantId, limit, false);
    }

    /**
     * P4-4: Find nodes by product with option to include/exclude tombstoned nodes.
     * Active graph queries should exclude tombstones (includeTombstones=false).
     * Historical/diff queries should include tombstones (includeTombstones=true).
     */
    public Promise<List<ArtifactNodeDto>> findNodesByProduct(String productId, String tenantId, int limit, boolean includeTombstones) {
        return Promise.ofBlocking(executor, () -> {
            String tombstoneFilter = includeTombstones ? "" : " AND is_tombstone = false";
            String sql = """
                SELECT node_id, node_type, node_name, file_path, content_snippet,
                       properties_json, tags_json, tenant_id, project_id
                FROM artifact_nodes
                WHERE tenant_id = ? AND project_id = ?
                """ + tombstoneFilter + """
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
        return findEdgesByProduct(productId, tenantId, false);
    }

    /**
     * P4-4: Find edges by product with option to include/exclude tombstoned edges.
     */
    public Promise<List<ArtifactEdgeDto>> findEdgesByProduct(String productId, String tenantId, boolean includeTombstones) {
        return Promise.ofBlocking(executor, () -> {
            String tombstoneFilter = includeTombstones ? "" : " AND is_tombstone = false";
            String sql = """
                SELECT source_node_id, target_node_id, relationship_type, properties_json
                FROM artifact_edges
                WHERE tenant_id = ? AND project_id = ?
                """ + tombstoneFilter + """
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
        return tombstoneGraphForProduct(productId, tenantId);
    }

    /**
     * P4-4: Tombstone all nodes and edges for a product instead of hard delete.
     * This preserves history and enables cross-snapshot diffing.
     */
    public Promise<Boolean> tombstoneGraphForProduct(String productId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection()) {
                // Tombstone edges first (foreign key dependency)
                String tombstoneEdges = """
                    UPDATE artifact_edges 
                    SET is_tombstone = true, updated_at = ?
                    WHERE tenant_id = ? AND project_id = ? AND is_tombstone = false
                    """;
                try (PreparedStatement edgeStmt = connection.prepareStatement(tombstoneEdges)) {
                    edgeStmt.setTimestamp(1, Timestamp.from(Instant.now()));
                    edgeStmt.setString(2, tenantId);
                    edgeStmt.setString(3, productId);
                    int edgeCount = edgeStmt.executeUpdate();
                    log.info("Tombstoned {} edges for product {}", edgeCount, productId);
                }
                // Tombstone nodes
                String tombstoneNodes = """
                    UPDATE artifact_nodes 
                    SET is_tombstone = true, updated_at = ?
                    WHERE tenant_id = ? AND project_id = ? AND is_tombstone = false
                    """;
                try (PreparedStatement nodeStmt = connection.prepareStatement(tombstoneNodes)) {
                    nodeStmt.setTimestamp(1, Timestamp.from(Instant.now()));
                    nodeStmt.setString(2, tenantId);
                    nodeStmt.setString(3, productId);
                    int nodeCount = nodeStmt.executeUpdate();
                    log.info("Tombstoned {} nodes for product {}", nodeCount, productId);
                    return nodeCount > 0;
                }
            }
        });
    }

    /**
     * P4-4: Tombstone specific nodes by ID list.
     * Used when files/symbols are removed from the source repository.
     */
    public Promise<Integer> tombstoneNodes(List<String> nodeIds, String tenantId, String snapshotId) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return Promise.of(0);
        }
        return Promise.ofBlocking(executor, () -> {
            String placeholders = nodeIds.stream().map(id -> "?").collect(Collectors.joining(", "));
            String sql = """
                UPDATE artifact_nodes 
                SET is_tombstone = true, updated_at = ?, snapshot_id = ?
                WHERE tenant_id = ? AND node_id IN (""" + placeholders + """) AND is_tombstone = false
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(Instant.now()));
                statement.setString(2, snapshotId);
                statement.setString(3, tenantId);
                for (int index = 0; index < nodeIds.size(); index++) {
                    statement.setString(index + 4, nodeIds.get(index));
                }
                int count = statement.executeUpdate();
                log.info("Tombstoned {} nodes (snapshotId={})", count, snapshotId);
                return count;
            }
        });
    }

    /**
     * P4-4: Tombstone specific edges by source/target node ID lists.
     */
    public Promise<Integer> tombstoneEdges(List<String> sourceNodeIds, List<String> targetNodeIds, String tenantId, String snapshotId) {
        if ((sourceNodeIds == null || sourceNodeIds.isEmpty()) && (targetNodeIds == null || targetNodeIds.isEmpty())) {
            return Promise.of(0);
        }
        return Promise.ofBlocking(executor, () -> {
            StringBuilder sql = new StringBuilder(
                "UPDATE artifact_edges SET is_tombstone = true, updated_at = ?, snapshot_id = ? " +
                "WHERE tenant_id = ? AND is_tombstone = false"
            );
            List<String> params = new ArrayList<>();
            
            if (sourceNodeIds != null && !sourceNodeIds.isEmpty()) {
                String placeholders = sourceNodeIds.stream().map(id -> "?").collect(Collectors.joining(", "));
                sql.append(" AND source_node_id IN (").append(placeholders).append(")");
                params.addAll(sourceNodeIds);
            }
            if (targetNodeIds != null && !targetNodeIds.isEmpty()) {
                String placeholders = targetNodeIds.stream().map(id -> "?").collect(Collectors.joining(", "));
                sql.append(" AND target_node_id IN (").append(placeholders).append(")");
                params.addAll(targetNodeIds);
            }
            
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                statement.setTimestamp(1, Timestamp.from(Instant.now()));
                statement.setString(2, snapshotId);
                statement.setString(3, tenantId);
                for (int index = 0; index < params.size(); index++) {
                    statement.setString(index + 4, params.get(index));
                }
                int count = statement.executeUpdate();
                log.info("Tombstoned {} edges (snapshotId={})", count, snapshotId);
                return count;
            }
        });
    }

    /**
     * Compute checksum for a node to enable incremental upsert.
     * Uses SHA-256 hash of node content + properties + tags.
     */
    private String computeChecksum(ArtifactNodeDto node) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(node.id().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(node.content().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(writeJson(node.properties()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(writeJson(node.tags()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available, using fallback checksum", e);
            return String.valueOf(node.content().hashCode());
        }
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

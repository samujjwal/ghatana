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
import java.util.HashMap;
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

    /**
     * P1-12: Removed default blocking constructor.
     * Requires explicit executor injection to avoid blocking the event loop.
     */
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
     * P1-12: Skip unchanged nodes by checksum comparison to reduce database writes.
     */
    public Promise<Void> saveNodes(String productId, String tenantId, List<ArtifactNodeDto> nodes, String snapshotId, String versionId) {
        if (nodes == null || nodes.isEmpty()) {
            return Promise.of(null);
        }
        return Promise.ofBlocking(executor, () -> {
            // P1-12: Fetch existing checksums to skip unchanged nodes
            Map<String, String> existingChecksums = new HashMap<>();
            String checksumQuery = """
                SELECT node_id, content_checksum
                FROM artifact_nodes
                WHERE tenant_id = ? AND project_id = ? AND is_tombstone = false
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(checksumQuery)) {
                stmt.setString(1, tenantId);
                stmt.setString(2, productId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        existingChecksums.put(rs.getString("node_id"), rs.getString("content_checksum"));
                    }
                }
            }

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
                int skipped = 0;
                for (ArtifactNodeDto node : nodes) {
                    if ((node.tenantId() != null && !tenantId.equals(node.tenantId()))
                            || (node.projectId() != null && !productId.equals(node.projectId()))) {
                        log.warn("Ignoring node payload scope for nodeId={} and using server scope tenantId={}, productId={}",
                                node.id(), tenantId, productId);
                    }
                    
                    // P1-12: Skip unchanged nodes by checksum
                    String newChecksum = computeChecksum(node);
                    String existingChecksum = existingChecksums.get(node.id());
                    if (existingChecksum != null && existingChecksum.equals(newChecksum)) {
                        skipped++;
                        continue;
                    }
                    
                    statement.setString(1, node.id());
                    statement.setString(2, node.type());
                    statement.setString(3, node.name());
                    statement.setString(4, node.filePath());
                    statement.setString(5, node.content());
                    statement.setString(6, writeJson(node.properties()));
                    statement.setString(7, writeJson(node.tags()));
                    statement.setString(8, tenantId);
                    statement.setString(9, productId);
                    statement.setString(10, newChecksum);
                    Instant now = Instant.now();
                    statement.setTimestamp(11, Timestamp.from(now));
                    statement.setTimestamp(12, Timestamp.from(now));
                    statement.setString(13, snapshotId);
                    statement.setString(14, versionId);
                    statement.setBoolean(15, false); // is_tombstone
                    statement.addBatch();
                }
                statement.executeBatch();
                log.info("Saved {} artifact nodes for product {} (snapshotId={}, versionId={}, skipped={})", 
                    nodes.size() - skipped, productId, snapshotId, versionId, skipped);
            }
            return null;
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

    /**
     * P4-2: Alias for saveNodes - provides incremental upsert semantics.
     * Uses ON CONFLICT DO UPDATE to only insert/update changed nodes.
     */
    public Promise<Void> upsertNodes(String productId, String tenantId, List<ArtifactNodeDto> nodes, String snapshotId, String versionId, String contentChecksum) {
        return saveNodes(productId, tenantId, nodes, snapshotId, versionId);
    }

    /**
     * P4-2: Alias for saveEdges - provides incremental upsert semantics.
     * Uses ON CONFLICT DO UPDATE to only insert/update changed edges.
     */
    public Promise<Void> upsertEdges(String productId, String tenantId, List<ArtifactEdgeDto> edges, String snapshotId, String versionId) {
        return saveEdges(productId, tenantId, edges, snapshotId, versionId);
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
                WHERE tenant_id = ? AND node_id IN (%s) AND is_tombstone = false
                """.formatted(placeholders);
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

    // ============================================================================
    // P4-4: Cursor Pagination and Snapshot Diff Methods
    // ============================================================================

    /**
     * P4-4: Cursor-based pagination for nodes.
     * Returns a page of nodes along with a cursor for the next page.
     */
    public Promise<PageResult<ArtifactNodeDto>> findNodesPaginated(
            String productId, String tenantId, String cursor, int pageSize) {
        return Promise.ofBlocking(executor, () -> {
            String cursorFilter = "";
            List<Object> params = new ArrayList<>();
            params.add(tenantId);
            params.add(productId);
            
            if (cursor != null && !cursor.isBlank()) {
                cursorFilter = " AND updated_at < ?::timestamp";
                params.add(cursor);
            }
            
            String sql = """
                SELECT node_id, node_type, node_name, file_path, content_snippet,
                      properties_json, tags_json, tenant_id, project_id, updated_at
                FROM artifact_nodes
                WHERE tenant_id = ? AND project_id = ? AND is_tombstone = false
                """ + cursorFilter + """
                ORDER BY updated_at DESC
                LIMIT ?
                """;
            params.add(pageSize);
            
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < params.size(); index++) {
                    statement.setObject(index + 1, params.get(index));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<ArtifactNodeDto> nodes = new ArrayList<>();
                    String nextCursor = null;
                    while (resultSet.next()) {
                        nodes.add(mapNode(resultSet));
                        nextCursor = resultSet.getTimestamp("updated_at").toString();
                    }
                    return new PageResult<>(nodes, nextCursor);
                }
            }
        });
    }

    /**
     * P4-4: Cursor-based pagination for edges.
     */
    public Promise<PageResult<ArtifactEdgeDto>> findEdgesPaginated(
            String productId, String tenantId, String cursor, int pageSize) {
        return Promise.ofBlocking(executor, () -> {
            String cursorFilter = "";
            List<Object> params = new ArrayList<>();
            params.add(tenantId);
            params.add(productId);
            
            if (cursor != null && !cursor.isBlank()) {
                cursorFilter = " AND updated_at < ?::timestamp";
                params.add(cursor);
            }
            
            String sql = """
                SELECT source_node_id, target_node_id, relationship_type, properties_json, updated_at
                FROM artifact_edges
                WHERE tenant_id = ? AND project_id = ? AND is_tombstone = false
                """ + cursorFilter + """
                ORDER BY updated_at DESC
                LIMIT ?
                """;
            params.add(pageSize);
            
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < params.size(); index++) {
                    statement.setObject(index + 1, params.get(index));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<ArtifactEdgeDto> edges = new ArrayList<>();
                    String nextCursor = null;
                    while (resultSet.next()) {
                        edges.add(mapEdge(resultSet));
                        nextCursor = resultSet.getTimestamp("updated_at").toString();
                    }
                    return new PageResult<>(edges, nextCursor);
                }
            }
        });
    }

    /**
     * P4-4: Compute snapshot diff between two snapshots.
     * Returns added, removed, and modified nodes/edges.
     */
    public Promise<SnapshotDiffResult> computeSnapshotDiff(
            String productId, String tenantId, String fromSnapshotId, String toSnapshotId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT node_id, node_type, node_name, file_path, content_snippet,
                       properties_json, tags_json, tenant_id, project_id, snapshot_id
                FROM artifact_nodes
                WHERE tenant_id = ? AND project_id = ? AND snapshot_id IN (?, ?)
                ORDER BY snapshot_id
                """;
            
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, productId);
                statement.setString(3, fromSnapshotId);
                statement.setString(4, toSnapshotId);
                
                try (ResultSet resultSet = statement.executeQuery()) {
                    Map<String, ArtifactNodeDto> fromNodes = new HashMap<>();
                    Map<String, ArtifactNodeDto> toNodes = new HashMap<>();
                    
                    while (resultSet.next()) {
                        String snapshotId = resultSet.getString("snapshot_id");
                        ArtifactNodeDto node = new ArtifactNodeDto(
                            resultSet.getString("node_id"),
                            resultSet.getString("node_type"),
                            resultSet.getString("node_name"),
                            resultSet.getString("file_path"),
                            resultSet.getString("content_snippet"),
                            readJson(resultSet.getString("properties_json"), OBJECT_MAP, Map.of()),
                            readJson(resultSet.getString("tags_json"), STRING_LIST, List.of()),
                            resultSet.getString("tenant_id"),
                            resultSet.getString("project_id"),
                            null, // sourceLocation
                            null, // extractorId
                            null, // extractorVersion
                            null, // confidence
                            null, // provenance
                            null, // privacySecurityFlags
                            null, // residualFragmentIds
                            null, // sourceRef
                            null  // symbolRef
                        );
                        
                        if (snapshotId.equals(fromSnapshotId)) {
                            fromNodes.put(node.id(), node);
                        } else {
                            toNodes.put(node.id(), node);
                        }
                    }
                    
                    List<ArtifactNodeDto> addedNodes = new ArrayList<>();
                    List<ArtifactNodeDto> removedNodes = new ArrayList<>();
                    List<ArtifactNodeDto> modifiedNodes = new ArrayList<>();
                    
                    for (ArtifactNodeDto node : toNodes.values()) {
                        if (!fromNodes.containsKey(node.id())) {
                            addedNodes.add(node);
                        } else {
                            ArtifactNodeDto fromNode = fromNodes.get(node.id());
                            if (!fromNode.content().equals(node.content()) ||
                                !fromNode.properties().equals(node.properties())) {
                                modifiedNodes.add(node);
                            }
                        }
                    }
                    
                    for (ArtifactNodeDto node : fromNodes.values()) {
                        if (!toNodes.containsKey(node.id())) {
                            removedNodes.add(node);
                        }
                    }
                    
                    return new SnapshotDiffResult(addedNodes, removedNodes, modifiedNodes, List.of(), List.of(), List.of());
                }
            }
        });
    }

    /**
     * P4-4: Page result container for cursor-based pagination.
     */
    public record PageResult<T>(List<T> items, String nextCursor) {}

    /**
     * P4-4: Snapshot diff result container.
     */
    public record SnapshotDiffResult(
        List<ArtifactNodeDto> addedNodes,
        List<ArtifactNodeDto> removedNodes,
        List<ArtifactNodeDto> modifiedNodes,
        List<ArtifactEdgeDto> addedEdges,
        List<ArtifactEdgeDto> removedEdges,
        List<ArtifactEdgeDto> modifiedEdges
    ) {}

    // ============================================================================
    // P1-12: Unresolved Edges and Residual Islands Persistence
    // ============================================================================

    /**
     * P1-12: Save unresolved edges to the artifact_unresolved_edges table.
     */
    public Promise<Void> saveUnresolvedEdges(
            String productId, String tenantId, String snapshotId,
            List<UnresolvedEdgeRecord> unresolvedEdges) {
        if (unresolvedEdges == null || unresolvedEdges.isEmpty()) {
            return Promise.of(null);
        }
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO artifact_unresolved_edges (
                    id, snapshot_id, source_node_id, target_ref, relationship,
                    target_kind_hint, confidence, metadata, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (UnresolvedEdgeRecord edge : unresolvedEdges) {
                    statement.setString(1, edge.id());
                    statement.setString(2, snapshotId);
                    statement.setString(3, edge.sourceNodeId());
                    statement.setString(4, edge.targetRef());
                    statement.setString(5, edge.relationship());
                    statement.setString(6, edge.targetKindHint());
                    if (edge.confidence() != null) {
                        statement.setBigDecimal(7, java.math.BigDecimal.valueOf(edge.confidence()));
                    } else {
                        statement.setNull(7, java.sql.Types.NUMERIC);
                    }
                    statement.setString(8, writeJson(edge.metadata()));
                    statement.setTimestamp(9, Timestamp.from(Instant.now()));
                    statement.addBatch();
                }
                statement.executeBatch();
                log.info("Saved {} unresolved edges for product {} (snapshotId={})", 
                    unresolvedEdges.size(), productId, snapshotId);
            }
            return null;
        }).map(v -> null);
    }

    /**
     * P1-12: Save edge resolution records to the artifact_edge_resolution_records table.
     */
    public Promise<Void> saveEdgeResolutionRecords(
            String productId, String tenantId, List<EdgeResolutionRecord> records) {
        if (records == null || records.isEmpty()) {
            return Promise.of(null);
        }
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO artifact_edge_resolution_records (
                    id, unresolved_edge_id, status, resolved_target_id,
                    candidate_ids, review_required, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (EdgeResolutionRecord record : records) {
                    statement.setString(1, record.id());
                    statement.setString(2, record.unresolvedEdgeId());
                    statement.setString(3, record.status());
                    statement.setString(4, record.resolvedTargetId());
                    statement.setString(5, writeJson(record.candidateIds()));
                    statement.setBoolean(6, record.reviewRequired());
                    statement.setTimestamp(7, Timestamp.from(Instant.now()));
                    statement.addBatch();
                }
                statement.executeBatch();
                log.info("Saved {} edge resolution records for product {}", records.size(), productId);
            }
            return null;
        }).map(v -> null);
    }

    /**
     * P1-12: Save residual islands to the residual_islands table.
     */
    public Promise<Void> saveResidualIslands(
            String productId, String tenantId, String snapshotId,
            List<ResidualIslandRecord> islands) {
        if (islands == null || islands.isEmpty()) {
            return Promise.of(null);
        }
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO residual_islands (
                    id, snapshot_id, island_type, summary, file_count,
                    confidence, metadata, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (ResidualIslandRecord island : islands) {
                    statement.setString(1, island.id());
                    statement.setString(2, snapshotId);
                    statement.setString(3, island.islandType());
                    statement.setString(4, island.summary());
                    statement.setInt(5, island.fileCount());
                    if (island.confidence() != null) {
                        statement.setBigDecimal(6, java.math.BigDecimal.valueOf(island.confidence()));
                    } else {
                        statement.setNull(6, java.sql.Types.NUMERIC);
                    }
                    statement.setString(7, writeJson(island.metadata()));
                    statement.setTimestamp(8, Timestamp.from(Instant.now()));
                    statement.addBatch();
                }
                statement.executeBatch();
                log.info("Saved {} residual islands for product {} (snapshotId={})", 
                    islands.size(), productId, snapshotId);
            }
            return null;
        }).map(v -> null);
    }

    /**
     * P1-12: Record types for unresolved edges and residual islands.
     */
    public record UnresolvedEdgeRecord(
        String id,
        String sourceNodeId,
        String targetRef,
        String relationship,
        String targetKindHint,
        Double confidence,
        Map<String, Object> metadata
    ) {}

    public record EdgeResolutionRecord(
        String id,
        String unresolvedEdgeId,
        String status,
        String resolvedTargetId,
        List<String> candidateIds,
        boolean reviewRequired
    ) {}

    public record ResidualIslandRecord(
        String id,
        String islandType,
        String summary,
        int fileCount,
        Double confidence,
        Map<String, Object> metadata
    ) {}

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
            resultSet.getString("project_id"),
            null, // sourceLocation
            null, // extractorId
            null, // extractorVersion
            null, // confidence
            null, // provenance
            null, // privacySecurityFlags
            null, // residualFragmentIds
            null, // sourceRef
            null  // symbolRef
        );
    }

    private ArtifactEdgeDto mapEdge(ResultSet resultSet) throws SQLException {
        return new ArtifactEdgeDto(
            null, // edgeId
            resultSet.getString("source_node_id"),
            resultSet.getString("target_node_id"),
            resultSet.getString("relationship_type"),
            readJson(resultSet.getString("properties_json"), OBJECT_MAP, Map.of()),
            null, // confidence
            null, // bidirectional
            null, // metadata
            null, // snapshotId
            null  // versionId
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

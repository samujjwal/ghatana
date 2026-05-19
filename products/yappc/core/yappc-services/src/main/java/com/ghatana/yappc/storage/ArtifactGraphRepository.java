package com.ghatana.yappc.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.domain.artifact.SourceLocationDto;
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
 * Nodes and edges now track which repository scan they came from, enabling cross-scan diffing.
 * Removed nodes are tombstoned rather than hard-deleted to preserve history.
 *
 * P0: Updated to include workspace_id for complete tenant/workspace/project scope isolation.
 * All queries now include workspace scope to prevent cross-workspace data leakage.
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

    public Promise<Void> saveNodes(String projectId, String tenantId, String workspaceId, List<ArtifactNodeDto> nodes) {
        return saveNodes(projectId, tenantId, workspaceId, nodes, null, null);
    }

    /**
     * P4-4: Save nodes with snapshot/version ID tracking for incremental upsert.
     * Snapshot ID identifies which repository scan produced these nodes.
     * Version ID enables tracking of graph evolution over time.
     * P1-12: Skip unchanged nodes by checksum comparison to reduce database writes.
     */
    public Promise<Void> saveNodes(String projectId, String tenantId, String workspaceId, List<ArtifactNodeDto> nodes, String snapshotId, String versionId) {
        if (nodes == null || nodes.isEmpty()) {
            return Promise.of(null);
        }
        return Promise.ofBlocking(executor, () -> {
            // P1-12: Fetch existing checksums to skip unchanged nodes
            Map<String, String> existingChecksums = new HashMap<>();
            String checksumQuery = """
                SELECT node_id, content_checksum
                FROM artifact_nodes
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND is_tombstone = false
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(checksumQuery)) {
                stmt.setString(1, tenantId);
                stmt.setString(2, workspaceId);
                stmt.setString(3, projectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        existingChecksums.put(rs.getString("node_id"), rs.getString("content_checksum"));
                    }
                }
            }

            String sql = """
                INSERT INTO artifact_nodes (
                    node_id, node_type, node_name, file_path, content_snippet,
                    properties_json, tags_json, tenant_id, workspace_id, project_id, content_checksum,
                    source_location_json, extractor_id, extractor_version, confidence, provenance,
                    privacy_security_flags_json, residual_fragment_ids_json, source_ref, symbol_ref,
                    created_at, updated_at, snapshot_id, version_id, is_tombstone
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, workspace_id, node_id) DO UPDATE SET
                    node_type = EXCLUDED.node_type,
                    node_name = EXCLUDED.node_name,
                    file_path = EXCLUDED.file_path,
                    content_snippet = EXCLUDED.content_snippet,
                    properties_json = EXCLUDED.properties_json,
                    tags_json = EXCLUDED.tags_json,
                    content_checksum = EXCLUDED.content_checksum,
                    source_location_json = EXCLUDED.source_location_json,
                    extractor_id = EXCLUDED.extractor_id,
                    extractor_version = EXCLUDED.extractor_version,
                    confidence = EXCLUDED.confidence,
                    provenance = EXCLUDED.provenance,
                    privacy_security_flags_json = EXCLUDED.privacy_security_flags_json,
                    residual_fragment_ids_json = EXCLUDED.residual_fragment_ids_json,
                    source_ref = EXCLUDED.source_ref,
                    symbol_ref = EXCLUDED.symbol_ref,
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
                            || (node.projectId() != null && !projectId.equals(node.projectId()))) {
                        log.warn("Ignoring node payload scope for nodeId={} and using server scope tenantId={}, projectId={}",
                                node.id(), tenantId, projectId);
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
                    statement.setString(9, workspaceId);
                    statement.setString(10, projectId);
                    statement.setString(11, newChecksum);
                    statement.setString(12, writeJson(node.sourceLocation()));
                    statement.setString(13, node.extractorId());
                    statement.setString(14, node.extractorVersion());
                    if (node.confidence() != null) {
                        statement.setDouble(15, node.confidence());
                    } else {
                        statement.setNull(15, java.sql.Types.DOUBLE);
                    }
                    statement.setString(16, node.provenance());
                    statement.setString(17, writeJson(node.privacySecurityFlags()));
                    statement.setString(18, writeJson(node.residualFragmentIds()));
                    statement.setString(19, node.sourceRef());
                    statement.setString(20, node.symbolRef());
                    Instant now = Instant.now();
                    statement.setTimestamp(21, Timestamp.from(now));
                    statement.setTimestamp(22, Timestamp.from(now));
                    statement.setString(23, snapshotId);
                    statement.setString(24, versionId);
                    statement.setBoolean(25, false); // is_tombstone
                    statement.addBatch();
                }
                statement.executeBatch();
                log.info("Saved {} artifact nodes for project {} (snapshotId={}, versionId={}, skipped={})", 
                    nodes.size() - skipped, projectId, snapshotId, versionId, skipped);
            }
            return null;
        }).map(v -> null);
    }

    public Promise<Void> saveEdges(String projectId, String tenantId, String workspaceId, List<ArtifactEdgeDto> edges) {
        return saveEdges(projectId, tenantId, workspaceId, edges, null, null);
    }

    /**
     * P4-4: Save edges with snapshot/version ID tracking for incremental upsert.
     */
    public Promise<Void> saveEdges(String projectId, String tenantId, String workspaceId, List<ArtifactEdgeDto> edges, String snapshotId, String versionId) {
        if (edges == null || edges.isEmpty()) {
            return Promise.of(null);
        }
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO artifact_edges (
                    source_node_id, target_node_id, relationship_type,
                    properties_json, edge_id, confidence, bidirectional, edge_metadata_json,
                    tenant_id, workspace_id, project_id, created_at, updated_at, snapshot_id, version_id, is_tombstone
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, workspace_id, source_node_id, target_node_id, relationship_type) DO UPDATE SET
                    properties_json = EXCLUDED.properties_json,
                    edge_id = EXCLUDED.edge_id,
                    confidence = EXCLUDED.confidence,
                    bidirectional = EXCLUDED.bidirectional,
                    edge_metadata_json = EXCLUDED.edge_metadata_json,
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
                    statement.setString(5, edge.edgeId());
                    if (edge.confidence() != null) {
                        statement.setDouble(6, edge.confidence());
                    } else {
                        statement.setNull(6, java.sql.Types.DOUBLE);
                    }
                    if (edge.bidirectional() != null) {
                        statement.setBoolean(7, edge.bidirectional());
                    } else {
                        statement.setNull(7, java.sql.Types.BOOLEAN);
                    }
                    statement.setString(8, writeJson(edge.metadata()));
                    statement.setString(9, tenantId);
                    statement.setString(10, workspaceId);
                    statement.setString(11, projectId);
                    Instant now = Instant.now();
                    statement.setTimestamp(12, Timestamp.from(now));
                    statement.setTimestamp(13, Timestamp.from(now));
                    statement.setString(14, snapshotId != null ? snapshotId : edge.snapshotId());
                    statement.setString(15, versionId != null ? versionId : edge.versionId());
                    statement.setBoolean(16, false); // is_tombstone
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        }).whenComplete((result, e) -> {
            if (e == null) {
                log.info("Saved {} artifact edges for project {} (snapshotId={}, versionId={})", 
                    edges.size(), projectId, snapshotId, versionId);
            }
        }).map(v -> null);
    }

    /**
     * P4-2: Alias for saveNodes - provides incremental upsert semantics.
     * Uses ON CONFLICT DO UPDATE to only insert/update changed nodes.
     */
    public Promise<Void> upsertNodes(String projectId, String tenantId, String workspaceId, List<ArtifactNodeDto> nodes, String snapshotId, String versionId, String contentChecksum) {
        return saveNodes(projectId, tenantId, workspaceId, nodes, snapshotId, versionId);
    }

    /**
     * P4-2: Alias for saveEdges - provides incremental upsert semantics.
     * Uses ON CONFLICT DO UPDATE to only insert/update changed edges.
     */
    public Promise<Void> upsertEdges(String projectId, String tenantId, String workspaceId, List<ArtifactEdgeDto> edges, String snapshotId, String versionId) {
        return saveEdges(projectId, tenantId, workspaceId, edges, snapshotId, versionId);
    }

    public Promise<List<ArtifactNodeDto>> findNodesByProduct(String projectId, String tenantId, String workspaceId, int limit) {
        return findNodesByProduct(projectId, tenantId, workspaceId, limit, false);
    }

    /**
     * P4-4: Find nodes by product with option to include/exclude tombstoned nodes.
     * Active graph queries should exclude tombstones (includeTombstones=false).
     * Historical/diff queries should include tombstones (includeTombstones=true).
     */
    public Promise<List<ArtifactNodeDto>> findNodesByProduct(String projectId, String tenantId, String workspaceId, int limit, boolean includeTombstones) {
        return Promise.ofBlocking(executor, () -> {
            String tombstoneFilter = includeTombstones ? "" : " AND is_tombstone = false";
            String sql = """
                SELECT node_id, node_type, node_name, file_path, content_snippet,
                      properties_json, tags_json, tenant_id, workspace_id, project_id,
                      source_location_json, extractor_id, extractor_version, confidence, provenance,
                      privacy_security_flags_json, residual_fragment_ids_json, source_ref, symbol_ref
                FROM artifact_nodes
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ?
                """ + tombstoneFilter + """
                ORDER BY updated_at DESC
                LIMIT ?
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
                statement.setInt(4, limit);
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

    public Promise<List<ArtifactNodeDto>> findNodesByIds(List<String> nodeIds, String tenantId, String workspaceId) {
        if (nodeIds.isEmpty()) {
            return Promise.of(List.of());
        }
        return Promise.ofBlocking(executor, () -> {
            String placeholders = nodeIds.stream().map(id -> "?").collect(Collectors.joining(", "));
            String sql = """
                SELECT node_id, node_type, node_name, file_path, content_snippet,
                      properties_json, tags_json, tenant_id, workspace_id, project_id,
                      source_location_json, extractor_id, extractor_version, confidence, provenance,
                      privacy_security_flags_json, residual_fragment_ids_json, source_ref, symbol_ref
                FROM artifact_nodes
                WHERE tenant_id = ? AND workspace_id = ? AND node_id IN (%s)
                """.formatted(placeholders);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                for (int index = 0; index < nodeIds.size(); index++) {
                    statement.setString(index + 3, nodeIds.get(index));
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

    public Promise<List<ArtifactEdgeDto>> findEdgesByProduct(String projectId, String tenantId, String workspaceId) {
        return findEdgesByProduct(projectId, tenantId, workspaceId, false);
    }

    /**
     * P4-4: Find edges by product with option to include/exclude tombstoned edges.
     */
    public Promise<List<ArtifactEdgeDto>> findEdgesByProduct(String projectId, String tenantId, String workspaceId, boolean includeTombstones) {
        return Promise.ofBlocking(executor, () -> {
            String tombstoneFilter = includeTombstones ? "" : " AND is_tombstone = false";
            String sql = """
                  SELECT source_node_id, target_node_id, relationship_type, properties_json,
                      edge_id, confidence, bidirectional, edge_metadata_json, tenant_id, workspace_id,
                      snapshot_id, version_id
                FROM artifact_edges
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ?
                """ + tombstoneFilter + """
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
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

    public Promise<Boolean> deleteGraphForProduct(String projectId, String tenantId, String workspaceId) {
        return tombstoneGraphForProduct(projectId, tenantId, workspaceId);
    }

    /**
     * Tombstone only nodes and edges associated with a specific snapshot within scoped project data.
     * This is used as a compensation rollback when downstream persistence fails post-ingest.
     */
    public Promise<Boolean> tombstoneGraphForSnapshot(String projectId, String tenantId, String workspaceId, String snapshotId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection()) {
                String tombstoneEdges = """
                    UPDATE artifact_edges
                    SET is_tombstone = true, updated_at = ?
                    WHERE tenant_id = ? AND workspace_id = ? AND project_id = ?
                      AND snapshot_id = ? AND is_tombstone = false
                    """;
                int edgeCount;
                try (PreparedStatement edgeStmt = connection.prepareStatement(tombstoneEdges)) {
                    edgeStmt.setTimestamp(1, Timestamp.from(Instant.now()));
                    edgeStmt.setString(2, tenantId);
                    edgeStmt.setString(3, workspaceId);
                    edgeStmt.setString(4, projectId);
                    edgeStmt.setString(5, snapshotId);
                    edgeCount = edgeStmt.executeUpdate();
                }

                String tombstoneNodes = """
                    UPDATE artifact_nodes
                    SET is_tombstone = true, updated_at = ?
                    WHERE tenant_id = ? AND workspace_id = ? AND project_id = ?
                      AND snapshot_id = ? AND is_tombstone = false
                    """;
                int nodeCount;
                try (PreparedStatement nodeStmt = connection.prepareStatement(tombstoneNodes)) {
                    nodeStmt.setTimestamp(1, Timestamp.from(Instant.now()));
                    nodeStmt.setString(2, tenantId);
                    nodeStmt.setString(3, workspaceId);
                    nodeStmt.setString(4, projectId);
                    nodeStmt.setString(5, snapshotId);
                    nodeCount = nodeStmt.executeUpdate();
                }

                log.info(
                    "Tombstoned snapshot-scoped graph rows for project {} (snapshotId={}, nodes={}, edges={})",
                    projectId,
                    snapshotId,
                    nodeCount,
                    edgeCount
                );
                return nodeCount > 0 || edgeCount > 0;
            }
        });
    }

    /**
     * P4-4: Tombstone all nodes and edges for a product instead of hard delete.
     * This preserves history and enables cross-snapshot diffing.
     */
    public Promise<Boolean> tombstoneGraphForProduct(String projectId, String tenantId, String workspaceId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection()) {
                // Tombstone edges first (foreign key dependency)
                String tombstoneEdges = """
                    UPDATE artifact_edges
                    SET is_tombstone = true, updated_at = ?
                    WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND is_tombstone = false
                    """;
                try (PreparedStatement edgeStmt = connection.prepareStatement(tombstoneEdges)) {
                    edgeStmt.setTimestamp(1, Timestamp.from(Instant.now()));
                    edgeStmt.setString(2, tenantId);
                    edgeStmt.setString(3, workspaceId);
                    edgeStmt.setString(4, projectId);
                    int edgeCount = edgeStmt.executeUpdate();
                    log.info("Tombstoned {} edges for project {}", edgeCount, projectId);
                }
                // Tombstone nodes
                String tombstoneNodes = """
                    UPDATE artifact_nodes
                    SET is_tombstone = true, updated_at = ?
                    WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND is_tombstone = false
                    """;
                try (PreparedStatement nodeStmt = connection.prepareStatement(tombstoneNodes)) {
                    nodeStmt.setTimestamp(1, Timestamp.from(Instant.now()));
                    nodeStmt.setString(2, tenantId);
                    nodeStmt.setString(3, workspaceId);
                    nodeStmt.setString(4, projectId);
                    int nodeCount = nodeStmt.executeUpdate();
                    log.info("Tombstoned {} nodes for project {}", nodeCount, projectId);
                    return nodeCount > 0;
                }
            }
        });
    }

    /**
     * P4-4: Tombstone specific nodes by ID list.
     * Used when files/symbols are removed from the source repository.
     */
    public Promise<Integer> tombstoneNodes(List<String> nodeIds, String tenantId, String workspaceId, String snapshotId) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return Promise.of(0);
        }
        return Promise.ofBlocking(executor, () -> {
            String placeholders = nodeIds.stream().map(id -> "?").collect(Collectors.joining(", "));
            String sql = """
                UPDATE artifact_nodes
                SET is_tombstone = true, updated_at = ?, snapshot_id = ?
                WHERE tenant_id = ? AND workspace_id = ? AND node_id IN (%s) AND is_tombstone = false
                """.formatted(placeholders);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(Instant.now()));
                statement.setString(2, snapshotId);
                statement.setString(3, tenantId);
                statement.setString(4, workspaceId);
                for (int index = 0; index < nodeIds.size(); index++) {
                    statement.setString(index + 5, nodeIds.get(index));
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
    public Promise<Integer> tombstoneEdges(List<String> sourceNodeIds, List<String> targetNodeIds, String tenantId, String workspaceId, String snapshotId) {
        if ((sourceNodeIds == null || sourceNodeIds.isEmpty()) && (targetNodeIds == null || targetNodeIds.isEmpty())) {
            return Promise.of(0);
        }
        return Promise.ofBlocking(executor, () -> {
            StringBuilder sql = new StringBuilder(
                "UPDATE artifact_edges SET is_tombstone = true, updated_at = ?, snapshot_id = ? " +
                "WHERE tenant_id = ? AND workspace_id = ? AND is_tombstone = false"
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
                statement.setString(4, workspaceId);
                for (int index = 0; index < params.size(); index++) {
                    statement.setString(index + 5, params.get(index));
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
     * P0: Added workspaceId parameter and workspace_id filter to prevent cross-workspace data leakage.
     */
    public Promise<PageResult<ArtifactNodeDto>> findNodesPaginated(
            String projectId, String tenantId, String workspaceId, String cursor, int pageSize) {
        return Promise.ofBlocking(executor, () -> {
            String cursorFilter = "";
            List<Object> params = new ArrayList<>();
            params.add(tenantId);
            params.add(workspaceId);
            params.add(projectId);
            
            if (cursor != null && !cursor.isBlank()) {
                cursorFilter = " AND updated_at < ?::timestamp";
                params.add(cursor);
            }
            
            String sql = """
                SELECT node_id, node_type, node_name, file_path, content_snippet,
                        properties_json, tags_json, tenant_id, workspace_id, project_id, updated_at,
                        source_location_json, extractor_id, extractor_version, confidence, provenance,
                        privacy_security_flags_json, residual_fragment_ids_json, source_ref, symbol_ref
                FROM artifact_nodes
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND is_tombstone = false
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
     * P0: Added workspaceId parameter and workspace_id filter to prevent cross-workspace data leakage.
     */
    public Promise<PageResult<ArtifactEdgeDto>> findEdgesPaginated(
            String projectId, String tenantId, String workspaceId, String cursor, int pageSize) {
        return Promise.ofBlocking(executor, () -> {
            String cursorFilter = "";
            List<Object> params = new ArrayList<>();
            params.add(tenantId);
            params.add(workspaceId);
            params.add(projectId);
            
            if (cursor != null && !cursor.isBlank()) {
                cursorFilter = " AND updated_at < ?::timestamp";
                params.add(cursor);
            }
            
            String sql = """
                  SELECT source_node_id, target_node_id, relationship_type, properties_json, updated_at,
                      edge_id, confidence, bidirectional, edge_metadata_json, snapshot_id, version_id
                FROM artifact_edges
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND is_tombstone = false
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
     * P0: Added workspaceId parameter and workspace_id filter to prevent cross-workspace data leakage.
     */
    public Promise<SnapshotDiffResult> computeSnapshotDiff(
            String projectId, String tenantId, String workspaceId, String fromSnapshotId, String toSnapshotId) {
        return Promise.ofBlocking(executor, () -> {
            String nodeSql = """
                SELECT node_id, node_type, node_name, file_path, content_snippet,
                      properties_json, tags_json, tenant_id, workspace_id, project_id, snapshot_id,
                      source_location_json, extractor_id, extractor_version, confidence, provenance,
                      privacy_security_flags_json, residual_fragment_ids_json, source_ref, symbol_ref
                FROM artifact_nodes
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND snapshot_id IN (?, ?)
                ORDER BY snapshot_id
                """;
            
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(nodeSql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
                statement.setString(4, fromSnapshotId);
                statement.setString(5, toSnapshotId);
                
                try (ResultSet resultSet = statement.executeQuery()) {
                    Map<String, ArtifactNodeDto> fromNodes = new HashMap<>();
                    Map<String, ArtifactNodeDto> toNodes = new HashMap<>();
                    
                    while (resultSet.next()) {
                        String snapshotId = resultSet.getString("snapshot_id");
                        ArtifactNodeDto node = mapNode(resultSet);
                        
                        if (Objects.equals(snapshotId, fromSnapshotId)) {
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
                            if (!Objects.equals(fromNode.content(), node.content()) ||
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
                    
                    EdgeDiff edgeDiff = computeEdgeDiff(connection, tenantId, workspaceId, projectId, fromSnapshotId, toSnapshotId);
                    return new SnapshotDiffResult(
                            addedNodes,
                            removedNodes,
                            modifiedNodes,
                            edgeDiff.addedEdges(),
                            edgeDiff.removedEdges(),
                            edgeDiff.modifiedEdges());
                }
            }
        });
    }

    private EdgeDiff computeEdgeDiff(
            Connection connection,
            String tenantId,
            String workspaceId,
            String projectId,
            String fromSnapshotId,
            String toSnapshotId) throws SQLException {
        String edgeSql = """
            SELECT edge_id, source_node_id, target_node_id, relationship_type,
                   properties_json, confidence, bidirectional, edge_metadata_json, snapshot_id, version_id
            FROM artifact_edges
            WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND snapshot_id IN (?, ?)
            ORDER BY snapshot_id
            """;
        try (PreparedStatement statement = connection.prepareStatement(edgeSql)) {
            statement.setString(1, tenantId);
            statement.setString(2, workspaceId);
            statement.setString(3, projectId);
            statement.setString(4, fromSnapshotId);
            statement.setString(5, toSnapshotId);

            Map<String, ArtifactEdgeDto> fromEdges = new HashMap<>();
            Map<String, ArtifactEdgeDto> toEdges = new HashMap<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String snapshotId = resultSet.getString("snapshot_id");
                    ArtifactEdgeDto edge = mapEdge(resultSet);
                    String key = edgeDiffKey(edge);
                    if (Objects.equals(snapshotId, fromSnapshotId)) {
                        fromEdges.put(key, edge);
                    } else {
                        toEdges.put(key, edge);
                    }
                }
            }

            List<ArtifactEdgeDto> addedEdges = new ArrayList<>();
            List<ArtifactEdgeDto> removedEdges = new ArrayList<>();
            List<ArtifactEdgeDto> modifiedEdges = new ArrayList<>();

            for (Map.Entry<String, ArtifactEdgeDto> entry : toEdges.entrySet()) {
                ArtifactEdgeDto fromEdge = fromEdges.get(entry.getKey());
                ArtifactEdgeDto toEdge = entry.getValue();
                if (fromEdge == null) {
                    addedEdges.add(toEdge);
                } else if (!Objects.equals(fromEdge.properties(), toEdge.properties())
                        || !Objects.equals(fromEdge.metadata(), toEdge.metadata())
                        || !Objects.equals(fromEdge.confidence(), toEdge.confidence())
                        || !Objects.equals(fromEdge.bidirectional(), toEdge.bidirectional())) {
                    modifiedEdges.add(toEdge);
                }
            }

            for (Map.Entry<String, ArtifactEdgeDto> entry : fromEdges.entrySet()) {
                if (!toEdges.containsKey(entry.getKey())) {
                    removedEdges.add(entry.getValue());
                }
            }

            return new EdgeDiff(addedEdges, removedEdges, modifiedEdges);
        }
    }

    private String edgeDiffKey(ArtifactEdgeDto edge) {
        return edge.sourceNodeId() + "\u0000" + edge.targetNodeId() + "\u0000" + edge.relationshipType();
    }

    private record EdgeDiff(
            List<ArtifactEdgeDto> addedEdges,
            List<ArtifactEdgeDto> removedEdges,
            List<ArtifactEdgeDto> modifiedEdges
    ) {}

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
    // P0: Query-Specific Methods for Accurate Graph Analysis
    // ============================================================================

    /**
     * P0: Find orphaned nodes across all data (not just current page).
     * Returns node IDs that are not referenced as targets by any edge.
     */
    public Promise<List<String>> findOrphanedNodeIds(String projectId, String tenantId, String workspaceId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT DISTINCT n.node_id
                FROM artifact_nodes n
                WHERE n.tenant_id = ? AND n.workspace_id = ? AND n.project_id = ? AND n.is_tombstone = false
                AND n.node_id NOT IN (
                    SELECT DISTINCT e.target_node_id
                    FROM artifact_edges e
                    WHERE e.tenant_id = ? AND e.workspace_id = ? AND e.project_id = ? AND e.is_tombstone = false
                )
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
                statement.setString(4, tenantId);
                statement.setString(5, workspaceId);
                statement.setString(6, projectId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<String> orphanedIds = new ArrayList<>();
                    while (resultSet.next()) {
                        orphanedIds.add(resultSet.getString("node_id"));
                    }
                    return orphanedIds;
                }
            }
        });
    }

    /**
     * P0: Find dependencies for given node IDs across all data.
     * Returns a map of source node ID to list of target node IDs.
     */
    public Promise<Map<String, List<String>>> findDependencies(String projectId, String tenantId, String workspaceId, List<String> sourceNodeIds) {
        if (sourceNodeIds == null || sourceNodeIds.isEmpty()) {
            return Promise.of(Map.of());
        }
        return Promise.ofBlocking(executor, () -> {
            String placeholders = sourceNodeIds.stream().map(id -> "?").collect(Collectors.joining(", "));
            String sql = """
                SELECT source_node_id, target_node_id
                FROM artifact_edges
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND is_tombstone = false
                AND source_node_id IN (%s)
                """.formatted(placeholders);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
                for (int index = 0; index < sourceNodeIds.size(); index++) {
                    statement.setString(index + 4, sourceNodeIds.get(index));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    Map<String, List<String>> dependencies = new HashMap<>();
                    while (resultSet.next()) {
                        String sourceId = resultSet.getString("source_node_id");
                        String targetId = resultSet.getString("target_node_id");
                        dependencies.computeIfAbsent(sourceId, k -> new ArrayList<>()).add(targetId);
                    }
                    return dependencies;
                }
            }
        });
    }

    /**
     * P0: Find dependents for given node IDs across all data.
     * Returns a map of target node ID to list of source node IDs.
     */
    public Promise<Map<String, List<String>>> findDependents(String projectId, String tenantId, String workspaceId, List<String> targetNodeIds) {
        if (targetNodeIds == null || targetNodeIds.isEmpty()) {
            return Promise.of(Map.of());
        }
        return Promise.ofBlocking(executor, () -> {
            String placeholders = targetNodeIds.stream().map(id -> "?").collect(Collectors.joining(", "));
            String sql = """
                SELECT source_node_id, target_node_id
                FROM artifact_edges
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND is_tombstone = false
                AND target_node_id IN (%s)
                """.formatted(placeholders);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
                for (int index = 0; index < targetNodeIds.size(); index++) {
                    statement.setString(index + 4, targetNodeIds.get(index));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    Map<String, List<String>> dependents = new HashMap<>();
                    while (resultSet.next()) {
                        String sourceId = resultSet.getString("source_node_id");
                        String targetId = resultSet.getString("target_node_id");
                        dependents.computeIfAbsent(targetId, k -> new ArrayList<>()).add(sourceId);
                    }
                    return dependents;
                }
            }
        });
    }

    /**
     * P0: Get accurate node and edge counts across all data.
     */
    public Promise<Map<String, Long>> getGraphStats(String projectId, String tenantId, String workspaceId) {
        return Promise.ofBlocking(executor, () -> {
            Map<String, Long> stats = new HashMap<>();
            
            String nodeCountSql = """
                SELECT COUNT(*) as count
                FROM artifact_nodes
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND is_tombstone = false
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(nodeCountSql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        stats.put("nodeCount", resultSet.getLong("count"));
                    }
                }
            }
            
            String edgeCountSql = """
                SELECT COUNT(*) as count
                FROM artifact_edges
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND is_tombstone = false
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(edgeCountSql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        stats.put("edgeCount", resultSet.getLong("count"));
                    }
                }
            }
            
            String typeCountSql = """
                SELECT node_type, COUNT(*) as count
                FROM artifact_nodes
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND is_tombstone = false
                GROUP BY node_type
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(typeCountSql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        stats.put("nodeType_" + resultSet.getString("node_type"), resultSet.getLong("count"));
                    }
                }
            }
            
            return stats;
        });
    }

    // ============================================================================
    // P1-12: Unresolved Edges and Residual Islands Persistence
    // ============================================================================

    /**
     * P1-12: Save unresolved edges to the artifact_unresolved_edges table.
     */
    public Promise<Void> saveUnresolvedEdges(
            String projectId, String tenantId, String workspaceId, String snapshotId,
            List<UnresolvedEdgeRecord> unresolvedEdges) {
        if (unresolvedEdges == null || unresolvedEdges.isEmpty()) {
            return Promise.of(null);
        }
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO artifact_unresolved_edges (
                    id, tenant_id, project_id, workspace_id, snapshot_id, source_node_id, target_ref, relationship,
                    target_kind_hint, confidence, metadata_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (UnresolvedEdgeRecord edge : unresolvedEdges) {
                    statement.setString(1, edge.id());
                    statement.setString(2, tenantId);
                    statement.setString(3, projectId);
                    statement.setString(4, workspaceId);
                    statement.setString(5, snapshotId);
                    statement.setString(6, edge.sourceNodeId());
                    statement.setString(7, edge.targetRef());
                    statement.setString(8, edge.relationship());
                    statement.setString(9, edge.targetKindHint());
                    if (edge.confidence() != null) {
                        statement.setBigDecimal(10, java.math.BigDecimal.valueOf(edge.confidence()));
                    } else {
                        statement.setNull(10, java.sql.Types.NUMERIC);
                    }
                    statement.setString(11, writeJson(edge.metadata()));
                    statement.setTimestamp(12, Timestamp.from(Instant.now()));
                    statement.addBatch();
                }
                statement.executeBatch();
                log.info("Saved {} unresolved edges for project {} (snapshotId={})", 
                    unresolvedEdges.size(), projectId, snapshotId);
            }
            return null;
        }).map(v -> null);
    }

    /**
     * P1-12: Save edge resolution records to the artifact_edge_resolution_records table.
     */
    public Promise<Void> saveEdgeResolutionRecords(
            String projectId, String tenantId, String workspaceId, List<EdgeResolutionRecord> records) {
        if (records == null || records.isEmpty()) {
            return Promise.of(null);
        }
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO artifact_edge_resolution_records (
                    id, tenant_id, project_id, workspace_id, unresolved_edge_id, status, resolved_target_id,
                    candidate_ids_json, review_required, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (EdgeResolutionRecord record : records) {
                    statement.setString(1, record.id());
                    statement.setString(2, tenantId);
                    statement.setString(3, projectId);
                    statement.setString(4, workspaceId);
                    statement.setString(5, record.unresolvedEdgeId());
                    statement.setString(6, record.status());
                    statement.setString(7, record.resolvedTargetId());
                    statement.setString(8, writeJson(record.candidateIds()));
                    statement.setBoolean(9, record.reviewRequired());
                    statement.setTimestamp(10, Timestamp.from(Instant.now()));
                    statement.addBatch();
                }
                statement.executeBatch();
                log.info("Saved {} edge resolution records for project {}", records.size(), projectId);
            }
            return null;
        }).map(v -> null);
    }

    /**
     * P1-12: Save residual islands to the residual_islands table.
     */
    public Promise<Void> saveResidualIslands(
            String projectId, String tenantId, String snapshotId,
            List<ResidualIslandRecord> islands) {
        if (islands == null || islands.isEmpty()) {
            return Promise.of(null);
        }
        // P0: Validate that all islands have required fields for full fidelity
        for (ResidualIslandRecord island : islands) {
            if (island.originalSource() == null || island.originalSource().isBlank()) {
                throw new IllegalArgumentException(
                    "ResidualIsland '" + island.id() + "' missing required field 'originalSource'. " +
                    "Lossy ingest rejected - full residual payload required.");
            }
            if (island.checksum() == null || island.checksum().isBlank()) {
                throw new IllegalArgumentException(
                    "ResidualIsland '" + island.id() + "' missing required field 'checksum'. " +
                    "Lossy ingest rejected - full residual payload required.");
            }
            if (island.rawFragmentRef() == null || island.rawFragmentRef().isBlank()) {
                throw new IllegalArgumentException(
                    "ResidualIsland '" + island.id() + "' missing required field 'rawFragmentRef'. " +
                    "Lossy ingest rejected - full residual payload required.");
            }
        }
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO residual_islands (
                    id, tenant_id, project_id, snapshot_id, workspace_id, island_type, summary, 
                    original_source, source_location_json, source_span, checksum, raw_fragment_ref, reason,
                    confidence, review_required, risk_score, file_count, metadata_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, workspace_id, project_id, id) DO UPDATE SET
                    island_type = EXCLUDED.island_type,
                    summary = EXCLUDED.summary,
                    original_source = EXCLUDED.original_source,
                    source_location_json = EXCLUDED.source_location_json,
                    source_span = EXCLUDED.source_span,
                    checksum = EXCLUDED.checksum,
                    raw_fragment_ref = EXCLUDED.raw_fragment_ref,
                    reason = EXCLUDED.reason,
                    confidence = EXCLUDED.confidence,
                    review_required = EXCLUDED.review_required,
                    risk_score = EXCLUDED.risk_score,
                    file_count = EXCLUDED.file_count,
                    metadata_json = EXCLUDED.metadata_json
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (ResidualIslandRecord island : islands) {
                    statement.setString(1, island.id());
                    statement.setString(2, tenantId);
                    statement.setString(3, projectId);
                    statement.setString(4, snapshotId);
                    statement.setString(5, island.workspaceId());
                    statement.setString(6, island.islandType());
                    statement.setString(7, island.summary());
                    statement.setString(8, island.originalSource());
                    statement.setString(9, writeJson(island.sourceLocation()));
                    statement.setString(10, island.sourceSpan());
                    statement.setString(11, island.checksum());
                    statement.setString(12, island.rawFragmentRef());
                    statement.setString(13, island.reason());
                    if (!Double.isNaN(island.confidence())) {
                        statement.setBigDecimal(14, java.math.BigDecimal.valueOf(island.confidence()));
                    } else {
                        statement.setNull(14, java.sql.Types.NUMERIC);
                    }
                    statement.setBoolean(15, island.reviewRequired());
                    if (!Double.isNaN(island.riskScore())) {
                        statement.setBigDecimal(16, java.math.BigDecimal.valueOf(island.riskScore()));
                    } else {
                        statement.setNull(16, java.sql.Types.NUMERIC);
                    }
                    statement.setInt(17, island.fileCount());
                    statement.setString(18, writeJson(island.metadata()));
                    statement.setTimestamp(19, Timestamp.from(Instant.now()));
                    statement.addBatch();
                }
                statement.executeBatch();
                log.info("Saved {} residual islands for project {} (snapshotId={}, workspaceId={})", 
                    islands.size(), projectId, snapshotId, islands.get(0).workspaceId());
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

    /**
     * P0: Updated ResidualIslandRecord with full schema for source fidelity including originalSource
     * Matches the proto definition with original source, source span, checksum, raw fragment ref, reason, risk, review requirement
     */
    public record ResidualIslandRecord(
        String id,
        String islandType,
        String summary,
        String originalSource, // P0: Added for round-trip fidelity
        Map<String, Object> sourceLocation,
        String sourceSpan,
        String checksum,
        String rawFragmentRef,
        String reason,
        double confidence,
        boolean reviewRequired,
        double riskScore,
        int fileCount,
        Map<String, Object> metadata,
        String tenantId,
        String projectId,
        String workspaceId,
        String snapshotId
    ) {}

    /**
     * Compute checksum for a node to enable incremental upsert.
     * Uses SHA-256 hash of node id + content + properties + tags.
     * P0: Made null-safe - handles null content by using empty string.
     * P0: Uses stable canonical fields for checksum to support nodes with null content.
     */
    private String computeChecksum(ArtifactNodeDto node) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(node.id().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // P0: Null-safe content handling - use empty string if content is null
            String content = node.content() != null ? node.content() : "";
            digest.update(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
            // P0: Null-safe fallback - use id hash if content is null
            String content = node.content() != null ? node.content() : "";
            return String.valueOf((node.id() + content).hashCode());
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
            readSourceLocation(resultSet.getString("source_location_json")),
            resultSet.getString("extractor_id"),
            resultSet.getString("extractor_version"),
            resultSet.getObject("confidence") != null ? resultSet.getDouble("confidence") : null,
            resultSet.getString("provenance"),
            readJson(resultSet.getString("privacy_security_flags_json"), STRING_LIST, List.of()),
            readJson(resultSet.getString("residual_fragment_ids_json"), STRING_LIST, List.of()),
            resultSet.getString("source_ref"),
            resultSet.getString("symbol_ref")
        );
    }

    private ArtifactEdgeDto mapEdge(ResultSet resultSet) throws SQLException {
        return new ArtifactEdgeDto(
            resultSet.getString("edge_id"),
            resultSet.getString("source_node_id"),
            resultSet.getString("target_node_id"),
            resultSet.getString("relationship_type"),
            readJson(resultSet.getString("properties_json"), OBJECT_MAP, Map.of()),
            resultSet.getObject("confidence") != null ? resultSet.getDouble("confidence") : null,
            resultSet.getObject("bidirectional") != null ? resultSet.getBoolean("bidirectional") : null,
            readJson(resultSet.getString("edge_metadata_json"), OBJECT_MAP, Map.of()),
            resultSet.getString("snapshot_id"),
            resultSet.getString("version_id")
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

    private SourceLocationDto readSourceLocation(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, OBJECT_MAP);
            return new SourceLocationDto(
                (String) map.get("filePath"),
                map.get("startLine") instanceof Number ? ((Number) map.get("startLine")).intValue() : 0,
                map.get("startColumn") instanceof Number ? ((Number) map.get("startColumn")).intValue() : 0,
                map.get("endLine") instanceof Number ? ((Number) map.get("endLine")).intValue() : 0,
                map.get("endColumn") instanceof Number ? ((Number) map.get("endColumn")).intValue() : 0
            );
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse source location JSON", e);
            return null;
        }
    }
}

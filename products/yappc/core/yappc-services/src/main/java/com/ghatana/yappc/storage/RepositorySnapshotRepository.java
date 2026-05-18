package com.ghatana.yappc.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.domain.source.SourceLocator;
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
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * @doc.type class
 * @doc.purpose JDBC persistence for immutable repository snapshots with full file metadata and source locator refs
 * @doc.layer infrastructure
 * @doc.pattern Repository
 *
 * P1: Provides durable storage for repository snapshots used in source import and
 * compile-back operations. Snapshots are immutable once created and referenced by
 * deterministic snapshot IDs derived from commit SHA and repo path.
 *
 * P1: Stores source locator refs to track the exact source (provider, repo, ref, credential) used for snapshot creation.
 */
public final class RepositorySnapshotRepository {

    private static final Logger log = LoggerFactory.getLogger(RepositorySnapshotRepository.class);
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public RepositorySnapshotRepository(DataSource dataSource, ObjectMapper objectMapper, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Persist a complete repository snapshot with all file metadata and source locator ref.
     *
     * P1: Stores source locator ref to track the exact source used for snapshot creation.
     * P1: Stores inventory metadata including skip reasons, package boundaries, and file counts.
     *
     * @param snapshot the snapshot to persist
     * @param sourceLocator the source locator used to create this snapshot
     * @param inventoryMetadata optional inventory metadata to persist
     * @return promise of the persisted snapshot
     */
    public Promise<RepositorySnapshot> saveSnapshot(RepositorySnapshot snapshot, SourceLocator sourceLocator, Map<String, Object> inventoryMetadata) {
        return Promise.ofBlocking(executor, () -> {
            // P0: Use scoped ON CONFLICT with tenant/workspace/project/provider/repo scope to prevent cross-tenant collisions
            // and to match the canonical repository_snapshots key in V15/V28 migrations.
            String snapshotSql = """
                INSERT INTO repository_snapshots (
                    snapshot_id, provider, repo_id, commit_sha, materialized_root,
                    checksum, content_hash, created_at, tenant_id, workspace_id, project_id,
                    created_by, diagnostics_json, source_locator_json, inventory_metadata_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, workspace_id, project_id, provider, repo_id, commit_sha) DO UPDATE SET
                    snapshot_id = EXCLUDED.snapshot_id,
                    materialized_root = EXCLUDED.materialized_root,
                    checksum = EXCLUDED.checksum,
                    content_hash = EXCLUDED.content_hash,
                    diagnostics_json = EXCLUDED.diagnostics_json,
                    source_locator_json = EXCLUDED.source_locator_json,
                    inventory_metadata_json = EXCLUDED.inventory_metadata_json
                """;

            String fileSql = """
                INSERT INTO repository_snapshot_files (
                    snapshot_id, relative_path, absolute_path, size_bytes, last_modified_at,
                    content_checksum, file_type
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (snapshot_id, relative_path) DO UPDATE SET
                    absolute_path = EXCLUDED.absolute_path,
                    size_bytes = EXCLUDED.size_bytes,
                    last_modified_at = EXCLUDED.last_modified_at,
                    content_checksum = EXCLUDED.content_checksum,
                    file_type = EXCLUDED.file_type
                """;

            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // Insert/update snapshot
                    try (PreparedStatement statement = connection.prepareStatement(snapshotSql)) {
                        statement.setString(1, snapshot.snapshotId());
                        statement.setString(2, snapshot.provider());
                        statement.setString(3, snapshot.repoId());
                        statement.setString(4, snapshot.commitSha().orElse(null));
                        statement.setString(5, snapshot.materializedRoot());
                        statement.setString(6, snapshot.checksum());
                        statement.setString(7, snapshot.contentHash().orElse(null));
                        statement.setTimestamp(8, Timestamp.from(snapshot.createdAt()));
                        statement.setString(9, snapshot.tenantId());
                        statement.setString(10, snapshot.workspaceId());
                        statement.setString(11, snapshot.projectId());
                        statement.setString(12, "system"); // created_by
                        statement.setString(13, writeDiagnostics(snapshot.diagnostics()));
                        statement.setString(14, writeSourceLocator(sourceLocator));
                        statement.setString(15, writeInventoryMetadata(inventoryMetadata));
                        statement.executeUpdate();
                    }

                    // Insert/update files
                    if (snapshot.files() != null && !snapshot.files().isEmpty()) {
                        try (PreparedStatement statement = connection.prepareStatement(fileSql)) {
                            for (RepositorySnapshot.SnapshotFile file : snapshot.files()) {
                                statement.setString(1, snapshot.snapshotId());
                                statement.setString(2, file.relativePath());
                                statement.setString(3, file.absolutePath());
                                statement.setLong(4, file.sizeBytes());
                                statement.setTimestamp(5, Timestamp.from(file.lastModified()));
                                statement.setString(6, file.contentChecksum());
                                statement.setString(7, inferFileType(file.relativePath()));
                                statement.addBatch();
                            }
                            statement.executeBatch();
                        }
                    }

                    connection.commit();
                    log.info("Persisted snapshot {} for repo {} with {} files, source locator ref, and inventory metadata",
                        snapshot.snapshotId(), snapshot.repoId(),
                        snapshot.files() != null ? snapshot.files().size() : 0);
                    return snapshot;
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        });
    }

    /**
     * Persist a complete repository snapshot with all file metadata and source locator ref.
     *
     * P1: Stores source locator ref to track the exact source used for snapshot creation.
     * P1: Backward-compatible method without inventory metadata.
     *
     * @param snapshot the snapshot to persist
     * @param sourceLocator the source locator used to create this snapshot
     * @return promise of the persisted snapshot
     */
    public Promise<RepositorySnapshot> saveSnapshot(RepositorySnapshot snapshot, SourceLocator sourceLocator) {
        return saveSnapshot(snapshot, sourceLocator, null);
    }

    /**
     * P0: Removed unscoped saveSnapshot method - all saves must be explicitly scoped.
     * Production code must use the scoped saveSnapshot(snapshot, sourceLocator) method.
     */
    /**
     * Find a snapshot by its deterministic ID with tenant/workspace/project scope.
     *
     * P0: Removed unscoped findById method - all queries must be explicitly scoped.
     * This prevents cross-tenant data leakage.
     *
     * @param snapshotId the snapshot ID
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param projectId the project ID
     * @return promise of optional snapshot
     */
    public Promise<Optional<RepositorySnapshot>> findById(String snapshotId, String tenantId, String workspaceId, String projectId) {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");

        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT snapshot_id, provider, repo_id, commit_sha, materialized_root,
                       checksum, content_hash, created_at, tenant_id, workspace_id, project_id,
                       created_by, diagnostics_json, source_locator_json, inventory_metadata_json
                FROM repository_snapshots
                WHERE snapshot_id = ? AND tenant_id = ? AND workspace_id = ? AND project_id = ?
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, snapshotId);
                statement.setString(2, tenantId);
                statement.setString(3, workspaceId);
                statement.setString(4, projectId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        List<RepositorySnapshot.SnapshotFile> files = loadSnapshotFiles(connection, snapshotId, tenantId, workspaceId, projectId);
                        return Optional.of(mapSnapshot(rs, files));
                    }
                    return Optional.<RepositorySnapshot>empty();
                }
            }
        });
    }

    /**
     * Find snapshots by tenant, workspace, and project scope.
     *
     * P1: Returns snapshots with source locator refs if available.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param projectId the project ID
     * @param limit maximum number of results
     * @return promise of snapshot list
     */
    public Promise<List<RepositorySnapshot>> findByScope(String tenantId, String workspaceId, String projectId, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");

        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT snapshot_id, provider, repo_id, commit_sha, materialized_root,
                       checksum, content_hash, created_at, tenant_id, workspace_id, project_id,
                       created_by, diagnostics_json, source_locator_json, inventory_metadata_json
                FROM repository_snapshots
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;

            List<RepositorySnapshot> snapshots = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
                statement.setInt(4, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String snapshotId = rs.getString("snapshot_id");
                        List<RepositorySnapshot.SnapshotFile> files = loadSnapshotFiles(connection, snapshotId, tenantId, workspaceId, projectId);
                        snapshots.add(mapSnapshot(rs, files));
                    }
                }
            }
            return snapshots;
        });
    }

    /**
     * P0: Removed unscoped findByRepoId method - all queries must be explicitly scoped.
     * Production code must use findByScope instead.
     */
    /**
     * Check if a snapshot exists with the given content hash within tenant/workspace/project scope.
     *
     * P0: Removed unscoped findByContentHash method - all queries must be explicitly scoped.
     * This prevents cross-tenant data leakage.
     *
     * @param contentHash the content hash to check
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param projectId the project ID
     * @return promise of optional matching snapshot ID
     */
    public Promise<Optional<String>> findByContentHash(String contentHash, String tenantId, String workspaceId, String projectId) {
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");

        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT snapshot_id
                FROM repository_snapshots
                WHERE content_hash = ? AND tenant_id = ? AND workspace_id = ? AND project_id = ?
                LIMIT 1
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, contentHash);
                statement.setString(2, tenantId);
                statement.setString(3, workspaceId);
                statement.setString(4, projectId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(rs.getString("snapshot_id"));
                    }
                    return Optional.<String>empty();
                }
            }
        });
    }

    /**
     * P0: Removed unscoped deleteOldSnapshots method - all deletions must be explicitly scoped.
     * Production code must use the scoped deleteOldSnapshots method.
     */
    /**
     * Delete old snapshots for a specific tenant/workspace/project scope.
     *
     * P0: Added scoped deletion - prevents cross-tenant data deletion.
     *
     * @param olderThan delete snapshots older than this instant
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param projectId the project ID
     * @return promise of count of deleted snapshots
     */
    public Promise<Integer> deleteOldSnapshots(Instant olderThan, String tenantId, String workspaceId, String projectId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");

        return Promise.ofBlocking(executor, () -> {
            // First delete files (cascade would handle this, but explicit is safer)
            String deleteFilesSql = """
                DELETE FROM repository_snapshot_files
                WHERE snapshot_id IN (
                    SELECT snapshot_id FROM repository_snapshots
                    WHERE created_at < ? AND tenant_id = ? AND workspace_id = ? AND project_id = ?
                )
                """;

            String deleteSnapshotsSql = """
                DELETE FROM repository_snapshots
                WHERE created_at < ? AND tenant_id = ? AND workspace_id = ? AND project_id = ?
                """;

            try (Connection connection = dataSource.getConnection()) {
                int deletedFiles;
                try (PreparedStatement statement = connection.prepareStatement(deleteFilesSql)) {
                    statement.setTimestamp(1, Timestamp.from(olderThan));
                    statement.setString(2, tenantId);
                    statement.setString(3, workspaceId);
                    statement.setString(4, projectId);
                    deletedFiles = statement.executeUpdate();
                }

                int deletedSnapshots;
                try (PreparedStatement statement = connection.prepareStatement(deleteSnapshotsSql)) {
                    statement.setTimestamp(1, Timestamp.from(olderThan));
                    statement.setString(2, tenantId);
                    statement.setString(3, workspaceId);
                    statement.setString(4, projectId);
                    deletedSnapshots = statement.executeUpdate();
                }

                log.info("Deleted {} old snapshots and {} associated files for scope {}/{}/{}",
                    deletedSnapshots, deletedFiles, tenantId, workspaceId, projectId);
                return deletedSnapshots;
            }
        });
    }

    /**
     * P1: Find source locator ref for a snapshot (scoped).
     *
     * P0: Added scope parameters to prevent cross-tenant data leakage.
     *
     * @param snapshotId the snapshot ID
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param projectId the project ID
     * @return promise of optional source locator
     */
    public Promise<Optional<SourceLocator>> findSourceLocator(String snapshotId, String tenantId, String workspaceId, String projectId) {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");

        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT source_locator_json
                FROM repository_snapshots
                WHERE snapshot_id = ? AND tenant_id = ? AND workspace_id = ? AND project_id = ?
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, snapshotId);
                statement.setString(2, tenantId);
                statement.setString(3, workspaceId);
                statement.setString(4, projectId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        String json = rs.getString("source_locator_json");
                        if (json != null && !json.isBlank()) {
                            return Optional.of(readSourceLocator(json));
                        }
                    }
                    return Optional.<SourceLocator>empty();
                }
            }
        });
    }

    private List<RepositorySnapshot.SnapshotFile> loadSnapshotFiles(Connection connection, String snapshotId, String tenantId, String workspaceId, String projectId) throws SQLException {
        String sql = """
            SELECT relative_path, absolute_path, size_bytes, last_modified_at, content_checksum, file_type
            FROM repository_snapshot_files
            WHERE snapshot_id = ?
            ORDER BY relative_path
            """;

        List<RepositorySnapshot.SnapshotFile> files = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, snapshotId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    files.add(new RepositorySnapshot.SnapshotFile(
                        rs.getString("relative_path"),
                        rs.getString("absolute_path"),
                        rs.getLong("size_bytes"),
                        rs.getTimestamp("last_modified_at").toInstant(),
                        rs.getString("content_checksum")
                    ));
                }
            }
        }
        return files;
    }

    private RepositorySnapshot mapSnapshot(ResultSet rs, List<RepositorySnapshot.SnapshotFile> files) throws SQLException {
        return RepositorySnapshot.builder()
            .snapshotId(rs.getString("snapshot_id"))
            .provider(rs.getString("provider"))
            .repoId(rs.getString("repo_id"))
            .commitSha(rs.getString("commit_sha"))
            .materializedRoot(rs.getString("materialized_root"))
            .checksum(rs.getString("checksum"))
            .contentHash(rs.getString("content_hash"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .tenantId(rs.getString("tenant_id"))
            .workspaceId(rs.getString("workspace_id"))
            .projectId(rs.getString("project_id"))
            .files(files)
            .diagnostics(readDiagnostics(rs.getString("diagnostics_json")))
            .build();
    }

    private String writeDiagnostics(List<RepositorySnapshot.SnapshotDiagnostic> diagnostics) throws JsonProcessingException {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "[]";
        }
        return objectMapper.writeValueAsString(diagnostics);
    }

    private List<RepositorySnapshot.SnapshotDiagnostic> readDiagnostics(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse diagnostics JSON", e);
            return List.of();
        }
    }

    /**
     * P1: Write source locator to JSON for persistence.
     */
    private String writeSourceLocator(SourceLocator sourceLocator) throws JsonProcessingException {
        if (sourceLocator == null) {
            return null;
        }
        java.util.Map<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("provider", sourceLocator.provider());
        payload.put("repoId", sourceLocator.repoId());
        sourceLocator.ref().ifPresent(ref -> payload.put("ref", ref));
        sourceLocator.path().ifPresent(path -> payload.put("path", path));
        sourceLocator.credentialRef().ifPresent(credentialRef -> payload.put("credentialRef", credentialRef));
        payload.put("tenantId", sourceLocator.tenantId());
        payload.put("workspaceId", sourceLocator.workspaceId());
        payload.put("projectId", sourceLocator.projectId());
        return objectMapper.writeValueAsString(payload);
    }

    /**
     * P1: Read source locator from JSON.
     */
    private SourceLocator readSourceLocator(String json) {
        try {
            Map<String, String> map = objectMapper.readValue(json, STRING_MAP);
            return SourceLocator.builder()
                .provider(map.get("provider"))
                .repoId(map.get("repoId"))
                .ref(map.get("ref"))
                .path(map.get("path"))
                .credentialRef(map.get("credentialRef"))
                .tenantId(map.get("tenantId"))
                .workspaceId(map.get("workspaceId"))
                .projectId(map.get("projectId"))
                .build();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse source locator JSON", e);
            throw new IllegalStateException("Invalid source locator JSON", e);
        }
    }

    /**
     * P1: Retrieve inventory metadata for a snapshot.
     *
     * @param snapshotId the snapshot ID
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param projectId the project ID
     * @return promise of optional inventory metadata
     */
    public Promise<Optional<Map<String, Object>>> findInventoryMetadata(String snapshotId, String tenantId, String workspaceId, String projectId) {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");

        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT inventory_metadata_json
                FROM repository_snapshots
                WHERE snapshot_id = ? AND tenant_id = ? AND workspace_id = ? AND project_id = ?
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, snapshotId);
                statement.setString(2, tenantId);
                statement.setString(3, workspaceId);
                statement.setString(4, projectId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        String json = rs.getString("inventory_metadata_json");
                        if (json != null && !json.isBlank()) {
                            return Optional.of(readStringMap(json));
                        }
                    }
                    return Optional.<Map<String, Object>>empty();
                }
            }
        });
    }

    private String writeInventoryMetadata(Map<String, Object> metadata) throws JsonProcessingException {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(metadata);
    }

    private Map<String, Object> readStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse string map JSON", e);
            return Map.of();
        }
    }

    private static String inferFileType(String relativePath) {
        String lower = relativePath.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "typescript";
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return "javascript";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".go")) return "go";
        if (lower.endsWith(".rs")) return "rust";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "yaml";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".scss") || lower.endsWith(".sass")) return "scss";
        if (lower.endsWith(".sql")) return "sql";
        if (lower.endsWith(".proto")) return "protobuf";
        return "unknown";
    }
}

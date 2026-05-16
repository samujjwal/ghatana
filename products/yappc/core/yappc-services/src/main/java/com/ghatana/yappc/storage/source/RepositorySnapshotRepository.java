package com.ghatana.yappc.storage.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.domain.source.RepositorySnapshot.DiagnosticLevel;
import com.ghatana.yappc.domain.source.RepositorySnapshot.SnapshotDiagnostic;
import com.ghatana.yappc.domain.source.RepositorySnapshot.SnapshotFile;
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

/**
 * @doc.type class
 * @doc.purpose JDBC persistence for repository snapshots with tenant/workspace/project scope.
 * @doc.layer infrastructure
 * @doc.pattern Repository
 *
 * P0: Durable repository snapshot persistence with full scope isolation.
 */
public class RepositorySnapshotRepository {

    private static final Logger log = LoggerFactory.getLogger(RepositorySnapshotRepository.class);
    private static final TypeReference<List<Map<String, Object>>> DIAGNOSTICS_TYPE = new TypeReference<>() {};

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public RepositorySnapshotRepository(DataSource dataSource, ObjectMapper objectMapper, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Save (insert or update) a repository snapshot.
     */
    public Promise<Void> save(RepositorySnapshot snapshot) {
        return Promise.ofBlocking(executor, () -> saveBlocking(snapshot)).map(v -> null);
    }

    /**
     * Synchronous save for use within blocking contexts.
     */
    public void saveBlocking(RepositorySnapshot snapshot) {
        String sql = """
            INSERT INTO repository_snapshots (
                snapshot_id, tenant_id, workspace_id, project_id,
                provider, repo_id, commit_sha, content_hash,
                materialized_root, checksum, file_count, diagnostics_json, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, snapshot_id) DO UPDATE SET
                commit_sha = EXCLUDED.commit_sha,
                content_hash = EXCLUDED.content_hash,
                materialized_root = EXCLUDED.materialized_root,
                checksum = EXCLUDED.checksum,
                file_count = EXCLUDED.file_count,
                diagnostics_json = EXCLUDED.diagnostics_json
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, snapshot.snapshotId());
            stmt.setString(2, snapshot.tenantId());
            stmt.setString(3, snapshot.workspaceId());
            stmt.setString(4, snapshot.projectId());
            stmt.setString(5, snapshot.provider());
            stmt.setString(6, snapshot.repoId());
            stmt.setString(7, snapshot.commitSha().orElse(null));
            stmt.setString(8, snapshot.contentHash().orElse(null));
            stmt.setString(9, snapshot.materializedRoot());
            stmt.setString(10, snapshot.checksum());
            stmt.setInt(11, snapshot.fileCount());
            stmt.setString(12, writeDiagnostics(snapshot.diagnostics()));
            stmt.setTimestamp(13, Timestamp.from(snapshot.createdAt()));

            stmt.executeUpdate();
            log.debug("Saved repository snapshot {} for tenant {}", snapshot.snapshotId(), snapshot.tenantId());

            // Save snapshot files
            saveSnapshotFiles(snapshot.snapshotId(), snapshot.files(), connection);

        } catch (SQLException e) {
            log.error("Failed to save repository snapshot {}", snapshot.snapshotId(), e);
            throw new RuntimeException("Failed to save repository snapshot", e);
        }
    }

    private void saveSnapshotFiles(String snapshotId, List<SnapshotFile> files, Connection connection) throws SQLException {
        String sql = """
            INSERT INTO repository_snapshot_files (
                snapshot_id, relative_path, absolute_path, size_bytes, last_modified, content_checksum
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (snapshot_id, relative_path) DO UPDATE SET
                absolute_path = EXCLUDED.absolute_path,
                size_bytes = EXCLUDED.size_bytes,
                last_modified = EXCLUDED.last_modified,
                content_checksum = EXCLUDED.content_checksum
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (SnapshotFile file : files) {
                stmt.setString(1, snapshotId);
                stmt.setString(2, file.relativePath());
                stmt.setString(3, file.absolutePath());
                stmt.setLong(4, file.sizeBytes());
                stmt.setTimestamp(5, Timestamp.from(file.lastModified()));
                stmt.setString(6, file.contentChecksum());
                stmt.addBatch();
            }
            stmt.executeBatch();
            log.debug("Saved {} files for snapshot {}", files.size(), snapshotId);
        }
    }

    /**
     * Find a snapshot by ID with tenant scope validation.
     */
    public Promise<RepositorySnapshot> findById(String snapshotId, String tenantId) {
        return Promise.ofBlocking(executor, () -> findByIdBlocking(snapshotId, tenantId));
    }

    /**
     * Synchronous find by ID for blocking contexts.
     */
    public RepositorySnapshot findByIdBlocking(String snapshotId, String tenantId) {
        String sql = """
            SELECT snapshot_id, tenant_id, workspace_id, project_id,
                   provider, repo_id, commit_sha, content_hash,
                   materialized_root, checksum, file_count, diagnostics_json, created_at
            FROM repository_snapshots
            WHERE snapshot_id = ? AND tenant_id = ?
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, snapshotId);
            stmt.setString(2, tenantId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs, connection);
                }
                return null;
            }

        } catch (SQLException e) {
            log.error("Failed to find repository snapshot {} for tenant {}", snapshotId, tenantId, e);
            throw new RuntimeException("Failed to find repository snapshot", e);
        }
    }

    /**
     * List snapshots by scope.
     */
    public Promise<List<RepositorySnapshot>> findByScope(String tenantId, String workspaceId, String projectId) {
        return Promise.ofBlocking(executor, () -> findByScopeBlocking(tenantId, workspaceId, projectId));
    }

    /**
     * Synchronous find by scope for blocking contexts.
     */
    public List<RepositorySnapshot> findByScopeBlocking(String tenantId, String workspaceId, String projectId) {
        StringBuilder sql = new StringBuilder("""
            SELECT snapshot_id, tenant_id, workspace_id, project_id,
                   provider, repo_id, commit_sha, content_hash,
                   materialized_root, checksum, file_count, diagnostics_json, created_at
            FROM repository_snapshots
            WHERE tenant_id = ?
            """);

        List<Object> params = new ArrayList<>();
        params.add(tenantId);

        if (workspaceId != null && !workspaceId.isBlank()) {
            sql.append(" AND workspace_id = ?");
            params.add(workspaceId);
        }
        if (projectId != null && !projectId.isBlank()) {
            sql.append(" AND project_id = ?");
            params.add(projectId);
        }

        sql.append(" ORDER BY created_at DESC");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                List<RepositorySnapshot> snapshots = new ArrayList<>();
                while (rs.next()) {
                    snapshots.add(mapRow(rs, connection));
                }
                return snapshots;
            }

        } catch (SQLException e) {
            log.error("Failed to list repository snapshots for tenant {}", tenantId, e);
            throw new RuntimeException("Failed to list repository snapshots", e);
        }
    }

    private RepositorySnapshot mapRow(ResultSet rs, Connection connection) throws SQLException {
        String snapshotId = rs.getString("snapshot_id");

        // Load files for this snapshot
        List<SnapshotFile> files = loadSnapshotFiles(snapshotId, connection);

        String diagnosticsJson = rs.getString("diagnostics_json");
        List<SnapshotDiagnostic> diagnostics = readDiagnostics(diagnosticsJson);

        return RepositorySnapshot.builder()
                .snapshotId(snapshotId)
                .tenantId(rs.getString("tenant_id"))
                .workspaceId(rs.getString("workspace_id"))
                .projectId(rs.getString("project_id"))
                .provider(rs.getString("provider"))
                .repoId(rs.getString("repo_id"))
                .commitSha(rs.getString("commit_sha"))
                .contentHash(rs.getString("content_hash"))
                .materializedRoot(rs.getString("materialized_root"))
                .files(files)
                .checksum(rs.getString("checksum"))
                .diagnostics(diagnostics)
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .build();
    }

    private List<SnapshotFile> loadSnapshotFiles(String snapshotId, Connection connection) throws SQLException {
        String sql = """
            SELECT relative_path, absolute_path, size_bytes, last_modified, content_checksum
            FROM repository_snapshot_files
            WHERE snapshot_id = ?
            ORDER BY relative_path
            """;

        List<SnapshotFile> files = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, snapshotId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    files.add(new SnapshotFile(
                            rs.getString("relative_path"),
                            rs.getString("absolute_path"),
                            rs.getLong("size_bytes"),
                            rs.getTimestamp("last_modified").toInstant(),
                            rs.getString("content_checksum")
                    ));
                }
            }
        }
        return files;
    }

    private String writeDiagnostics(List<SnapshotDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "[]";
        }
        try {
            List<Map<String, Object>> jsonList = diagnostics.stream()
                    .map(d -> Map.<String, Object>of(
                            "level", d.level().name(),
                            "code", d.code(),
                            "message", d.message(),
                            "resourcePath", d.resourcePath(),
                            "timestamp", d.timestamp().toString()
                    ))
                    .toList();
            return objectMapper.writeValueAsString(jsonList);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize diagnostics", e);
            return "[]";
        }
    }

    private List<SnapshotDiagnostic> readDiagnostics(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> jsonList = objectMapper.readValue(json, DIAGNOSTICS_TYPE);
            return jsonList.stream()
                    .map(d -> new SnapshotDiagnostic(
                            DiagnosticLevel.valueOf((String) d.get("level")),
                            (String) d.get("code"),
                            (String) d.get("message"),
                            (String) d.get("resourcePath"),
                            Instant.parse((String) d.get("timestamp"))
                    ))
                    .toList();
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize diagnostics", e);
            return List.of();
        }
    }
}

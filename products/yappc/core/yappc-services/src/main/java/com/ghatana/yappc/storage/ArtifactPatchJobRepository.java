package com.ghatana.yappc.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.services.patch.ArtifactPatchJobService;
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
 * @doc.purpose JDBC persistence for durable artifact patch jobs.
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public final class ArtifactPatchJobRepository {

    private static final Logger log = LoggerFactory.getLogger(ArtifactPatchJobRepository.class);
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public ArtifactPatchJobRepository(DataSource dataSource, ObjectMapper objectMapper, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public Promise<ArtifactPatchJobService.PatchJob> save(ArtifactPatchJobService.PatchJob job) {
        Objects.requireNonNull(job, "job must not be null");
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO patch_jobs (
                    job_id, tenant_id, workspace_id, project_id, plan_id, snapshot_id,
                    status, progress_percent, status_message, created_at, updated_at,
                    completed_at, patch_set_id, metadata_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (job_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    progress_percent = EXCLUDED.progress_percent,
                    status_message = EXCLUDED.status_message,
                    updated_at = EXCLUDED.updated_at,
                    completed_at = EXCLUDED.completed_at,
                    patch_set_id = EXCLUDED.patch_set_id,
                    metadata_json = EXCLUDED.metadata_json
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                bindJob(statement, job);
                statement.executeUpdate();
            }
            return job;
        });
    }

    public Promise<Optional<ArtifactPatchJobService.PatchJob>> findById(String jobId) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT job_id, tenant_id, workspace_id, project_id, plan_id, snapshot_id,
                       status, progress_percent, status_message, created_at, updated_at,
                       completed_at, patch_set_id, metadata_json
                FROM patch_jobs
                WHERE job_id = ?
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, jobId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapJob(rs));
                    }
                    return Optional.<ArtifactPatchJobService.PatchJob>empty();
                }
            }
        });
    }

    public Promise<List<ArtifactPatchJobService.PatchJob>> listByScope(
        String tenantId,
        String workspaceId,
        String projectId,
        int limit
    ) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");

        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT job_id, tenant_id, workspace_id, project_id, plan_id, snapshot_id,
                       status, progress_percent, status_message, created_at, updated_at,
                       completed_at, patch_set_id, metadata_json
                FROM patch_jobs
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;

            List<ArtifactPatchJobService.PatchJob> jobs = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
                statement.setInt(4, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        jobs.add(mapJob(rs));
                    }
                }
            }
            return jobs;
        });
    }

    private void bindJob(PreparedStatement statement, ArtifactPatchJobService.PatchJob job) throws SQLException, JsonProcessingException {
        statement.setString(1, job.jobId());
        statement.setString(2, job.tenantId());
        statement.setString(3, job.workspaceId());
        statement.setString(4, job.projectId());
        statement.setString(5, job.planId());
        statement.setString(6, job.snapshotId());
        statement.setString(7, job.status().name());
        statement.setInt(8, job.progressPercent());
        statement.setString(9, job.statusMessage());
        statement.setTimestamp(10, Timestamp.from(job.createdAt()));
        statement.setTimestamp(11, Timestamp.from(job.updatedAt() != null ? job.updatedAt() : job.createdAt()));
        statement.setTimestamp(12, job.completedAt() != null ? Timestamp.from(job.completedAt()) : null);
        statement.setString(13, job.patchSetId());
        statement.setString(14, objectMapper.writeValueAsString(job.metadata() == null ? Map.of() : job.metadata()));
    }

    private ArtifactPatchJobService.PatchJob mapJob(ResultSet rs) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Timestamp completedAt = rs.getTimestamp("completed_at");
        return new ArtifactPatchJobService.PatchJob(
            rs.getString("job_id"),
            rs.getString("tenant_id"),
            rs.getString("workspace_id"),
            rs.getString("project_id"),
            rs.getString("plan_id"),
            rs.getString("snapshot_id"),
            ArtifactPatchJobService.PatchJobStatus.valueOf(rs.getString("status")),
            rs.getInt("progress_percent"),
            rs.getString("status_message"),
            rs.getTimestamp("created_at").toInstant(),
            updatedAt != null ? updatedAt.toInstant() : null,
            completedAt != null ? completedAt.toInstant() : null,
            rs.getString("patch_set_id"),
            readObjectMap(rs.getString("metadata_json"))
        );
    }

    private Map<String, Object> readObjectMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse patch job metadata JSON", e);
            return Map.of();
        }
    }
}

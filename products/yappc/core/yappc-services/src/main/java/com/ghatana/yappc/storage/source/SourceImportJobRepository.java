package com.ghatana.yappc.storage.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.source.SourceLocator;
import com.ghatana.yappc.services.source.SourceImportService.SourceImportJob;
import com.ghatana.yappc.services.source.SourceImportService.JobStatus;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * @doc.type class
 * @doc.purpose JDBC persistence for source import jobs with tenant/workspace/project scope.
 * @doc.layer infrastructure
 * @doc.pattern Repository
 *
 * P0: Durable source import job persistence with full scope isolation.
 */
public class SourceImportJobRepository {

    private static final Logger log = LoggerFactory.getLogger(SourceImportJobRepository.class);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public SourceImportJobRepository(DataSource dataSource, ObjectMapper objectMapper, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Save (insert or update) a source import job.
     */
    public Promise<Void> save(SourceImportJob job) {
        return Promise.ofBlocking(executor, () -> saveBlocking(job)).map(v -> null);
    }

    /**
     * Synchronous save for use within blocking contexts.
     */
    public void saveBlocking(SourceImportJob job) {
        String sql = """
            INSERT INTO source_import_jobs (
                job_id, tenant_id, workspace_id, project_id,
                provider, repo_id, ref, path, credential_ref,
                status, progress_percent, current_step, error_message, snapshot_id,
                created_at, updated_at, completed_at, locator_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, job_id) DO UPDATE SET
                status = EXCLUDED.status,
                progress_percent = EXCLUDED.progress_percent,
                current_step = EXCLUDED.current_step,
                error_message = EXCLUDED.error_message,
                snapshot_id = EXCLUDED.snapshot_id,
                updated_at = EXCLUDED.updated_at,
                completed_at = EXCLUDED.completed_at
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, job.jobId());
            stmt.setString(2, job.tenantId());
            stmt.setString(3, job.workspaceId());
            stmt.setString(4, job.projectId());
            stmt.setString(5, job.locator().provider());
            stmt.setString(6, job.locator().repoId());
            stmt.setString(7, job.locator().ref().orElse(null));
            stmt.setString(8, job.locator().path().orElse(null));
            stmt.setString(9, job.locator().credentialRef().orElse(null));
            stmt.setString(10, job.status().name());
            stmt.setInt(11, job.progressPercent());
            stmt.setString(12, job.currentStep());
            stmt.setString(13, job.errorMessage());
            stmt.setString(14, job.snapshotId());
            stmt.setTimestamp(15, Timestamp.from(job.createdAt()));
            stmt.setTimestamp(16, Timestamp.from(job.updatedAt()));
            stmt.setTimestamp(17, job.completedAt() != null ? Timestamp.from(job.completedAt()) : null);
            stmt.setString(18, writeLocatorJson(job.locator()));

            stmt.executeUpdate();
            log.debug("Saved source import job {}", job.jobId());

        } catch (SQLException e) {
            log.error("Failed to save source import job {}", job.jobId(), e);
            throw new RuntimeException("Failed to save source import job", e);
        }
    }

    /**
     * Find a job by ID with tenant scope validation.
     */
    public Promise<Optional<SourceImportJob>> findById(String jobId, String tenantId) {
        return Promise.ofBlocking(executor, () -> findByIdBlocking(jobId, tenantId));
    }

    /**
     * Synchronous find by ID for blocking contexts.
     */
    public Optional<SourceImportJob> findByIdBlocking(String jobId, String tenantId) {
        String sql = """
            SELECT job_id, tenant_id, workspace_id, project_id,
                   provider, repo_id, ref, path, credential_ref,
                   status, progress_percent, current_step, error_message, snapshot_id,
                   created_at, updated_at, completed_at, locator_json
            FROM source_import_jobs
            WHERE job_id = ? AND tenant_id = ?
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, jobId);
            stmt.setString(2, tenantId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            log.error("Failed to find source import job {} for tenant {}", jobId, tenantId, e);
            throw new RuntimeException("Failed to find source import job", e);
        }
    }

    /**
     * List jobs by scope with optional filtering.
     */
    public Promise<List<SourceImportJob>> findByScope(String tenantId, String workspaceId, String projectId) {
        return Promise.ofBlocking(executor, () -> findByScopeBlocking(tenantId, workspaceId, projectId));
    }

    /**
     * Synchronous find by scope for blocking contexts.
     */
    public List<SourceImportJob> findByScopeBlocking(String tenantId, String workspaceId, String projectId) {
        StringBuilder sql = new StringBuilder("""
            SELECT job_id, tenant_id, workspace_id, project_id,
                   provider, repo_id, ref, path, credential_ref,
                   status, progress_percent, current_step, error_message, snapshot_id,
                   created_at, updated_at, completed_at, locator_json
            FROM source_import_jobs
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
                List<SourceImportJob> jobs = new ArrayList<>();
                while (rs.next()) {
                    jobs.add(mapRow(rs));
                }
                return jobs;
            }

        } catch (SQLException e) {
            log.error("Failed to list source import jobs for tenant {}", tenantId, e);
            throw new RuntimeException("Failed to list source import jobs", e);
        }
    }

    private SourceImportJob mapRow(ResultSet rs) throws SQLException {
        String locatorJson = rs.getString("locator_json");
        SourceLocator locator = readLocatorJson(locatorJson);

        Instant completedAt = rs.getTimestamp("completed_at") != null
                ? rs.getTimestamp("completed_at").toInstant()
                : null;

        return new SourceImportJob(
                rs.getString("job_id"),
                rs.getString("tenant_id"),
                rs.getString("workspace_id"),
                rs.getString("project_id"),
                locator,
                JobStatus.valueOf(rs.getString("status")),
                rs.getInt("progress_percent"),
                rs.getString("current_step"),
                rs.getString("error_message"),
                rs.getString("snapshot_id"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                completedAt
        );
    }

    private String writeLocatorJson(SourceLocator locator) {
        try {
            return objectMapper.writeValueAsString(new LocatorJson(
                    locator.provider(),
                    locator.repoId(),
                    locator.ref().orElse(null),
                    locator.path().orElse(null),
                    locator.credentialRef().orElse(null),
                    locator.tenantId(),
                    locator.workspaceId(),
                    locator.projectId()
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize locator", e);
            throw new RuntimeException("Failed to serialize locator", e);
        }
    }

    private SourceLocator readLocatorJson(String json) {
        try {
            LocatorJson locatorJson = objectMapper.readValue(json, LocatorJson.class);
            return SourceLocator.builder()
                    .provider(locatorJson.provider())
                    .repoId(locatorJson.repoId())
                    .ref(locatorJson.ref())
                    .path(locatorJson.path())
                    .credentialRef(locatorJson.credentialRef())
                    .tenantId(locatorJson.tenantId())
                    .workspaceId(locatorJson.workspaceId())
                    .projectId(locatorJson.projectId())
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize locator", e);
            throw new RuntimeException("Failed to deserialize locator", e);
        }
    }

    private record LocatorJson(
            String provider,
            String repoId,
            String ref,
            String path,
            String credentialRef,
            String tenantId,
            String workspaceId,
            String projectId
    ) {}
}

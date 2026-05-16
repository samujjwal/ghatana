package com.ghatana.yappc.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.SourceImportJob;
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
 * @doc.purpose JDBC persistence for source import jobs with audit trail, progress tracking, and cancellation support
 * @doc.layer infrastructure
 * @doc.pattern Repository
 * 
 * P2.6: Durable job tables replacing in-memory job storage.
 * Supports async scan jobs with progress updates, audit logging, cancellation, and resume capability.
 */
public final class SourceImportJobRepository {

    private static final Logger log = LoggerFactory.getLogger(SourceImportJobRepository.class);
    private static final TypeReference<List<SourceImportJob.ValidationResult>> VALIDATION_LIST = new TypeReference<>() { };
    private static final TypeReference<List<SourceImportJob.DecompilationResult>> DECOMPILATION_LIST = new TypeReference<>() { };
    private static final TypeReference<List<SourceImportJob.MappingResult>> MAPPING_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public SourceImportJobRepository(DataSource dataSource) {
        this(dataSource, new ObjectMapper(), Runnable::run);
    }

    public SourceImportJobRepository(DataSource dataSource, ObjectMapper objectMapper, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Save or update a source import job.
     * P2.6: Durable persistence with ON CONFLICT DO UPDATE for idempotent upsert.
     */
    public Promise<SourceImportJob> saveJob(SourceImportJob job) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO source_import_jobs (
                    job_id, project_id, workspace_id, tenant_id, source_url, source_type,
                    status, current_step, total_steps, percentage, current_phase,
                    validation_results_json, decompilation_results_json, mapping_results_json,
                    residual_review_status, submitted_at, started_at, completed_at,
                    submitted_by, error_message, metadata_json, is_cancelled, cancellation_requested_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (job_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    current_step = EXCLUDED.current_step,
                    total_steps = EXCLUDED.total_steps,
                    percentage = EXCLUDED.percentage,
                    current_phase = EXCLUDED.current_phase,
                    validation_results_json = EXCLUDED.validation_results_json,
                    decompilation_results_json = EXCLUDED.decompilation_results_json,
                    mapping_results_json = EXCLUDED.mapping_results_json,
                    residual_review_status = EXCLUDED.residual_review_status,
                    started_at = EXCLUDED.started_at,
                    completed_at = EXCLUDED.completed_at,
                    error_message = EXCLUDED.error_message,
                    metadata_json = EXCLUDED.metadata_json,
                    is_cancelled = EXCLUDED.is_cancelled,
                    cancellation_requested_at = EXCLUDED.cancellation_requested_at
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, job.jobId());
                statement.setString(2, job.projectId());
                statement.setString(3, job.workspaceId());
                statement.setString(4, job.tenantId());
                statement.setString(5, job.sourceUrl());
                statement.setString(6, job.sourceType());
                statement.setString(7, job.status().name());
                statement.setInt(8, job.progress().currentStep());
                statement.setInt(9, job.progress().totalSteps());
                statement.setDouble(10, job.progress().percentage());
                statement.setString(11, job.progress().currentPhase());
                statement.setString(12, writeJson(job.validationResults(), VALIDATION_LIST));
                statement.setString(13, writeJson(job.decompilationResults(), DECOMPILATION_LIST));
                statement.setString(14, writeJson(job.mappingResults(), MAPPING_LIST));
                statement.setString(15, job.residualReviewStatus());
                statement.setTimestamp(16, Timestamp.from(job.submittedAt()));
                statement.setTimestamp(17, job.startedAt() != null ? Timestamp.from(job.startedAt()) : null);
                statement.setTimestamp(18, job.completedAt() != null ? Timestamp.from(job.completedAt()) : null);
                statement.setString(19, job.submittedBy());
                statement.setString(20, job.error());
                statement.setString(21, writeJson(job.metadata(), STRING_MAP));
                statement.setBoolean(22, false); // is_cancelled - will be updated separately
                statement.setTimestamp(23, null); // cancellation_requested_at - will be updated separately
                statement.executeUpdate();
            }
            return job;
        }).whenResult(j -> log.debug("Saved source import job {} with status {}", j.jobId(), j.status()));
    }

    /**
     * Update job progress incrementally.
     * P2.6: Support for progress tracking during async execution.
     */
    public Promise<Void> updateProgress(String jobId, int currentStep, int totalSteps, double percentage, String currentPhase) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                UPDATE source_import_jobs
                SET current_step = ?, total_steps = ?, percentage = ?, current_phase = ?
                WHERE job_id = ? AND is_cancelled = false
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, currentStep);
                statement.setInt(2, totalSteps);
                statement.setDouble(3, percentage);
                statement.setString(4, currentPhase);
                statement.setString(5, jobId);
                int updated = statement.executeUpdate();
                if (updated == 0) {
                    log.warn("No job found or job cancelled for progress update: {}", jobId);
                }
            }
            return null;
        });
    }

    /**
     * Update job status.
     * P2.6: Support for status transitions during async execution.
     */
    public Promise<Void> updateStatus(String jobId, SourceImportJob.JobStatus status) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                UPDATE source_import_jobs
                SET status = ?
                WHERE job_id = ? AND is_cancelled = false
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, status.name());
                statement.setString(2, jobId);
                int updated = statement.executeUpdate();
                if (updated == 0) {
                    log.warn("No job found or job cancelled for status update: {}", jobId);
                }
            }
            return null;
        });
    }

    /**
     * Mark job as cancelled.
     * P2.6: Support for job cancellation.
     */
    public Promise<Boolean> cancelJob(String jobId, String cancelledBy) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                UPDATE source_import_jobs
                SET is_cancelled = true, cancellation_requested_at = ?, status = 'FAILED', error_message = 'Job cancelled by ' || ?
                WHERE job_id = ? AND status IN ('SUBMITTED', 'VALIDATING', 'DECOMPILING', 'MAPPING')
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(Instant.now()));
                statement.setString(2, cancelledBy);
                statement.setString(3, jobId);
                int updated = statement.executeUpdate();
                log.info("Cancellation requested for job {} by {} - {} rows affected", jobId, cancelledBy, updated);
                return updated > 0;
            }
        });
    }

    /**
     * Find job by ID.
     */
    public Promise<SourceImportJob> findJobById(String jobId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT job_id, project_id, workspace_id, tenant_id, source_url, source_type,
                       status, current_step, total_steps, percentage, current_phase,
                       validation_results_json, decompilation_results_json, mapping_results_json,
                       residual_review_status, submitted_at, started_at, completed_at,
                       submitted_by, error_message, metadata_json, is_cancelled, cancellation_requested_at
                FROM source_import_jobs
                WHERE job_id = ?
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, jobId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return mapJob(resultSet);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Find jobs by tenant and project.
     */
    public Promise<List<SourceImportJob>> findJobsByTenantAndProduct(String tenantId, String projectId, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT job_id, project_id, workspace_id, tenant_id, source_url, source_type,
                       status, current_step, total_steps, percentage, current_phase,
                       validation_results_json, decompilation_results_json, mapping_results_json,
                       residual_review_status, submitted_at, started_at, completed_at,
                       submitted_by, error_message, metadata_json, is_cancelled, cancellation_requested_at
                FROM source_import_jobs
                WHERE tenant_id = ? AND project_id = ?
                ORDER BY submitted_at DESC
                LIMIT ?
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, projectId);
                statement.setInt(3, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<SourceImportJob> jobs = new ArrayList<>();
                    while (resultSet.next()) {
                        jobs.add(mapJob(resultSet));
                    }
                    return jobs;
                }
            }
        });
    }

    /**
     * Find jobs by status for a tenant.
     * P2.6: Support for querying active jobs for monitoring.
     */
    public Promise<List<SourceImportJob>> findJobsByStatus(String tenantId, SourceImportJob.JobStatus status, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT job_id, project_id, workspace_id, tenant_id, source_url, source_type,
                       status, current_step, total_steps, percentage, current_phase,
                       validation_results_json, decompilation_results_json, mapping_results_json,
                       residual_review_status, submitted_at, started_at, completed_at,
                       submitted_by, error_message, metadata_json, is_cancelled, cancellation_requested_at
                FROM source_import_jobs
                WHERE tenant_id = ? AND status = ? AND is_cancelled = false
                ORDER BY submitted_at DESC
                LIMIT ?
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, status.name());
                statement.setInt(3, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<SourceImportJob> jobs = new ArrayList<>();
                    while (resultSet.next()) {
                        jobs.add(mapJob(resultSet));
                    }
                    return jobs;
                }
            }
        });
    }

    /**
     * Delete old completed jobs for cleanup.
     * P2.6: Support for job retention policy.
     */
    public Promise<Integer> deleteOldJobs(Instant olderThan) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                DELETE FROM source_import_jobs
                WHERE completed_at < ? AND status IN ('COMPLETED', 'FAILED')
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(olderThan));
                int deleted = statement.executeUpdate();
                log.info("Deleted {} old source import jobs older than {}", deleted, olderThan);
                return deleted;
            }
        });
    }

    private SourceImportJob mapJob(ResultSet resultSet) throws SQLException {
        SourceImportJob.JobStatus status = SourceImportJob.JobStatus.valueOf(resultSet.getString("status"));
        SourceImportJob.JobProgress progress = new SourceImportJob.JobProgress(
                resultSet.getInt("current_step"),
                resultSet.getInt("total_steps"),
                resultSet.getDouble("percentage"),
                resultSet.getString("current_phase")
        );

        return new SourceImportJob(
                resultSet.getString("job_id"),
                resultSet.getString("project_id"),
                resultSet.getString("workspace_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("source_url"),
                resultSet.getString("source_type"),
                status,
                progress,
                readJson(resultSet.getString("validation_results_json"), VALIDATION_LIST, List.of()),
                readJson(resultSet.getString("decompilation_results_json"), DECOMPILATION_LIST, List.of()),
                readJson(resultSet.getString("mapping_results_json"), MAPPING_LIST, List.of()),
                resultSet.getString("residual_review_status"),
                resultSet.getTimestamp("submitted_at").toInstant(),
                resultSet.getTimestamp("started_at") != null ? resultSet.getTimestamp("started_at").toInstant() : null,
                resultSet.getTimestamp("completed_at") != null ? resultSet.getTimestamp("completed_at").toInstant() : null,
                resultSet.getString("submitted_by"),
                resultSet.getString("error_message"),
                readJson(resultSet.getString("metadata_json"), STRING_MAP, Map.of())
        );
    }

    private String writeJson(Object value, TypeReference<?> typeReference) {
        try {
            return objectMapper.writerFor(typeReference).writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize job data", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> typeReference, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readerFor(typeReference).readValue(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize job data", exception);
        }
    }
}

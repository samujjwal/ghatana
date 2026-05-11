/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC-backed implementation of GenerationRunRepository.
 *
 * <p>Provides durable, transactional persistence of generation runs with provenance.
 * All operations are dispatched on a virtual thread executor to avoid blocking
 * the ActiveJ event loop.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed durable generation run repository with provenance
 * @doc.layer api
 * @doc.pattern Repository
 */
public final class JdbcGenerationRunRepository implements GenerationRunRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcGenerationRunRepository.class);
    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static final String INSERT_SQL = """
            INSERT INTO generation_runs
              (id, plan_id, project_id, tenant_id, workspace_id, intent, status,
               artifact_ids, review_status, preview_session_id, created_at, completed_at,
               provenance, metadata)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
            ON CONFLICT (id) DO UPDATE SET
              status = EXCLUDED.status,
              artifact_ids = EXCLUDED.artifact_ids,
              review_status = EXCLUDED.review_status,
              preview_session_id = EXCLUDED.preview_session_id,
              completed_at = EXCLUDED.completed_at,
              provenance = EXCLUDED.provenance,
              metadata = EXCLUDED.metadata
            """;

    private static final String SELECT_BY_ID_SQL = """
            SELECT id, plan_id, project_id, tenant_id, workspace_id, intent, status,
                   artifact_ids, review_status, preview_session_id, created_at, completed_at,
                   provenance, metadata
            FROM generation_runs
            WHERE id = ?
            """;

    private static final String SELECT_BY_PROJECT_SQL = """
            SELECT id, plan_id, project_id, tenant_id, workspace_id, intent, status,
                   artifact_ids, review_status, preview_session_id, created_at, completed_at,
                   provenance, metadata
            FROM generation_runs
            WHERE project_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;

    private static final String SELECT_BY_PLAN_SQL = """
            SELECT id, plan_id, project_id, tenant_id, workspace_id, intent, status,
                   artifact_ids, review_status, preview_session_id, created_at, completed_at,
                   provenance, metadata
            FROM generation_runs
            WHERE plan_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;

    private static final String UPDATE_REVIEW_STATUS_SQL = """
            UPDATE generation_runs
            SET review_status = ?
            WHERE id = ?
            """;

    private static final String UPDATE_STATUS_SQL = """
            UPDATE generation_runs
            SET status = ?
            WHERE id = ?
            """;

    private static final String UPDATE_PREVIEW_SESSION_SQL = """
            UPDATE generation_runs
            SET preview_session_id = ?
            WHERE id = ?
            """;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcGenerationRunRepository(@NotNull DataSource dataSource, @NotNull ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public Promise<Void> save(@NotNull GenerationRun run) {
        return Promise.ofBlocking(EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

                ps.setString(1, run.id());
                ps.setString(2, run.planId());
                ps.setString(3, run.projectId());
                ps.setString(4, run.tenantId());
                ps.setString(5, run.workspaceId());
                ps.setString(6, toJson(run.intent()));
                ps.setString(7, run.status().name());
                ps.setString(8, toJson(run.artifactIds()));
                ps.setString(9, run.reviewStatus().name());
                ps.setString(10, run.previewSessionId());
                ps.setTimestamp(11, Timestamp.from(run.createdAt()));
                ps.setTimestamp(12, run.completedAt() != null ? Timestamp.from(run.completedAt()) : null);
                ps.setString(13, toJson(run.provenance()));
                ps.setString(14, toJson(run.metadata()));

                ps.executeUpdate();
                log.debug("Persisted generation run: id={}, projectId={}, tenantId={}",
                    run.id(), run.projectId(), run.tenantId());
            } catch (SQLException e) {
                log.error("Failed to persist generation run: id={}, projectId={}, tenantId={}",
                    run.id(), run.projectId(), run.tenantId(), e);
                throw new RuntimeException("Failed to persist generation run", e);
            }
        });
    }

    @Override
    public Promise<GenerationRun> findById(@NotNull String id) {
        return Promise.ofBlocking(EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {

                ps.setString(1, id);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    return mapRowToGenerationRun(rs);
                }
                return null;
            } catch (SQLException e) {
                log.error("Failed to find generation run by id: id={}", id, e);
                throw new RuntimeException("Failed to find generation run", e);
            }
        });
    }

    @Override
    public Promise<List<GenerationRun>> findByProjectId(@NotNull String projectId) {
        return Promise.ofBlocking(EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_PROJECT_SQL)) {

                ps.setString(1, projectId);
                ps.setInt(2, 100); // Default limit

                ResultSet rs = ps.executeQuery();
                List<GenerationRun> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRowToGenerationRun(rs));
                }
                return results;
            } catch (SQLException e) {
                log.error("Failed to find generation runs by project: projectId={}", projectId, e);
                throw new RuntimeException("Failed to find generation runs", e);
            }
        });
    }

    @Override
    public Promise<List<GenerationRun>> findByPlanId(@NotNull String planId) {
        return Promise.ofBlocking(EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_PLAN_SQL)) {

                ps.setString(1, planId);
                ps.setInt(2, 100); // Default limit

                ResultSet rs = ps.executeQuery();
                List<GenerationRun> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRowToGenerationRun(rs));
                }
                return results;
            } catch (SQLException e) {
                log.error("Failed to find generation runs by plan: planId={}", planId, e);
                throw new RuntimeException("Failed to find generation runs", e);
            }
        });
    }

    @Override
    public Promise<Void> updateReviewStatus(@NotNull String id, @NotNull GenerationRun.ReviewStatus reviewStatus) {
        return Promise.ofBlocking(EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_REVIEW_STATUS_SQL)) {

                ps.setString(1, reviewStatus.name());
                ps.setString(2, id);

                ps.executeUpdate();
                log.debug("Updated generation run review status: id={}, reviewStatus={}", id, reviewStatus);
            } catch (SQLException e) {
                log.error("Failed to update generation run review status: id={}, reviewStatus={}", id, reviewStatus, e);
                throw new RuntimeException("Failed to update generation run review status", e);
            }
        });
    }

    @Override
    public Promise<Void> updateStatus(@NotNull String id, @NotNull GenerationRun.RunStatus status) {
        return Promise.ofBlocking(EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS_SQL)) {

                ps.setString(1, status.name());
                ps.setString(2, id);

                ps.executeUpdate();
                log.debug("Updated generation run status: id={}, status={}", id, status);
            } catch (SQLException e) {
                log.error("Failed to update generation run status: id={}, status={}", id, status, e);
                throw new RuntimeException("Failed to update generation run status", e);
            }
        });
    }

    @Override
    public Promise<Void> associatePreviewSession(@NotNull String id, @NotNull String previewSessionId) {
        return Promise.ofBlocking(EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_PREVIEW_SESSION_SQL)) {

                ps.setString(1, previewSessionId);
                ps.setString(2, id);

                ps.executeUpdate();
                log.debug("Associated preview session with generation run: id={}, previewSessionId={}", id, previewSessionId);
            } catch (SQLException e) {
                log.error("Failed to associate preview session: id={}, previewSessionId={}", id, previewSessionId, e);
                throw new RuntimeException("Failed to associate preview session", e);
            }
        });
    }

    private GenerationRun mapRowToGenerationRun(ResultSet rs) throws SQLException {
        try {
            return GenerationRun.builder()
                .id(rs.getString("id"))
                .planId(rs.getString("plan_id"))
                .projectId(rs.getString("project_id"))
                .tenantId(rs.getString("tenant_id"))
                .workspaceId(rs.getString("workspace_id"))
                .intent(fromJson(rs.getString("intent"), IntentInput.class))
                .status(GenerationRun.RunStatus.valueOf(rs.getString("status")))
                .artifactIds(fromJson(rs.getString("artifact_ids"), List.class))
                .reviewStatus(GenerationRun.ReviewStatus.valueOf(rs.getString("review_status")))
                .previewSessionId(rs.getString("preview_session_id"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .completedAt(rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null)
                .provenance(fromJson(rs.getString("provenance"), Map.class))
                .metadata(fromJson(rs.getString("metadata"), Map.class))
                .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize generation run from database", e);
            throw new RuntimeException("Failed to deserialize generation run", e);
        }
    }

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    private <T> T fromJson(String json, Class<T> clazz) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readValue(json, clazz);
    }
}

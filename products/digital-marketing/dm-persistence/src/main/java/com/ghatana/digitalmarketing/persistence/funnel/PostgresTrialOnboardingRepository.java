package com.ghatana.digitalmarketing.persistence.funnel;

import com.ghatana.digitalmarketing.application.funnel.TrialOnboardingRepository;
import com.ghatana.digitalmarketing.domain.funnel.TrialOnboarding;
import com.ghatana.digitalmarketing.domain.funnel.TrialOnboardingStatus;
import com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Production PostgreSQL adapter for {@link TrialOnboardingRepository}.
 *
 * <p>Wraps all blocking JDBC I/O in {@code Promise.ofBlocking()} to remain event-loop safe.
 * Uses upsert semantics for idempotent saves. Enforces tenant isolation at query level.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for trial onboarding persistence (P3-001)
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresTrialOnboardingRepository implements TrialOnboardingRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresTrialOnboardingRepository.class);

    private static final String UPSERT_SQL =
        "INSERT INTO dmos_trial_onboardings (id, tenant_id, workspace_id, lead_id, demo_workspace_id, status, current_step, total_steps, step_progress, created_at, started_at, completed_at, cancellation_reason) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE SET " +
        "  status = EXCLUDED.status, current_step = EXCLUDED.current_step, step_progress = EXCLUDED.step_progress, " +
        "  started_at = EXCLUDED.started_at, completed_at = EXCLUDED.completed_at, cancellation_reason = EXCLUDED.cancellation_reason";

    private static final String SELECT_BY_ID_SQL =
        "SELECT id, tenant_id, workspace_id, lead_id, demo_workspace_id, status, current_step, total_steps, step_progress, created_at, started_at, completed_at, cancellation_reason " +
        "FROM dmos_trial_onboardings WHERE id = ? AND tenant_id = ?";

    private static final String SELECT_BY_LEAD_ID_SQL =
        "SELECT id, tenant_id, workspace_id, lead_id, demo_workspace_id, status, current_step, total_steps, step_progress, created_at, started_at, completed_at, cancellation_reason " +
        "FROM dmos_trial_onboardings WHERE lead_id = ? AND tenant_id = ?";

    private static final String SELECT_BY_DEMO_WORKSPACE_ID_SQL =
        "SELECT id, tenant_id, workspace_id, lead_id, demo_workspace_id, status, current_step, total_steps, step_progress, created_at, started_at, completed_at, cancellation_reason " +
        "FROM dmos_trial_onboardings WHERE demo_workspace_id = ? AND tenant_id = ?";

    private static final String SELECT_BY_TENANT_SQL =
        "SELECT id, tenant_id, workspace_id, lead_id, demo_workspace_id, status, current_step, total_steps, step_progress, created_at, started_at, completed_at, cancellation_reason " +
        "FROM dmos_trial_onboardings WHERE tenant_id = ? ORDER BY created_at";

    private static final String DELETE_SQL =
        "DELETE FROM dmos_trial_onboardings WHERE id = ? AND tenant_id = ?";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresTrialOnboardingRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<TrialOnboarding> save(TrialOnboarding onboarding) {
        Objects.requireNonNull(onboarding, "onboarding must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, onboarding.getId());
                stmt.setString(2, onboarding.getTenantId());
                stmt.setString(3, onboarding.getWorkspaceId());
                stmt.setString(4, onboarding.getLeadId());
                stmt.setString(5, onboarding.getDemoWorkspaceId());
                stmt.setString(6, onboarding.getStatus().name());
                stmt.setInt(7, onboarding.getCurrentStep());
                stmt.setInt(8, onboarding.getTotalSteps());
                stmt.setString(9, onboarding.getStepProgress() != null ? onboarding.getStepProgress().toString() : null);
                stmt.setTimestamp(10, Timestamp.from(onboarding.getCreatedAt()));
                stmt.setTimestamp(11, onboarding.getStartedAt() != null ? Timestamp.from(onboarding.getStartedAt()) : null);
                stmt.setTimestamp(12, onboarding.getCompletedAt() != null ? Timestamp.from(onboarding.getCompletedAt()) : null);
                stmt.setString(13, onboarding.getCancellationReason());
                stmt.executeUpdate();
                LOG.info("[DMOS-PERSIST] trial onboarding upserted: id={} tenant={} status={}",
                    onboarding.getId(), onboarding.getTenantId(), onboarding.getStatus());
                return onboarding;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to save trial onboarding id={}: {}",
                    onboarding.getId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to save trial onboarding: " + onboarding.getId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<TrialOnboarding>> findById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                stmt.setString(1, id);
                stmt.setString(2, extractTenantId(id));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find trial onboarding id={}: {}", id, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find trial onboarding: " + id, e);
            }
        });
    }

    @Override
    public Promise<Optional<TrialOnboarding>> findByLeadId(String leadId) {
        Objects.requireNonNull(leadId, "leadId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_LEAD_ID_SQL)) {
                stmt.setString(1, leadId);
                stmt.setString(2, extractTenantId(leadId));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find trial onboarding by lead_id={}: {}", leadId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find trial onboarding by lead_id: " + leadId, e);
            }
        });
    }

    @Override
    public Promise<List<TrialOnboarding>> findByDemoWorkspaceId(String demoWorkspaceId) {
        Objects.requireNonNull(demoWorkspaceId, "demoWorkspaceId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_DEMO_WORKSPACE_ID_SQL)) {
                stmt.setString(1, demoWorkspaceId);
                stmt.setString(2, extractTenantId(demoWorkspaceId));
                try (ResultSet rs = stmt.executeQuery()) {
                    List<TrialOnboarding> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find trial onboardings by demo_workspace_id={}: {}", demoWorkspaceId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find trial onboardings by demo_workspace_id: " + demoWorkspaceId, e);
            }
        });
    }

    @Override
    public Promise<List<TrialOnboarding>> findByTenantId(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_TENANT_SQL)) {
                stmt.setString(1, tenantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<TrialOnboarding> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to list trial onboardings for tenant={}: {}", tenantId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to list trial onboardings for tenant: " + tenantId, e);
            }
        });
    }

    @Override
    public Promise<List<TrialOnboarding>> listByTenant(String tenantId) {
        return findByTenantId(tenantId);
    }

    @Override
    public Promise<Void> delete(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
                stmt.setString(1, id);
                stmt.setString(2, extractTenantId(id));
                int rows = stmt.executeUpdate();
                LOG.info("[DMOS-PERSIST] trial onboarding deleted: id={} rows={}", id, rows);
                return null;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to delete trial onboarding id={}: {}", id, e.getMessage(), e);
                throw new DmPersistenceException("Failed to delete trial onboarding: " + id, e);
            }
        });
    }

    private static String extractTenantId(String id) {
        return id.substring(0, id.indexOf('-'));
    }

    private static TrialOnboarding mapRow(ResultSet rs) throws SQLException {
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Timestamp startedAtTs = rs.getTimestamp("started_at");
        Instant startedAt = startedAtTs != null ? startedAtTs.toInstant() : null;
        Timestamp completedAtTs = rs.getTimestamp("completed_at");
        Instant completedAt = completedAtTs != null ? completedAtTs.toInstant() : null;
        String stepProgressJson = rs.getString("step_progress");
        
        return TrialOnboarding.builder()
            .id(rs.getString("id"))
            .tenantId(rs.getString("tenant_id"))
            .workspaceId(rs.getString("workspace_id"))
            .leadId(rs.getString("lead_id"))
            .demoWorkspaceId(rs.getString("demo_workspace_id"))
            .status(TrialOnboardingStatus.valueOf(rs.getString("status")))
            .currentStep(rs.getInt("current_step"))
            .totalSteps(rs.getInt("total_steps"))
            .stepProgress(stepProgressJson != null ? parseJsonMap(stepProgressJson) : null)
            .createdAt(createdAt)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .cancellationReason(rs.getString("cancellation_reason"))
            .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            LOG.warn("[DMOS-PERSIST] failed to parse step_progress json: {}", e.getMessage());
            return null;
        }
    }
}

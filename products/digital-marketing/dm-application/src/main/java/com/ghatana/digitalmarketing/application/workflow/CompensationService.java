package com.ghatana.digitalmarketing.application.workflow;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
import java.util.UUID;

/**
 * P1-025: Compensation and rollback service for workflow failures.
 *
 * <p>Provides compensating actions for failed workflows to maintain data
 * consistency and enable recovery:</p>
 * <ul>
 *   <li>Campaign state rollback on publish failure</li>
 *   <li>Google Ads campaign cleanup on sync failure</li>
 *   <li>Budget restoration on modification failure</li>
 *   <li>Audit trail of all compensation actions</li>
 *   <li>Idempotent compensation to handle partial failures</li>
 * </ul>
 *
 * <p>Compensation actions are stored in the database and executed asynchronously
 * to ensure eventual consistency. Each compensation is idempotent and can be
 * safely retried.</p>
 *
 * @doc.type class
 * @doc.purpose Compensating actions for workflow failure recovery (P1-025)
 * @doc.layer product
 * @doc.pattern Saga, Compensation, Event Sourcing
 */
public final class CompensationService {

    private static final Logger LOG = LoggerFactory.getLogger(CompensationService.class);
    private static final int MAX_COMPENSATION_RETRIES = 3;

    private final DataSource dataSource;
    private final CompensationExecutor executor;

    public CompensationService(DataSource dataSource, CompensationExecutor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * P1-025: Records a compensation action for later execution.
     *
     * <p>When a workflow step fails, this method creates a compensation entry
     * that will revert any changes made by previous successful steps.</p>
     *
     * @param ctx operation context
     * @param workflowId the workflow that failed
     * @param failedStep the step that caused the failure
     * @param compensationType type of compensation needed
     * @param payload data needed for compensation
     * @return promise resolving to compensation entry ID
     */
    public Promise<String> recordCompensation(
            DmOperationContext ctx,
            String workflowId,
            String failedStep,
            CompensationType compensationType,
            String payload) {

        String correlationId = ctx.getCorrelationId().getValue();
        MDC.put("correlationId", correlationId);
        MDC.put("workflowId", workflowId);

        String compensationId = UUID.randomUUID().toString();

        LOG.warn("[DMOS-COMPENSATION] Recording compensation: workflow={}, step={}, type={}",
            workflowId, failedStep, compensationType);

        return executeInDb(conn -> {
            String sql = "INSERT INTO dmos_compensation_log " +
                "(id, workflow_id, tenant_id, failed_step, compensation_type, payload, " +
                "status, correlation_id, created_at, retry_count, max_retries) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, 0, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, compensationId);
                stmt.setString(2, workflowId);
                stmt.setString(3, ctx.getTenantId().getValue());
                stmt.setString(4, failedStep);
                stmt.setString(5, compensationType.name());
                stmt.setString(6, payload);
                stmt.setString(7, correlationId);
                stmt.setTimestamp(8, Timestamp.from(Instant.now()));
                stmt.setInt(9, MAX_COMPENSATION_RETRIES);
                stmt.executeUpdate();

                LOG.info("[DMOS-COMPENSATION] Compensation recorded: id={}", compensationId);
                return compensationId;
            }
        }).whenComplete(() -> MDC.clear());
    }

    /**
     * P1-025: Executes pending compensation actions.
     *
     * <p>This method should be called periodically (e.g., by a scheduler)
     * to process any pending compensations.</p>
     *
     * @return promise resolving to number of compensations processed
     */
    public Promise<Integer> executePendingCompensations() {
        LOG.info("[DMOS-COMPENSATION] Executing pending compensations");

        return fetchPendingCompensations()
            .then(entries -> {
                if (entries.isEmpty()) {
                    LOG.debug("[DMOS-COMPENSATION] No pending compensations");
                    return Promise.of(0);
                }

                LOG.info("[DMOS-COMPENSATION] Found {} pending compensations", entries.size());

                List<Promise<Void>> executions = new ArrayList<>();
                for (CompensationEntry entry : entries) {
                    executions.add(executeCompensation(entry));
                }

                return Promises.all(executions)
                    .map(v -> entries.size());
            });
    }

    /**
     * P1-025: Executes a single compensation action.
     *
     * <p>Compensation is idempotent - safe to retry on partial failure.</p>
     *
     * @param entry the compensation to execute
     * @return promise resolving when complete
     */
    private Promise<Void> executeCompensation(CompensationEntry entry) {
        MDC.put("compensationId", entry.id());
        MDC.put("workflowId", entry.workflowId());

        LOG.info("[DMOS-COMPENSATION] Executing compensation: id={}, type={}",
            entry.id(), entry.type());

        Promise<Void> executionPromise = switch (entry.type()) {
            case CAMPAIGN_ROLLBACK -> executeCampaignRollback(entry);
            case GOOGLE_ADS_CLEANUP -> executeGoogleAdsCleanup(entry);
            case BUDGET_RESTORE -> executeBudgetRestore(entry);
            case STRATEGY_INVALIDATE -> executeStrategyInvalidate(entry);
            case AUDIT_RECORD -> executeAuditRecord(entry);
        };

        return executionPromise
            .then(v -> markCompensationCompleted(entry.id()))
            .whenException(e -> handleCompensationFailure(entry, e))
            .whenComplete(() -> MDC.remove("compensationId"));
    }

    /**
     * P1-025: Rolls back campaign to previous state.
     */
    private Promise<Void> executeCampaignRollback(CompensationEntry entry) {
        LOG.info("[DMOS-COMPENSATION] Rolling back campaign state: compensation={}", entry.id());

        return executor.rollbackCampaignState(entry.workflowId(), entry.payload())
            .whenResult(v -> LOG.info("[DMOS-COMPENSATION] Campaign rollback successful: compensation={}",
                entry.id()))
            .whenException(e -> LOG.error("[DMOS-COMPENSATION] Campaign rollback failed: compensation={}",
                entry.id(), e));
    }

    /**
     * P1-025: Cleans up Google Ads campaign if creation succeeded but later steps failed.
     */
    private Promise<Void> executeGoogleAdsCleanup(CompensationEntry entry) {
        LOG.info("[DMOS-COMPENSATION] Cleaning up Google Ads campaign: compensation={}", entry.id());

        return executor.cleanupGoogleAdsCampaign(entry.workflowId(), entry.payload())
            .whenResult(v -> LOG.info("[DMOS-COMPENSATION] Google Ads cleanup successful: compensation={}",
                entry.id()))
            .whenException(e -> LOG.error("[DMOS-COMPENSATION] Google Ads cleanup failed: compensation={}",
                entry.id(), e));
    }

    /**
     * P1-025: Restores budget to previous amount on modification failure.
     */
    private Promise<Void> executeBudgetRestore(CompensationEntry entry) {
        LOG.info("[DMOS-COMPENSATION] Restoring budget: compensation={}", entry.id());

        return executor.restoreBudget(entry.workflowId(), entry.payload())
            .whenResult(v -> LOG.info("[DMOS-COMPENSATION] Budget restore successful: compensation={}",
                entry.id()))
            .whenException(e -> LOG.error("[DMOS-COMPENSATION] Budget restore failed: compensation={}",
                entry.id(), e));
    }

    /**
     * P1-025: Invalidates generated strategy if approval workflow fails.
     */
    private Promise<Void> executeStrategyInvalidate(CompensationEntry entry) {
        LOG.info("[DMOS-COMPENSATION] Invalidating strategy: compensation={}", entry.id());

        return executor.invalidateStrategy(entry.workflowId(), entry.payload())
            .whenResult(v -> LOG.info("[DMOS-COMPENSATION] Strategy invalidation successful: compensation={}",
                entry.id()))
            .whenException(e -> LOG.error("[DMOS-COMPENSATION] Strategy invalidation failed: compensation={}",
                entry.id(), e));
    }

    /**
     * P1-025: Records audit entry for compensation action.
     */
    private Promise<Void> executeAuditRecord(CompensationEntry entry) {
        return executor.recordCompensationAudit(entry.workflowId(), entry.payload());
    }

    /**
     * P1-025: Handles compensation execution failure with retry logic.
     */
    private Promise<Void> handleCompensationFailure(CompensationEntry entry, Throwable error) {
        LOG.error("[DMOS-COMPENSATION] Execution failed: compensation={}, error={}",
            entry.id(), error.getMessage(), error);

        if (entry.retryCount() < entry.maxRetries()) {
            LOG.warn("[DMOS-COMPENSATION] Will retry: compensation={}, attempt={}/{}",
                entry.id(), entry.retryCount() + 1, entry.maxRetries());

            return incrementRetryCount(entry.id())
                .then(v -> {
                    // Schedule retry for later
                    LOG.info("[DMOS-COMPENSATION] Retry scheduled: compensation={}", entry.id());
                    return Promise.complete();
                });
        } else {
            LOG.error("[DMOS-COMPENSATION] Max retries exceeded, marking failed: compensation={}",
                entry.id());

            return markCompensationFailed(entry.id(), error.getMessage());
        }
    }

    private Promise<List<CompensationEntry>> fetchPendingCompensations() {
        return executeInDb(conn -> {
            String sql = "SELECT id, workflow_id, tenant_id, failed_step, compensation_type, " +
                "payload, correlation_id, retry_count, max_retries " +
                "FROM dmos_compensation_log " +
                "WHERE status = 'PENDING' AND retry_count < max_retries " +
                "ORDER BY created_at ASC " +
                "LIMIT 100";

            List<CompensationEntry> entries = new ArrayList<>();

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    entries.add(new CompensationEntry(
                        rs.getString("id"),
                        rs.getString("workflow_id"),
                        rs.getString("tenant_id"),
                        rs.getString("failed_step"),
                        CompensationType.valueOf(rs.getString("compensation_type")),
                        rs.getString("payload"),
                        rs.getString("correlation_id"),
                        rs.getInt("retry_count"),
                        rs.getInt("max_retries")
                    ));
                }
            }

            return entries;
        });
    }

    private Promise<Void> markCompensationCompleted(String compensationId) {
        return executeInDb(conn -> {
            String sql = "UPDATE dmos_compensation_log SET status = 'COMPLETED', completed_at = ? WHERE id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, Timestamp.from(Instant.now()));
                stmt.setString(2, compensationId);
                stmt.executeUpdate();
                return null;
            }
        });
    }

    private Promise<Void> markCompensationFailed(String compensationId, String error) {
        return executeInDb(conn -> {
            String sql = "UPDATE dmos_compensation_log SET status = 'FAILED', error_message = ?, completed_at = ? WHERE id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, error);
                stmt.setTimestamp(2, Timestamp.from(Instant.now()));
                stmt.setString(3, compensationId);
                stmt.executeUpdate();
                return null;
            }
        });
    }

    private Promise<Void> incrementRetryCount(String compensationId) {
        return executeInDb(conn -> {
            String sql = "UPDATE dmos_compensation_log SET retry_count = retry_count + 1, last_retry_at = ? WHERE id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, Timestamp.from(Instant.now()));
                stmt.setString(2, compensationId);
                stmt.executeUpdate();
                return null;
            }
        });
    }

    private <T> Promise<T> executeInDb(DbOperation<T> operation) {
        return Promise.ofBlocking(null, () -> {
            try (Connection conn = dataSource.getConnection()) {
                return operation.execute(conn);
            } catch (SQLException e) {
                LOG.error("[DMOS-COMPENSATION] Database operation failed", e);
                throw new RuntimeException("Database operation failed", e);
            }
        });
    }

    @FunctionalInterface
    private interface DbOperation<T> {
        T execute(Connection conn) throws SQLException;
    }

    /**
     * Types of compensation actions supported.
     */
    public enum CompensationType {
        CAMPAIGN_ROLLBACK,      // Revert campaign state
        GOOGLE_ADS_CLEANUP,     // Delete created Google Ads campaign
        BUDGET_RESTORE,         // Restore previous budget amount
        STRATEGY_INVALIDATE,    // Mark strategy as invalid
        AUDIT_RECORD           // Record compensation in audit log
    }

    /**
     * Compensation entry record.
     */
    public record CompensationEntry(
        String id,
        String workflowId,
        String tenantId,
        String failedStep,
        CompensationType type,
        String payload,
        String correlationId,
        int retryCount,
        int maxRetries
    ) {}

    /**
     * Executor interface for compensation actions.
     * Implemented by domain services.
     */
    public interface CompensationExecutor {
        Promise<Void> rollbackCampaignState(String workflowId, String payload);
        Promise<Void> cleanupGoogleAdsCampaign(String workflowId, String payload);
        Promise<Void> restoreBudget(String workflowId, String payload);
        Promise<Void> invalidateStrategy(String workflowId, String payload);
        Promise<Void> recordCompensationAudit(String workflowId, String payload);
    }
}

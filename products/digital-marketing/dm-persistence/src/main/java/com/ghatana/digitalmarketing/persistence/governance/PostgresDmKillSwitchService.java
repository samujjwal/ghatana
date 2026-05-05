package com.ghatana.digitalmarketing.persistence.governance;

import com.ghatana.digitalmarketing.application.governance.DmKillSwitchService;
import com.ghatana.digitalmarketing.persistence.DmPersistenceException;
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
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Production PostgreSQL implementation of {@link DmKillSwitchService}.
 *
 * <p>Implements hierarchical kill switch checks: FEATURE → WORKSPACE → TENANT → GLOBAL.
 * All blocking I/O is wrapped in {@code Promise.ofBlocking()} to remain event-loop safe.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for DMOS kill switch operations (P1-024)
 * @doc.layer product
 * @doc.pattern Adapter, Repository, CircuitBreaker
 */
public final class PostgresDmKillSwitchService implements DmKillSwitchService {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresDmKillSwitchService.class);

    private static final String CHECK_KILL_SWITCH_SQL =
        "SELECT status FROM dmos_kill_switches " +
        "WHERE feature = ? AND status = 'ACTIVE' " +
        "AND (scope = 'GLOBAL' OR (scope = 'TENANT' AND scope_id = ?) " +
        "    OR (scope = 'WORKSPACE' AND scope_id = ?) " +
        "    OR (scope = 'FEATURE' AND scope_id = ?)) " +
        "ORDER BY CASE scope " +
        "  WHEN 'FEATURE' THEN 1 " +
        "  WHEN 'WORKSPACE' THEN 2 " +
        "  WHEN 'TENANT' THEN 3 " +
        "  WHEN 'GLOBAL' THEN 4 " +
        "END " +
        "LIMIT 1";

    private static final String ACTIVATE_SQL =
        "INSERT INTO dmos_kill_switches (scope, scope_id, feature, status, reason, activated_by, activated_at) " +
        "VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?) " +
        "ON CONFLICT (scope, scope_id, feature) DO UPDATE SET " +
        "  status = 'ACTIVE', reason = EXCLUDED.reason, activated_by = EXCLUDED.activated_by, " +
        "  activated_at = EXCLUDED.activated_at, deactivated_by = NULL, deactivated_at = NULL";

    private static final String DEACTIVATE_SQL =
        "UPDATE dmos_kill_switches SET status = 'INACTIVE', deactivated_by = ?, deactivated_at = ? " +
        "WHERE scope = ? AND scope_id = ? AND feature = ? AND status = 'ACTIVE'";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresDmKillSwitchService(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<Boolean> isKillSwitchActive(String tenantId, String workspaceId, String feature) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(feature, "feature must not be null");

        String wsId = workspaceId != null ? workspaceId : "";
        String featureScope = feature + ":" + tenantId + ":" + wsId;

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(CHECK_KILL_SWITCH_SQL)) {
                stmt.setString(1, feature);
                stmt.setString(2, tenantId);
                stmt.setString(3, wsId);
                stmt.setString(4, featureScope);

                try (ResultSet rs = stmt.executeQuery()) {
                    boolean isActive = rs.next();
                    if (isActive) {
                        LOG.warn("[DMOS-KILLSWITCH] BLOCKED: feature={} tenant={} workspace={}",
                            feature, tenantId, wsId);
                    }
                    return isActive;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-KILLSWITCH] Failed to check kill switch for feature={} tenant={}: {}",
                    feature, tenantId, e.getMessage(), e);
                // Fail-safe: if we can't check, assume not active but log loudly
                return false;
            }
        });
    }

    @Override
    public Promise<Void> activateKillSwitch(
            String scope, String scopeId, String feature, String reason, String activatedBy) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(scopeId, "scopeId must not be null");
        Objects.requireNonNull(feature, "feature must not be null");

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(ACTIVATE_SQL)) {
                stmt.setString(1, scope);
                stmt.setString(2, scopeId);
                stmt.setString(3, feature);
                stmt.setString(4, reason);
                stmt.setString(5, activatedBy);
                stmt.setTimestamp(6, Timestamp.from(Instant.now()));

                stmt.executeUpdate();
                LOG.info("[DMOS-KILLSWITCH] Activated: scope={} scopeId={} feature={} by={} reason={}",
                    scope, scopeId, feature, activatedBy, reason);
                return null;
            } catch (SQLException e) {
                LOG.error("[DMOS-KILLSWITCH] Failed to activate kill switch: {}", e.getMessage(), e);
                throw new DmPersistenceException("Failed to activate kill switch", e);
            }
        });
    }

    @Override
    public Promise<Void> deactivateKillSwitch(
            String scope, String scopeId, String feature, String deactivatedBy) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(scopeId, "scopeId must not be null");
        Objects.requireNonNull(feature, "feature must not be null");

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(DEACTIVATE_SQL)) {
                stmt.setString(1, deactivatedBy);
                stmt.setTimestamp(2, Timestamp.from(Instant.now()));
                stmt.setString(3, scope);
                stmt.setString(4, scopeId);
                stmt.setString(5, feature);

                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    LOG.info("[DMOS-KILLSWITCH] Deactivated: scope={} scopeId={} feature={} by={}",
                        scope, scopeId, feature, deactivatedBy);
                }
                return null;
            } catch (SQLException e) {
                LOG.error("[DMOS-KILLSWITCH] Failed to deactivate kill switch: {}", e.getMessage(), e);
                throw new DmPersistenceException("Failed to deactivate kill switch", e);
            }
        });
    }

    @Override
    public Promise<Void> recordKillSwitchAudit(
            String tenantId, String workspaceId, String feature,
            boolean wasBlocked, String correlationId) {
        // This would typically insert into an audit table
        // For now, we just log it - the audit plugin handles the actual recording
        if (wasBlocked) {
            LOG.warn("[DMOS-KILLSWITCH-AUDIT] Operation blocked: feature={} tenant={} workspace={} correlation={}",
                feature, tenantId, workspaceId, correlationId);
        } else {
            LOG.debug("[DMOS-KILLSWITCH-AUDIT] Operation allowed: feature={} tenant={} workspace={} correlation={}",
                feature, tenantId, workspaceId, correlationId);
        }
        return Promise.of(null);
    }
}

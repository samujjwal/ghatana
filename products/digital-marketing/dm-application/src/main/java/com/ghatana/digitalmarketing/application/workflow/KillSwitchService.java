package com.ghatana.digitalmarketing.application.workflow;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.bridge.KernelBridge;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P1-024: Kill switch enforcement service.
 *
 * <p>Provides emergency circuit breaker functionality for critical operations:
 * <ul>
 *   <li>Google Ads publishing</li>
 *   <li>AI content generation</li>
 *   <li>Budget modifications</li>
 *   <li>External API calls</li>
 * </ul>
 *
 * <p>Kill switches can be activated at multiple levels:
 * <ul>
 *   <li>Global - affects all tenants</li>
 *   <li>Tenant-specific - affects single tenant</li>
 *   <li>Workspace-specific - affects single workspace</li>
 *   <li>Feature-specific - affects specific feature/module</li>
 * </ul>
 *
 * <p>All kill switch checks are synchronous and fail-fast to ensure
 * immediate protection when activated.</p>
 *
 * @doc.type class
 * @doc.purpose Emergency circuit breaker for critical operations (P1-024)
 * @doc.layer product
 * @doc.pattern Circuit Breaker, Kill Switch, Safety
 */
public final class KillSwitchService {

    private static final Logger LOG = LoggerFactory.getLogger(KillSwitchService.class);

    private final DataSource dataSource;
    private final KernelBridge kernelBridge;

    // Local cache for kill switch states to avoid DB round-trips
    private final ConcurrentHashMap<String, KillSwitchState> cache = new ConcurrentHashMap<>();
    private volatile long lastCacheRefresh = 0;
    private static final long CACHE_TTL_MS = 5000; // 5 second TTL

    public KillSwitchService(DataSource dataSource, KernelBridge kernelBridge) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.kernelBridge = Objects.requireNonNull(kernelBridge, "kernelBridge must not be null");
    }

    /**
     * P1-024: Checks if a specific operation is allowed to proceed.
     *
     * <p>This method implements fail-closed behavior - if the kill switch
     * cannot be determined (e.g., DB unavailable), the operation is blocked.</p>
     *
     * @param ctx operation context
     * @param feature the feature/operation to check
     * @return true if operation is allowed, false if blocked
     */
    public boolean isOperationAllowed(DmOperationContext ctx, String feature) {
        String tenantId = ctx.getTenantId().getValue();
        String workspaceId = ctx.getWorkspaceId().getValue();
        String correlationId = ctx.getCorrelationId().getValue();

        MDC.put("correlationId", correlationId);
        MDC.put("tenantId", tenantId);
        MDC.put("workspaceId", workspaceId);
        MDC.put("feature", feature);

        try {
            // P1-024: Check hierarchy: global -> tenant -> workspace -> feature
            if (isGlobalKillSwitchActive(feature)) {
                LOG.warn("[DMOS-KILLSWITCH] BLOCKED: Global kill switch active for feature={}", feature);
                return false;
            }

            if (isTenantKillSwitchActive(tenantId, feature)) {
                LOG.warn("[DMOS-KILLSWITCH] BLOCKED: Tenant kill switch active: tenant={}, feature={}",
                    tenantId, feature);
                return false;
            }

            if (isWorkspaceKillSwitchActive(workspaceId, feature)) {
                LOG.warn("[DMOS-KILLSWITCH] BLOCKED: Workspace kill switch active: workspace={}, feature={}",
                    workspaceId, feature);
                return false;
            }

            // P1-024: Check feature-specific kills (even if no tenant/workspace scope)
            if (isFeatureKillSwitchActive(feature)) {
                LOG.warn("[DMOS-KILLSWITCH] BLOCKED: Feature kill switch active: feature={}", feature);
                return false;
            }

            LOG.debug("[DMOS-KILLSWITCH] ALLOWED: operation permitted for feature={}", feature);
            return true;

        } catch (Exception e) {
            // P1-024: Fail-closed: if we can't determine kill switch state, block the operation
            LOG.error("[DMOS-KILLSWITCH] FAIL-CLOSED: Error checking kill switch state for feature={}: {}",
                feature, e.getMessage());
            return false;
        } finally {
            MDC.clear();
        }
    }

    /**
     * P1-024: Async version for use in promise chains.
     */
    public Promise<Boolean> isOperationAllowedAsync(DmOperationContext ctx, String feature) {
        return Promise.of(isOperationAllowed(ctx, feature));
    }

    /**
     * P1-024: Activates a global kill switch.
     */
    public Promise<Void> activateGlobalKillSwitch(String feature, String reason, String activatedBy) {
        LOG.error("[DMOS-KILLSWITCH] ACTIVATING GLOBAL: feature={}, reason={}, by={}",
            feature, reason, activatedBy);

        return executeInDb(conn -> {
            String sql = "INSERT INTO dmos_kill_switches " +
                "(scope, scope_id, feature, status, reason, activated_by, activated_at) " +
                "VALUES ('GLOBAL', '*', ?, 'ACTIVE', ?, ?, ?) " +
                "ON CONFLICT (scope, scope_id, feature) DO UPDATE SET " +
                "status = 'ACTIVE', reason = ?, activated_by = ?, activated_at = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                Instant now = Instant.now();
                stmt.setString(1, feature);
                stmt.setString(2, reason);
                stmt.setString(3, activatedBy);
                stmt.setTimestamp(4, java.sql.Timestamp.from(now));
                stmt.setString(5, reason);
                stmt.setString(6, activatedBy);
                stmt.setTimestamp(7, java.sql.Timestamp.from(now));
                stmt.executeUpdate();

                // Update cache
                cache.put("GLOBAL:" + feature, new KillSwitchState(true, reason, now));

                LOG.error("[DMOS-KILLSWITCH] GLOBAL ACTIVATED: feature={}", feature);
                return null;
            }
        });
    }

    /**
     * P1-024: Activates a tenant-specific kill switch.
     */
    public Promise<Void> activateTenantKillSwitch(String tenantId, String feature,
                                                     String reason, String activatedBy) {
        LOG.error("[DMOS-KILLSWITCH] ACTIVATING TENANT: tenant={}, feature={}, reason={}, by={}",
            tenantId, feature, reason, activatedBy);

        return executeInDb(conn -> {
            String sql = "INSERT INTO dmos_kill_switches " +
                "(scope, scope_id, feature, status, reason, activated_by, activated_at) " +
                "VALUES ('TENANT', ?, ?, 'ACTIVE', ?, ?, ?) " +
                "ON CONFLICT (scope, scope_id, feature) DO UPDATE SET " +
                "status = 'ACTIVE', reason = ?, activated_by = ?, activated_at = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                Instant now = Instant.now();
                stmt.setString(1, tenantId);
                stmt.setString(2, feature);
                stmt.setString(3, reason);
                stmt.setString(4, activatedBy);
                stmt.setTimestamp(5, java.sql.Timestamp.from(now));
                stmt.setString(6, reason);
                stmt.setString(7, activatedBy);
                stmt.setTimestamp(8, java.sql.Timestamp.from(now));
                stmt.executeUpdate();

                cache.put("TENANT:" + tenantId + ":" + feature,
                    new KillSwitchState(true, reason, now));

                return null;
            }
        });
    }

    /**
     * P1-024: Deactivates a kill switch.
     */
    public Promise<Void> deactivateKillSwitch(String scope, String scopeId, String feature,
                                               String deactivatedBy) {
        LOG.info("[DMOS-KILLSWITCH] DEACTIVATING: scope={}, scopeId={}, feature={}, by={}",
            scope, scopeId, feature, deactivatedBy);

        return executeInDb(conn -> {
            String sql = "UPDATE dmos_kill_switches SET " +
                "status = 'INACTIVE', deactivated_by = ?, deactivated_at = ? " +
                "WHERE scope = ? AND scope_id = ? AND feature = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, deactivatedBy);
                stmt.setTimestamp(2, java.sql.Timestamp.from(Instant.now()));
                stmt.setString(3, scope);
                stmt.setString(4, scopeId);
                stmt.setString(5, feature);
                int updated = stmt.executeUpdate();

                if (updated > 0) {
                    LOG.info("[DMOS-KILLSWITCH] DEACTIVATED: scope={}, scopeId={}, feature={}",
                        scope, scopeId, feature);

                    // Update cache
                    String cacheKey = scope + ":" + scopeId + ":" + feature;
                    cache.remove(cacheKey);
                }

                return null;
            }
        });
    }

    /**
     * P1-024: Checks global kill switch with caching.
     */
    private boolean isGlobalKillSwitchActive(String feature) {
        String cacheKey = "GLOBAL:" + feature;
        KillSwitchState cached = cache.get(cacheKey);

        if (cached != null && !isCacheStale()) {
            return cached.active();
        }

        return checkKillSwitchInDb("GLOBAL", "*", feature);
    }

    /**
     * P1-024: Checks tenant kill switch with caching.
     */
    private boolean isTenantKillSwitchActive(String tenantId, String feature) {
        String cacheKey = "TENANT:" + tenantId + ":" + feature;
        KillSwitchState cached = cache.get(cacheKey);

        if (cached != null && !isCacheStale()) {
            return cached.active();
        }

        return checkKillSwitchInDb("TENANT", tenantId, feature);
    }

    /**
     * P1-024: Checks workspace kill switch with caching.
     */
    private boolean isWorkspaceKillSwitchActive(String workspaceId, String feature) {
        String cacheKey = "WORKSPACE:" + workspaceId + ":" + feature;
        KillSwitchState cached = cache.get(cacheKey);

        if (cached != null && !isCacheStale()) {
            return cached.active();
        }

        return checkKillSwitchInDb("WORKSPACE", workspaceId, feature);
    }

    /**
     * P1-024: Checks feature-specific kill switch.
     */
    private boolean isFeatureKillSwitchActive(String feature) {
        String cacheKey = "FEATURE:" + feature;
        KillSwitchState cached = cache.get(cacheKey);

        if (cached != null && !isCacheStale()) {
            return cached.active();
        }

        return checkKillSwitchInDb("FEATURE", feature, "*");
    }

    /**
     * P1-024: Queries kill switch state from database.
     */
    private boolean checkKillSwitchInDb(String scope, String scopeId, String feature) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT status, reason, activated_at FROM dmos_kill_switches " +
                 "WHERE scope = ? AND scope_id = ? AND feature = ? AND status = 'ACTIVE'")) {

            stmt.setString(1, scope);
            stmt.setString(2, scopeId);
            stmt.setString(3, feature);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String reason = rs.getString("reason");
                    Instant activatedAt = rs.getTimestamp("activated_at").toInstant();

                    // Update cache
                    String cacheKey = scope + ":" + scopeId + ":" + feature;
                    cache.put(cacheKey, new KillSwitchState(true, reason, activatedAt));

                    return true;
                }
            }

            // Not found = not active
            return false;

        } catch (SQLException e) {
            LOG.error("[DMOS-KILLSWITCH] Database error checking kill switch: scope={}, scopeId={}, feature={}",
                scope, scopeId, feature, e);
            throw new RuntimeException("Failed to check kill switch state", e);
        }
    }

    private boolean isCacheStale() {
        return System.currentTimeMillis() - lastCacheRefresh > CACHE_TTL_MS;
    }

    private <T> Promise<T> executeInDb(DbOperation<T> operation) {
        return Promise.ofBlocking(null, () -> {
            try (Connection conn = dataSource.getConnection()) {
                return operation.execute(conn);
            } catch (SQLException e) {
                LOG.error("[DMOS-KILLSWITCH] Database operation failed", e);
                throw new RuntimeException("Database operation failed", e);
            }
        });
    }

    @FunctionalInterface
    private interface DbOperation<T> {
        T execute(Connection conn) throws SQLException;
    }

    // Kill switch features
    public static final String FEATURE_GOOGLE_ADS_PUBLISH = "google_ads.publish";
    public static final String FEATURE_GOOGLE_ADS_UPDATE = "google_ads.update";
    public static final String FEATURE_AI_GENERATION = "ai.generation";
    public static final String FEATURE_BUDGET_MODIFICATION = "budget.modification";
    public static final String FEATURE_EXTERNAL_API = "external.api";
    public static final String FEATURE_CAMPAIGN_ACTIVATION = "campaign.activation";

    private record KillSwitchState(boolean active, String reason, Instant activatedAt) {}
}

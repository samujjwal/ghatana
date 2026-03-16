package com.ghatana.appplatform.operator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Manage gradual feature rollout strategies and A/B testing for the platform.
 *              Supports ALL_AT_ONCE, PERCENTAGE, and TENANT_LIST strategies.
 *              A/B variants allow two cohorts to receive different rollout targets.
 *              Kill switch disables any rollout immediately with no delay.
 *              Rollout eligibility is evaluated per-request — no long-lived cache.
 * @doc.layer   Operator Workflows (O-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-O01-005: Feature rollout and A/B management
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS feature_rollouts (
 *   rollout_id     TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   feature_key    TEXT NOT NULL UNIQUE,
 *   strategy       TEXT NOT NULL,          -- ALL_AT_ONCE | PERCENTAGE | TENANT_LIST
 *   percentage     INT ,                   -- used when strategy=PERCENTAGE (0-100)
 *   variant_a      TEXT,                   -- optional A/B variant label for first cohort
 *   variant_b      TEXT,                   -- optional A/B variant label for second cohort
 *   kill_switch    BOOLEAN NOT NULL DEFAULT FALSE,
 *   active         BOOLEAN NOT NULL DEFAULT TRUE,
 *   created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS feature_rollout_tenants (
 *   rollout_id     TEXT NOT NULL,
 *   tenant_id      TEXT NOT NULL,
 *   variant        TEXT,                   -- A | B | NULL (for non-AB rollouts)
 *   PRIMARY KEY(rollout_id, tenant_id)
 * );
 * </pre>
 */
public class FeatureRolloutService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record RolloutPlan(
        String rolloutId, String featureKey, String strategy,
        Integer percentage, String variantA, String variantB,
        boolean killSwitch, boolean active
    ) {}

    public record EligibilityResult(String featureKey, boolean eligible, String variant) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter killSwitchActivations;
    private final Counter eligibilityEvaluations;

    public FeatureRolloutService(
        javax.sql.DataSource ds,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                    = ds;
        this.audit                 = audit;
        this.executor              = executor;
        this.killSwitchActivations = Counter.builder("operator.rollout.kill_switch_activations").register(registry);
        this.eligibilityEvaluations = Counter.builder("operator.rollout.eligibility_checks").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Create or replace a rollout plan for a feature. */
    public Promise<RolloutPlan> upsertRollout(RolloutPlan plan, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            validateStrategy(plan);
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO feature_rollouts (feature_key, strategy, percentage, variant_a, variant_b, kill_switch, active) " +
                     "VALUES (?,?,?,?,?,?,?) ON CONFLICT (feature_key) DO UPDATE " +
                     "SET strategy=EXCLUDED.strategy, percentage=EXCLUDED.percentage, " +
                     "variant_a=EXCLUDED.variant_a, variant_b=EXCLUDED.variant_b, " +
                     "kill_switch=EXCLUDED.kill_switch, active=EXCLUDED.active, updated_at=NOW() " +
                     "RETURNING rollout_id"
                 )) {
                ps.setString(1, plan.featureKey()); ps.setString(2, plan.strategy());
                if (plan.percentage() != null) ps.setInt(3, plan.percentage()); else ps.setNull(3, Types.INTEGER);
                ps.setString(4, plan.variantA()); ps.setString(5, plan.variantB());
                ps.setBoolean(6, plan.killSwitch()); ps.setBoolean(7, plan.active());
                String rolloutId;
                try (ResultSet rs = ps.executeQuery()) { rs.next(); rolloutId = rs.getString("rollout_id"); }
                audit.record(operatorId, "ROLLOUT_UPSERTED", "feature=" + plan.featureKey() + " strategy=" + plan.strategy());
                return new RolloutPlan(rolloutId, plan.featureKey(), plan.strategy(), plan.percentage(),
                    plan.variantA(), plan.variantB(), plan.killSwitch(), plan.active());
            }
        });
    }

    /**
     * Activate kill switch for a feature — disables rollout immediately for all tenants.
     */
    public Promise<Void> activateKillSwitch(String featureKey, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE feature_rollouts SET kill_switch=TRUE, updated_at=NOW() WHERE feature_key=?"
                 )) {
                ps.setString(1, featureKey); ps.executeUpdate();
            }
            killSwitchActivations.increment();
            audit.record(operatorId, "KILL_SWITCH_ACTIVATED", "feature=" + featureKey);
            return null;
        });
    }

    /**
     * Assign specific tenants to a TENANT_LIST or A/B rollout.
     */
    public Promise<Void> assignTenants(String featureKey, Map<String, String> tenantVariants, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                String rolloutId = getRolloutId(c, featureKey);
                for (Map.Entry<String, String> e : tenantVariants.entrySet()) {
                    try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO feature_rollout_tenants (rollout_id, tenant_id, variant) VALUES (?,?,?) " +
                        "ON CONFLICT (rollout_id, tenant_id) DO UPDATE SET variant=EXCLUDED.variant"
                    )) {
                        ps.setString(1, rolloutId); ps.setString(2, e.getKey()); ps.setString(3, e.getValue());
                        ps.executeUpdate();
                    }
                }
                c.commit();
            }
            audit.record(operatorId, "ROLLOUT_TENANTS_ASSIGNED", "feature=" + featureKey + " count=" + tenantVariants.size());
            return null;
        });
    }

    /**
     * Per-request eligibility check. Returns immediately — no caching.
     */
    public Promise<EligibilityResult> isEligible(String featureKey, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            eligibilityEvaluations.increment();
            try (Connection c = ds.getConnection()) {
                RolloutRecord r = loadRollout(c, featureKey);
                if (r == null || !r.active || r.killSwitch) {
                    return new EligibilityResult(featureKey, false, null);
                }
                return switch (r.strategy) {
                    case "ALL_AT_ONCE" -> new EligibilityResult(featureKey, true, null);
                    case "PERCENTAGE"  -> evaluatePercentage(featureKey, tenantId, r.percentage);
                    case "TENANT_LIST" -> evaluateTenantList(c, r.rolloutId, tenantId, featureKey);
                    default            -> new EligibilityResult(featureKey, false, null);
                };
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateStrategy(RolloutPlan p) {
        if ("PERCENTAGE".equals(p.strategy()) && (p.percentage() == null || p.percentage() < 0 || p.percentage() > 100)) {
            throw new IllegalArgumentException("PERCENTAGE strategy requires percentage 0-100");
        }
    }

    private String getRolloutId(Connection c, String featureKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT rollout_id FROM feature_rollouts WHERE feature_key=?")) {
            ps.setString(1, featureKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("No rollout for feature: " + featureKey);
                return rs.getString("rollout_id");
            }
        }
    }

    private record RolloutRecord(String rolloutId, String strategy, int percentage, boolean killSwitch, boolean active) {}

    private RolloutRecord loadRollout(Connection c, String featureKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT rollout_id, strategy, COALESCE(percentage,0) AS percentage, kill_switch, active " +
            "FROM feature_rollouts WHERE feature_key=?"
        )) {
            ps.setString(1, featureKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new RolloutRecord(rs.getString("rollout_id"), rs.getString("strategy"),
                    rs.getInt("percentage"), rs.getBoolean("kill_switch"), rs.getBoolean("active"));
            }
        }
    }

    private EligibilityResult evaluatePercentage(String featureKey, String tenantId, int pct) {
        // Deterministic hash so the same tenant always gets the same bucket
        int bucket = Math.abs(Objects.hash(featureKey, tenantId)) % 100;
        return new EligibilityResult(featureKey, bucket < pct, null);
    }

    private EligibilityResult evaluateTenantList(Connection c, String rolloutId, String tenantId, String featureKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT variant FROM feature_rollout_tenants WHERE rollout_id=? AND tenant_id=?"
        )) {
            ps.setString(1, rolloutId); ps.setString(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new EligibilityResult(featureKey, false, null);
                return new EligibilityResult(featureKey, true, rs.getString("variant"));
            }
        }
    }
}

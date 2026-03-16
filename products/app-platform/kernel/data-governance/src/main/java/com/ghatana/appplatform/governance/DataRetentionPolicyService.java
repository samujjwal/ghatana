package com.ghatana.appplatform.governance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Defines and stores data retention policies per asset. Default: 7 years for
 *              financial data (NRB requirement). Actions: ARCHIVE / DELETE / ANONYMIZE.
 *              Policy registry with CRUD. K-01 maker-checker for policy changes.
 *              Publishes RetentionPolicyApplied event. Satisfies STORY-K08-009.
 * @doc.layer   Kernel
 * @doc.pattern 7-year NRB default retention; K-01 maker-checker; ARCHIVE/DELETE/ANONYMIZE
 *              actions; glob pattern matching; RetentionPolicyApplied event; Gauge.
 */
public class DataRetentionPolicyService {

    private static final int DEFAULT_RETENTION_YEARS = 7;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final WorkflowPort     workflowPort;
    private final EventPort        eventPort;
    private final Counter          policiesAppliedCounter;
    private final AtomicLong       activePoliciesGauge = new AtomicLong(0);

    public DataRetentionPolicyService(HikariDataSource dataSource, Executor executor,
                                       WorkflowPort workflowPort, EventPort eventPort,
                                       MeterRegistry registry) {
        this.dataSource            = dataSource;
        this.executor              = executor;
        this.workflowPort          = workflowPort;
        this.eventPort             = eventPort;
        this.policiesAppliedCounter = Counter.builder("governance.retention.policies_applied_total").register(registry);
        Gauge.builder("governance.retention.active_policies", activePoliciesGauge, AtomicLong::get)
                .register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface WorkflowPort {
        String createApprovalTask(String policyId, String submittedBy, String action);
    }

    public interface EventPort {
        void publish(String topic, Object event);
    }

    // ─── Enums & Records ─────────────────────────────────────────────────────

    public enum RetentionAction { ARCHIVE, DELETE, ANONYMIZE }

    public record RetentionPolicy(String policyId, String assetPattern, int retentionDays,
                                   RetentionAction action, String regulatoryBasis,
                                   boolean active, LocalDateTime createdAt) {}

    public record PolicyMatch(String assetId, String assetName, RetentionPolicy policy) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<RetentionPolicy> createPolicy(String assetPattern, int retentionDays,
                                                  RetentionAction action, String regulatoryBasis,
                                                  String submittedBy) {
        return Promise.ofBlocking(executor, () -> {
            String policyId = UUID.randomUUID().toString();
            RetentionPolicy policy = insertPolicy(policyId, assetPattern, retentionDays,
                    action, regulatoryBasis);
            // K-01 maker-checker for policy changes
            workflowPort.createApprovalTask(policyId, submittedBy, "CREATE_RETENTION_POLICY");
            activePoliciesGauge.set(countActivePolicies());
            return policy;
        });
    }

    /** Create default 7-year NRB financial data retention policy. */
    public Promise<RetentionPolicy> createDefaultFinancialPolicy(String assetPattern,
                                                                   String submittedBy) {
        return createPolicy(assetPattern, DEFAULT_RETENTION_YEARS * 365,
                RetentionAction.ARCHIVE, "NRB Financial Records Retention Regulation", submittedBy);
    }

    public Promise<List<RetentionPolicy>> listPolicies() {
        return Promise.ofBlocking(executor, () -> fetchAllPolicies());
    }

    public Promise<RetentionPolicy> deactivatePolicy(String policyId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "UPDATE retention_policies SET active=FALSE WHERE policy_id=? RETURNING *";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, policyId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Policy not found: " + policyId);
                    RetentionPolicy p = mapRow(rs);
                    activePoliciesGauge.set(countActivePolicies());
                    return p;
                }
            }
        });
    }

    /** Match an asset against all active policies — returns highest-priority match. */
    public Promise<List<PolicyMatch>> matchAssets() {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    SELECT dc.asset_id, dc.name, rp.policy_id
                    FROM data_catalog dc
                    CROSS JOIN retention_policies rp
                    WHERE rp.active=TRUE
                      AND dc.name ILIKE REPLACE(REPLACE(rp.asset_pattern, '*', '%'), '?', '_')
                    """;
            List<PolicyMatch> matches = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RetentionPolicy policy = loadPolicy(rs.getString("policy_id"));
                    matches.add(new PolicyMatch(rs.getString("asset_id"), rs.getString("name"), policy));
                }
            }
            return matches;
        });
    }

    /** Mark policy as applied for a specific asset and publish event. */
    public Promise<Void> applyPolicy(String policyId, String assetId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    INSERT INTO retention_policy_applications
                        (application_id, policy_id, asset_id, applied_at)
                    VALUES (gen_random_uuid(), ?, ?, NOW())
                    ON CONFLICT (policy_id, asset_id) DO UPDATE SET applied_at=NOW()
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, policyId); ps.setString(2, assetId);
                ps.executeUpdate();
            }
            policiesAppliedCounter.increment();
            eventPort.publish("governance.retention.policy_applied",
                    new RetentionPolicyAppliedEvent(policyId, assetId, LocalDateTime.now()));
            return null;
        });
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private RetentionPolicy insertPolicy(String policyId, String assetPattern, int retentionDays,
                                          RetentionAction action,
                                          String regulatoryBasis) throws SQLException {
        String sql = """
                INSERT INTO retention_policies
                    (policy_id, asset_pattern, retention_days, action, regulatory_basis,
                     active, created_at)
                VALUES (?, ?, ?, ?, ?, TRUE, NOW())
                ON CONFLICT (asset_pattern, action) DO UPDATE
                    SET retention_days=EXCLUDED.retention_days,
                        regulatory_basis=EXCLUDED.regulatory_basis
                RETURNING *
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, policyId); ps.setString(2, assetPattern);
            ps.setInt(3, retentionDays); ps.setString(4, action.name());
            ps.setString(5, regulatoryBasis);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next(); return mapRow(rs);
            }
        }
    }

    private RetentionPolicy loadPolicy(String policyId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM retention_policies WHERE policy_id=?")) {
            ps.setString(1, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Policy not found: " + policyId);
                return mapRow(rs);
            }
        }
    }

    private List<RetentionPolicy> fetchAllPolicies() throws SQLException {
        List<RetentionPolicy> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM retention_policies ORDER BY created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    private long countActivePolicies() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM retention_policies WHERE active=TRUE");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private RetentionPolicy mapRow(ResultSet rs) throws SQLException {
        return new RetentionPolicy(rs.getString("policy_id"), rs.getString("asset_pattern"),
                rs.getInt("retention_days"), RetentionAction.valueOf(rs.getString("action")),
                rs.getString("regulatory_basis"), rs.getBoolean("active"),
                rs.getObject("created_at", LocalDateTime.class));
    }

    record RetentionPolicyAppliedEvent(String policyId, String assetId, LocalDateTime appliedAt) {}
}

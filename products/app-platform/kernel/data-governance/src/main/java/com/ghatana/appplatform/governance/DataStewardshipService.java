package com.ghatana.appplatform.governance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Data steward assignment and accountability management. Links data stewards to
 *              data domains and individual assets. Each assignment carries an SLA (days) for
 *              acting on quality breaks, classification reviews, and lineage validations.
 *              Stewards who miss their SLA window are escalated to the domain owner.
 *              Integrates with K-01 RBAC to verify steward role. Satisfies STORY-K08-013.
 * @doc.layer   Kernel
 * @doc.pattern Role-based steward assignment; SLA monitoring with automatic escalation;
 *              domain-level and asset-level assignment scopes; K-07 audit trail;
 *              overwdueCount Gauge.
 */
public class DataStewardshipService {

    private final HikariDataSource   dataSource;
    private final Executor           executor;
    private final RbacPort           rbacPort;
    private final NotificationPort   notificationPort;
    private final AuditPort          auditPort;
    private final AtomicLong         overdueCount = new AtomicLong(0);

    public DataStewardshipService(HikariDataSource dataSource, Executor executor,
                                   RbacPort rbacPort,
                                   NotificationPort notificationPort,
                                   AuditPort auditPort,
                                   MeterRegistry registry) {
        this.dataSource       = dataSource;
        this.executor         = executor;
        this.rbacPort         = rbacPort;
        this.notificationPort = notificationPort;
        this.auditPort        = auditPort;

        Gauge.builder("governance.stewardship.overdue_count", overdueCount, AtomicLong::get).register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-01 RBAC: check whether a user has the required role. */
    public interface RbacPort {
        boolean hasRole(String userId, String role);
        String domainOwner(String domainId);
    }

    /** Steward and escalation notifications. */
    public interface NotificationPort {
        void notifySteward(String stewardId, String message);
        void notifyOwner(String ownerId, String message);
    }

    /** K-07 audit trail. */
    public interface AuditPort {
        void log(String action, String resourceType, String resourceId, Map<String, Object> details);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum AssignmentScope { DOMAIN, ASSET }
    public enum ActionStatus { PENDING, IN_PROGRESS, COMPLETED }

    public record StewardAssignment(
        String assignmentId, AssignmentScope scope,
        String domainId, String assetId, String stewardId,
        int slaDays, Instant assignedAt, Instant slaDeadline
    ) {}

    public record StewardAction(
        String actionId, String assignmentId, String stewardId,
        String actionType, ActionStatus status, Instant createdAt, Instant dueAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Assign a steward to a data domain. Only users with DATA_STEWARD role may be assigned.
     * Existing assignment for the domain is replaced (ON CONFLICT DO UPDATE).
     */
    public Promise<StewardAssignment> assignDomainSteward(String domainId, String stewardId,
                                                            int slaDays, String assignedBy) {
        return Promise.ofBlocking(executor, () -> {
            if (!rbacPort.hasRole(stewardId, "DATA_STEWARD")) {
                throw new IllegalArgumentException("User " + stewardId + " lacks DATA_STEWARD role");
            }

            String assignmentId = UUID.randomUUID().toString();
            Instant now         = Instant.now();
            Instant slaDeadline = now.plusSeconds((long) slaDays * 86400);

            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO data_steward_assignments " +
                     "(assignment_id, scope, domain_id, asset_id, steward_id, sla_days, assigned_at, sla_deadline) " +
                     "VALUES (?, 'DOMAIN', ?, NULL, ?, ?, NOW(), ?) " +
                     "ON CONFLICT (domain_id) WHERE scope = 'DOMAIN' DO UPDATE SET " +
                     "steward_id = EXCLUDED.steward_id, sla_days = EXCLUDED.sla_days, " +
                     "sla_deadline = EXCLUDED.sla_deadline, assigned_at = NOW()")) {
                ps.setString(1, assignmentId);
                ps.setString(2, domainId);
                ps.setString(3, stewardId);
                ps.setInt(4, slaDays);
                ps.setTimestamp(5, Timestamp.from(slaDeadline));
                ps.executeUpdate();
            }

            notificationPort.notifySteward(stewardId,
                "You have been assigned as steward for domain " + domainId +
                " with a " + slaDays + "-day SLA.");

            auditPort.log("STEWARD_ASSIGNED", "Domain", domainId,
                Map.of("stewardId", stewardId, "slaDays", slaDays, "assignedBy", assignedBy));

            return new StewardAssignment(assignmentId, AssignmentScope.DOMAIN,
                domainId, null, stewardId, slaDays, now, slaDeadline);
        });
    }

    /**
     * Assign a steward to a specific data asset (overrides domain-level assignment for this asset).
     */
    public Promise<StewardAssignment> assignAssetSteward(String assetId, String domainId,
                                                          String stewardId, int slaDays,
                                                          String assignedBy) {
        return Promise.ofBlocking(executor, () -> {
            if (!rbacPort.hasRole(stewardId, "DATA_STEWARD")) {
                throw new IllegalArgumentException("User " + stewardId + " lacks DATA_STEWARD role");
            }

            String assignmentId = UUID.randomUUID().toString();
            Instant now         = Instant.now();
            Instant slaDeadline = now.plusSeconds((long) slaDays * 86400);

            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO data_steward_assignments " +
                     "(assignment_id, scope, domain_id, asset_id, steward_id, sla_days, assigned_at, sla_deadline) " +
                     "VALUES (?, 'ASSET', ?, ?, ?, ?, NOW(), ?) " +
                     "ON CONFLICT (asset_id) WHERE scope = 'ASSET' DO UPDATE SET " +
                     "steward_id = EXCLUDED.steward_id, sla_days = EXCLUDED.sla_days, " +
                     "sla_deadline = EXCLUDED.sla_deadline, assigned_at = NOW()")) {
                ps.setString(1, assignmentId);
                ps.setString(2, domainId);
                ps.setString(3, assetId);
                ps.setString(4, stewardId);
                ps.setInt(5, slaDays);
                ps.setTimestamp(6, Timestamp.from(slaDeadline));
                ps.executeUpdate();
            }

            auditPort.log("STEWARD_ASSIGNED", "DataAsset", assetId,
                Map.of("stewardId", stewardId, "slaDays", slaDays, "assignedBy", assignedBy));

            return new StewardAssignment(assignmentId, AssignmentScope.ASSET,
                domainId, assetId, stewardId, slaDays, now, slaDeadline);
        });
    }

    /**
     * Record a steward action taken on a quality break, classification review, or lineage task.
     */
    public Promise<StewardAction> recordAction(String assignmentId, String stewardId,
                                                String actionType) {
        return Promise.ofBlocking(executor, () -> {
            String actionId = UUID.randomUUID().toString();
            Instant now     = Instant.now();

            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO steward_action_log " +
                     "(action_id, assignment_id, steward_id, action_type, status, created_at) " +
                     "VALUES (?, ?, ?, ?, 'IN_PROGRESS', NOW())")) {
                ps.setString(1, actionId);
                ps.setString(2, assignmentId);
                ps.setString(3, stewardId);
                ps.setString(4, actionType);
                ps.executeUpdate();
            }

            auditPort.log("STEWARD_ACTION_RECORDED", "StewardAssignment", assignmentId,
                Map.of("actionId", actionId, "stewardId", stewardId, "actionType", actionType));

            return new StewardAction(actionId, assignmentId, stewardId, actionType,
                ActionStatus.IN_PROGRESS, now, now.plusSeconds(86400));
        });
    }

    /**
     * Scan all assignments for SLA breaches and escalate overdue ones to domain owners.
     * Returns the number of escalations triggered.
     */
    public Promise<Integer> escalateSlaBreaches() {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, String>> overdueAssignments = fetchOverdueAssignments();
            overdueCount.set(overdueAssignments.size());

            for (Map<String, String> assignment : overdueAssignments) {
                String stewardId  = assignment.get("steward_id");
                String domainId   = assignment.get("domain_id");
                String ownerId    = rbacPort.domainOwner(domainId);
                String assignmentId = assignment.get("assignment_id");

                notificationPort.notifyOwner(ownerId,
                    String.format("Steward %s has exceeded SLA for domain %s. Escalating.",
                        stewardId, domainId));

                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                         "UPDATE data_steward_assignments SET escalated = TRUE, " +
                         "escalated_at = NOW() WHERE assignment_id = ? AND NOT escalated")) {
                    ps.setString(1, assignmentId);
                    ps.executeUpdate();
                }

                auditPort.log("STEWARD_SLA_ESCALATED", "StewardAssignment", assignmentId,
                    Map.of("stewardId", stewardId, "domainId", domainId, "escalatedTo", ownerId));
            }

            return overdueAssignments.size();
        });
    }

    /**
     * Retrieve the effective steward for a given asset (asset-level beats domain-level).
     */
    public Promise<Optional<String>> resolveEffectiveSteward(String assetId, String domainId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT steward_id FROM data_steward_assignments " +
                     "WHERE (asset_id = ? AND scope = 'ASSET') OR (domain_id = ? AND scope = 'DOMAIN') " +
                     "ORDER BY scope DESC LIMIT 1")) {
                ps.setString(1, assetId);
                ps.setString(2, domainId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(rs.getString("steward_id"));
                }
            }
            return Optional.empty();
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private List<Map<String, String>> fetchOverdueAssignments() throws SQLException {
        List<Map<String, String>> results = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT assignment_id, steward_id, domain_id FROM data_steward_assignments " +
                 "WHERE sla_deadline < NOW() AND NOT escalated")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> row = new HashMap<>();
                    row.put("assignment_id", rs.getString("assignment_id"));
                    row.put("steward_id",    rs.getString("steward_id"));
                    row.put("domain_id",     rs.getString("domain_id"));
                    results.add(row);
                }
            }
        }
        return results;
    }
}

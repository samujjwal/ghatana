package com.ghatana.digitalmarketing.application.approval;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.approval.ApprovalRequest;
import com.ghatana.digitalmarketing.domain.approval.ApprovalStatus;
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
import java.util.*;

/**
 * P1-012: Approval Queue Governance Service
 *
 * <p>Aligns direct approval operations with queue governance:
 * <ul>
 *   <li>Canonical approval model with queue semantics</li>
 *   <li>Priority-based queue ordering</li>
 *   <li>SLA enforcement for approval times</li>
 *   <li>Escalation rules for overdue approvals</li>
 *   <li>Queue position tracking</li>
 *   <li>Bulk approval operations</li>
 *   <li>Approval delegation</li>
 * </ul>
 *
 * <p>Ensures all approvals follow consistent governance regardless of entry point.</p>
 *
 * @doc.type class
 * @doc.purpose Approval queue governance and canonical approval model (P1-012)
 * @doc.layer product
 * @doc.pattern Approval Queue, Governance, Workflow
 */
public final class ApprovalQueueGovernanceService {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovalQueueGovernanceService.class);

    // SLA thresholds
    private static final int HIGH_PRIORITY_SLA_HOURS = 4;
    private static final int MEDIUM_PRIORITY_SLA_HOURS = 24;
    private static final int LOW_PRIORITY_SLA_HOURS = 72;
    private static final int ESCALATION_THRESHOLD_HOURS = 2; // Hours past SLA before escalation

    private final DataSource dataSource;
    private final ApprovalService approvalService;
    private final NotificationService notificationService;

    public ApprovalQueueGovernanceService(
            DataSource dataSource,
            ApprovalService approvalService,
            NotificationService notificationService) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
    }

    /**
     * P1-012: Submits approval request to the governed queue.
     *
     * <p>All approvals go through the canonical queue regardless of entry point.</p>
     *
     * @param ctx operation context
     * @param request the approval request to queue
     * @return promise resolving to queued approval
     */
    public Promise<ApprovalRequest> submitToQueue(DmOperationContext ctx, ApprovalRequest request) {
        String correlationId = ctx.getCorrelationId().getValue();
        MDC.put("correlationId", correlationId);
        MDC.put("approvalId", request.getId());

        LOG.info("[DMOS-APPROVAL-QUEUE] Submitting to queue: id={}, type={}, priority={}",
            request.getId(), request.getType(), request.getPriority());

        // P1-012: Enqueue with governance metadata
        return enqueueRequest(ctx, request)
            .then(queuedRequest -> {
                // Calculate SLA based on priority
                Instant slaDeadline = calculateSlaDeadline(request.getPriority());

                // Store queue metadata
                return storeQueueMetadata(ctx, queuedRequest.getId(), slaDeadline)
                    .then(v -> {
                        LOG.info("[DMOS-APPROVAL-QUEUE] Queued successfully: id={}, queuePosition={}",
                            queuedRequest.getId(), queuedRequest.getQueuePosition());
                        return Promise.of(queuedRequest);
                    });
            })
            .whenResult(r -> {
                // Notify approvers
                notifyApproversOfNewRequest(ctx, r);
            })
            .whenComplete(() -> MDC.clear());
    }

    /**
     * P1-012: Processes the next approval from the queue.
     *
     * <p>Used by the approval worker to process queue items.</p>
     *
     * @param ctx operation context
     * @return promise resolving to next approval, or empty if queue empty
     */
    public Promise<Optional<ApprovalRequest>> processNextFromQueue(DmOperationContext ctx) {
        LOG.debug("[DMOS-APPROVAL-QUEUE] Processing next item from queue");

        return fetchNextQueuedItem(ctx)
            .then(optionalRequest -> {
                if (optionalRequest.isEmpty()) {
                    LOG.debug("[DMOS-APPROVAL-QUEUE] Queue is empty");
                    return Promise.of(Optional.<ApprovalRequest>empty());
                }

                ApprovalRequest request = optionalRequest.get();

                // P1-012: Check if SLA breached
                return checkSlaStatus(ctx, request.getId())
                    .then(slaInfo -> {
                        if (slaInfo.isBreached()) {
                            LOG.warn("[DMOS-APPROVAL-QUEUE] SLA breached for: id={}, hoursOverdue={}",
                                request.getId(), slaInfo.hoursOverdue());

                            // Trigger escalation
                            return escalateRequest(ctx, request.getId(), slaInfo)
                                .then(v -> Promise.of(Optional.of(request)));
                        }

                        return Promise.of(Optional.of(request));
                    });
            });
    }

    /**
     * P1-012: Gets the approval queue for a tenant.
     *
     * @param tenantId the tenant
     * @param filters optional filters (priority, status, date range)
     * @return promise resolving to queue items
     */
    public Promise<List<QueuedApprovalItem>> getApprovalQueue(
            String tenantId,
            QueueFilters filters) {

        return executeInDb(conn -> {
            StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.type, a.entity_id, a.status, a.priority, a.created_at, " +
                "q.queue_position, q.sla_deadline, q.escalation_level " +
                "FROM dmos_approvals a " +
                "JOIN dmos_approval_queue q ON a.id = q.approval_id " +
                "WHERE a.tenant_id = ? AND a.status = ?"
            );

            List<Object> params = new ArrayList<>();
            params.add(tenantId);
            params.add(ApprovalStatus.PENDING.name());

            if (filters.priority() != null) {
                sql.append(" AND a.priority = ?");
                params.add(filters.priority().name());
            }

            sql.append(" ORDER BY q.queue_position ASC, a.priority DESC, a.created_at ASC");

            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }

                List<QueuedApprovalItem> items = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        items.add(new QueuedApprovalItem(
                            rs.getString("id"),
                            rs.getString("type"),
                            rs.getString("entity_id"),
                            ApprovalStatus.valueOf(rs.getString("status")),
                            ApprovalPriority.valueOf(rs.getString("priority")),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getInt("queue_position"),
                            rs.getTimestamp("sla_deadline").toInstant(),
                            rs.getInt("escalation_level")
                        ));
                    }
                }
                return items;
            }
        });
    }

    /**
     * P1-012: Reorders the queue based on priority and business rules.
     *
     * <p>Should be called periodically or when priorities change.</p>
     *
     * @param tenantId the tenant
     * @return promise resolving when reordering complete
     */
    public Promise<Void> reorderQueue(String tenantId) {
        LOG.info("[DMOS-APPROVAL-QUEUE] Reordering queue for tenant: {}", tenantId);

        return executeInDb(conn -> {
            String sql =
                "WITH ranked AS (" +
                "  SELECT q.id, " +
                "    ROW_NUMBER() OVER (" +
                "      ORDER BY " +
                "        CASE a.priority " +
                "          WHEN 'CRITICAL' THEN 1 " +
                "          WHEN 'HIGH' THEN 2 " +
                "          WHEN 'MEDIUM' THEN 3 " +
                "          WHEN 'LOW' THEN 4 " +
                "          ELSE 5 " +
                "        END, " +
                "        a.created_at ASC" +
                "    ) as new_position " +
                "  FROM dmos_approval_queue q " +
                "  JOIN dmos_approvals a ON q.approval_id = a.id " +
                "  WHERE a.tenant_id = ? AND a.status = 'PENDING'" +
                ") " +
                "UPDATE dmos_approval_queue " +
                "SET queue_position = ranked.new_position " +
                "FROM ranked " +
                "WHERE dmos_approval_queue.id = ranked.id";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tenantId);
                int updated = stmt.executeUpdate();
                LOG.info("[DMOS-APPROVAL-QUEUE] Reordered {} items", updated);
                return null;
            }
        });
    }

    /**
     * P1-012: Performs bulk approval of multiple items.
     *
     * <p>All items must be eligible for approval by the current user.</p>
     *
     * @param ctx operation context
     * @param approvalIds list of approval IDs to approve
     * @param comment approval comment
     * @return promise resolving to bulk operation result
     */
    public Promise<BulkApprovalResult> bulkApprove(
            DmOperationContext ctx,
            List<String> approvalIds,
            String comment) {

        LOG.info("[DMOS-APPROVAL-QUEUE] Bulk approving {} items", approvalIds.size());

        List<String> successful = new ArrayList<>();
        Map<String, String> failures = new HashMap<>();

        return Promise.all(
            approvalIds.stream()
                .map(id -> approvalService.approve(ctx, id, comment)
                    .whenResult(r -> successful.add(id))
                    .whenException(e -> failures.put(id, e.getMessage()))
                    .toVoid())
                .toArray(Promise[]::new)
        ).map(v -> {
            LOG.info("[DMOS-APPROVAL-QUEUE] Bulk approve complete: {} successful, {} failed",
                successful.size(), failures.size());
            return new BulkApprovalResult(successful, failures);
        });
    }

    /**
     * P1-012: Delegates approval to another user.
     *
     * @param ctx operation context
     * @param approvalId the approval to delegate
     * @param delegateToUserId the user to delegate to
     * @param reason delegation reason
     * @return promise resolving when complete
     */
    public Promise<Void> delegateApproval(
            DmOperationContext ctx,
            String approvalId,
            String delegateToUserId,
            String reason) {

        LOG.info("[DMOS-APPROVAL-QUEUE] Delegating approval {} to user {}",
            approvalId, delegateToUserId);

        return executeInDb(conn -> {
            String sql =
                "INSERT INTO dmos_approval_delegations (" +
                "  approval_id, from_user_id, to_user_id, reason, delegated_at" +
                ") VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, approvalId);
                stmt.setString(2, ctx.getActor().getPrincipalId());
                stmt.setString(3, delegateToUserId);
                stmt.setString(4, reason);
                stmt.setTimestamp(5, java.sql.Timestamp.from(Instant.now()));
                stmt.executeUpdate();

                // Update queue ownership
                String updateQueueSql =
                    "UPDATE dmos_approval_queue SET assigned_to = ? WHERE approval_id = ?";
                try (PreparedStatement updateStmt = conn.prepareStatement(updateQueueSql)) {
                    updateStmt.setString(1, delegateToUserId);
                    updateStmt.setString(2, approvalId);
                    updateStmt.executeUpdate();
                }

                return null;
            }
        }).whenResult(v -> {
            // Notify delegatee
            notificationService.notifyUser(delegateToUserId,
                "Approval Delegated",
                "You have been delegated an approval request: " + reason);
        });
    }

    // Helper methods

    private Promise<ApprovalRequest> enqueueRequest(DmOperationContext ctx, ApprovalRequest request) {
        return executeInDb(conn -> {
            // Get next queue position
            int nextPosition = getNextQueuePosition(conn, ctx.getTenantId().getValue());

            String sql =
                "INSERT INTO dmos_approval_queue (" +
                "  approval_id, tenant_id, queue_position, status, created_at" +
                ") VALUES (?, ?, ?, 'PENDING', ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, request.getId());
                stmt.setString(2, ctx.getTenantId().getValue());
                stmt.setInt(3, nextPosition);
                stmt.setTimestamp(4, java.sql.Timestamp.from(Instant.now()));
                stmt.executeUpdate();

                return request.withQueuePosition(nextPosition);
            }
        });
    }

    private int getNextQueuePosition(Connection conn, String tenantId) throws SQLException {
        String sql =
            "SELECT COALESCE(MAX(queue_position), 0) + 1 " +
            "FROM dmos_approval_queue q " +
            "JOIN dmos_approvals a ON q.approval_id = a.id " +
            "WHERE a.tenant_id = ? AND a.status = 'PENDING'";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tenantId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 1;
            }
        }
    }

    private Promise<Void> storeQueueMetadata(DmOperationContext ctx, String approvalId, Instant slaDeadline) {
        return executeInDb(conn -> {
            String sql =
                "UPDATE dmos_approval_queue SET sla_deadline = ? WHERE approval_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, java.sql.Timestamp.from(slaDeadline));
                stmt.setString(2, approvalId);
                stmt.executeUpdate();
                return null;
            }
        });
    }

    private Instant calculateSlaDeadline(ApprovalPriority priority) {
        int hours = switch (priority) {
            case CRITICAL -> 1;
            case HIGH -> HIGH_PRIORITY_SLA_HOURS;
            case MEDIUM -> MEDIUM_PRIORITY_SLA_HOURS;
            case LOW -> LOW_PRIORITY_SLA_HOURS;
        };

        return Instant.now().plusSeconds(hours * 3600L);
    }

    private Promise<Optional<ApprovalRequest>> fetchNextQueuedItem(DmOperationContext ctx) {
        return executeInDb(conn -> {
            String sql =
                "SELECT a.* " +
                "FROM dmos_approvals a " +
                "JOIN dmos_approval_queue q ON a.id = q.approval_id " +
                "WHERE a.tenant_id = ? AND a.status = 'PENDING' " +
                "ORDER BY q.queue_position ASC " +
                "LIMIT 1";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, ctx.getTenantId().getValue());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToApprovalRequest(rs));
                    }
                    return Optional.<ApprovalRequest>empty();
                }
            }
        });
    }

    private Promise<SlaInfo> checkSlaStatus(DmOperationContext ctx, String approvalId) {
        return executeInDb(conn -> {
            String sql =
                "SELECT sla_deadline, escalation_level " +
                "FROM dmos_approval_queue WHERE approval_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, approvalId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Instant slaDeadline = rs.getTimestamp("sla_deadline").toInstant();
                        int escalationLevel = rs.getInt("escalation_level");
                        boolean breached = Instant.now().isAfter(slaDeadline);
                        long hoursOverdue = breached
                            ? (Instant.now().getEpochSecond() - slaDeadline.getEpochSecond()) / 3600
                            : 0;

                        return new SlaInfo(breached, hoursOverdue, escalationLevel);
                    }
                    return new SlaInfo(false, 0, 0);
                }
            }
        });
    }

    private Promise<Void> escalateRequest(DmOperationContext ctx, String approvalId, SlaInfo slaInfo) {
        return executeInDb(conn -> {
            int newEscalationLevel = slaInfo.currentEscalationLevel() + 1;

            String sql =
                "UPDATE dmos_approval_queue " +
                "SET escalation_level = ?, escalated_at = ? " +
                "WHERE approval_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, newEscalationLevel);
                stmt.setTimestamp(2, java.sql.Timestamp.from(Instant.now()));
                stmt.setString(3, approvalId);
                stmt.executeUpdate();
                return null;
            }
        }).whenResult(v -> {
            // Send escalation notification
            notificationService.notifyEscalation(approvalId, slaInfo.hoursOverdue(), newEscalationLevel);
        });
    }

    private void notifyApproversOfNewRequest(DmOperationContext ctx, ApprovalRequest request) {
        notificationService.notifyApprovers(
            ctx.getTenantId().getValue(),
            "New Approval Request",
            String.format("A new %s approval request is pending (Priority: %s)",
                request.getType(), request.getPriority())
        );
    }

    private <T> Promise<T> executeInDb(DbOperation<T> operation) {
        return Promise.ofBlocking(null, () -> {
            try (Connection conn = dataSource.getConnection()) {
                return operation.execute(conn);
            } catch (SQLException e) {
                LOG.error("[DMOS-APPROVAL-QUEUE] Database operation failed", e);
                throw new RuntimeException("Database operation failed", e);
            }
        });
    }

    private ApprovalRequest mapResultSetToApprovalRequest(ResultSet rs) throws SQLException {
        // Map result set to domain object
        return new ApprovalRequest(
            rs.getString("id"),
            rs.getString("type"),
            rs.getString("entity_id"),
            ApprovalStatus.valueOf(rs.getString("status")),
            ApprovalPriority.valueOf(rs.getString("priority"))
        );
    }

    @FunctionalInterface
    private interface DbOperation<T> {
        T execute(Connection conn) throws SQLException;
    }

    // Records
    public record QueueFilters(ApprovalPriority priority) {}

    public record QueuedApprovalItem(
        String id,
        String type,
        String entityId,
        ApprovalStatus status,
        ApprovalPriority priority,
        Instant createdAt,
        int queuePosition,
        Instant slaDeadline,
        int escalationLevel
    ) {}

    public record BulkApprovalResult(
        List<String> successful,
        Map<String, String> failures
    ) {}

    private record SlaInfo(boolean isBreached, long hoursOverdue, int currentEscalationLevel) {}
}

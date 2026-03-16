package com.ghatana.appplatform.pms.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Maker-checker approval workflow for rebalancing orders. Shows current vs target
 *              allocation vs proposed orders with estimated transaction cost. Integrates with
 *              K-01 workflow engine via WorkflowPort for task assignment. Supports approve,
 *              reject, and modify actions. Satisfies STORY-D03-010.
 * @doc.layer   Domain
 * @doc.pattern Maker-checker; K-01 WorkflowPort task assignment; cost estimation;
 *              Counter for approve/reject/modify actions.
 */
public class RebalanceApprovalService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final WorkflowPort     workflowPort;
    private final Counter          approveCounter;
    private final Counter          rejectCounter;
    private final Counter          modifyCounter;

    public RebalanceApprovalService(HikariDataSource dataSource, Executor executor,
                                     WorkflowPort workflowPort, MeterRegistry registry) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.workflowPort   = workflowPort;
        this.approveCounter = Counter.builder("pms.rebalance.approval.approved").register(registry);
        this.rejectCounter  = Counter.builder("pms.rebalance.approval.rejected").register(registry);
        this.modifyCounter  = Counter.builder("pms.rebalance.approval.modified").register(registry);
    }

    // ─── Inner port ───────────────────────────────────────────────────────────

    /** K-01 workflow engine port for approval task lifecycle. */
    public interface WorkflowPort {
        String createApprovalTask(String requestId, String portfolioId, String assigneeRole);
        void completeTask(String taskId, String decision, String actorId, String comment);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record OrderLine(String instrumentId, BigDecimal currentWeight, BigDecimal targetWeight,
                            String side, BigDecimal qty, BigDecimal estimatedValue,
                            BigDecimal estimatedCost) {}

    public record ApprovalPackage(String requestId, String portfolioId, String status,
                                  String submittedBy, LocalDateTime submittedAt,
                                  BigDecimal totalEstimatedCost, List<OrderLine> orderLines,
                                  String taskId) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ApprovalPackage> getApprovalPackage(String requestId) {
        return Promise.ofBlocking(executor, () -> buildApprovalPackage(requestId));
    }

    public Promise<Void> approve(String requestId, String approverId, String comment) {
        return Promise.ofBlocking(executor, () -> {
            ApprovalRequest ar = loadApprovalRequest(requestId);
            if (ar.submittedBy().equals(approverId)) {
                throw new IllegalStateException("Approver cannot be same as submitter (maker-checker)");
            }
            workflowPort.completeTask(ar.taskId(), "APPROVED", approverId, comment);
            updateStatus(requestId, "APPROVED", approverId);
            approveCounter.increment();
            return null;
        });
    }

    public Promise<Void> reject(String requestId, String approverId, String reason) {
        return Promise.ofBlocking(executor, () -> {
            ApprovalRequest ar = loadApprovalRequest(requestId);
            workflowPort.completeTask(ar.taskId(), "REJECTED", approverId, reason);
            updateStatus(requestId, "REJECTED", approverId);
            rejectCounter.increment();
            return null;
        });
    }

    public Promise<Void> modifyAndResubmit(String requestId, String approverId,
                                            List<String> removedInstrumentIds, String comment) {
        return Promise.ofBlocking(executor, () -> {
            ApprovalRequest ar = loadApprovalRequest(requestId);
            workflowPort.completeTask(ar.taskId(), "MODIFIED", approverId, comment);
            removeOrderLines(requestId, removedInstrumentIds);
            String newTaskId = workflowPort.createApprovalTask(requestId, ar.portfolioId(), "PORTFOLIO_MANAGER");
            updateTaskId(requestId, newTaskId, "PENDING_APPROVAL");
            modifyCounter.increment();
            return null;
        });
    }

    // ─── Package builder ─────────────────────────────────────────────────────

    private ApprovalPackage buildApprovalPackage(String requestId) throws SQLException {
        String sql = """
                SELECT r.portfolio_id, r.status, r.submitted_by, r.submitted_at, r.task_id,
                       rl.instrument_id, rl.current_weight, rl.target_weight, rl.side,
                       rl.qty, rl.estimated_value, rl.estimated_cost
                FROM rebalance_requests r
                JOIN rebalance_order_lines rl ON rl.request_id = r.request_id
                WHERE r.request_id = ?
                ORDER BY rl.side DESC, rl.estimated_value DESC
                """;
        List<OrderLine> lines = new ArrayList<>();
        String portfolioId = null, status = null, submittedBy = null;
        LocalDateTime submittedAt = null;
        String taskId = null;
        BigDecimal totalCost = BigDecimal.ZERO;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (portfolioId == null) {
                        portfolioId  = rs.getString("portfolio_id");
                        status       = rs.getString("status");
                        submittedBy  = rs.getString("submitted_by");
                        submittedAt  = rs.getTimestamp("submitted_at").toLocalDateTime();
                        taskId       = rs.getString("task_id");
                    }
                    BigDecimal cost = rs.getBigDecimal("estimated_cost");
                    totalCost = totalCost.add(cost != null ? cost : BigDecimal.ZERO);
                    lines.add(new OrderLine(rs.getString("instrument_id"),
                            rs.getBigDecimal("current_weight"), rs.getBigDecimal("target_weight"),
                            rs.getString("side"), rs.getBigDecimal("qty"),
                            rs.getBigDecimal("estimated_value"), cost));
                }
            }
        }
        if (portfolioId == null) throw new IllegalArgumentException("Request not found: " + requestId);
        return new ApprovalPackage(requestId, portfolioId, status, submittedBy,
                submittedAt, totalCost, lines, taskId);
    }

    private record ApprovalRequest(String portfolioId, String submittedBy, String taskId) {}

    private ApprovalRequest loadApprovalRequest(String requestId) throws SQLException {
        String sql = "SELECT portfolio_id, submitted_by, task_id FROM rebalance_requests WHERE request_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Request not found: " + requestId);
                return new ApprovalRequest(rs.getString("portfolio_id"),
                        rs.getString("submitted_by"), rs.getString("task_id"));
            }
        }
    }

    private void updateStatus(String requestId, String status, String actorId) throws SQLException {
        String sql = "UPDATE rebalance_requests SET status = ?, approved_by = ?, actioned_at = NOW() WHERE request_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, actorId);
            ps.setString(3, requestId);
            ps.executeUpdate();
        }
    }

    private void updateTaskId(String requestId, String taskId, String status) throws SQLException {
        String sql = "UPDATE rebalance_requests SET task_id = ?, status = ? WHERE request_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            ps.setString(2, status);
            ps.setString(3, requestId);
            ps.executeUpdate();
        }
    }

    private void removeOrderLines(String requestId, List<String> instrumentIds) throws SQLException {
        if (instrumentIds == null || instrumentIds.isEmpty()) return;
        String sql = "DELETE FROM rebalance_order_lines WHERE request_id = ? AND instrument_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String id : instrumentIds) {
                ps.setString(1, requestId);
                ps.setString(2, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}

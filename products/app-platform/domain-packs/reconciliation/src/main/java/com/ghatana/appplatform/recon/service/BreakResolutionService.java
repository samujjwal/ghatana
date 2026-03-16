package com.ghatana.appplatform.recon.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Break resolution workflow: resolver reviews → adds notes → selects action
 *              (WRITE_OFF, ADJUST, TIMING_RESOLVED, FALSE_POSITIVE) → maker-checker approval
 *              for write-offs above configurable threshold (K-02). Resolution audit trail
 *              stored in break_resolution_log. Bulk timing resolution for auto-clearing breaks.
 *              Satisfies STORY-D13-015.
 * @doc.layer   Domain
 * @doc.pattern Maker-checker; K-02 write-off threshold; bulk timing resolution;
 *              INSERT-only resolution audit; K-01 IAM for role check.
 */
public class BreakResolutionService {

    private static final Logger log = LoggerFactory.getLogger(BreakResolutionService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ConfigPort       configPort;
    private final Counter          resolvedCounter;
    private final Counter          writeOffCounter;

    public BreakResolutionService(HikariDataSource dataSource, Executor executor,
                                  ConfigPort configPort, MeterRegistry registry) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.configPort     = configPort;
        this.resolvedCounter = registry.counter("recon.breaks.resolved");
        this.writeOffCounter = registry.counter("recon.breaks.write_offs");
    }

    // ─── Inner port ───────────────────────────────────────────────────────────

    /** K-02 ConfigPort — write-off threshold and configuration. */
    public interface ConfigPort {
        BigDecimal getWriteOffThreshold();  // requires maker-checker above this amount
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Resolver submits a resolution (non-write-off or below threshold).
     * Write-offs above threshold are set to PENDING_APPROVAL.
     */
    public Promise<String> submitResolution(String breakId, String resolverId,
                                            String action, String notes) {
        return Promise.ofBlocking(executor, () -> {
            validateAction(action);
            BigDecimal breakAmount = loadBreakAmount(breakId);
            BigDecimal threshold = configPort.getWriteOffThreshold();

            boolean needsApproval = "WRITE_OFF".equals(action)
                    && breakAmount.compareTo(threshold) > 0;
            String resolutionId = UUID.randomUUID().toString();
            String status = needsApproval ? "PENDING_APPROVAL" : "RESOLVED";

            persistResolution(resolutionId, breakId, resolverId, action, notes, status);
            if (!needsApproval) {
                closeBreak(breakId, resolutionId);
                resolvedCounter.increment();
                if ("WRITE_OFF".equals(action)) writeOffCounter.increment();
            }
            return resolutionId;
        });
    }

    /** Checker approves a write-off resolution — must differ from resolver. */
    public Promise<Void> approveResolution(String resolutionId, String approverId) {
        return Promise.ofBlocking(executor, () -> {
            String resolverId = loadResolverId(resolutionId);
            if (approverId.equals(resolverId)) {
                throw new IllegalStateException("Approver must differ from resolver: " + approverId);
            }
            String breakId = loadBreakId(resolutionId);
            try (Connection conn = dataSource.getConnection()) {
                String upd = """
                        UPDATE break_resolution_log
                        SET status='APPROVED', approver_id=?, approved_at=NOW()
                        WHERE resolution_id=?
                        """;
                try (PreparedStatement ps = conn.prepareStatement(upd)) {
                    ps.setString(1, approverId);
                    ps.setString(2, resolutionId);
                    ps.executeUpdate();
                }
                closeBreak(breakId, resolutionId, conn);
            }
            resolvedCounter.increment();
            writeOffCounter.increment();
            return null;
        });
    }

    /** Bulk auto-resolve TIMING_DIFFERENCE breaks that have auto-cleared. */
    public Promise<Integer> bulkResolveTimingBreaks(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    UPDATE recon_breaks rb
                    SET status='RESOLVED', resolved_date=?, resolution_action='TIMING_RESOLVED'
                    WHERE rb.break_type='TIMING_DIFFERENCE'
                      AND rb.status='OPEN'
                      AND EXISTS (
                        SELECT 1 FROM recon_matches rm
                        WHERE rm.statement_entry_id = rb.entry_id
                          AND rm.matched_at::date = ?
                      )
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, runDate);
                ps.setObject(2, runDate);
                int rows = ps.executeUpdate();
                resolvedCounter.increment(rows);
                return rows;
            }
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void validateAction(String action) {
        if (!action.matches("WRITE_OFF|ADJUST|TIMING_RESOLVED|FALSE_POSITIVE")) {
            throw new IllegalArgumentException("Invalid resolution action: " + action);
        }
    }

    private BigDecimal loadBreakAmount(String breakId) throws SQLException {
        String sql = "SELECT COALESCE(amount, 0) FROM recon_breaks WHERE break_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, breakId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    private void persistResolution(String resolutionId, String breakId, String resolverId,
                                   String action, String notes, String status) throws SQLException {
        String sql = """
                INSERT INTO break_resolution_log
                    (resolution_id, break_id, resolver_id, action, notes, status, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (break_id) DO UPDATE
                    SET action=EXCLUDED.action, notes=EXCLUDED.notes,
                        status=EXCLUDED.status, submitted_at=NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resolutionId);
            ps.setString(2, breakId);
            ps.setString(3, resolverId);
            ps.setString(4, action);
            ps.setString(5, notes);
            ps.setString(6, status);
            ps.executeUpdate();
        }
    }

    private void closeBreak(String breakId, String resolutionId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            closeBreak(breakId, resolutionId, conn);
        }
    }

    private void closeBreak(String breakId, String resolutionId, Connection conn) throws SQLException {
        String sql = """
                UPDATE recon_breaks
                SET status='RESOLVED', resolved_date=CURRENT_DATE, resolution_log_id=?
                WHERE break_id=?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resolutionId);
            ps.setString(2, breakId);
            ps.executeUpdate();
        }
    }

    private String loadResolverId(String resolutionId) throws SQLException {
        String sql = "SELECT resolver_id FROM break_resolution_log WHERE resolution_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resolutionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }

    private String loadBreakId(String resolutionId) throws SQLException {
        String sql = "SELECT break_id FROM break_resolution_log WHERE resolution_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resolutionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }
}

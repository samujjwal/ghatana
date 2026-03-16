package com.ghatana.appplatform.corporateactions.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Manages corporate action (CA) lifecycle: ANNOUNCED → EX_DATE → RECORD_DATE →
 *              PAYMENT_DATE → COMPLETED. Supports CASH_DIVIDEND, STOCK_DIVIDEND, BONUS,
 *              RIGHTS, SPLIT, and MERGER action types. Dual BS/AD calendar dates via K-15.
 *              Maker-checker enforced via K-01 WorkflowPort before activation.
 *              Fires CorporateActionAnnounced event on publish. Satisfies STORY-D12-001.
 * @doc.layer   Domain
 * @doc.pattern CA lifecycle; K-15 dual calendar; K-01 maker-checker; event publish; Counter.
 */
public class CorporateActionLifecycleService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final CalendarPort     calendarPort;
    private final WorkflowPort     workflowPort;
    private final EventPort        eventPort;
    private final Counter          announcedCounter;
    private final Counter          completedCounter;

    public CorporateActionLifecycleService(HikariDataSource dataSource, Executor executor,
                                            CalendarPort calendarPort, WorkflowPort workflowPort,
                                            EventPort eventPort, MeterRegistry registry) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.calendarPort      = calendarPort;
        this.workflowPort      = workflowPort;
        this.eventPort         = eventPort;
        this.announcedCounter  = Counter.builder("ca.lifecycle.announced_total").register(registry);
        this.completedCounter  = Counter.builder("ca.lifecycle.completed_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface CalendarPort {
        String toNepaliDate(LocalDate adDate);
    }

    public interface WorkflowPort {
        String createApprovalTask(String entityId, String entityType, String requestedBy);
        void approveTask(String taskId, String approverId);
    }

    public interface EventPort {
        void publish(String topic, Object payload);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum CaType   { CASH_DIVIDEND, STOCK_DIVIDEND, BONUS, RIGHTS, SPLIT, MERGER }
    public enum CaStatus { ANNOUNCED, EX_DATE, RECORD_DATE, PAYMENT_DATE, COMPLETED }

    public record CorporateAction(String caId, String issuerId, CaType caType, CaStatus status,
                                   LocalDate announcedDate, LocalDate exDate, LocalDate recordDate,
                                   LocalDate paymentDate, String exDateBs, String recordDateBs,
                                   String paymentDateBs, double ratio, String currency,
                                   String createdBy, LocalDateTime createdAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<CorporateAction> announce(String issuerId, CaType caType, LocalDate exDate,
                                              LocalDate recordDate, LocalDate paymentDate,
                                              double ratio, String currency, String createdBy) {
        return Promise.ofBlocking(executor, () -> {
            String caId = UUID.randomUUID().toString();
            CorporateAction ca = persistCa(caId, issuerId, caType, exDate, recordDate,
                    paymentDate, ratio, currency, createdBy);
            workflowPort.createApprovalTask(caId, "CORPORATE_ACTION", createdBy);
            announcedCounter.increment();
            return ca;
        });
    }

    public Promise<CorporateAction> activate(String caId, String taskId, String approverId) {
        return Promise.ofBlocking(executor, () -> {
            workflowPort.approveTask(taskId, approverId);
            updateStatus(caId, CaStatus.ANNOUNCED);
            CorporateAction ca = loadCa(caId);
            eventPort.publish("ca.lifecycle.announced", ca);
            return ca;
        });
    }

    public Promise<CorporateAction> advanceStatus(String caId) {
        return Promise.ofBlocking(executor, () -> {
            CorporateAction ca = loadCa(caId);
            CaStatus next = switch (ca.status()) {
                case ANNOUNCED   -> CaStatus.EX_DATE;
                case EX_DATE     -> CaStatus.RECORD_DATE;
                case RECORD_DATE -> CaStatus.PAYMENT_DATE;
                case PAYMENT_DATE -> CaStatus.COMPLETED;
                case COMPLETED   -> throw new IllegalStateException("Already completed");
            };
            updateStatus(caId, next);
            if (next == CaStatus.COMPLETED) completedCounter.increment();
            return loadCa(caId);
        });
    }

    public Promise<List<CorporateAction>> listByStatus(CaStatus status) {
        return Promise.ofBlocking(executor, () -> loadByStatus(status));
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private CorporateAction persistCa(String caId, String issuerId, CaType caType,
                                       LocalDate exDate, LocalDate recordDate, LocalDate paymentDate,
                                       double ratio, String currency, String createdBy) throws SQLException {
        String exBs      = calendarPort.toNepaliDate(exDate);
        String recordBs  = calendarPort.toNepaliDate(recordDate);
        String payBs     = calendarPort.toNepaliDate(paymentDate);
        String sql = """
                INSERT INTO corporate_actions
                    (ca_id, issuer_id, ca_type, status, announced_date, ex_date, record_date,
                     payment_date, ex_date_bs, record_date_bs, payment_date_bs, ratio, currency,
                     created_by, created_at)
                VALUES (?, ?, ?, 'ANNOUNCED', CURRENT_DATE, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            ps.setString(2, issuerId);
            ps.setString(3, caType.name());
            ps.setObject(4, exDate);
            ps.setObject(5, recordDate);
            ps.setObject(6, paymentDate);
            ps.setString(7, exBs);
            ps.setString(8, recordBs);
            ps.setString(9, payBs);
            ps.setDouble(10, ratio);
            ps.setString(11, currency);
            ps.setString(12, createdBy);
            ps.executeUpdate();
        }
        return loadCa(caId);
    }

    private void updateStatus(String caId, CaStatus status) throws SQLException {
        String sql = "UPDATE corporate_actions SET status=? WHERE ca_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, caId);
            ps.executeUpdate();
        }
    }

    private CorporateAction loadCa(String caId) throws SQLException {
        String sql = "SELECT * FROM corporate_actions WHERE ca_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("CA not found: " + caId);
                return mapCa(rs);
            }
        }
    }

    private List<CorporateAction> loadByStatus(CaStatus status) throws SQLException {
        String sql = "SELECT * FROM corporate_actions WHERE status=? ORDER BY payment_date";
        List<CorporateAction> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapCa(rs));
            }
        }
        return result;
    }

    private CorporateAction mapCa(ResultSet rs) throws SQLException {
        return new CorporateAction(rs.getString("ca_id"), rs.getString("issuer_id"),
                CaType.valueOf(rs.getString("ca_type")), CaStatus.valueOf(rs.getString("status")),
                rs.getObject("announced_date", LocalDate.class), rs.getObject("ex_date", LocalDate.class),
                rs.getObject("record_date", LocalDate.class), rs.getObject("payment_date", LocalDate.class),
                rs.getString("ex_date_bs"), rs.getString("record_date_bs"), rs.getString("payment_date_bs"),
                rs.getDouble("ratio"), rs.getString("currency"),
                rs.getString("created_by"), rs.getObject("created_at", LocalDateTime.class));
    }
}

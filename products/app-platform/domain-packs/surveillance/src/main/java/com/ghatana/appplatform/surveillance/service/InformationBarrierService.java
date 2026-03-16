package com.ghatana.appplatform.surveillance.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Monitor information barriers (Chinese walls): detect breaches where restricted
 *              information may have crossed. Tracks: restricted list additions (D-07), employee
 *              trading activity, MNPI event correlation. Alerts when employee trades in an
 *              instrument within configurable window after it appears on restricted list or
 *              after a Material Non-Public Information (MNPI) event. Satisfies STORY-D08-008.
 * @doc.layer   Domain
 * @doc.pattern Restricted list time correlation; MNPI event detection; D-07 integration;
 *              K-01 WorkflowPort for breach investigation task; Counter for breach alerts.
 */
public class InformationBarrierService {

    private static final int DEFAULT_WINDOW_DAYS = 5;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ConfigPort       configPort;
    private final AlertPort        alertPort;
    private final Counter          alertCounter;

    public InformationBarrierService(HikariDataSource dataSource, Executor executor,
                                      ConfigPort configPort, AlertPort alertPort,
                                      MeterRegistry registry) {
        this.dataSource   = dataSource;
        this.executor     = executor;
        this.configPort   = configPort;
        this.alertPort    = alertPort;
        this.alertCounter = Counter.builder("surveillance.info_barrier.alerts_total").register(registry);
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    public interface ConfigPort {
        int getBarrierBreachWindowDays();
    }

    public interface AlertPort {
        String persistAlert(String clientId, String instrumentId, String alertType,
                            String description, String severity, LocalDate runDate);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record BarrierBreachAlert(String alertId, String employeeId, String instrumentId,
                                     LocalDate restrictedAdded, LocalDate employeeTradeDate,
                                     int daysBetween, String triggerType, String severity) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<BarrierBreachAlert>> detect(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> detectBreaches(runDate));
    }

    // ─── Detection logic ─────────────────────────────────────────────────────

    private List<BarrierBreachAlert> detectBreaches(LocalDate runDate) throws SQLException {
        int windowDays = configPort.getBarrierBreachWindowDays();
        if (windowDays == 0) windowDays = DEFAULT_WINDOW_DAYS;

        List<BarrierBreachAlert> alerts = new ArrayList<>();
        alerts.addAll(detectRestrictedListBreaches(runDate, windowDays));
        alerts.addAll(detectMnpiBreaches(runDate, windowDays));
        return alerts;
    }

    /** Alert if employee trades instrument that was added to restricted list in últimos N days. */
    private List<BarrierBreachAlert> detectRestrictedListBreaches(LocalDate runDate, int windowDays)
            throws SQLException {
        List<BarrierBreachAlert> alerts = new ArrayList<>();
        String sql = """
                SELECT et.employee_id, et.instrument_id,
                       rl.added_date AS restricted_added,
                       DATE(et.trade_at) AS trade_date,
                       DATE(et.trade_at) - rl.added_date AS days_after_restriction
                FROM employee_personal_trades et
                JOIN restricted_instruments rl ON rl.instrument_id = et.instrument_id
                    AND rl.added_date BETWEEN ? - (? || ' days')::interval AND ?
                    AND (rl.removed_date IS NULL OR rl.removed_date > DATE(et.trade_at))
                WHERE DATE(et.trade_at) = ? AND et.status = 'EXECUTED'
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            ps.setInt(2, windowDays);
            ps.setObject(3, runDate);
            ps.setObject(4, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int days    = rs.getInt("days_after_restriction");
                    String sev  = days <= 1 ? "CRITICAL" : days <= 3 ? "HIGH" : "MEDIUM";
                    String desc = "Employee=%s traded restricted instrument=%s, %d day(s) after restriction"
                            .formatted(rs.getString("employee_id"), rs.getString("instrument_id"), days);
                    String alertId = alertPort.persistAlert(rs.getString("employee_id"),
                            rs.getString("instrument_id"), "INFO_BARRIER_BREACH", desc, sev, runDate);
                    alerts.add(new BarrierBreachAlert(alertId, rs.getString("employee_id"),
                            rs.getString("instrument_id"),
                            rs.getObject("restricted_added", LocalDate.class),
                            rs.getObject("trade_date", LocalDate.class), days,
                            "RESTRICTED_LIST", sev));
                    alertCounter.increment();
                }
            }
        }
        return alerts;
    }

    /** Alert if employee trades instrument within N days of a registered MNPI event. */
    private List<BarrierBreachAlert> detectMnpiBreaches(LocalDate runDate, int windowDays)
            throws SQLException {
        List<BarrierBreachAlert> alerts = new ArrayList<>();
        String sql = """
                SELECT et.employee_id, et.instrument_id, me.event_date,
                       DATE(et.trade_at) AS trade_date,
                       DATE(et.trade_at) - me.event_date AS days_after_mnpi
                FROM employee_personal_trades et
                JOIN mnpi_events me ON me.instrument_id = et.instrument_id
                    AND me.event_date BETWEEN ? - (? || ' days')::interval AND ?
                WHERE DATE(et.trade_at) = ? AND et.status = 'EXECUTED'
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            ps.setInt(2, windowDays);
            ps.setObject(3, runDate);
            ps.setObject(4, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int days   = rs.getInt("days_after_mnpi");
                    String sev = "HIGH";
                    String desc = "Employee=%s traded instrument=%s within %d day(s) of MNPI event"
                            .formatted(rs.getString("employee_id"), rs.getString("instrument_id"), days);
                    String alertId = alertPort.persistAlert(rs.getString("employee_id"),
                            rs.getString("instrument_id"), "MNPI_BREACH", desc, sev, runDate);
                    alerts.add(new BarrierBreachAlert(alertId, rs.getString("employee_id"),
                            rs.getString("instrument_id"),
                            rs.getObject("event_date", LocalDate.class),
                            rs.getObject("trade_date", LocalDate.class), days,
                            "MNPI_EVENT", sev));
                    alertCounter.increment();
                }
            }
        }
        return alerts;
    }
}

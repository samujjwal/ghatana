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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Detect front-running: employee/broker trades ahead of known client orders.
 *              Pattern: employee buys → client large buy order → price rises → employee sells.
 *              Requires cross-referencing employee personal accounts vs client order flow.
 *              Configurable time-window via ConfigPort. K-05 linked to employee account registry.
 *              D-01 order management integration. Satisfies STORY-D08-007.
 * @doc.layer   Domain
 * @doc.pattern Cross-account correlation; temporal pattern matching; K-02 time window;
 *              FrontRunningAlert with trade pair; Counter for alerts.
 */
public class FrontRunningDetectionService {

    private static final int DEFAULT_WINDOW_MINUTES = 30;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ConfigPort       configPort;
    private final AlertPort        alertPort;
    private final Counter          alertCounter;

    public FrontRunningDetectionService(HikariDataSource dataSource, Executor executor,
                                         ConfigPort configPort, AlertPort alertPort,
                                         MeterRegistry registry) {
        this.dataSource   = dataSource;
        this.executor     = executor;
        this.configPort   = configPort;
        this.alertPort    = alertPort;
        this.alertCounter = Counter.builder("surveillance.front_running.alerts_total").register(registry);
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    public interface ConfigPort {
        int getWindowMinutes();
        double getClientOrderSizeThreshold(); // min qty to qualify as "large" order
    }

    public interface AlertPort {
        String persistAlert(String clientId, String instrumentId, String alertType,
                            String description, String severity, LocalDate runDate);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record FrontRunningAlert(String alertId, String employeeId, String clientId,
                                    String instrumentId, LocalDate runDate,
                                    LocalDateTime employeeTradeAt, LocalDateTime clientOrderAt,
                                    double minutesBetween, String employeeSide, long employeeQty,
                                    long clientOrderQty, double priceMoveAfterClient) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<FrontRunningAlert>> detect(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> runDetection(runDate));
    }

    // ─── Detection logic ─────────────────────────────────────────────────────

    private List<FrontRunningAlert> runDetection(LocalDate runDate) throws SQLException {
        List<FrontRunningAlert> alerts = new ArrayList<>();
        int windowMins    = configPort.getWindowMinutes();
        if (windowMins == 0) windowMins = DEFAULT_WINDOW_MINUTES;
        double sizeThresh = configPort.getClientOrderSizeThreshold();

        List<PotentialPattern> patterns = loadCandidatePatterns(runDate, windowMins, sizeThresh);
        for (PotentialPattern p : patterns) {
            double priceMove = loadPriceMove(p.instrumentId(), p.clientOrderAt(), runDate);
            // Front-running signature: employee traded on same side as client, price moved favorably
            boolean priceMoveSignificant = Math.abs(priceMove) > 0.005; // >0.5% move
            if (priceMoveSignificant) {
                String desc = "Employee=%s trade at %s, client large order at %s (%d mins later), price move=%.2f%%"
                        .formatted(p.employeeId(), p.employeeTradeAt(), p.clientOrderAt(),
                                (long) p.minutesBetween(), priceMove * 100);
                String alertId = alertPort.persistAlert(p.clientId(), p.instrumentId(),
                        "FRONT_RUNNING", desc, "HIGH", runDate);
                alerts.add(new FrontRunningAlert(alertId, p.employeeId(), p.clientId(),
                        p.instrumentId(), runDate, p.employeeTradeAt(), p.clientOrderAt(),
                        p.minutesBetween(), p.employeeSide(), p.employeeQty(), p.clientOrderQty(),
                        priceMove));
                alertCounter.increment();
            }
        }
        return alerts;
    }

    private record PotentialPattern(String employeeId, String clientId, String instrumentId,
                                    LocalDateTime employeeTradeAt, LocalDateTime clientOrderAt,
                                    double minutesBetween, String employeeSide,
                                    long employeeQty, long clientOrderQty) {}

    private List<PotentialPattern> loadCandidatePatterns(LocalDate runDate, int windowMins,
                                                           double sizeThresh) throws SQLException {
        List<PotentialPattern> list = new ArrayList<>();
        // Query: employee personal account trade, followed by large client order in same instrument
        // within configurable window, on same direction
        String sql = """
                SELECT et.employee_id, co.client_id, et.instrument_id,
                       et.trade_at AS employee_at, co.submitted_at AS client_at,
                       EXTRACT(EPOCH FROM (co.submitted_at - et.trade_at)) / 60.0 AS minutes_between,
                       et.side AS employee_side, et.quantity AS emp_qty, co.quantity AS client_qty
                FROM employee_personal_trades et
                JOIN orders co ON co.instrument_id = et.instrument_id
                    AND co.side = et.side
                    AND co.submitted_at > et.trade_at
                    AND co.submitted_at <= et.trade_at + (? || ' minutes')::interval
                    AND co.quantity >= ?
                WHERE DATE(et.trade_at) = ?
                  AND et.status = 'EXECUTED'
                ORDER BY et.trade_at
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, windowMins);
            ps.setDouble(2, sizeThresh);
            ps.setObject(3, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PotentialPattern(rs.getString("employee_id"),
                            rs.getString("client_id"), rs.getString("instrument_id"),
                            rs.getTimestamp("employee_at").toLocalDateTime(),
                            rs.getTimestamp("client_at").toLocalDateTime(),
                            rs.getDouble("minutes_between"), rs.getString("employee_side"),
                            rs.getLong("emp_qty"), rs.getLong("client_qty")));
                }
            }
        }
        return list;
    }

    private double loadPriceMove(String instrumentId, LocalDateTime fromTime, LocalDate runDate)
            throws SQLException {
        // Compare price immediately after client order vs price at employee trade time
        String sql = """
                SELECT (p2.price - p1.price) / NULLIF(p1.price, 0) AS price_move
                FROM instrument_prices_intraday p1, instrument_prices_intraday p2
                WHERE p1.instrument_id = ? AND p2.instrument_id = ?
                  AND p1.price_at <= ? AND p2.price_at >= ?
                  AND DATE(p2.price_at) = ?
                ORDER BY p1.price_at DESC, p2.price_at ASC LIMIT 1
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            ps.setString(2, instrumentId);
            ps.setObject(3, fromTime);
            ps.setObject(4, fromTime);
            ps.setObject(5, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("price_move");
            }
        }
        return 0.0;
    }
}

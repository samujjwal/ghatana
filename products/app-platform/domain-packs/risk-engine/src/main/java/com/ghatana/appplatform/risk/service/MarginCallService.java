package com.ghatana.appplatform.risk.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Generates margin call records when a maintenance margin deficit is detected,
 *              storing call details and emitting MarginCallIssued events (D06-012).
 * @doc.layer   Domain — risk engine
 * @doc.pattern Event-driven — triggered by MaintenanceMarginService deficit events
 */
public class MarginCallService {

    public enum MarginCallStatus { ISSUED, MET, BREACHED, LIQUIDATING }

    public record MarginCall(
        String callId,
        String clientId,
        BigDecimal deficitAmount,
        BigDecimal requiredDeposit,   // deficit + 10% buffer
        LocalDate deadline,           // T+1 by default
        String deadlineBs,
        MarginCallStatus status,
        Instant issuedAt
    ) {}

    public record MarginCallIssuedEvent(String callId, String clientId, BigDecimal deficit, LocalDate deadline) {}

    private static final BigDecimal BUFFER_RATE = new BigDecimal("0.10");
    private static final int DEFAULT_DEADLINE_DAYS = 1;

    private final DataSource dataSource;
    private final Executor executor;
    private final Consumer<MarginCallIssuedEvent> eventPublisher;
    private final Counter callsIssuedCounter;
    private final Counter callsMetCounter;
    private final Counter callsBreachedCounter;

    public MarginCallService(DataSource dataSource, Executor executor,
                              Consumer<MarginCallIssuedEvent> eventPublisher,
                              MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
        this.callsIssuedCounter  = Counter.builder("risk.margin.calls_issued_total").register(registry);
        this.callsMetCounter     = Counter.builder("risk.margin.calls_met_total").register(registry);
        this.callsBreachedCounter= Counter.builder("risk.margin.calls_breached_total").register(registry);
    }

    /** Create a margin call when a deficit is detected. Idempotent — skips if open call exists. */
    public Promise<MarginCall> issueCall(String clientId, BigDecimal deficitAmount) {
        return Promise.ofBlocking(executor, () -> {
            // Idempotency: one open call per client
            if (hasOpenCall(clientId)) {
                return loadOpenCall(clientId);
            }
            BigDecimal required = deficitAmount.multiply(BigDecimal.ONE.add(BUFFER_RATE));
            LocalDate deadline = LocalDate.now().plusDays(DEFAULT_DEADLINE_DAYS);
            String callId = UUID.randomUUID().toString();

            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO margin_calls(id, client_id, deficit_amount, required_deposit, " +
                     "deadline, status, issued_at) VALUES(?,?,?,?,?,'ISSUED',NOW())")) {
                ps.setObject(1, UUID.fromString(callId));
                ps.setObject(2, UUID.fromString(clientId));
                ps.setBigDecimal(3, deficitAmount);
                ps.setBigDecimal(4, required);
                ps.setObject(5, deadline);
                ps.executeUpdate();
            }
            callsIssuedCounter.increment();
            MarginCall call = new MarginCall(callId, clientId, deficitAmount, required,
                deadline, null, MarginCallStatus.ISSUED, Instant.now());
            eventPublisher.accept(new MarginCallIssuedEvent(callId, clientId, deficitAmount, deadline));
            return call;
        });
    }

    /** Mark a margin call as MET when sufficient collateral is posted. */
    public Promise<Void> markMet(String callId, String resolvedBy) {
        return Promise.ofBlocking(executor, () -> {
            updateStatus(callId, "MET");
            callsMetCounter.increment();
            return null;
        });
    }

    /** Mark a margin call as BREACHED when deadline passes unpaid. */
    public Promise<Void> markBreached(String callId) {
        return Promise.ofBlocking(executor, () -> {
            updateStatus(callId, "BREACHED");
            callsBreachedCounter.increment();
            return null;
        });
    }

    public Promise<MarginCallDashboardService.MarginDashboard> getDashboard() {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FILTER (WHERE status='ISSUED') AS open_calls, " +
                     "COALESCE(SUM(deficit_amount) FILTER (WHERE status='ISSUED'), 0) AS total_deficit, " +
                     "COALESCE(AVG(EXTRACT(EPOCH FROM (NOW()-issued_at))/86400) FILTER (WHERE status='ISSUED'), 0) AS avg_age_days, " +
                     "COUNT(*) FILTER (WHERE status='ISSUED' AND deadline < CURRENT_DATE) AS overdue_count " +
                     "FROM margin_calls")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new MarginCallDashboardService.MarginDashboard(
                            rs.getInt("open_calls"),
                            rs.getBigDecimal("total_deficit"),
                            rs.getDouble("avg_age_days"),
                            rs.getInt("overdue_count"));
                    }
                }
            }
            return new MarginCallDashboardService.MarginDashboard(0, BigDecimal.ZERO, 0.0, 0);
        });
    }

    private boolean hasOpenCall(String clientId) throws Exception {
        String sql = "SELECT 1 FROM margin_calls WHERE client_id = ? AND status = 'ISSUED' LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(clientId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private MarginCall loadOpenCall(String clientId) throws Exception {
        String sql = "SELECT id, deficit_amount, required_deposit, deadline, issued_at " +
                     "FROM margin_calls WHERE client_id = ? AND status = 'ISSUED' LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(clientId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new MarginCall(rs.getString("id"), clientId,
                        rs.getBigDecimal("deficit_amount"), rs.getBigDecimal("required_deposit"),
                        rs.getDate("deadline").toLocalDate(), null, MarginCallStatus.ISSUED,
                        rs.getTimestamp("issued_at").toInstant());
                }
            }
        }
        throw new IllegalStateException("Expected open call for client " + clientId);
    }

    private void updateStatus(String callId, String status) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE margin_calls SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setObject(2, UUID.fromString(callId));
            ps.executeUpdate();
        }
    }
}

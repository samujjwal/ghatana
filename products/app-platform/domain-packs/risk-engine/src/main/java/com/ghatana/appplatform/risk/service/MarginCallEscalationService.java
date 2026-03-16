package com.ghatana.appplatform.risk.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Escalates margin calls through four tiers (T0→T+1→T+2→T+3) based on BS calendar
 *              business days, restricting trading and initiating forced liquidation (D06-013).
 * @doc.layer   Domain — risk engine
 * @doc.pattern State machine — escalation tier determined by age vs business-day thresholds
 */
public class MarginCallEscalationService {

    public enum EscalationTier {
        T0_NOTIFY,          // Margin call issued, client notified
        T1_REMINDER,        // T+1 unmet: reminder sent
        T2_RESTRICT,        // T+2 unmet: risk manager notified, trading restricted
        T3_LIQUIDATE        // T+3 unmet: forced liquidation initiated
    }

    public record EscalationEvent(String callId, String clientId, EscalationTier tier,
                                   BigDecimal deficitAmount, LocalDate escalatedOn) {}

    private final DataSource dataSource;
    private final Executor executor;
    private final Consumer<EscalationEvent> eventPublisher;
    private final Counter escalationCounter;

    public MarginCallEscalationService(DataSource dataSource, Executor executor,
                                        Consumer<EscalationEvent> eventPublisher,
                                        MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
        this.escalationCounter = Counter.builder("risk.margin.escalations_total")
            .register(registry);
    }

    /**
     * Scheduled daily: scan all ISSUED calls and apply escalation tiers.
     * Tier thresholds: T+0=issued, T+1=reminder, T+2=restrict trading, T+3=force liquidation.
     */
    public Promise<Integer> runDailyEscalation() {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT id, client_id, deficit_amount, deadline, issued_at " +
                         "FROM margin_calls WHERE status = 'ISSUED'";
            int escalated = 0;
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String callId = rs.getString("id");
                    String clientId = rs.getString("client_id");
                    BigDecimal deficit = rs.getBigDecimal("deficit_amount");
                    LocalDate issuedDate = rs.getDate("issued_at").toLocalDate();
                    long businessDaysSinceIssue = businessDaysBetween(issuedDate, LocalDate.now());

                    EscalationTier newTier = determineTier(businessDaysSinceIssue);
                    String currentTier = loadCurrentTier(c, callId);
                    if (!newTier.name().equals(currentTier)) {
                        applyEscalation(c, callId, clientId, newTier, deficit);
                        escalated++;
                    }
                }
            }
            return escalated;
        });
    }

    /** Manually escalate a specific call (admin override). */
    public Promise<Void> forceEscalate(String callId, EscalationTier tier, String adminId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection()) {
                String clientId = loadClientId(c, callId);
                BigDecimal deficit = loadDeficit(c, callId);
                applyEscalation(c, callId, clientId, tier, deficit);
            }
            return null;
        });
    }

    private EscalationTier determineTier(long businessDays) {
        if (businessDays >= 3) return EscalationTier.T3_LIQUIDATE;
        if (businessDays >= 2) return EscalationTier.T2_RESTRICT;
        if (businessDays >= 1) return EscalationTier.T1_REMINDER;
        return EscalationTier.T0_NOTIFY;
    }

    private void applyEscalation(Connection c, String callId, String clientId,
                                   EscalationTier tier, BigDecimal deficit) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE margin_calls SET escalation_tier = ? WHERE id = ?")) {
            ps.setString(1, tier.name());
            ps.setObject(2, UUID.fromString(callId));
            ps.executeUpdate();
        }
        if (tier == EscalationTier.T2_RESTRICT) {
            restrictTrading(c, clientId);
        }
        escalationCounter.increment();
        eventPublisher.accept(new EscalationEvent(callId, clientId, tier, deficit, LocalDate.now()));
    }

    private void restrictTrading(Connection c, String clientId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO client_trading_restrictions(client_id, reason, restricted_at) " +
                "VALUES(?, 'MARGIN_CALL_T2', NOW()) ON CONFLICT (client_id) DO NOTHING")) {
            ps.setObject(1, UUID.fromString(clientId));
            ps.executeUpdate();
        }
    }

    // Simple business day calculation (excludes weekends; production would use K-15 for BS holidays)
    private long businessDaysBetween(LocalDate from, LocalDate to) {
        long days = 0;
        LocalDate d = from;
        while (d.isBefore(to)) {
            if (d.getDayOfWeek().getValue() <= 5) days++;  // Mon-Fri
            d = d.plusDays(1);
        }
        return days;
    }

    private String loadCurrentTier(Connection c, String callId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT escalation_tier FROM margin_calls WHERE id = ?")) {
            ps.setObject(1, UUID.fromString(callId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }

    private String loadClientId(Connection c, String callId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT client_id::text FROM margin_calls WHERE id = ?")) {
            ps.setObject(1, UUID.fromString(callId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        throw new IllegalArgumentException("Call not found: " + callId);
    }

    private BigDecimal loadDeficit(Connection c, String callId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT deficit_amount FROM margin_calls WHERE id = ?")) {
            ps.setObject(1, UUID.fromString(callId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
        }
        return BigDecimal.ZERO;
    }
}

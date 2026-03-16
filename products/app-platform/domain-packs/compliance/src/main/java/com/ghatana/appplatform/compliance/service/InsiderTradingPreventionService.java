package com.ghatana.appplatform.compliance.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Prevents insider trading by (1) maintaining a blackout calendar and
 *                (2) checking orders against that calendar and the insider list from D-11.
 *                Blackout windows are auto-created on corporate action announcements (D-12).
 * @doc.layer     Application
 * @doc.pattern   Pre-trade gate; listens to corporate action events
 *
 * Story: D07-012
 */
public class InsiderTradingPreventionService {

    private static final Logger log = LoggerFactory.getLogger(InsiderTradingPreventionService.class);

    private final InsiderListPort  insiderListPort;
    private final DataSource       dataSource;
    private final Consumer<Object> eventPublisher;
    private final Counter          tradesBlocked;

    public InsiderTradingPreventionService(InsiderListPort insiderListPort,
                                            DataSource dataSource,
                                            Consumer<Object> eventPublisher,
                                            MeterRegistry meterRegistry) {
        this.insiderListPort = insiderListPort;
        this.dataSource      = dataSource;
        this.eventPublisher  = eventPublisher;
        this.tradesBlocked   = meterRegistry.counter("compliance.insider.trades.blocked");
    }

    /**
     * Checks whether the order violates insider trading rules.
     *
     * @param orderId       order being checked
     * @param clientId      placing client
     * @param instrumentId  instrument being traded
     * @param tradeTime     time of the proposed trade
     * @return check result — PASS or BLOCKED with reason
     */
    public InsiderCheckResult check(String orderId, String clientId,
                                     String instrumentId, Instant tradeTime) {
        // Step 1: Is this client an insider for the issuer of instrumentId?
        boolean isInsider = insiderListPort.isInsider(clientId, instrumentId);
        if (!isInsider) {
            return InsiderCheckResult.pass();
        }

        // Step 2: Is there an active blackout window for this instrument?
        List<BlackoutWindow> active = findActiveBlackouts(instrumentId, tradeTime);
        if (active.isEmpty()) {
            return InsiderCheckResult.pass();
        }

        BlackoutWindow window = active.get(0);
        log.warn("Insider trade blocked: client={} instrument={} window={}", clientId, instrumentId, window.windowId());
        tradesBlocked.increment();
        eventPublisher.accept(new InsiderTradeBlockedEvent(orderId, clientId, instrumentId,
                window.windowId(), window.reason(), tradeTime));
        return InsiderCheckResult.blocked("Insider blackout window active: " + window.reason());
    }

    /**
     * Opens a blackout window for an instrument. Called on corporate action announcements.
     *
     * @param instrumentId  instrument under blackout
     * @param startsAt      window start
     * @param endsAt        window end
     * @param reason        e.g. "EARNINGS_ANNOUNCEMENT", "RIGHTS_ISSUE"
     * @return new window ID
     */
    public String openBlackoutWindow(String instrumentId, Instant startsAt, Instant endsAt, String reason) {
        String windowId = UUID.randomUUID().toString();
        String sql = "INSERT INTO insider_blackout_windows"
                   + "(window_id, instrument_id, starts_at, ends_at, reason, created_at) "
                   + "VALUES(?,?,?,?,?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, windowId);
            ps.setString(2, instrumentId);
            ps.setTimestamp(3, Timestamp.from(startsAt));
            ps.setTimestamp(4, Timestamp.from(endsAt));
            ps.setString(5, reason);
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open blackout window for " + instrumentId, e);
        }
        log.info("Blackout window opened={} instrument={} reason={}", windowId, instrumentId, reason);
        eventPublisher.accept(new BlackoutWindowCreatedEvent(windowId, instrumentId, startsAt, endsAt, reason));
        return windowId;
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private List<BlackoutWindow> findActiveBlackouts(String instrumentId, Instant at) {
        String sql = "SELECT window_id, reason, starts_at, ends_at FROM insider_blackout_windows "
                   + "WHERE instrument_id=? AND starts_at<=? AND ends_at>=?";
        List<BlackoutWindow> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            ps.setTimestamp(2, Timestamp.from(at));
            ps.setTimestamp(3, Timestamp.from(at));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new BlackoutWindow(rs.getString("window_id"), instrumentId,
                            rs.getTimestamp("starts_at").toInstant(),
                            rs.getTimestamp("ends_at").toInstant(),
                            rs.getString("reason")));
                }
            }
        } catch (SQLException e) {
            log.error("findActiveBlackouts: DB error instrument={}", instrumentId, e);
        }
        return result;
    }

    // ─── Port ─────────────────────────────────────────────────────────────────

    public interface InsiderListPort {
        /** Returns true if the client is a registered insider for the issuer of this instrument. */
        boolean isInsider(String clientId, String instrumentId);
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record InsiderCheckResult(boolean passed, String blockReason) {
        public static InsiderCheckResult pass()               { return new InsiderCheckResult(true,  null); }
        public static InsiderCheckResult blocked(String r)    { return new InsiderCheckResult(false, r); }
    }

    public record BlackoutWindow(String windowId, String instrumentId,
                                  Instant startsAt, Instant endsAt, String reason) {}

    // ─── Events ───────────────────────────────────────────────────────────────

    public record InsiderTradeBlockedEvent(String orderId, String clientId, String instrumentId,
                                           String windowId, String blackoutReason, Instant tradeTime) {}
    public record BlackoutWindowCreatedEvent(String windowId, String instrumentId,
                                             Instant startsAt, Instant endsAt, String reason) {}
}

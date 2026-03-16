package com.ghatana.appplatform.risk.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Guards the forced liquidation engine with configurable safeguards:
 *              maximum liquidation per client per day, 30-minute cooling period between
 *              batches, 5% daily volume cap per instrument, and market halt detection.
 *              A manual risk-manager override bypasses all safeguards when required.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; reads from liquidation_audit and market_halt_events tables;
 *              no writes — safeguard checks are read-only gate conditions.
 */
public class LiquidationSafeguardService {

    private static final Logger log = LoggerFactory.getLogger(LiquidationSafeguardService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    private static final long   COOLING_PERIOD_MINUTES    = 30;
    private static final double MARKET_IMPACT_CAP_PCT     = 0.05;  // 5% of daily volume
    private static final double DEFAULT_MAX_DAILY_AMOUNT  = 5_000_000.0;  // NPR 50 lakhs

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Counter          allowedCounter;
    private final Counter          blockedCounter;

    public LiquidationSafeguardService(HikariDataSource dataSource, Executor executor,
                                       MeterRegistry registry) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.allowedCounter  = registry.counter("risk.liquidation.safeguard", "result", "allowed");
        this.blockedCounter  = registry.counter("risk.liquidation.safeguard", "result", "blocked");
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record SafeguardDecision(
        boolean allowed,
        String  blockReason   // null when allowed
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Evaluate all safeguards before initiating a liquidation batch.
     *
     * @param clientId          the client to be liquidated
     * @param instrumentId      instrument to sell
     * @param proposedQty       quantity proposed for this batch
     * @param proposedAmount    notional value of proposed liquidation
     * @param manualOverride    true when risk manager bypasses safeguards
     */
    public Promise<SafeguardDecision> evaluate(String clientId, String instrumentId,
                                               double proposedQty, double proposedAmount,
                                               boolean manualOverride) {
        return Promise.ofBlocking(executor, () -> {
            if (manualOverride) {
                log.info("Liquidation safeguard bypassed by manual override: clientId={}", clientId);
                allowedCounter.increment();
                return new SafeguardDecision(true, null);
            }
            return doEvaluate(clientId, instrumentId, proposedQty, proposedAmount);
        });
    }

    /**
     * Returns true when a market halt is currently active for an instrument.
     */
    public Promise<Boolean> isMarketHalted(String instrumentId) {
        return Promise.ofBlocking(executor, () -> checkMarketHalt(instrumentId));
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private SafeguardDecision doEvaluate(String clientId, String instrumentId,
                                          double proposedQty, double proposedAmount) {
        // 1. Market halt check
        if (checkMarketHalt(instrumentId)) {
            blockedCounter.increment();
            return new SafeguardDecision(false, "MARKET_HALTED instrument=" + instrumentId);
        }

        // 2. Daily liquidation cap
        double todayLiquidated = loadTodayLiquidatedAmount(clientId);
        double maxDaily        = loadMaxDailyLimit(clientId);
        if (todayLiquidated + proposedAmount > maxDaily) {
            blockedCounter.increment();
            return new SafeguardDecision(false,
                "DAILY_CAP_EXCEEDED today=" + todayLiquidated + " proposed=" + proposedAmount + " max=" + maxDaily);
        }

        // 3. Cooling period check
        if (!coolingPeriodElapsed(clientId)) {
            blockedCounter.increment();
            return new SafeguardDecision(false, "COOLING_PERIOD_NOT_ELAPSED minutes=" + COOLING_PERIOD_MINUTES);
        }

        // 4. Market impact cap (5% of ADV)
        double adv = loadAverageDailyVolume(instrumentId);
        if (adv > 0 && proposedQty > adv * MARKET_IMPACT_CAP_PCT) {
            blockedCounter.increment();
            return new SafeguardDecision(false,
                "MARKET_IMPACT_EXCEEDED proposed_qty=" + proposedQty + " cap=" + (adv * MARKET_IMPACT_CAP_PCT));
        }

        allowedCounter.increment();
        return new SafeguardDecision(true, null);
    }

    private boolean checkMarketHalt(String instrumentId) {
        String sql = """
            SELECT 1 FROM market_halt_events
            WHERE instrument_id = ?
              AND status = 'ACTIVE'
              AND halt_start   <= now()
              AND (halt_end IS NULL OR halt_end >= now())
            LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            log.error("Failed to check market halt for instrumentId={}", instrumentId, ex);
            return true; // fail-safe: treat as halted
        }
    }

    private double loadTodayLiquidatedAmount(String clientId) {
        String sql = """
            SELECT COALESCE(SUM(actual_liquidated), 0)
            FROM forced_liquidation_audit
            WHERE client_id  = ?
              AND created_at >= date_trunc('day', now() AT TIME ZONE 'Asia/Kathmandu')
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        } catch (SQLException ex) {
            log.error("Failed to load today liquidated amount for clientId={}", clientId, ex);
            return 0;
        }
    }

    private double loadMaxDailyLimit(String clientId) {
        String sql = """
            SELECT COALESCE(max_daily_liquidation, ?) 
            FROM client_risk_settings 
            WHERE client_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, DEFAULT_MAX_DAILY_AMOUNT);
            ps.setString(2, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException ex) {
            log.error("Failed to load max daily limit for clientId={}", clientId, ex);
        }
        return DEFAULT_MAX_DAILY_AMOUNT;
    }

    private boolean coolingPeriodElapsed(String clientId) {
        String sql = """
            SELECT EXTRACT(EPOCH FROM (now() - MAX(created_at))) / 60 AS minutes_since_last
            FROM forced_liquidation_audit
            WHERE client_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double minutesSince = rs.getDouble(1);
                    if (rs.wasNull()) return true;  // no previous liquidation
                    return minutesSince >= COOLING_PERIOD_MINUTES;
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to check cooling period for clientId={}", clientId, ex);
        }
        return true;
    }

    private double loadAverageDailyVolume(String instrumentId) {
        String sql = """
            SELECT COALESCE(AVG(volume), 0)
            FROM price_history
            WHERE instrument_id = ?
              AND price_date_ad  >= CURRENT_DATE - INTERVAL '20 days'
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException ex) {
            log.error("Failed to load ADV for instrumentId={}", instrumentId, ex);
        }
        return 0;
    }
}

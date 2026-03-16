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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Calculates margin for derivative positions using a SPAN-like worst-case
 *              scenario approach: initial margin = max loss across price/vol scenarios,
 *              variation margin = daily P&L settlement, spread credits for offsets.
 *              Supports T2 plugin extension for custom margin models.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; inner DerivativePricerPort for P&L scenario calculations;
 *              separate tables for derivative_margin_accounts and variation_margin_log.
 */
public class DerivativeMarginService {

    private static final Logger log = LoggerFactory.getLogger(DerivativeMarginService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    // SPAN-like scenario parameters – configurable per instrument type
    private static final int    PRICE_SCENARIOS    = 16;
    private static final double SPREAD_CREDIT_RATE = 0.50;  // 50% credit for spread positions

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final DerivativePricerPort pricer;
    private final Counter             initialMarginCounter;
    private final Counter             variationMarginCounter;

    public DerivativeMarginService(HikariDataSource dataSource, Executor executor,
                                   DerivativePricerPort pricer, MeterRegistry registry) {
        this.dataSource            = dataSource;
        this.executor              = executor;
        this.pricer                = pricer;
        this.initialMarginCounter  = registry.counter("risk.derivative.margin.initial");
        this.variationMarginCounter = registry.counter("risk.derivative.margin.variation");
    }

    // ─── Inner port (T2 extension point) ─────────────────────────────────────

    /**
     * T2 plugin interface for pricing derivative positions across scenarios.
     */
    public interface DerivativePricerPort {
        /** Returns P&L of position under a given price and vol shift. */
        double scenarioPnl(String positionId, double priceShiftPct, double volShiftPct);
        /** Returns current MTM value of position. */
        double currentMtm(String positionId);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record DerivativePosition(
        String positionId,
        String clientId,
        String instrumentId,
        String instrumentType,  // FUTURE | OPTION | SWAP
        double notional,
        double previousMtm,
        double spreadOffsetPositionId  // empty string if standalone
    ) {}

    public record DerivativeMarginResult(
        String clientId,
        double initialMargin,
        double variationMargin,  // positive = client pays, negative = client receives
        double spreadCredit,
        double netMarginRequired,
        int    scenariosEvaluated
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Calculate derivative margin for a client and persist results.
     */
    public Promise<DerivativeMarginResult> calculateDerivativeMargin(String clientId) {
        return Promise.ofBlocking(executor, () -> {
            List<DerivativePosition> positions = loadDerivativePositions(clientId);
            DerivativeMarginResult result = computeMargin(clientId, positions);
            persistMarginResult(clientId, result);
            return result;
        });
    }

    /**
     * Settle variation margin for all clients (EOD batch).
     */
    public Promise<Void> settleVariationMargin(List<String> clientIds) {
        return Promise.ofBlocking(executor, () -> {
            for (String cid : clientIds) {
                try {
                    List<DerivativePosition> positions = loadDerivativePositions(cid);
                    double variationMargin = computeVariationMargin(positions);
                    persistVariationMarginEntry(cid, variationMargin);
                    variationMarginCounter.increment();
                } catch (Exception ex) {
                    log.error("Variation margin settlement failed for clientId={}", cid, ex);
                }
            }
            return null;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private DerivativeMarginResult computeMargin(String clientId, List<DerivativePosition> positions) {
        if (positions.isEmpty()) {
            return new DerivativeMarginResult(clientId, 0, 0, 0, 0, 0);
        }
        // Initial margin: worst-case scenario loss across PRICE_SCENARIOS × vol scenarios
        double initialMargin   = computeInitialMargin(positions);
        double variationMargin = computeVariationMargin(positions);
        double spreadCredit    = computeSpreadCredit(positions);
        double net             = Math.max(0, initialMargin + variationMargin - spreadCredit);

        initialMarginCounter.increment();
        log.info("Derivative margin clientId={} initial={} variation={} spread_credit={} net={}",
                 clientId, initialMargin, variationMargin, spreadCredit, net);
        return new DerivativeMarginResult(clientId, initialMargin, variationMargin,
                                          spreadCredit, net, PRICE_SCENARIOS * 2);
    }

    private double computeInitialMargin(List<DerivativePosition> positions) {
        double[] priceShifts = {-0.08, -0.06, -0.04, -0.02, 0, 0.02, 0.04, 0.06,
                                 0.08, 0.06, 0.04, 0.02, 0, -0.02, -0.04, -0.06};
        double[] volShifts   = {0.25, 0.25, 0, 0, 0.25, 0.25, 0, 0,
                                -0.25, -0.25, 0, 0, -0.25, -0.25, 0, 0};

        double worstLoss = 0;
        for (DerivativePosition pos : positions) {
            double posWorstLoss = 0;
            for (int i = 0; i < PRICE_SCENARIOS; i++) {
                double scenarioPnl = pricer.scenarioPnl(pos.positionId(), priceShifts[i], volShifts[i]);
                posWorstLoss = Math.max(posWorstLoss, -scenarioPnl);
            }
            worstLoss += posWorstLoss;
        }
        return worstLoss;
    }

    private double computeVariationMargin(List<DerivativePosition> positions) {
        double totalVariation = 0;
        for (DerivativePosition pos : positions) {
            double currentMtm = pricer.currentMtm(pos.positionId());
            totalVariation += pos.previousMtm() - currentMtm; // positive means client owes
        }
        return totalVariation;
    }

    private double computeSpreadCredit(List<DerivativePosition> positions) {
        // Credit for positions with an offsetting leg (same instrument, opposite direction)
        long spreadPositions = positions.stream()
            .filter(p -> !p.spreadOffsetPositionId().isEmpty())
            .count();
        double grossInitial = computeInitialMargin(positions);
        return grossInitial * SPREAD_CREDIT_RATE * (spreadPositions / (double) Math.max(1, positions.size()));
    }

    private List<DerivativePosition> loadDerivativePositions(String clientId) {
        String sql = """
            SELECT position_id, client_id, instrument_id, instrument_type,
                   notional, previous_mtm, COALESCE(spread_offset_pos_id, '') AS spread_offset_pos_id
            FROM derivative_positions
            WHERE client_id = ? AND status = 'OPEN'
            """;
        List<DerivativePosition> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new DerivativePosition(
                        rs.getString("position_id"),
                        rs.getString("client_id"),
                        rs.getString("instrument_id"),
                        rs.getString("instrument_type"),
                        rs.getDouble("notional"),
                        rs.getDouble("previous_mtm"),
                        rs.getString("spread_offset_pos_id")
                    ));
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to load derivative positions for clientId={}", clientId, ex);
        }
        return result;
    }

    private void persistMarginResult(String clientId, DerivativeMarginResult result) {
        String sql = """
            INSERT INTO derivative_margin_accounts
                (margin_id, client_id, initial_margin, variation_margin,
                 spread_credit, net_required, computed_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (client_id) DO UPDATE
                SET initial_margin  = EXCLUDED.initial_margin,
                    variation_margin = EXCLUDED.variation_margin,
                    spread_credit   = EXCLUDED.spread_credit,
                    net_required    = EXCLUDED.net_required,
                    computed_at     = EXCLUDED.computed_at
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, clientId);
            ps.setDouble(3, result.initialMargin());
            ps.setDouble(4, result.variationMargin());
            ps.setDouble(5, result.spreadCredit());
            ps.setDouble(6, result.netMarginRequired());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist derivative margin for clientId={}", clientId, ex);
        }
    }

    private void persistVariationMarginEntry(String clientId, double variationMargin) {
        String sql = """
            INSERT INTO variation_margin_log
                (log_id, client_id, variation_margin, settled_at)
            VALUES (?, ?, ?, now())
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, clientId);
            ps.setDouble(3, variationMargin);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist variation margin log for clientId={}", clientId, ex);
        }
    }
}

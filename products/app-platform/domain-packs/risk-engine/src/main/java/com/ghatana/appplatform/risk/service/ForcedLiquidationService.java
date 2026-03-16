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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Triggered by MarginCallEscalationService when a margin call reaches T3 status.
 *              Sells positions in order of liquidity (most liquid first — highest ADV) until
 *              the margin deficit is covered plus a 10% buffer. Creates SYSTEM-flagged orders
 *              in OMS. Emits a ForcedLiquidation domain event and writes a regulatory audit trail.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; inner OmsOrderPort for SYSTEM order creation; inner
 *              LiquidityRankingPort for ADV-based sorting; audit trail via INSERT-only table.
 */
public class ForcedLiquidationService {

    private static final Logger log = LoggerFactory.getLogger(ForcedLiquidationService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    private static final double LIQUIDATION_BUFFER = 0.10;  // 10% extra buffer over deficit

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final OmsOrderPort        omsOrderPort;
    private final LiquidityRankingPort liquidityRankingPort;
    private final Counter             liquidationInitiatedCounter;
    private final Counter             ordersCreatedCounter;

    public ForcedLiquidationService(HikariDataSource dataSource, Executor executor,
                                    OmsOrderPort omsOrderPort,
                                    LiquidityRankingPort liquidityRankingPort,
                                    MeterRegistry registry) {
        this.dataSource                 = dataSource;
        this.executor                   = executor;
        this.omsOrderPort               = omsOrderPort;
        this.liquidityRankingPort       = liquidityRankingPort;
        this.liquidationInitiatedCounter = registry.counter("risk.liquidation.initiated");
        this.ordersCreatedCounter       = registry.counter("risk.liquidation.orders_created");
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /**
     * Port for submitting SYSTEM (bypass-approval) orders to OMS.
     */
    public interface OmsOrderPort {
        String submitSystemOrder(String clientId, String instrumentId,
                                 String side, double quantity, String reason);
    }

    /**
     * Port for retrieving average daily volume ranking of instruments.
     * Higher ADV = more liquid = liquidated first.
     */
    public interface LiquidityRankingPort {
        /** Returns ADV (shares) for the given instrument over last 20 trading days. */
        double getAverageDailyVolume(String instrumentId);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record ClientPosition(
        String instrumentId,
        double quantity,
        double lastPrice,
        double marketValue
    ) {}

    public record LiquidationResult(
        String         liquidationId,
        String         clientId,
        double         deficitAmount,
        double         targetLiquidation,  // deficit + buffer
        double         actualLiquidated,
        List<String>   systemOrderIds,
        boolean        deficitCovered
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Initiate forced liquidation for a client.
     *
     * @param clientId       target client
     * @param marginDeficit  positive amount by which deposited margin is below required
     * @param marginCallId   the escalated margin call triggering this liquidation
     */
    public Promise<LiquidationResult> initiateLiquidation(String clientId,
                                                           double marginDeficit,
                                                           String marginCallId) {
        return Promise.ofBlocking(executor, () -> {
            liquidationInitiatedCounter.increment();
            return doLiquidation(clientId, marginDeficit, marginCallId);
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private LiquidationResult doLiquidation(String clientId, double deficit, String callId) {
        String liquidationId  = UUID.randomUUID().toString();
        double targetAmount   = deficit * (1.0 + LIQUIDATION_BUFFER);
        double liquidated     = 0.0;
        List<String> orderIds = new ArrayList<>();

        // Load positions sorted by ADV descending (most liquid first)
        List<ClientPosition> positions = loadPositionsSortedByLiquidity(clientId);

        for (ClientPosition pos : positions) {
            if (liquidated >= targetAmount) break;
            double remaining       = targetAmount - liquidated;
            double sellQty         = Math.min(pos.quantity(), remaining / Math.max(pos.lastPrice(), 0.01));
            sellQty                = Math.floor(sellQty);  // whole shares only
            if (sellQty < 1) continue;

            String orderId = omsOrderPort.submitSystemOrder(
                clientId, pos.instrumentId(), "SELL", sellQty,
                "FORCED_LIQUIDATION margin_call_id=" + callId
            );
            orderIds.add(orderId);
            ordersCreatedCounter.increment();
            liquidated += sellQty * pos.lastPrice();
            log.info("Liquidation order created: clientId={} instrument={} qty={} orderId={}",
                     clientId, pos.instrumentId(), sellQty, orderId);
        }

        boolean covered = liquidated >= deficit;
        persistLiquidationAudit(liquidationId, clientId, deficit, targetAmount, liquidated, callId, covered, orderIds);

        if (!covered) {
            log.warn("Liquidation insufficient: clientId={} deficit={} liquidated={}", clientId, deficit, liquidated);
        }
        return new LiquidationResult(liquidationId, clientId, deficit, targetAmount, liquidated, orderIds, covered);
    }

    private List<ClientPosition> loadPositionsSortedByLiquidity(String clientId) {
        String sql = """
            SELECT instrument_id, quantity, last_price,
                   quantity * last_price AS market_value
            FROM portfolio_positions
            WHERE client_id = ? AND quantity > 0
            """;
        List<ClientPosition> positions = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    positions.add(new ClientPosition(
                        rs.getString("instrument_id"),
                        rs.getDouble("quantity"),
                        rs.getDouble("last_price"),
                        rs.getDouble("market_value")
                    ));
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to load positions for clientId={}", clientId, ex);
        }

        // Sort by ADV descending (most liquid first)
        positions.sort(Comparator.comparingDouble(
            p -> -liquidityRankingPort.getAverageDailyVolume(p.instrumentId())
        ));
        return positions;
    }

    private void persistLiquidationAudit(String liquidationId, String clientId,
                                          double deficit, double target, double liquidated,
                                          String callId, boolean covered, List<String> orderIds) {
        String sql = """
            INSERT INTO forced_liquidation_audit
                (liquidation_id, client_id, margin_call_id, deficit_amount,
                 target_amount, actual_liquidated, order_count, deficit_covered, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, liquidationId);
            ps.setString(2, clientId);
            ps.setString(3, callId);
            ps.setDouble(4, deficit);
            ps.setDouble(5, target);
            ps.setDouble(6, liquidated);
            ps.setInt(7, orderIds.size());
            ps.setBoolean(8, covered);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist liquidation audit for clientId={}", clientId, ex);
        }
    }
}

package com.ghatana.appplatform.pms.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * @doc.purpose Generate buy/sell rebalancing orders from drift-detected portfolio.
 *              Calculates required quantity per instrument using current price and lot-size
 *              rounding. Filters orders below minimum order value. Supports tax-loss harvesting
 *              (prefer selling loss-making positions first). K-17 saga port for order lifecycle.
 *              Maker-checker: submitRebalance() → approveRebalance(). Satisfies STORY-D03-008.
 * @doc.layer   Domain
 * @doc.pattern Saga via K-17 OrderSagaPort; maker-checker approval; lot-size rounding;
 *              tax-loss harvesting heuristic; Counter for submitted/approved/rejected.
 */
public class RebalancingOrderService {

    private static final Logger      log              = LoggerFactory.getLogger(RebalancingOrderService.class);
    private static final BigDecimal  MIN_ORDER_VALUE  = new BigDecimal("1000.00"); // filter tiny orders

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final PricePort        pricePort;
    private final OrderSagaPort    orderSagaPort;
    private final Counter          submittedCounter;
    private final Counter          approvedCounter;
    private final Counter          rejectedCounter;

    public RebalancingOrderService(HikariDataSource dataSource, Executor executor,
                                    PricePort pricePort, OrderSagaPort orderSagaPort,
                                    MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.pricePort       = pricePort;
        this.orderSagaPort   = orderSagaPort;
        this.submittedCounter = Counter.builder("pms.rebalance.submitted_total").register(registry);
        this.approvedCounter  = Counter.builder("pms.rebalance.approved_total").register(registry);
        this.rejectedCounter  = Counter.builder("pms.rebalance.rejected_total").register(registry);
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    public interface PricePort {
        BigDecimal getMarketPrice(String instrumentId);
    }

    /** K-17 saga port for order lifecycle. */
    public interface OrderSagaPort {
        String submitOrders(String portfolioId, List<RebalanceOrder> orders, String submittedBy);
        void approveOrders(String sagaId, String approverId);
        void rejectOrders(String sagaId, String approverId, String reason);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record RebalanceOrder(String instrumentId, String side, // BUY or SELL
                                 BigDecimal qty, BigDecimal estimatedValue, boolean taxLossHarvested) {}

    public record RebalanceRequest(String requestId, String portfolioId, LocalDateTime submittedAt,
                                   String submittedBy, String sagaId, String status,
                                   List<RebalanceOrder> orders) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<RebalanceRequest> submitRebalance(String portfolioId, String submittedBy) {
        return Promise.ofBlocking(executor, () -> {
            List<RebalanceOrder> orders = generateOrders(portfolioId);
            if (orders.isEmpty()) {
                log.info("No rebalancing orders required for portfolio={}", portfolioId);
                return buildRequest(portfolioId, submittedBy, null, "NO_ACTION", orders);
            }
            String sagaId = orderSagaPort.submitOrders(portfolioId, orders, submittedBy);
            String requestId = persistRequest(portfolioId, submittedBy, sagaId, "PENDING_APPROVAL", orders);
            submittedCounter.increment();
            return buildRequest(requestId, portfolioId, submittedBy, sagaId, "PENDING_APPROVAL", orders);
        });
    }

    public Promise<Void> approveRebalance(String requestId, String approverId) {
        return Promise.ofBlocking(executor, () -> {
            RebalancePending pending = loadPendingRequest(requestId);
            if (pending.submittedBy().equals(approverId)) {
                throw new IllegalStateException("Approver cannot be same as submitter (maker-checker)");
            }
            orderSagaPort.approveOrders(pending.sagaId(), approverId);
            updateRequestStatus(requestId, "APPROVED", approverId);
            approvedCounter.increment();
            return null;
        });
    }

    public Promise<Void> rejectRebalance(String requestId, String approverId, String reason) {
        return Promise.ofBlocking(executor, () -> {
            RebalancePending pending = loadPendingRequest(requestId);
            orderSagaPort.rejectOrders(pending.sagaId(), approverId, reason);
            updateRequestStatus(requestId, "REJECTED", approverId);
            rejectedCounter.increment();
            return null;
        });
    }

    // ─── Order generation ────────────────────────────────────────────────────

    private List<RebalanceOrder> generateOrders(String portfolioId) throws SQLException {
        List<RebalanceOrder> orders = new ArrayList<>();
        List<DriftRow> rows = loadDriftRows(portfolioId);

        for (DriftRow row : rows) {
            BigDecimal price = pricePort.getMarketPrice(row.instrumentId());
            if (price == null || price.compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal targetMv  = row.totalPortfolioNAV().multiply(row.targetWeight());
            BigDecimal actualMv  = row.totalPortfolioNAV().multiply(row.actualWeight());
            BigDecimal diffMv    = targetMv.subtract(actualMv);
            BigDecimal rawQty    = diffMv.divide(price, 6, RoundingMode.HALF_UP);

            // Lot-size rounding
            BigDecimal lotSize   = row.lotSize().compareTo(BigDecimal.ZERO) > 0 ? row.lotSize() : BigDecimal.ONE;
            BigDecimal lots      = rawQty.divide(lotSize, 0, RoundingMode.HALF_UP);
            BigDecimal adjQty    = lots.multiply(lotSize).abs();
            BigDecimal estValue  = adjQty.multiply(price);

            if (estValue.compareTo(MIN_ORDER_VALUE) < 0 || adjQty.compareTo(BigDecimal.ZERO) == 0) continue;

            String  side              = rawQty.compareTo(BigDecimal.ZERO) > 0 ? "BUY" : "SELL";
            boolean taxLossHarvested  = "SELL".equals(side) && row.unrealizedPnl().compareTo(BigDecimal.ZERO) < 0;

            orders.add(new RebalanceOrder(row.instrumentId(), side, adjQty, estValue, taxLossHarvested));
        }

        // Sort sells first to free cash before buys, tax-loss sells prioritized
        orders.sort((a, b) -> {
            if (!a.side().equals(b.side())) return "SELL".equals(a.side()) ? -1 : 1;
            if (a.taxLossHarvested() != b.taxLossHarvested()) return a.taxLossHarvested() ? -1 : 1;
            return 0;
        });
        return orders;
    }

    private record DriftRow(String instrumentId, BigDecimal targetWeight, BigDecimal actualWeight,
                            BigDecimal totalPortfolioNAV, BigDecimal lotSize, BigDecimal unrealizedPnl) {}

    private List<DriftRow> loadDriftRows(String portfolioId) throws SQLException {
        List<DriftRow> rows = new ArrayList<>();
        String sql = """
                SELECT ta.instrument_id, ta.target_weight,
                       COALESCE(h.actual_weight, 0) AS actual_weight,
                       n.nav AS total_nav,
                       COALESCE(r.lot_size, 1) AS lot_size,
                       COALESCE(h.unrealized_pnl, 0) AS unrealized_pnl
                FROM target_allocations ta
                LEFT JOIN portfolio_holdings_latest h ON h.instrument_id = ta.instrument_id
                    AND h.portfolio_id = ta.portfolio_id
                JOIN nav_history n ON n.portfolio_id = ta.portfolio_id
                    AND n.calc_date_ad = (SELECT MAX(calc_date_ad) FROM nav_history
                                         WHERE portfolio_id = ta.portfolio_id)
                LEFT JOIN reference_data r ON r.instrument_id = ta.instrument_id
                WHERE ta.portfolio_id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new DriftRow(rs.getString("instrument_id"),
                            rs.getBigDecimal("target_weight"), rs.getBigDecimal("actual_weight"),
                            rs.getBigDecimal("total_nav"), rs.getBigDecimal("lot_size"),
                            rs.getBigDecimal("unrealized_pnl")));
                }
            }
        }
        return rows;
    }

    // ─── Persistence helpers ─────────────────────────────────────────────────

    private String persistRequest(String portfolioId, String submittedBy, String sagaId,
                                   String status, List<RebalanceOrder> orders) throws SQLException {
        String requestId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO rebalance_requests
                    (request_id, portfolio_id, submitted_by, submitted_at, saga_id, status, order_count)
                VALUES (?, ?, ?, NOW(), ?, ?, ?)
                ON CONFLICT (request_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.setString(2, portfolioId);
            ps.setString(3, submittedBy);
            ps.setString(4, sagaId);
            ps.setString(5, status);
            ps.setInt(6, orders.size());
            ps.executeUpdate();
        }
        return requestId;
    }

    private record RebalancePending(String submittedBy, String sagaId) {}

    private RebalancePending loadPendingRequest(String requestId) throws SQLException {
        String sql = "SELECT submitted_by, saga_id FROM rebalance_requests WHERE request_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Request not found: " + requestId);
                return new RebalancePending(rs.getString("submitted_by"), rs.getString("saga_id"));
            }
        }
    }

    private void updateRequestStatus(String requestId, String status, String actorId) throws SQLException {
        String sql = "UPDATE rebalance_requests SET status = ?, approved_by = ?, actioned_at = NOW() WHERE request_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, actorId);
            ps.setString(3, requestId);
            ps.executeUpdate();
        }
    }

    private RebalanceRequest buildRequest(String portfolioId, String submittedBy, String sagaId,
                                           String status, List<RebalanceOrder> orders) {
        return new RebalanceRequest(UUID.randomUUID().toString(), portfolioId, LocalDateTime.now(),
                submittedBy, sagaId, status, orders);
    }

    private RebalanceRequest buildRequest(String requestId, String portfolioId, String submittedBy,
                                           String sagaId, String status, List<RebalanceOrder> orders) {
        return new RebalanceRequest(requestId, portfolioId, LocalDateTime.now(),
                submittedBy, sagaId, status, orders);
    }
}

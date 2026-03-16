package com.ghatana.appplatform.oms.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * @doc.type      Service
 * @doc.purpose   Order blotter and query API service. Provides paginated, filtered order queries
 *                for the OMS blotter view and admin dashboard.
 * @doc.layer     Application
 * @doc.pattern   CQRS Read Side — query-optimized order retrieval
 *
 * Supports filtering by: client_id, status, instrument_id, date range.
 * Sorting: created_at DESC (default).
 * Performance target: &lt; 50ms for paginated queries.
 *
 * Story: D01-018
 */
public class OrderBlotterService {

    private static final Logger log  = LoggerFactory.getLogger(OrderBlotterService.class);
    private static final int    MAX_PAGE_SIZE = 200;

    private final DataSource dataSource;
    private final Timer      queryTimer;

    public OrderBlotterService(DataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource  = dataSource;
        this.queryTimer  = meterRegistry.timer("oms.blotter.query");
    }

    /**
     * Fetches a paginated list of orders with optional filters.
     *
     * @param filter  search criteria (may be null/empty for all)
     * @param page    0-based page index
     * @param size    page size (max {@value #MAX_PAGE_SIZE})
     * @return ordered page of matching orders
     */
    public OrderPage queryOrders(OrderFilter filter, int page, int size) {
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        int offset   = page * safeSize;

        return queryTimer.record(() -> {
            StringBuilder sql = new StringBuilder(
                    "SELECT order_id, client_id, instrument_id, side, order_type, status, "
                  + "total_quantity, filled_quantity, avg_fill_price, limit_price, time_in_force, "
                  + "rejection_reason, created_at, updated_at FROM orders WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (filter.clientId() != null) {
                sql.append(" AND client_id = ?");
                params.add(filter.clientId());
            }
            if (filter.status() != null) {
                sql.append(" AND status = ?");
                params.add(filter.status());
            }
            if (filter.instrumentId() != null) {
                sql.append(" AND instrument_id = ?");
                params.add(filter.instrumentId());
            }
            if (filter.from() != null) {
                sql.append(" AND created_at >= ?");
                params.add(Timestamp.from(filter.from()));
            }
            if (filter.to() != null) {
                sql.append(" AND created_at <= ?");
                params.add(Timestamp.from(filter.to()));
            }

            sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
            params.add(safeSize);
            params.add(offset);

            List<OrderSummary> orders = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        orders.add(mapOrderRow(rs));
                    }
                }
            } catch (SQLException e) {
                log.error("OrderBlotter: DB error querying orders", e);
            }

            long total = countOrders(filter);
            return new OrderPage(orders, page, safeSize, total);
        });
    }

    /**
     * Fetches full order detail including fills and approval history.
     *
     * @param orderId the order ID to load
     * @return full detail or empty if not found
     */
    public Optional<OrderDetail> getOrderDetail(String orderId) {
        String sql = "SELECT order_id, client_id, instrument_id, side, order_type, status, "
                  + "total_quantity, filled_quantity, avg_fill_price, limit_price, time_in_force, "
                  + "rejection_reason, created_at, updated_at FROM orders WHERE order_id = ?";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                OrderSummary summary = mapOrderRow(rs);
                List<FillRecord> fills  = loadFills(c, orderId);
                List<ApprovalRecord> approvals = loadApprovals(c, orderId);
                return Optional.of(new OrderDetail(summary, fills, approvals));
            }
        } catch (SQLException e) {
            log.error("OrderBlotter: DB error loading order detail orderId={}", orderId, e);
            return Optional.empty();
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private OrderSummary mapOrderRow(ResultSet rs) throws SQLException {
        return new OrderSummary(
                rs.getString("order_id"),
                rs.getString("client_id"),
                rs.getString("instrument_id"),
                rs.getString("side"),
                rs.getString("order_type"),
                rs.getString("status"),
                rs.getLong("total_quantity"),
                rs.getLong("filled_quantity"),
                rs.getBigDecimal("avg_fill_price"),
                rs.getBigDecimal("limit_price"),
                rs.getString("time_in_force"),
                rs.getString("rejection_reason"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private List<FillRecord> loadFills(Connection c, String orderId) throws SQLException {
        List<FillRecord> fills = new ArrayList<>();
        String sql = "SELECT fill_id, exec_id, filled_quantity, fill_price, exchange, filled_at "
                   + "FROM order_fills WHERE order_id = ? ORDER BY filled_at";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fills.add(new FillRecord(rs.getString("fill_id"), rs.getString("exec_id"),
                            rs.getLong("filled_quantity"), rs.getBigDecimal("fill_price"),
                            rs.getString("exchange"), rs.getTimestamp("filled_at").toInstant()));
                }
            }
        }
        return fills;
    }

    private List<ApprovalRecord> loadApprovals(Connection c, String orderId) throws SQLException {
        List<ApprovalRecord> approvals = new ArrayList<>();
        String sql = "SELECT approval_id, actor_id, decision, reason, decided_at "
                   + "FROM order_approvals WHERE order_id = ? ORDER BY decided_at";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    approvals.add(new ApprovalRecord(rs.getString("approval_id"),
                            rs.getString("actor_id"), rs.getString("decision"),
                            rs.getString("reason"), rs.getTimestamp("decided_at").toInstant()));
                }
            }
        }
        return approvals;
    }

    private long countOrders(OrderFilter filter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM orders WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (filter.clientId() != null) { sql.append(" AND client_id = ?"); params.add(filter.clientId()); }
        if (filter.status() != null)   { sql.append(" AND status = ?");    params.add(filter.status()); }
        if (filter.from() != null)     { sql.append(" AND created_at >= ?"); params.add(Timestamp.from(filter.from())); }
        if (filter.to() != null)       { sql.append(" AND created_at <= ?"); params.add(Timestamp.from(filter.to())); }

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            log.error("OrderBlotter: count query failed", e);
            return 0;
        }
    }

    // ─── Domain types ─────────────────────────────────────────────────────────

    public record OrderFilter(String clientId, String status, String instrumentId,
                               Instant from, Instant to) {}

    public record OrderSummary(String orderId, String clientId, String instrumentId,
                                String side, String orderType, String status,
                                long totalQuantity, long filledQuantity, BigDecimal avgFillPrice,
                                BigDecimal limitPrice, String timeInForce, String rejectionReason,
                                Instant createdAt, Instant updatedAt) {}

    public record FillRecord(String fillId, String execId, long filledQuantity,
                              BigDecimal fillPrice, String exchange, Instant filledAt) {}

    public record ApprovalRecord(String approvalId, String actorId, String decision,
                                  String reason, Instant decidedAt) {}

    public record OrderDetail(OrderSummary summary, List<FillRecord> fills, List<ApprovalRecord> approvals) {}

    public record OrderPage(List<OrderSummary> orders, int page, int size, long totalCount) {
        public int totalPages() { return (int) Math.ceil((double) totalCount / size); }
    }
}

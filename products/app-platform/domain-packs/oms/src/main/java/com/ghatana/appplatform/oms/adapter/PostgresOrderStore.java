package com.ghatana.appplatform.oms.adapter;

import com.ghatana.appplatform.oms.domain.*;
import com.ghatana.appplatform.oms.port.OrderStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * @doc.type    Adapter (Infrastructure)
 * @doc.purpose PostgreSQL implementation of OrderStore using JDBC + HikariCP.
 * @doc.layer   Adapter (Infrastructure)
 * @doc.pattern Hexagonal Architecture — Adapter, Repository
 */
public class PostgresOrderStore implements OrderStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresOrderStore.class);

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresOrderStore(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<Void> save(Order order) {
        return Promise.ofBlocking(executor, () -> {
            try (var conn = dataSource.getConnection();
                 var ps = conn.prepareStatement("""
                         INSERT INTO orders
                           (order_id, client_id, account_id, instrument_id,
                            side, order_type, time_in_force, quantity, price, stop_price,
                            status, idempotency_key,
                            instrument_symbol, exchange, currency, order_value, arrival_price,
                            filled_quantity, remaining_quantity, avg_fill_price,
                            created_at, created_at_bs, updated_at)
                         VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                         """)) {
                bindOrder(ps, order);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Void> update(Order order) {
        return Promise.ofBlocking(executor, () -> {
            try (var conn = dataSource.getConnection();
                 var ps = conn.prepareStatement("""
                         UPDATE orders SET
                           status = ?, filled_quantity = ?, remaining_quantity = ?,
                           avg_fill_price = ?, rejection_reason = ?, routing_id = ?,
                           updated_at = ?
                         WHERE order_id = ?
                         """)) {
                ps.setString(1, order.status().name());
                ps.setBigDecimal(2, order.filledQuantity());
                ps.setBigDecimal(3, order.remainingQuantity());
                ps.setBigDecimal(4, order.avgFillPrice());
                ps.setString(5, order.rejectionReason());
                ps.setString(6, order.routingId());
                ps.setTimestamp(7, Timestamp.from(Instant.now()));
                ps.setString(8, order.orderId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Optional<Order>> findById(String orderId) {
        return Promise.ofBlocking(executor, () -> {
            try (var conn = dataSource.getConnection();
                 var ps = conn.prepareStatement("SELECT * FROM orders WHERE order_id = ?")) {
                ps.setString(1, orderId);
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<Optional<Order>> findByIdempotencyKey(String idempotencyKey) {
        return Promise.ofBlocking(executor, () -> {
            try (var conn = dataSource.getConnection();
                 var ps = conn.prepareStatement("SELECT * FROM orders WHERE idempotency_key = ?")) {
                ps.setString(1, idempotencyKey);
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<List<Order>> findByClientId(String clientId, OrderStatus status,
                                                String instrumentId, Instant from, Instant to,
                                                int page, int size) {
        return Promise.ofBlocking(executor, () -> {
            var sql = new StringBuilder("SELECT * FROM orders WHERE client_id = ?");
            if (status != null) sql.append(" AND status = ?");
            if (instrumentId != null) sql.append(" AND instrument_id = ?");
            if (from != null) sql.append(" AND created_at >= ?");
            if (to != null) sql.append(" AND created_at <= ?");
            sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");

            try (var conn = dataSource.getConnection();
                 var ps = conn.prepareStatement(sql.toString())) {
                int idx = 1;
                ps.setString(idx++, clientId);
                if (status != null) ps.setString(idx++, status.name());
                if (instrumentId != null) ps.setString(idx++, instrumentId);
                if (from != null) ps.setTimestamp(idx++, Timestamp.from(from));
                if (to != null) ps.setTimestamp(idx++, Timestamp.from(to));
                ps.setInt(idx++, size);
                ps.setInt(idx, page * size);
                try (var rs = ps.executeQuery()) {
                    var result = new ArrayList<Order>();
                    while (rs.next()) result.add(mapRow(rs));
                    return result;
                }
            }
        });
    }

    @Override
    public Promise<List<Order>> findPendingApproval(String assigneeId) {
        return Promise.ofBlocking(executor, () -> {
            try (var conn = dataSource.getConnection();
                 var ps = conn.prepareStatement(
                         "SELECT * FROM orders WHERE status = 'PENDING_APPROVAL' ORDER BY created_at ASC")) {
                try (var rs = ps.executeQuery()) {
                    var result = new ArrayList<Order>();
                    while (rs.next()) result.add(mapRow(rs));
                    return result;
                }
            }
        });
    }

    @Override
    public Promise<List<Order>> findExpiredOrders(Instant before) {
        return Promise.ofBlocking(executor, () -> {
            try (var conn = dataSource.getConnection();
                 var ps = conn.prepareStatement("""
                         SELECT * FROM orders
                         WHERE status IN ('PENDING','ROUTED','PARTIALLY_FILLED')
                           AND created_at < ?
                         """)) {
                ps.setTimestamp(1, Timestamp.from(before));
                try (var rs = ps.executeQuery()) {
                    var result = new ArrayList<Order>();
                    while (rs.next()) result.add(mapRow(rs));
                    return result;
                }
            }
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void bindOrder(PreparedStatement ps, Order o) throws SQLException {
        ps.setString(1, o.orderId());
        ps.setString(2, o.clientId());
        ps.setString(3, o.accountId());
        ps.setString(4, o.instrumentId());
        ps.setString(5, o.side().name());
        ps.setString(6, o.orderType().name());
        ps.setString(7, o.timeInForce().name());
        ps.setBigDecimal(8, o.quantity());
        ps.setBigDecimal(9, o.price());
        ps.setBigDecimal(10, o.stopPrice());
        ps.setString(11, o.status().name());
        ps.setString(12, o.idempotencyKey());
        ps.setString(13, o.instrumentSymbol());
        ps.setString(14, o.exchange());
        ps.setString(15, o.currency());
        ps.setBigDecimal(16, o.orderValue());
        ps.setBigDecimal(17, o.arrivalPrice());
        ps.setBigDecimal(18, o.filledQuantity());
        ps.setBigDecimal(19, o.remainingQuantity());
        ps.setBigDecimal(20, o.avgFillPrice());
        ps.setTimestamp(21, Timestamp.from(o.createdAt()));
        ps.setString(22, o.createdAtBs());
        ps.setTimestamp(23, Timestamp.from(o.updatedAt()));
    }

    private Order mapRow(ResultSet rs) throws SQLException {
        return new Order(
                rs.getString("order_id"),
                rs.getString("client_id"),
                rs.getString("account_id"),
                rs.getString("instrument_id"),
                OrderSide.valueOf(rs.getString("side")),
                OrderType.valueOf(rs.getString("order_type")),
                TimeInForce.valueOf(rs.getString("time_in_force")),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("stop_price"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getString("idempotency_key"),
                rs.getString("instrument_symbol"),
                rs.getString("exchange"),
                rs.getString("currency"),
                rs.getBigDecimal("order_value"),
                rs.getBigDecimal("arrival_price"),
                rs.getBigDecimal("filled_quantity"),
                rs.getBigDecimal("remaining_quantity"),
                rs.getBigDecimal("avg_fill_price"),
                List.of(), // fills loaded separately if needed
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_at_bs"),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getString("rejection_reason"),
                rs.getString("routing_id")
        );
    }
}

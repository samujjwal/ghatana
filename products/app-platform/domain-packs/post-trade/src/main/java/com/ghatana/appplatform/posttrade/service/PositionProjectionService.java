package com.ghatana.appplatform.posttrade.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Maintains real-time client position state. Listens to SettlementCompleted and
 *              CorporateAction events. Updates PostgreSQL `client_positions` as the durable
 *              read model and mirrors to Redis HSET for sub-1ms hot queries.
 *              Supports corporate action types: BONUS (quantity adjustment) and DIVIDEND_CASH
 *              (cash position credit). Applies weighted-average cost basis on settlement.
 * @doc.layer   Domain
 * @doc.pattern Event-driven; Redis write-through cache; idempotency via ON CONFLICT on
 *              (client_id, instrument_id); K-15 CalendarPort unused here (positions in AD dates).
 */
public class PositionProjectionService {

    private static final Logger log = LoggerFactory.getLogger(PositionProjectionService.class);

    private static final String REDIS_HASH_PREFIX = "position:";

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final JedisPool        jedisPool;
    private final Counter          settlementUpdates;
    private final Counter          corpActionUpdates;
    private final AtomicLong       cachedPositionCount = new AtomicLong(0);

    public PositionProjectionService(HikariDataSource dataSource, Executor executor,
                                     JedisPool jedisPool, MeterRegistry registry) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.jedisPool         = jedisPool;
        this.settlementUpdates = registry.counter("posttrade.positions.settlement_updates");
        this.corpActionUpdates = registry.counter("posttrade.positions.corp_action_updates");
        Gauge.builder("posttrade.positions.cache_size", cachedPositionCount, AtomicLong::get)
             .register(registry);
    }

    // ─── Events (incoming) ───────────────────────────────────────────────────

    public record SettlementCompleted(
        String settlementId,
        String buyerClientId,
        String sellerClientId,
        String instrumentId,
        double quantity,
        double price,
        String currency
    ) {}

    public record CorporateAction(
        String actionId,
        String actionType,        // BONUS | DIVIDEND_CASH
        String instrumentId,
        String exDate,            // AD date string yyyy-MM-dd
        double bonusRatio,        // for BONUS: new shares per existing share
        double dividendPerShare   // for DIVIDEND_CASH
    ) {}

    // ─── Read model ──────────────────────────────────────────────────────────

    public record ClientPosition(
        String clientId,
        String instrumentId,
        double quantity,
        double avgCostPerShare,
        double totalCostBasis,
        String lastUpdated
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Update positions for both buyer and seller on settlement.
     */
    public Promise<Void> onSettlementCompleted(SettlementCompleted event) {
        return Promise.ofBlocking(executor, () -> {
            // Buyer: add quantity, update weighted avg cost
            applyBuyerPosition(event);
            // Seller: subtract quantity
            applySellerPosition(event);
            settlementUpdates.increment();
            return null;
        });
    }

    /**
     * Apply corporate action to all affected client positions.
     */
    public Promise<Void> onCorporateAction(CorporateAction action) {
        return Promise.ofBlocking(executor, () -> {
            List<String> affectedClients = loadClientsWithHolding(action.instrumentId());
            switch (action.actionType()) {
                case "BONUS"          -> applyBonusToAll(affectedClients, action);
                case "DIVIDEND_CASH"  -> applyDividendToAll(affectedClients, action);
                default -> log.warn("Unknown corporate action type={}", action.actionType());
            }
            corpActionUpdates.increment();
            return null;
        });
    }

    /**
     * Fetch position from Redis cache; falls back to PostgreSQL if cache miss.
     */
    public Promise<ClientPosition> getPosition(String clientId, String instrumentId) {
        return Promise.ofBlocking(executor, () -> {
            String key   = REDIS_HASH_PREFIX + clientId;
            String field = instrumentId;
            try (var jedis = jedisPool.getResource()) {
                String cached = jedis.hget(key, field);
                if (cached != null) {
                    return deserializePosition(clientId, instrumentId, cached);
                }
            }
            // Cache miss: load from DB and re-cache
            ClientPosition pos = loadFromDb(clientId, instrumentId);
            if (pos != null) {
                cachePosition(pos);
            }
            return pos;
        });
    }

    // ─── Settlement position updates ─────────────────────────────────────────

    private void applyBuyerPosition(SettlementCompleted e) throws SQLException {
        String sql = """
            INSERT INTO client_positions (
                client_id, instrument_id, quantity, avg_cost_per_share, total_cost_basis,
                last_updated, last_settlement_id
            ) VALUES (?, ?, ?, ?, ?, now(), ?)
            ON CONFLICT (client_id, instrument_id) DO UPDATE SET
                quantity = client_positions.quantity + EXCLUDED.quantity,
                total_cost_basis = client_positions.total_cost_basis + EXCLUDED.total_cost_basis,
                avg_cost_per_share = (client_positions.total_cost_basis + EXCLUDED.total_cost_basis)
                                     / (client_positions.quantity + EXCLUDED.quantity),
                last_updated = now(),
                last_settlement_id = EXCLUDED.last_settlement_id
            """;
        double cost = e.quantity() * e.price();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.buyerClientId());
            ps.setString(2, e.instrumentId());
            ps.setDouble(3, e.quantity());
            ps.setDouble(4, e.price());
            ps.setDouble(5, cost);
            ps.setString(6, e.settlementId());
            ps.executeUpdate();
        }
        invalidateCache(e.buyerClientId(), e.instrumentId());
    }

    private void applySellerPosition(SettlementCompleted e) throws SQLException {
        String sql = """
            UPDATE client_positions
            SET quantity = quantity - ?,
                last_updated = now(),
                last_settlement_id = ?
            WHERE client_id = ? AND instrument_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, e.quantity());
            ps.setString(2, e.settlementId());
            ps.setString(3, e.sellerClientId());
            ps.setString(4, e.instrumentId());
            ps.executeUpdate();
        }
        invalidateCache(e.sellerClientId(), e.instrumentId());
    }

    // ─── Corporate action application ────────────────────────────────────────

    private void applyBonusToAll(List<String> clients, CorporateAction action) throws SQLException {
        String sql = """
            UPDATE client_positions
            SET quantity = quantity * (1 + ?),
                avg_cost_per_share = avg_cost_per_share / (1 + ?),
                last_updated = now()
            WHERE client_id = ? AND instrument_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String cid : clients) {
                ps.setDouble(1, action.bonusRatio());
                ps.setDouble(2, action.bonusRatio());
                ps.setString(3, cid);
                ps.setString(4, action.instrumentId());
                ps.addBatch();
                invalidateCache(cid, action.instrumentId());
            }
            ps.executeBatch();
        }
    }

    private void applyDividendToAll(List<String> clients, CorporateAction action) throws SQLException {
        // Credit cash position equivalent: insert or add to a special CASH instrument record
        String sql = """
            INSERT INTO client_positions (
                client_id, instrument_id, quantity, avg_cost_per_share, total_cost_basis,
                last_updated, last_settlement_id
            )
            SELECT cp.client_id, 'NPR_CASH', cp.quantity * ?, 1.0, cp.quantity * ?, now(), ?
            FROM client_positions cp
            WHERE cp.client_id = ? AND cp.instrument_id = ?
            ON CONFLICT (client_id, instrument_id) DO UPDATE SET
                quantity = client_positions.quantity + EXCLUDED.quantity,
                total_cost_basis = client_positions.total_cost_basis + EXCLUDED.total_cost_basis,
                last_updated = now()
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String cid : clients) {
                ps.setDouble(1, action.dividendPerShare());
                ps.setDouble(2, action.dividendPerShare());
                ps.setString(3, action.actionId());
                ps.setString(4, cid);
                ps.setString(5, action.instrumentId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ─── DB helpers ──────────────────────────────────────────────────────────

    private List<String> loadClientsWithHolding(String instrumentId) throws SQLException {
        String sql = "SELECT client_id FROM client_positions WHERE instrument_id = ? AND quantity > 0";
        List<String> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString("client_id"));
            }
        }
        return result;
    }

    private ClientPosition loadFromDb(String clientId, String instrumentId) throws SQLException {
        String sql = """
            SELECT quantity, avg_cost_per_share, total_cost_basis, last_updated
            FROM client_positions
            WHERE client_id = ? AND instrument_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setString(2, instrumentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ClientPosition(
                        clientId, instrumentId,
                        rs.getDouble("quantity"),
                        rs.getDouble("avg_cost_per_share"),
                        rs.getDouble("total_cost_basis"),
                        rs.getString("last_updated")
                    );
                }
            }
        }
        return null;
    }

    // ─── Cache helpers ────────────────────────────────────────────────────────

    private void cachePosition(ClientPosition pos) {
        String value = pos.quantity() + "," + pos.avgCostPerShare() + "," + pos.totalCostBasis();
        try (var jedis = jedisPool.getResource()) {
            jedis.hset(REDIS_HASH_PREFIX + pos.clientId(), pos.instrumentId(), value);
            cachedPositionCount.incrementAndGet();
        }
    }

    private void invalidateCache(String clientId, String instrumentId) {
        try (var jedis = jedisPool.getResource()) {
            jedis.hdel(REDIS_HASH_PREFIX + clientId, instrumentId);
        }
    }

    private ClientPosition deserializePosition(String clientId, String instrumentId, String raw) {
        String[] parts = raw.split(",");
        return new ClientPosition(
            clientId, instrumentId,
            Double.parseDouble(parts[0]),
            Double.parseDouble(parts[1]),
            Double.parseDouble(parts[2]),
            "from-cache"
        );
    }
}

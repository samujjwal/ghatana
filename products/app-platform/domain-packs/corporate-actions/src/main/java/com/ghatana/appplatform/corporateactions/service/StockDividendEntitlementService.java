package com.ghatana.appplatform.corporateactions.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

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
 * @doc.purpose Calculates stock dividend (bonus share) entitlements. Entitlement shares =
 *              floor(quantity × ratio). Fractional shares are settled as cash-in-lieu at
 *              current market price. Cost basis is adjusted so total basis is unchanged
 *              (per-share cost reduces proportionally). Position update delegated to
 *              PositionPort (D-09). Satisfies STORY-D12-005.
 * @doc.layer   Domain
 * @doc.pattern Stock entitlement; fractional cash-in-lieu; cost basis adjustment; PositionPort.
 */
public class StockDividendEntitlementService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final PositionPort     positionPort;
    private final EventPort        eventPort;
    private final Counter          entitlementCounter;
    private final Counter          cashInLieuCounter;

    public StockDividendEntitlementService(HikariDataSource dataSource, Executor executor,
                                            PositionPort positionPort, EventPort eventPort,
                                            MeterRegistry registry) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.positionPort        = positionPort;
        this.eventPort           = eventPort;
        this.entitlementCounter  = Counter.builder("ca.stock_dividend.entitlements_total").register(registry);
        this.cashInLieuCounter   = Counter.builder("ca.stock_dividend.cash_in_lieu_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface PositionPort {
        void addShares(String clientId, String instrumentId, long quantity, LocalDate settleDate);
        double getCurrentPrice(String instrumentId);
        double getAvgCostBasis(String clientId, String instrumentId);
    }

    public interface EventPort { void publish(String topic, Object payload); }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record StockEntitlement(String entitlementId, String caId, String clientId,
                                    long bonusShares, BigDecimal cashInLieu,
                                    double newAvgCostPerShare, LocalDateTime calculatedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<StockEntitlement>> calculateEntitlements(String caId, String instrumentId,
                                                                   double ratio, LocalDate settleDate) {
        return Promise.ofBlocking(executor, () -> {
            List<HolderRow> holders = loadSnapshot(caId);
            List<StockEntitlement> results = new ArrayList<>();
            double marketPrice = positionPort.getCurrentPrice(instrumentId);

            for (HolderRow holder : holders) {
                double rawBonus   = holder.quantity() * ratio;
                long   bonusWhole = (long) rawBonus;
                double fractional = rawBonus - bonusWhole;

                BigDecimal cashInLieu = BigDecimal.ZERO;
                if (fractional > 0) {
                    cashInLieu = BigDecimal.valueOf(fractional * marketPrice)
                            .setScale(2, RoundingMode.HALF_UP);
                    cashInLieuCounter.increment();
                }

                // Adjust cost basis: total cost = qty × avgCost remains unchanged;
                // new per-share cost = (qty × avgCost) / (qty + bonusWhole)
                double avgCost    = positionPort.getAvgCostBasis(holder.clientId(), instrumentId);
                double totalCost  = holder.quantity() * avgCost;
                long   newTotal   = (long) holder.quantity() + bonusWhole;
                double newAvgCost = newTotal == 0 ? 0 : totalCost / newTotal;

                positionPort.addShares(holder.clientId(), instrumentId, bonusWhole, settleDate);

                StockEntitlement ent = persistEntitlement(caId, holder.clientId(), bonusWhole,
                        cashInLieu, newAvgCost);
                eventPort.publish("ca.stock_entitlement.calculated", ent);
                entitlementCounter.increment();
                results.add(ent);
            }
            return results;
        });
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private StockEntitlement persistEntitlement(String caId, String clientId, long bonusShares,
                                                 BigDecimal cashInLieu, double newAvgCost) throws SQLException {
        String entId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO ca_stock_entitlements
                    (entitlement_id, ca_id, client_id, bonus_shares, cash_in_lieu,
                     new_avg_cost_per_share, calculated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (ca_id, client_id) DO UPDATE
                SET bonus_shares=EXCLUDED.bonus_shares, cash_in_lieu=EXCLUDED.cash_in_lieu,
                    new_avg_cost_per_share=EXCLUDED.new_avg_cost_per_share, calculated_at=NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entId); ps.setString(2, caId); ps.setString(3, clientId);
            ps.setLong(4, bonusShares); ps.setBigDecimal(5, cashInLieu); ps.setDouble(6, newAvgCost);
            ps.executeUpdate();
        }
        return new StockEntitlement(entId, caId, clientId, bonusShares, cashInLieu, newAvgCost,
                LocalDateTime.now());
    }

    record HolderRow(String clientId, double quantity) {}

    private List<HolderRow> loadSnapshot(String caId) throws SQLException {
        String sql = "SELECT client_id, quantity FROM ca_holder_snapshots WHERE ca_id=?";
        List<HolderRow> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new HolderRow(rs.getString("client_id"), rs.getDouble("quantity")));
            }
        }
        return result;
    }
}

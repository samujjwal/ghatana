package com.ghatana.appplatform.posttrade.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Bilateral netting: net obligations between counterparty pairs (D09-003).
 *              Groups ACKNOWLEDGED trade confirmations by (counterparty pair + instrument +
 *              settlement_date), computes net_qty = Σbuys - Σsells, reduces settlement
 *              obligations, and persists a netting_set and netting_positions.
 * @doc.layer   Domain — Post-Trade netting
 * @doc.pattern Batch: EOD cutoff; netting efficiency = gross_count / net_count reduction
 */
public class BilateralNettingService {

    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    public record NettingPosition(
        String nettingPositionId,
        String nettingSetId,
        String participantId,
        String counterpartyId,
        String instrumentId,
        LocalDate settlementDate,
        long netQuantity,          // positive = net buy, negative = net sell
        BigDecimal netCash,
        String currency
    ) {}

    public record NettingResult(
        String nettingSetId,
        LocalDate runDate,
        int grossTradeCount,
        int netPositionCount,
        BigDecimal nettingEfficiencyRatio,  // 1 - (netCount / grossCount)
        List<NettingPosition> positions
    ) {}

    private final DataSource dataSource;
    private final Executor executor;
    private final Counter nettingRunsCounter;

    public BilateralNettingService(DataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.nettingRunsCounter = Counter.builder("posttrade.netting.bilateral_runs_total").register(registry);
    }

    /**
     * Run bilateral netting for all ACKNOWLEDGED confirmations through the current EOD cutoff.
     * Creates one netting_set and netting_positions for each counterparty pair.
     */
    public Promise<NettingResult> runEodNetting(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> {
            String nettingSetId = UUID.randomUUID().toString();

            List<RawTrade> rawTrades = loadAcknowledgedTrades(runDate);
            Map<NettingKey, Long> netQtyMap = new HashMap<>();
            Map<NettingKey, BigDecimal> netCashMap = new HashMap<>();
            Map<NettingKey, String> currencyMap = new HashMap<>();

            for (RawTrade t : rawTrades) {
                NettingKey key = new NettingKey(t.clientId, t.counterpartyId,
                    t.instrumentId, t.settlementDate);
                long qty = "BUY".equals(t.side) ? t.quantity : -t.quantity;
                BigDecimal cash = t.price.multiply(BigDecimal.valueOf(t.quantity));
                if ("SELL".equals(t.side)) cash = cash.negate();

                netQtyMap.merge(key, qty, Long::sum);
                netCashMap.merge(key, cash, BigDecimal::add);
                currencyMap.putIfAbsent(key, t.currency);
            }

            // Persist netting set
            persistNettingSet(nettingSetId, "BILATERAL", runDate, rawTrades.size());

            // Persist net positions
            List<NettingPosition> positions = new ArrayList<>();
            for (Map.Entry<NettingKey, Long> entry : netQtyMap.entrySet()) {
                NettingKey k = entry.getKey();
                NettingPosition pos = new NettingPosition(UUID.randomUUID().toString(), nettingSetId,
                    k.participantId, k.counterpartyId, k.instrumentId, k.settlementDate,
                    entry.getValue(), netCashMap.get(k), currencyMap.get(k));
                persistNettingPosition(pos);
                positions.add(pos);
            }

            BigDecimal efficiency = rawTrades.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.ONE.subtract(
                    BigDecimal.valueOf(positions.size())
                        .divide(BigDecimal.valueOf(rawTrades.size()), 4,
                            java.math.RoundingMode.HALF_UP));

            nettingRunsCounter.increment();
            return new NettingResult(nettingSetId, runDate, rawTrades.size(),
                positions.size(), efficiency, positions);
        });
    }

    private record NettingKey(String participantId, String counterpartyId,
                               String instrumentId, LocalDate settlementDate) {}

    private record RawTrade(String clientId, String counterpartyId, String instrumentId,
                             String side, long quantity, BigDecimal price,
                             String currency, LocalDate settlementDate) {}

    private List<RawTrade> loadAcknowledgedTrades(LocalDate runDate) throws Exception {
        List<RawTrade> trades = new ArrayList<>();
        String sql = "SELECT tc.client_id, o.counterparty_id, tc.instrument_id, tc.side, " +
                     "tc.quantity, tc.price, tc.currency, tc.settlement_date " +
                     "FROM trade_confirmations tc " +
                     "JOIN orders o ON o.id = tc.order_id " +
                     "WHERE tc.status = 'ACKNOWLEDGED' AND tc.trade_date = ? " +
                     "AND tc.netting_set_id IS NULL";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    trades.add(new RawTrade(rs.getString("client_id"), rs.getString("counterparty_id"),
                        rs.getString("instrument_id"), rs.getString("side"),
                        rs.getLong("quantity"), rs.getBigDecimal("price"),
                        rs.getString("currency"), rs.getDate("settlement_date").toLocalDate()));
                }
            }
        }
        return trades;
    }

    private void persistNettingSet(String id, String type, LocalDate runDate, int grossCount) throws Exception {
        String sql = "INSERT INTO netting_sets(id, netting_type, run_date, cutoff_time, " +
                     "status, gross_trade_count) VALUES(?,?,?,NOW(),'COMPLETED',?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(id));
            ps.setString(2, type);
            ps.setObject(3, runDate);
            ps.setInt(4, grossCount);
            ps.executeUpdate();
        }
    }

    private void persistNettingPosition(NettingPosition pos) throws Exception {
        String sql = "INSERT INTO netting_positions(id, netting_set_id, participant_id, counterparty_id, " +
                     "instrument_id, settlement_date, net_quantity, net_cash, currency) VALUES(?,?,?,?,?,?,?,?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(pos.nettingPositionId()));
            ps.setObject(2, UUID.fromString(pos.nettingSetId()));
            ps.setObject(3, UUID.fromString(pos.participantId()));
            ps.setObject(4, pos.counterpartyId() != null ? UUID.fromString(pos.counterpartyId()) : null);
            ps.setObject(5, UUID.fromString(pos.instrumentId()));
            ps.setObject(6, pos.settlementDate());
            ps.setLong(7, pos.netQuantity());
            ps.setBigDecimal(8, pos.netCash());
            ps.setString(9, pos.currency());
            ps.executeUpdate();
        }
    }
}

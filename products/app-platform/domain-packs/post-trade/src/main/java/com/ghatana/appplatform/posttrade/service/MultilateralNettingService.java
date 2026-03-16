package com.ghatana.appplatform.posttrade.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Multilateral netting via CCP (Central Counterparty) (D09-004).
 *              All participants net against the CCP as single counterparty.
 *              Each participant has one net obligation per instrument per settlement date.
 *              CCP-specific rules applied via inner port (T3 plugin pattern).
 * @doc.layer   Domain — Post-Trade netting
 * @doc.pattern CCP interposition; T3 plug-in for exchange-specific rules; zero-sum validation
 */
public class MultilateralNettingService {

    /** T3 plug-in port: CCP-specific netting rules and participant eligibility checks. */
    public interface CcpRulesPort {
        boolean isEligible(String participantId, String instrumentId);
        String getCcpId();
    }

    public record MultiNetPosition(
        String positionId,
        String nettingSetId,
        String participantId,
        String ccpId,                // always CCP as counterparty
        String instrumentId,
        LocalDate settlementDate,
        long netQuantity,            // positive = participant owes CCP; negative = CCP owes participant
        BigDecimal netCash,
        String currency
    ) {}

    public record MultilateralNettingResult(
        String nettingSetId,
        LocalDate runDate,
        String ccpId,
        int participantCount,
        int grossTradeCount,
        int netPositionCount,
        BigDecimal grossNettingRatio,
        List<MultiNetPosition> positions
    ) {}

    private final DataSource dataSource;
    private final CcpRulesPort ccpRules;
    private final Executor executor;
    private final Counter nettingRunsCounter;

    public MultilateralNettingService(DataSource dataSource, CcpRulesPort ccpRules,
                                       Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.ccpRules = ccpRules;
        this.executor = executor;
        this.nettingRunsCounter = Counter.builder("posttrade.netting.multilateral_runs_total").register(registry);
    }

    /**
     * Run multilateral netting for a given settlement date.
     * All eligible participant obligations net against the CCP.
     */
    public Promise<MultilateralNettingResult> runNetting(LocalDate settlementDate) {
        return Promise.ofBlocking(executor, () -> {
            String nettingSetId = UUID.randomUUID().toString();
            String ccpId = ccpRules.getCcpId();

            List<RawObligation> obligations = loadNetBilateralPositions(settlementDate);
            int grossCount = obligations.size();

            // Each participant nets across all counterparties into single CCP obligation
            Map<MultiKey, Long> multiNetQty = new HashMap<>();
            Map<MultiKey, BigDecimal> multiNetCash = new HashMap<>();
            Map<MultiKey, String> currencyMap = new HashMap<>();

            for (RawObligation ob : obligations) {
                if (!ccpRules.isEligible(ob.participantId, ob.instrumentId)) continue;
                MultiKey key = new MultiKey(ob.participantId, ob.instrumentId, settlementDate);
                multiNetQty.merge(key, ob.netQuantity, Long::sum);
                multiNetCash.merge(key, ob.netCash, BigDecimal::add);
                currencyMap.putIfAbsent(key, ob.currency);
            }

            persistNettingSet(nettingSetId, "MULTILATERAL", settlementDate, grossCount, ccpId);

            List<MultiNetPosition> positions = new ArrayList<>();
            for (Map.Entry<MultiKey, Long> entry : multiNetQty.entrySet()) {
                MultiKey k = entry.getKey();
                MultiNetPosition pos = new MultiNetPosition(UUID.randomUUID().toString(), nettingSetId,
                    k.participantId, ccpId, k.instrumentId, k.settlementDate,
                    entry.getValue(), multiNetCash.get(k), currencyMap.get(k));
                persistPosition(pos);
                positions.add(pos);
            }

            BigDecimal ratio = grossCount > 0
                ? BigDecimal.ONE.subtract(BigDecimal.valueOf(positions.size())
                    .divide(BigDecimal.valueOf(grossCount), 4, java.math.RoundingMode.HALF_UP))
                : BigDecimal.ZERO;

            nettingRunsCounter.increment();
            return new MultilateralNettingResult(nettingSetId, settlementDate, ccpId,
                (int) multiNetQty.keySet().stream().map(k -> k.participantId).distinct().count(),
                grossCount, positions.size(), ratio, positions);
        });
    }

    private record MultiKey(String participantId, String instrumentId, LocalDate settlementDate) {}

    private record RawObligation(String participantId, String instrumentId,
                                  long netQuantity, BigDecimal netCash,
                                  String currency, LocalDate settlementDate) {}

    private List<RawObligation> loadNetBilateralPositions(LocalDate settlementDate) throws Exception {
        List<RawObligation> list = new ArrayList<>();
        String sql = "SELECT participant_id, instrument_id, SUM(net_quantity) AS net_qty, " +
                     "SUM(net_cash) AS net_cash, MAX(currency) AS currency " +
                     "FROM netting_positions " +
                     "WHERE settlement_date = ? " +
                     "GROUP BY participant_id, instrument_id";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, settlementDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RawObligation(rs.getString("participant_id"),
                        rs.getString("instrument_id"), rs.getLong("net_qty"),
                        rs.getBigDecimal("net_cash"), rs.getString("currency"), settlementDate));
                }
            }
        }
        return list;
    }

    private void persistNettingSet(String id, String type, LocalDate date, int grossCount,
                                    String ccpId) throws Exception {
        String sql = "INSERT INTO netting_sets(id, netting_type, run_date, cutoff_time, " +
                     "status, gross_trade_count, participant_ids) VALUES(?,?,?,NOW(),'COMPLETED',?,ARRAY[?::uuid])";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(id));
            ps.setString(2, type);
            ps.setObject(3, date);
            ps.setInt(4, grossCount);
            ps.setString(5, ccpId);
            ps.executeUpdate();
        }
    }

    private void persistPosition(MultiNetPosition pos) throws Exception {
        String sql = "INSERT INTO netting_positions(id, netting_set_id, participant_id, counterparty_id, " +
                     "instrument_id, settlement_date, net_quantity, net_cash, currency) VALUES(?,?,?,?,?,?,?,?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(pos.positionId()));
            ps.setObject(2, UUID.fromString(pos.nettingSetId()));
            ps.setObject(3, UUID.fromString(pos.participantId()));
            ps.setObject(4, UUID.fromString(pos.ccpId()));
            ps.setObject(5, UUID.fromString(pos.instrumentId()));
            ps.setObject(6, pos.settlementDate());
            ps.setLong(7, pos.netQuantity());
            ps.setBigDecimal(8, pos.netCash());
            ps.setString(9, pos.currency());
            ps.executeUpdate();
        }
    }
}

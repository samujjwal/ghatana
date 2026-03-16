package com.ghatana.appplatform.ems.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Computes Transaction Cost Analysis (TCA) for a completed or in-progress order (D02-003).
 *              Measures arrival price vs execution price (implementation shortfall), timing cost,
 *              and market impact. Results stored in tca_records for reporting and best-execution audit.
 * @doc.layer   Domain — EMS best execution analytics
 * @doc.pattern Stateless compute; audit trail persisted per order; MiFID II best execution evidence
 */
public class BestExecutionAnalysisService {

    public record TcaResult(
        String tcaId,
        String orderId,
        BigDecimal arrivalPrice,
        BigDecimal executedVwap,
        BigDecimal implementationShortfallBps,   // (execVwap - arrival) / arrival * 10000
        BigDecimal timingCostBps,                // benchmark VWAP vs arrival
        BigDecimal marketImpactBps,              // execVwap vs intraday VWAP
        BigDecimal totalCostBps,                 // IS + explicit fees
        String venue,
        Instant analysedAt
    ) {}

    private static final BigDecimal BPS = BigDecimal.valueOf(10_000.0);

    private final DataSource dataSource;
    private final Executor executor;
    private final Counter tcaRunCounter;

    public BestExecutionAnalysisService(DataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.tcaRunCounter = Counter.builder("ems.tca.analyses_total").register(registry);
    }

    /**
     * Run TCA for a completed order. arrivalPrice is the mid-price at time of order receipt.
     * intradayVwap is the market VWAP for the instrument over the order's execution window.
     */
    public Promise<TcaResult> analyse(String orderId, BigDecimal arrivalPrice,
                                      BigDecimal executedVwap, BigDecimal intradayVwap,
                                      String venue) {
        return Promise.ofBlocking(executor, () -> {
            BigDecimal isBps   = relativeBps(executedVwap.subtract(arrivalPrice), arrivalPrice);
            BigDecimal timingBps   = relativeBps(intradayVwap.subtract(arrivalPrice), arrivalPrice);
            BigDecimal impactBps   = relativeBps(executedVwap.subtract(intradayVwap), intradayVwap);
            BigDecimal totalBps    = isBps;  // explicit fees added by caller if available

            TcaResult result = new TcaResult(UUID.randomUUID().toString(), orderId,
                arrivalPrice, executedVwap, isBps, timingBps, impactBps, totalBps,
                venue, Instant.now());

            persistTca(result);
            tcaRunCounter.increment();
            return result;
        });
    }

    private BigDecimal relativeBps(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP).multiply(BPS)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private void persistTca(TcaResult r) throws Exception {
        String sql = "INSERT INTO tca_records(id, order_id, arrival_price, executed_vwap, " +
                     "implementation_shortfall_bps, timing_cost_bps, market_impact_bps, " +
                     "total_cost_bps, venue, analysed_at) VALUES(?,?,?,?,?,?,?,?,?,NOW())";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(r.tcaId()));
            ps.setObject(2, UUID.fromString(r.orderId()));
            ps.setBigDecimal(3, r.arrivalPrice());
            ps.setBigDecimal(4, r.executedVwap());
            ps.setBigDecimal(5, r.implementationShortfallBps());
            ps.setBigDecimal(6, r.timingCostBps());
            ps.setBigDecimal(7, r.marketImpactBps());
            ps.setBigDecimal(8, r.totalCostBps());
            ps.setString(9, r.venue());
            ps.executeUpdate();
        }
    }
}

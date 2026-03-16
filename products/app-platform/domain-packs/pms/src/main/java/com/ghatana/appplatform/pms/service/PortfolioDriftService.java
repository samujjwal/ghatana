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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Detect portfolio drift: |actual_weight - target_weight| per instrument.
 *              K-02 ConfigPort supplies per-instrument and global drift thresholds (default 5%).
 *              On drift detection, a DriftEvent is persisted and published via DriftAlertPort.
 *              Drift heatmap data (all instrument weights) is included in every event.
 *              Satisfies STORY-D03-007.
 * @doc.layer   Domain
 * @doc.pattern Event-driven drift detection; K-02 thresholds; heatmap data encapsulated;
 *              Counter for total drift events and critical drift events.
 */
public class PortfolioDriftService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioDriftService.class);
    private static final BigDecimal DEFAULT_DRIFT_THRESHOLD = new BigDecimal("0.05");

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ConfigPort       configPort;
    private final DriftAlertPort   alertPort;
    private final Counter          driftEventCounter;
    private final Counter          criticalDriftCounter;

    public PortfolioDriftService(HikariDataSource dataSource, Executor executor,
                                  ConfigPort configPort, DriftAlertPort alertPort,
                                  MeterRegistry registry) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.configPort          = configPort;
        this.alertPort           = alertPort;
        this.driftEventCounter   = Counter.builder("pms.drift.events_total").register(registry);
        this.criticalDriftCounter= Counter.builder("pms.drift.critical_total").register(registry);
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    /** K-02 configuration thresholds. */
    public interface ConfigPort {
        BigDecimal getDriftThreshold(String portfolioId, String instrumentId);
    }

    /** Downstream alert port (e.g., notification service or event bus). */
    public interface DriftAlertPort {
        void publishDriftDetected(DriftEvent event);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record InstrumentDrift(String instrumentId, BigDecimal targetWeight,
                                  BigDecimal actualWeight, BigDecimal driftPct, boolean breached) {}

    public record DriftEvent(String eventId, String portfolioId, LocalDateTime detectedAt,
                             List<InstrumentDrift> breachedInstruments,
                             List<InstrumentDrift> heatmap) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<DriftEvent> checkDrift(String portfolioId) {
        return Promise.ofBlocking(executor, () -> detectDrift(portfolioId));
    }

    // ─── Drift detection ─────────────────────────────────────────────────────

    private DriftEvent detectDrift(String portfolioId) throws SQLException {
        List<InstrumentDrift> heatmap = loadHeatmap(portfolioId);
        List<InstrumentDrift> breached = new ArrayList<>();

        for (InstrumentDrift d : heatmap) {
            BigDecimal threshold = configPort.getDriftThreshold(portfolioId, d.instrumentId());
            if (threshold == null) threshold = DEFAULT_DRIFT_THRESHOLD;
            if (d.driftPct().compareTo(threshold) > 0) {
                breached.add(d);
            }
        }

        DriftEvent event = new DriftEvent(UUID.randomUUID().toString(), portfolioId,
                LocalDateTime.now(), breached, heatmap);

        if (!breached.isEmpty()) {
            persistDriftEvent(event);
            alertPort.publishDriftDetected(event);
            driftEventCounter.increment();
            long criticalCount = breached.stream()
                    .filter(d -> d.driftPct().compareTo(new BigDecimal("0.10")) > 0).count();
            if (criticalCount > 0) criticalDriftCounter.increment(criticalCount);
            log.info("Drift detected for portfolio={} breachedCount={}", portfolioId, breached.size());
        }
        return event;
    }

    private List<InstrumentDrift> loadHeatmap(String portfolioId) throws SQLException {
        List<InstrumentDrift> list = new ArrayList<>();
        String sql = """
                SELECT ta.instrument_id,
                       ta.target_weight,
                       COALESCE(ah.actual_weight, 0) AS actual_weight,
                       ABS(COALESCE(ah.actual_weight, 0) - ta.target_weight) AS drift_pct
                FROM target_allocations ta
                LEFT JOIN (
                    SELECT instrument_id,
                           (market_value / NULLIF(SUM(market_value) OVER (PARTITION BY portfolio_id), 0))
                               AS actual_weight
                    FROM portfolio_holdings_latest
                    WHERE portfolio_id = ?
                ) ah ON ah.instrument_id = ta.instrument_id
                WHERE ta.portfolio_id = ?
                ORDER BY drift_pct DESC
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            ps.setString(2, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal target = rs.getBigDecimal("target_weight");
                    BigDecimal actual = rs.getBigDecimal("actual_weight");
                    BigDecimal drift  = rs.getBigDecimal("drift_pct").setScale(6, RoundingMode.HALF_UP);
                    list.add(new InstrumentDrift(rs.getString("instrument_id"),
                            target, actual, drift, false)); // breached flag set later
                }
            }
        }
        return list;
    }

    private void persistDriftEvent(DriftEvent event) throws SQLException {
        String sql = """
                INSERT INTO portfolio_drift_events (event_id, portfolio_id, detected_at,
                    breach_count, event_payload)
                VALUES (?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (event_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.eventId());
            ps.setString(2, event.portfolioId());
            ps.setObject(3, event.detectedAt());
            ps.setInt(4, event.breachedInstruments().size());
            ps.setString(5, buildPayloadJson(event));
            ps.executeUpdate();
        }
    }

    private String buildPayloadJson(DriftEvent event) {
        StringBuilder sb = new StringBuilder("{\"breached\":[");
        List<InstrumentDrift> b = event.breachedInstruments();
        for (int i = 0; i < b.size(); i++) {
            InstrumentDrift d = b.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"instrumentId\":\"").append(d.instrumentId())
              .append("\",\"driftPct\":").append(d.driftPct()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
}

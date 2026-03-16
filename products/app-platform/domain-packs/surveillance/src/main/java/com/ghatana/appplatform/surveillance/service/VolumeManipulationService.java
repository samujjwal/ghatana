package com.ghatana.appplatform.surveillance.service;

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
 * @doc.purpose Detect artificial volume inflation: client generates high trade volume without
 *              genuine economic purpose. Metrics: trade_volume/normal_volume ratio,
 *              order-to-trade ratio, self-trade percentage. Alert if volume ratio &gt; 5×
 *              10-day normal for client-instrument pair. Configurable market-maker exclusions.
 *              K-02 ConfigPort for thresholds. Satisfies STORY-D08-003.
 * @doc.layer   Domain
 * @doc.pattern Threshold-based surveillance; 10-day rolling baseline; market-maker exclusion;
 *              K-02 ConfigPort; Counter for alerts generated.
 */
public class VolumeManipulationService {

    private static final double DEFAULT_VOLUME_RATIO_THRESHOLD   = 5.0;
    private static final double DEFAULT_ORDER_TO_TRADE_THRESHOLD = 10.0;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ConfigPort       configPort;
    private final AlertPort        alertPort;
    private final Counter          alertCounter;

    public VolumeManipulationService(HikariDataSource dataSource, Executor executor,
                                      ConfigPort configPort, AlertPort alertPort,
                                      MeterRegistry registry) {
        this.dataSource   = dataSource;
        this.executor     = executor;
        this.configPort   = configPort;
        this.alertPort    = alertPort;
        this.alertCounter = Counter.builder("surveillance.volume_manipulation.alerts_total").register(registry);
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    /** K-02 configurable thresholds and market-maker list. */
    public interface ConfigPort {
        double getVolumeRatioThreshold();
        double getOrderToTradeThreshold();
        boolean isMarketMaker(String clientId);
    }

    /** Downstream alert service. */
    public interface AlertPort {
        String persistAlert(String clientId, String instrumentId, String alertType,
                            String description, String severity, LocalDate runDate);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record VolumeAlert(String alertId, String clientId, String instrumentId,
                              LocalDate runDate, double volumeRatio, double normalVolume,
                              double actualVolume, double orderToTradeRatio,
                              double selfTradePct, String severity) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<VolumeAlert>> detectVolumeManipulation(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> detect(runDate));
    }

    // ─── Detection logic ─────────────────────────────────────────────────────

    private List<VolumeAlert> detect(LocalDate runDate) throws SQLException {
        List<VolumeAlert> alerts = new ArrayList<>();
        double volThreshold = configPort.getVolumeRatioThreshold();
        if (volThreshold == 0.0) volThreshold = DEFAULT_VOLUME_RATIO_THRESHOLD;
        double ottThreshold = configPort.getOrderToTradeThreshold();
        if (ottThreshold == 0.0) ottThreshold = DEFAULT_ORDER_TO_TRADE_THRESHOLD;

        List<ClientInstrumentVolume> rows = loadClientVolumes(runDate);
        for (ClientInstrumentVolume row : rows) {
            if (configPort.isMarketMaker(row.clientId())) continue;
            if (row.normalVolume() == 0.0) continue;

            double volumeRatio = row.actualVolume() / row.normalVolume();
            boolean spiked     = volumeRatio > volThreshold;
            boolean highOtt    = row.orderToTradeRatio() > ottThreshold;

            if (spiked || highOtt) {
                String severity = volumeRatio > volThreshold * 2 ? "HIGH" : "MEDIUM";
                String desc = "Volume ratio=%.2f (threshold=%.1f), O/T ratio=%.2f".formatted(
                        volumeRatio, volThreshold, row.orderToTradeRatio());
                String alertId = alertPort.persistAlert(row.clientId(), row.instrumentId(),
                        "VOLUME_MANIPULATION", desc, severity, runDate);
                alerts.add(new VolumeAlert(alertId, row.clientId(), row.instrumentId(), runDate,
                        volumeRatio, row.normalVolume(), row.actualVolume(),
                        row.orderToTradeRatio(), row.selfTradePct(), severity));
                alertCounter.increment();
            }
        }
        return alerts;
    }

    private record ClientInstrumentVolume(String clientId, String instrumentId,
                                          double actualVolume, double normalVolume,
                                          double orderToTradeRatio, double selfTradePct) {}

    private List<ClientInstrumentVolume> loadClientVolumes(LocalDate runDate) throws SQLException {
        List<ClientInstrumentVolume> rows = new ArrayList<>();
        String sql = """
                SELECT t.client_id, t.instrument_id,
                       SUM(t.quantity) AS actual_volume,
                       AVG(b.avg_10d_volume) AS normal_volume,
                       CAST(COUNT(o.order_id) AS DOUBLE PRECISION) /
                           NULLIF(SUM(t.quantity), 0) AS order_to_trade_ratio,
                       COALESCE(
                           SUM(CASE WHEN t.counterparty_client_id = t.client_id THEN t.quantity ELSE 0 END)::decimal /
                           NULLIF(SUM(t.quantity), 0), 0) * 100 AS self_trade_pct
                FROM trades t
                LEFT JOIN (
                    SELECT client_id, instrument_id,
                           AVG(daily_volume) AS avg_10d_volume
                    FROM (
                        SELECT client_id, instrument_id, trade_date,
                               SUM(quantity) AS daily_volume
                        FROM trades
                        WHERE trade_date BETWEEN ? - INTERVAL '10 days' AND ? - INTERVAL '1 day'
                        GROUP BY client_id, instrument_id, trade_date
                    ) d GROUP BY client_id, instrument_id
                ) b ON b.client_id = t.client_id AND b.instrument_id = t.instrument_id
                LEFT JOIN orders o ON o.client_id = t.client_id AND o.instrument_id = t.instrument_id
                    AND DATE(o.submitted_at) = ?
                WHERE t.trade_date = ? AND t.status = 'SETTLED'
                GROUP BY t.client_id, t.instrument_id
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            ps.setObject(2, runDate);
            ps.setObject(3, runDate);
            ps.setObject(4, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ClientInstrumentVolume(rs.getString("client_id"),
                            rs.getString("instrument_id"), rs.getDouble("actual_volume"),
                            rs.getDouble("normal_volume"), rs.getDouble("order_to_trade_ratio"),
                            rs.getDouble("self_trade_pct")));
                }
            }
        }
        return rows;
    }
}

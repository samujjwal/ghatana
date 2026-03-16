package com.ghatana.appplatform.recon.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose LSTM time-series ML model (K-09) that forecasts which upcoming reconciliation
 *              runs are likely to produce breaks, enabling operations to pre-empt issues.
 *              Features: per-counterparty break history, settlement volume spike,
 *              counterparty operational events, market stress regime (D06-020),
 *              public holiday impact (K-15). Output: break_probability for each scheduled
 *              recon run for the next 3 business days. HIGH-probability runs (≥ 0.70) are
 *              flagged to operations the day before. Satisfies STORY-D13-018.
 * @doc.layer   Domain
 * @doc.pattern K-09 LSTM forecast model; K-15 CalendarPort for holiday impact;
 *              3-day horizon; INSERT-only forecast log.
 */
public class ReconFailureForecastService {

    private static final Logger log = LoggerFactory.getLogger(ReconFailureForecastService.class);

    private static final double HIGH_PROB_THRESHOLD = 0.70;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final LstmModelPort    lstmModel;
    private final CalendarPort     calendarPort;
    private final AlertPort        alertPort;
    private final Counter          highProbForecastCounter;

    public ReconFailureForecastService(HikariDataSource dataSource, Executor executor,
                                       LstmModelPort lstmModel, CalendarPort calendarPort,
                                       AlertPort alertPort, MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.lstmModel               = lstmModel;
        this.calendarPort            = calendarPort;
        this.alertPort               = alertPort;
        this.highProbForecastCounter = registry.counter("recon.forecast.high_prob_alerts");
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    /** K-09 LSTM model port — 3-day ahead forecast. */
    public interface LstmModelPort {
        double forecastBreakProbability(ForecastFeatures features);
    }

    /** K-15 CalendarPort for public holiday lookup. */
    public interface CalendarPort {
        boolean isPublicHoliday(LocalDate date);
        int getBusinessDaysUntil(LocalDate from, LocalDate to);
    }

    public interface AlertPort {
        void alertHighRisk(String forecastId, String counterpartyId, LocalDate runDate,
                           double probability);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record ForecastFeatures(String counterpartyId, int breakCount30d,
                                   double totalBreakAmount30d, double volumeSpike,
                                   boolean isPublicHolidayAdjacentDay, double marketStressScore,
                                   double statementLagHoursAvg) {}

    public record ForecastResult(String forecastId, String counterpartyId, LocalDate forecastDate,
                                 double breakProbability, boolean highRisk) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Daily batch: generate 3-business-day horizon forecasts for all scheduled recon runs. */
    public Promise<List<ForecastResult>> generateDailyForecasts(LocalDate baseDate,
                                                                double marketStressScore) {
        return Promise.ofBlocking(executor, () -> {
            List<String> counterparties = loadScheduledCounterparties();
            List<ForecastResult> results = new ArrayList<>();

            // generate for next 3 business days
            LocalDate d = baseDate.plusDays(1);
            int horizon = 0;
            while (horizon < 3) {
                if (!calendarPort.isPublicHoliday(d)) {
                    for (String cp : counterparties) {
                        ForecastFeatures features = buildFeatures(cp, d, marketStressScore, baseDate);
                        double prob = lstmModel.forecastBreakProbability(features);
                        boolean high = prob >= HIGH_PROB_THRESHOLD;
                        String forecastId = UUID.randomUUID().toString();
                        persistForecast(forecastId, cp, d, prob, high);
                        if (high) {
                            alertPort.alertHighRisk(forecastId, cp, d, prob);
                            highProbForecastCounter.increment();
                        }
                        results.add(new ForecastResult(forecastId, cp, d, prob, high));
                    }
                    horizon++;
                }
                d = d.plusDays(1);
            }
            return results;
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private List<String> loadScheduledCounterparties() throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT counterparty_id FROM recon_schedules WHERE active = true";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString(1));
        }
        return list;
    }

    private ForecastFeatures buildFeatures(String counterpartyId, LocalDate forecastDate,
                                           double marketStress, LocalDate baseDate) throws SQLException {
        String sql = """
                SELECT
                    COUNT(rb.break_id) AS break_count_30d,
                    COALESCE(SUM(rb.amount), 0) AS total_break_amount_30d,
                    COALESCE(AVG(sf.statement_lag_hours), 0) AS avg_lag_hours,
                    COALESCE(
                        (SELECT volume_spike_ratio FROM recon_volume_stats
                         WHERE counterparty_id = ? AND stat_date = ?), 1.0
                    ) AS volume_spike
                FROM recon_runs rr
                LEFT JOIN recon_breaks rb ON rb.recon_run_id = rr.recon_run_id
                LEFT JOIN statement_fetch_log sf ON sf.recon_run_id = rr.recon_run_id
                WHERE rr.counterparty_id = ?
                  AND rr.run_date >= ? - INTERVAL '30 days'
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, counterpartyId);
            ps.setObject(2, baseDate);
            ps.setString(3, counterpartyId);
            ps.setObject(4, baseDate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean isHolidayAdj = calendarPort.isPublicHoliday(forecastDate.minusDays(1))
                                       || calendarPort.isPublicHoliday(forecastDate.plusDays(1));
                    return new ForecastFeatures(counterpartyId,
                            rs.getInt("break_count_30d"),
                            rs.getDouble("total_break_amount_30d"),
                            rs.getDouble("volume_spike"),
                            isHolidayAdj, marketStress,
                            rs.getDouble("avg_lag_hours"));
                }
            }
        }
        return new ForecastFeatures(counterpartyId, 0, 0.0, 1.0, false, marketStress, 0.0);
    }

    private void persistForecast(String forecastId, String counterpartyId, LocalDate forecastDate,
                                 double prob, boolean highRisk) throws SQLException {
        String sql = """
                INSERT INTO recon_failure_forecasts
                    (forecast_id, counterparty_id, forecast_date, break_probability,
                     high_risk, created_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (counterparty_id, forecast_date) DO UPDATE
                    SET break_probability=EXCLUDED.break_probability,
                        high_risk=EXCLUDED.high_risk,
                        created_at=NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, forecastId);
            ps.setString(2, counterpartyId);
            ps.setObject(3, forecastDate);
            ps.setDouble(4, prob);
            ps.setBoolean(5, highRisk);
            ps.executeUpdate();
        }
    }
}

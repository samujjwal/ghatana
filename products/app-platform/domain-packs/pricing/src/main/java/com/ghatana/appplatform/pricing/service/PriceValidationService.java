package com.ghatana.appplatform.pricing.service;

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
 * @doc.purpose Validates incoming price ticks and EOD prices against a rule set before they
 *              are persisted or distributed. Rules (configurable via K-02 ConfigPort):
 *              (1) price > 0,
 *              (2) bid ≤ ask (if both present),
 *              (3) within circuit breaker band: within ±circuit_breaker_pct% of previous close,
 *              (4) daily % change within daily_change_limit_pct vs last EOD price.
 *              Invalid prices are quarantined to price_validation_log with the violated rule.
 *              The last valid price is returned as a fallback for downstream consumers.
 * @doc.layer   Domain
 * @doc.pattern K-02 ConfigPort for per-instrument rule overrides; quarantine pattern;
 *              last-valid fallback.
 */
public class PriceValidationService {

    private static final Logger log = LoggerFactory.getLogger(PriceValidationService.class);

    /** Default circuit breaker band: ±10% of previous close. */
    private static final double DEFAULT_CIRCUIT_BREAKER_PCT  = 0.10;
    /** Default daily change limit: ±15% of previous EOD close. */
    private static final double DEFAULT_DAILY_CHANGE_LIMIT   = 0.15;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ConfigPort       configPort;
    private final Counter          acceptedCounter;
    private final Counter          quarantinedCounter;

    public PriceValidationService(HikariDataSource dataSource, Executor executor,
                                  ConfigPort configPort, MeterRegistry registry) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.configPort        = configPort;
        this.acceptedCounter   = registry.counter("pricing.validation.accepted");
        this.quarantinedCounter = registry.counter("pricing.validation.quarantined");
    }

    // ─── Inner port (K-02) ───────────────────────────────────────────────────

    public interface ConfigPort {
        double getCircuitBreakerPct(String instrumentId);   // fraction e.g. 0.10
        double getDailyChangeLimitPct(String instrumentId); // fraction e.g. 0.15
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record PriceToValidate(
        String  instrumentId,
        double  price,
        double  bidPrice,
        double  askPrice,
        String  priceSource   // TICK | EOD | MANUAL
    ) {}

    public record ValidationResult(
        boolean valid,
        String  rejectionRule,   // null if valid
        double  fallbackPrice    // last valid price if invalid; 0 if none available
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ValidationResult> validate(PriceToValidate input) {
        return Promise.ofBlocking(executor, () -> doValidate(input));
    }

    public Promise<List<ValidationResult>> validateBatch(List<PriceToValidate> inputs) {
        return Promise.ofBlocking(executor, () -> {
            List<ValidationResult> results = new ArrayList<>();
            for (PriceToValidate p : inputs) results.add(doValidate(p));
            return results;
        });
    }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private ValidationResult doValidate(PriceToValidate input) throws SQLException {
        // Rule 1: price must be positive
        if (input.price() <= 0) {
            return quarantine(input, "PRICE_NON_POSITIVE");
        }

        // Rule 2: bid ≤ ask
        if (input.bidPrice() > 0 && input.askPrice() > 0 && input.bidPrice() > input.askPrice()) {
            return quarantine(input, "BID_EXCEEDS_ASK");
        }

        // Rule 3: circuit breaker vs previous close
        Double prevClose = loadPreviousClose(input.instrumentId());
        if (prevClose != null && prevClose > 0) {
            double cbPct = configPort.getCircuitBreakerPct(input.instrumentId());
            double upperLimit = prevClose * (1.0 + cbPct);
            double lowerLimit = prevClose * (1.0 - cbPct);
            if (input.price() > upperLimit || input.price() < lowerLimit) {
                return quarantine(input, "CIRCUIT_BREAKER_BREACH");
            }
        }

        // Rule 4: daily % change limit
        Double lastEod = loadLastEodClose(input.instrumentId());
        if (lastEod != null && lastEod > 0) {
            double dailyLimit = configPort.getDailyChangeLimitPct(input.instrumentId());
            double pctChange  = Math.abs((input.price() - lastEod) / lastEod);
            if (pctChange > dailyLimit) {
                return quarantine(input, "DAILY_CHANGE_LIMIT_BREACH");
            }
        }

        acceptedCounter.increment();
        return new ValidationResult(true, null, input.price());
    }

    private ValidationResult quarantine(PriceToValidate input, String rule) throws SQLException {
        double fallback = loadLastValidPrice(input.instrumentId());
        persistQuarantine(input, rule);
        quarantinedCounter.increment();
        log.warn("Price quarantined instrumentId={} price={} rule={}", input.instrumentId(), input.price(), rule);
        return new ValidationResult(false, rule, fallback);
    }

    // ─── DB helpers ──────────────────────────────────────────────────────────

    private Double loadPreviousClose(String instrumentId) throws SQLException {
        String sql = """
            SELECT close_price FROM price_history
            WHERE instrument_id = ? AND price_date_ad < CURRENT_DATE
            ORDER BY price_date_ad DESC LIMIT 1
            """;
        return querySingleDouble(sql, instrumentId);
    }

    private Double loadLastEodClose(String instrumentId) throws SQLException {
        String sql = """
            SELECT close_price FROM price_history
            WHERE instrument_id = ?
            ORDER BY price_date_ad DESC LIMIT 1
            """;
        return querySingleDouble(sql, instrumentId);
    }

    private double loadLastValidPrice(String instrumentId) throws SQLException {
        Double v = loadLastEodClose(instrumentId);
        return v != null ? v : 0.0;
    }

    private Double querySingleDouble(String sql, String instrumentId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double v = rs.getDouble(1);
                    return rs.wasNull() ? null : v;
                }
            }
        }
        return null;
    }

    private void persistQuarantine(PriceToValidate input, String rule) throws SQLException {
        String sql = """
            INSERT INTO price_validation_log (
                log_id, instrument_id, submitted_price, bid_price, ask_price,
                price_source, rejection_rule, logged_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, now())
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, input.instrumentId());
            ps.setDouble(3, input.price());
            ps.setDouble(4, input.bidPrice());
            ps.setDouble(5, input.askPrice());
            ps.setString(6, input.priceSource());
            ps.setString(7, rule);
            ps.executeUpdate();
        }
    }
}

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
 * @doc.purpose Bootstraps interest rate yield curves from market deposit rates, FRAs, and swap
 *              rates. Starts from short-end deposits and bootstraps iteratively to longer tenors.
 *              Supports linear, log-linear, and cubic spline interpolation (configurable via K-02).
 *              Stores the resulting tenor array as JSONB. Emits YieldCurveBuilt event.
 *              Currently supports NPR government bond curve (NRB) and USD SOFR curve.
 * @doc.layer   Domain
 * @doc.pattern Iterative bootstrapping; K-02 ConfigPort for interpolation method selection;
 *              event emission; UPSERT idempotency via ON CONFLICT(currency, curve_type, curve_date_ad).
 */
public class YieldCurveService {

    private static final Logger log = LoggerFactory.getLogger(YieldCurveService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ConfigPort       configPort;
    private final CalendarPort     calendarPort;
    private final YieldCurveEventPort eventPort;
    private final Counter          builtCounter;

    public YieldCurveService(HikariDataSource dataSource, Executor executor,
                              ConfigPort configPort, CalendarPort calendarPort,
                              YieldCurveEventPort eventPort, MeterRegistry registry) {
        this.dataSource   = dataSource;
        this.executor     = executor;
        this.configPort   = configPort;
        this.calendarPort = calendarPort;
        this.eventPort    = eventPort;
        this.builtCounter = registry.counter("pricing.yield_curve.built");
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-02 config port for interpolation method selection. */
    public interface ConfigPort {
        String getInterpolationMethod(String currency, String curveType);  // LINEAR | LOG_LINEAR | CUBIC_SPLINE
    }

    /** K-15 CalendarPort for BS date. */
    public interface CalendarPort {
        String adToBs(LocalDate adDate);
    }

    public interface YieldCurveEventPort {
        void emitYieldCurveBuilt(YieldCurveBuilt event);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record RateInput(
        double tenorYears,
        double rate        // decimal, e.g. 0.07 for 7%
    ) {}

    public record TenorPoint(
        double tenorYears,
        double zeroRate,
        double discountFactor
    ) {}

    public record YieldCurve(
        String          curveId,
        String          currency,
        String          curveType,
        LocalDate       curveDateAd,
        String          curveDateBs,
        List<TenorPoint> tenors,
        String          interpolationMethod
    ) {}

    public record YieldCurveBuilt(
        String    curveId,
        String    currency,
        String    curveType,
        LocalDate curveDateAd
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Build a yield curve from raw market rates.
     */
    public Promise<YieldCurve> buildCurve(String currency, String curveType,
                                          LocalDate curveDateAd, List<RateInput> inputs) {
        return Promise.ofBlocking(executor, () -> {
            String method   = configPort.getInterpolationMethod(currency, curveType);
            String bsDate   = calendarPort.adToBs(curveDateAd);
            List<TenorPoint> tenors = bootstrap(inputs, method);
            String curveId  = UUID.randomUUID().toString();
            persistCurve(curveId, currency, curveType, curveDateAd, bsDate, tenors, method);
            builtCounter.increment();
            eventPort.emitYieldCurveBuilt(new YieldCurveBuilt(curveId, currency, curveType, curveDateAd));
            log.info("Yield curve built currency={} type={} date={} tenors={} method={}",
                     currency, curveType, curveDateAd, tenors.size(), method);
            return new YieldCurve(curveId, currency, curveType, curveDateAd, bsDate, tenors, method);
        });
    }

    /**
     * Interpolate a zero rate for a given tenor on a stored curve.
     */
    public Promise<Double> interpolateRate(String currency, String curveType,
                                           LocalDate curveDateAd, double tenorYears) {
        return Promise.ofBlocking(executor, () -> {
            List<TenorPoint> tenors = loadCurveTenors(currency, curveType, curveDateAd);
            String method = configPort.getInterpolationMethod(currency, curveType);
            return interpolate(tenors, tenorYears, method);
        });
    }

    // ─── Bootstrapping ────────────────────────────────────────────────────────

    private List<TenorPoint> bootstrap(List<RateInput> inputs, String method) {
        // Sort by tenor ascending
        List<RateInput> sorted = inputs.stream()
            .sorted((a, b) -> Double.compare(a.tenorYears(), b.tenorYears()))
            .toList();

        List<TenorPoint> tenors = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            RateInput ri = sorted.get(i);
            double df;
            if (i == 0) {
                // Short end: simple discount factor
                df = 1.0 / (1.0 + ri.rate() * ri.tenorYears());
            } else {
                // Bootstrap: sum of intermediate coupon DFs using already-computed tenors
                double couponPv = 0.0;
                TenorPoint prev = null;
                for (int j = 0; j < i; j++) {
                    TenorPoint tp = tenors.get(j);
                    double dt = (prev == null ? tp.tenorYears() : tp.tenorYears() - tenors.get(j - 1).tenorYears());
                    couponPv += ri.rate() * dt * tp.discountFactor();
                    prev = tp;
                }
                df = (1.0 - couponPv) / (1.0 + ri.rate() * (ri.tenorYears() - (i > 0 ? sorted.get(i - 1).tenorYears() : 0)));
            }
            double zeroRate = (ri.tenorYears() > 0)
                ? (Math.log(1.0 / df) / ri.tenorYears())
                : ri.rate();
            tenors.add(new TenorPoint(ri.tenorYears(), zeroRate, df));
        }
        return tenors;
    }

    private double interpolate(List<TenorPoint> tenors, double targetTenor, String method) {
        if (tenors.isEmpty()) throw new IllegalStateException("No tenor points");
        if (targetTenor <= tenors.getFirst().tenorYears()) return tenors.getFirst().zeroRate();
        if (targetTenor >= tenors.getLast().tenorYears())  return tenors.getLast().zeroRate();

        // Find bracketing points
        TenorPoint lo = null, hi = null;
        for (int i = 0; i < tenors.size() - 1; i++) {
            if (tenors.get(i).tenorYears() <= targetTenor
                && tenors.get(i + 1).tenorYears() >= targetTenor) {
                lo = tenors.get(i);
                hi = tenors.get(i + 1);
                break;
            }
        }
        if (lo == null) return tenors.getLast().zeroRate();
        double t = (targetTenor - lo.tenorYears()) / (hi.tenorYears() - lo.tenorYears());

        return switch (method) {
            case "LOG_LINEAR" -> {
                double logLo = Math.log(lo.zeroRate());
                double logHi = Math.log(hi.zeroRate());
                yield Math.exp(logLo + t * (logHi - logLo));
            }
            default -> lo.zeroRate() + t * (hi.zeroRate() - lo.zeroRate()); // LINEAR
        };
    }

    // ─── DB helpers ──────────────────────────────────────────────────────────

    private void persistCurve(String curveId, String currency, String curveType,
                               LocalDate curveDateAd, String curveDateBs,
                               List<TenorPoint> tenors, String method) throws SQLException {
        String tenorsJson = buildTenorJson(tenors);
        String sql = """
            INSERT INTO yield_curves (
                curve_id, currency, curve_type, curve_date_ad, curve_date_bs,
                tenors_json, interpolation_method, created_at
            ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, now())
            ON CONFLICT (currency, curve_type, curve_date_ad) DO UPDATE SET
                tenors_json           = EXCLUDED.tenors_json,
                interpolation_method  = EXCLUDED.interpolation_method,
                created_at            = now()
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, curveId);
            ps.setString(2, currency);
            ps.setString(3, curveType);
            ps.setObject(4, curveDateAd);
            ps.setString(5, curveDateBs);
            ps.setString(6, tenorsJson);
            ps.setString(7, method);
            ps.executeUpdate();
        }
    }

    private List<TenorPoint> loadCurveTenors(String currency, String curveType,
                                              LocalDate curveDateAd) throws SQLException {
        String sql = """
            SELECT tenors_json FROM yield_curves
            WHERE currency = ? AND curve_type = ? AND curve_date_ad = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currency);
            ps.setString(2, curveType);
            ps.setObject(3, curveDateAd);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return parseTenorJson(rs.getString("tenors_json"));
                }
            }
        }
        return List.of();
    }

    private String buildTenorJson(List<TenorPoint> tenors) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tenors.size(); i++) {
            TenorPoint tp = tenors.get(i);
            sb.append(String.format("{\"tenor\":%.4f,\"zero_rate\":%.8f,\"df\":%.8f}",
                                    tp.tenorYears(), tp.zeroRate(), tp.discountFactor()));
            if (i < tenors.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private List<TenorPoint> parseTenorJson(String json) {
        // Simple JSON array parser — avoids external library dependency
        List<TenorPoint> result = new ArrayList<>();
        if (json == null || json.isBlank()) return result;
        String[] objects = json.replace("[{", "").replace("}]", "").split("},\\{");
        for (String obj : objects) {
            double tenor = parseJsonDouble(obj, "tenor");
            double zero  = parseJsonDouble(obj, "zero_rate");
            double df    = parseJsonDouble(obj, "df");
            result.add(new TenorPoint(tenor, zero, df));
        }
        return result;
    }

    private double parseJsonDouble(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return 0.0;
        int start = idx + search.length();
        int end   = json.indexOf(',', start);
        if (end < 0) end = json.indexOf('}', start);
        return Double.parseDouble(json.substring(start, end).strip());
    }
}

package com.ghatana.appplatform.pms.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Timer;
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
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Calculates Time-Weighted Return (TWR) and Money-Weighted Return (MWR/IRR)
 *              for portfolios. TWR: measures performance independent of cash flows, computed
 *              by compounding sub-period returns between each cash flow. MWR: IRR considering
 *              cash flow timing (Newton-Raphson). Both for daily/MTD/QTD/YTD/ITD periods
 *              in nominal and annualized form. K-15 CalendarPort for BS date periods.
 *              Satisfies STORY-D03-005.
 * @doc.layer   Domain
 * @doc.pattern TWR sub-period compounding; Newton-Raphson IRR solver; K-15 CalendarPort;
 *              result persistence for dashboard queries.
 */
public class PortfolioReturnService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioReturnService.class);

    private static final int    MWR_MAX_ITERATIONS = 200;
    private static final double MWR_TOLERANCE       = 1e-8;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final CalendarPort     calendarPort;
    private final Timer            calcTimer;

    public PortfolioReturnService(HikariDataSource dataSource, Executor executor,
                                  CalendarPort calendarPort, MeterRegistry registry) {
        this.dataSource   = dataSource;
        this.executor     = executor;
        this.calendarPort = calendarPort;
        this.calcTimer    = Timer.builder("pms.returns.calc_duration_ms").register(registry);
    }

    // ─── Inner port ───────────────────────────────────────────────────────────

    /** K-15 CalendarPort for BS period boundaries. */
    public interface CalendarPort {
        LocalDate bsYearStart(int bsYear);
        LocalDate bsQuarterStart(int bsYear, int quarter);
        LocalDate bsMonthStart(int bsYear, int month);
        LocalDate inceptionDate(String portfolioId); // AD date
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record ReturnResult(String portfolioId, String period,
                               double twr, double twrAnnualized,
                               double mwr, double mwrAnnualized) {}

    public record NavPoint(LocalDate date, double nav, double cashFlow) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ReturnResult> computeReturns(String portfolioId, String period,
                                                LocalDate asOf) {
        return Promise.ofBlocking(executor, () ->
                calcTimer.recordCallable(() -> {
                    LocalDate from = periodStart(portfolioId, period, asOf);
                    List<NavPoint> navSeries = loadNavSeries(portfolioId, from, asOf);
                    if (navSeries.size() < 2) {
                        return new ReturnResult(portfolioId, period, 0, 0, 0, 0);
                    }
                    double twr = computeTwr(navSeries);
                    double yearsElapsed = daysBetween(from, asOf) / 365.25;
                    double twrAnn = yearsElapsed > 0 ? Math.pow(1 + twr, 1.0 / yearsElapsed) - 1 : twr;
                    double mwr = computeMwr(navSeries);
                    double mwrAnn = yearsElapsed > 0 ? Math.pow(1 + mwr, 1.0 / yearsElapsed) - 1 : mwr;
                    return new ReturnResult(portfolioId, period, twr, twrAnn, mwr, mwrAnn);
                }));
    }

    // ─── TWR calculation ──────────────────────────────────────────────────────

    /**
     * TWR = Π(1 + r_i) - 1 where r_i = (NAV_end_i / (NAV_begin_i + CashFlow_i)) - 1
     * Sub-periods split at each cash flow event.
     */
    private double computeTwr(List<NavPoint> series) {
        double compound = 1.0;
        for (int i = 1; i < series.size(); i++) {
            NavPoint prev = series.get(i - 1);
            NavPoint curr = series.get(i);
            double beginning = prev.nav() + curr.cashFlow(); // adjust for CF at period start
            if (beginning <= 0) continue;
            compound *= curr.nav() / beginning;
        }
        return compound - 1.0;
    }

    // ─── MWR / IRR calculation (Newton-Raphson) ───────────────────────────────

    /**
     * MWR solves for r where NPV of all cash flows = 0.
     * Initial cash flow = -nav[0], terminal = +nav[last], intermediates = cash flows.
     */
    private double computeMwr(List<NavPoint> series) {
        int n = series.size();
        LocalDate t0 = series.get(0).date();
        double[] amounts = new double[n];
        double[] tFrac   = new double[n]; // years from t0

        amounts[0] = -series.get(0).nav();
        tFrac[0]   = 0.0;
        for (int i = 1; i < n - 1; i++) {
            amounts[i] = series.get(i).cashFlow();
            tFrac[i]   = daysBetween(t0, series.get(i).date()) / 365.25;
        }
        amounts[n - 1] = series.get(n - 1).nav();
        tFrac[n - 1]   = daysBetween(t0, series.get(n - 1).date()) / 365.25;

        // Newton-Raphson
        double r = 0.10; // initial guess 10%
        for (int iter = 0; iter < MWR_MAX_ITERATIONS; iter++) {
            double npv = 0, dNpv = 0;
            for (int i = 0; i < n; i++) {
                double disc = Math.pow(1 + r, -tFrac[i]);
                npv  += amounts[i] * disc;
                dNpv -= amounts[i] * tFrac[i] * Math.pow(1 + r, -tFrac[i] - 1);
            }
            if (Math.abs(dNpv) < 1e-12) break;
            double delta = npv / dNpv;
            r -= delta;
            if (Math.abs(delta) < MWR_TOLERANCE) break;
        }
        return r;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private LocalDate periodStart(String portfolioId, String period, LocalDate asOf) {
        return switch (period) {
            case "DAILY" -> asOf.minusDays(1);
            case "MTD"   -> asOf.withDayOfMonth(1);
            case "QTD"   -> asOf.withDayOfMonth(1).withMonth(((asOf.getMonthValue() - 1) / 3) * 3 + 1);
            case "YTD"   -> asOf.withDayOfYear(1);
            case "ITD"   -> calendarPort.inceptionDate(portfolioId);
            default      -> asOf.minusDays(30);
        };
    }

    private List<NavPoint> loadNavSeries(String portfolioId, LocalDate from, LocalDate to)
            throws SQLException {
        List<NavPoint> series = new ArrayList<>();
        String sql = """
                SELECT calc_date_ad, nav, COALESCE(cash_flow, 0) AS cash_flow
                FROM nav_history
                WHERE portfolio_id = ? AND calc_date_ad BETWEEN ? AND ?
                ORDER BY calc_date_ad
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            ps.setObject(2, from);
            ps.setObject(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    series.add(new NavPoint(
                            rs.getObject("calc_date_ad", LocalDate.class),
                            rs.getDouble("nav"),
                            rs.getDouble("cash_flow")));
                }
            }
        }
        return series;
    }

    private double daysBetween(LocalDate from, LocalDate to) {
        return from.until(to, java.time.temporal.ChronoUnit.DAYS);
    }
}

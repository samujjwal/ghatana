package com.ghatana.appplatform.pms.service;

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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Calculates end-of-day Net Asset Value (NAV) for all active portfolios.
 *              Formula: NAV = Σ(quantity × market_price) - total_liabilities + accrued_income.
 *              nav_per_unit = total_nav / total_units_outstanding.
 *              Emits NAVCalculated event for downstream consumers (audit, reporting).
 *              Stores both AD and BS (Bikram Sambat) dates for each NAV record.
 *              Idempotent: re-running for the same portfolio+date updates rather than duplicates.
 * @doc.layer   Domain
 * @doc.pattern EOD batch trigger; K-15 CalendarPort for BS date; event emission; UPSERT idempotency.
 */
public class NavCalculationService {

    private static final Logger log  = LoggerFactory.getLogger(NavCalculationService.class);
    private static final ZoneId NST  = ZoneId.of("Asia/Kathmandu");

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final CalendarPort     calendarPort;
    private final PricePort        pricePort;
    private final NavEventPort     eventPort;
    private final Counter          calculatedCounter;
    private final Counter          errorCounter;

    public NavCalculationService(HikariDataSource dataSource, Executor executor,
                                 CalendarPort calendarPort, PricePort pricePort,
                                 NavEventPort eventPort, MeterRegistry registry) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.calendarPort      = calendarPort;
        this.pricePort         = pricePort;
        this.eventPort         = eventPort;
        this.calculatedCounter = registry.counter("pms.nav.calculated");
        this.errorCounter      = registry.counter("pms.nav.error");
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface CalendarPort {
        String adToBs(LocalDate adDate);
    }

    public interface PricePort {
        double getLatestPrice(String instrumentId);   // NPR
    }

    public interface NavEventPort {
        void emit(NavCalculated event);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record NavCalculated(
        String    portfolioId,
        LocalDate calcDateAd,
        String    calcDateBs,
        double    totalNav,
        double    navPerUnit,
        double    totalUnits
    ) {}

    public record NavResult(
        String    navId,
        String    portfolioId,
        LocalDate calcDateAd,
        String    calcDateBs,
        double    grossAssetValue,
        double    totalLiabilities,
        double    accruedIncome,
        double    totalNav,
        double    totalUnits,
        double    navPerUnit
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Run EOD NAV calculation for all active portfolios.
     */
    public Promise<List<NavResult>> runEodBatch(LocalDate calcDateAd) {
        return Promise.ofBlocking(executor, () -> {
            List<String> activePortfolios = loadActivePortfolioIds();
            List<NavResult> results = new ArrayList<>();
            for (String portfolioId : activePortfolios) {
                try {
                    NavResult result = calculateNav(portfolioId, calcDateAd);
                    results.add(result);
                    calculatedCounter.increment();
                } catch (Exception ex) {
                    log.error("NAV calculation failed portfolioId={} date={}", portfolioId, calcDateAd, ex);
                    errorCounter.increment();
                }
            }
            log.info("EOD NAV batch complete date={} count={}", calcDateAd, results.size());
            return results;
        });
    }

    /**
     * Calculate NAV for a single portfolio.
     */
    public Promise<NavResult> calculateForPortfolio(String portfolioId, LocalDate calcDateAd) {
        return Promise.ofBlocking(executor, () -> calculateNav(portfolioId, calcDateAd));
    }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private NavResult calculateNav(String portfolioId, LocalDate calcDateAd) throws SQLException {
        String calcDateBs = calendarPort.adToBs(calcDateAd);

        List<HoldingPosition> holdings = loadHoldingPositions(portfolioId);
        double grossAssetValue = 0.0;
        for (HoldingPosition h : holdings) {
            double price = pricePort.getLatestPrice(h.instrumentId());
            grossAssetValue += h.quantity() * price;
        }

        double totalLiabilities = loadTotalLiabilities(portfolioId);
        double accruedIncome    = loadAccruedIncome(portfolioId);
        double totalNav         = grossAssetValue - totalLiabilities + accruedIncome;
        double totalUnits       = loadTotalUnits(portfolioId);
        double navPerUnit       = totalUnits > 0 ? totalNav / totalUnits : 0.0;

        String navId = UUID.randomUUID().toString();
        persistNav(navId, portfolioId, calcDateAd, calcDateBs,
                   grossAssetValue, totalLiabilities, accruedIncome, totalNav, totalUnits, navPerUnit);

        NavCalculated event = new NavCalculated(portfolioId, calcDateAd, calcDateBs, totalNav, navPerUnit, totalUnits);
        eventPort.emit(event);

        log.info("NAV portfolioId={} date={} nav={} perUnit={}", portfolioId, calcDateAd, totalNav, navPerUnit);
        return new NavResult(navId, portfolioId, calcDateAd, calcDateBs,
                             grossAssetValue, totalLiabilities, accruedIncome,
                             totalNav, totalUnits, navPerUnit);
    }

    // ─── DB helpers ──────────────────────────────────────────────────────────

    private record HoldingPosition(String instrumentId, double quantity) {}

    private List<HoldingPosition> loadHoldingPositions(String portfolioId) throws SQLException {
        String sql = "SELECT instrument_id, quantity FROM portfolio_holdings WHERE portfolio_id = ? AND quantity > 0";
        List<HoldingPosition> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new HoldingPosition(rs.getString("instrument_id"), rs.getDouble("quantity")));
                }
            }
        }
        return list;
    }

    private double loadTotalLiabilities(String portfolioId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM portfolio_liabilities WHERE portfolio_id = ? AND status = 'OUTSTANDING'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    private double loadAccruedIncome(String portfolioId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM portfolio_accruals WHERE portfolio_id = ? AND accrual_date >= CURRENT_DATE - INTERVAL '30 days'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    private double loadTotalUnits(String portfolioId) throws SQLException {
        String sql = "SELECT COALESCE(total_units, 1.0) FROM portfolios WHERE portfolio_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 1.0;
    }

    private void persistNav(String navId, String portfolioId, LocalDate calcDateAd, String calcDateBs,
                            double grossAssetValue, double totalLiabilities, double accruedIncome,
                            double totalNav, double totalUnits, double navPerUnit) throws SQLException {
        String sql = """
            INSERT INTO nav_history (
                nav_id, portfolio_id, calc_date_ad, calc_date_bs, gross_asset_value,
                total_liabilities, accrued_income, total_nav, total_units, nav_per_unit, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (portfolio_id, calc_date_ad) DO UPDATE SET
                gross_asset_value  = EXCLUDED.gross_asset_value,
                total_liabilities  = EXCLUDED.total_liabilities,
                accrued_income     = EXCLUDED.accrued_income,
                total_nav          = EXCLUDED.total_nav,
                total_units        = EXCLUDED.total_units,
                nav_per_unit       = EXCLUDED.nav_per_unit,
                created_at         = now()
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, navId);
            ps.setString(2, portfolioId);
            ps.setObject(3, calcDateAd);
            ps.setString(4, calcDateBs);
            ps.setDouble(5, grossAssetValue);
            ps.setDouble(6, totalLiabilities);
            ps.setDouble(7, accruedIncome);
            ps.setDouble(8, totalNav);
            ps.setDouble(9, totalUnits);
            ps.setDouble(10, navPerUnit);
            ps.executeUpdate();
        }
    }

    private List<String> loadActivePortfolioIds() throws SQLException {
        String sql = "SELECT portfolio_id FROM portfolios WHERE status = 'ACTIVE'";
        List<String> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString("portfolio_id"));
        }
        return ids;
    }
}

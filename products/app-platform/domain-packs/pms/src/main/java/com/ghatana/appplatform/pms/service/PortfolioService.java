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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose CRUD operations for portfolios and their holdings. Portfolios have dual-calendar
 *              inception dates (AD and BS), a strategy enum, and a lifecycle status
 *              (ACTIVE / SUSPENDED / CLOSED). Holdings track instrument, quantity, avg cost,
 *              and weight. CLOSED portfolios are soft-deleted (status update, no row removal).
 *              Holdings are updated atomically on each rebalancing event.
 * @doc.layer   Domain
 * @doc.pattern K-15 CalendarPort for BS inception date; soft-delete on close;
 *              UPSERT (ON CONFLICT DO UPDATE) for holdings to support incremental rebalancing.
 */
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final CalendarPort     calendarPort;
    private final Counter          createdCounter;
    private final Counter          updatedCounter;

    public PortfolioService(HikariDataSource dataSource, Executor executor,
                            CalendarPort calendarPort, MeterRegistry registry) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.calendarPort   = calendarPort;
        this.createdCounter = registry.counter("pms.portfolio.created");
        this.updatedCounter = registry.counter("pms.portfolio.updated");
    }

    // ─── Inner port (K-15) ───────────────────────────────────────────────────

    public interface CalendarPort {
        String adToBs(LocalDate adDate);   // returns "YYYY/MM/DD" BS string
        LocalDate bsToAd(int bsYear, int bsMonth, int bsDay);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record Portfolio(
        String    portfolioId,
        String    portfolioName,
        String    clientId,
        String    strategy,         // EQUITY_GROWTH | BALANCED | INCOME | INDEX
        LocalDate inceptionDateAd,
        String    inceptionDateBs,
        String    status,           // ACTIVE | SUSPENDED | CLOSED
        String    baseCurrency,
        String    managerId
    ) {}

    public record Holding(
        String    holdingId,
        String    portfolioId,
        String    instrumentId,
        double    quantity,
        double    avgCostPerShare,
        double    currentWeight,    // fractional weight in portfolio
        LocalDate lastUpdated
    ) {}

    public record CreatePortfolioCommand(
        String    portfolioName,
        String    clientId,
        String    strategy,
        LocalDate inceptionDateAd,
        String    baseCurrency,
        String    managerId
    ) {}

    public record UpsertHoldingCommand(
        String portfolioId,
        String instrumentId,
        double quantity,
        double avgCostPerShare,
        double currentWeight
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<Portfolio> createPortfolio(CreatePortfolioCommand cmd) {
        return Promise.ofBlocking(executor, () -> {
            String portfolioId = UUID.randomUUID().toString();
            String bsDate      = calendarPort.adToBs(cmd.inceptionDateAd());
            insertPortfolio(portfolioId, cmd, bsDate);
            createdCounter.increment();
            log.info("Portfolio created portfolioId={} name={}", portfolioId, cmd.portfolioName());
            return new Portfolio(portfolioId, cmd.portfolioName(), cmd.clientId(),
                                 cmd.strategy(), cmd.inceptionDateAd(), bsDate,
                                 "ACTIVE", cmd.baseCurrency(), cmd.managerId());
        });
    }

    public Promise<Portfolio> getPortfolio(String portfolioId) {
        return Promise.ofBlocking(executor, () -> loadPortfolio(portfolioId));
    }

    public Promise<List<Portfolio>> listPortfolios(String clientId) {
        return Promise.ofBlocking(executor, () -> loadPortfoliosByClient(clientId));
    }

    public Promise<Void> updateStatus(String portfolioId, String newStatus) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "UPDATE portfolios SET status = ?, updated_at = now() WHERE portfolio_id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newStatus);
                ps.setString(2, portfolioId);
                ps.executeUpdate();
            }
            updatedCounter.increment();
            log.info("Portfolio status updated portfolioId={} status={}", portfolioId, newStatus);
            return null;
        });
    }

    public Promise<Holding> upsertHolding(UpsertHoldingCommand cmd) {
        return Promise.ofBlocking(executor, () -> {
            String holdingId = upsertHoldingRecord(cmd);
            return new Holding(holdingId, cmd.portfolioId(), cmd.instrumentId(),
                               cmd.quantity(), cmd.avgCostPerShare(), cmd.currentWeight(),
                               LocalDate.now());
        });
    }

    public Promise<List<Holding>> listHoldings(String portfolioId) {
        return Promise.ofBlocking(executor, () -> loadHoldings(portfolioId));
    }

    public Promise<Void> removeHolding(String portfolioId, String instrumentId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM portfolio_holdings WHERE portfolio_id = ? AND instrument_id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, portfolioId);
                ps.setString(2, instrumentId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ─── DB helpers ──────────────────────────────────────────────────────────

    private void insertPortfolio(String portfolioId, CreatePortfolioCommand cmd,
                                 String bsDate) throws SQLException {
        String sql = """
            INSERT INTO portfolios (
                portfolio_id, portfolio_name, client_id, strategy, inception_date_ad,
                inception_date_bs, status, base_currency, manager_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, now(), now())
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            ps.setString(2, cmd.portfolioName());
            ps.setString(3, cmd.clientId());
            ps.setString(4, cmd.strategy());
            ps.setObject(5, cmd.inceptionDateAd());
            ps.setString(6, bsDate);
            ps.setString(7, cmd.baseCurrency());
            ps.setString(8, cmd.managerId());
            ps.executeUpdate();
        }
    }

    private Portfolio loadPortfolio(String portfolioId) throws SQLException {
        String sql = """
            SELECT portfolio_id, portfolio_name, client_id, strategy, inception_date_ad,
                   inception_date_bs, status, base_currency, manager_id
            FROM portfolios WHERE portfolio_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapPortfolio(rs);
                }
            }
        }
        return null;
    }

    private List<Portfolio> loadPortfoliosByClient(String clientId) throws SQLException {
        String sql = """
            SELECT portfolio_id, portfolio_name, client_id, strategy, inception_date_ad,
                   inception_date_bs, status, base_currency, manager_id
            FROM portfolios WHERE client_id = ? AND status != 'CLOSED'
            ORDER BY created_at DESC
            """;
        List<Portfolio> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapPortfolio(rs));
            }
        }
        return list;
    }

    private Portfolio mapPortfolio(ResultSet rs) throws SQLException {
        return new Portfolio(
            rs.getString("portfolio_id"),
            rs.getString("portfolio_name"),
            rs.getString("client_id"),
            rs.getString("strategy"),
            rs.getObject("inception_date_ad", LocalDate.class),
            rs.getString("inception_date_bs"),
            rs.getString("status"),
            rs.getString("base_currency"),
            rs.getString("manager_id")
        );
    }

    private String upsertHoldingRecord(UpsertHoldingCommand cmd) throws SQLException {
        String holdingId = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO portfolio_holdings (
                holding_id, portfolio_id, instrument_id, quantity, avg_cost_per_share,
                current_weight, last_updated
            ) VALUES (?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (portfolio_id, instrument_id) DO UPDATE SET
                quantity         = EXCLUDED.quantity,
                avg_cost_per_share = EXCLUDED.avg_cost_per_share,
                current_weight   = EXCLUDED.current_weight,
                last_updated     = now()
            RETURNING holding_id
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, holdingId);
            ps.setString(2, cmd.portfolioId());
            ps.setString(3, cmd.instrumentId());
            ps.setDouble(4, cmd.quantity());
            ps.setDouble(5, cmd.avgCostPerShare());
            ps.setDouble(6, cmd.currentWeight());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) holdingId = rs.getString(1);
            }
        }
        return holdingId;
    }

    private List<Holding> loadHoldings(String portfolioId) throws SQLException {
        String sql = """
            SELECT holding_id, portfolio_id, instrument_id, quantity, avg_cost_per_share,
                   current_weight, last_updated
            FROM portfolio_holdings WHERE portfolio_id = ? AND quantity > 0
            ORDER BY current_weight DESC
            """;
        List<Holding> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Holding(
                        rs.getString("holding_id"),
                        rs.getString("portfolio_id"),
                        rs.getString("instrument_id"),
                        rs.getDouble("quantity"),
                        rs.getDouble("avg_cost_per_share"),
                        rs.getDouble("current_weight"),
                        rs.getObject("last_updated", LocalDate.class)
                    ));
                }
            }
        }
        return list;
    }
}

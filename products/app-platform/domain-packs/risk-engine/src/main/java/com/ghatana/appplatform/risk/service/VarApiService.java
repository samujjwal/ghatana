package com.ghatana.appplatform.risk.service;

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
 * @doc.purpose Exposes REST-compatible VaR query results for individual portfolios and
 *              runs the EOD batch that computes VaR for all active portfolios. Delegates
 *              actual VaR calculation to VarCalculationService.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; delegates math to VarCalculationService; stores results
 *              in var_history for trend queries and per-instrument decomposition.
 */
public class VarApiService {

    private static final Logger log = LoggerFactory.getLogger(VarApiService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final VarCalculationService varCalculationService;
    private final Counter             eodBatchCounter;
    private final Counter             apiQueryCounter;

    public VarApiService(HikariDataSource dataSource,
                         Executor executor,
                         VarCalculationService varCalculationService,
                         MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.varCalculationService = varCalculationService;
        this.eodBatchCounter      = registry.counter("risk.var.api.eodBatch");
        this.apiQueryCounter      = registry.counter("risk.var.api.query");
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record VarResponse(
        String       portfolioId,
        String       method,
        double       confidenceLevel,
        int          holdingPeriodDays,
        double       varAmount,
        LocalDate    tradeDate,
        List<InstrumentContribution> decomposition
    ) {}

    public record InstrumentContribution(
        String instrumentId,
        double weight,
        double individualVar,
        double contributionBps  // contribution as fraction of total VaR in bps
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns VaR for a portfolio — used by REST endpoint
     * {@code GET /risk/var?portfolio_id=&method=&confidence=&holding_period=}.
     */
    public Promise<VarResponse> getVar(String portfolioId, String method,
                                       double confidence, int holdingPeriodDays) {
        return Promise.ofBlocking(executor, () -> {
            apiQueryCounter.increment();
            return buildVarResponse(portfolioId, method, confidence, holdingPeriodDays);
        });
    }

    /**
     * EOD batch: compute VaR for every active portfolio and persist in {@code var_history}.
     */
    public Promise<Void> runEodBatch(String method, double confidence, int holdingPeriodDays) {
        return Promise.ofBlocking(executor, () -> {
            List<String> portfolioIds = loadActivePortfolioIds();
            log.info("VaR EOD batch: {} portfolios, method={}", portfolioIds.size(), method);
            for (String pid : portfolioIds) {
                try {
                    VarResponse resp = buildVarResponse(pid, method, confidence, holdingPeriodDays);
                    persistVarHistory(resp);
                    eodBatchCounter.increment();
                } catch (Exception ex) {
                    log.error("VaR EOD failed for portfolioId={}", pid, ex);
                }
            }
            return null;
        });
    }

    /**
     * Returns historical VaR trend for a portfolio (for dashboard trend chart).
     */
    public Promise<List<VarResponse>> getVarHistory(String portfolioId, String method,
                                                     double confidence, int limitDays) {
        return Promise.ofBlocking(executor, () -> loadVarHistory(portfolioId, method, confidence, limitDays));
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private VarResponse buildVarResponse(String portfolioId, String method,
                                          double confidence, int holdingPeriodDays) {
        // Delegate math to VarCalculationService running synchronously inside executor thread
        var calcResult = varCalculationService.calculateVarSync(portfolioId, method, confidence, holdingPeriodDays);
        List<InstrumentContribution> decomp = buildDecomposition(portfolioId, calcResult.varAmount());
        log.debug("VaR query portfolioId={} method={} var={}", portfolioId, method, calcResult.varAmount());
        return new VarResponse(portfolioId, method, confidence, holdingPeriodDays,
                               calcResult.varAmount(), LocalDate.now(NST), decomp);
    }

    private List<InstrumentContribution> buildDecomposition(String portfolioId, double totalVar) {
        String sql = """
            SELECT instrument_id, quantity * last_price AS market_value
            FROM portfolio_positions
            WHERE portfolio_id = ?
              AND quantity > 0
            ORDER BY market_value DESC
            LIMIT 20
            """;
        List<InstrumentContribution> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            double totalMv = 0.0;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    totalMv += rs.getDouble("market_value");
                }
            }
            if (totalMv <= 0.0) {
                log.warn("VaR decomposition: portfolioId={} has zero/negative total market value — returning empty decomposition", portfolioId);
                return result;
            }
            // Recalculate contributions with final totalMv known
            result = recomputeWithWeights(portfolioId, totalMv, totalVar);
        } catch (SQLException ex) {
            log.error("Failed to build VaR decomposition portfolioId={}", portfolioId, ex);
        }
        return result;
    }

    private List<InstrumentContribution> recomputeWithWeights(String portfolioId,
                                                               double totalMv, double totalVar) {
        String sql = """
            SELECT instrument_id, quantity * last_price AS market_value,
                   daily_volatility
            FROM portfolio_positions
            WHERE portfolio_id = ?
              AND quantity > 0
            ORDER BY market_value DESC
            LIMIT 20
            """;
        List<InstrumentContribution> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String instId    = rs.getString("instrument_id");
                    double mv        = rs.getDouble("market_value");
                    double vol       = rs.getDouble("daily_volatility");
                    double weight    = mv / totalMv;
                    double indivVar  = mv * vol * 2.33;  // approx 99% parametric
                    double contribBps = totalVar > 0 ? (indivVar / totalVar) * 10_000 : 0;
                    result.add(new InstrumentContribution(instId, weight, indivVar, contribBps));
                }
            }
        } catch (SQLException ex) {
            log.error("Failed recomputeWithWeights portfolioId={}", portfolioId, ex);
        }
        return result;
    }

    private List<String> loadActivePortfolioIds() {
        String sql = "SELECT portfolio_id FROM portfolios WHERE status = 'ACTIVE'";
        List<String> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString("portfolio_id"));
        } catch (SQLException ex) {
            log.error("Failed to load active portfolio IDs", ex);
        }
        return ids;
    }

    private void persistVarHistory(VarResponse resp) {
        String sql = """
            INSERT INTO var_history
                (history_id, portfolio_id, trade_date, method, confidence_pct,
                 holding_period_days, var_amount, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (portfolio_id, method, trade_date) DO UPDATE
                SET var_amount = EXCLUDED.var_amount
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, resp.portfolioId());
            ps.setObject(3, resp.tradeDate());
            ps.setString(4, resp.method());
            ps.setDouble(5, resp.confidenceLevel());
            ps.setInt(6, resp.holdingPeriodDays());
            ps.setDouble(7, resp.varAmount());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist var_history for portfolioId={}", resp.portfolioId(), ex);
        }
    }

    private List<VarResponse> loadVarHistory(String portfolioId, String method,
                                              double confidence, int limitDays) {
        String sql = """
            SELECT trade_date, var_amount, holding_period_days
            FROM var_history
            WHERE portfolio_id   = ?
              AND method         = ?
              AND confidence_pct = ?
            ORDER BY trade_date DESC
            LIMIT ?
            """;
        List<VarResponse> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            ps.setString(2, method);
            ps.setDouble(3, confidence);
            ps.setInt(4, limitDays);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new VarResponse(
                        portfolioId, method, confidence,
                        rs.getInt("holding_period_days"),
                        rs.getDouble("var_amount"),
                        rs.getDate("trade_date").toLocalDate(),
                        List.of()
                    ));
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to load var_history for portfolioId={}", portfolioId, ex);
        }
        return result;
    }
}

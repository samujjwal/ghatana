package com.ghatana.appplatform.recon.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Verifies client money segregation: total_client_balances must be ≤ the balance
 *              held in segregated accounts separate from firm money. Regulatory minimum ratio
 *              ≥ 1.0. Breach → immediate escalation. Supports multiple segregated accounts
 *              per jurisdiction. K-16 integration for ledger-backed balance data.
 *              Satisfies STORY-D13-012.
 * @doc.layer   Domain
 * @doc.pattern Daily check; K-16 LedgerPort for balance queries; breach escalation;
 *              historical ratio trend via segregation_checks table.
 */
public class ClientMoneySegregationService {

    private static final Logger log = LoggerFactory.getLogger(ClientMoneySegregationService.class);

    private static final BigDecimal MIN_RATIO = BigDecimal.ONE;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final LedgerPort       ledger;
    private final EscalationPort   escalation;
    private final Counter          breachCounter;
    private final AtomicLong       latestRatioBps = new AtomicLong(10000);

    public ClientMoneySegregationService(HikariDataSource dataSource, Executor executor,
                                         LedgerPort ledger, EscalationPort escalation,
                                         MeterRegistry registry) {
        this.dataSource   = dataSource;
        this.executor     = executor;
        this.ledger       = ledger;
        this.escalation   = escalation;
        this.breachCounter = registry.counter("recon.segregation.breaches");
        Gauge.builder("recon.segregation.ratio_bps", latestRatioBps, AtomicLong::get)
             .description("Segregation ratio in bps (10000 = 1.0 ratio)").register(registry);
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    /** K-16 ledger port for balance queries. */
    public interface LedgerPort {
        BigDecimal getClientObligations(String jurisdiction);
        BigDecimal getSegregatedBalance(String jurisdiction, String accountCode);
    }

    public interface EscalationPort {
        void escalateBreach(String checkId, String jurisdiction, BigDecimal deficit,
                            BigDecimal ratio, LocalDate checkDate);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record SegregationCheckResult(String checkId, LocalDate checkDate, String jurisdiction,
                                         BigDecimal clientObligations, BigDecimal segregatedBalance,
                                         BigDecimal ratio, boolean compliant) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<SegregationCheckResult>> runDailyChecks(LocalDate checkDate) {
        return Promise.ofBlocking(executor, () -> {
            List<String> jurisdictions = loadJurisdictions();
            List<SegregationCheckResult> results = new ArrayList<>();
            for (String jur : jurisdictions) {
                BigDecimal obligations = ledger.getClientObligations(jur);
                BigDecimal segregated  = sumSegregatedAccounts(jur);
                BigDecimal ratio = obligations.signum() > 0
                        ? segregated.divide(obligations, 6, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ONE;
                boolean compliant = ratio.compareTo(MIN_RATIO) >= 0;
                String checkId = UUID.randomUUID().toString();

                persistCheck(checkId, checkDate, jur, obligations, segregated, ratio, compliant);
                latestRatioBps.set(ratio.multiply(BigDecimal.valueOf(10000)).longValue());

                if (!compliant) {
                    BigDecimal deficit = obligations.subtract(segregated);
                    escalation.escalateBreach(checkId, jur, deficit, ratio, checkDate);
                    breachCounter.increment();
                    log.error("Segregation breach: jurisdiction={} ratio={} deficit={}", jur, ratio, deficit);
                }
                results.add(new SegregationCheckResult(checkId, checkDate, jur,
                        obligations, segregated, ratio, compliant));
            }
            return results;
        });
    }

    public Promise<List<SegregationCheckResult>> getHistoricalRatios(String jurisdiction,
                                                                      LocalDate from, LocalDate to) {
        return Promise.ofBlocking(executor, () -> {
            List<SegregationCheckResult> history = new ArrayList<>();
            String sql = """
                    SELECT check_id, check_date, jurisdiction, client_obligations,
                           segregated_balance, ratio, compliant
                    FROM client_money_segregation_checks
                    WHERE jurisdiction = ? AND check_date BETWEEN ? AND ?
                    ORDER BY check_date
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, jurisdiction);
                ps.setObject(2, from);
                ps.setObject(3, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        history.add(new SegregationCheckResult(
                                rs.getString("check_id"),
                                rs.getObject("check_date", LocalDate.class),
                                rs.getString("jurisdiction"),
                                rs.getBigDecimal("client_obligations"),
                                rs.getBigDecimal("segregated_balance"),
                                rs.getBigDecimal("ratio"),
                                rs.getBoolean("compliant")));
                    }
                }
            }
            return history;
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private List<String> loadJurisdictions() throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT jurisdiction FROM segregated_accounts WHERE active = true";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString(1));
        }
        return list;
    }

    private BigDecimal sumSegregatedAccounts(String jurisdiction) throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(balance), 0)
                FROM segregated_accounts
                WHERE jurisdiction = ? AND active = true
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jurisdiction);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    private void persistCheck(String checkId, LocalDate checkDate, String jurisdiction,
                              BigDecimal obligations, BigDecimal segregated, BigDecimal ratio,
                              boolean compliant) throws SQLException {
        String sql = """
                INSERT INTO client_money_segregation_checks
                    (check_id, check_date, jurisdiction, client_obligations,
                     segregated_balance, ratio, compliant, checked_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (check_date, jurisdiction) DO UPDATE
                    SET client_obligations = EXCLUDED.client_obligations,
                        segregated_balance = EXCLUDED.segregated_balance,
                        ratio = EXCLUDED.ratio,
                        compliant = EXCLUDED.compliant,
                        checked_at = NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, checkId);
            ps.setObject(2, checkDate);
            ps.setString(3, jurisdiction);
            ps.setBigDecimal(4, obligations);
            ps.setBigDecimal(5, segregated);
            ps.setBigDecimal(6, ratio);
            ps.setBoolean(7, compliant);
            ps.executeUpdate();
        }
    }
}

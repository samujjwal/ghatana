package com.ghatana.appplatform.corporateactions.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Reconciles CA entitlements vs actual payments/positions. Detects
 *              AMOUNT_MISMATCH, MISSING_PAYMENT, EXTRA_PAYMENT and POSITION_MISMATCH
 *              breaks using anti-join SQL. Publishes CAReconciliationBreak events and
 *              writes breaks to ca_recon_breaks. Satisfies STORY-D12-012.
 * @doc.layer   Domain
 * @doc.pattern Anti-join SQL reconciliation; ON CONFLICT DO NOTHING idempotency;
 *              event publishing; Counter + Gauge.
 */
public class CorporateActionReconciliationService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final EventPort        eventPort;
    private final Counter          breaksDetectedCounter;
    private final AtomicLong       openBreaksGaugeValue = new AtomicLong(0);

    public CorporateActionReconciliationService(HikariDataSource dataSource, Executor executor,
                                                 EventPort eventPort, MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.eventPort            = eventPort;
        this.breaksDetectedCounter = Counter.builder("ca.recon.breaks_total").register(registry);
        Gauge.builder("ca.recon.open_breaks", openBreaksGaugeValue, AtomicLong::get)
                .register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface EventPort {
        void publish(String topic, Object event);
    }

    // ─── Break types ─────────────────────────────────────────────────────────

    public enum BreakType {
        AMOUNT_MISMATCH,
        MISSING_PAYMENT,
        EXTRA_PAYMENT,
        POSITION_MISMATCH
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record CaReconBreak(String breakId, String caId, String clientId, BreakType breakType,
                                BigDecimal expectedValue, BigDecimal actualValue,
                                BigDecimal difference, LocalDate reconDate,
                                LocalDateTime detectedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<CaReconBreak>> reconcile(String caId, LocalDate reconDate) {
        return Promise.ofBlocking(executor, () -> {
            List<CaReconBreak> breaks = new ArrayList<>();
            breaks.addAll(detectCashBreaks(caId, reconDate));
            breaks.addAll(detectPositionBreaks(caId, reconDate));
            openBreaksGaugeValue.set(countOpenBreaks());
            return breaks;
        });
    }

    // ─── Break detection ─────────────────────────────────────────────────────

    private List<CaReconBreak> detectCashBreaks(String caId, LocalDate reconDate) throws SQLException {
        List<CaReconBreak> breaks = new ArrayList<>();

        // MISSING_PAYMENT: entitlement exists but no ledger cash entry
        String missingPaymentSql = """
                SELECT e.client_id,
                       e.gross_amount AS expected,
                       CAST(0 AS NUMERIC) AS actual
                FROM ca_cash_entitlements e
                WHERE e.ca_id = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM ca_ledger_cash_entries l
                      WHERE l.ca_id = e.ca_id AND l.client_id = e.client_id
                  )
                """;
        breaks.addAll(queryBreaks(missingPaymentSql, caId, reconDate, BreakType.MISSING_PAYMENT));

        // EXTRA_PAYMENT: ledger cash entry exists but no entitlement
        String extraPaymentSql = """
                SELECT l.client_id,
                       CAST(0 AS NUMERIC) AS expected,
                       l.gross_amount AS actual
                FROM ca_ledger_cash_entries l
                WHERE l.ca_id = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM ca_cash_entitlements e
                      WHERE e.ca_id = l.ca_id AND e.client_id = l.client_id
                  )
                """;
        breaks.addAll(queryBreaks(extraPaymentSql, caId, reconDate, BreakType.EXTRA_PAYMENT));

        // AMOUNT_MISMATCH: both exist but amounts differ > 0.005 tolerance
        String amountMismatchSql = """
                SELECT e.client_id,
                       e.gross_amount AS expected,
                       l.gross_amount AS actual
                FROM ca_cash_entitlements e
                JOIN ca_ledger_cash_entries l
                  ON l.ca_id = e.ca_id AND l.client_id = e.client_id
                WHERE e.ca_id = ?
                  AND ABS(e.gross_amount - l.gross_amount) > 0.005
                """;
        breaks.addAll(queryBreaks(amountMismatchSql, caId, reconDate, BreakType.AMOUNT_MISMATCH));

        return breaks;
    }

    private List<CaReconBreak> detectPositionBreaks(String caId, LocalDate reconDate) throws SQLException {
        // POSITION_MISMATCH: stock entitlement bonus_shares vs actual position delta
        String positionMismatchSql = """
                SELECT e.client_id,
                       e.bonus_shares AS expected,
                       COALESCE(l.share_units, 0) AS actual
                FROM ca_stock_entitlements e
                LEFT JOIN ca_ledger_sec_entries l
                       ON l.ca_id = e.ca_id AND l.client_id = e.client_id
                WHERE e.ca_id = ?
                  AND ABS(e.bonus_shares - COALESCE(l.share_units, 0)) > 0.0001
                """;
        return queryBreaks(positionMismatchSql, caId, reconDate, BreakType.POSITION_MISMATCH);
    }

    private List<CaReconBreak> queryBreaks(String sql, String caId, LocalDate reconDate,
                                            BreakType breakType) throws SQLException {
        List<CaReconBreak> breaks = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String breakId    = java.util.UUID.randomUUID().toString();
                    String clientId   = rs.getString("client_id");
                    BigDecimal expected = rs.getBigDecimal("expected");
                    BigDecimal actual   = rs.getBigDecimal("actual");
                    BigDecimal diff     = expected.subtract(actual).setScale(4, RoundingMode.HALF_UP);

                    CaReconBreak brk = new CaReconBreak(breakId, caId, clientId, breakType,
                            expected, actual, diff, reconDate, LocalDateTime.now());
                    persistBreak(brk);
                    eventPort.publish("ca.recon.break", brk);
                    breaksDetectedCounter.increment();
                    breaks.add(brk);
                }
            }
        }
        return breaks;
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private void persistBreak(CaReconBreak brk) throws SQLException {
        String sql = """
                INSERT INTO ca_recon_breaks
                    (break_id, ca_id, client_id, break_type, expected_value, actual_value,
                     difference, recon_date, detected_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')
                ON CONFLICT (ca_id, client_id, break_type, recon_date) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, brk.breakId()); ps.setString(2, brk.caId());
            ps.setString(3, brk.clientId()); ps.setString(4, brk.breakType().name());
            ps.setBigDecimal(5, brk.expectedValue()); ps.setBigDecimal(6, brk.actualValue());
            ps.setBigDecimal(7, brk.difference()); ps.setObject(8, brk.reconDate());
            ps.setObject(9, brk.detectedAt());
            ps.executeUpdate();
        }
    }

    private long countOpenBreaks() throws SQLException {
        String sql = "SELECT COUNT(*) FROM ca_recon_breaks WHERE status='OPEN'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }
}

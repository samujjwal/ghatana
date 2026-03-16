package com.ghatana.appplatform.corporateactions.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
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
 * @doc.purpose Double-entry cash posting for CA dividend payments via K-16 LedgerPort.
 *              Journal: DEBIT issuer_payable / CREDIT holder_cash (net of tax) / CREDIT
 *              tax_payable. Validates that total debits == total credits before committing.
 *              Satisfies STORY-D12-010.
 * @doc.layer   Domain
 * @doc.pattern K-16 double-entry ledger; debit/credit balance check; cash posting; Counter.
 */
public class CaLedgerCashPostingService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final LedgerPort       ledgerPort;
    private final Counter          journalPostedCounter;

    public CaLedgerCashPostingService(HikariDataSource dataSource, Executor executor,
                                       LedgerPort ledgerPort, MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.ledgerPort           = ledgerPort;
        this.journalPostedCounter = Counter.builder("ca.ledger.cash_journals_total").register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    /** K-16 double-entry ledger port. */
    public interface LedgerPort {
        void post(String journalId, String caId, String clientId,
                  String debitAccount, String creditAccount, BigDecimal amount,
                  String currency, LocalDate valueDate, String narrative);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record CashJournalEntry(String journalId, String caId, String clientId,
                                    BigDecimal gross, BigDecimal tax, BigDecimal net,
                                    LocalDate valueDate, LocalDateTime postedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<CashJournalEntry>> postCashDividends(String caId, String currency) {
        return Promise.ofBlocking(executor, () -> {
            List<EntitlementRow> rows = loadEntitlementsWithTax(caId);
            List<CashJournalEntry> entries = new ArrayList<>();

            // Validate balance before posting
            BigDecimal totalDebit  = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;
            for (EntitlementRow r : rows) {
                totalDebit  = totalDebit.add(r.gross());
                totalCredit = totalCredit.add(r.net()).add(r.tax());
            }
            if (totalDebit.compareTo(totalCredit) != 0) {
                throw new IllegalStateException("Ledger imbalance for CA " + caId
                        + ": debit=" + totalDebit + " credit=" + totalCredit);
            }

            LocalDate paymentDate = loadPaymentDate(caId);
            for (EntitlementRow r : rows) {
                String journalId = UUID.randomUUID().toString();

                // Debit: issuer_payable (reduce liability)
                ledgerPort.post(journalId, caId, r.clientId(),
                        "ISSUER_PAYABLE_" + caId, "HOLDER_CASH_" + r.clientId(),
                        r.net(), currency, paymentDate,
                        "CA cash dividend net: " + caId);

                // Credit tax payable
                if (r.tax().compareTo(BigDecimal.ZERO) > 0) {
                    ledgerPort.post(journalId + "_TAX", caId, r.clientId(),
                            "ISSUER_PAYABLE_" + caId, "TAX_PAYABLE",
                            r.tax(), currency, paymentDate,
                            "CA TDS: " + caId);
                }

                entries.add(persistJournal(journalId, caId, r.clientId(), r.gross(), r.tax(), r.net(), paymentDate));
                journalPostedCounter.increment();
            }
            return entries;
        });
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private CashJournalEntry persistJournal(String journalId, String caId, String clientId,
                                             BigDecimal gross, BigDecimal tax, BigDecimal net,
                                             LocalDate valueDate) throws SQLException {
        String sql = """
                INSERT INTO ca_ledger_cash_entries
                    (journal_id, ca_id, client_id, gross_amount, tax_amount, net_amount,
                     value_date, posted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (journal_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, journalId); ps.setString(2, caId); ps.setString(3, clientId);
            ps.setBigDecimal(4, gross); ps.setBigDecimal(5, tax); ps.setBigDecimal(6, net);
            ps.setObject(7, valueDate);
            ps.executeUpdate();
        }
        return new CashJournalEntry(journalId, caId, clientId, gross, tax, net, valueDate, LocalDateTime.now());
    }

    record EntitlementRow(String clientId, BigDecimal gross, BigDecimal tax, BigDecimal net) {}

    private List<EntitlementRow> loadEntitlementsWithTax(String caId) throws SQLException {
        String sql = """
                SELECT e.client_id, e.gross_amount,
                       COALESCE(t.tax_amount, 0)  AS tax,
                       COALESCE(t.net_payable, e.gross_amount) AS net
                FROM ca_cash_entitlements e
                LEFT JOIN ca_tax_withholdings t
                       ON t.ca_id = e.ca_id AND t.client_id = e.client_id
                WHERE e.ca_id=?
                """;
        List<EntitlementRow> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new EntitlementRow(rs.getString("client_id"),
                        rs.getBigDecimal("gross_amount"), rs.getBigDecimal("tax"),
                        rs.getBigDecimal("net")));
            }
        }
        return result;
    }

    private LocalDate loadPaymentDate(String caId) throws SQLException {
        String sql = "SELECT payment_date FROM corporate_actions WHERE ca_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("CA not found: " + caId);
                return rs.getObject("payment_date", LocalDate.class);
            }
        }
    }
}

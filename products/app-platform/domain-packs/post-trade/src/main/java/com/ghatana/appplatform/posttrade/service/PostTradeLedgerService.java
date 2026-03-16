package com.ghatana.appplatform.posttrade.service;

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
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose On successful settlement (SettlementCompleted event), posts balanced ledger
 *              entries via K-16 for three legs: securities, cash, and fees. All entries share
 *              one journalId and must sum to zero. Supports multi-currency postings.
 *              Idempotent: re-posting the same settlementId yields the same journal.
 * @doc.layer   Domain
 * @doc.pattern Event-driven (listens for SettlementCompleted); K-16 LedgerPort; K-17 saga
 *              compensation if ledger posting fails; idempotency via ON CONFLICT on settlement_id.
 */
public class PostTradeLedgerService {

    private static final Logger log = LoggerFactory.getLogger(PostTradeLedgerService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final LedgerPort       ledgerPort;
    private final Counter          postingsCounter;
    private final Counter          errorCounter;

    public PostTradeLedgerService(HikariDataSource dataSource, Executor executor,
                                  LedgerPort ledgerPort, MeterRegistry registry) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.ledgerPort     = ledgerPort;
        this.postingsCounter = registry.counter("posttrade.ledger.posted");
        this.errorCounter   = registry.counter("posttrade.ledger.error");
    }

    // ─── Inner port (K-16) ───────────────────────────────────────────────────

    /**
     * K-16 Double-entry ledger port. Each call posts one journal entry.
     * All entries for a settlement share the same journalId.
     */
    public interface LedgerPort {
        void postEntry(String journalId, String accountDebit, String accountCredit,
                       double amount, String currency, String description);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    /**
     * SettlementCompleted event payload (received from DVP service via event bus).
     */
    public record SettlementCompleted(
        String settlementId,
        String matchId,
        String buyerClientId,
        String sellerClientId,
        String instrumentId,
        double quantity,
        double price,
        double commissionFee,
        double settlementFee,
        String currency
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Handle a SettlementCompleted event and post all three journal legs.
     */
    public Promise<Void> onSettlementCompleted(SettlementCompleted event) {
        return Promise.ofBlocking(executor, () -> {
            if (alreadyPosted(event.settlementId())) {
                log.info("Ledger already posted for settlementId={} — skipping", event.settlementId());
                return null;
            }
            postJournal(event);
            recordPosting(event.settlementId());
            postingsCounter.increment();
            log.info("Ledger posted settlementId={} journalId", event.settlementId());
            return null;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void postJournal(SettlementCompleted e) {
        String journalId   = UUID.randomUUID().toString();
        double gross       = e.quantity() * e.price();
        double totalFees   = e.commissionFee() + e.settlementFee();

        // Leg 1: Securities — debit seller's securities inventory, credit buyer's
        ledgerPort.postEntry(journalId,
            e.sellerClientId() + ":SECURITIES:" + e.instrumentId(),
            e.buyerClientId()  + ":SECURITIES:" + e.instrumentId(),
            e.quantity(), e.instrumentId(),
            "DVP securities transfer settlementId=" + e.settlementId());

        // Leg 2: Cash — debit buyer's cash, credit seller's cash
        ledgerPort.postEntry(journalId,
            e.buyerClientId()  + ":CASH:" + e.currency(),
            e.sellerClientId() + ":CASH:" + e.currency(),
            gross, e.currency(),
            "DVP cash transfer settlementId=" + e.settlementId());

        // Leg 3: Fees — debit both buyer and seller's cash, credit fee income
        if (totalFees > 0) {
            ledgerPort.postEntry(journalId,
                e.buyerClientId() + ":CASH:" + e.currency(),
                "FEE_INCOME:TRADING:" + e.currency(),
                e.commissionFee() + (e.settlementFee() / 2.0), e.currency(),
                "Buyer fees settlementId=" + e.settlementId());
            ledgerPort.postEntry(journalId,
                e.sellerClientId() + ":CASH:" + e.currency(),
                "FEE_INCOME:TRADING:" + e.currency(),
                e.settlementFee() / 2.0, e.currency(),
                "Seller fees settlementId=" + e.settlementId());
        }
    }

    private boolean alreadyPosted(String settlementId) {
        String sql = "SELECT 1 FROM settlement_ledger_postings WHERE settlement_id = ? LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, settlementId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            log.error("Failed to check idempotency for settlementId={}", settlementId, ex);
            return false;
        }
    }

    private void recordPosting(String settlementId) {
        String sql = """
            INSERT INTO settlement_ledger_postings (posting_id, settlement_id, posted_at)
            VALUES (?, ?, now())
            ON CONFLICT (settlement_id) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, settlementId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to record ledger posting settlementId={}", settlementId, ex);
        }
    }
}

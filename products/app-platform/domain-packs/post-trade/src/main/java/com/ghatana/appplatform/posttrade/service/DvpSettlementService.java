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
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Implements Delivery vs Payment (DVP) settlement via K-17 saga orchestration.
 *              Step 1: reserve securities in deliverer's account.
 *              Step 2: reserve cash in receiver's account.
 *              Step 3: atomic transfer of both legs.
 *              Compensation reverses completed steps if any step fails. Ledger posting via K-16.
 * @doc.layer   Domain
 * @doc.pattern K-17 saga with compensation; inner SecuritiesReservationPort and CashReservationPort;
 *              inner LedgerPort (K-16); all steps write audit to dvp_saga_log table.
 */
public class DvpSettlementService {

    private static final Logger log = LoggerFactory.getLogger(DvpSettlementService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    private final HikariDataSource       dataSource;
    private final Executor               executor;
    private final SecuritiesReservationPort securitiesPort;
    private final CashReservationPort    cashPort;
    private final LedgerPort             ledgerPort;
    private final Counter                dvpSuccessCounter;
    private final Counter                dvpCompensationCounter;

    public DvpSettlementService(HikariDataSource dataSource, Executor executor,
                                SecuritiesReservationPort securitiesPort,
                                CashReservationPort cashPort, LedgerPort ledgerPort,
                                MeterRegistry registry) {
        this.dataSource             = dataSource;
        this.executor               = executor;
        this.securitiesPort         = securitiesPort;
        this.cashPort               = cashPort;
        this.ledgerPort             = ledgerPort;
        this.dvpSuccessCounter      = registry.counter("posttrade.dvp.settlement", "result", "success");
        this.dvpCompensationCounter = registry.counter("posttrade.dvp.settlement", "result", "compensated");
    }

    // ─── Inner ports (K-17 saga participants) ────────────────────────────────

    public interface SecuritiesReservationPort {
        String   reserveSecurities(String delivererId, String instrumentId, double quantity);  // returns reservationId
        boolean  releaseReservation(String reservationId);
        boolean  executeTransfer(String reservationId, String receiverId);
    }

    public interface CashReservationPort {
        String   reserveCash(String receiverId, double amount, String currency);  // returns reservationId
        boolean  releaseReservation(String reservationId);
        boolean  executeTransfer(String reservationId, String delivererId);
    }

    /**
     * K-16 Ledger posting port.
     */
    public interface LedgerPort {
        void postJournal(String journalId, String description,
                         String debitAccount, String creditAccount,
                         double amount, String currency);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record DvpRequest(
        String matchId,
        String delivererId,
        String receiverId,
        String instrumentId,
        double quantity,
        double amount,
        String currency
    ) {}

    public record DvpResult(
        String sagaId,
        String matchId,
        String status,           // COMPLETED | COMPENSATED | FAILED
        String securitiesReservationId,
        String cashReservationId
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Execute DVP settlement for a matched settlement pair.
     */
    public Promise<DvpResult> execute(DvpRequest request) {
        return Promise.ofBlocking(executor, () -> {
            String sagaId = UUID.randomUUID().toString();
            logSaga(sagaId, request.matchId(), "STARTED", null, null);
            return runSaga(sagaId, request);
        });
    }

    // ─── Saga steps ──────────────────────────────────────────────────────────

    private DvpResult runSaga(String sagaId, DvpRequest req) {
        // Step 1: Reserve securities
        String secResId = null;
        try {
            secResId = securitiesPort.reserveSecurities(req.delivererId(), req.instrumentId(), req.quantity());
            logSaga(sagaId, req.matchId(), "SECURITIES_RESERVED", secResId, null);
        } catch (Exception ex) {
            log.error("DVP Step 1 failed sagaId={}: {}", sagaId, ex.getMessage());
            logSaga(sagaId, req.matchId(), "FAILED_STEP1", null, ex.getMessage());
            return new DvpResult(sagaId, req.matchId(), "FAILED", null, null);
        }

        // Step 2: Reserve cash
        String cashResId = null;
        try {
            cashResId = cashPort.reserveCash(req.receiverId(), req.amount(), req.currency());
            logSaga(sagaId, req.matchId(), "CASH_RESERVED", secResId, cashResId);
        } catch (Exception ex) {
            log.error("DVP Step 2 failed sagaId={} — compensating Step 1", sagaId);
            securitiesPort.releaseReservation(secResId);
            logSaga(sagaId, req.matchId(), "COMPENSATED_STEP1", secResId, null);
            dvpCompensationCounter.increment();
            return new DvpResult(sagaId, req.matchId(), "COMPENSATED", secResId, null);
        }

        // Step 3: Atomic transfer
        try {
            boolean secOk  = securitiesPort.executeTransfer(secResId, req.receiverId());
            boolean cashOk = cashPort.executeTransfer(cashResId, req.delivererId());
            if (!secOk || !cashOk) throw new IllegalStateException("Transfer execution returned false");

            postLedgerEntries(sagaId, req);
            logSaga(sagaId, req.matchId(), "COMPLETED", secResId, cashResId);
            dvpSuccessCounter.increment();
            log.info("DVP settlement completed sagaId={} matchId={}", sagaId, req.matchId());
            return new DvpResult(sagaId, req.matchId(), "COMPLETED", secResId, cashResId);
        } catch (Exception ex) {
            log.error("DVP Step 3 failed sagaId={} — compensating both steps", sagaId);
            securitiesPort.releaseReservation(secResId);
            cashPort.releaseReservation(cashResId);
            logSaga(sagaId, req.matchId(), "COMPENSATED_BOTH", secResId, cashResId);
            dvpCompensationCounter.increment();
            return new DvpResult(sagaId, req.matchId(), "COMPENSATED", secResId, cashResId);
        }
    }

    private void postLedgerEntries(String sagaId, DvpRequest req) {
        String journalId = UUID.randomUUID().toString();
        // Securities: debit deliverer, credit receiver
        ledgerPort.postJournal(journalId, "DVP securities leg " + sagaId,
            req.delivererId() + ":SECURITIES", req.receiverId() + ":SECURITIES",
            req.quantity(), req.instrumentId());
        // Cash: debit receiver, credit deliverer
        ledgerPort.postJournal(journalId, "DVP cash leg " + sagaId,
            req.receiverId() + ":CASH", req.delivererId() + ":CASH",
            req.amount(), req.currency());
    }

    private void logSaga(String sagaId, String matchId, String step,
                          String secResId, String cashResId) {
        String sql = """
            INSERT INTO dvp_saga_log
                (log_id, saga_id, match_id, step, sec_reservation_id, cash_reservation_id, logged_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, sagaId);
            ps.setString(3, matchId);
            ps.setString(4, step);
            ps.setString(5, secResId);
            ps.setString(6, cashResId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to log saga step sagaId={} step={}", sagaId, step, ex);
        }
    }
}

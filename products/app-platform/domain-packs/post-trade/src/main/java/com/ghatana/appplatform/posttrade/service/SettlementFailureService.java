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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Detects settlement failures (instructions not settled by settlement_date +
 *              grace period) and initiates buy-in process when a seller fails to deliver
 *              securities after the grace period expires. Failure types: SECURITIES_SHORTFALL,
 *              CASH_SHORTFALL, CSD_REJECT, TIMEOUT. The buy-in saga is coordinated via K-17.
 *              Satisfies STORY-D09-015.
 * @doc.layer   Domain
 * @doc.pattern Scheduled detection; K-17 saga for buy-in compensation; INSERT-only failure log;
 *              configurable grace period via K-02.
 */
public class SettlementFailureService {

    private static final Logger log = LoggerFactory.getLogger(SettlementFailureService.class);

    private static final int DEFAULT_GRACE_DAYS = 2;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ConfigPort       configPort;
    private final BuyInPort        buyInPort;
    private final Counter          failureCounter;
    private final Counter          buyInCounter;

    public SettlementFailureService(HikariDataSource dataSource, Executor executor,
                                    ConfigPort configPort, BuyInPort buyInPort,
                                    MeterRegistry registry) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.configPort     = configPort;
        this.buyInPort      = buyInPort;
        this.failureCounter = registry.counter("posttrade.settlement.failures");
        this.buyInCounter   = registry.counter("posttrade.settlement.buyins");
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    public interface ConfigPort {
        int getGracePeriodDays(String instrumentId);  // default 2
    }

    /** K-17 saga port for buy-in order orchestration. */
    public interface BuyInPort {
        String initiateBuyIn(String settlementId, String instrumentId, long quantity,
                             String failingPartyId, String buyerClientId);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record FailedInstruction(String settlementId, String instrumentId, String sellerClientId,
                                    String buyerClientId, long quantity, LocalDate settlementDate,
                                    String failureType) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** EOD detection: find all overdue, unresolved settlement instructions. */
    public Promise<List<FailedInstruction>> detectFailures(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> {
            List<FailedInstruction> failures = new ArrayList<>();
            try (Connection conn = dataSource.getConnection()) {
                String sql = """
                        SELECT si.settlement_id, si.instrument_id, si.seller_client_id,
                               si.buyer_client_id, si.quantity, si.settlement_date_ad,
                               si.failure_type
                        FROM settlement_instructions si
                        WHERE si.status NOT IN ('SETTLED','CANCELLED','FAILED')
                          AND si.settlement_date_ad < ?
                        """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, runDate);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String instrumentId = rs.getString("instrument_id");
                            LocalDate settleDate = rs.getObject("settlement_date_ad", LocalDate.class);
                            int grace = configPort.getGracePeriodDays(instrumentId);
                            if (!runDate.isAfter(settleDate.plusDays(grace))) {
                                continue; // still within grace period
                            }
                            String failureType = rs.getString("failure_type");
                            if (failureType == null) failureType = "TIMEOUT";
                            FailedInstruction fi = new FailedInstruction(
                                    rs.getString("settlement_id"), instrumentId,
                                    rs.getString("seller_client_id"),
                                    rs.getString("buyer_client_id"),
                                    rs.getLong("quantity"), settleDate, failureType);
                            failures.add(fi);
                            recordFailure(conn, fi, runDate);
                            failureCounter.increment();
                        }
                    }
                }
            }
            return failures;
        });
    }

    /**
     * Initiates buy-in saga for SECURITIES_SHORTFALL failures where seller has
     * exceeded the grace period. The buyer pays market price; difference charged to seller.
     */
    public Promise<String> initiateBuyIn(FailedInstruction instruction) {
        return Promise.ofBlocking(executor, () -> {
            String buyInId = buyInPort.initiateBuyIn(
                    instruction.settlementId(), instruction.instrumentId(),
                    instruction.quantity(), instruction.sellerClientId(),
                    instruction.buyerClientId());
            try (Connection conn = dataSource.getConnection()) {
                updateBuyInReference(conn, instruction.settlementId(), buyInId);
            }
            buyInCounter.increment();
            log.info("Buy-in initiated: settlementId={} buyInId={}", instruction.settlementId(), buyInId);
            return buyInId;
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void recordFailure(Connection conn, FailedInstruction fi, LocalDate detectedDate)
            throws SQLException {
        String sql = """
                INSERT INTO settlement_failures
                    (failure_id, settlement_id, instrument_id, seller_client_id,
                     buyer_client_id, quantity, settlement_date_ad, failure_type,
                     detected_date, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')
                ON CONFLICT (settlement_id) DO UPDATE
                    SET failure_type = EXCLUDED.failure_type,
                        detected_date = EXCLUDED.detected_date
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, fi.settlementId());
            ps.setString(3, fi.instrumentId());
            ps.setString(4, fi.sellerClientId());
            ps.setString(5, fi.buyerClientId());
            ps.setLong(6, fi.quantity());
            ps.setObject(7, fi.settlementDate());
            ps.setString(8, fi.failureType());
            ps.setObject(9, detectedDate);
            ps.executeUpdate();
        }
        // also mark instruction as FAILED
        String upd = "UPDATE settlement_instructions SET status='FAILED' WHERE settlement_id=?";
        try (PreparedStatement ps = conn.prepareStatement(upd)) {
            ps.setString(1, fi.settlementId());
            ps.executeUpdate();
        }
    }

    private void updateBuyInReference(Connection conn, String settlementId, String buyInId)
            throws SQLException {
        String sql = "UPDATE settlement_failures SET buy_in_id=?, status='BUY_IN_INITIATED' WHERE settlement_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buyInId);
            ps.setString(2, settlementId);
            ps.executeUpdate();
        }
    }
}

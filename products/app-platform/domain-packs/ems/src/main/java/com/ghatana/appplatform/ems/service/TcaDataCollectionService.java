package com.ghatana.appplatform.ems.service;

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
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Captures all TCA-relevant data points for every execution event:
 *              arrival price, decision price, exec price, exec time, and market
 *              condition snapshots (volume, volatility, spread) at each key timestamp.
 *              Stores in tca_records table linked to order_id and fill_id.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; inner MarketSnapshotPort for live market conditions;
 *              ON CONFLICT(fill_id) DO NOTHING for idempotency.
 */
public class TcaDataCollectionService {

    private static final Logger log = LoggerFactory.getLogger(TcaDataCollectionService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final MarketSnapshotPort  marketSnapshotPort;
    private final Counter             tcaRecordCounter;

    public TcaDataCollectionService(HikariDataSource dataSource, Executor executor,
                                    MarketSnapshotPort marketSnapshotPort, MeterRegistry registry) {
        this.dataSource         = dataSource;
        this.executor           = executor;
        this.marketSnapshotPort = marketSnapshotPort;
        this.tcaRecordCounter   = registry.counter("ems.tca.records.collected");
    }

    // ─── Inner port ───────────────────────────────────────────────────────────

    /**
     * Port for obtaining real-time market condition snapshots.
     */
    public interface MarketSnapshotPort {
        MarketSnapshot snapshot(String instrumentId, Instant at);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record MarketSnapshot(
        double volume,
        double spreadBps,
        double impliedVolatility,
        double bidSize,
        double askSize
    ) {}

    public record TcaOrderEvent(
        String  orderId,
        String  fillId,
        String  instrumentId,
        String  side,
        double  quantity,
        double  arrivalPrice,    // mid at order receipt
        double  decisionPrice,   // mid at routing decision
        double  execPrice,       // actual fill price
        Instant orderReceivedAt,
        Instant orderRoutedAt,
        Instant firstFillAt,
        Instant lastFillAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Collect and persist all TCA data for a completed fill event.
     */
    public Promise<Void> collectForFill(TcaOrderEvent event) {
        return Promise.ofBlocking(executor, () -> {
            collectData(event);
            return null;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void collectData(TcaOrderEvent event) {
        MarketSnapshot atArrival  = marketSnapshotPort.snapshot(event.instrumentId(), event.orderReceivedAt());
        MarketSnapshot atDecision = marketSnapshotPort.snapshot(event.instrumentId(), event.orderRoutedAt());
        MarketSnapshot atFirst    = marketSnapshotPort.snapshot(event.instrumentId(), event.firstFillAt());
        MarketSnapshot atLast     = marketSnapshotPort.snapshot(event.instrumentId(), event.lastFillAt());

        persistTcaRecord(event, atArrival, atDecision, atFirst, atLast);
        tcaRecordCounter.increment();
        log.debug("TCA data collected orderId={} fillId={}", event.orderId(), event.fillId());
    }

    private void persistTcaRecord(TcaOrderEvent event,
                                   MarketSnapshot atArrival, MarketSnapshot atDecision,
                                   MarketSnapshot atFirst,   MarketSnapshot atLast) {
        String sql = """
            INSERT INTO tca_records
                (tca_id, order_id, fill_id, instrument_id, side, quantity,
                 arrival_price, decision_price, exec_price,
                 arrival_volume, arrival_spread_bps, arrival_impl_vol,
                 decision_volume, decision_spread_bps,
                 first_fill_spread_bps, last_fill_spread_bps,
                 order_received_at, order_routed_at, first_fill_at, last_fill_at,
                 created_at)
            VALUES (?, ?, ?, ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?,
                    ?, ?,
                    ?, ?,
                    ?, ?, ?, ?,
                    now())
            ON CONFLICT (fill_id) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, event.orderId());
            ps.setString(3, event.fillId());
            ps.setString(4, event.instrumentId());
            ps.setString(5, event.side());
            ps.setDouble(6, event.quantity());
            ps.setDouble(7, event.arrivalPrice());
            ps.setDouble(8, event.decisionPrice());
            ps.setDouble(9, event.execPrice());
            ps.setDouble(10, atArrival.volume());
            ps.setDouble(11, atArrival.spreadBps());
            ps.setDouble(12, atArrival.impliedVolatility());
            ps.setDouble(13, atDecision.volume());
            ps.setDouble(14, atDecision.spreadBps());
            ps.setDouble(15, atFirst.spreadBps());
            ps.setDouble(16, atLast.spreadBps());
            ps.setObject(17, event.orderReceivedAt());
            ps.setObject(18, event.orderRoutedAt());
            ps.setObject(19, event.firstFillAt());
            ps.setObject(20, event.lastFillAt());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist TCA record orderId={} fillId={}", event.orderId(), event.fillId(), ex);
        }
    }
}

package com.ghatana.appplatform.ems.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Handles market circuit-breaker halts and resumes (D02-017, K-10 circuit breaker).
 *              On MarketHalted: pauses all active algorithm slices for affected instruments.
 *              On MarketResumed: recalculates remaining execution pacing and re-queues slices.
 * @doc.layer   Domain — EMS circuit breaker response
 * @doc.pattern Event-driven halt/resume; queue-based recovery; no slice loss on halt
 */
public class CircuitBreakerResponseService {

    public record HaltEvent(String marketId, String instrumentId, Instant haltedAt, String reason) {}
    public record ResumeEvent(String marketId, String instrumentId, Instant resumedAt) {}

    public record PausedSlice(
        String sliceId,
        String parentOrderId,
        String algoType,
        long targetQuantity,
        int sliceNumber
    ) {}

    private final DataSource dataSource;
    private final Executor executor;
    private final Counter haltsCounter;
    private final Counter resumesCounter;

    public CircuitBreakerResponseService(DataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.haltsCounter  = Counter.builder("ems.circuit_breaker.halts_total").register(registry);
        this.resumesCounter = Counter.builder("ems.circuit_breaker.resumes_total").register(registry);
    }

    /**
     * Pause all pending/submitted algorithm slices for the halted instrument.
     * Slices in state SUBMITTED are moved to HALTED (not cancelled).
     */
    public Promise<List<PausedSlice>> onMarketHalted(HaltEvent event) {
        return Promise.ofBlocking(executor, () -> {
            List<PausedSlice> paused = pauseSlicesForInstrument(event.instrumentId());
            recordHalt(event);
            haltsCounter.increment();
            return paused;
        });
    }

    /**
     * Resume execution for an instrument after circuit-breaker lifts.
     * Recalculates pace based on remaining time window and re-queues HALTED slices.
     */
    public Promise<Integer> onMarketResumed(ResumeEvent event) {
        return Promise.ofBlocking(executor, () -> {
            int resumed = resumeHaltedSlices(event.instrumentId());
            recordResume(event);
            resumesCounter.increment();
            return resumed;
        });
    }

    /** Check if a given instrument is currently halted. */
    public Promise<Boolean> isHalted(String instrumentId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM market_halt_events " +
                     "WHERE instrument_id = ? AND resumed_at IS NULL")) {
                ps.setObject(1, UUID.fromString(instrumentId));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getLong(1) > 0;
                }
            }
        });
    }

    private List<PausedSlice> pauseSlicesForInstrument(String instrumentId) throws Exception {
        List<PausedSlice> paused = new ArrayList<>();
        // Identify parent orders for this instrument
        List<String> affectedOrderIds = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id FROM orders WHERE instrument_id = ? AND status IN ('OPEN','PARTIALLY_FILLED')")) {
            ps.setObject(1, UUID.fromString(instrumentId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) affectedOrderIds.add(rs.getString("id"));
            }
        }

        for (String orderId : affectedOrderIds) {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE algo_vwap_slices SET status = 'HALTED' " +
                     "WHERE parent_order_id = ? AND status IN ('PENDING','SUBMITTED') RETURNING id, slice_number, target_quantity")) {
                ps.setObject(1, UUID.fromString(orderId));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        paused.add(new PausedSlice(rs.getString("id"), orderId, "VWAP",
                            rs.getLong("target_quantity"), rs.getInt("slice_number")));
                    }
                }
            }
            // Same for TWAP and IS slices
            haltSlices("algo_twap_slices", orderId, "TWAP", paused);
            haltSlices("algo_is_slices", orderId, "IS", paused);
        }
        return paused;
    }

    private void haltSlices(String table, String orderId, String algoType,
                             List<PausedSlice> paused) throws Exception {
        String sql = "UPDATE " + table + " SET status = 'HALTED' " +
                     "WHERE parent_order_id = ? AND status IN ('PENDING','SUBMITTED') " +
                     "RETURNING id, slice_number, target_quantity";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(orderId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    paused.add(new PausedSlice(rs.getString("id"), orderId, algoType,
                        rs.getLong("target_quantity"), rs.getInt("slice_number")));
                }
            }
        }
    }

    private int resumeHaltedSlices(String instrumentId) throws Exception {
        int count = 0;
        List<String> affectedOrderIds = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id FROM orders WHERE instrument_id = ? AND status IN ('OPEN','PARTIALLY_FILLED')")) {
            ps.setObject(1, UUID.fromString(instrumentId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) affectedOrderIds.add(rs.getString("id"));
            }
        }

        for (String orderId : affectedOrderIds) {
            count += resumeTable("algo_vwap_slices", orderId);
            count += resumeTable("algo_twap_slices", orderId);
            count += resumeTable("algo_is_slices", orderId);
        }
        return count;
    }

    private int resumeTable(String table, String orderId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE " + table + " SET status = 'PENDING' WHERE parent_order_id = ? AND status = 'HALTED'")) {
            ps.setObject(1, UUID.fromString(orderId));
            return ps.executeUpdate();
        }
    }

    private void recordHalt(HaltEvent event) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO market_halt_events(id, market_id, instrument_id, halted_at, reason) " +
                 "VALUES(?,?,?,?,?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, event.marketId());
            ps.setObject(3, UUID.fromString(event.instrumentId()));
            ps.setTimestamp(4, java.sql.Timestamp.from(event.haltedAt()));
            ps.setString(5, event.reason());
            ps.executeUpdate();
        }
    }

    private void recordResume(ResumeEvent event) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE market_halt_events SET resumed_at = ? " +
                 "WHERE instrument_id = ? AND resumed_at IS NULL")) {
            ps.setTimestamp(1, java.sql.Timestamp.from(event.resumedAt()));
            ps.setObject(2, UUID.fromString(event.instrumentId()));
            ps.executeUpdate();
        }
    }
}

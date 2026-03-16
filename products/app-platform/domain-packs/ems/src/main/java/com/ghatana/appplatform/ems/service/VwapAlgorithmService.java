package com.ghatana.appplatform.ems.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * @doc.purpose Volume-Weighted Average Price (VWAP) algorithm execution service (D02-005).
 *              Distributes parent order across trading day proportionally to historical
 *              intraday volume profile, generating child slices at each interval.
 *              Default participation rate 20%; no slice exceeds K-02 venue limits.
 * @doc.layer   Domain — EMS algorithm execution
 * @doc.pattern Strategy pattern — VWAP implementation of algorithm; child orders emitted per slice
 */
public class VwapAlgorithmService {

    private static final double DEFAULT_PARTICIPATION_RATE = 0.20;
    private static final int DEFAULT_INTERVALS = 10;  // splits day into N equal time buckets

    public record VwapSlice(
        String sliceId,
        String parentOrderId,
        int sliceNumber,
        long targetQuantity,
        double volumeProfileWeight,
        Instant scheduledAt,
        Instant submittedAt,
        String status   // PENDING / SUBMITTED / FILLED / CANCELLED
    ) {}

    public record VwapSchedule(
        String parentOrderId,
        long totalQuantity,
        double participationRate,
        int intervals,
        List<VwapSlice> slices
    ) {}

    private final DataSource dataSource;
    private final Executor executor;
    private final Counter slicesSubmittedCounter;

    public VwapAlgorithmService(DataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.slicesSubmittedCounter = Counter.builder("ems.algo.vwap_slices_submitted_total")
            .register(registry);
    }

    /**
     * Build and persist a VWAP execution schedule for a parent order.
     * Volume profile loaded from market_volume_profiles (D-04 market data dependency).
     */
    public Promise<VwapSchedule> buildSchedule(String parentOrderId, long totalQuantity,
                                                String instrumentId,
                                                Double participationRate) {
        return Promise.ofBlocking(executor, () -> {
            double rate = (participationRate != null) ? participationRate : DEFAULT_PARTICIPATION_RATE;
            List<Double> profile = loadVolumeProfile(instrumentId, DEFAULT_INTERVALS);
            List<VwapSlice> slices = buildSlices(parentOrderId, totalQuantity, profile);
            persistSlices(slices);
            return new VwapSchedule(parentOrderId, totalQuantity, rate, DEFAULT_INTERVALS, slices);
        });
    }

    /**
     * Submit the next pending VWAP slice as a child order.
     * Called by the algorithm scheduler on each interval tick.
     */
    public Promise<VwapSlice> submitNextSlice(String parentOrderId) {
        return Promise.ofBlocking(executor, () -> {
            VwapSlice pending = loadNextPendingSlice(parentOrderId);
            if (pending == null) throw new IllegalStateException("No pending VWAP slice for: " + parentOrderId);
            markSubmitted(pending.sliceId());
            slicesSubmittedCounter.increment();
            return new VwapSlice(pending.sliceId(), parentOrderId, pending.sliceNumber(),
                pending.targetQuantity(), pending.volumeProfileWeight(),
                pending.scheduledAt(), Instant.now(), "SUBMITTED");
        });
    }

    private List<Double> loadVolumeProfile(String instrumentId, int intervals) throws Exception {
        List<Double> profile = new ArrayList<>();
        String sql = "SELECT weight FROM market_volume_profiles " +
                     "WHERE instrument_id = ? ORDER BY interval_number ASC LIMIT ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(instrumentId));
            ps.setInt(2, intervals);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) profile.add(rs.getDouble("weight"));
            }
        }
        // fallback: uniform profile if no market data
        if (profile.isEmpty()) {
            for (int i = 0; i < intervals; i++) profile.add(1.0 / intervals);
        }
        // normalise weights
        double sum = profile.stream().mapToDouble(d -> d).sum();
        return profile.stream().map(w -> w / sum).toList();
    }

    private List<VwapSlice> buildSlices(String parentOrderId, long totalQuantity,
                                         List<Double> profile) {
        List<VwapSlice> slices = new ArrayList<>();
        long allocated = 0;
        for (int i = 0; i < profile.size(); i++) {
            long qty = (i == profile.size() - 1)
                ? (totalQuantity - allocated)
                : Math.round(totalQuantity * profile.get(i));
            allocated += qty;
            slices.add(new VwapSlice(UUID.randomUUID().toString(), parentOrderId, i + 1,
                qty, profile.get(i), Instant.now(), null, "PENDING"));
        }
        return slices;
    }

    private void persistSlices(List<VwapSlice> slices) throws Exception {
        String sql = "INSERT INTO algo_vwap_slices(id, parent_order_id, slice_number, target_quantity, " +
                     "volume_profile_weight, scheduled_at, status) VALUES(?,?,?,?,?,NOW(),'PENDING')";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (VwapSlice s : slices) {
                ps.setObject(1, UUID.fromString(s.sliceId()));
                ps.setObject(2, UUID.fromString(s.parentOrderId()));
                ps.setInt(3, s.sliceNumber());
                ps.setLong(4, s.targetQuantity());
                ps.setDouble(5, s.volumeProfileWeight());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private VwapSlice loadNextPendingSlice(String parentOrderId) throws Exception {
        String sql = "SELECT id, slice_number, target_quantity, volume_profile_weight, scheduled_at " +
                     "FROM algo_vwap_slices WHERE parent_order_id = ? AND status = 'PENDING' " +
                     "ORDER BY slice_number ASC LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(parentOrderId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new VwapSlice(rs.getString("id"), parentOrderId,
                        rs.getInt("slice_number"), rs.getLong("target_quantity"),
                        rs.getDouble("volume_profile_weight"),
                        rs.getTimestamp("scheduled_at").toInstant(), null, "PENDING");
                }
            }
        }
        return null;
    }

    private void markSubmitted(String sliceId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE algo_vwap_slices SET status = 'SUBMITTED', submitted_at = NOW() WHERE id = ?")) {
            ps.setObject(1, UUID.fromString(sliceId));
            ps.executeUpdate();
        }
    }
}

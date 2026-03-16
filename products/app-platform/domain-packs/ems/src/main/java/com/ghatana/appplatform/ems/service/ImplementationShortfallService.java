package com.ghatana.appplatform.ems.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
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
 * @doc.purpose Implementation Shortfall (IS) algorithm service (D02-007).
 *              Balances market impact vs timing risk based on order urgency.
 *              HIGH urgency: front-loads execution to accept more market impact.
 *              LOW urgency: back-loads to minimize market impact; accepts more timing risk.
 *              Generates a schedule of child orders with IS-derived size weights.
 * @doc.layer   Domain — EMS algorithm execution
 * @doc.pattern Almgren-Chriss model approximation; urgency-driven trade-off curve
 */
public class ImplementationShortfallService {

    public enum Urgency { LOW, MEDIUM, HIGH }

    public record IsSlice(
        String sliceId,
        String parentOrderId,
        int sliceNumber,
        long targetQuantity,
        double scheduleWeight,
        Instant scheduledAt,
        String status
    ) {}

    public record IsSchedule(
        String parentOrderId,
        long totalQuantity,
        Urgency urgency,
        List<IsSlice> slices,
        double expectedIsBps    // estimated IS cost in basis points
    ) {}

    // Urgency-driven weighting matrix (front-loaded vs back-loaded).
    // Each row = slice weights for 5 time buckets, summing to 1.0.
    private static final double[][] URGENCY_WEIGHTS = {
        {0.08, 0.12, 0.20, 0.28, 0.32},  // LOW  — back-loaded
        {0.15, 0.20, 0.25, 0.22, 0.18},  // MEDIUM
        {0.35, 0.28, 0.20, 0.12, 0.05},  // HIGH — front-loaded
    };

    private final DataSource dataSource;
    private final Executor executor;
    private final Counter schedulesCreatedCounter;

    public ImplementationShortfallService(DataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.schedulesCreatedCounter = Counter.builder("ems.algo.is_schedules_created_total")
            .register(registry);
    }

    /**
     * Build an IS schedule for a parent order given market impact parameters.
     * @param dailyVolume estimated daily traded volume (for market impact calculation)
     */
    public Promise<IsSchedule> buildSchedule(String parentOrderId, long totalQuantity,
                                              Urgency urgency, long dailyVolume,
                                              BigDecimal arrivalMidPrice) {
        return Promise.ofBlocking(executor, () -> {
            double[] weights = URGENCY_WEIGHTS[urgency.ordinal()];
            List<IsSlice> slices = new ArrayList<>();
            long allocated = 0;
            Instant now = Instant.now();
            for (int i = 0; i < weights.length; i++) {
                long qty = (i == weights.length - 1)
                    ? (totalQuantity - allocated)
                    : Math.max(1, Math.round(totalQuantity * weights[i]));
                allocated += qty;
                slices.add(new IsSlice(UUID.randomUUID().toString(), parentOrderId,
                    i + 1, qty, weights[i], now, "PENDING"));
            }

            double participationFraction = dailyVolume > 0 ? (double) totalQuantity / dailyVolume : 0.0;
            double expectedIsBps = estimateIsBps(participationFraction, urgency);
            persistSlices(slices);
            schedulesCreatedCounter.increment();
            return new IsSchedule(parentOrderId, totalQuantity, urgency, slices, expectedIsBps);
        });
    }

    /** Submit next IS slice — equivalent pattern to VWAP/TWAP. */
    public Promise<IsSlice> submitNextSlice(String parentOrderId) {
        return Promise.ofBlocking(executor, () -> {
            IsSlice pending = loadNextPendingSlice(parentOrderId);
            if (pending == null) throw new IllegalStateException("No pending IS slice for: " + parentOrderId);
            markSubmitted(pending.sliceId());
            return new IsSlice(pending.sliceId(), parentOrderId, pending.sliceNumber(),
                pending.targetQuantity(), pending.scheduleWeight(), pending.scheduledAt(),
                "SUBMITTED");
        });
    }

    // Simple linear price impact model: IS ≈ participation_fraction * impact_coefficient * 10000 bps
    private double estimateIsBps(double participationFraction, Urgency urgency) {
        double impactCoeff = switch (urgency) {
            case LOW    -> 0.5;
            case MEDIUM -> 1.0;
            case HIGH   -> 2.0;
        };
        return participationFraction * impactCoeff * 10_000.0;
    }

    private void persistSlices(List<IsSlice> slices) throws Exception {
        String sql = "INSERT INTO algo_is_slices(id, parent_order_id, slice_number, target_quantity, " +
                     "schedule_weight, scheduled_at, status) VALUES(?,?,?,?,?,NOW(),'PENDING')";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (IsSlice s : slices) {
                ps.setObject(1, UUID.fromString(s.sliceId()));
                ps.setObject(2, UUID.fromString(s.parentOrderId()));
                ps.setInt(3, s.sliceNumber());
                ps.setLong(4, s.targetQuantity());
                ps.setDouble(5, s.scheduleWeight());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private IsSlice loadNextPendingSlice(String parentOrderId) throws Exception {
        String sql = "SELECT id, slice_number, target_quantity, schedule_weight, scheduled_at " +
                     "FROM algo_is_slices WHERE parent_order_id = ? AND status = 'PENDING' " +
                     "ORDER BY slice_number ASC LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(parentOrderId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new IsSlice(rs.getString("id"), parentOrderId,
                        rs.getInt("slice_number"), rs.getLong("target_quantity"),
                        rs.getDouble("schedule_weight"),
                        rs.getTimestamp("scheduled_at").toInstant(), "PENDING");
                }
            }
        }
        return null;
    }

    private void markSubmitted(String sliceId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE algo_is_slices SET status = 'SUBMITTED', submitted_at = NOW() WHERE id = ?")) {
            ps.setObject(1, UUID.fromString(sliceId));
            ps.executeUpdate();
        }
    }
}

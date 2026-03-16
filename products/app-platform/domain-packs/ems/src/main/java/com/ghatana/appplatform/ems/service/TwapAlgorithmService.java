package com.ghatana.appplatform.ems.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Time-Weighted Average Price (TWAP) algorithm execution service (D02-006).
 *              Distributes a parent order evenly across equal time slices, applying ±10%
 *              randomisation to avoid predictable execution patterns.
 * @doc.layer   Domain — EMS algorithm execution
 * @doc.pattern Strategy pattern — TWAP implementation; time-based scheduling; quantity jitter
 */
public class TwapAlgorithmService {

    private static final double JITTER_FACTOR = 0.10;     // ±10% randomisation

    public record TwapSlice(
        String sliceId,
        String parentOrderId,
        int sliceNumber,
        long baseQuantity,
        long jitteredQuantity,
        Instant scheduledAt,
        Instant submittedAt,
        String status
    ) {}

    public record TwapSchedule(
        String parentOrderId,
        long totalQuantity,
        int sliceCount,
        Duration sliceInterval,
        Instant startAt,
        Instant endAt,
        List<TwapSlice> slices
    ) {}

    private final DataSource dataSource;
    private final Executor executor;
    private final Counter slicesSubmittedCounter;
    private final Random jitterRng = new Random(42);   // seeded for reproducibility

    public TwapAlgorithmService(DataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.slicesSubmittedCounter = Counter.builder("ems.algo.twap_slices_submitted_total")
            .register(registry);
    }

    /**
     * Build and persist a TWAP execution schedule.
     * @param startAt  when to begin executing (typically now or open)
     * @param endAt    when to finish (typically close)
     * @param sliceCount number of equal time buckets
     */
    public Promise<TwapSchedule> buildSchedule(String parentOrderId, long totalQuantity,
                                                Instant startAt, Instant endAt, int sliceCount) {
        return Promise.ofBlocking(executor, () -> {
            Duration window = Duration.between(startAt, endAt);
            Duration interval = window.dividedBy(sliceCount);
            long baseQty = totalQuantity / sliceCount;

            List<TwapSlice> slices = new ArrayList<>();
            long allocated = 0;
            for (int i = 0; i < sliceCount; i++) {
                Instant schedAt = startAt.plus(interval.multipliedBy(i));
                long jitteredQty;
                if (i < sliceCount - 1) {
                    long jitter = Math.round(baseQty * JITTER_FACTOR * (jitterRng.nextDouble() * 2 - 1));
                    jitteredQty = Math.max(1, baseQty + jitter);
                    allocated += jitteredQty;
                } else {
                    jitteredQty = Math.max(1, totalQuantity - allocated);
                }
                slices.add(new TwapSlice(UUID.randomUUID().toString(), parentOrderId,
                    i + 1, baseQty, jitteredQty, schedAt, null, "PENDING"));
            }

            persistSlices(slices);
            return new TwapSchedule(parentOrderId, totalQuantity, sliceCount, interval, startAt, endAt, slices);
        });
    }

    /** Submit the next pending TWAP slice. Called by the algorithm scheduler. */
    public Promise<TwapSlice> submitNextSlice(String parentOrderId) {
        return Promise.ofBlocking(executor, () -> {
            TwapSlice pending = loadNextPendingSlice(parentOrderId);
            if (pending == null) throw new IllegalStateException("No pending TWAP slice for: " + parentOrderId);
            markSubmitted(pending.sliceId());
            slicesSubmittedCounter.increment();
            return new TwapSlice(pending.sliceId(), parentOrderId, pending.sliceNumber(),
                pending.baseQuantity(), pending.jitteredQuantity(),
                pending.scheduledAt(), Instant.now(), "SUBMITTED");
        });
    }

    private void persistSlices(List<TwapSlice> slices) throws Exception {
        String sql = "INSERT INTO algo_twap_slices(id, parent_order_id, slice_number, base_quantity, " +
                     "jittered_quantity, scheduled_at, status) VALUES(?,?,?,?,?,?,'PENDING')";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (TwapSlice s : slices) {
                ps.setObject(1, UUID.fromString(s.sliceId()));
                ps.setObject(2, UUID.fromString(s.parentOrderId()));
                ps.setInt(3, s.sliceNumber());
                ps.setLong(4, s.baseQuantity());
                ps.setLong(5, s.jitteredQuantity());
                ps.setTimestamp(6, java.sql.Timestamp.from(s.scheduledAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private TwapSlice loadNextPendingSlice(String parentOrderId) throws Exception {
        String sql = "SELECT id, slice_number, base_quantity, jittered_quantity, scheduled_at " +
                     "FROM algo_twap_slices WHERE parent_order_id = ? AND status = 'PENDING' " +
                     "AND scheduled_at <= NOW() ORDER BY slice_number ASC LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(parentOrderId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new TwapSlice(rs.getString("id"), parentOrderId,
                        rs.getInt("slice_number"), rs.getLong("base_quantity"),
                        rs.getLong("jittered_quantity"),
                        rs.getTimestamp("scheduled_at").toInstant(), null, "PENDING");
                }
            }
        }
        return null;
    }

    private void markSubmitted(String sliceId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE algo_twap_slices SET status = 'SUBMITTED', submitted_at = NOW() WHERE id = ?")) {
            ps.setObject(1, UUID.fromString(sliceId));
            ps.executeUpdate();
        }
    }
}

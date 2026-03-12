/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.aep.AepClient;
import com.ghatana.yappc.api.aep.AepException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background service that polls {@code yappc.event_outbox} and forwards PENDING
 * entries to AEP (Agentic Event Processor).
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Every {@value #POLL_INTERVAL_MS} ms, query outbox for rows where
 *       {@code status IN ('PENDING', 'FAILED')} and {@code next_retry_at &lt;= NOW()}.</li>
 *   <li>For each entry, call {@link AepClient#publishEvent(String, String)}.</li>
 *   <li>On success, set {@code status = 'DELIVERED'} and {@code processed_at = NOW()}.</li>
 *   <li>On failure, set {@code status = 'FAILED'}, increment {@code attempts}, store the
 *       error message, and set {@code next_retry_at} using an exponential back-off
 *       (up to {@value #MAX_RETRY_DELAY_SECONDS} seconds).</li>
 * </ol>
 *
 * <h2>Concurrency</h2>
 * <p>Runs on a single-threaded {@link ScheduledExecutorService} (virtual thread). All JDBC
 * calls are made directly on the scheduler thread — the ActiveJ event loop is never blocked.
 *
 * <h2>Back-off</h2>
 * <p>Retry back-off: {@code min(2^attempts × 5s, 300s)}.
 *
 * @doc.type class
 * @doc.purpose Outbox relay service — bridges YAPPC domain events to AEP EventCloud
 * @doc.layer product
 * @doc.pattern Outbox / Scheduler
 */
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private static final long POLL_INTERVAL_MS   = 500L;
    private static final int  BATCH_SIZE         = 50;
    private static final int  MAX_RETRY_DELAY_SECONDS = 300;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    // ── SQL ──────────────────────────────────────────────────────────────────

    /**
     * Select pending/retryable entries across ALL tenants. The relay process has
     * elevated privileges and deliberately does not filter by tenant.
     */
    private static final String SELECT_PENDING =
            "SELECT id, tenant_id, event_type, payload, attempts " +
            "  FROM yappc.event_outbox " +
            " WHERE status IN ('PENDING', 'FAILED') " +
            "   AND next_retry_at <= NOW() " +
            " ORDER BY next_retry_at ASC " +
            " LIMIT " + BATCH_SIZE;

    /** Mark an entry delivered. */
    private static final String UPDATE_DELIVERED =
            "UPDATE yappc.event_outbox " +
            "   SET status = 'DELIVERED', processed_at = ? " +
            " WHERE id = ?";

    /** Mark an entry failed with back-off. */
    private static final String UPDATE_FAILED =
            "UPDATE yappc.event_outbox " +
            "   SET status = 'FAILED', attempts = attempts + 1, last_error = ?, " +
            "       next_retry_at = ? " +
            " WHERE id = ?";

    // ── State ─────────────────────────────────────────────────────────────────

    private final DataSource dataSource;
    private final AepClient  aepClient;

    private ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Constructs the relay service.
     *
     * @param dataSource JDBC DataSource targeting the YAPPC database
     * @param aepClient  AEP client ({@code LIBRARY} or {@code SERVICE} mode)
     */
    public OutboxRelayService(DataSource dataSource, AepClient aepClient) {
        this.dataSource = dataSource;
        this.aepClient  = aepClient;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Starts the relay scheduler.
     *
     * <p>Idempotent — calling {@code start()} on an already-running service is a no-op.
     */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = Thread.ofVirtual().name("outbox-relay").unstarted(r);
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleWithFixedDelay(this::relayBatch,
                    POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
            log.info("OutboxRelayService started (interval={}ms, batchSize={})",
                    POLL_INTERVAL_MS, BATCH_SIZE);
        }
    }

    /**
     * Stops the relay scheduler gracefully, waiting up to 5 s for in-flight processing.
     */
    public synchronized void stop() {
        if (running.compareAndSet(true, false) && scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("OutboxRelayService stopped");
        }
    }

    // ── Core relay ────────────────────────────────────────────────────────────

    /**
     * Fetches up to {@value #BATCH_SIZE} pending outbox entries and relays each to AEP.
     * Called on the scheduler thread — never the ActiveJ event loop.
     */
    void relayBatch() {
        try {
            List<OutboxEntry> entries = fetchPending();
            if (entries.isEmpty()) return;
            log.debug("Relaying {} outbox entries", entries.size());
            for (OutboxEntry entry : entries) {
                relay(entry);
            }
        } catch (Exception e) {
            log.error("OutboxRelayService batch error — will retry next cycle", e);
        }
    }

    private void relay(OutboxEntry entry) {
        try {
            aepClient.publishEvent(entry.eventType(), entry.payload());
            markDelivered(entry.id());
            log.debug("Delivered outbox entry id={} type={} tenant={}",
                    entry.id(), entry.eventType(), entry.tenantId());
        } catch (AepException e) {
            Duration backOff = computeBackOff(entry.attempts());
            markFailed(entry.id(), e.getMessage(), Instant.now().plus(backOff));
            log.warn("Failed to relay outbox entry id={} type={} attempts={} nextRetry={}s: {}",
                    entry.id(), entry.eventType(), entry.attempts() + 1,
                    backOff.toSeconds(), e.getMessage());
        }
    }

    // ── JDBC helpers ──────────────────────────────────────────────────────────

    private List<OutboxEntry> fetchPending() throws SQLException {
        List<OutboxEntry> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_PENDING);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new OutboxEntry(
                        rs.getString("id"),
                        rs.getString("tenant_id"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getInt("attempts")));
            }
        }
        return result;
    }

    private void markDelivered(String id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_DELIVERED)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setObject(2, UUID.fromString(id));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to mark outbox entry {} as DELIVERED: {}", id, e.getMessage(), e);
        }
    }

    private void markFailed(String id, String error, Instant nextRetry) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_FAILED)) {
            ps.setString(1, error);
            ps.setTimestamp(2, Timestamp.from(nextRetry));
            ps.setObject(3, UUID.fromString(id));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to mark outbox entry {} as FAILED: {}", id, e.getMessage(), e);
        }
    }

    /** Exponential back-off: min(2^attempts × 5s, MAX_RETRY_DELAY_SECONDS). */
    private static Duration computeBackOff(int attempts) {
        long seconds = Math.min(
                (long) Math.pow(2, Math.min(attempts, 10)) * 5L,
                MAX_RETRY_DELAY_SECONDS);
        return Duration.ofSeconds(seconds);
    }

    // ── Value record ──────────────────────────────────────────────────────────

    /**
     * Lightweight projection of an outbox row — just what the relay needs.
     */
    record OutboxEntry(
            String id,
            String tenantId,
            String eventType,
            String payload,
            int attempts) {}
}

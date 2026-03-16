package com.ghatana.appplatform.sanctions.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Schedules and coordinates batch re-screening runs of the entire client population
 *                against the current sanctions lists. Enhanced from Sprint 6: now emits structured
 *                batch reports and tracks run history. Runs triggered by K-05 scheduler (nightly NST).
 * @doc.layer     Application
 * @doc.pattern   Batch job orchestration; delegation to ScreeningEngineService
 *
 * Story: D14-011
 */
public class BatchScreeningSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(BatchScreeningSchedulerService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    private final DataSource              dataSource;
    private final ScreeningEngineService  screeningEngine;
    private final Consumer<Object>        eventPublisher;
    private final AtomicInteger           inFlightRuns = new AtomicInteger(0);
    private final Counter                 runsStarted;
    private final Counter                 runsCompleted;
    private final Counter                 runsFailed;

    public BatchScreeningSchedulerService(DataSource dataSource,
                                           ScreeningEngineService screeningEngine,
                                           Consumer<Object> eventPublisher,
                                           MeterRegistry meterRegistry) {
        this.dataSource      = dataSource;
        this.screeningEngine = screeningEngine;
        this.eventPublisher  = eventPublisher;
        this.runsStarted     = meterRegistry.counter("sanctions.batch.runs_started");
        this.runsCompleted   = meterRegistry.counter("sanctions.batch.runs_completed");
        this.runsFailed      = meterRegistry.counter("sanctions.batch.runs_failed");
        Gauge.builder("sanctions.batch.in_flight", inFlightRuns, AtomicInteger::get)
             .register(meterRegistry);
    }

    /**
     * Entrypoint called by K-05 scheduler. Fetches all active client IDs in pages
     * and screens each one, recording results in the batch run table.
     *
     * @return batch run identifier
     */
    public String runNightlyBatch() {
        String runId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        createRunRecord(runId, startedAt);
        runsStarted.increment();
        inFlightRuns.incrementAndGet();

        log.info("BatchScreening started runId={} time={}", runId,
                ZonedDateTime.ofInstant(startedAt, NST));

        int screened = 0, hits = 0;
        try {
            List<String> clientIds = loadAllActiveClientIds();
            for (String clientId : clientIds) {
                try {
                    boolean hasHit = screeningEngine.screenClient(clientId);
                    screened++;
                    if (hasHit) hits++;
                } catch (Exception e) {
                    log.error("BatchScreening: error for clientId={}", clientId, e);
                }
            }
            completeRunRecord(runId, screened, hits, null);
            runsCompleted.increment();
            log.info("BatchScreening completed runId={} screened={} hits={}", runId, screened, hits);
            eventPublisher.accept(new BatchRunCompletedEvent(runId, screened, hits, startedAt, Instant.now()));
        } catch (Exception e) {
            log.error("BatchScreening: run failed runId={}", runId, e);
            completeRunRecord(runId, screened, hits, e.getMessage());
            runsFailed.increment();
            eventPublisher.accept(new BatchRunFailedEvent(runId, e.getMessage(), Instant.now()));
        } finally {
            inFlightRuns.decrementAndGet();
        }
        return runId;
    }

    /**
     * Returns the last N batch run summaries ordered by most recent first.
     */
    public List<BatchRunSummary> getRecentRuns(int limit) {
        String sql = "SELECT run_id, started_at, completed_at, clients_screened, hits_found, "
                   + "error_message FROM sanctions_batch_runs ORDER BY started_at DESC LIMIT ?";
        List<BatchRunSummary> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.min(limit, 100));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp completed = rs.getTimestamp("completed_at");
                    result.add(new BatchRunSummary(rs.getString("run_id"),
                            rs.getTimestamp("started_at").toInstant(),
                            completed != null ? completed.toInstant() : null,
                            rs.getInt("clients_screened"), rs.getInt("hits_found"),
                            rs.getString("error_message")));
                }
            }
        } catch (SQLException e) {
            log.error("getRecentRuns DB error", e);
        }
        return result;
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private List<String> loadAllActiveClientIds() throws SQLException {
        String sql = "SELECT client_id FROM clients WHERE status='ACTIVE' ORDER BY client_id";
        List<String> ids = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString("client_id"));
        }
        return ids;
    }

    private void createRunRecord(String runId, Instant startedAt) {
        String sql = "INSERT INTO sanctions_batch_runs(run_id, started_at) VALUES(?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, runId);
            ps.setTimestamp(2, Timestamp.from(startedAt));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("createRunRecord DB error runId={}", runId, e);
        }
    }

    private void completeRunRecord(String runId, int screened, int hits, String error) {
        String sql = "UPDATE sanctions_batch_runs "
                   + "SET completed_at=?, clients_screened=?, hits_found=?, error_message=? "
                   + "WHERE run_id=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setInt(2, screened);
            ps.setInt(3, hits);
            ps.setString(4, error);
            ps.setString(5, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("completeRunRecord DB error runId={}", runId, e);
        }
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record BatchRunSummary(String runId, Instant startedAt, Instant completedAt,
                                   int clientsScreened, int hitsFound, String errorMessage) {
        public boolean succeeded() { return errorMessage == null; }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record BatchRunCompletedEvent(String runId, int clientsScreened, int hitsFound,
                                          Instant startedAt, Instant completedAt) {}
    public record BatchRunFailedEvent(String runId, String errorMessage, Instant failedAt) {}
}

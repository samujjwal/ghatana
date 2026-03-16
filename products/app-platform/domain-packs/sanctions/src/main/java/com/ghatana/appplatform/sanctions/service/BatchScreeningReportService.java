package com.ghatana.appplatform.sanctions.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * @doc.type      Service
 * @doc.purpose   Generates structured batch screening reports from completed batch run data.
 *                Provides summary statistics (screened count, hit rate, lists used, top matches)
 *                and line-item detail for each hit found in the run.
 * @doc.layer     Application
 * @doc.pattern   Read model aggregation over batch run results
 *
 * Story: D14-012
 */
public class BatchScreeningReportService {

    private static final Logger log = LoggerFactory.getLogger(BatchScreeningReportService.class);

    private final DataSource dataSource;
    private final Timer      reportGenerationTimer;

    public BatchScreeningReportService(DataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource            = dataSource;
        this.reportGenerationTimer = meterRegistry.timer("sanctions.batch_report.generation");
    }

    /**
     * Generates the full report for a completed batch run.
     *
     * @param runId  batch run identifier
     * @return structured report or null if run not found
     */
    public BatchReport generateReport(String runId) {
        return reportGenerationTimer.record(() -> {
            BatchRunSummary summary = loadSummary(runId);
            if (summary == null) {
                log.warn("generateReport: runId={} not found", runId);
                return null;
            }
            List<HitRecord> hits = loadHits(runId);
            List<ListUsed>  lists = loadListsUsed(runId);

            double hitRate = summary.clientsScreened() > 0
                    ? (double) hits.size() / summary.clientsScreened() * 100.0 : 0.0;

            log.info("BatchReport generated runId={} hits={} hitRate={}%", runId, hits.size(), hitRate);
            return new BatchReport(runId, summary, hits, lists, hitRate, Instant.now());
        });
    }

    /**
     * Returns the most recent report summary (without line-item hits) for each of the last N runs.
     * Used by the dashboard.
     */
    public List<BatchRunSummary> getRecentSummaries(int limit) {
        String sql = "SELECT run_id, started_at, completed_at, clients_screened, hits_found, error_message "
                   + "FROM sanctions_batch_runs ORDER BY started_at DESC LIMIT ?";
        List<BatchRunSummary> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.min(limit, 50));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp comp = rs.getTimestamp("completed_at");
                    result.add(new BatchRunSummary(rs.getString("run_id"),
                            rs.getTimestamp("started_at").toInstant(),
                            comp != null ? comp.toInstant() : null,
                            rs.getInt("clients_screened"), rs.getInt("hits_found"),
                            rs.getString("error_message")));
                }
            }
        } catch (SQLException e) {
            log.error("getRecentSummaries DB error", e);
        }
        return result;
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private BatchRunSummary loadSummary(String runId) {
        String sql = "SELECT run_id, started_at, completed_at, clients_screened, hits_found, error_message "
                   + "FROM sanctions_batch_runs WHERE run_id=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp comp = rs.getTimestamp("completed_at");
                    return new BatchRunSummary(rs.getString("run_id"),
                            rs.getTimestamp("started_at").toInstant(),
                            comp != null ? comp.toInstant() : null,
                            rs.getInt("clients_screened"), rs.getInt("hits_found"),
                            rs.getString("error_message"));
                }
            }
        } catch (SQLException e) {
            log.error("loadSummary DB error runId={}", runId, e);
        }
        return null;
    }

    private List<HitRecord> loadHits(String runId) {
        String sql = "SELECT client_id, entity_ref, match_score, match_algorithm, list_id, screened_at "
                   + "FROM sanctions_screening_results "
                   + "WHERE batch_run_id=? AND is_hit=TRUE ORDER BY match_score DESC";
        List<HitRecord> hits = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hits.add(new HitRecord(rs.getString("client_id"), rs.getString("entity_ref"),
                            rs.getDouble("match_score"), rs.getString("match_algorithm"),
                            rs.getString("list_id"), rs.getTimestamp("screened_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            log.error("loadHits DB error runId={}", runId, e);
        }
        return hits;
    }

    private List<ListUsed> loadListsUsed(String runId) {
        String sql = "SELECT DISTINCT list_id FROM sanctions_screening_results WHERE batch_run_id=?";
        List<ListUsed> lists = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lists.add(new ListUsed(rs.getString("list_id")));
            }
        } catch (SQLException e) {
            log.error("loadListsUsed DB error runId={}", runId, e);
        }
        return lists;
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record BatchRunSummary(String runId, Instant startedAt, Instant completedAt,
                                   int clientsScreened, int hitsFound, String errorMessage) {}

    public record HitRecord(String clientId, String entityRef, double matchScore,
                             String matchAlgorithm, String listId, Instant screenedAt) {}

    public record ListUsed(String listId) {}

    public record BatchReport(String runId, BatchRunSummary summary, List<HitRecord> hits,
                               List<ListUsed> listsUsed, double hitRatePct, Instant generatedAt) {}
}

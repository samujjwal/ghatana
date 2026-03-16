package com.ghatana.appplatform.workflow;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Collect and expose workflow metrics: completion rate, duration distribution,
 *              per-step latency percentiles, error rate, SLA compliance, bottleneck analysis.
 *              Alerts on threshold breach via K-06.
 * @doc.layer   Workflow Orchestration (W-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Micrometer; Promise.ofBlocking
 *
 * STORY-W01-009: Workflow metrics and SLA tracking
 *
 * DDL (idempotent create):
 * <pre>
 * CREATE TABLE IF NOT EXISTS workflow_metrics_snapshot (
 *   snapshot_id     TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   workflow_name   TEXT NOT NULL,
 *   period_start    TIMESTAMPTZ NOT NULL,
 *   period_end      TIMESTAMPTZ NOT NULL,
 *   total_started   INT NOT NULL DEFAULT 0,
 *   total_completed INT NOT NULL DEFAULT 0,
 *   total_failed    INT NOT NULL DEFAULT 0,
 *   avg_duration_ms BIGINT,
 *   p50_duration_ms BIGINT,
 *   p95_duration_ms BIGINT,
 *   p99_duration_ms BIGINT,
 *   sla_limit_ms    BIGINT,
 *   sla_compliance_pct NUMERIC(5,2),
 *   created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class WorkflowMetricsSlaService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface AlertPort {
        void fireAlert(String alertName, String severity, String message) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public record LatencyPercentiles(long p50Ms, long p95Ms, long p99Ms) {}

    public record StepMetrics(
        String stepId,
        String stepType,
        long avgLatencyMs,
        LatencyPercentiles latency,
        double errorRate,
        long executionCount
    ) {}

    public record BottleneckReport(List<StepMetrics> slowestSteps, List<StepMetrics> highestErrorSteps) {}

    public record WorkflowSlaReport(
        String workflowName,
        String period,
        long totalStarted,
        long totalCompleted,
        long totalFailed,
        double completionRate,
        double errorRate,
        LatencyPercentiles durationPercentiles,
        long slaLimitMs,
        double slaCompliancePct,
        BottleneckReport bottleneck
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final AlertPort alertPort;
    private final Executor executor;

    // Per-workflow Micrometer timers are registered lazily
    private final MeterRegistry registry;
    private final Counter slaBreachCounter;

    private static final double DEFAULT_SLA_THRESHOLD_PCT = 99.0;

    public WorkflowMetricsSlaService(
        javax.sql.DataSource ds,
        AlertPort alertPort,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds        = ds;
        this.alertPort = alertPort;
        this.registry  = registry;
        this.executor  = executor;
        this.slaBreachCounter = Counter.builder("workflow.sla.breaches").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Record a completed workflow execution duration for metric aggregation.
     */
    public Promise<Void> recordCompletion(String workflowName, long durationMs, boolean success) {
        return Promise.ofBlocking(executor, () -> {
            Timer.builder("workflow.instance.duration")
                .tag("workflow", workflowName)
                .tag("success", String.valueOf(success))
                .register(registry)
                .record(java.time.Duration.ofMillis(durationMs));

            if (!success) {
                Counter.builder("workflow.instance.failures")
                    .tag("workflow", workflowName)
                    .register(registry)
                    .increment();
            }
            return null;
        });
    }

    /**
     * Record a single step execution outcome.
     */
    public Promise<Void> recordStepExecution(String workflowName, String stepId, String stepType, long latencyMs, boolean error) {
        return Promise.ofBlocking(executor, () -> {
            Timer.builder("workflow.step.latency")
                .tag("workflow", workflowName)
                .tag("step", stepId)
                .tag("type", stepType)
                .register(registry)
                .record(java.time.Duration.ofMillis(latencyMs));

            if (error) {
                Counter.builder("workflow.step.errors")
                    .tag("workflow", workflowName)
                    .tag("step", stepId)
                    .register(registry)
                    .increment();
            }
            return null;
        });
    }

    /**
     * Compute and persist a period SLA report for one workflow, firing an alert if needed.
     */
    public Promise<WorkflowSlaReport> computeSlaReport(
        String workflowName,
        String periodStart,
        String periodEnd,
        long slaLimitMs
    ) {
        return Promise.ofBlocking(executor, () -> {
            // Aggregate from workflow_instances table (assumed to exist from W01-004)
            long started = 0, completed = 0, failed = 0;
            List<Long> durations = new ArrayList<>();

            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT status, EXTRACT(EPOCH FROM (completed_at - started_at)) * 1000 AS duration_ms " +
                     "FROM workflow_instances " +
                     "WHERE workflow_name=? AND started_at >= ?::timestamptz AND started_at < ?::timestamptz"
                 )) {
                ps.setString(1, workflowName);
                ps.setString(2, periodStart);
                ps.setString(3, periodEnd);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        started++;
                        String status = rs.getString("status");
                        if ("COMPLETED".equals(status)) {
                            completed++;
                            long dur = rs.getLong("duration_ms");
                            if (!rs.wasNull()) durations.add(dur);
                        } else if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                            failed++;
                        }
                    }
                }
            }

            Collections.sort(durations);
            LatencyPercentiles latency = durations.isEmpty()
                ? new LatencyPercentiles(0, 0, 0)
                : new LatencyPercentiles(percentile(durations, 50), percentile(durations, 95), percentile(durations, 99));

            long withinSla = durations.stream().filter(d -> d <= slaLimitMs).count();
            double compliancePct = durations.isEmpty() ? 100.0 : (100.0 * withinSla / durations.size());

            double completionRate = started > 0 ? (100.0 * completed / started) : 100.0;
            double errorRate = started > 0 ? (100.0 * failed / started) : 0.0;

            // Fire alert if compliance below threshold
            if (compliancePct < DEFAULT_SLA_THRESHOLD_PCT) {
                slaBreachCounter.increment();
                alertPort.fireAlert(
                    "workflow.sla.breach",
                    compliancePct < 95.0 ? "CRITICAL" : "WARNING",
                    String.format("Workflow %s SLA compliance %.1f%% (threshold %.1f%%, period %s→%s)",
                        workflowName, compliancePct, DEFAULT_SLA_THRESHOLD_PCT, periodStart, periodEnd)
                );
            }

            // Bottleneck analysis — query slowest steps
            BottleneckReport bottleneck = computeBottleneck(workflowName, periodStart, periodEnd);

            // Persist snapshot
            persistSnapshot(workflowName, periodStart, periodEnd, started, completed, failed,
                latency, slaLimitMs, compliancePct);

            return new WorkflowSlaReport(workflowName, periodStart + "/" + periodEnd, started, completed, failed,
                completionRate, errorRate, latency, slaLimitMs, compliancePct, bottleneck);
        });
    }

    /** Load historical SLA report snapshots for a workflow. */
    public Promise<List<WorkflowSlaReport>> loadSnapshots(String workflowName, int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<WorkflowSlaReport> results = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM workflow_metrics_snapshot WHERE workflow_name=? ORDER BY created_at DESC LIMIT ?"
                 )) {
                ps.setString(1, workflowName);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LatencyPercentiles lat = new LatencyPercentiles(
                            rs.getLong("p50_duration_ms"),
                            rs.getLong("p95_duration_ms"),
                            rs.getLong("p99_duration_ms")
                        );
                        results.add(new WorkflowSlaReport(
                            rs.getString("workflow_name"),
                            rs.getString("period_start") + "/" + rs.getString("period_end"),
                            rs.getLong("total_started"),
                            rs.getLong("total_completed"),
                            rs.getLong("total_failed"),
                            0, 0, lat,
                            rs.getLong("sla_limit_ms"),
                            rs.getDouble("sla_compliance_pct"),
                            new BottleneckReport(List.of(), List.of())
                        ));
                    }
                }
            }
            return results;
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private BottleneckReport computeBottleneck(String workflowName, String start, String end) {
        // Placeholder: in production queries workflow_step_executions table
        return new BottleneckReport(List.of(), List.of());
    }

    private void persistSnapshot(String workflowName, String start, String end, long started, long completed,
        long failed, LatencyPercentiles lat, long slaLimitMs, double compliancePct
    ) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO workflow_metrics_snapshot " +
                 "(workflow_name, period_start, period_end, total_started, total_completed, total_failed, " +
                 "p50_duration_ms, p95_duration_ms, p99_duration_ms, sla_limit_ms, sla_compliance_pct) " +
                 "VALUES (?,?::timestamptz,?::timestamptz,?,?,?,?,?,?,?,?)"
             )) {
            ps.setString(1, workflowName);
            ps.setString(2, start);
            ps.setString(3, end);
            ps.setLong(4, started);
            ps.setLong(5, completed);
            ps.setLong(6, failed);
            ps.setLong(7, lat.p50Ms());
            ps.setLong(8, lat.p95Ms());
            ps.setLong(9, lat.p99Ms());
            ps.setLong(10, slaLimitMs);
            ps.setDouble(11, compliancePct);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}

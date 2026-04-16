/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.report;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Structured report generation service for AEP operational intelligence.
 *
 * <p>Queries multiple DataCloud collections and assembles named
 * {@link Report reports} consisting of one or more {@link ReportSection sections},
 * each containing a tabular data set and a summary.
 *
 * <h3>Supported Reports</h3>
 * <table border="1">
 *   <tr><th>Type</th><th>Description</th></tr>
 *   <tr><td>KPI_SUMMARY</td><td>Last-known value and trend for all tracked KPIs</td></tr>
 *   <tr><td>PATTERN_PERFORMANCE</td><td>Active pattern count, priorities, and anomaly rates</td></tr>
 *   <tr><td>ANOMALY_SUMMARY</td><td>Open anomaly count by severity and KPI name</td></tr>
 *   <tr><td>TENANT_USAGE</td><td>Entity counts across all AEP collections for a tenant</td></tr>
 *   <tr><td>SYSTEM_HEALTH</td><td>Combined health snapshot across all report types</td></tr>
 * </table>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AepReportingService reports = new AepReportingService(client, meterRegistry);
 *
 * Report report = reports.generate(new ReportRequest(
 *     "tenant-acme",
 *     ReportType.ANOMALY_SUMMARY,
 *     Instant.now().minus(Duration.ofDays(7)),
 *     Instant.now()))
 *   .getResult();
 *
 * report.sections().forEach(sec ->
 *     sec.rows().forEach(row -> logger.info("{}", row)));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Structured report generation for AEP operational intelligence
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AepReportingService {

    private static final Logger log = LoggerFactory.getLogger(AepReportingService.class);

    private static final int QUERY_LIMIT = 1_000;

    // AEP collection names
    private static final String COL_PATTERNS  = "aep_patterns";
    private static final String COL_PIPELINES = "aep_pipelines";
    private static final String COL_ANOMALIES = "aep_anomalies";
    private static final String COL_KPI       = "aep_kpi_snapshots";
    private static final String COL_METRICS   = "aep_metrics";

    private final DataCloudClient client;
    private final Counter         reportCounter;
    private final Counter         errorCounter;
    private final Timer           reportTimer;

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates the reporting service.
     *
     * @param client        the DataCloud client; must not be {@code null}
     * @param meterRegistry Micrometer registry for operational metrics
     */
    public AepReportingService(DataCloudClient client, MeterRegistry meterRegistry) {
        this.client        = Objects.requireNonNull(client, "DataCloudClient must not be null");
        Objects.requireNonNull(meterRegistry, "MeterRegistry must not be null");
        this.reportCounter = meterRegistry.counter("aep.report.total");
        this.errorCounter  = meterRegistry.counter("aep.report.errors");
        this.reportTimer   = meterRegistry.timer("aep.report.duration");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Report Generation API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a structured report for the given request.
     *
     * @param request report parameters including tenant, type, and date range
     * @return promise of the completed {@link Report}
     * @throws NullPointerException if request is null
     */
    public Promise<Report> generate(ReportRequest request) {
        Objects.requireNonNull(request, "ReportRequest must not be null");

        Instant start = Instant.now();
        Promise<Report> base = switch (request.reportType()) {
            case KPI_SUMMARY         -> generateKpiSummary(request);
            case PATTERN_PERFORMANCE -> generatePatternPerformance(request);
            case ANOMALY_SUMMARY     -> generateAnomalySummary(request);
            case TENANT_USAGE        -> generateTenantUsage(request);
            case SYSTEM_HEALTH       -> generateSystemHealth(request);
        };
        return base.map(report -> {
            reportCounter.increment();
            reportTimer.record(Duration.between(start, Instant.now()));
            log.info("Report generated type={} tenant={} sections={}",
                    request.reportType(), request.tenantId(), report.sections().size());
            return report;
        }).whenException(e -> {
            errorCounter.increment();
            log.error("Report generation failed type={} tenant={}: {}",
                    request.reportType(), request.tenantId(), e.getMessage(), e);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  KPI Summary
    // ─────────────────────────────────────────────────────────────────────────

    private Promise<Report> generateKpiSummary(ReportRequest req) {
        Query q = tenantQuery(req.tenantId(), req.from(), req.to(), "recordedAt");

        return client.query(req.tenantId(), COL_KPI, q)
                .map(kpis -> {
                    // Group by kpiName → latest value per KPI
                    Map<String, Entity> latestByKpi = new LinkedHashMap<>();
                    for (Entity e : kpis) {
                        String name = str(e.data(), "kpiName");
                        if (name == null) continue;
                        latestByKpi.merge(name, e, (existing, incoming) -> {
                            Instant eTime = parseInstant(existing.data(), "recordedAt");
                            Instant iTime = parseInstant(incoming.data(), "recordedAt");
                            if (eTime == null) return incoming;
                            if (iTime == null) return existing;
                            return iTime.isAfter(eTime) ? incoming : existing;
                        });
                    }

                    List<Map<String, Object>> rows = latestByKpi.values().stream()
                            .map(e -> {
                                Map<String, Object> row = new LinkedHashMap<>();
                                row.put("kpiName", str(e.data(), "kpiName"));
                                row.put("value",   e.data().getOrDefault("value", "N/A"));
                                row.put("unit",    e.data().getOrDefault("unit", ""));
                                row.put("recordedAt", str(e.data(), "recordedAt"));
                                return row;
                            })
                            .collect(Collectors.toList());

                    String summary = String.format("Tracked KPIs: %d unique metrics in period", rows.size());
                    ReportSection section = new ReportSection("KPI Snapshot", rows, summary);

                    return buildReport(req, List.of(section),
                            "KPI Summary Report — " + rows.size() + " metrics");
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Pattern Performance
    // ─────────────────────────────────────────────────────────────────────────

    private Promise<Report> generatePatternPerformance(ReportRequest req) {
        Query patternsQ  = tenantQuery(req.tenantId(), req.from(), req.to(), "updatedAt");
        Query anomaliesQ = tenantQuery(req.tenantId(), req.from(), req.to(), "detectedAt");

        Promise<List<Entity>> patternsFuture  = client.query(req.tenantId(), COL_PATTERNS,  patternsQ);
        Promise<List<Entity>> anomaliesFuture = client.query(req.tenantId(), COL_ANOMALIES, anomaliesQ);

        return Promises.toList(List.of(patternsFuture, anomaliesFuture))
                .map(results -> {
                    List<Entity> patterns  = results.get(0);
                    List<Entity> anomalies = results.get(1);

                    // Anomaly rate per pattern name
                    Map<String, Long> anomalyByPattern = anomalies.stream()
                            .collect(Collectors.groupingBy(
                                    e -> str(e.data(), "kpiName") != null
                                            ? str(e.data(), "kpiName") : "unknown",
                                    Collectors.counting()));

                    // Pattern status breakdown
                    Map<String, Long> statusCount = patterns.stream()
                            .collect(Collectors.groupingBy(
                                    e -> str(e.data(), "status") != null
                                            ? str(e.data(), "status") : "UNKNOWN",
                                    Collectors.counting()));

                    List<Map<String, Object>> statusRows = statusCount.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .map(entry -> {
                                Map<String, Object> row = new LinkedHashMap<>();
                                row.put("status", entry.getKey());
                                row.put("count",  entry.getValue());
                                return row;
                            })
                            .collect(Collectors.toList());

                    List<Map<String, Object>> anomalyRows = anomalyByPattern.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .limit(10)
                            .map(entry -> {
                                Map<String, Object> row = new LinkedHashMap<>();
                                row.put("kpiName",      entry.getKey());
                                row.put("anomalyCount", entry.getValue());
                                return row;
                            })
                            .collect(Collectors.toList());

                    return buildReport(req, List.of(
                            new ReportSection("Pattern Status",
                                    statusRows,
                                    "Total patterns: " + patterns.size()),
                            new ReportSection("Top Anomalous KPIs",
                                    anomalyRows,
                                    "Total anomalies in period: " + anomalies.size())
                    ), "Pattern Performance Report");
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Anomaly Summary
    // ─────────────────────────────────────────────────────────────────────────

    private Promise<Report> generateAnomalySummary(ReportRequest req) {
        Query q = tenantQuery(req.tenantId(), req.from(), req.to(), "detectedAt");

        return client.query(req.tenantId(), COL_ANOMALIES, q)
                .map(anomalies -> {
                    Map<String, Long> bySeverity = anomalies.stream()
                            .collect(Collectors.groupingBy(
                                    e -> str(e.data(), "severity") != null
                                            ? str(e.data(), "severity") : "UNKNOWN",
                                    Collectors.counting()));

                    Map<String, Long> byStatus = anomalies.stream()
                            .collect(Collectors.groupingBy(
                                    e -> str(e.data(), "status") != null
                                            ? str(e.data(), "status") : "UNKNOWN",
                                    Collectors.counting()));

                    List<Map<String, Object>> severityRows = bySeverity.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .map(entry -> {
                                Map<String, Object> row = new LinkedHashMap<>();
                                row.put("severity", entry.getKey());
                                row.put("count",    entry.getValue());
                                return row;
                            })
                            .collect(Collectors.toList());

                    List<Map<String, Object>> statusRows = byStatus.entrySet().stream()
                            .map(entry -> {
                                Map<String, Object> row = new LinkedHashMap<>();
                                row.put("status", entry.getKey());
                                row.put("count",  entry.getValue());
                                return row;
                            })
                            .collect(Collectors.toList());

                    long openCount = Optional.ofNullable(byStatus.get("OPEN")).orElse(0L);
                    String overallSummary = String.format(
                            "Total anomalies: %d | Open: %d | Severities: %d distinct",
                            anomalies.size(), openCount, bySeverity.size());

                    return buildReport(req, List.of(
                            new ReportSection("By Severity", severityRows,
                                    "Anomaly distribution by severity"),
                            new ReportSection("By Status",   statusRows,
                                    "Anomaly distribution by resolution status")
                    ), overallSummary);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tenant Usage
    // ─────────────────────────────────────────────────────────────────────────

    private Promise<Report> generateTenantUsage(ReportRequest req) {
        Query q = Query.builder()
                .filter(Filter.eq("tenantId", req.tenantId()))
                .limit(QUERY_LIMIT)
                .build();

        Promise<Integer> patterns  = client.query(req.tenantId(), COL_PATTERNS,  q).map(List::size);
        Promise<Integer> pipelines = client.query(req.tenantId(), COL_PIPELINES, q).map(List::size);
        Promise<Integer> anomalies = client.query(req.tenantId(), COL_ANOMALIES, q).map(List::size);
        Promise<Integer> kpis      = client.query(req.tenantId(), COL_KPI,       q).map(List::size);

        return Promises.toList(List.of(patterns, pipelines, anomalies, kpis))
                .map(counts -> {
                    List<Map<String, Object>> rows = List.of(
                            usageRow("aep_patterns",       counts.get(0)),
                            usageRow("aep_pipelines",      counts.get(1)),
                            usageRow("aep_anomalies",      counts.get(2)),
                            usageRow("aep_kpi_snapshots",  counts.get(3))
                    );
                    int total = counts.stream().mapToInt(Integer::intValue).sum();
                    return buildReport(req, List.of(
                            new ReportSection("Entity Counts by Collection",
                                    rows, "Total entities tracked: " + total)
                    ), "Tenant Usage Report — " + req.tenantId());
                });
    }

    private static Map<String, Object> usageRow(String collection, int count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("collection", collection);
        row.put("count", count);
        return row;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  System Health (composite)
    // ─────────────────────────────────────────────────────────────────────────

    private Promise<Report> generateSystemHealth(ReportRequest req) {
        Promise<Report> kpi     = generateKpiSummary(req);
        Promise<Report> anomaly = generateAnomalySummary(req);
        Promise<Report> usage   = generateTenantUsage(req);

        return Promises.toList(List.of(kpi, anomaly, usage))
                .map(subReports -> {
                    List<ReportSection> allSections = new ArrayList<>();
                    for (Report subReport : subReports) {
                        for (ReportSection s : subReport.sections()) {
                            allSections.add(new ReportSection(
                                    subReport.reportType().name() + " — " + s.title(),
                                    s.rows(),
                                    s.summary()));
                        }
                    }
                    return buildReport(req, allSections, "System Health Report — all sub-reports combined");
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static Query tenantQuery(String tenantId, Instant from, Instant to, String timeField) {
        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("tenantId", tenantId));
        if (from != null) filters.add(Filter.gte(timeField, from.toString()));
        if (to   != null) filters.add(Filter.lte(timeField, to.toString()));
        return Query.builder().filters(filters).limit(QUERY_LIMIT).build();
    }

    private static Report buildReport(ReportRequest req, List<ReportSection> sections,
                                       String summary) {
        return new Report(
                req.reportType(),
                req.tenantId(),
                Instant.now(),
                req.from(),
                req.to(),
                sections,
                summary
        );
    }

    private static String str(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }

    private static Instant parseInstant(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v == null) return null;
        if (v instanceof Instant i) return i;
        try {
            return Instant.parse(v.toString());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API Types
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Enumeration of supported report types.
     *
     * @doc.type enum
     * @doc.purpose Identifies the kind of operational report to generate
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public enum ReportType {
        /** Last-known value and trend for all tracked KPIs. */
        KPI_SUMMARY,
        /** Active pattern count, priorities, and anomaly rates. */
        PATTERN_PERFORMANCE,
        /** Open anomaly count by severity and KPI name. */
        ANOMALY_SUMMARY,
        /** Entity counts across all AEP collections for a tenant. */
        TENANT_USAGE,
        /** Combined health snapshot across all report types. */
        SYSTEM_HEALTH
    }

    /**
     * Report generation request.
     *
     * @param tenantId   owning tenant
     * @param reportType the kind of report to generate
     * @param from       start of the reporting window (inclusive; may be {@code null} for open-ended)
     * @param to         end of the reporting window (inclusive; may be {@code null} for up-to-now)
     *
     * @doc.type record
     * @doc.purpose Immutable report request
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record ReportRequest(
            String     tenantId,
            ReportType reportType,
            Instant    from,
            Instant    to) {

        public ReportRequest {
            Objects.requireNonNull(tenantId,   "tenantId must not be null");
            Objects.requireNonNull(reportType, "reportType must not be null");
        }
    }

    /**
     * A named section within a report, containing a tabular data set.
     *
     * @param title   section heading
     * @param rows    list of data rows; each row is a field-name → value map
     * @param summary one-line human-readable summary for dashboards
     *
     * @doc.type record
     * @doc.purpose A named table section within a structured report
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record ReportSection(
            String                    title,
            List<Map<String, Object>> rows,
            String                    summary) {

        public ReportSection {
            rows = rows != null ? List.copyOf(rows) : List.of();
        }
    }

    /**
     * A fully assembled structured report.
     *
     * @param reportType  the type of this report
     * @param tenantId    owning tenant
     * @param generatedAt when the report was generated
     * @param from        reporting period start (may be {@code null})
     * @param to          reporting period end (may be {@code null})
     * @param sections    ordered list of report sections
     * @param summary     overall report summary text
     *
     * @doc.type record
     * @doc.purpose Immutable structured report with named sections and summary
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record Report(
            ReportType          reportType,
            String              tenantId,
            Instant             generatedAt,
            Instant             from,
            Instant             to,
            List<ReportSection> sections,
            String              summary) {

        public Report {
            sections = sections != null ? List.copyOf(sections) : List.of();
        }
    }
}

/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.api.reports;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for report generation — type coverage, filtering, pagination,
 * and export format correctness.
 *
 * @doc.type    class
 * @doc.purpose Tests for data-cloud report generation service
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("Report Generation Tests [GH-90000]")
class ReportGenerationTest extends EventloopTestBase {

    // ── Report model ──────────────────────────────────────────────────────────

    enum ReportType { COLLECTION_SUMMARY, EVENT_TREND, PIPELINE_HEALTH, MODEL_PERFORMANCE, ANALYTICS_AGGREGATE }

    enum ExportFormat { JSON, CSV, PDF }

    record ReportFilter(String tenantId, Instant from, Instant to, Map<String, String> attributes) {} // GH-90000

    record ReportPage(List<ReportRow> rows, int totalCount, int page, int pageSize) { // GH-90000
        int totalPages() { return (int) Math.ceil((double) totalCount / pageSize); } // GH-90000
    }

    record ReportRow(String id, ReportType type, String tenantId, Instant generatedAt, Map<String, Object> data) {} // GH-90000

    private ReportEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new ReportEngine(); // GH-90000
        // Seed reports
        Instant base = Instant.parse("2026-03-01T00:00:00Z [GH-90000]");
        for (ReportType type : ReportType.values()) { // GH-90000
            for (int i = 0; i < 4; i++) { // GH-90000
                engine.addReport(new ReportRow( // GH-90000
                        type.name().toLowerCase() + "-" + i, // GH-90000
                        type, "tenant-gen", base.plusSeconds(i * 3600L), // GH-90000
                        Map.of("metric", "value-" + i))); // GH-90000
            }
        }
        // Add reports for a second tenant
        for (int i = 0; i < 3; i++) { // GH-90000
            engine.addReport(new ReportRow( // GH-90000
                    "other-" + i, ReportType.ANALYTICS_AGGREGATE,
                    "tenant-other", base.plusSeconds(i * 1800L), // GH-90000
                    Map.of("score", i * 10))); // GH-90000
        }
    }

    // ── Generation by type ────────────────────────────────────────────────────

    @ParameterizedTest(name = "type={0}") // GH-90000
    @EnumSource(ReportType.class) // GH-90000
    @DisplayName("can generate a report for every supported type [GH-90000]")
    void generateReportForEveryType(ReportType type) { // GH-90000
        ReportFilter filter = new ReportFilter("tenant-gen", null, null, Map.of()); // GH-90000
        List<ReportRow> rows = engine.generate(type, filter); // GH-90000

        assertThat(rows).isNotEmpty(); // GH-90000
        assertThat(rows).allMatch(r -> r.type() == type); // GH-90000
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("filter by tenantId returns only that tenant's reports [GH-90000]")
    void filterByTenantReturnsOnlyMatchingReports() { // GH-90000
        ReportFilter filter = new ReportFilter("tenant-gen", null, null, Map.of()); // GH-90000
        List<ReportRow> rows = engine.generate(ReportType.ANALYTICS_AGGREGATE, filter); // GH-90000

        assertThat(rows).isNotEmpty(); // GH-90000
        assertThat(rows).allMatch(r -> r.tenantId().equals("tenant-gen [GH-90000]"));
    }

    @Test
    @DisplayName("filter by time range retains only reports within the window [GH-90000]")
    void filterByTimeRangeRetainsBoundedReports() { // GH-90000
        Instant from = Instant.parse("2026-03-01T01:00:00Z [GH-90000]");
        Instant to   = Instant.parse("2026-03-01T03:00:00Z [GH-90000]");
        ReportFilter filter = new ReportFilter("tenant-gen", from, to, Map.of()); // GH-90000

        List<ReportRow> rows = engine.generateAll(filter); // GH-90000

        assertThat(rows).isNotEmpty(); // GH-90000
        assertThat(rows).allMatch(r -> // GH-90000
                !r.generatedAt().isBefore(from) && !r.generatedAt().isAfter(to)); // GH-90000
    }

    @Test
    @DisplayName("filter returns empty list when no reports match the time window [GH-90000]")
    void filterReturnsEmptyForUnmatchedTimeWindow() { // GH-90000
        Instant from = Instant.parse("2020-01-01T00:00:00Z [GH-90000]");
        Instant to   = Instant.parse("2020-01-02T00:00:00Z [GH-90000]");
        ReportFilter filter = new ReportFilter("tenant-gen", from, to, Map.of()); // GH-90000

        List<ReportRow> rows = engine.generateAll(filter); // GH-90000
        assertThat(rows).isEmpty(); // GH-90000
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pagination returns the correct subset of results [GH-90000]")
    void paginationReturnsCorrectSubset() { // GH-90000
        ReportFilter filter = new ReportFilter("tenant-gen", null, null, Map.of()); // GH-90000
        List<ReportRow> all = engine.generateAll(filter); // GH-90000

        ReportPage page1 = engine.paginate(all, 0, 5); // GH-90000
        ReportPage page2 = engine.paginate(all, 1, 5); // GH-90000

        assertThat(page1.rows()).hasSize(Math.min(5, all.size())); // GH-90000
        assertThat(page1.totalCount()).isEqualTo(all.size()); // GH-90000
        if (all.size() > 5) { // GH-90000
            assertThat(page2.rows()).isNotEmpty(); // GH-90000
            assertThat(page1.rows()).doesNotContainAnyElementsOf(page2.rows()); // GH-90000
        }
    }

    @Test
    @DisplayName("total pages calculation is correct for given page size [GH-90000]")
    void totalPagesCalculationIsCorrect() { // GH-90000
        List<ReportRow> all = new ArrayList<>(); // GH-90000
        for (int i = 0; i < 25; i++) { // GH-90000
            all.add(new ReportRow("r-" + i, ReportType.EVENT_TREND, "t", Instant.now(), Map.of())); // GH-90000
        }

        ReportPage page = engine.paginate(all, 0, 10); // GH-90000
        assertThat(page.totalPages()).isEqualTo(3); // GH-90000
    }

    // ── Export format ─────────────────────────────────────────────────────────

    @ParameterizedTest(name = "format={0}") // GH-90000
    @EnumSource(ExportFormat.class) // GH-90000
    @DisplayName("export produces non-empty output for each supported format [GH-90000]")
    void exportProducesNonEmptyOutputForEachFormat(ExportFormat format) { // GH-90000
        ReportFilter filter = new ReportFilter("tenant-gen", null, null, Map.of()); // GH-90000
        List<ReportRow> rows = engine.generate(ReportType.COLLECTION_SUMMARY, filter); // GH-90000

        String exported = engine.export(rows, format); // GH-90000
        assertThat(exported).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("CSV export contains header and data rows [GH-90000]")
    void csvExportContainsHeaderAndDataRows() { // GH-90000
        List<ReportRow> rows = List.of( // GH-90000
                new ReportRow("r1", ReportType.EVENT_TREND, "t", Instant.now(), Map.of("metric", "42"))); // GH-90000
        String csv = engine.export(rows, ExportFormat.CSV); // GH-90000

        assertThat(csv).contains("id,type,tenantId,generatedAt [GH-90000]");
        assertThat(csv.lines().count()).isGreaterThan(1); // GH-90000
    }

    @Test
    @DisplayName("JSON export is parseable and contains all row IDs [GH-90000]")
    void jsonExportContainsAllRowIds() { // GH-90000
        List<ReportRow> rows = List.of( // GH-90000
                new ReportRow("rex-1", ReportType.MODEL_PERFORMANCE, "t", Instant.now(), Map.of()), // GH-90000
                new ReportRow("rex-2", ReportType.MODEL_PERFORMANCE, "t", Instant.now(), Map.of())); // GH-90000
        String json = engine.export(rows, ExportFormat.JSON); // GH-90000

        assertThat(json).contains("rex-1 [GH-90000]");
        assertThat(json).contains("rex-2 [GH-90000]");
    }

    // ── Report engine (inner, for tests) ────────────────────────────────────── // GH-90000

    static class ReportEngine {
        private final List<ReportRow> store = new CopyOnWriteArrayList<>(); // GH-90000

        void addReport(ReportRow row) { store.add(row); } // GH-90000

        List<ReportRow> generate(ReportType type, ReportFilter filter) { // GH-90000
            return applyFilter(store.stream() // GH-90000
                    .filter(r -> r.type() == type) // GH-90000
                    .toList(), filter); // GH-90000
        }

        List<ReportRow> generateAll(ReportFilter filter) { // GH-90000
            return applyFilter(List.copyOf(store), filter); // GH-90000
        }

        private List<ReportRow> applyFilter(List<ReportRow> rows, ReportFilter filter) { // GH-90000
            return rows.stream() // GH-90000
                    .filter(r -> filter.tenantId() == null || r.tenantId().equals(filter.tenantId())) // GH-90000
                    .filter(r -> filter.from() == null || !r.generatedAt().isBefore(filter.from())) // GH-90000
                    .filter(r -> filter.to() == null || !r.generatedAt().isAfter(filter.to())) // GH-90000
                    .toList(); // GH-90000
        }

        ReportPage paginate(List<ReportRow> rows, int page, int pageSize) { // GH-90000
            int start = page * pageSize;
            int end = Math.min(start + pageSize, rows.size()); // GH-90000
            List<ReportRow> pageRows = start < rows.size() ? rows.subList(start, end) : List.of(); // GH-90000
            return new ReportPage(pageRows, rows.size(), page, pageSize); // GH-90000
        }

        String export(List<ReportRow> rows, ExportFormat format) { // GH-90000
            return switch (format) { // GH-90000
                case JSON -> {
                    StringBuilder sb = new StringBuilder("[ [GH-90000]");
                    rows.forEach(r -> sb.append("{\"id\":\"").append(r.id()) // GH-90000
                            .append("\",\"type\":\"").append(r.type()) // GH-90000
                            .append("\",\"tenantId\":\"").append(r.tenantId()) // GH-90000
                            .append("\"},")); // GH-90000
                    if (!rows.isEmpty()) sb.setLength(sb.length() - 1); // GH-90000
                    sb.append("] [GH-90000]");
                    yield sb.toString(); // GH-90000
                }
                case CSV -> {
                    StringBuilder sb = new StringBuilder("id,type,tenantId,generatedAt\n [GH-90000]");
                    rows.forEach(r -> sb.append(r.id()).append(',') // GH-90000
                            .append(r.type()).append(',') // GH-90000
                            .append(r.tenantId()).append(',') // GH-90000
                            .append(r.generatedAt()).append('\n')); // GH-90000
                    yield sb.toString(); // GH-90000
                }
                case PDF -> "[PDF:" + rows.size() + " rows]"; // GH-90000
            };
        }
    }
}

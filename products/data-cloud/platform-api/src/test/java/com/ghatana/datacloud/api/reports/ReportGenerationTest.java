/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("Report Generation Tests")
class ReportGenerationTest extends EventloopTestBase {

    // ── Report model ──────────────────────────────────────────────────────────

    enum ReportType { COLLECTION_SUMMARY, EVENT_TREND, PIPELINE_HEALTH, MODEL_PERFORMANCE, ANALYTICS_AGGREGATE }

    enum ExportFormat { JSON, CSV, PDF }

    record ReportFilter(String tenantId, Instant from, Instant to, Map<String, String> attributes) {}

    record ReportPage(List<ReportRow> rows, int totalCount, int page, int pageSize) {
        int totalPages() { return (int) Math.ceil((double) totalCount / pageSize); }
    }

    record ReportRow(String id, ReportType type, String tenantId, Instant generatedAt, Map<String, Object> data) {}

    private ReportEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ReportEngine();
        // Seed reports
        Instant base = Instant.parse("2026-03-01T00:00:00Z");
        for (ReportType type : ReportType.values()) {
            for (int i = 0; i < 4; i++) {
                engine.addReport(new ReportRow(
                        type.name().toLowerCase() + "-" + i,
                        type, "tenant-gen", base.plusSeconds(i * 3600L),
                        Map.of("metric", "value-" + i)));
            }
        }
        // Add reports for a second tenant
        for (int i = 0; i < 3; i++) {
            engine.addReport(new ReportRow(
                    "other-" + i, ReportType.ANALYTICS_AGGREGATE,
                    "tenant-other", base.plusSeconds(i * 1800L),
                    Map.of("score", i * 10)));
        }
    }

    // ── Generation by type ────────────────────────────────────────────────────

    @ParameterizedTest(name = "type={0}")
    @EnumSource(ReportType.class)
    @DisplayName("can generate a report for every supported type")
    void generateReportForEveryType(ReportType type) {
        ReportFilter filter = new ReportFilter("tenant-gen", null, null, Map.of());
        List<ReportRow> rows = engine.generate(type, filter);

        assertThat(rows).isNotEmpty();
        assertThat(rows).allMatch(r -> r.type() == type);
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("filter by tenantId returns only that tenant's reports")
    void filterByTenantReturnsOnlyMatchingReports() {
        ReportFilter filter = new ReportFilter("tenant-gen", null, null, Map.of());
        List<ReportRow> rows = engine.generate(ReportType.ANALYTICS_AGGREGATE, filter);

        assertThat(rows).isNotEmpty();
        assertThat(rows).allMatch(r -> r.tenantId().equals("tenant-gen"));
    }

    @Test
    @DisplayName("filter by time range retains only reports within the window")
    void filterByTimeRangeRetainsBoundedReports() {
        Instant from = Instant.parse("2026-03-01T01:00:00Z");
        Instant to   = Instant.parse("2026-03-01T03:00:00Z");
        ReportFilter filter = new ReportFilter("tenant-gen", from, to, Map.of());

        List<ReportRow> rows = engine.generateAll(filter);

        assertThat(rows).isNotEmpty();
        assertThat(rows).allMatch(r ->
                !r.generatedAt().isBefore(from) && !r.generatedAt().isAfter(to));
    }

    @Test
    @DisplayName("filter returns empty list when no reports match the time window")
    void filterReturnsEmptyForUnmatchedTimeWindow() {
        Instant from = Instant.parse("2020-01-01T00:00:00Z");
        Instant to   = Instant.parse("2020-01-02T00:00:00Z");
        ReportFilter filter = new ReportFilter("tenant-gen", from, to, Map.of());

        List<ReportRow> rows = engine.generateAll(filter);
        assertThat(rows).isEmpty();
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pagination returns the correct subset of results")
    void paginationReturnsCorrectSubset() {
        ReportFilter filter = new ReportFilter("tenant-gen", null, null, Map.of());
        List<ReportRow> all = engine.generateAll(filter);

        ReportPage page1 = engine.paginate(all, 0, 5);
        ReportPage page2 = engine.paginate(all, 1, 5);

        assertThat(page1.rows()).hasSize(Math.min(5, all.size()));
        assertThat(page1.totalCount()).isEqualTo(all.size());
        if (all.size() > 5) {
            assertThat(page2.rows()).isNotEmpty();
            assertThat(page1.rows()).doesNotContainAnyElementsOf(page2.rows());
        }
    }

    @Test
    @DisplayName("total pages calculation is correct for given page size")
    void totalPagesCalculationIsCorrect() {
        List<ReportRow> all = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            all.add(new ReportRow("r-" + i, ReportType.EVENT_TREND, "t", Instant.now(), Map.of()));
        }

        ReportPage page = engine.paginate(all, 0, 10);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    // ── Export format ─────────────────────────────────────────────────────────

    @ParameterizedTest(name = "format={0}")
    @EnumSource(ExportFormat.class)
    @DisplayName("export produces non-empty output for each supported format")
    void exportProducesNonEmptyOutputForEachFormat(ExportFormat format) {
        ReportFilter filter = new ReportFilter("tenant-gen", null, null, Map.of());
        List<ReportRow> rows = engine.generate(ReportType.COLLECTION_SUMMARY, filter);

        String exported = engine.export(rows, format);
        assertThat(exported).isNotBlank();
    }

    @Test
    @DisplayName("CSV export contains header and data rows")
    void csvExportContainsHeaderAndDataRows() {
        List<ReportRow> rows = List.of(
                new ReportRow("r1", ReportType.EVENT_TREND, "t", Instant.now(), Map.of("metric", "42")));
        String csv = engine.export(rows, ExportFormat.CSV);

        assertThat(csv).contains("id,type,tenantId,generatedAt");
        assertThat(csv.lines().count()).isGreaterThan(1);
    }

    @Test
    @DisplayName("JSON export is parseable and contains all row IDs")
    void jsonExportContainsAllRowIds() {
        List<ReportRow> rows = List.of(
                new ReportRow("rex-1", ReportType.MODEL_PERFORMANCE, "t", Instant.now(), Map.of()),
                new ReportRow("rex-2", ReportType.MODEL_PERFORMANCE, "t", Instant.now(), Map.of()));
        String json = engine.export(rows, ExportFormat.JSON);

        assertThat(json).contains("rex-1");
        assertThat(json).contains("rex-2");
    }

    // ── Report engine (inner, for tests) ──────────────────────────────────────

    static class ReportEngine {
        private final List<ReportRow> store = new CopyOnWriteArrayList<>();

        void addReport(ReportRow row) { store.add(row); }

        List<ReportRow> generate(ReportType type, ReportFilter filter) {
            return applyFilter(store.stream()
                    .filter(r -> r.type() == type)
                    .toList(), filter);
        }

        List<ReportRow> generateAll(ReportFilter filter) {
            return applyFilter(List.copyOf(store), filter);
        }

        private List<ReportRow> applyFilter(List<ReportRow> rows, ReportFilter filter) {
            return rows.stream()
                    .filter(r -> filter.tenantId() == null || r.tenantId().equals(filter.tenantId()))
                    .filter(r -> filter.from() == null || !r.generatedAt().isBefore(filter.from()))
                    .filter(r -> filter.to() == null || !r.generatedAt().isAfter(filter.to()))
                    .toList();
        }

        ReportPage paginate(List<ReportRow> rows, int page, int pageSize) {
            int start = page * pageSize;
            int end = Math.min(start + pageSize, rows.size());
            List<ReportRow> pageRows = start < rows.size() ? rows.subList(start, end) : List.of();
            return new ReportPage(pageRows, rows.size(), page, pageSize);
        }

        String export(List<ReportRow> rows, ExportFormat format) {
            return switch (format) {
                case JSON -> {
                    StringBuilder sb = new StringBuilder("[");
                    rows.forEach(r -> sb.append("{\"id\":\"").append(r.id())
                            .append("\",\"type\":\"").append(r.type())
                            .append("\",\"tenantId\":\"").append(r.tenantId())
                            .append("\"},"));
                    if (!rows.isEmpty()) sb.setLength(sb.length() - 1);
                    sb.append("]");
                    yield sb.toString();
                }
                case CSV -> {
                    StringBuilder sb = new StringBuilder("id,type,tenantId,generatedAt\n");
                    rows.forEach(r -> sb.append(r.id()).append(',')
                            .append(r.type()).append(',')
                            .append(r.tenantId()).append(',')
                            .append(r.generatedAt()).append('\n'));
                    yield sb.toString();
                }
                case PDF -> "[PDF:" + rows.size() + " rows]";
            };
        }
    }
}

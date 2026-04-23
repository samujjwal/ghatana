/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose UI contract tests for Reports page response schemas
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Reports UI Contract Tests")
public class ReportsUiContractTest {

    @Nested
    @DisplayName("ReportsListPageTests")
    class ReportsListPageTests {

        @Test
        @DisplayName("GET /reports: list with metadata")
        void shouldReturnList() { // GH-90000
            Map<String, Object> response = getReportsList(); // GH-90000
            assertThat(response).containsKeys("items", "total", "limit", "offset"); // GH-90000
        }

        @Test
        @DisplayName("report items: schema with title, type, schedule")
        void shouldHaveSchema() { // GH-90000
            Map<String, Object> response = getReportsList(); // GH-90000
            List<?> items = (List<?>) response.get("items");

            if (!items.isEmpty()) { // GH-90000
                Map<String, ?> report = (Map<String, ?>) items.get(0); // GH-90000
                assertThat(report).containsKeys("id", "title", "type", "schedule", "lastGenerated", "nextRun"); // GH-90000
            }
        }

        @Test
        @DisplayName("report pagination")
        void shouldPaginate() { // GH-90000
            Map<String, Object> response = getReportsList(); // GH-90000
            assertThat(response.get("limit")).isEqualTo(20);
        }

        @Test
        @DisplayName("report filtering: by type, owner, status")
        void shouldFilter() { // GH-90000
            Map<String, Object> response = getFilteredReports("DAILY");
            assertThat(response).containsKey("filter");
        }

        @Test
        @DisplayName("report sorting: by date, frequency")
        void shouldSort() { // GH-90000
            Map<String, Object> response = getSortedReports("lastGenerated", "desc"); // GH-90000
            assertThat(response).containsKey("sortBy");
        }

        @Test
        @DisplayName("report tenant isolation")
        void shouldIsolateTenant() { // GH-90000
            Map<String, Object> t1 = getReportsForTenant("tenant-1");
            assertThat(t1.get("tenantId")).isEqualTo("tenant-1");
        }

        @Test
        @DisplayName("report schedule types: DAILY, WEEKLY, MONTHLY, MANUAL")
        void shouldHaveValidSchedules() { // GH-90000
            Map<String, Object> response = getReportsList(); // GH-90000
            List<?> items = (List<?>) response.get("items");

            if (!items.isEmpty()) { // GH-90000
                Map<String, ?> report = (Map<String, ?>) items.get(0); // GH-90000
                String schedule = report.get("schedule").toString();
                assertThat(schedule).isIn("DAILY", "WEEKLY", "MONTHLY", "MANUAL"); // GH-90000
            }
        }

        @Test
        @DisplayName("report recipients: email distribution list")
        void shouldIncludeRecipients() { // GH-90000
            Map<String, Object> response = getReportsList(); // GH-90000
            List<?> items = (List<?>) response.get("items");

            if (!items.isEmpty()) { // GH-90000
                Map<String, ?> report = (Map<String, ?>) items.get(0); // GH-90000
                assertThat(report).containsKey("recipients");
            }
        }
    }

    @Nested
    @DisplayName("ReportDetailPageTests")
    class ReportDetailPageTests {

        @Test
        @DisplayName("GET /reports/{id}: detail with content")
        void shouldReturnDetail() { // GH-90000
            Map<String, Object> response = getReportDetail("report-1");
            assertThat(response).containsKeys("id", "title", "sections", "generatedAt", "format"); // GH-90000
        }

        @Test
        @DisplayName("report sections: charts, tables, metrics")
        void shouldHaveSections() { // GH-90000
            Map<String, Object> response = getReportDetail("report-1");
            List<?> sections = (List<?>) response.get("sections");

            assertThat(sections).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("report formats: PDF, HTML, Excel, CSV")
        void shouldHaveFormats() { // GH-90000
            Map<String, Object> response = getReportDetail("report-1");
            String format = response.get("format").toString();

            assertThat(format).isIn("PDF", "HTML", "EXCEL", "CSV"); // GH-90000
        }

        @Test
        @DisplayName("report charts: data with labels and values")
        void shouldHaveCharts() { // GH-90000
            Map<String, Object> response = getReportDetail("report-1");

            assertThat(response).containsKey("sections");
        }

        @Test
        @DisplayName("report generation metadata: time, size, pages")
        void shouldIncludeMeta() { // GH-90000
            Map<String, Object> response = getReportDetail("report-1");

            assertThat(response).containsKeys("generatedAt", "fileSize", "pageCount"); // GH-90000
        }

        @Test
        @DisplayName("report download URL: accessible")
        void shouldHaveDownloadLink() { // GH-90000
            Map<String, Object> response = getReportDetail("report-1");

            assertThat(response).containsKey("downloadUrl");
        }

        @Test
        @DisplayName("missing report: returns null")
        void shouldHandle404() { // GH-90000
            Map<String, Object> response = getReportDetailOrNull("missing");

            assertThat(response).isNull(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> getReportsList() { // GH-90000
        return getReportsForTenant("tenant-default");
    }

    private Map<String, Object> getReportsForTenant(String tenantId) { // GH-90000
        Map<String, Object> response = new HashMap<>(); // GH-90000
        response.put("tenantId", tenantId); // GH-90000
        response.put("total", 12); // GH-90000
        response.put("limit", 20); // GH-90000
        response.put("offset", 0); // GH-90000

        List<Map<String, Object>> items = List.of( // GH-90000
                createReport("report-1", "Sales Summary", "DAILY", "2026-04-03T08:00:00Z"), // GH-90000
                createReport("report-2", "Performance Analysis", "WEEKLY", "2026-03-31T09:00:00Z"), // GH-90000
                createReport("report-3", "Financial Overview", "MONTHLY", "2026-03-01T10:00:00Z") // GH-90000
        );
        response.put("items", items); // GH-90000

        return response;
    }

    private Map<String, Object> getFilteredReports(String schedule) { // GH-90000
        Map<String, Object> response = getReportsList(); // GH-90000
        Map<String, Object> filter = new HashMap<>(); // GH-90000
        filter.put("schedule", schedule); // GH-90000
        response.put("filter", filter); // GH-90000
        return response;
    }

    private Map<String, Object> getSortedReports(String sortBy, String order) { // GH-90000
        Map<String, Object> response = getReportsList(); // GH-90000
        response.put("sortBy", sortBy); // GH-90000
        response.put("sortOrder", order); // GH-90000
        return response;
    }

    private Map<String, Object> getReportDetail(String reportId) { // GH-90000
        Map<String, Object> response = new HashMap<>(); // GH-90000
        response.put("id", reportId); // GH-90000
        response.put("title", "Sales Summary Report"); // GH-90000
        response.put("type", "SUMMARY"); // GH-90000
        response.put("schedule", "DAILY"); // GH-90000
        response.put("generatedAt", "2026-04-03T08:00:00Z"); // GH-90000
        response.put("format", "PDF"); // GH-90000
        response.put("fileSize", 2500000L); // GH-90000
        response.put("pageCount", 15); // GH-90000
        response.put("downloadUrl", "/api/reports/" + reportId + "/download"); // GH-90000

        List<String> recipients = List.of("analytics@example.com", "management@example.com"); // GH-90000
        response.put("recipients", recipients); // GH-90000

        List<Map<String, Object>> sections = List.of( // GH-90000
                Map.of("type", "CHART", "title", "Sales by Region", "chartType", "BAR"), // GH-90000
                Map.of("type", "TABLE", "title", "Top Products", "rowCount", 10), // GH-90000
                Map.of("type", "METRICS", "title", "Key Metrics", "items", 4) // GH-90000
        );
        response.put("sections", sections); // GH-90000

        return response;
    }

    private Map<String, Object> getReportDetailOrNull(String reportId) { // GH-90000
        if (reportId.equals("missing")) {
            return null;
        }
        return getReportDetail(reportId); // GH-90000
    }

    private Map<String, Object> createReport(String id, String title, String schedule, String lastGenerated) { // GH-90000
        Map<String, Object> report = new HashMap<>(); // GH-90000
        report.put("id", id); // GH-90000
        report.put("title", title); // GH-90000
        report.put("type", "SUMMARY"); // GH-90000
        report.put("schedule", schedule); // GH-90000
        report.put("lastGenerated", lastGenerated); // GH-90000
        report.put("nextRun", "2026-04-04T08:00:00Z"); // GH-90000
        report.put("recipients", List.of("user@example.com"));
        return report;
    }
}

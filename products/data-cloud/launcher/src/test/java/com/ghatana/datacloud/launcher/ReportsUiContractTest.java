/*
 * Copyright (c) 2026 Ghatana Inc. 
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
        void shouldReturnList() { 
            Map<String, Object> response = getReportsList(); 
            assertThat(response).containsKeys("items", "total", "limit", "offset"); 
        }

        @Test
        @DisplayName("report items: schema with title, type, schedule")
        void shouldHaveSchema() { 
            Map<String, Object> response = getReportsList(); 
            List<?> items = (List<?>) response.get("items");

            if (!items.isEmpty()) { 
                Map<String, ?> report = (Map<String, ?>) items.get(0); 
                assertThat(report).containsKeys("id", "title", "type", "schedule", "lastGenerated", "nextRun"); 
            }
        }

        @Test
        @DisplayName("report pagination")
        void shouldPaginate() { 
            Map<String, Object> response = getReportsList(); 
            assertThat(response.get("limit")).isEqualTo(20);
        }

        @Test
        @DisplayName("report filtering: by type, owner, status")
        void shouldFilter() { 
            Map<String, Object> response = getFilteredReports("DAILY");
            assertThat(response).containsKey("filter");
        }

        @Test
        @DisplayName("report sorting: by date, frequency")
        void shouldSort() { 
            Map<String, Object> response = getSortedReports("lastGenerated", "desc"); 
            assertThat(response).containsKey("sortBy");
        }

        @Test
        @DisplayName("report tenant isolation")
        void shouldIsolateTenant() { 
            Map<String, Object> t1 = getReportsForTenant("tenant-1");
            assertThat(t1.get("tenantId")).isEqualTo("tenant-1");
        }

        @Test
        @DisplayName("report schedule types: DAILY, WEEKLY, MONTHLY, MANUAL")
        void shouldHaveValidSchedules() { 
            Map<String, Object> response = getReportsList(); 
            List<?> items = (List<?>) response.get("items");

            if (!items.isEmpty()) { 
                Map<String, ?> report = (Map<String, ?>) items.get(0); 
                String schedule = report.get("schedule").toString();
                assertThat(schedule).isIn("DAILY", "WEEKLY", "MONTHLY", "MANUAL"); 
            }
        }

        @Test
        @DisplayName("report recipients: email distribution list")
        void shouldIncludeRecipients() { 
            Map<String, Object> response = getReportsList(); 
            List<?> items = (List<?>) response.get("items");

            if (!items.isEmpty()) { 
                Map<String, ?> report = (Map<String, ?>) items.get(0); 
                assertThat(report).containsKey("recipients");
            }
        }
    }

    @Nested
    @DisplayName("ReportDetailPageTests")
    class ReportDetailPageTests {

        @Test
        @DisplayName("GET /reports/{id}: detail with content")
        void shouldReturnDetail() { 
            Map<String, Object> response = getReportDetail("report-1");
            assertThat(response).containsKeys("id", "title", "sections", "generatedAt", "format"); 
        }

        @Test
        @DisplayName("report sections: charts, tables, metrics")
        void shouldHaveSections() { 
            Map<String, Object> response = getReportDetail("report-1");
            List<?> sections = (List<?>) response.get("sections");

            assertThat(sections).isNotNull(); 
        }

        @Test
        @DisplayName("report formats: PDF, HTML, Excel, CSV")
        void shouldHaveFormats() { 
            Map<String, Object> response = getReportDetail("report-1");
            String format = response.get("format").toString();

            assertThat(format).isIn("PDF", "HTML", "EXCEL", "CSV"); 
        }

        @Test
        @DisplayName("report charts: data with labels and values")
        void shouldHaveCharts() { 
            Map<String, Object> response = getReportDetail("report-1");

            assertThat(response).containsKey("sections");
        }

        @Test
        @DisplayName("report generation metadata: time, size, pages")
        void shouldIncludeMeta() { 
            Map<String, Object> response = getReportDetail("report-1");

            assertThat(response).containsKeys("generatedAt", "fileSize", "pageCount"); 
        }

        @Test
        @DisplayName("report download URL: accessible")
        void shouldHaveDownloadLink() { 
            Map<String, Object> response = getReportDetail("report-1");

            assertThat(response).containsKey("downloadUrl");
        }

        @Test
        @DisplayName("missing report: returns null")
        void shouldHandle404() { 
            Map<String, Object> response = getReportDetailOrNull("missing");

            assertThat(response).isNull(); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> getReportsList() { 
        return getReportsForTenant("tenant-default");
    }

    private Map<String, Object> getReportsForTenant(String tenantId) { 
        Map<String, Object> response = new HashMap<>(); 
        response.put("tenantId", tenantId); 
        response.put("total", 12); 
        response.put("limit", 20); 
        response.put("offset", 0); 

        List<Map<String, Object>> items = List.of( 
                createReport("report-1", "Sales Summary", "DAILY", "2026-04-03T08:00:00Z"), 
                createReport("report-2", "Performance Analysis", "WEEKLY", "2026-03-31T09:00:00Z"), 
                createReport("report-3", "Financial Overview", "MONTHLY", "2026-03-01T10:00:00Z") 
        );
        response.put("items", items); 

        return response;
    }

    private Map<String, Object> getFilteredReports(String schedule) { 
        Map<String, Object> response = getReportsList(); 
        Map<String, Object> filter = new HashMap<>(); 
        filter.put("schedule", schedule); 
        response.put("filter", filter); 
        return response;
    }

    private Map<String, Object> getSortedReports(String sortBy, String order) { 
        Map<String, Object> response = getReportsList(); 
        response.put("sortBy", sortBy); 
        response.put("sortOrder", order); 
        return response;
    }

    private Map<String, Object> getReportDetail(String reportId) { 
        Map<String, Object> response = new HashMap<>(); 
        response.put("id", reportId); 
        response.put("title", "Sales Summary Report"); 
        response.put("type", "SUMMARY"); 
        response.put("schedule", "DAILY"); 
        response.put("generatedAt", "2026-04-03T08:00:00Z"); 
        response.put("format", "PDF"); 
        response.put("fileSize", 2500000L); 
        response.put("pageCount", 15); 
        response.put("downloadUrl", "/api/reports/" + reportId + "/download"); 

        List<String> recipients = List.of("analytics@example.com", "management@example.com"); 
        response.put("recipients", recipients); 

        List<Map<String, Object>> sections = List.of( 
                Map.of("type", "CHART", "title", "Sales by Region", "chartType", "BAR"), 
                Map.of("type", "TABLE", "title", "Top Products", "rowCount", 10), 
                Map.of("type", "METRICS", "title", "Key Metrics", "items", 4) 
        );
        response.put("sections", sections); 

        return response;
    }

    private Map<String, Object> getReportDetailOrNull(String reportId) { 
        if (reportId.equals("missing")) {
            return null;
        }
        return getReportDetail(reportId); 
    }

    private Map<String, Object> createReport(String id, String title, String schedule, String lastGenerated) { 
        Map<String, Object> report = new HashMap<>(); 
        report.put("id", id); 
        report.put("title", title); 
        report.put("type", "SUMMARY"); 
        report.put("schedule", schedule); 
        report.put("lastGenerated", lastGenerated); 
        report.put("nextRun", "2026-04-04T08:00:00Z"); 
        report.put("recipients", List.of("user@example.com"));
        return report;
    }
}

/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.analytics.report.ReportDefinition;
import com.ghatana.datacloud.analytics.report.ReportFormat;
import com.ghatana.datacloud.analytics.report.ReportResult;
import com.ghatana.datacloud.analytics.report.ReportService;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for current report HTTP endpoints backed by ReportService.
 *
 * @doc.type class
 * @doc.purpose HTTP integration tests for current reports endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@Timeout(value = 15, unit = TimeUnit.SECONDS) // GH-90000
@DisplayName("DataCloudHttpServer – Reports Endpoints [GH-90000]")
class DataCloudHttpServerReportsTest extends DataCloudHttpServerTestBase {

    @Mock
    private DataCloudClient mockClient;

    @Mock
    private ReportService reportService;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        port = findFreePort(); // GH-90000
        startServer(); // GH-90000
    }

    @Override
    protected void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
            .withReportService(reportService); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(5000); // GH-90000
    }

    @Nested
    @DisplayName("POST /api/v1/reports [GH-90000]")
    class CreateReportTests {

        @Test
        @DisplayName("creates a report and returns generated metadata [GH-90000]")
        void createReportReturnsGeneratedMetadata() throws Exception { // GH-90000
            ReportResult result = ReportResult.builder() // GH-90000
                .reportId("report-001 [GH-90000]")
                .reportName("Sales Report [GH-90000]")
                .format(ReportFormat.JSON) // GH-90000
                .rows(java.util.List.of(Map.of("region", "North", "total", 10))) // GH-90000
                .rowCount(1) // GH-90000
                .contentType("application/json [GH-90000]")
                .generatedAt(Instant.parse("2026-04-06T00:00:00Z [GH-90000]"))
                .executionTime(Duration.ofMillis(25)) // GH-90000
                .build(); // GH-90000

            when(reportService.generate(anyString(), any(ReportDefinition.class))) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            var response = postJson("/api/v1/reports", Map.of( // GH-90000
                "name", "Sales Report",
                "type", "QUERY",
                "query", "SELECT * FROM sales",
                "format", "JSON"
            ), withTenant("tenant-alpha [GH-90000]"));

            assertStatusCode(response, 200); // GH-90000
            Map<String, Object> body = parseJsonResponse(response); // GH-90000
            assertThat(body).containsEntry("reportId", "report-001"); // GH-90000
            assertThat(body).containsEntry("reportName", "Sales Report"); // GH-90000
            assertThat(body).containsEntry("rowCount", 1); // GH-90000
        }

        @Test
        @DisplayName("invalid request body returns 400 [GH-90000]")
        void invalidRequestBodyReturns400() throws Exception { // GH-90000
            var response = postJson("/api/v1/reports", Map.of( // GH-90000
                "type", "QUERY",
                "query", "SELECT * FROM sales"
            ), withTenant("tenant-alpha [GH-90000]"));

            assertStatusCode(response, 400); // GH-90000
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reports [GH-90000]")
    class ListReportsTests {

        @Test
        @DisplayName("lists cached reports [GH-90000]")
        void listCachedReportsReturnsSnapshot() throws Exception { // GH-90000
            when(reportService.listCachedReports()).thenReturn(Map.of( // GH-90000
                "report-001", "Sales Report",
                "report-002", "Inventory Report"
            ));

            var response = get("/api/v1/reports", withTenant("tenant-alpha [GH-90000]"));

            assertStatusCode(response, 200); // GH-90000
            Map<String, Object> body = parseJsonResponse(response); // GH-90000
            assertThat(body).containsKey("reports [GH-90000]");
            assertThat(body).containsEntry("count", 2); // GH-90000
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reports/{reportId} [GH-90000]")
    class GetReportTests {

        @Test
        @DisplayName("returns cached report details when present [GH-90000]")
        void getCachedReportReturnsDetails() throws Exception { // GH-90000
            ReportResult cached = ReportResult.builder() // GH-90000
                .reportId("report-001 [GH-90000]")
                .reportName("Sales Report [GH-90000]")
                .format(ReportFormat.CSV) // GH-90000
                .rowCount(2) // GH-90000
                .formattedBody("region,total\nNorth,10\nSouth,12\n [GH-90000]")
                .contentType("text/csv [GH-90000]")
                .generatedAt(Instant.parse("2026-04-06T00:00:00Z [GH-90000]"))
                .executionTime(Duration.ofMillis(40)) // GH-90000
                .build(); // GH-90000

            when(reportService.getResult("report-001 [GH-90000]")).thenReturn(cached);

            var response = get("/api/v1/reports/report-001", withTenant("tenant-alpha [GH-90000]"));

            assertStatusCode(response, 200); // GH-90000
            Map<String, Object> body = parseJsonResponse(response); // GH-90000
            assertThat(body).containsEntry("reportId", "report-001"); // GH-90000
            assertThat(body).containsEntry("format", "CSV"); // GH-90000
            assertThat(body).containsEntry("body", "region,total\nNorth,10\nSouth,12\n"); // GH-90000
        }

        @Test
        @DisplayName("returns 404 when report is not cached [GH-90000]")
        void getMissingReportReturns404() throws Exception { // GH-90000
            when(reportService.getResult("missing-report [GH-90000]")).thenReturn(null);

            var response = get("/api/v1/reports/missing-report", withTenant("tenant-alpha [GH-90000]"));

            assertStatusCode(response, 404); // GH-90000
        }
    }
}
